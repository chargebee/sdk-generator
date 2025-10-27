package com.chargebee.sdk.php.v4.generators;

import static com.chargebee.GenUtil.toCamelCase;
import static com.chargebee.sdk.php.v4.Constants.*;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Resource;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.php.v4.Constants;
import com.chargebee.sdk.php.v4.ListResponseParser;
import com.chargebee.sdk.php.v4.PHP_V4;
import com.chargebee.sdk.php.v4.ResponseParser;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.util.*;

public class ResponseFileGenerator implements FileGenerator {
  private final PHP_V4 phpGenerator;
  private final Template responseTemplate;
  private final Template listResponseTemplate;
  private final Template listResponseObjectTemplate;

  public ResponseFileGenerator(PHP_V4 phpGenerator) {
    this.phpGenerator = phpGenerator;
    this.responseTemplate = phpGenerator.getTemplateContent(Constants.RESPONSE);
    this.listResponseTemplate = phpGenerator.getTemplateContent(LIST_RESPONSE);
    this.listResponseObjectTemplate = phpGenerator.getTemplateContent(LIST_RESPONSE_OBJECT);
  }

  @Override
  public List<FileOp> generate(String outputPath, List<?> items) throws IOException {
    List<Resource> resources = (List<Resource>) items;
    List<FileOp> fileOps = new ArrayList<>();

    fileOps.addAll(generateRegularResponses(outputPath, resources));
    fileOps.addAll(generateListResponses(outputPath, resources));

    return fileOps;
  }

  private boolean isPostActionWithListResponse(Action action) {
    if (action.getOnSuccessSchema() == null) {
      return false;
    }
    var schema = action.getOnSuccessSchema();
    boolean isPostActionWithListResponse =
        schema.get$ref() == null
            && schema.getProperties() != null
            && schema.getProperties().size() == 1
            && schema.getProperties().containsKey("list");

    return isPostActionWithListResponse;
  }

  private List<FileOp> generateRegularResponses(String outputPath, List<Resource> resources)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();

    for (Resource resource : resources) {
      if (!resource.isNotHiddenFromSDKGeneration()) continue;

      for (Action action : resource.actions) {
        if (!action.isNotHiddenFromSDK() || action.isListResourceAction()) continue;
        if (isPostActionWithListResponse(action)) continue;

        var responseResource = ResponseParser.actionResponses(action, resource);
        if (responseResource == null) continue;

        String directoryName = toCamelCase(resource.name) + RESPONSE_DIR_SUFFIX;
        fileOps.addAll(
            generateResponseFile(outputPath, directoryName, responseResource, responseTemplate));
      }
    }

    return fileOps;
  }

  private List<FileOp> generateListResponses(String outputPath, List<Resource> resources)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();

    for (Resource resource : resources) {
      for (Action action : resource.actions) {
        if (!action.isNotHiddenFromSDK()) continue;
        if (!action.isListResourceAction() && !isPostActionWithListResponse(action)) continue;

        var responseResource = ListResponseParser.actionResponses(action, resource);
        if (responseResource == null) continue;

        String directoryName = toCamelCase(resource.name) + RESPONSE_DIR_SUFFIX;
        fileOps.addAll(
            generateResponseFile(
                outputPath, directoryName, responseResource, listResponseTemplate));
        fileOps.addAll(
            (generateListResponseObject(
                outputPath, directoryName, responseResource, listResponseObjectTemplate)));
      }
    }

    return fileOps;
  }

  private List<FileOp> generateResponseFile(
      String basePath,
      String directoryName,
      com.chargebee.sdk.php.v4.models.Resource resource,
      Template template)
      throws IOException {

    String content =
        template.apply(phpGenerator.getObjectMapper().convertValue(resource, Map.class));

    return Arrays.asList(
        new FileOp.CreateDirectory(basePath, directoryName),
        new FileOp.WriteString(
            basePath + "/" + directoryName,
            resource.getClazName() + PHP_FILE_NAME_EXTENSION,
            content));
  }

  private List<FileOp> generateListResponseObject(
      String basePath,
      String directoryName,
      com.chargebee.sdk.php.v4.models.Resource resource,
      Template template)
      throws IOException {

    String content =
        template.apply(phpGenerator.getObjectMapper().convertValue(resource, Map.class));

    return Arrays.asList(
        new FileOp.CreateDirectory(basePath, directoryName),
        new FileOp.WriteString(
            basePath + "/" + directoryName,
            resource.getClazName() + "ListObject" + PHP_FILE_NAME_EXTENSION,
            content));
  }
}
