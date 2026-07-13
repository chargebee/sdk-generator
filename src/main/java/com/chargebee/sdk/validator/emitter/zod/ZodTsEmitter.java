package com.chargebee.sdk.validator.emitter.zod;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.HttpRequestType;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.validator.ValidatorEmitter;
import com.chargebee.sdk.validator.ast.js.JsBuilder;
import com.chargebee.sdk.validator.ast.js.JsNode;
import com.chargebee.sdk.validator.ast.js.TsPrinter;
import com.chargebee.sdk.validator.ir.PropertyEntry;
import com.chargebee.sdk.validator.ir.SharedSchemaRegistry;
import com.chargebee.sdk.validator.ir.ValidationIRBuilder;
import com.chargebee.sdk.validator.ir.ValidationNode;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.*;

/**
 * Emits Zod schema files ({@code .schema.ts}) for POST bodies and GET query parameters.
 *
 * <p>Output layout (flat {@code src/schema} style):
 *
 * <pre>
 * {outputDir}/
 *   shared.schema.ts           ← shared $ref schemas
 *   {resource_snake}.schema.ts ← all actions for that resource + z.infer type exports
 *   index.ts                   ← barrel re-export
 * </pre>
 */
public class ZodTsEmitter implements ValidatorEmitter {

  @Override
  public List<FileOp> emit(Spec spec, SharedSchemaRegistry registry, String outputDir) {
    List<FileOp> ops = new ArrayList<>();
    TsPrinter printer = new TsPrinter();
    ValidationIRBuilder irBuilder = new ValidationIRBuilder(spec.openAPI(), registry);

    ops.add(new FileOp.CreateDirectory(outputDir, ""));

    List<String> indexExports = new ArrayList<>();

    for (Resource resource : spec.resources()) {
      List<Action> actions =
          resource.actions.stream()
              .filter(Action::isNotHiddenFromSDK)
              .filter(Action::isNotBulkOperation)
              .filter(Action::isNotInternalOperation)
              .sorted(Comparator.comparing(Action::sortOrder))
              .toList();

      List<ActionSchemaUnit> units = new ArrayList<>();
      for (Action action : actions) {
        Schema<?> bodySchema = resolveBodySchema(action, spec);
        if (bodySchema == null) continue;

        ValidationNode irNode = irBuilder.buildRootNode(bodySchema, new HashSet<>());
        ValidationNode.ObjectNode rootObj = ensureObject(irNode);
        if (rootObj == null) continue;

        rootObj = new ValidationNode.ObjectNode(rootObj.properties(), true, rootObj.ref());
        units.add(new ActionSchemaUnit(action, irNode, rootObj));
      }

      if (units.isEmpty()) {
        continue;
      }

      Set<String> usedRefs = new LinkedHashSet<>();
      for (ActionSchemaUnit unit : units) {
        collectRefsInto(unit.irNode, usedRefs);
      }

      List<JsNode> body = new ArrayList<>();
      body.add(resourceFileHeader(resource.name, units.stream().map(u -> u.action.name).toList()));
      body.add(new JsNode.RequireCall("zod", List.of("z")));

      if (!usedRefs.isEmpty()) {
        List<String> importNames =
            usedRefs.stream().map(ZodNamingStrategy::sharedSchemaName).sorted().toList();
        body.add(
            new JsNode.RequireCall(
                "./" + ZodNamingStrategy.SHARED_SCHEMA_FILE.replace(".ts", ".js"), importNames));
      }

      for (int i = 0; i < units.size(); i++) {
        ActionSchemaUnit unit = units.get(i);
        List<JsNode.VariableDeclaration> nestedDecls = new ArrayList<>();
        ZodTypeMapper mapper =
            new ZodTypeMapper(unit.action.name, resource.name, registry, nestedDecls);
        JsNode bodyExpr = mapper.buildZodObjectExpr(unit.rootObj, "");
        String bodyConst = ZodNamingStrategy.bodySchemaName(unit.action.name, resource.name);

        if (i > 0) {
          body.add(new JsNode.Identifier(""));
        }
        body.add(new JsNode.Identifier("\n//" + resource.name + "." + unit.action.name + "\n"));
        body.add(new JsNode.Identifier(""));
        body.addAll(nestedDecls);
        body.add(JsBuilder.constDecl(bodyConst, bodyExpr));
        body.add(JsBuilder.exportNamed(bodyConst, JsBuilder.id(bodyConst)));
        body.add(
            new JsNode.TypeInferExport(
                ZodNamingStrategy.bodyInferredTypeName(unit.action.name, resource.name),
                bodyConst));
      }

      String fileName = ZodNamingStrategy.resourceSchemaFileName(resource.name);
      ops.add(
          new FileOp.WriteString(
              outputDir, fileName, printer.print(JsBuilder.program(body)) + "\n"));
      indexExports.add("./" + fileName.replace(".ts", ".js"));
    }

    if (!registry.all().isEmpty()) {
      ops.add(
          new FileOp.WriteString(
              outputDir,
              ZodNamingStrategy.SHARED_SCHEMA_FILE,
              buildSharedFile(registry, printer) + "\n"));
    }

    ops.add(
        new FileOp.WriteString(outputDir, "index.ts", buildIndex(indexExports, registry) + "\n"));

    return ops;
  }

