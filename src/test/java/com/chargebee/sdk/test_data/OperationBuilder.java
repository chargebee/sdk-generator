package com.chargebee.sdk.test_data;

import static com.chargebee.openapi.Extension.*;

import com.chargebee.openapi.Extension;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.*;

public class OperationBuilder {
  private final String methodName;
  private final Operation operation;
  private final boolean isListOperation;
  private final boolean isPostOperation;

  private static final String IS_CUSTOM_FIELDS_SUPPORTED = "x-cb-is-custom-fields-supported";
  private static final String OPERATION_METHOD_NAME = "x-cb-operation-method-name";
  private static final String SDK_METHOD_NAME = "x-cb-sdk-method-name";
  private static final String OPERATION_IS_LIST = "x-cb-operation-is-list";

  private OperationBuilder(
      String methodName, Operation operation, boolean isListOperation, boolean isPostOperation) {
    this.methodName = methodName;
    this.operation = operation;
    this.isListOperation = isListOperation;
    this.isPostOperation = isPostOperation;
  }

  public static OperationBuilder buildOperation(String methodName) {
    LinkedHashMap linkedHashMap = new LinkedHashMap();
    linkedHashMap.put(OPERATION_METHOD_NAME, methodName);
    linkedHashMap.put(SDK_METHOD_NAME, methodName);
    Operation operation = new Operation().operationId(methodName).extensions(linkedHashMap);
    return new OperationBuilder(methodName, operation, false, false);
  }

  public static OperationBuilder buildListOperation(String methodName) {
    LinkedHashMap linkedHashMap = new LinkedHashMap();
    linkedHashMap.put(OPERATION_METHOD_NAME, methodName);
    linkedHashMap.put(SDK_METHOD_NAME, methodName);
    linkedHashMap.put(OPERATION_IS_LIST, true);
    Operation operation = new Operation().operationId(methodName).extensions(linkedHashMap);
    return new OperationBuilder(methodName, operation, true, false);
  }

  public static OperationBuilder buildListOperationWithCustomField(
      String methodName, boolean customFieldSupported) {
    LinkedHashMap linkedHashMap = new LinkedHashMap();
    linkedHashMap.put(OPERATION_METHOD_NAME, methodName);
    linkedHashMap.put(SDK_METHOD_NAME, methodName);
    linkedHashMap.put(OPERATION_IS_LIST, true);
    linkedHashMap.put(IS_CUSTOM_FIELDS_SUPPORTED, customFieldSupported);
    Operation operation = new Operation().operationId(methodName).extensions(linkedHashMap);
    return new OperationBuilder(methodName, operation, true, false);
  }

  public static OperationBuilder buildPostOperation(String methodName) {
    return buildPostOperation(methodName, null, false);
  }

  public static OperationBuilder buildPostOperation(String methodName, String description) {
    return buildPostOperation(methodName, description, false);
  }

  public static OperationBuilder buildPostOperationThatSupportsCustomFields(String methodName) {
    return buildPostOperation(methodName, null, true);
  }

  private static OperationBuilder buildPostOperation(
      String methodName, String description, boolean hasSupportForAdditionalProperties) {
    LinkedHashMap linkedHashMap = new LinkedHashMap();
    linkedHashMap.put(OPERATION_METHOD_NAME, methodName);
    linkedHashMap.put(SDK_METHOD_NAME, methodName);
    Operation operation =
        new Operation()
            .description(description)
            .operationId(methodName)
            .extensions(linkedHashMap)
            .requestBody(
                new RequestBody()
                    .content(
                        new Content()
                            .addMediaType(
                                "application/x-www-form-urlencoded",
                                new MediaType()
                                    .schema(
                                        new ObjectSchema()
                                            .additionalProperties(
                                                hasSupportForAdditionalProperties)))));
    return new OperationBuilder(methodName, operation, false, true);
  }

  public static OperationBuilder buildPostOperationWitCbCustomField(
      String methodName, String description, boolean customFieldSupported) {
    LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<>();
    linkedHashMap.put(OPERATION_METHOD_NAME, methodName);
    linkedHashMap.put(SDK_METHOD_NAME, methodName);

    LinkedHashMap<String, Object> abc = new LinkedHashMap<>();
    abc.put(IS_CUSTOM_FIELDS_SUPPORTED, customFieldSupported);
    ObjectSchema schema = (ObjectSchema) new ObjectSchema().extensions(abc);

    Operation operation =
        new Operation()
            .description(description)
            .operationId(methodName)
            .extensions(linkedHashMap)
            .requestBody(
                new RequestBody()
                    .content(
                        new Content()
                            .addMediaType(
                                "application/x-www-form-urlencoded",
                                new MediaType()
                                    .schema(schema)))); // Use the schema with the custom field

    return new OperationBuilder(methodName, operation, false, true);
  }

  public static OperationBuilder buildPostOperationWitNullCbCustomField(
      String methodName, String description) {
    LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<>();
    linkedHashMap.put(OPERATION_METHOD_NAME, methodName);
    linkedHashMap.put(SDK_METHOD_NAME, methodName);

    LinkedHashMap<String, Object> abc = new LinkedHashMap<>();
    abc.put(IS_CUSTOM_FIELDS_SUPPORTED, null);
    ObjectSchema schema = (ObjectSchema) new ObjectSchema().extensions(abc);

    Operation operation =
        new Operation()
            .description(description)
            .operationId(methodName)
            .extensions(linkedHashMap)
            .requestBody(
                new RequestBody()
                    .content(
                        new Content()
                            .addMediaType(
                                "application/x-www-form-urlencoded",
                                new MediaType()
                                    .schema(schema)))); // Use the schema with the custom field

    return new OperationBuilder(methodName, operation, false, true);
  }

