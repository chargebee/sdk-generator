package com.chargebee.sdk.validator.emitter.joi;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.HttpRequestType;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.validator.ValidatorEmitter;
import com.chargebee.sdk.validator.ast.js.JsBuilder;
import com.chargebee.sdk.validator.ast.js.JsNode;
import com.chargebee.sdk.validator.ast.js.JsPrinter;
import com.chargebee.sdk.validator.ir.PropertyEntry;
import com.chargebee.sdk.validator.ir.SharedSchemaRegistry;
import com.chargebee.sdk.validator.ir.ValidationIRBuilder;
import com.chargebee.sdk.validator.ir.ValidationNode;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Paths;
import java.util.*;

/**
 * Emits Joi validation files for every action in the spec.
 *
 * <p>Output layout:
 * <pre>
 * {outputDir}/
 *   shared.validation.js
 *   {resource}/
 *     {action}.validation.js
 *   index.js
 * </pre>
 */
public class JoiEmitter implements ValidatorEmitter {

  private static final String JOI_MODULE = "joi";

  @Override
  public List<FileOp> emit(Spec spec, SharedSchemaRegistry registry, String outputDir) {
    List<FileOp> ops = new ArrayList<>();
    JsPrinter printer = new JsPrinter();
    ValidationIRBuilder irBuilder = new ValidationIRBuilder(spec.openAPI(), registry);

    // Ensure base directory exists
    ops.add(new FileOp.CreateDirectory(outputDir, ""));

    List<String> indexExports = new ArrayList<>();

    for (Resource resource : spec.resources()) {
      String resourceDir = JoiNamingStrategy.resourceDir(resource.name);
      ops.add(new FileOp.CreateDirectory(outputDir, resourceDir));

      List<Action> actions =
          resource.actions.stream()
              .filter(Action::isNotHiddenFromSDK)
              .filter(Action::isNotBulkOperation)
              .filter(Action::isNotInternalOperation)
              .sorted(Comparator.comparing(Action::sortOrder))
              .toList();

      for (Action action : actions) {
        Schema<?> bodySchema = resolveBodySchema(action, spec);
        if (bodySchema == null) continue;

        List<JsNode.VariableDeclaration> nestedDecls = new ArrayList<>();
        JoiTypeMapper mapper =
            new JoiTypeMapper(action.name, resource.name, registry, nestedDecls);

        ValidationNode irNode = irBuilder.buildNode(bodySchema, new HashSet<>());

        ValidationNode.ObjectNode rootObj = ensureObjectNode(irNode);
        if (rootObj == null) continue;

        // Force allowUnknown=true on top-level body schema for forward compatibility
        rootObj =
            new ValidationNode.ObjectNode(
                rootObj.properties(), true, rootObj.ref());

        JsNode bodyExpr = mapper.buildJoiObjectExpr(rootObj, "");
        String bodySchemaConstName =
            JoiNamingStrategy.bodySchemaName(action.name, resource.name);

        // Collect shared schema imports needed by this file
        Set<String> usedRefs = collectRefs(irNode);

        List<JsNode> body = new ArrayList<>();
        body.add(headerComment(resource.name, action.name));
        body.add(JsBuilder.require(JOI_MODULE));

        // Import shared schemas
        for (String refName : usedRefs) {
          String constName = JoiNamingStrategy.sharedSchemaName(refName);
          body.add(
              JsBuilder.constDecl(
                  constName,
                  new JsNode.MemberAccess(
                      new JsNode.CallExpression(
                          JsBuilder.id("require"),
                          List.of(JsBuilder.lit("../shared.validation"))),
                      constName)));
        }

        // Nested inline schemas (built while mapping)
        body.addAll(nestedDecls);

        // Main body schema
        body.add(JsBuilder.constDecl(bodySchemaConstName, bodyExpr));

        // Export
        body.add(JsBuilder.exportNamed(bodySchemaConstName, JsBuilder.id(bodySchemaConstName)));

        String fileName = JoiNamingStrategy.fileName(action.name);
        String fileContent = printer.print(JsBuilder.program(body));
        String filePath = Paths.get(resourceDir, fileName).toString();
        ops.add(new FileOp.WriteString(outputDir, filePath, fileContent + "\n"));

        indexExports.add("./" + resourceDir + "/" + JoiNamingStrategy.fileName(action.name).replace(".js", ""));
      }
    }

    // shared.validation.js
    if (!registry.all().isEmpty()) {
      String sharedContent = buildSharedFile(registry, printer);
      ops.add(new FileOp.WriteString(outputDir, "shared.validation.js", sharedContent + "\n"));
    }

    // index.js
    String indexContent = buildIndexFile(indexExports, registry.all().keySet(), printer);
    ops.add(new FileOp.WriteString(outputDir, "index.js", indexContent + "\n"));

    return ops;
  }

