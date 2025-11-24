package com.chargebee.sdk.node.webhook;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.github.jknack.handlebars.Template;
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
      Template eventTypesTemplate,
      Template contentTemplate,
      Template handlerTemplate,
      Template authTemplate)
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

    // Compute models directory by taking parent of webhook output dir
    java.io.File webhookDir = new java.io.File(outputDirectoryPath + webhookDirectoryPath);
    java.io.File chargebeeRoot = webhookDir.getParentFile();

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

        List<String> allSchemas = getEventResourcesForAEvent(matchedSchema);
        List<String> schemaImports = new ArrayList<>();

        for(String schema : allSchemas) {
             // In Node we import Resource classes/interfaces. 
             // Assuming 'Customer' -> 'Customer' in types
             schemaImports.add(schema);
             uniqueImports.add(schema);
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("type", type);
        params.put("resource_schemas", schemaImports);
        events.add(params);
    }
    
    events.sort(Comparator.comparing(e -> e.get("type").toString()));

    // event_types.ts
    {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("events", events);
        fileOps.add(
            new FileOp.WriteString(
                outputDirectoryPath + webhookDirectoryPath, 
                "event_types.ts", 
                eventTypesTemplate.apply(ctx)
            )
        );
    }

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
                contentTemplate.apply(ctx)
            )
        );
    }

    // handler.ts
    {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("events", events);
        fileOps.add(
          new FileOp.WriteString(
              outputDirectoryPath + webhookDirectoryPath,
              "handler.ts",
              handlerTemplate.apply(ctx)
          )
        );
    }

    // auth.ts
    {
        fileOps.add(
          new FileOp.WriteString(
              outputDirectoryPath + webhookDirectoryPath,
              "auth.ts",
              authTemplate.apply("")
          )
        );
    }

    return fileOps;
  }
}

