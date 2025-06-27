package com.chargebee.openapi;

import static com.chargebee.openapi.Extension.*;
import static com.chargebee.openapi.MarkdownHelper.convertHtmlToMarkdown;
import static com.chargebee.openapi.Resource.RESOURCE_ID_EXTENSION;

import com.chargebee.QAModeHandler;
import com.chargebee.openapi.parameter.Path;
import com.chargebee.sdk.DataType;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

public class Action {
  public static final String HIDDEN_FROM_SDK = "x-cb-hidden-from-client-sdk";
  static String bulkOperation = "x-cb-operation-is-bulk";
  static String internalOperation = "x-cb-internal";
  public final String id;
  public final String name;
  public final HttpRequestType httpRequestType;
  private final Operation operation;

  @Getter @Setter private String url;

  public Action(HttpRequestType httpRequestType, Operation operation, String url) {
    this.operation = operation;
    this.id = operation.getOperationId();
    if (operation.getExtensions() == null) {
      throw new IllegalArgumentException("Operation Extensions not found");
    }
    Object methodName = operation.getExtensions().get("x-cb-operation-method-name");
    if (methodName == null) {
      throw new IllegalArgumentException("Operation method name not found");
    }
    this.httpRequestType = httpRequestType;
    this.name = (String) methodName;
    this.url = url;
  }

  public List<Path> pathParameters() {
    List<Parameter> parameters = operation.getParameters();
    if (parameters == null) {
      return List.of();
    }
    return parameters.stream()
        .filter(p -> p.getIn().equalsIgnoreCase("path"))
        .map(p -> new Path(p.getName(), p.getSchema()))
        .toList();
  }

  public Response response() {
    return isListResourceAction()
        ? new ListResponse(name, onSuccessSchema())
        : new Response(name, onSuccessSchema());
  }

  public Map<String, Object> templateParams(DataType lang) {
    List<Path> pathParameters = pathParameters();
    List<com.chargebee.openapi.parameter.Parameter> queryParameters = queryParameters();
    var response =
        isListResourceAction()
            ? new ListResponse(name, onSuccessSchema())
            : new Response(name, onSuccessSchema());
    var requestBodyParameters = requestBodyParameters();
    List<Map<String, Integer>> jsonKeys = getJsonKeysInRequestBody();
    Map<String, Object> additionalOptions = getAdditionalOptions();

    Map<String, Object> templateMap =
        new HashMap<>(
            Map.ofEntries(
                new AbstractMap.SimpleEntry<String, Object>("name", name),
                new AbstractMap.SimpleEntry<String, Object>(
                    "description",
                    operation.getDescription() != null
                        ? convertHtmlToMarkdown(operation.getDescription())
                        : ""),
                new AbstractMap.SimpleEntry<String, Object>(
                    "pathParameters",
                    pathParameters.stream().map(pp -> pp.templateParams(lang)).toList()),
                new AbstractMap.SimpleEntry<String, Object>(
                    "queryParameters",
                    queryParameters.stream().map(qp -> qp.templateParams(lang)).toList()),
                new AbstractMap.SimpleEntry<String, Object>(
                    "hasQueryParameters", !queryParameters.isEmpty()),
                new AbstractMap.SimpleEntry<String, Object>(
                    "requestBodyParameters",
                    requestBodyParameters.stream()
                        .filter(entry -> !entry.isHiddenFromSDK())
                        .map(qp -> qp.templateParams(lang))
                        .toList()),
                new AbstractMap.SimpleEntry<String, Object>(
                    "hasRequestBodyParameters", !requestBodyParameters.isEmpty()),
                new AbstractMap.SimpleEntry<String, Object>(
                    "isAdditionalPropertiesSupportedInRequestBody",
                    isAdditionalPropertiesSupportedInRequestBody()),
                new AbstractMap.SimpleEntry<String, Object>(
                    "isAllRequestBodyParamsOptional",
                    requestBodyParameters.stream()
                        .noneMatch(p -> p.isRequired || p.hasRequiredSubParameters())),
                new AbstractMap.SimpleEntry<String, Object>(
                    "isAllQueryParamsOptional",
                    queryParameters.stream()
                        .noneMatch(p -> p.isRequired || p.hasRequiredSubParameters())),
                new AbstractMap.SimpleEntry<String, Object>(
                    "hasPathParameters", !pathParameters.isEmpty()),
                new AbstractMap.SimpleEntry<String, Object>(
                    "hasMultipleInputParams",
                    (!queryParameters.isEmpty() && !pathParameters.isEmpty())
                        || (!requestBodyParameters.isEmpty() && !pathParameters.isEmpty())),
                new AbstractMap.SimpleEntry<String, Object>(
                    "response", response.templateParams(lang)),
                new AbstractMap.SimpleEntry<String, Object>(
                    "isListResourceAction", isListResourceAction()),
                new AbstractMap.SimpleEntry<String, Object>("id", id),
                new AbstractMap.SimpleEntry<String, Object>("httpRequestType", httpRequestType),
                new AbstractMap.SimpleEntry<String, Object>("url", url),
                new AbstractMap.SimpleEntry<String, Object>("urlPrefix", getUrlPrefix()),
                new AbstractMap.SimpleEntry<String, Object>("urlSuffix", getUrlSuffix()),
                new AbstractMap.SimpleEntry<String, Object>(
                    "isDeprecated", this.isOperationDeprecated()),
                new AbstractMap.SimpleEntry<String, Object>("sortOrder", sortOrder()),
                new AbstractMap.SimpleEntry<String, Object>(
                    "isOperationNeedsJsonInput", this.isOperationNeedsJsonInput()),
                new AbstractMap.SimpleEntry<String, Object>("jsonKeys", jsonKeys),
                new AbstractMap.SimpleEntry<String, Object>("options", additionalOptions)));
    if (subDomain() != null) {
      templateMap.put("subDomain", subDomain());
    }
    return templateMap;
  }

