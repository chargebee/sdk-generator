package com.chargebee.sdk;

import com.chargebee.ApiVersionHandler;
import com.chargebee.QAModeHandler;
import com.chargebee.handlebar.*;
import com.chargebee.openapi.ApiVersion;
import com.chargebee.openapi.Error;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.openapi.parameter.Response;
import com.chargebee.sdk.responseHelper.ResponseHelper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.helper.StringHelpers;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class Language implements DataType {
  protected final String[] hiddenOverride = {"media", "business_entity_change", "non_subscription"};
  private final Map<String, Template> templateContents;

  protected Language() {
    templateContents = new HashMap<>();
  }

  public Template getTemplateContent(String templateId) {
    return templateContents.get(templateId);
  }

  private void initialise() throws IOException {
    var handlebars = new Handlebars();
    handlebars.registerHelper("tokenBasedSplitTextToLines", new TokenBasedSplitTextToLinesHelper());
    handlebars.registerHelper("neq", ConditionalHelpers.neq);
    handlebars.registerHelper("eq", ConditionalHelpers.eq);
    handlebars.registerHelper("upper", StringHelpers.upper);
    handlebars.registerHelper("lower", StringHelpers.lower);
    handlebars.registerHelper("and", ConditionalHelpers.and);
    handlebars.registerHelper("or", ConditionalHelpers.or);
    handlebars.registerHelper("not", ConditionalHelpers.not);
    handlebars.registerHelper("camelCaseToPascalCase", NameFormatHelpers.CAMEL_CASE_TO_PASCAL_CASE);
    handlebars.registerHelper("camelCaseToSnakeCase", NameFormatHelpers.CAMEL_CASE_TO_SNAKE_CASE);
    handlebars.registerHelper("snakeCaseToCamelCase", NameFormatHelpers.SNAKE_CASE_TO_CAMEL_CASE);
    handlebars.registerHelper("pascalCaseToSnakeCase", NameFormatHelpers.PASCAL_CASE_TO_SNAKE_CASE);
    handlebars.registerHelper("pascalCaseToCamelCase", NameFormatHelpers.PASCAL_CASE_TO_CAMEL_CASE);
    handlebars.registerHelper("snakeCaseToPascalCase", NameFormatHelpers.SNAKE_CASE_TO_PASCAL_CASE);
    handlebars.registerHelper("golangCase", NameFormatHelpers.PASCAL_CASE_TO_GO_CASE);
    handlebars.registerHelper("toUpperCase", NameFormatHelpers.TO_UPPER_CASE);
    handlebars.registerHelper("camelCase", NameFormatHelpers.CAMEL_CASE);
    handlebars.registerHelper("pascalCase", NameFormatHelpers.TO_PASCAL);
    handlebars.registerHelper(
        "operationNameToPascalCase", NameFormatHelpers.OPERATION_NAME_TO_PASCAL_CASE);
    handlebars.registerHelper("constantCase", NameFormatHelpers.CONSTANT_CASE);

    handlebars.registerHelper(
        "snakeCaseToPascalCaseAndSingularize",
        NameFormatHelpers.SNAKE_CASE_TO_PASCAL_CASE_AND_SINGULARIZE);
    handlebars.registerHelper(
        "snakeCaseToCamelCaseAndSingularize",
        NameFormatHelpers.SNAKE_CASE_TO_CAMEL_CASE_AND_SINGULARIZE);
    handlebars.registerHelper(
        "pascalCaseToCamelCaseAndPluralize",
        NameFormatHelpers.PASCAL_CASE_TO_CAMEL_CASE_AND_PLURALIZE);
    handlebars.registerHelper(
        "pascalCaseToSnakeCaseAndPluralize",
        NameFormatHelpers.PASCAL_CASE_TO_SNAKE_CASE_AND_PLURALIZE);
    handlebars.registerHelper("pluralize", NameFormatHelpers.PLURALIZE);
    handlebars.registerHelper("singularize", NameFormatHelpers.SINGULARIZE);
    handlebars.registerHelper("in", ArrayHelpers.IN);
    handlebars.registerHelper("curly", SpecialCharacters.CURLY_BRACKETS);
    handlebars.registerHelper("backslash", SpecialCharacters.BACK_SLASH);
    handlebars.registerHelper("includeFile", IncludeFileHelpers.INCLUDE_FILE);
    loadTemplates(handlebars);
    initialiseQAHandler();
  }

  private void loadTemplates(Handlebars handlebars) throws IOException {
    templateContents.clear();
    for (var entry : templatesDefinition().entrySet()) {
      templateContents.put(
          entry.getKey(), handlebars.compileInline(readResourceFileContent(entry.getValue())));
    }
  }

  private String readResourceFileContent(String filePath) throws IOException {
    var inputStream = Language.class.getResourceAsStream(filePath);
    if (inputStream == null) {
      throw new IllegalArgumentException("Resource file " + filePath + " not found");
    }
    return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
  }

  public List<FileOp> generate(String outputDirectoryPath, Spec spec) throws IOException {
    initialise();
    return generateSDK(outputDirectoryPath, spec);
  }

  protected Map<String, Object> resourceResponses(List<Resource> resources) {
    List<Map<String, Object>> responseMap =
        resources.stream()
            .filter(r -> r.isNotDependentResource() && r.isNotThirdPartyResource())
            .sorted(Comparator.comparing(Resource::sortOrder))
            .map(r -> r.templateParams(this))
            .toList();

    ResponseHelper responseHelper = new ResponseHelper(resources);
    var resourceResponseList = listResponses(responseHelper);

    List<Map<String, Object>> listResourceMap =
        responseMap.stream()
            .filter(r -> responseHelper.isListResponse(resourceResponseList, r))
            .toList();

    List<String> jsonResponses = responseHelper.jsonResponse(this);
    return Map.of(
        "responses",
        responseMap,
        "listResponses",
        listResourceMap,
        "jsonResponses",
        jsonResponses,
        "isApiV1",
        QAModeHandler.getInstance().getValue()
            && ApiVersionHandler.getInstance().getValue().equals(ApiVersion.V1),
        "isApiV2",
        QAModeHandler.getInstance().getValue()
            && ApiVersionHandler.getInstance().getValue().equals(ApiVersion.V2));
  }

  protected List<Map<String, Object>> errorSchemas(List<Error> errorList) {
    List<Map<String, Object>> errorSchemas =
        errorList.stream()
            .filter(r -> !r.name.matches("\\d+"))
            .map(r -> r.templateParams(this))
            .toList();
    return errorSchemas;
  }

  protected List<Map<String, Object>> listResponses(ResponseHelper responseHelper) {

    return responseHelper.listResponses().stream()
        .map(response -> response.templateParams(this))
        .filter(ResponseHelper::isTypeDefined)
        .toList()
        .stream()
        .collect(
            Collectors.toMap(
                response -> response.get("type"),
                response -> response,
                (existing, replacement) -> existing))
        .values()
        .stream()
        .toList();
  }

  protected abstract List<FileOp> generateSDK(String outputDirectoryPath, Spec spec)
      throws IOException;

  protected abstract Map<String, String> templatesDefinition();

  @Override
  public String dataType(Schema<?> schema) {
    return "unknown";
  }

  @Override
  public String listDataType(List<Response> responseParameters) {
    return "unknown";
  }

  public Map<String, Object> additionalTemplateParams(Resource resource) {
    return Map.of();
  }

  public boolean cleanDirectoryBeforeGenerate() {
    return true;
  }

  public void initialiseQAHandler() {
    QAModeHandler.getInstance().setValue(false);
  }
}
