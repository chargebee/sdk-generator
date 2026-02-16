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

    // Generate webhook files (content, handler, auth, eventType, errors)
    {
      Template contentTemplate = getTemplateContent("webhookContent");
      Template handlerTemplate = getTemplateContent("webhookHandler");
      Template authTemplate = getTemplateContent("webhookAuth");
      Template eventTypesTemplate = getTemplateContent("webhookEventTypes");
      Template errorsTemplate = getTemplateContent("webhookErrors");
      fileOps.addAll(
          WebhookGenerator.generate(
              outputDirectoryPath,
              spec,
              contentTemplate,
              handlerTemplate,
              authTemplate,
              eventTypesTemplate,
              errorsTemplate));
    }

    // Generate entry point files (in parent directory of resources)
    {
      String parentDirectoryPath = outputDirectoryPath.replace("/resources", "");
      Template esmTemplate = getTemplateContent("chargebeeEsm");
      Template cjsTemplate = getTemplateContent("chargebeeCjs");
      fileOps.add(
          new FileOp.WriteString(parentDirectoryPath, "chargebee.esm.ts", esmTemplate.apply("")));
      fileOps.add(
          new FileOp.WriteString(parentDirectoryPath, "chargebee.cjs.ts", cjsTemplate.apply("")));
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
    templates.put("webhookErrors", "/templates/node/webhook_errors.ts.hbs");
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

  private List<FileOp> generateWebhookEventTypes(String outputDirectoryPath, Spec spec)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();
    final String webhookDirectoryPath = "/webhook";

    // Ensure webhook directory exists
    fileOps.add(new FileOp.CreateDirectory(outputDirectoryPath, webhookDirectoryPath));

    // Include deprecated webhook events (like PCV1) since customers may still receive them
    var webhookInfo = spec.extractWebhookInfo(true);

    if (webhookInfo.isEmpty()) {
      return fileOps;
    }

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

    // eventType.ts
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("events", events);
    Template eventTypesTemplate = getTemplateContent("webhookEventTypes");
    fileOps.add(
        new FileOp.WriteString(
            outputDirectoryPath + webhookDirectoryPath,
            "eventType.ts",
            eventTypesTemplate.apply(ctx)));

    return fileOps;
  }

  private List<FileOp> generateEntryPoints(String parentDirectoryPath) throws IOException {
    List<FileOp> fileOps = new ArrayList<>();

    Template esmTemplate = getTemplateContent("chargebeeEsm");
    Template cjsTemplate = getTemplateContent("chargebeeCjs");

    fileOps.add(
        new FileOp.WriteString(parentDirectoryPath, "chargebee.esm.ts", esmTemplate.apply("")));
    fileOps.add(
        new FileOp.WriteString(parentDirectoryPath, "chargebee.cjs.ts", cjsTemplate.apply("")));

    return fileOps;
  }
}