  private boolean isAdditionalPropertiesSupportedInRequestBody() {
    Schema<?> requestBodySchema = getRequestBodySchema();
    if (requestBodySchema == null) {
      return false;
    }
    return requestBodySchema.getAdditionalProperties() != null
        && (boolean) requestBodySchema.getAdditionalProperties();
  }

  public List<com.chargebee.openapi.parameter.Parameter> requestBodyParameters() {
    if (httpRequestType != HttpRequestType.POST || operation.getRequestBody() == null) {
      return List.of();
    }
    Schema<?> schema = getRequestBodySchema();
    if (schema == null
        || schema.getProperties() == null
        || schema.getProperties().isEmpty()
        || !(schema instanceof ObjectSchema || schema instanceof MapSchema)) {
      return List.of();
    }
    var requiredProperties =
        schema.getRequired() == null ? Set.of() : new HashSet<>(schema.getRequired());
    return schema.getProperties().entrySet().stream()
        .map(
            entry ->
                new com.chargebee.openapi.parameter.Parameter(
                    entry.getKey(),
                    entry.getValue(),
                    requiredProperties.contains(entry.getKey())
                        || entry.getValue().getRequired() != null))
        .sorted(Comparator.comparing(com.chargebee.openapi.parameter.Parameter::sortOrder))
        .toList();
  }

