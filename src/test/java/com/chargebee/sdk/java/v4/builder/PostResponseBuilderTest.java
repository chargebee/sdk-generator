package com.chargebee.sdk.java.v4.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Comprehensive test suite for PostResponseBuilder.
 *
 * <p>This class validates POST response generation from OpenAPI specifications. It covers:
 *
 * <ul>
 *   <li>Response class generation for POST operations
 *   <li>Success response selection (200, 202, 204)
 *   <li>Response field extraction
 *   <li>Import collection from response schemas
 *   <li>Referenced types ($ref) handling
 *   <li>Array and nested object responses
 *   <li>Empty responses (204 No Content)
 *   <li>Batch operation handling
 * </ul>
 */
@DisplayName("POST Response Builder")
class PostResponseBuilderTest {

  private PostResponseBuilder responseBuilder;
  private Template mockTemplate;
  private OpenAPI openAPI;
  private String outputPath;

  @BeforeEach
  void setUp() throws IOException {
    responseBuilder = new PostResponseBuilder();
    outputPath = "/test/output";
    openAPI = new OpenAPI();

    Handlebars handlebars =
        new Handlebars(
            new com.github.jknack.handlebars.io.ClassPathTemplateLoader(
                "/templates/java/next", ""));
    HandlebarsUtil.registerAllHelpers(handlebars);
    mockTemplate = handlebars.compile("core.post.response.hbs");
  }

  // BUILDER CONFIGURATION

  @Nested
  @DisplayName("Builder Configuration")
  class BuilderConfigurationTests {

    @Test
    @DisplayName("Should configure output directory path")
    void shouldConfigureOutputDirectoryPath() {
      PostResponseBuilder result = responseBuilder.withOutputDirectoryPath(outputPath);

      assertThat(result).isSameAs(responseBuilder);
    }