  public static Schema hiddenFromSDKPropSchema() {
    return new StringSchema().extensions(Map.of("x-cb-hidden-from-client-sdk", true));
  }

  public OperationBuilder withPathParam(String paramName) {
    Parameter parameter = new PathParameter().name(paramName).schema(new StringSchema());
    ArrayList<Parameter> parameters = new ArrayList<>();
    parameters.add(parameter);
    operation.setParameters(parameters);
    return this;
  }

  public OperationBuilder withResponse(ResourceResponseParam... resourceResponseParams) {
    ObjectSchema objectSchema = new ObjectSchema();
    for (var resourceResponseParam : resourceResponseParams) {
      objectSchema.addProperty(resourceResponseParam.paramName, resourceResponseParam.schema());
    }
    objectSchema.setRequired(
        Arrays.stream(resourceResponseParams)
            .filter(resourceResponseParam -> resourceResponseParam.isRequired)
            .map(resourceResponseParam -> resourceResponseParam.paramName)
            .toList());
    var schema =
        isListOperation
            ? new ObjectSchema()
                .properties(
                    Map.of(
                        "list",
                        new ArraySchema().items(objectSchema),
                        "next_offset",
                        new StringSchema()))
                .required(List.of("list"))
            : objectSchema;
    MediaType mediaType = new MediaType().schema(schema);
    operation.setResponses(
        new ApiResponses()
            .addApiResponse(
                "200",
                new ApiResponse()
                    .content(new Content().addMediaType("application/json", mediaType))));
    return this;
  }

  public Operation done() {
    return operation;
  }

  public OperationBuilder forResource(String resourceId) {
    operation.addExtension("x-cb-resource-id", resourceId);
    return this;
  }

  public OperationBuilder withQueryParam(String name) {
    return withQueryParam(name, false);
  }

  public OperationBuilder withQueryParam(String name, boolean isRequired) {
    return withQueryParam(name, new StringSchema(), isRequired);
  }

  public OperationBuilder withQueryParam(String name, Schema<?> schema) {
    return withQueryParam(name, schema, false);
  }

  public OperationBuilder withQueryParam(String name, Schema<?> schema, boolean isRequired) {
    operation.addParametersItem(
        new QueryParameter().name(name).required(isRequired).schema(schema));
    return this;
  }

  public OperationBuilder withCompositeArrayRequestBody(String name, Schema<?> objectSchema) {
    return withCompositeArrayRequestBody(name, null, objectSchema);
  }

  public OperationBuilder withCompositeArrayRequestBody(
      String name, String description, Schema<?> objectSchema) {
    var compositeArraySchema = new ObjectSchema().description(description);
    compositeArraySchema.addExtension("x-cb-is-composite-array-request-body", true);
    objectSchema
        .getProperties()
        .forEach((k, v) -> compositeArraySchema.addProperty(k, new ArraySchema().items(v)));
    compositeArraySchema.setRequired(objectSchema.getRequired());
    return withRequestBody(name, compositeArraySchema);
  }

  public OperationBuilder withRequestBody(String name, Schema<?> schema) {
    return withRequestBody(name, schema, false);
  }

  public OperationBuilder asHiddenFromSDKGeneration() {
    operation.addExtension("x-cb-hidden-from-client-sdk", true);
    return this;
  }

  public OperationBuilder asBulkOperationFromSDKGeneration() {
    operation.addExtension("x-cb-operation-is-bulk", true);
    return this;
  }

  public OperationBuilder withSortOrder(int sortOrder) {
    operation.addExtension("x-cb-sort-order", sortOrder);
    return this;
  }

  public OperationBuilder asBatch() {
    operation.addExtension(OPERATION_IS_BATCH, true);
    return this;
  }

  public OperationBuilder withFilterParameter() {
    operation.addExtension("x-cb-is-filter-parameter", true);
    return this;
  }

  public OperationBuilder isDeprecatedOperation() {
    operation.deprecated(true);
    return this;
  }

  public OperationBuilder asInputObjNeeded() {
    operation.addExtension("x-cb-is-operation-needs-input-object", true);
    return this;
  }

  public OperationBuilder withRequestBody(String name, Schema<?> schema, boolean isRequired) {
    var reqBodySchema =
        operation
            .getRequestBody()
            .getContent()
            .get("application/x-www-form-urlencoded")
            .getSchema();
    if (isRequired) {
      var requiredProps =
          reqBodySchema.getRequired() == null ? new ArrayList<>() : reqBodySchema.getRequired();
      requiredProps.add(name);
      reqBodySchema.setRequired(requiredProps);
    }
    reqBodySchema.addProperty(name, schema);
    return this;
  }

  public OperationBuilder withSubDomain(String subDomain) {
    operation.addExtension(OPERATION_SUB_DOMAIN, subDomain);
    return this;
  }

  public OperationBuilder isContentTypeJson(boolean contentTypeJson) {
    operation.addExtension(IS_OPERATION_NEEDS_JSON_INPUT, contentTypeJson);
    return this;
  }

  public OperationBuilder isBatch(boolean isBatch) {
    operation.addExtension(OPERATION_IS_BATCH, isBatch);
    return this;
  }

  public OperationBuilder asIdempotentEndpoint() {
    operation.addExtension(Extension.IS_OPERATION_IDEMPOTENT, true);
    return this;
  }
}
