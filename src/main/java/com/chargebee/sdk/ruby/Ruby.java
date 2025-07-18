package com.chargebee.sdk.ruby;

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
    var createModelsDirectory =
        new FileOp.CreateDirectory(outputDirectoryPath, modelsDirectoryPath);
    List<FileOp> fileOps = new ArrayList<>();

    fileOps.add(createModelsDirectory);
    fileOps.addAll(generateResourceFiles(outputDirectoryPath + modelsDirectoryPath, resources));
    fileOps.add(generateResultFile(outputDirectoryPath, resources));

    return fileOps;
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of(
        "models.resource",
        "/templates/ruby/models.resource.rb.hbs",
        "result",
        "/templates/ruby/result.rb.hbs");
  }

  private List<FileOp> generateResourceFiles(String outDirectoryPath, List<Resource> resources)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();

    Template resourceTemplate = getTemplateContent("models.resource");
    for (var resource : resources) {
      var content = resourceTemplate.apply(resource.templateParams(this));
      String fileName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, resource.name);
      fileOps.add(new FileOp.WriteString(outDirectoryPath, fileName + ".rb", content));
    }
    return fileOps;
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
