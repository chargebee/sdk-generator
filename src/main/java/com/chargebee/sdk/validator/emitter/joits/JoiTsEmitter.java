package com.chargebee.sdk.validator.emitter.joits;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.HttpRequestType;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.validator.ValidatorEmitter;
import com.chargebee.sdk.validator.ast.js.JsBuilder;
import com.chargebee.sdk.validator.ast.js.JsNode;
import com.chargebee.sdk.validator.ast.js.TsPrinter;
import com.chargebee.sdk.validator.emitter.joi.JoiNamingStrategy;
import com.chargebee.sdk.validator.emitter.joi.JoiTypeMapper;
import com.chargebee.sdk.validator.ir.PropertyEntry;
import com.chargebee.sdk.validator.ir.SharedSchemaRegistry;
import com.chargebee.sdk.validator.ir.ValidationIRBuilder;
import com.chargebee.sdk.validator.ir.ValidationNode;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Paths;
import java.util.*;

/**
 * TypeScript variant of the Joi emitter.
 *
 * <p>Generates {@code .validation.ts} files using ES {@code import}/{@code export} syntax.
 * Output layout matches the JS emitter but with {@code .ts} extensions.
 *
 * <pre>
 * {outputDir}/
 *   shared.validation.ts
 *   {resource}/
 *     {action}.validation.ts
 *   index.ts
 * </pre>
 */
public class JoiTsEmitter implements ValidatorEmitter {

  private static final String JOI_MODULE = "joi";

  @Override
  public List<FileOp> emit(Spec spec, SharedSchemaRegistry registry, String outputDir) {
    List<FileOp> ops = new ArrayList<>();
    TsPrinter printer = new TsPrinter();
    ValidationIRBuilder irBuilder = new ValidationIRBuilder(spec.openAPI(), registry);

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

        // Force allowUnknown=true on top-level body schema
        rootObj = new ValidationNode.ObjectNode(rootObj.properties(), true, rootObj.ref());

        JsNode bodyExpr = mapper.buildJoiObjectExpr(rootObj, "");
        String bodySchemaConstName = JoiNamingStrategy.bodySchemaName(action.name, resource.name);

        Set<String> usedRefs = collectRefs(irNode);

        List<JsNode> body = new ArrayList<>();
        body.add(headerComment(resource.name, action.name));
        body.add(JsBuilder.require(JOI_MODULE));

        // Import shared schemas from shared.validation.ts
        for (String refName : usedRefs) {
          String constName = JoiNamingStrategy.sharedSchemaName(refName);
          body.add(
              new JsNode.RequireCall("../shared.validation.js", List.of(constName)));
        }

        body.addAll(nestedDecls);
        body.add(JsBuilder.constDecl(bodySchemaConstName, bodyExpr));
        body.add(JsBuilder.exportNamed(bodySchemaConstName, JsBuilder.id(bodySchemaConstName)));

        String fileName = tsFileName(action.name);
        String fileContent = printer.print(JsBuilder.program(body));
        String filePath = Paths.get(resourceDir, fileName).toString();
        ops.add(new FileOp.WriteString(outputDir, filePath, fileContent + "\n"));

        indexExports.add(
            "./" + resourceDir + "/" + tsFileName(action.name).replace(".ts", ".js"));
      }
    }

    // shared.validation.ts
    if (!registry.all().isEmpty()) {
      String sharedContent = buildSharedFile(registry, printer);
      ops.add(new FileOp.WriteString(outputDir, "shared.validation.ts", sharedContent + "\n"));
    }

    // index.ts
    String indexContent = buildIndexFile(indexExports, registry.all().keySet(), printer);
    ops.add(new FileOp.WriteString(outputDir, "index.ts", indexContent + "\n"));

    return ops;
  }

  // ---- helpers ----

  private static String tsFileName(String actionName) {
    return JoiNamingStrategy.fileName(actionName).replace(".validation.js", ".validation.ts");
  }

  private Schema<?> resolveBodySchema(Action action, Spec spec) {
    if (action.httpRequestType == HttpRequestType.GET) return null;
    var bodyParams = action.requestBodyParameters();
    if (bodyParams.isEmpty()) return null;
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

  private Set<String> collectRefs(ValidationNode node) {
    Set<String> refs = new LinkedHashSet<>();
    collectRefsInto(node, refs);
    return refs;
  }

  private void collectRefsInto(ValidationNode node, Set<String> refs) {
    if (node instanceof ValidationNode.RefNode rn) {
      refs.add(rn.targetName());
    } else if (node instanceof ValidationNode.ObjectNode on) {
      for (PropertyEntry pe : on.properties().values()) {
        collectRefsInto(pe.node(), refs);
      }
    } else if (node instanceof ValidationNode.ArrayNode an) {
      collectRefsInto(an.items(), refs);
    } else if (node instanceof ValidationNode.MapNode mn) {
      collectRefsInto(mn.valueSchema(), refs);
    }
  }

  private JsNode headerComment(String resourceName, String actionName) {
    return new JsNode.Identifier(
        "// Generated Joi validator: "
            + resourceName
            + "."
            + actionName
            + "\n// Do not edit manually – regenerate via sdk-generator\n");
  }

  private String buildSharedFile(SharedSchemaRegistry registry, TsPrinter printer) {
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
      List<String> actionFiles, Set<String> sharedRefs, TsPrinter printer) {
    StringBuilder sb = new StringBuilder();
    sb.append("// Auto-generated barrel export for Joi validators\n");

    if (!sharedRefs.isEmpty()) {
      sb.append("export * from './shared.validation.js';\n");
    }

    for (String filePath : actionFiles) {
      sb.append("export * from '").append(filePath).append("';\n");
    }

    return sb.toString();
  }
}