  private record ActionSchemaUnit(
      Action action, ValidationNode irNode, ValidationNode.ObjectNode rootObj) {}

  // ---- helpers ----

  private Schema<?> resolveBodySchema(Action action, Spec spec) {
    if (action.httpRequestType == HttpRequestType.GET) {
      return resolveQueryParamsSchema(action, spec);
    }

    if (action.requestBodyParameters().isEmpty()) return null;
    if (spec.openAPI().getPaths() == null) return null;
    var pathItem = spec.openAPI().getPaths().get(action.getUrl());
    if (pathItem == null || pathItem.getPost() == null) return null;
    var rb = pathItem.getPost().getRequestBody();
    if (rb == null) return null;
    var content = rb.getContent();
    if (content == null) return null;
    var mt = content.get("application/x-www-form-urlencoded");
    return mt == null ? null : mt.getSchema();
  }

  private Schema<?> resolveQueryParamsSchema(Action action, Spec spec) {
    if (spec.openAPI().getPaths() == null) return null;
    var pathItem = spec.openAPI().getPaths().get(action.getUrl());
    if (pathItem == null || pathItem.getGet() == null) return null;
    var operation = pathItem.getGet();
    if (operation.getParameters() == null || operation.getParameters().isEmpty()) return null;

    List<Parameter> queryParams =
        operation.getParameters().stream()
            .filter(p -> "query".equals(p.getIn()))
            .filter(p -> p.getSchema() != null)
            .toList();

    if (queryParams.isEmpty()) return null;

    // Construct an object schema from query parameters
    ObjectSchema objectSchema = new ObjectSchema();
    Map<String, Schema> properties = new LinkedHashMap<>();
    List<String> requiredFields = new ArrayList<>();

    for (Parameter param : queryParams) {
      properties.put(param.getName(), param.getSchema());
      if (param.getRequired() != null && param.getRequired()) {
        requiredFields.add(param.getName());
      }
    }

    objectSchema.setProperties(properties);
    if (!requiredFields.isEmpty()) {
      objectSchema.setRequired(requiredFields);
    }

    return objectSchema;
  }

  private ValidationNode.ObjectNode ensureObject(ValidationNode node) {
    return node instanceof ValidationNode.ObjectNode on ? on : null;
  }

  private void collectRefsInto(ValidationNode node, Set<String> refs) {
    if (node instanceof ValidationNode.RefNode rn) {
      refs.add(rn.targetName());
    } else if (node instanceof ValidationNode.ObjectNode on) {
      for (PropertyEntry pe : on.properties().values()) collectRefsInto(pe.node(), refs);
    } else if (node instanceof ValidationNode.ArrayNode an) {
      collectRefsInto(an.items(), refs);
    } else if (node instanceof ValidationNode.MapNode mn) {
      collectRefsInto(mn.valueSchema(), refs);
    }
  }

  private JsNode resourceFileHeader(String resource, List<String> actionNames) {
    String actions = String.join(", ", actionNames);
    return new JsNode.Identifier(
        "// Generated Zod schemas: "
            + resource
            + "\n// Actions: "
            + actions
            + "\n// Do not edit manually – regenerate via sdk-generator\n");
  }

  private String buildSharedFile(SharedSchemaRegistry registry, TsPrinter printer) {
    List<JsNode> body = new ArrayList<>();
    body.add(
        new JsNode.Identifier(
            "// Shared Zod schemas\n// Do not edit manually – regenerate via sdk-generator\n"));
    body.add(new JsNode.RequireCall("zod", List.of("z")));

    for (Map.Entry<String, ValidationNode> entry : registry.all().entrySet()) {
      String refName = entry.getKey();
      ValidationNode refNode = entry.getValue();
      String constName = ZodNamingStrategy.sharedSchemaName(refName);

      List<JsNode.VariableDeclaration> nestedDecls = new ArrayList<>();
      ZodTypeMapper mapper = new ZodTypeMapper("shared", refName, registry, nestedDecls);

      JsNode expr =
          refNode instanceof ValidationNode.ObjectNode on
              ? mapper.buildZodObjectExpr(on, refName)
              : mapper.toZod(refNode, refName, false);

      body.addAll(nestedDecls);
      body.add(JsBuilder.constDecl(constName, expr));
      body.add(JsBuilder.exportNamed(constName, JsBuilder.id(constName)));
    }

    return printer.print(JsBuilder.program(body));
  }

  private String buildIndex(List<String> resourceSchemaFiles, SharedSchemaRegistry registry) {
    StringBuilder sb = new StringBuilder("// Auto-generated barrel export for Zod validators\n");
    if (!registry.all().isEmpty()) {
      sb.append("export * from './")
          .append(ZodNamingStrategy.SHARED_SCHEMA_FILE.replace(".ts", ".js"))
          .append("';\n");
    }
    for (String f : resourceSchemaFiles) {
      sb.append("export * from '").append(f).append("';\n");
    }
    return sb.toString();
  }
}
