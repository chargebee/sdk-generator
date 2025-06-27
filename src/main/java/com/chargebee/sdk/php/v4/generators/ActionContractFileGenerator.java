package com.chargebee.sdk.php.v4.generators;

import static com.chargebee.sdk.php.v4.Constants.*;

import com.chargebee.openapi.Resource;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.php.v4.ActionContractParser;
import com.chargebee.sdk.php.v4.PHP_V4;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ActionContractFileGenerator implements FileGenerator {
  private final PHP_V4 phpGenerator;
  private final Template actionTemplate;

  public ActionContractFileGenerator(PHP_V4 phpGenerator) {
    this.phpGenerator = phpGenerator;
    this.actionTemplate = phpGenerator.getTemplateContent(ACTION_CONTRACT);
  }

  @Override
  public List<FileOp> generate(String outputPath, List<?> items) throws IOException {
    List<Resource> resources = (List<Resource>) items;

    return resources.stream()
        .filter(Resource::isNotHiddenFromSDKGeneration)
        .map(resource -> generateActionContractFile(outputPath, resource))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private FileOp generateActionContractFile(String outputPath, Resource resource) {
    var actions = ActionContractParser.getActionsContractsForAction(resource);
    if (actions.getActions().isEmpty()) {
      return null;
    }

    try {
      String content =
          actionTemplate.apply(phpGenerator.getObjectMapper().convertValue(actions, Map.class));

      return new FileOp.WriteString(
          outputPath, resource.name + ACTIONS + INTERFACE + PHP_FILE_NAME_EXTENSION, content);
    } catch (IOException e) {
      throw new RuntimeException("Failed to generate action file for " + resource.name, e);
    }
  }
}
