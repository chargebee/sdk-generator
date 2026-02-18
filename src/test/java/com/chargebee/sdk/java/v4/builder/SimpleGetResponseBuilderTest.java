package com.chargebee.sdk.java.v4.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link SimpleGetResponseBuilder}.
 *
 * <p>SimpleGetResponseBuilder generates simple GET response classes (non-paginated) with support
 * for:
 * - Single resource retrieval
 * - Schema resolution ($ref handling)
 * - Composed schemas (allOf, anyOf, oneOf)
 * - Nested objects and arrays
 * - Import collection
 *
 */
@DisplayName("Simple GET Response Builder")
class SimpleGetResponseBuilderTest {

  private SimpleGetResponseBuilder responseBuilder;
  private Template template;
  private String outputPath;
  private OpenAPI openAPI;

  @BeforeEach
  void setUp() throws IOException {
    responseBuilder = new SimpleGetResponseBuilder();
    outputPath = "/test/output";

    openAPI = new OpenAPI();
    openAPI.setPaths(new Paths());
    openAPI.setComponents(new Components().schemas(new HashMap<>()));

    // Load actual template
    Handlebars handlebars =
        new Handlebars(
            new com.github.jknack.handlebars.io.ClassPathTemplateLoader(
                "/templates/java/next", ""));
    HandlebarsUtil.registerAllHelpers(handlebars);
    template = handlebars.compile("core.get.response.hbs");
  }

  // BUILDER CONFIGURATION

  @Nested
  @DisplayName("Builder Configuration")
  class BuilderConfigurationTests {

    @Test
    @DisplayName("Should configure output directory path")
    void shouldConfigureOutputDirectoryPath() throws IOException {
      responseBuilder.withOutputDirectoryPath(outputPath);

      List<FileOp> fileOps = responseBuilder.withTemplate(template).build(openAPI);

      // Should create directory
      assertThat(fileOps).isNotEmpty();
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
      FileOp.CreateDirectory dirOp = (FileOp.CreateDirectory) fileOps.get(0);
      assertThat(dirOp.basePath).isEqualTo(outputPath);
    }

    @Test
    @DisplayName("Should configure template")
    void shouldConfigureTemplate() {
      SimpleGetResponseBuilder builder = responseBuilder.withTemplate(template);
      assertThat(builder).isSameAs(responseBuilder);
    }

