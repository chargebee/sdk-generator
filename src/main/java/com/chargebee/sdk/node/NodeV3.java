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
    
    // Generate webhook files (content, handler, auth, eventType)
    {
        Template contentTemplate = getTemplateContent("webhookContent");
        Template handlerTemplate = getTemplateContent("webhookHandler");
        Template authTemplate = getTemplateContent("webhookAuth");
        Template eventTypesTemplate = getTemplateContent("webhookEventTypes");
        fileOps.addAll(
            WebhookGenerator.generate(
                outputDirectoryPath, 
                spec, 
                contentTemplate, 
                handlerTemplate,
                authTemplate,
                eventTypesTemplate
            )
        );
    }
    
    // Generate entry point files (in parent directory of resources)
    {
        String parentDirectoryPath = outputDirectoryPath.replace("/resources", "");
        Template esmTemplate = getTemplateContent("chargebeeEsm");
        Template cjsTemplate = getTemplateContent("chargebeeCjs");
        fileOps.add(
            new FileOp.WriteString(
                parentDirectoryPath,
                "chargebee.esm.ts",
                esmTemplate.apply("")
            )
        );
        fileOps.add(
            new FileOp.WriteString(
                parentDirectoryPath,
                "chargebee.cjs.ts",
                cjsTemplate.apply("")
            )
        );
    }
    
    return fileOps;
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    var templates = new HashMap<String, String>();
    templates.put("api_endpoints", "/templates/node/api_endpoints.ts.hbs");
    templates.put("webhookContent", "/templates/node/webhook_content.ts.hbs");
    templates.put("webhookHandler", "/templates/node/webhook_handler.ts.hbs");
    templates.put("webhookAuth", "/templates/node/webhook_auth.ts.hbs");
    templates.put("webhookEventTypes", "/templates/node/webhook_event_types.ts.hbs");
    templates.put("chargebeeEsm", "/templates/node/chargebee_esm.ts.hbs");
    templates.put("chargebeeCjs", "/templates/node/chargebee_cjs.ts.hbs");
    return templates;
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