  public List<Map<String, Integer>> getJsonKeysInRequestBody() {
    if (httpRequestType != HttpRequestType.POST || operation.getRequestBody() == null) {
      return List.of();
    }
    Schema<?> schema = getRequestBodySchema();
    if (schema == null
        || schema.getProperties() == null
        || schema.getProperties().isEmpty()
        || !(schema instanceof ObjectSchema || schema instanceof MapSchema)) {
      return List.of();
    }
    List<Map<String, Integer>> jsonKeys = new ArrayList<>();
    recursivelyExtractJsonKeysInEachLevel(schema.getProperties(), jsonKeys, 0);
    Set<String> seenKeys = new HashSet<>();
    return jsonKeys.stream()
        .map(
            map ->
                map.entrySet().stream()
                    .filter(entry -> seenKeys.add(entry.getKey() + "=" + entry.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
        .filter(map -> !map.isEmpty())
        .collect(Collectors.toList());
  }

  private Map<String, Object> getAdditionalOptions() {
    Map<String, Object> additionalOptions = new HashMap<>();
    if (operation.getExtensions() != null) {
      if (operation.getExtensions().get(IS_OPERATION_IDEMPOTENT) != null) {
        additionalOptions.put(
            "isIdempotent", operation.getExtensions().get(IS_OPERATION_IDEMPOTENT));
      }
    }
    return additionalOptions;
  }

  private void recursivelyExtractJsonKeysInEachLevel(
      Map<String, Schema> properties, List<Map<String, Integer>> jsonKeys, int level) {
    if (properties == null || properties.isEmpty()) {
      return;
    }

    for (Map.Entry<String, Schema> entry : properties.entrySet()) {
      String key = entry.getKey();
      Schema<?> schema = entry.getValue();
      if (schemaHiddenFromSDK(schema)) {
        continue;
      }
      if ((schema instanceof MapSchema && schema.getProperties() == null)
          || (schema instanceof ObjectSchema && schema.getProperties() == null)
          || (schema instanceof ArraySchema
              && schema.getItems() != null
              && schema.getItems().getType() == null)) {
        Map<String, Integer> map = new HashMap<>();
        map.put(key, level);
        jsonKeys.add(map);
      }
      if (schema instanceof ObjectSchema || schema instanceof MapSchema) {
        recursivelyExtractJsonKeysInEachLevel(schema.getProperties(), jsonKeys, level + 1);
      }
      if (schema instanceof ArraySchema && schema.getItems() != null) {
        Schema<?> itemsSchema = schema.getItems();
        if (itemsSchema.getType() == null) {
          Map<String, Integer> map = new HashMap<>();
          map.put(key, level);
          jsonKeys.add(map);
        } else {
          Map<String, Schema> itemsMap = Collections.singletonMap(key, itemsSchema);
          recursivelyExtractJsonKeysInEachLevel(itemsMap, jsonKeys, level);
        }
      }
    }
  }

  public boolean isNotHiddenFromSDK() {
    return operation.getExtensions() == null
        || operation.getExtensions().get(HIDDEN_FROM_SDK) == null
        || !((boolean) operation.getExtensions().get(HIDDEN_FROM_SDK))
        || QAModeHandler.getInstance().getValue();
  }

  public boolean isNotBulkOperation() {
    return operation.getExtensions() == null
        || operation.getExtensions().get(bulkOperation) == null
        || !((boolean) operation.getExtensions().get(bulkOperation))
        || QAModeHandler.getInstance().getValue();
  }

  public boolean isNotInternalOperation() {
    return operation.getExtensions() == null
        || operation.getExtensions().get(internalOperation) == null
        || !((boolean) operation.getExtensions().get(internalOperation))
        || QAModeHandler.getInstance().getValue();
  }

  public boolean isOperationDeprecated() {
    return operation.getDeprecated() != null
        ? operation.getDeprecated()
        : operation.getDeprecated() != null;
  }

  private Schema<?> getRequestBodySchema() {
    if (operation.getRequestBody() == null) {
      return null;
    }
    return operation
        .getRequestBody()
        .getContent()
        .get("application/x-www-form-urlencoded")
        .getSchema();
  }

  public List<com.chargebee.openapi.parameter.Parameter> queryParameters() {
    if (httpRequestType != HttpRequestType.GET) {
      return List.of();
    }
    if (operation.getParameters() == null) {
      return List.of();
    }
    return operation.getParameters().stream()
        .filter(parameter -> Objects.equals(parameter.getIn(), "query"))
        .map(com.chargebee.openapi.parameter.Parameter::fromParameter)
        .sorted(Comparator.comparing(com.chargebee.openapi.parameter.Parameter::sortOrder))
        .toList();
  }

  public String resourceId() {
    return operation.getExtensions().get(RESOURCE_ID_EXTENSION).toString();
  }

  public int sortOrder() {
    return operation.getExtensions().get("x-cb-sort-order") != null
        ? (int) operation.getExtensions().get("x-cb-sort-order")
        : -1;
  }

  public boolean isListResourceAction() {
    Object isListOperation = operation.getExtensions().get("x-cb-operation-is-list");
    if (isListOperation == null) {
      return false;
    }
    return (boolean) isListOperation;
  }

  public boolean isOperationNeedsJsonInput() {
    Object isOperationNeedsJsonInput = operation.getExtensions().get(IS_OPERATION_NEEDS_JSON_INPUT);
    if (isOperationNeedsJsonInput == null) {
      return false;
    }
    return (boolean) isOperationNeedsJsonInput;
  }

  private ObjectSchema onSuccessSchema() {
    return (ObjectSchema)
        operation.getResponses().get("200").getContent().get("application/json").getSchema();
  }

  public ObjectSchema getOnSuccessSchema() {
    return (ObjectSchema)
        operation.getResponses().get("200").getContent().get("application/json").getSchema();
  }

  public boolean hasInputParamsForEAPClientLibs() {
    return operation.getParameters() != null
        && operation.getParameters().stream()
            .anyMatch(p -> isEapParameter(p) || isNotSkipClientLibParameter(p));
  }

  public boolean hasInputParamsForClientLibs() {
    return operation.getParameters() != null
        && operation.getParameters().stream().anyMatch(this::isNotSkipClientLibParameter);
  }

  private boolean isEapParameter(Parameter parameter) {
    return parameter.getExtensions() != null
        && parameter.getExtensions().get(IS_EAP) != null
        && (boolean) parameter.getExtensions().get(IS_EAP);
  }

  private boolean isNotSkipClientLibParameter(Parameter parameter) {
    return parameter.getExtensions() == null
        || parameter.getExtensions().get(HIDDEN_FROM_CLIENT_SDK) == null
        || !((boolean) parameter.getExtensions().get(HIDDEN_FROM_CLIENT_SDK))
        || QAModeHandler.getInstance().getValue();
  }

  public boolean isInputObjNeeded() {
    return operation.getExtensions() != null
        && operation.getExtensions().get(IS_OPERATION_NEEDS_INPUT_OBJECT) != null
        && (boolean) operation.getExtensions().get(IS_OPERATION_NEEDS_INPUT_OBJECT);
  }

  public boolean isCustomFieldSupported() {
    var schema = getRequestBodySchema();
    if (httpRequestType != HttpRequestType.POST) {
      return operation.getExtensions() != null
          && operation.getExtensions().get(IS_CUSTOM_FIELDS_SUPPORTED) != null
          && (boolean) operation.getExtensions().get(IS_CUSTOM_FIELDS_SUPPORTED);
    }
    if (schema == null || schema.getExtensions() == null) {
      return false;
    }
    return schema.getExtensions().get(IS_CUSTOM_FIELDS_SUPPORTED) != null
        && (boolean) schema.getExtensions().get(IS_CUSTOM_FIELDS_SUPPORTED);
  }

  public boolean isBatch() {
    return operation.getExtensions() != null
        && operation.getExtensions().get(OPERATION_IS_BATCH) != null
        && (boolean) operation.getExtensions().get(OPERATION_IS_BATCH);
  }

  public String subDomain() {
    if (operation.getExtensions() == null
        || operation.getExtensions().get(OPERATION_SUB_DOMAIN) == null) {
      return null;
    }
    return operation.getExtensions().get(OPERATION_SUB_DOMAIN).toString();
  }

  public String batchId() {
    if (operation.getExtensions() == null
        || operation.getExtensions().get(BATCH_OPERATION_PATH_ID) == null) return "";
    return operation.getExtensions().get(BATCH_OPERATION_PATH_ID).toString();
  }

  public String modelName() {
    if (operation.getExtensions() == null || operation.getExtensions().get(MODEL_NAME) == null)
      return "";
    return operation.getExtensions().get(MODEL_NAME).toString();
  }

  public String getUrlPrefix() {
    Matcher urlPattern =
        Pattern.compile("^/([^/]+)(?:/\\{[^}]+\\})?(?:/([^/]+(?:/[^/]+)?))?$").matcher(url);
    if (urlPattern.matches() && urlPattern.group(1) != null) {
      return urlPattern.group(1);
    }
    return "";
  }

  public String getUrlSuffix() {
    Matcher urlPattern =
        Pattern.compile("^/([^/]+)(?:/\\{[^}]+\\})?(?:/([^/]+(?:/[^/]+)?))?$").matcher(url);
    if (urlPattern.matches() && urlPattern.group(2) != null) {
      return urlPattern.group(2);
    }
    return "";
  }

  private List<com.chargebee.openapi.parameter.Parameter> requestBodySubParameters(
      Schema<?> schema) {
    if (schema == null
        || schema.getProperties() == null
        || schema.getProperties().isEmpty()
        || !(schema instanceof ObjectSchema || schema instanceof MapSchema)) {
      return List.of();
    }
    var requiredProperties =
        schema.getRequired() == null ? Set.of() : new HashSet<>(schema.getRequired());
    return schema.getProperties().entrySet().stream()
        .map(
            entry ->
                new com.chargebee.openapi.parameter.Parameter(
                    entry.getKey(),
                    entry.getValue(),
                    requiredProperties.contains(entry.getKey())
                        || entry.getValue().getRequired() != null))
        .sorted(Comparator.comparing(com.chargebee.openapi.parameter.Parameter::sortOrder))
        .toList();
  }

  public boolean isContentTypeJsonAction() {
    return operation.getExtensions() != null
        && operation.getExtensions().get(Extension.IS_OPERATION_NEEDS_JSON_INPUT) != null
        && ((boolean) operation.getExtensions().get(Extension.IS_OPERATION_NEEDS_JSON_INPUT));
  }

  public boolean isIdempotent() {
    return operation.getExtensions() != null
        && operation.getExtensions().get(IS_OPERATION_IDEMPOTENT) != null
        && (boolean) operation.getExtensions().get(IS_OPERATION_IDEMPOTENT);
  }

  public boolean hasPostActionContainingFilterAsBodyParams() {
    List<com.chargebee.openapi.parameter.Parameter> requestBodyParameters = requestBodyParameters();
    for (com.chargebee.openapi.parameter.Parameter param : requestBodyParameters) {
      if (param.isFilterParameters()) {
        return true;
      }
      List<com.chargebee.openapi.parameter.Parameter> requestBodySubParameters =
          requestBodySubParameters(param.schema);
      if (requestBodySubParameters.stream()
          .anyMatch(com.chargebee.openapi.parameter.Parameter::isFilterParameters)) {
        return true;
      }
    }
    return false;
  }

  private boolean schemaHiddenFromSDK(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(HIDDEN_FROM_SDK) != null
        && (boolean) schema.getExtensions().get(HIDDEN_FROM_SDK);
  }

  public boolean isAllRequestBodyParamsOptional() {
    return requestBodyParameters().stream()
        .noneMatch(p -> p.isRequired || p.hasRequiredSubParameters());
  }

  public boolean isAllQueryParamsOptional() {
    return queryParameters().stream().noneMatch(p -> p.isRequired || p.hasRequiredSubParameters());
  }
}
