package com.chargebee.sdk.python;

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

public class Python extends Language {
  protected final String[] hiddenOverride = {"media"};

  @Override
  public List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    String modelsDirectoryPath = "/models";
    var resources =
        spec.resources().stream()
            .filter(resource -> !Arrays.stream(this.hiddenOverride).toList().contains(resource.id))
            .toList();
    var createModelsDirectory =
        new FileOp.CreateDirectory(outputDirectoryPath, modelsDirectoryPath);
    var exceptionsResources = spec.errorResources();

    List<FileOp> fileOps = new ArrayList<>();

    fileOps.add(createModelsDirectory);
    fileOps.add(generateModelsInitFile(outputDirectoryPath + modelsDirectoryPath, resources));
    fileOps.addAll(generateResourceFiles(outputDirectoryPath + modelsDirectoryPath, resources));
    fileOps.add(generateResultFile(outputDirectoryPath, resources));
    //    fileOps.add(generateExeptionFile(outputDirectoryPath, exceptionsResources));

    return fileOps;
  }

  private FileOp generateExeptionFile(
      String outputDirectoryPath, List<com.chargebee.openapi.Error> errorsResources)
      throws IOException {
    Template exceptionTemplate = getTemplateContent("api_errors");
    var templateParams = errorSchemas(errorsResources);
    return new FileOp.WriteString(
        outputDirectoryPath, "api_error.py", exceptionTemplate.apply(templateParams));
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of(
        "models.init",
        "/templates/python/models.init.py.hbs",
        "models.resource",
        "/templates/python/models.resource.py.hbs",
        "result",
        "/templates/python/result.py.hbs",
        "api_errors",
        "/templates/python/api_errors.py.hbs");
  }

  private FileOp generateModelsInitFile(String outputDirectoryPath, List<Resource> resources)
      throws IOException {
    List<Map<String, Object>> resourceList =
        resources.stream().map(r -> r.templateParams(this)).toList();

    Template initTemplate = getTemplateContent("models.init");
    Map<String, List<Map<String, Object>>> templateParams = Map.of("resources", resourceList);

    return new FileOp.WriteString(
        outputDirectoryPath, "__init__.py", initTemplate.apply(templateParams));
  }

  private List<FileOp> generateResourceFiles(String outDirectoryPath, List<Resource> resources)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();

    Template resourceTemplate = getTemplateContent("models.resource");
    for (var resource : resources) {
      var content = resourceTemplate.apply(resource.templateParams(this));
      String fileName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, resource.name);
      fileOps.add(new FileOp.WriteString(outDirectoryPath, fileName + ".py", content));
    }
    return fileOps;
  }

  private FileOp generateResultFile(String outputDirectory, List<Resource> resources)
      throws IOException {
    Map<String, Object> templateParams = resourceResponses(resources);
    Template resultTemplate = getTemplateContent("result");
    return new FileOp.WriteString(
        outputDirectory, "result.py", resultTemplate.apply(templateParams));
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
            .map(attr -> "\"" + attr.name + "\"")
            .collect(Collectors.joining(",")));
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
