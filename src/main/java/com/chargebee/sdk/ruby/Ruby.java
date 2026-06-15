package com.chargebee.sdk.ruby;

import static com.chargebee.GenUtil.firstCharLower;
import static com.chargebee.GenUtil.normalizeToLowerCamelCase;
import static com.chargebee.GenUtil.toCamelCase;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Ruby extends Language {
  protected final String[] hiddenOverride = {"media", "business_entity_change"};

  @Override
  public List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    String modelsDirectoryPath = "/models";
    var resources =
        spec.resources().stream()
            .filter(resource -> !Arrays.stream(this.hiddenOverride).toList().contains(resource.id))
            .toList();
    var exceptionsResources = spec.errorResources();
    var createModelsDirectory =
        new FileOp.CreateDirectory(outputDirectoryPath, modelsDirectoryPath);
    List<FileOp> fileOps = new ArrayList<>();

    fileOps.add(createModelsDirectory);
    fileOps.addAll(generateResourceFiles(outputDirectoryPath + modelsDirectoryPath, resources));
    fileOps.add(generateResultFile(outputDirectoryPath, resources));
    fileOps.addAll(generateTelemetryFiles(outputDirectoryPath));
    //    fileOps.add(generateExeptionFile(outputDirectoryPath, exceptionsResources));

    return fileOps;
  }

  private List<FileOp> generateTelemetryFiles(String outputDirectoryPath) throws IOException {
    final String telemetryDir = outputDirectoryPath + "/telemetry";
    final String[] telemetryFiles = {
      "telemetry_attribute_keys.rb",
      "request_telemetry_context.rb",
      "request_telemetry_error.rb",
      "request_telemetry_result.rb",
      "telemetry_adapter.rb",
      "telemetry_support.rb"
    };
    final String[] templateKeys = {
      "telemetryAttributeKeys",
      "telemetryRequestContext",
      "telemetryRequestError",
      "telemetryRequestResult",
      "telemetryAdapter",
      "telemetrySupport"
    };

    List<FileOp> fileOps = new ArrayList<>();
    fileOps.add(new FileOp.CreateDirectory(telemetryDir, ""));

    for (int i = 0; i < telemetryFiles.length; i++) {
      Template template = getTemplateContent(templateKeys[i]);
      fileOps.add(new FileOp.WriteString(telemetryDir, telemetryFiles[i], template.apply("")));
    }

    return fileOps;
  }

  private FileOp generateExeptionFile(
      String outputDirectoryPath, List<com.chargebee.openapi.Error> errorsResources)
      throws IOException {
    Template exceptionTemplate = getTemplateContent("errors");
    var templateParams = errorSchemas(errorsResources);
    return new FileOp.WriteString(
        outputDirectoryPath, "errors.rb", exceptionTemplate.apply(templateParams));
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.ofEntries(
        Map.entry("models.resource", "/templates/ruby/models.resource.rb.hbs"),
        Map.entry("result", "/templates/ruby/result.rb.hbs"),
        Map.entry("errors", "/templates/ruby/errors.rb.hbs"),
        Map.entry("telemetryAttributeKeys", "/templates/ruby/telemetry/telemetry_attribute_keys.rb.hbs"),
        Map.entry("telemetryRequestContext", "/templates/ruby/telemetry/request_telemetry_context.rb.hbs"),
        Map.entry("telemetryRequestError", "/templates/ruby/telemetry/request_telemetry_error.rb.hbs"),
        Map.entry("telemetryRequestResult", "/templates/ruby/telemetry/request_telemetry_result.rb.hbs"),
        Map.entry("telemetryAdapter", "/templates/ruby/telemetry/telemetry_adapter.rb.hbs"),
        Map.entry("telemetrySupport", "/templates/ruby/telemetry/telemetry_support.rb.hbs"));
  }

  private List<FileOp> generateResourceFiles(String outDirectoryPath, List<Resource> resources)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();

    Template resourceTemplate = getTemplateContent("models.resource");
    for (var resource : resources) {
      var content = resourceTemplate.apply(enrichResourceParams(resource));
      String fileName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, resource.name);
      fileOps.add(new FileOp.WriteString(outDirectoryPath, fileName + ".rb", content));
    }
    return fileOps;
  }

  private Map<String, Object> enrichResourceParams(Resource resource) {
    Map<String, Object> params = new HashMap<>(resource.templateParams(this));
    params.put("telemetryResource", normalizeToLowerCamelCase(resource.id));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> actions = (List<Map<String, Object>>) params.get("actions");
    if (actions != null) {
      List<Map<String, Object>> enrichedActions = new ArrayList<>();
      for (Map<String, Object> action : actions) {
        Map<String, Object> enrichedAction = new HashMap<>(action);
        enrichedAction.put(
            "telemetryOperation", firstCharLower(toCamelCase((String) action.get("name"))));
        enrichedActions.add(enrichedAction);
      }
      params.put("actions", enrichedActions);
    }

    return params;
  }

  private FileOp generateResultFile(String outputDirectory, List<Resource> resources)
      throws IOException {
    Map<String, Object> templateParams = resourceResponses(resources);
    Template resultTemplate = getTemplateContent("result");
    return new FileOp.WriteString(
        outputDirectory, "result.rb", resultTemplate.apply(templateParams));
  }

  @Override
  public Map<String, Object> additionalTemplateParams(Resource resource) {
    return Map.of(
        "attributesAsCommaSeparatedText",
        resource.attributes().stream()
            .filter(Attribute::isNotHiddenAttribute)
            .filter(
                attr ->
                    !("content".equals(attr.name)
                        && ("Event".equals(resource.name) || "HostedPage".equals(resource.name))))
            .sorted(Comparator.comparing(Attribute::sortOrder))
            .map(attr -> ":" + attr.name)
            .collect(Collectors.joining(",")));
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