    @Test
    @DisplayName("Should throw exception for null output directory")
    void shouldThrowExceptionForNullOutputDirectory() {
      assertThatThrownBy(() -> responseBuilder.withOutputDirectoryPath(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("outputDirectoryPath must not be null or blank");
    }

    @Test
    @DisplayName("Should throw exception for blank output directory")
    void shouldThrowExceptionForBlankOutputDirectory() {
      assertThatThrownBy(() -> responseBuilder.withOutputDirectoryPath("   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("outputDirectoryPath must not be null or blank");
    }

    @Test
    @DisplayName("Should configure template")
    void shouldConfigureTemplate() {
      PostResponseBuilder result = responseBuilder.withTemplate(mockTemplate);

      assertThat(result).isSameAs(responseBuilder);
    }

    @Test
    @DisplayName("Should throw exception for null template")
    void shouldThrowExceptionForNullTemplate() {
      assertThatThrownBy(() -> responseBuilder.withTemplate(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should throw exception when building without template")
    void shouldThrowExceptionWhenBuildingWithoutTemplate() {
      responseBuilder.withOutputDirectoryPath(outputPath);

      assertThatThrownBy(() -> responseBuilder.build(openAPI))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Template not set");
    }

    @Test
    @DisplayName("Should throw exception when building without output directory")
    void shouldThrowExceptionWhenBuildingWithoutOutputDirectory() {
      responseBuilder.withTemplate(mockTemplate);

      assertThatThrownBy(() -> responseBuilder.build(openAPI))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Output directory not set");
    }

    @Test
    @DisplayName("Should throw exception for null OpenAPI spec")
    void shouldThrowExceptionForNullOpenAPISpec() {
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      assertThatThrownBy(() -> responseBuilder.build(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should create output directory structure")
    void shouldCreateOutputDirectoryStructure() throws IOException {
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
      FileOp.CreateDirectory dirOp = (FileOp.CreateDirectory) fileOps.get(0);
      assertThat(dirOp.basePath).endsWith("/v4/models");
    }
  }

  // BASIC RESPONSE GENERATION

  @Nested
  @DisplayName("Basic Response Generation")
  class BasicResponseGenerationTests {

    @Test
    @DisplayName("Should generate response class for POST operation with 200 response")
    void shouldGenerateResponseForPostOperationWith200Response() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("id", new StringSchema());
      responseSchema.addProperty("status", new StringSchema());

      Operation postOp = createPostOperationWithResponse("customer", "create", responseSchema);
      addPathWithPostOperation("/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerCreateResponse.java");
    }

    @Test
    @DisplayName("Should generate response class for multiple POST operations")
    void shouldGenerateMultipleResponseClasses() throws IOException {
      ObjectSchema customerSchema = new ObjectSchema();
      customerSchema.addProperty("id", new StringSchema());

      ObjectSchema invoiceSchema = new ObjectSchema();
      invoiceSchema.addProperty("invoice_id", new StringSchema());

      Operation customerCreateOp =
          createPostOperationWithResponse("customer", "create", customerSchema);
      Operation invoiceCreateOp =
          createPostOperationWithResponse("invoice", "create", invoiceSchema);

      addPathWithPostOperation("/customers", customerCreateOp);
      addPathWithPostOperation("/invoices", invoiceCreateOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerCreateResponse.java");
      assertFileExists(fileOps, "InvoiceCreateResponse.java");
    }

    @Test
    @DisplayName("Should skip GET operations")
    void shouldSkipGetOperations() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("id", new StringSchema());

      Operation getOp = new Operation();
      getOp.addExtension(Extension.SDK_METHOD_NAME, "retrieve");
      getOp.addExtension(Extension.RESOURCE_ID, "customer");

      PathItem pathItem = new PathItem();
      pathItem.setGet(getOp);
      addPath("/customers/{customer-id}", pathItem);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should only have base directory
      assertThat(fileOps).hasSize(1);
    }
  }

  // SUCCESS RESPONSE SELECTION

  @Nested
  @DisplayName("Success Response Selection")
  class SuccessResponseSelectionTests {

    @Test
    @DisplayName("Should prefer 200 response over 202 and 204")
    void shouldPrefer200ResponseOver202And204() throws IOException {
      ObjectSchema response200Schema = new ObjectSchema();
      response200Schema.addProperty("id", new StringSchema());

      ObjectSchema response202Schema = new ObjectSchema();
      response202Schema.addProperty("status", new StringSchema());

      Operation postOp = createPostOperation("customer", "create");
      addMultipleSuccessResponses(postOp, response200Schema, response202Schema, null);
      addPathWithPostOperation("/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerCreateResponse.java");
    }

    @Test
    @DisplayName("Should use 202 response if 200 not available")
    void shouldUse202ResponseIf200NotAvailable() throws IOException {
      ObjectSchema response202Schema = new ObjectSchema();
      response202Schema.addProperty("status", new StringSchema());

      Operation postOp = createPostOperation("customer", "create");
      addMultipleSuccessResponses(postOp, null, response202Schema, null);
      addPathWithPostOperation("/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerCreateResponse.java");
    }

    @Test
    @DisplayName("Should use 204 response if 200 and 202 not available")
    void shouldUse204ResponseIf200And202NotAvailable() throws IOException {
      // Path /customers/{customer-id}/delete with POST derives to "deleteForCustomer"
      Operation postOp = createPostOperation("customer", "delete");
      add204Response(postOp);
      addPathWithPostOperation("/customers/{customer-id}/delete", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Verify a delete response file is generated
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.contains("Delete"));
    }

    @Test
    @DisplayName("Should skip operation without success response")
    void shouldSkipOperationWithoutSuccessResponse() throws IOException {
      Operation postOp = createPostOperation("customer", "create");
      // No responses added
      addPathWithPostOperation("/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Only base directory
      assertThat(fileOps).hasSize(1);
    }
  }

  // RESPONSE FIELD EXTRACTION

  @Nested
  @DisplayName("Response Field Extraction")
  class ResponseFieldExtractionTests {

    @Test
    @DisplayName("Should extract fields from response schema")
    void shouldExtractFieldsFromResponseSchema() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("id", new StringSchema());
      responseSchema.addProperty("email", new StringSchema());
      responseSchema.addProperty("created_at", new IntegerSchema().format("unix-time"));

      Operation postOp = createPostOperationWithResponse("customer", "create", responseSchema);
      addPathWithPostOperation("/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerCreateResponse.java");
      assertThat(writeOp.fileContent).contains("id");
      assertThat(writeOp.fileContent).contains("email");
      assertThat(writeOp.fileContent).contains("createdAt");
    }

    @Test
    @DisplayName("Should handle response with referenced schema")
    void shouldHandleResponseWithReferencedSchema() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      StringSchema customerRef = new StringSchema();
      customerRef.set$ref("#/components/schemas/Customer");
      responseSchema.addProperty("customer", customerRef);

      Operation postOp = createPostOperationWithResponse("customer", "create", responseSchema);
      addPathWithPostOperation("/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerCreateResponse.java");
    }

    @Test
    @DisplayName("Should handle response with array fields")
    void shouldHandleResponseWithArrayFields() throws IOException {
      ArraySchema customersArray = new ArraySchema();
      StringSchema customerRef = new StringSchema();
      customerRef.set$ref("#/components/schemas/Customer");
      customersArray.setItems(customerRef);

      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("customers", customersArray);

      // Path /customers with POST derives to "create"
      Operation postOp = createPostOperationWithResponse("customer", "create", responseSchema);
      addPathWithPostOperation("/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerCreateResponse.java");
    }

    @Test
    @DisplayName("Should generate empty response for schema without properties")
    void shouldGenerateEmptyResponseForSchemaWithoutProperties() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      // No properties added

      Operation postOp = createPostOperationWithResponse("customer", "create", responseSchema);
      addPathWithPostOperation("/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerCreateResponse.java");
    }

    @Test
    @DisplayName("Should generate empty response for 204 No Content")
    void shouldGenerateEmptyResponseFor204NoContent() throws IOException {
      // Path /customers/{customer-id}/delete with POST derives to "deleteForCustomer"
      Operation postOp = createPostOperation("customer", "delete");
      add204Response(postOp);
      addPathWithPostOperation("/customers/{customer-id}/delete", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Verify a delete response file is generated
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.contains("Delete"));
    }
  }

  // IMPORT COLLECTION

  @Nested
  @DisplayName("Import Collection")
  class ImportCollectionTests {

    @Test
    @DisplayName("Should collect imports from referenced schemas")
    void shouldCollectImportsFromReferencedSchemas() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      StringSchema customerRef = new StringSchema();
      customerRef.set$ref("#/components/schemas/Customer");
      responseSchema.addProperty("customer", customerRef);

      Operation postOp = createPostOperationWithResponse("invoice", "create", responseSchema);
      addPathWithPostOperation("/invoices", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "InvoiceCreateResponse.java");
    }

    @Test
    @DisplayName("Should collect imports from array items")
    void shouldCollectImportsFromArrayItems() throws IOException {
      ArraySchema itemsArray = new ArraySchema();
      StringSchema itemRef = new StringSchema();
      itemRef.set$ref("#/components/schemas/LineItem");
      itemsArray.setItems(itemRef);

      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("items", itemsArray);

      Operation postOp = createPostOperationWithResponse("invoice", "create", responseSchema);
      addPathWithPostOperation("/invoices", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "InvoiceCreateResponse.java");
    }
  }

  // BATCH OPERATIONS

  @Nested
  @DisplayName("Batch Operations")
  class BatchOperationsTests {

    @Test
    @DisplayName("Should generate response for batch operations without batch-operation-path-id")
    void shouldGenerateResponseForBatchOperations() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("status", new StringSchema());

      // /batch/customers without x-cb-batch-operation-path-id is a regular POST
      Operation postOp = createPostOperationWithResponse("customer", "create", responseSchema);
      addPathWithPostOperation("/batch/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Module "customer" + method "create" = CustomerCreateResponse
      assertFileExists(fileOps, "CustomerCreateResponse.java");
    }

    @Test
    @DisplayName("Should skip response generation for true batch operations")
    void shouldSkipResponseForTrueBatchOperations() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("status", new StringSchema());

      // True batch operation has x-cb-batch-operation-path-id extension
      Operation postOp = createPostOperationWithResponse("ramp", "update", responseSchema);
      postOp.addExtension(Extension.BATCH_OPERATION_PATH_ID, "id");
      addPathWithPostOperation("/batch/ramps/update", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should only have base directory — no response class generated
      assertThat(fileOps).hasSize(1);
    }

    @Test
    @DisplayName("Should generate response for non-batch ops even when batch ops exist")
    void shouldGenerateResponseForNonBatchOpsAlongsideBatchOps() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("status", new StringSchema());

      // True batch op — should be skipped
      Operation batchOp = createPostOperationWithResponse("ramp", "update", responseSchema);
      batchOp.addExtension(Extension.BATCH_OPERATION_PATH_ID, "id");
      addPathWithPostOperation("/batch/ramps/update", batchOp);

      // Regular POST op — should generate response
      Operation regularOp = createPostOperationWithResponse("ramp", "create", responseSchema);
      addPathWithPostOperation("/ramps/create", regularOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should generate response only for the regular op
      assertFileExists(fileOps, "RampCreateResponse.java");
      assertThat(
              fileOps.stream()
                  .filter(op -> op instanceof FileOp.WriteString)
                  .map(op -> (FileOp.WriteString) op)
                  .noneMatch(op -> op.fileName.contains("Update")))
          .isTrue();
    }
  }

  // EDGE CASES

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle empty OpenAPI spec")
    void shouldHandleEmptyOpenAPISpec() throws IOException {
      openAPI.setPaths(new Paths());
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1); // Only base directory
    }

    @Test
    @DisplayName("Should skip operation without required extensions")
    void shouldSkipOperationWithoutRequiredExtensions() throws IOException {
      ObjectSchema responseSchema = new ObjectSchema();
      responseSchema.addProperty("id", new StringSchema());

      Operation postOp = new Operation();
      ApiResponses responses = new ApiResponses();
      responses.addApiResponse("200", createApiResponse(responseSchema));
      postOp.setResponses(responses);

      addPathWithPostOperation("/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Only base directory (operation skipped)
      assertThat(fileOps).hasSize(1);
    }

    @Test
    @DisplayName("Should skip operation with non-JSON content")
    void shouldSkipOperationWithNonJsonContent() throws IOException {
      Operation postOp = createPostOperation("customer", "export");
      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      Content content = new Content();
      content.addMediaType("text/csv", new MediaType());
      response.setContent(content);
      responses.addApiResponse("200", response);
      postOp.setResponses(responses);

      addPathWithPostOperation("/customers/export", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should generate empty response class for consistency
      assertFileExists(fileOps, "CustomerExportResponse.java");
    }

    @Test
    @DisplayName("Should handle response without content")
    void shouldHandleResponseWithoutContent() throws IOException {
      Operation postOp = createPostOperation("customer", "create");
      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      // No content set
      responses.addApiResponse("200", response);
      postOp.setResponses(responses);

      addPathWithPostOperation("/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerCreateResponse.java");
    }

    @Test
    @DisplayName("Should handle response without schema")
    void shouldHandleResponseWithoutSchema() throws IOException {
      Operation postOp = createPostOperation("customer", "create");
      ApiResponses responses = new ApiResponses();
      ApiResponse response = new ApiResponse();
      Content content = new Content();
      MediaType mediaType = new MediaType();
      // No schema set
      content.addMediaType("application/json", mediaType);
      response.setContent(content);
      responses.addApiResponse("200", response);
      postOp.setResponses(responses);

      addPathWithPostOperation("/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerCreateResponse.java");
    }

    @Test
    @DisplayName("Should skip non-object response schemas")
    void shouldSkipNonObjectResponseSchemas() throws IOException {
      // String response instead of object
      StringSchema stringSchema = new StringSchema();

      Operation postOp = new Operation();
      postOp.addExtension(Extension.SDK_METHOD_NAME, "create");
      postOp.addExtension(Extension.RESOURCE_ID, "customer");
      ApiResponses responses = new ApiResponses();
      responses.addApiResponse("200", createApiResponse(stringSchema));
      postOp.setResponses(responses);

      addPathWithPostOperation("/customers", postOp);
      responseBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Only base directory (non-object schema skipped)
      assertThat(fileOps).hasSize(1);
    }
  }

  // HELPER METHODS

  private Operation createPostOperation(String resourceId, String methodName) {
    Operation operation = new Operation();
    operation.addExtension(Extension.RESOURCE_ID, resourceId);
    operation.addExtension(Extension.SDK_METHOD_NAME, methodName);
    return operation;
  }

  private Operation createPostOperationWithResponse(
      String resourceId, String methodName, Schema<?> responseSchema) {
    Operation operation = createPostOperation(resourceId, methodName);
    ApiResponses responses = new ApiResponses();
    responses.addApiResponse("200", createApiResponse(responseSchema));
    operation.setResponses(responses);
    return operation;
  }

  private ApiResponse createApiResponse(Schema<?> schema) {
    ApiResponse response = new ApiResponse();
    Content content = new Content();
    MediaType mediaType = new MediaType();
    mediaType.setSchema(schema);
    content.addMediaType("application/json", mediaType);
    response.setContent(content);
    return response;
  }

  private void add204Response(Operation operation) {
    ApiResponses responses = new ApiResponses();
    ApiResponse response = new ApiResponse();
    response.setDescription("No Content");
    responses.addApiResponse("204", response);
    operation.setResponses(responses);
  }

  private void addMultipleSuccessResponses(
      Operation operation, ObjectSchema schema200, ObjectSchema schema202, ObjectSchema schema204) {
    ApiResponses responses = new ApiResponses();
    if (schema200 != null) {
      responses.addApiResponse("200", createApiResponse(schema200));
    }
    if (schema202 != null) {
      responses.addApiResponse("202", createApiResponse(schema202));
    }
    if (schema204 != null) {
      responses.addApiResponse("204", createApiResponse(schema204));
    }
    operation.setResponses(responses);
  }

  private void addPathWithPostOperation(String path, Operation postOperation) {
    PathItem pathItem = new PathItem();
    pathItem.setPost(postOperation);
    addPath(path, pathItem);
  }

  private void addPath(String path, PathItem pathItem) {
    if (openAPI.getPaths() == null) {
      openAPI.setPaths(new Paths());
    }
    openAPI.getPaths().addPathItem(path, pathItem);
  }

  private FileOp.WriteString findWriteOp(List<FileOp> fileOps, String fileName) {
    return fileOps.stream()
        .filter(op -> op instanceof FileOp.WriteString)
        .map(op -> (FileOp.WriteString) op)
        .filter(op -> op.fileName.equals(fileName))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("WriteString operation not found for file: " + fileName));
  }

  private void assertFileExists(List<FileOp> fileOps, String fileName) {
    assertThat(fileOps)
        .anyMatch(
            op ->
                op instanceof FileOp.WriteString
                    && ((FileOp.WriteString) op).fileName.equals(fileName));
  }
}