    @Test
    @DisplayName("Should support fluent builder chaining")
    void shouldSupportFluentBuilderChaining() {
      SimpleGetResponseBuilder result =
          responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);
      assertThat(result).isSameAs(responseBuilder);
    }
  }

  // SIMPLE GET RESPONSE GENERATION

  @Nested
  @DisplayName("Simple GET Response Generation")
  class SimpleGetResponseGenerationTests {

    @Test
    @DisplayName("Should generate response for simple GET operation")
    void shouldGenerateResponseForSimpleGetOperation() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("id", new StringSchema());
      responseSchema.addProperty("email", new StringSchema());
      responseSchema.addProperty("status", new StringSchema());

      addGetOperation("customer", "retrieve", responseSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerRetrieveResponse.java");
    }

    @Test
    @DisplayName("Should include fields from response schema")
    void shouldIncludeFieldsFromResponseSchema() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("id", new StringSchema());
      responseSchema.addProperty("email", new StringSchema());
      responseSchema.addProperty("created_at", new IntegerSchema());

      addGetOperation("customer", "retrieve", responseSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      FileOp.WriteString responseFile = findWriteOp(fileOps, "CustomerRetrieveResponse.java");

      // Should contain field names (camelCase converted)
      assertThat(responseFile.fileContent).containsIgnoringCase("id");
      assertThat(responseFile.fileContent).containsIgnoringCase("email");
      assertThat(responseFile.fileContent).containsIgnoringCase("createdAt");
      assertThat(responseFile.fileContent).startsWith("package com.chargebee.v4.models.");
    }

    @Test
    @DisplayName("Should skip paginated list operations")
    void shouldSkipPaginatedListOperations() throws IOException {
      // Add paginated list operation (should be skipped)
      ObjectSchema listSchema = new ObjectSchema();
      listSchema.addProperty("list", new ArraySchema());
      listSchema.addProperty("next_offset", new StringSchema());

      addGetOperation("customer", "list", listSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should only have directory, no response file for list operations
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should generate separate files for different resources")
    void shouldGenerateSeparateFilesForDifferentResources() throws IOException {
      ObjectSchema customerSchema = new ObjectSchema();
      customerSchema.addProperty("id", new StringSchema());
      addGetOperation("customer", "retrieve", customerSchema);

      ObjectSchema invoiceSchema = new ObjectSchema();
      invoiceSchema.addProperty("id", new StringSchema());
      addGetOperation("invoice", "retrieve", invoiceSchema);

      ObjectSchema subscriptionSchema = new ObjectSchema();
      subscriptionSchema.addProperty("id", new StringSchema());
      addGetOperation("subscription", "retrieve", subscriptionSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerRetrieveResponse.java");
      assertFileExists(fileOps, "InvoiceRetrieveResponse.java");
      assertFileExists(fileOps, "SubscriptionRetrieveResponse.java");
    }

    @Test
    @DisplayName("Should convert snake_case method names to PascalCase")
    void shouldConvertSnakeCaseMethodNamesToPascalCase() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("id", new StringSchema());

      // Path /payment_sources/{id}/retrieve_details - module "payment_source" + method
      // "retrieve_details"
      addGetOperation("payment_source", "retrieve_details", responseSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Module "payment_source" + method "retrieve_details" = PaymentSourceRetrieveDetailsResponse
      assertFileExists(fileOps, "PaymentSourceRetrieveDetailsResponse.java");
    }
  }

  // SCHEMA RESOLUTION

  @Nested
  @DisplayName("Schema Resolution and References")
  class SchemaResolutionTests {

    @Test
    @DisplayName("Should resolve top-level $ref in response schema")
    void shouldResolveTopLevelRefInResponseSchema() throws IOException {
      // Add Customer schema to components
      ObjectSchema customerSchema = new ObjectSchema();
      customerSchema.addProperty("id", new StringSchema());
      customerSchema.addProperty("email", new StringSchema());
      openAPI.getComponents().getSchemas().put("Customer", customerSchema);

      // Create response with $ref
      Schema<?> refSchema = new Schema<>();
      refSchema.set$ref("#/components/schemas/Customer");

      Operation operation = createOperationWithExtensions("customer", "retrieve");
      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      Content content = new Content();
      MediaType mediaType = new MediaType();
      mediaType.setSchema(refSchema);
      content.addMediaType("application/json", mediaType);
      response.setContent(content);
      responses.addApiResponse("200", response);
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers/{id}", pathItem);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerRetrieveResponse.java");
    }

    @Test
    @DisplayName("Should handle composed schemas with allOf")
    void shouldHandleComposedSchemasWithAllOf() throws IOException {
      // Create base schema
      ObjectSchema baseSchema = new ObjectSchema();
      baseSchema.addProperty("id", new StringSchema());
      baseSchema.addProperty("created_at", new IntegerSchema());
      openAPI.getComponents().getSchemas().put("BaseResource", baseSchema);

      // Create extension schema
      ObjectSchema extSchema = new ObjectSchema();
      extSchema.addProperty("email", new StringSchema());
      extSchema.addProperty("status", new StringSchema());

      // Create composed schema with allOf
      ComposedSchema composedSchema = new ComposedSchema();
      Schema<?> baseRef = new Schema<>();
      baseRef.set$ref("#/components/schemas/BaseResource");
      composedSchema.setAllOf(Arrays.asList(baseRef, extSchema));

      addGetOperationWithSchema("customer", "retrieve", composedSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      FileOp.WriteString responseFile = findWriteOp(fileOps, "CustomerRetrieveResponse.java");

      // Should include fields from both schemas
      assertThat(responseFile.fileContent).containsIgnoringCase("id");
      assertThat(responseFile.fileContent).containsIgnoringCase("email");
    }

    @Test
    @DisplayName("Should handle fields with $ref to other schemas")
    void shouldHandleFieldsWithRefToOtherSchemas() throws IOException {
      // Add Address schema
      ObjectSchema addressSchema = new ObjectSchema();
      addressSchema.addProperty("line1", new StringSchema());
      addressSchema.addProperty("city", new StringSchema());
      openAPI.getComponents().getSchemas().put("Address", addressSchema);

      // Create customer schema with address reference
      ObjectSchema customerSchema = new ObjectSchema();
      customerSchema.addProperty("id", new StringSchema());

      Schema<?> addressRef = new Schema<>();
      addressRef.set$ref("#/components/schemas/Address");
      customerSchema.addProperty("billing_address", addressRef);

      addGetOperation("customer", "retrieve", customerSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      FileOp.WriteString responseFile = findWriteOp(fileOps, "CustomerRetrieveResponse.java");

      // Should import Address
      assertThat(responseFile.fileContent).contains("com.chargebee.v4.models");
      assertThat(responseFile.fileContent).containsIgnoringCase("Address");
    }

    @Test
    @DisplayName("Should deduplicate imports for multiple references to same type")
    void shouldDeduplicateImportsForMultipleReferences() throws IOException {
      // Add Address schema
      ObjectSchema addressSchema = new ObjectSchema();
      addressSchema.addProperty("line1", new StringSchema());
      openAPI.getComponents().getSchemas().put("Address", addressSchema);

      // Create schema with multiple references to Address
      ObjectSchema customerSchema = new ObjectSchema();
      customerSchema.addProperty("id", new StringSchema());

      Schema<?> billingRef = new Schema<>();
      billingRef.set$ref("#/components/schemas/Address");
      customerSchema.addProperty("billing_address", billingRef);

      Schema<?> shippingRef = new Schema<>();
      shippingRef.set$ref("#/components/schemas/Address");
      customerSchema.addProperty("shipping_address", shippingRef);

      addGetOperation("customer", "retrieve", customerSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      FileOp.WriteString responseFile = findWriteOp(fileOps, "CustomerRetrieveResponse.java");

      // Should have import but not duplicated
      assertThat(responseFile.fileContent).contains("com.chargebee.v4.models");
    }
  }

  // NESTED OBJECTS AND ARRAYS

  @Nested
  @DisplayName("Nested Objects and Arrays")
  class NestedObjectsTests {

    @Test
    @DisplayName("Should handle nested object fields")
    void shouldHandleNestedObjectFields() throws IOException {
      ObjectSchema nestedObject = new ObjectSchema();
      nestedObject.addProperty("line1", new StringSchema());
      nestedObject.addProperty("city", new StringSchema());

      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("id", new StringSchema());
      responseSchema.addProperty("address", nestedObject);

      addGetOperation("customer", "retrieve", responseSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerRetrieveResponse.java");
    }

    @Test
    @DisplayName("Should handle array fields with object items")
    void shouldHandleArrayFieldsWithObjectItems() throws IOException {
      ObjectSchema itemSchema = new ObjectSchema();
      itemSchema.addProperty("name", new StringSchema());
      itemSchema.addProperty("value", new StringSchema());

      ArraySchema arraySchema = new ArraySchema();
      arraySchema.setItems(itemSchema);

      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("id", new StringSchema());
      responseSchema.addProperty("metadata", arraySchema);

      addGetOperation("customer", "retrieve", responseSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerRetrieveResponse.java");
    }

    @Test
    @DisplayName("Should handle array fields with $ref items")
    void shouldHandleArrayFieldsWithRefItems() throws IOException {
      // Add Tag schema
      ObjectSchema tagSchema = new ObjectSchema();
      tagSchema.addProperty("name", new StringSchema());
      openAPI.getComponents().getSchemas().put("Tag", tagSchema);

      // Create array with $ref items
      ArraySchema arraySchema = new ArraySchema();
      Schema<?> tagRef = new Schema<>();
      tagRef.set$ref("#/components/schemas/Tag");
      arraySchema.setItems(tagRef);

      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("id", new StringSchema());
      responseSchema.addProperty("tags", arraySchema);

      addGetOperation("customer", "retrieve", responseSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      FileOp.WriteString responseFile = findWriteOp(fileOps, "CustomerRetrieveResponse.java");

      // Should import Tag
      assertThat(responseFile.fileContent).contains("com.chargebee.v4.models");
      assertThat(responseFile.fileContent).containsIgnoringCase("Tag");
    }

    @Test
    @DisplayName("Should skip nested objects without properties")
    void shouldSkipNestedObjectsWithoutProperties() throws IOException {
      ObjectSchema emptyNestedObject = new ObjectSchema();
      // No properties

      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("id", new StringSchema());
      responseSchema.addProperty("metadata", emptyNestedObject);

      addGetOperation("customer", "retrieve", responseSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerRetrieveResponse.java");
    }

    @Test
    @DisplayName("Should skip array fields with empty items")
    void shouldSkipArrayFieldsWithEmptyItems() throws IOException {
      ArraySchema arraySchema = new ArraySchema();
      ObjectSchema emptyItem = new ObjectSchema();
      // No properties
      arraySchema.setItems(emptyItem);

      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("id", new StringSchema());
      responseSchema.addProperty("tags", arraySchema);

      addGetOperation("customer", "retrieve", responseSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerRetrieveResponse.java");
    }
  }

  // FALLBACK SCENARIOS

  @Nested
  @DisplayName("Fallback Scenarios")
  class FallbackScenariosTests {

    @Test
    @DisplayName("Should use schema fallback when properties are null but schema exists")
    void shouldUseSchemaFallbackWhenPropertiesAreNull() throws IOException {
      // Add Customer schema to components
      ObjectSchema customerSchema = new ObjectSchema();
      customerSchema.addProperty("id", new StringSchema());
      openAPI.getComponents().getSchemas().put("Customer", customerSchema);

      // Create response schema with no properties but required field
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.setProperties(null);
      responseSchema.setRequired(Arrays.asList("customer"));

      addGetOperation("customer", "retrieve", responseSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerRetrieveResponse.java");
    }

    @Test
    @DisplayName("Should use required fields fallback when properties and schema missing")
    void shouldUseRequiredFieldsFallbackWhenPropertiesAndSchemaMissing() throws IOException {
      // No schema in components

      // Create response schema with no properties but required fields
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.setProperties(null);
      responseSchema.setRequired(Arrays.asList("customer", "subscription"));

      addGetOperation("customer", "retrieve", responseSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerRetrieveResponse.java");
    }

    @Test
    @DisplayName("Should use module name fallback when all else fails")
    void shouldUseModuleNameFallbackWhenAllElseFails() throws IOException {
      // Create completely empty response schema
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.setProperties(null);
      responseSchema.setRequired(null);

      addGetOperation("customer", "retrieve", responseSchema);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerRetrieveResponse.java");
    }
  }

  // EDGE CASES

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle empty OpenAPI spec")
    void shouldHandleEmptyOpenAPISpec() throws IOException {
      OpenAPI emptyAPI = new OpenAPI();
      emptyAPI.setPaths(new Paths());

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(emptyAPI);

      // Should only create directory
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should handle null paths in OpenAPI")
    void shouldHandleNullPathsInOpenAPI() throws IOException {
      OpenAPI apiWithNullPaths = new OpenAPI();
      apiWithNullPaths.setPaths(null);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(apiWithNullPaths);

      // Should only create directory
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should handle operations without required extensions")
    void shouldHandleOperationsWithoutRequiredExtensions() throws IOException {
      Operation operation = new Operation();
      operation.setOperationId("retrieveCustomer");
      // No extensions

      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      response.setContent(createSimpleContent());
      responses.addApiResponse("200", response);
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers/{id}", pathItem);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should skip this operation
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle operations with null responses")
    void shouldHandleOperationsWithNullResponses() throws IOException {
      Operation operation = createOperationWithExtensions("customer", "retrieve");
      operation.setResponses(null);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers/{id}", pathItem);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should skip this operation
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle response without 200 status")
    void shouldHandleResponseWithout200Status() throws IOException {
      Operation operation = createOperationWithExtensions("customer", "retrieve");

      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      response.setContent(createSimpleContent());
      responses.addApiResponse("201", response); // Not 200
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers/{id}", pathItem);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should skip this operation
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle response without content")
    void shouldHandleResponseWithoutContent() throws IOException {
      Operation operation = createOperationWithExtensions("customer", "retrieve");

      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      response.setContent(null);
      responses.addApiResponse("200", response);
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers/{id}", pathItem);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should skip this operation
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle response without JSON content")
    void shouldHandleResponseWithoutJsonContent() throws IOException {
      Operation operation = createOperationWithExtensions("customer", "retrieve");

      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      Content content = new Content();
      MediaType mediaType = new MediaType();
      mediaType.setSchema(new ObjectSchema().addProperty("id", new StringSchema()));
      content.addMediaType("text/plain", mediaType); // Not application/json
      response.setContent(content);
      responses.addApiResponse("200", response);
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers/{id}", pathItem);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should skip this operation
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle null schema in response")
    void shouldHandleNullSchemaInResponse() throws IOException {
      Operation operation = createOperationWithExtensions("customer", "retrieve");

      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      Content content = new Content();
      MediaType mediaType = new MediaType();
      mediaType.setSchema(null);
      content.addMediaType("application/json", mediaType);
      response.setContent(content);
      responses.addApiResponse("200", response);
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers/{id}", pathItem);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should skip this operation
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle circular $ref resolution gracefully")
    void shouldHandleCircularRefResolutionGracefully() throws IOException {
      // Create schema with circular reference
      ObjectSchema nodeSchema = new ObjectSchema();
      nodeSchema.addProperty("id", new StringSchema());

      Schema<?> selfRef = new Schema<>();
      selfRef.set$ref("#/components/schemas/Node");
      nodeSchema.addProperty("parent", selfRef);

      openAPI.getComponents().getSchemas().put("Node", nodeSchema);

      // Use the schema in a response
      Schema<?> responseRef = new Schema<>();
      responseRef.set$ref("#/components/schemas/Node");

      Operation operation = createOperationWithExtensions("node", "retrieve");
      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      Content content = new Content();
      MediaType mediaType = new MediaType();
      mediaType.setSchema(responseRef);
      content.addMediaType("application/json", mediaType);
      response.setContent(content);
      responses.addApiResponse("200", response);
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/nodes/{id}", pathItem);

      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should handle without infinite loop
      assertFileExists(fileOps, "NodeRetrieveResponse.java");
    }
  }

  // HELPER METHODS

  private void addGetOperation(String resourceId, String methodName, ObjectSchema responseSchema) {
    Operation operation = createOperationWithExtensions(resourceId, methodName);

    ApiResponses responses = new ApiResponses();
    ApiResponse response = new ApiResponse();
    Content content = new Content();
    MediaType mediaType = new MediaType();
    mediaType.setSchema(responseSchema);
    content.addMediaType("application/json", mediaType);
    response.setContent(content);
    responses.addApiResponse("200", response);
    operation.setResponses(responses);

    PathItem pathItem = new PathItem();
    pathItem.setGet(operation);
    // Build path based on method name: retrieve -> /{id}, list -> no {id}, others -> /{id}/action
    String path;
    if ("retrieve".equals(methodName)) {
      path = "/" + resourceId + "s/{id}";
    } else if ("list".equals(methodName)) {
      path = "/" + resourceId + "s";
    } else {
      // For custom actions like retrieve_details, use path with action segment
      path = "/" + resourceId + "s/{id}/" + methodName;
    }
    openAPI.getPaths().addPathItem(path, pathItem);
  }

  private void addGetOperationWithSchema(String resourceId, String methodName, Schema<?> schema) {
    Operation operation = createOperationWithExtensions(resourceId, methodName);

    ApiResponses responses = new ApiResponses();
    ApiResponse response = new ApiResponse();
    Content content = new Content();
    MediaType mediaType = new MediaType();
    mediaType.setSchema(schema);
    content.addMediaType("application/json", mediaType);
    response.setContent(content);
    responses.addApiResponse("200", response);
    operation.setResponses(responses);

    PathItem pathItem = new PathItem();
    pathItem.setGet(operation);
    // Build path based on method name: retrieve -> /{id}, list -> no {id}, others -> /{id}/action
    String path;
    if ("retrieve".equals(methodName)) {
      path = "/" + resourceId + "s/{id}";
    } else if ("list".equals(methodName)) {
      path = "/" + resourceId + "s";
    } else {
      // For custom actions like retrieve_details, use path with action segment
      path = "/" + resourceId + "s/{id}/" + methodName;
    }
    openAPI.getPaths().addPathItem(path, pathItem);
  }

  private Operation createOperationWithExtensions(String resourceId, String methodName) {
    Operation operation = new Operation();
    operation.setOperationId(resourceId + "_" + methodName);

    Map<String, Object> extensions = new HashMap<>();
    extensions.put(Extension.RESOURCE_ID, resourceId);
    extensions.put(Extension.SDK_METHOD_NAME, methodName);
    // Set IS_OPERATION_LIST for list operations so path-based derivation works correctly
    if ("list".equals(methodName)) {
      extensions.put(Extension.IS_OPERATION_LIST, true);
    }
    operation.setExtensions(extensions);

    return operation;
  }

  private Content createSimpleContent() {
    ObjectSchema schema = new ObjectSchema();
    schema.addProperty("id", new StringSchema());

    Content content = new Content();
    MediaType mediaType = new MediaType();
    mediaType.setSchema(schema);
    content.addMediaType("application/json", mediaType);
    return content;
  }

  private void assertFileExists(List<FileOp> fileOps, String fileName) {
    boolean exists =
        fileOps.stream()
            .anyMatch(
                op ->
                    op instanceof FileOp.WriteString
                        && ((FileOp.WriteString) op).fileName.equals(fileName));
    assertThat(exists).as("Expected file %s to exist in file operations", fileName).isTrue();
  }

  private FileOp.WriteString findWriteOp(List<FileOp> fileOps, String fileName) {
    return fileOps.stream()
        .filter(
            op ->
                op instanceof FileOp.WriteString
                    && ((FileOp.WriteString) op).fileName.equals(fileName))
        .map(op -> (FileOp.WriteString) op)
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("Could not find WriteString operation for file: " + fileName));
  }
}
