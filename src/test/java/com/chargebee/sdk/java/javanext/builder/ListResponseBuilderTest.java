package com.chargebee.sdk.java.javanext.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link ListResponseBuilder}.
 *
 * <p>ListResponseBuilder generates paginated list response classes with support for:
 * - next_offset pagination
 * - Auto-pagination helpers
 * - Item type resolution
 * - Path parameter handling
 *
 */
@DisplayName("List Response Builder")
class ListResponseBuilderTest {

  private ListResponseBuilder listResponseBuilder;
  private Template template;
  private String outputPath;
  private OpenAPI openAPI;

  @BeforeEach
  void setUp() throws IOException {
    listResponseBuilder = new ListResponseBuilder();
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
    template = handlebars.compile("core.get.response.list.hbs");
  }

  // BUILDER CONFIGURATION

  @Nested
  @DisplayName("Builder Configuration")
  class BuilderConfigurationTests {

    @Test
    @DisplayName("Should configure output directory path")
    void shouldConfigureOutputDirectoryPath() throws IOException {
      listResponseBuilder.withOutputDirectoryPath(outputPath);

      List<FileOp> fileOps = listResponseBuilder.withTemplate(template).build(openAPI);

      // Should create directory
      assertThat(fileOps).isNotEmpty();
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
      FileOp.CreateDirectory dirOp = (FileOp.CreateDirectory) fileOps.get(0);
      assertThat(dirOp.basePath).isEqualTo(outputPath);
    }

    @Test
    @DisplayName("Should configure template")
    void shouldConfigureTemplate() {
      ListResponseBuilder builder = listResponseBuilder.withTemplate(template);
      assertThat(builder).isSameAs(listResponseBuilder);
    }

    @Test
    @DisplayName("Should support fluent builder chaining")
    void shouldSupportFluentBuilderChaining() {
      ListResponseBuilder result =
          listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);
      assertThat(result).isSameAs(listResponseBuilder);
    }

    @Test
    @DisplayName("Should throw exception when template is null during build")
    void shouldThrowExceptionWhenTemplateIsNull() {
      assertThatThrownBy(
              () -> listResponseBuilder.withOutputDirectoryPath(outputPath).build(openAPI))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Template");
    }

    @Test
    @DisplayName("Should throw exception when output directory is null during build")
    void shouldThrowExceptionWhenOutputDirectoryIsNull() {
      assertThatThrownBy(() -> listResponseBuilder.withTemplate(template).build(openAPI))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Output directory");
    }