  // ---- helpers ----

  private Schema<?> resolveBodySchema(Action action, Spec spec) {
    if (action.httpRequestType == HttpRequestType.GET) {
      // For GET actions, use query parameters
      var queryParams = action.queryParameters();
      if (queryParams.isEmpty()) return null;
      // Build a synthetic object schema from query parameters – handled by the IR builder
      // via the action's schema directly; for now skip GET actions without body
      return null;
    }
    var bodyParams = action.requestBodyParameters();
    if (bodyParams.isEmpty()) return null;

    // Get the raw schema from the spec's OpenAPI model
    if (spec.openAPI().getPaths() == null) return null;
    var pathItem = spec.openAPI().getPaths().get(action.getUrl());
    if (pathItem == null) return null;
    var op = pathItem.getPost();
    if (op == null || op.getRequestBody() == null) return null;
    var content = op.getRequestBody().getContent();
    if (content == null) return null;
    var mt = content.get("application/x-www-form-urlencoded");
    if (mt == null) return null;
    return mt.getSchema();
  }

  private ValidationNode.ObjectNode ensureObjectNode(ValidationNode node) {
    if (node instanceof ValidationNode.ObjectNode on) return on;
    return null;
  }

  /** Recursively collect all RefNode target names. */
  private Set<String> collectRefs(ValidationNode node) {
    Set<String> refs = new LinkedHashSet<>();
    collectRefsInto(node, refs, new HashSet<>());
    return refs;
  }

  private void collectRefsInto(ValidationNode node, Set<String> refs, Set<String> visiting) {
    if (node instanceof ValidationNode.RefNode rn) {
      refs.add(rn.targetName());
    } else if (node instanceof ValidationNode.ObjectNode on) {
      for (PropertyEntry pe : on.properties().values()) {
        collectRefsInto(pe.node(), refs, visiting);
      }
    } else if (node instanceof ValidationNode.ArrayNode an) {
      collectRefsInto(an.items(), refs, visiting);
    } else if (node instanceof ValidationNode.MapNode mn) {
      collectRefsInto(mn.valueSchema(), refs, visiting);
    }
    // primitives – nothing to do
  }

  private JsNode headerComment(String resourceName, String actionName) {
    // A JS comment node – represented as a raw Identifier for simplicity
    return new JsNode.Identifier(
        "// Generated Joi validator: "
            + resourceName
            + "."
            + actionName
            + "\n// Do not edit manually – regenerate via sdk-generator\n");
  }

  private String buildSharedFile(SharedSchemaRegistry registry, JsPrinter printer) {
    List<JsNode> body = new ArrayList<>();
    body.add(
        new JsNode.Identifier(
            "// Shared Joi schemas\n// Do not edit manually – regenerate via sdk-generator\n"));
    body.add(JsBuilder.require(JOI_MODULE));

    for (Map.Entry<String, ValidationNode> entry : registry.all().entrySet()) {
      String refName = entry.getKey();
      ValidationNode refNode = entry.getValue();
      String constName = JoiNamingStrategy.sharedSchemaName(refName);

      List<JsNode.VariableDeclaration> nestedDecls = new ArrayList<>();
      JoiTypeMapper mapper = new JoiTypeMapper("shared", refName, registry, nestedDecls);

      JsNode expr;
      if (refNode instanceof ValidationNode.ObjectNode on) {
        expr = mapper.buildJoiObjectExpr(on, refName);
      } else {
        expr = mapper.toJoi(refNode, refName, false);
      }
      body.addAll(nestedDecls);
      body.add(JsBuilder.constDecl(constName, expr));
      body.add(JsBuilder.exportNamed(constName, JsBuilder.id(constName)));
    }

    return printer.print(JsBuilder.program(body));
  }

  private String buildIndexFile(
      List<String> actionFiles, Set<String> sharedRefs, JsPrinter printer) {
    List<JsNode> body = new ArrayList<>();
    body.add(
        new JsNode.Identifier("// Auto-generated barrel export for Joi validators\n"));

    if (!sharedRefs.isEmpty()) {
      body.add(
          new JsNode.Identifier(
              "const shared = require('./shared.validation');\nmodule.exports.shared = shared;\n"));
    }

    for (String filePath : actionFiles) {
      // e.g. './customer/create'
      String varName =
          filePath
              .replace("./", "")
              .replace("/", "_")
              .replace("-", "_");
      body.add(
          new JsNode.Identifier(
              "const " + varName + " = require('" + filePath + "');\n"
              + "Object.assign(module.exports, " + varName + ");"));
    }

    return printer.print(JsBuilder.program(body));
  }
}
