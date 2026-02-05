package com.chargebee.sdk.node.webhook;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class WebhookGenerator {
  private static List<String> getEventResourcesForAEvent(Resource eventResource, Spec spec) {
    List<String> resources = new ArrayList<>();
    for (Attribute attribute : eventResource.attributes()) {
      if (attribute.name.equals("content")) {
        attribute
            .attributes()
            .forEach(
                (innerAttribute -> {
                  Schema<?> schema = innerAttribute.schema;
                  String ref = null;
                  boolean isArray = false;
                  if (schema instanceof ArraySchema) {
                    ArraySchema arraySchema = (ArraySchema) schema;
                    Schema<?> itemSchema = arraySchema.getItems();
                    if (itemSchema != null) {
                      ref = itemSchema.get$ref();
                      isArray = true;
                    }
                  } else {
                    ref = schema.get$ref();
                  }
                  Set<String> hiddenResourceNames = getHiddenResources(spec);
                  if (ref != null && ref.contains("/")) {
                    String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                    if (hiddenResourceNames.contains(schemaName)) {
                      return;
                    }
                    if (isArray) {
                      resources.add(
                          String.format("%s: import('chargebee').%s[];", schemaName, schemaName));
                    } else {
                      resources.add(
                          String.format("%s: import('chargebee').%s;", schemaName, schemaName));
                    }
                  }
                }));
      }
    }
    return resources;
  }

  public static List<FileOp> generate(
      String outputDirectoryPath,
      Spec spec,
      Template contentTemplate,
      Template handlerTemplate,
      Template authTemplate,
      Template eventTypesTemplate)
      throws IOException {
    final String webhookDirectoryPath = "/webhook";
    List<FileOp> fileOps = new ArrayList<>();
    // Ensure webhook directory exists
    fileOps.add(new FileOp.CreateDirectory(outputDirectoryPath, webhookDirectoryPath));

    // Include deprecated webhook events (like PCV1) since customers may still receive them
    var webhookInfo = spec.extractWebhookInfo(true);
    var eventSchema = spec.resourcesForEvents();

    if (webhookInfo.isEmpty()) {
      return fileOps;
    }

    List<Map<String, Object>> events = new ArrayList<>();
    Set<String> seenTypes = new HashSet<>();
    Set<String> uniqueImports = new HashSet<>();

    for (Map<String, String> info : webhookInfo) {
      String type = info.get("type");
      if (seenTypes.contains(type)) {
        continue;
      }
      seenTypes.add(type);

      String resourceSchemaName = info.get("resource_schema_name");
      Resource matchedSchema =
          eventSchema.stream()
              .filter(schema -> schema.name.equals(resourceSchemaName))
              .findFirst()
              .orElse(null);

      List<String> allSchemas = getEventResourcesForAEvent(matchedSchema, spec);
      List<String> schemaImports = new ArrayList<>();

      for (String schema : allSchemas) {
        schemaImports.add(schema);
        uniqueImports.add(schema);
      }

      Map<String, Object> params = new HashMap<>();
      params.put("type", type);
      params.put("resource_schemas", schemaImports);
      events.add(params);
    }

    events.sort(Comparator.comparing(e -> e.get("type").toString()));

    // content.ts
    {
      Map<String, Object> ctx = new HashMap<>();
      ctx.put("events", events);
      List<String> importsList = new ArrayList<>(uniqueImports);
      Collections.sort(importsList);
      ctx.put("unique_imports", importsList);

      fileOps.add(
          new FileOp.WriteString(
              outputDirectoryPath + webhookDirectoryPath,
              "content.ts",
              contentTemplate.apply(ctx)));
    }

    // handler.ts (static template)
    {
      fileOps.add(
          new FileOp.WriteString(
              outputDirectoryPath + webhookDirectoryPath, "handler.ts", handlerTemplate.apply("")));
    }

    // auth.ts
    {
      fileOps.add(
          new FileOp.WriteString(
              outputDirectoryPath + webhookDirectoryPath, "auth.ts", authTemplate.apply("")));
    }

    // eventType.ts
    {
      Map<String, Object> ctx = new HashMap<>();
      ctx.put("events", events);
      fileOps.add(
          new FileOp.WriteString(
              outputDirectoryPath + webhookDirectoryPath,
              "eventType.ts",
              eventTypesTemplate.apply(ctx)));
    }

    return fileOps;
  }

  public static Set<String> getHiddenResources(Spec spec) {
    return spec.allResources().stream()
        .filter((res) -> !res.isNotHiddenFromSDKGeneration())
        .map((res) -> res.name)
        .collect(Collectors.toSet());
  }
}