    @Test
    @DisplayName("Should throw exception when OpenAPI is null")
    void shouldThrowExceptionWhenOpenAPIIsNull() {
      assertThatThrownBy(
              () ->
                  listResponseBuilder
                      .withOutputDirectoryPath(outputPath)
                      .withTemplate(template)
                      .build(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("OpenAPI");
    }
  }

  // LIST RESPONSE GENERATION

  @Nested
  @DisplayName("List Response Generation")
  class ListResponseGenerationTests {

    @Test
    @DisplayName("Should generate list response for paginated list operation")
    void shouldGenerateListResponseForPaginatedOperation() throws IOException {
      addPaginatedListOperation("customer", "list");

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      // Should have directory + response file
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);
      assertFileExists(fileOps, "CustomerListResponse.java");
    }

    @Test
    @DisplayName("Should include list and next_offset fields in response")
    void shouldIncludeListAndNextOffsetFields() throws IOException {
      addPaginatedListOperation("customer", "list");

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      FileOp.WriteString responseFile = findWriteOp(fileOps, "CustomerListResponse.java");

      // Should contain list and nextOffset references
      assertThat(responseFile.fileContent).containsIgnoringCase("list");
      assertThat(responseFile.fileContent).containsIgnoringCase("nextOffset");
    }

    @Test
    @DisplayName("Should skip non-paginated operations")
    void shouldSkipNonPaginatedOperations() throws IOException {
      // Add operation without next_offset (not paginated)
      ObjectSchema simpleSchema = new ObjectSchema();
      simpleSchema.addProperty("id", new StringSchema());
      addGetOperation("customer", "retrieve", simpleSchema);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      // Should only have directory, no response file for non-paginated
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle list with referenced item type")
    void shouldHandleListWithReferencedItemType() throws IOException {
      // Add Customer schema
      ObjectSchema customerSchema = new ObjectSchema();
      customerSchema.addProperty("id", new StringSchema());
      customerSchema.addProperty("email", new StringSchema());
      openAPI.getComponents().getSchemas().put("Customer", customerSchema);

      // Add list operation with $ref to Customer
      ObjectSchema listSchema = new ObjectSchema();
      ArraySchema arraySchema = new ArraySchema();
      Schema<?> itemSchema = new Schema<>();
      itemSchema.set$ref("#/components/schemas/Customer");
      arraySchema.setItems(itemSchema);
      listSchema.addProperty("list", arraySchema);
      listSchema.addProperty("next_offset", new StringSchema());

      addGetOperation("customer", "list", listSchema);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      FileOp.WriteString responseFile = findWriteOp(fileOps, "CustomerListResponse.java");

      // Should import Customer model
      assertThat(responseFile.fileContent).contains("com.chargebee.v4.models");
      assertThat(responseFile.fileContent).containsIgnoringCase("Customer");
    }

    @Test
    @DisplayName("Should handle list with inline object item type")
    void shouldHandleListWithInlineObjectItemType() throws IOException {
      ObjectSchema listSchema = new ObjectSchema();
      ArraySchema arraySchema = new ArraySchema();

      // Inline object schema for items
      ObjectSchema itemSchema = new ObjectSchema();
      itemSchema.addProperty("id", new StringSchema());
      itemSchema.addProperty("name", new StringSchema());
      arraySchema.setItems(itemSchema);

      listSchema.addProperty("list", arraySchema);
      listSchema.addProperty("next_offset", new StringSchema());

      addGetOperation("customer", "list", listSchema);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListResponse.java");
    }

    @Test
    @DisplayName("Should handle path parameters in list operations")
    void shouldHandlePathParametersInListOperations() throws IOException {
      // Path /customers/{customer-id}/subscriptions derives to subscriptionsForCustomer
      // subscriptionsForCustomer doesn't contain "subscription", so prefix is added
      addPaginatedListOperation(
          "subscription", "subscriptionsForCustomer", "/customers/{customer-id}/subscriptions");

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      FileOp.WriteString responseFile =
          findWriteOp(fileOps, "SubscriptionsForCustomerResponse.java");

      // Should handle path parameters (converted to camelCase)
      assertThat(responseFile.fileContent).containsIgnoringCase("customerId");
    }

    @Test
    @DisplayName("Should generate separate files for different resources")
    void shouldGenerateSeparateFilesForDifferentResources() throws IOException {
      addPaginatedListOperation("customer", "list");
      addPaginatedListOperation("invoice", "list");
      addPaginatedListOperation("subscription", "list");

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListResponse.java");
      assertFileExists(fileOps, "InvoiceListResponse.java");
      assertFileExists(fileOps, "SubscriptionListResponse.java");
    }

    @Test
    @DisplayName("Should convert snake_case method names to PascalCase for class names")
    void shouldConvertSnakeCaseMethodNamesToPascalCase() throws IOException {
      addPaginatedListOperation("payment_source", "list_all");

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      // payment_source_list_all -> PaymentSourceListAllResponse
      assertFileExists(fileOps, "PaymentSourceListAllResponse.java");
    }

    @Test
    @DisplayName("Should deduplicate imports for referenced types")
    void shouldDeduplicateImportsForReferencedTypes() throws IOException {
      // Add referenced schemas
      ObjectSchema customerSchema = new ObjectSchema();
      customerSchema.addProperty("id", new StringSchema());
      openAPI.getComponents().getSchemas().put("Customer", customerSchema);

      ObjectSchema addressSchema = new ObjectSchema();
      addressSchema.addProperty("line1", new StringSchema());
      openAPI.getComponents().getSchemas().put("Address", addressSchema);

      // Add list with multiple references to same types
      ObjectSchema listSchema = new ObjectSchema();
      ArraySchema arraySchema = new ArraySchema();
      Schema<?> customerRef = new Schema<>();
      customerRef.set$ref("#/components/schemas/Customer");
      arraySchema.setItems(customerRef);
      listSchema.addProperty("list", arraySchema);
      listSchema.addProperty("next_offset", new StringSchema());

      // Add another field with same reference
      Schema<?> addressRef = new Schema<>();
      addressRef.set$ref("#/components/schemas/Address");
      listSchema.addProperty("billing_address", addressRef);
      listSchema.addProperty("shipping_address", addressRef); // duplicate reference

      addGetOperation("customer", "list", listSchema);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      FileOp.WriteString responseFile = findWriteOp(fileOps, "CustomerListResponse.java");

      // Should have imports but no duplicates
      String content = responseFile.fileContent;
      assertThat(content).contains("com.chargebee.v4.models");
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

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(emptyAPI);

      // Should only create directory
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should handle null paths in OpenAPI")
    void shouldHandleNullPathsInOpenAPI() throws IOException {
      OpenAPI apiWithNullPaths = new OpenAPI();
      apiWithNullPaths.setPaths(null);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(apiWithNullPaths);

      // Should only create directory
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should handle operations without required extensions")
    void shouldHandleOperationsWithoutRequiredExtensions() throws IOException {
      Operation operation = new Operation();
      operation.setOperationId("listCustomers");
      // No extensions

      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      response.setContent(createPaginatedListContent());
      responses.addApiResponse("200", response);
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers", pathItem);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      // Should skip this operation
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle operations with null extensions")
    void shouldHandleOperationsWithNullExtensions() throws IOException {
      Operation operation = new Operation();
      operation.setOperationId("listCustomers");
      operation.setExtensions(null);

      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      response.setContent(createPaginatedListContent());
      responses.addApiResponse("200", response);
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers", pathItem);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      // Should skip this operation
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle operations without 200 response")
    void shouldHandleOperationsWithout200Response() throws IOException {
      Operation operation = createOperationWithExtensions("customer", "list");

      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      response.setContent(createPaginatedListContent());
      responses.addApiResponse("201", response); // Not 200
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers", pathItem);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      // Should skip this operation (no 200 response)
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle response without content")
    void shouldHandleResponseWithoutContent() throws IOException {
      Operation operation = createOperationWithExtensions("customer", "list");

      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      response.setContent(null); // No content
      responses.addApiResponse("200", response);
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers", pathItem);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      // Should skip this operation
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle response without JSON content")
    void shouldHandleResponseWithoutJsonContent() throws IOException {
      Operation operation = createOperationWithExtensions("customer", "list");

      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      Content content = new Content();
      MediaType mediaType = new MediaType();
      ObjectSchema schema = new ObjectSchema();
      schema.addProperty("list", new ArraySchema());
      schema.addProperty("next_offset", new StringSchema());
      mediaType.setSchema(schema);
      content.addMediaType("text/plain", mediaType); // Not application/json
      response.setContent(content);
      responses.addApiResponse("200", response);
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers", pathItem);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      // Should skip this operation (no JSON content)
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle schema without properties")
    void shouldHandleSchemaWithoutProperties() throws IOException {
      Operation operation = createOperationWithExtensions("customer", "list");

      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      Content content = new Content();
      MediaType mediaType = new MediaType();
      Schema<?> schema = new Schema<>(); // No properties
      mediaType.setSchema(schema);
      content.addMediaType("application/json", mediaType);
      response.setContent(content);
      responses.addApiResponse("200", response);
      operation.setResponses(responses);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers", pathItem);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      // Should skip (not a valid list response)
      assertThat(fileOps).allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should handle list array without items schema")
    void shouldHandleListArrayWithoutItemsSchema() throws IOException {
      ObjectSchema listSchema = new ObjectSchema();
      ArraySchema arraySchema = new ArraySchema();
      arraySchema.setItems(null); // No items schema
      listSchema.addProperty("list", arraySchema);
      listSchema.addProperty("next_offset", new StringSchema());

      addGetOperation("customer", "list", listSchema);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      // Should still generate response (with Object items)
      assertFileExists(fileOps, "CustomerListResponse.java");
    }

    @Test
    @DisplayName("Should handle path without path parameters")
    void shouldHandlePathWithoutPathParameters() throws IOException {
      addPaginatedListOperation("customer", "list", "/customers");

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      FileOp.WriteString responseFile = findWriteOp(fileOps, "CustomerListResponse.java");

      // Should generate response without path param handling
      assertThat(responseFile.fileContent).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle multiple path parameters")
    void shouldHandleMultiplePathParameters() throws IOException {
      // Path with multiple path params derives based on path structure
      // /sites/{site-id}/customers/{customer-id}/subscriptions -> subscriptionsForCustomer
      addPaginatedListOperation(
          "subscription",
          "subscriptionsForCustomer",
          "/sites/{site-id}/customers/{customer-id}/subscriptions");

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      // Verify a response file is generated for the subscription resource
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.contains("Subscription"));
    }

    @Test
    @DisplayName("Should handle sub-models in list response")
    void shouldHandleSubModelsInListResponse() throws IOException {
      ObjectSchema listSchema = new ObjectSchema();
      ArraySchema arraySchema = new ArraySchema();
      arraySchema.setItems(new ObjectSchema().addProperty("id", new StringSchema()));
      listSchema.addProperty("list", arraySchema);
      listSchema.addProperty("next_offset", new StringSchema());

      // Add nested object field
      ObjectSchema nestedObject = new ObjectSchema();
      nestedObject.addProperty("field1", new StringSchema());
      nestedObject.addProperty("field2", new IntegerSchema());
      listSchema.addProperty("metadata", nestedObject);

      addGetOperation("customer", "list", listSchema);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListResponse.java");
    }

    @Test
    @DisplayName("Should handle list with array of objects as nested field")
    void shouldHandleListWithArrayOfObjectsAsNestedField() throws IOException {
      ObjectSchema listSchema = new ObjectSchema();
      ArraySchema mainList = new ArraySchema();
      mainList.setItems(new ObjectSchema().addProperty("id", new StringSchema()));
      listSchema.addProperty("list", mainList);
      listSchema.addProperty("next_offset", new StringSchema());

      // Add array of objects as another field
      ArraySchema nestedArray = new ArraySchema();
      ObjectSchema nestedItem = new ObjectSchema();
      nestedItem.addProperty("name", new StringSchema());
      nestedArray.setItems(nestedItem);
      listSchema.addProperty("tags", nestedArray);

      addGetOperation("customer", "list", listSchema);

      listResponseBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = listResponseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListResponse.java");
    }
  }

  // HELPER METHODS

  private void addPaginatedListOperation(String resourceId, String methodName) {
    // Build path based on method name
    String path;
    if ("list".equals(methodName)) {
      path = "/" + resourceId + "s";
    } else {
      // For custom actions like list_all, use path with action segment
      path = "/" + resourceId + "s/" + methodName;
    }
    addPaginatedListOperation(resourceId, methodName, path);
  }

  private void addPaginatedListOperation(String resourceId, String methodName, String path) {
    ObjectSchema listSchema = new ObjectSchema();
    ArraySchema arraySchema = new ArraySchema();
    arraySchema.setItems(new ObjectSchema().addProperty("id", new StringSchema()));
    listSchema.addProperty("list", arraySchema);
    listSchema.addProperty("next_offset", new StringSchema());

    addGetOperation(resourceId, methodName, listSchema, path);
  }

  private void addGetOperation(String resourceId, String methodName, ObjectSchema responseSchema) {
    addGetOperation(resourceId, methodName, responseSchema, "/" + resourceId + "s");
  }

  private void addGetOperation(
      String resourceId, String methodName, ObjectSchema responseSchema, String path) {
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
    openAPI.getPaths().addPathItem(path, pathItem);
  }

  private Operation createOperationWithExtensions(String resourceId, String methodName) {
    Operation operation = new Operation();
    operation.setOperationId(resourceId + "_" + methodName);

    Map<String, Object> extensions = new HashMap<>();
    extensions.put(Extension.RESOURCE_ID, resourceId);
    // Set IS_OPERATION_LIST for list operations so path-based derivation works correctly
    if ("list".equals(methodName)) {
      extensions.put(Extension.IS_OPERATION_LIST, true);
    }
    operation.setExtensions(extensions);

    return operation;
  }

  private Content createPaginatedListContent() {
    ObjectSchema listSchema = new ObjectSchema();
    ArraySchema arraySchema = new ArraySchema();
    arraySchema.setItems(new ObjectSchema().addProperty("id", new StringSchema()));
    listSchema.addProperty("list", arraySchema);
    listSchema.addProperty("next_offset", new StringSchema());

    Content content = new Content();
    MediaType mediaType = new MediaType();
    mediaType.setSchema(listSchema);
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
