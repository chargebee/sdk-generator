package com.chargebee.sdk.go.webhook;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import java.io.IOException;
import java.util.*;

public class WebhookGenerator {

  private static List<String> getEventResourcesForAEvent(Resource eventResource) {
    List<String> resources = new ArrayList<>();
    if (eventResource != null) {
      for (Attribute attribute : eventResource.attributes()) {
        if (attribute.name.equals("content")) {
          attribute
              .attributes()
              .forEach(
                  (innerAttribute -> {
                    String ref = innerAttribute.schema.get$ref();
                    if (ref != null && ref.contains("/")) {
                      String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
                      resources.add(schemaName);
                    }
                  }));
        }
      }
    }
    return resources;
  }

  public static List<FileOp> generate(
      String outputDirectoryPath,
      Spec spec,
      Template parserTemplate,
      Template contentTemplate,
      Template handlerTemplate)
      throws IOException {
    final String webhookDirectoryPath = "/webhook";
    List<FileOp> fileOps = new ArrayList<>();
    // Ensure webhook directory exists
    fileOps.add(new FileOp.CreateDirectory(outputDirectoryPath, webhookDirectoryPath));

    var webhookInfo = spec.extractWebhookInfo();
    var eventSchema = spec.resourcesForEvents();

    if (webhookInfo.isEmpty()) {
      return fileOps;
    }

    // parser.go
    fileOps.add(
        new FileOp.WriteString(
            outputDirectoryPath + webhookDirectoryPath, "parser.go", parserTemplate.apply("")));

    // content.go (only if event schema is provided)
    if (!eventSchema.isEmpty()) {
      List<Map<String, Object>> events = new ArrayList<>();
      Set<String> seenTypes = new HashSet<>();

      // Compute models directory by taking parent of webhook output dir
      java.io.File webhookDir = new java.io.File(outputDirectoryPath + webhookDirectoryPath);
      java.io.File chargebeeRoot = webhookDir.getParentFile();
      java.io.File modelsDir = new java.io.File(chargebeeRoot, "models");

      // Collect unique imports across all events
      Set<String> uniqueImports = new HashSet<>();

      for (Map<String, String> info : webhookInfo) {
        String type = info.get("type");
        if (seenTypes.contains(type)) {
          continue; // skip duplicate type
        }
        seenTypes.add(type);

        String resourceSchemaName = info.get("resource_schema_name");
        Resource matchedSchema =
            eventSchema.stream()
                .filter(schema -> schema.name.equals(resourceSchemaName))
                .findFirst()
                .orElse(null);

        List<String> allSchemas = getEventResourcesForAEvent(matchedSchema);

        // Filter schemas by presence of model directory to avoid missing imports
        List<String> filteredSchemas = new ArrayList<>();
        for (String schemaName : allSchemas) {
          // Convert to models directory name: lower_underscore then remove underscores
          String snake =
              CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, schemaName);
          String folder = snake.replace("_", "");
          java.io.File schemaDir = new java.io.File(modelsDir, folder);
          if (schemaDir.exists() && schemaDir.isDirectory()) {
            filteredSchemas.add(schemaName);
            uniqueImports.add(schemaName);
          }
        }

        Map<String, Object> params = new HashMap<>();
        params.put("type", type);
        params.put("resource_schemas", allSchemas);
        params.put("filtered_resource_schemas", filteredSchemas);
        events.add(params);
      }

      Map<String, Object> ctx = new HashMap<>();
      ctx.put("events", events);
      List<String> importsList = new ArrayList<>(uniqueImports);
      java.util.Collections.sort(importsList);
      ctx.put("unique_imports", importsList);

      fileOps.add(
          new FileOp.WriteString(
              outputDirectoryPath + webhookDirectoryPath,
              "content.go",
              contentTemplate.apply(ctx)));
    }

    // handler.go (always, using webhook types list)
    {
      List<Map<String, Object>> events = new ArrayList<>();
      Set<String> seenTypes = new HashSet<>();
      for (Map<String, String> info : webhookInfo) {
        String type = info.get("type");
        if (seenTypes.contains(type)) {
          continue;
        }
        seenTypes.add(type);
        Map<String, Object> params = new HashMap<>();
        params.put("type", type);
        events.add(params);
      }
      events.sort(Comparator.comparing(e -> e.get("type").toString()));
      Map<String, Object> ctx = new HashMap<>();
      ctx.put("events", events);
      fileOps.add(
          new FileOp.WriteString(
              outputDirectoryPath + webhookDirectoryPath,
              "handler.go",
              handlerTemplate.apply(ctx)));
    }

    return fileOps;
  }
}


