package com.chargebee.sdk.node;

import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.node.webhook.WebhookGenerator;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.util.*;

public class NodeV3 extends Language {
  protected final String[] hiddenOverride = {"media"};

  @Override
  protected List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    var resources =
        spec.resources().stream()
            .filter(Resource::isNotDependentResource)
            .filter(resource -> !Arrays.stream(this.hiddenOverride).toList().contains(resource.id))
            .sorted(Comparator.comparing(Resource::sortOrder))
            .toList();
    List<FileOp> fileOps = new ArrayList<>();
    fileOps.add(generateApiEndpointsFile(outputDirectoryPath, resources));
    
    // Generate webhook files (event types, content, handler)
    {
        Template eventTypesTemplate = getTemplateContent("webhookEventTypes");
        Template contentTemplate = getTemplateContent("webhookContent");
        Template handlerTemplate = getTemplateContent("webhookHandler");
        Template authTemplate = getTemplateContent("webhookAuth");
        fileOps.addAll(
            WebhookGenerator.generate(
                outputDirectoryPath, 
                spec, 
                eventTypesTemplate, 
                contentTemplate, 
                handlerTemplate,
                authTemplate
            )
        );
    }
    
    return fileOps;
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of(
        "api_endpoints", "/templates/node/api_endpoints.ts.hbs",
        "webhookEventTypes", "/templates/node/webhook_event_types.ts.hbs",
        "webhookContent", "/templates/node/webhook_content.ts.hbs",
        "webhookHandler", "/templates/node/webhook_handler.ts.hbs",
        "webhookAuth", "/templates/node/webhook_auth.ts.hbs"
    );
  }

  private FileOp generateApiEndpointsFile(String resourcesDirectoryPath, List<Resource> resources)
      throws IOException {
    List<Map<String, Object>> resourcesMap =
        resources.stream().map(resource -> resource.templateParams(this)).toList();
    Map<String, Object> templateParams = Map.of("resources", resourcesMap);
    Template resourceTemplate = getTemplateContent("api_endpoints");
    return new FileOp.WriteString(
        resourcesDirectoryPath, "api_endpoints.ts", resourceTemplate.apply(templateParams));
  }
}
