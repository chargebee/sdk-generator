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
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Paths;
import java.util.*;

/**
 * Emits Zod validation files (.validation.ts) for every POST action in the spec.
 *
 * <p>Output layout:
 * <pre>
 * {outputDir}/
 *   shared.validation.ts        ← shared $ref schemas
 *   {resource}/
 *     {action}.validation.ts   ← per-action body schema
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
      String resourceDir = ZodNamingStrategy.resourceDir(resource.name);
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
        ZodTypeMapper mapper = new ZodTypeMapper(action.name, resource.name, registry, nestedDecls);

        ValidationNode irNode = irBuilder.buildNode(bodySchema, new HashSet<>());
        ValidationNode.ObjectNode rootObj = ensureObject(irNode);
        if (rootObj == null) continue;

        // Top-level body schema always allows unknown keys
        rootObj = new ValidationNode.ObjectNode(rootObj.properties(), true, rootObj.ref());

        JsNode bodyExpr = mapper.buildZodObjectExpr(rootObj, "");
        String bodyConst = ZodNamingStrategy.bodySchemaName(action.name, resource.name);

        Set<String> usedRefs = collectRefs(irNode);

        List<JsNode> body = new ArrayList<>();
        body.add(headerComment(resource.name, action.name));
        body.add(new JsNode.RequireCall("zod", List.of("z"))); // import { z } from 'zod'

        // Named imports from shared.validation.ts
        for (String refName : usedRefs) {
          String constName = ZodNamingStrategy.sharedSchemaName(refName);
          body.add(new JsNode.RequireCall("../shared.validation.js", List.of(constName)));
        }

        body.addAll(nestedDecls);
        body.add(JsBuilder.constDecl(bodyConst, bodyExpr));
        body.add(JsBuilder.exportNamed(bodyConst, JsBuilder.id(bodyConst)));

        String fileName = ZodNamingStrategy.fileName(action.name);
        String filePath = Paths.get(resourceDir, fileName).toString();
        ops.add(
            new FileOp.WriteString(
                outputDir, filePath, printer.print(JsBuilder.program(body)) + "\n"));

        indexExports.add("./" + resourceDir + "/" + fileName.replace(".ts", ".js"));
      }
    }

    if (!registry.all().isEmpty()) {
      ops.add(
          new FileOp.WriteString(
              outputDir, "shared.validation.ts", buildSharedFile(registry, printer) + "\n"));
    }

    ops.add(
        new FileOp.WriteString(outputDir, "index.ts", buildIndex(indexExports, registry) + "\n"));

    return ops;
  }

  // ---- helpers ----

  private Schema<?> resolveBodySchema(Action action, Spec spec) {
    if (action.httpRequestType == HttpRequestType.GET) return null;
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

  private ValidationNode.ObjectNode ensureObject(ValidationNode node) {
    return node instanceof ValidationNode.ObjectNode on ? on : null;
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
      for (PropertyEntry pe : on.properties().values()) collectRefsInto(pe.node(), refs);
    } else if (node instanceof ValidationNode.ArrayNode an) {
      collectRefsInto(an.items(), refs);
    } else if (node instanceof ValidationNode.MapNode mn) {
      collectRefsInto(mn.valueSchema(), refs);
    }
  }

  private JsNode headerComment(String resource, String action) {
    return new JsNode.Identifier(
        "// Generated Zod validator: "
            + resource
            + "."
            + action
            + "\n"
            + "// Do not edit manually – regenerate via sdk-generator\n");
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

  private String buildIndex(List<String> actionFiles, SharedSchemaRegistry registry) {
    StringBuilder sb = new StringBuilder("// Auto-generated barrel export for Zod validators\n");
    if (!registry.all().isEmpty()) {
      sb.append("export * from './shared.validation.js';\n");
    }
    for (String f : actionFiles) {
      sb.append("export * from '").append(f).append("';\n");
    }
    return sb.toString();
  }
}
