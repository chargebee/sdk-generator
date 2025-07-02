package com.chargebee.sdk.php;

import com.chargebee.handlebar.Inflector;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

public class Php extends Language {

  @Override
  protected List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    String modelsDirectoryPath = "/ChargeBee/Models";
    var createModelsDirectory =
        new FileOp.CreateDirectory(outputDirectoryPath, modelsDirectoryPath);
    List<FileOp> fileOps = new ArrayList<>(List.of(createModelsDirectory));

    var resources =
        spec.resources().stream()
            .filter(resource -> !Arrays.stream(this.hiddenOverride).toList().contains(resource.id))
            .toList();
    List<FileOp> generateResourceFiles =
        generateResourceFiles(outputDirectoryPath + modelsDirectoryPath, resources);
    fileOps.addAll(generateResourceFiles);
    fileOps.add(generateResultFile(outputDirectoryPath + "/ChargeBee", resources));
    fileOps.add(generateInitFile(outputDirectoryPath, resources));

    return fileOps;
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of(
        "models",
        "/templates/php/models.php.hbs",
        "init",
        "/templates/php/init.php.hbs",
        "result",
        "/templates/php/result.php.hbs");
  }

  private List<FileOp> generateResourceFiles(
      String resourcesDirectoryPath, List<Resource> resources) throws IOException {
    List<FileOp> fileOps = new ArrayList<>();
    Template resourceTemplate = getTemplateContent("models");

    for (var resource : resources) {
      Map<String, Object> content = new HashMap<>(resource.templateParams(this));
      content.put("isResourceFile", true);
      fileOps.add(
          new FileOp.WriteString(
              resourcesDirectoryPath, resource.name + ".php", resourceTemplate.apply(content)));

      for (var subResource : resource.subResources()) {
        Map<String, Object> subContent = new HashMap<>(subResource.templateParams(this));
        subContent.put("name", resource.name + Inflector.singularize(subResource.name));
        fileOps.add(
            new FileOp.WriteString(
                resourcesDirectoryPath,
                resource.name + Inflector.singularize(subResource.name) + ".php",
                resourceTemplate.apply(subContent)));
      }
    }
    return fileOps;
  }

  private FileOp generateResultFile(String outputDirectoryPath, List<Resource> resources)
      throws IOException {
    Map<String, Object> templateParams = resourceResponses(resources);
    Template resultTemplate = getTemplateContent("result");
    return new FileOp.WriteString(
        outputDirectoryPath, "Result.php", resultTemplate.apply(templateParams));
  }

  private FileOp generateInitFile(String outputDirectoryPath, List<Resource> resources)
      throws IOException {
    List<String> resourceNames =
        resources.stream()
            .flatMap(
                resource ->
                    Stream.concat(
                        Stream.of(resource.name),
                        resource.subResources().stream()
                            .map(
                                subResource ->
                                    resource.name + Inflector.singularize(subResource.name))))
            .sorted()
            .toList();
    Map templateParams = Map.of("resourceNames", resourceNames);
    Template initTemplate = getTemplateContent("init");

    return new FileOp.WriteString(
        outputDirectoryPath, "init.php", initTemplate.apply(templateParams));
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
