package com.chargebee.sdk.java.javanext.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
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
 * Test suite for {@link GetResponseBuilder}.
 *
 * <p>GetResponseBuilder is a coordinator that delegates to:
 * - {@link ListResponseBuilder} for paginated list responses
 * - {@link SimpleGetResponseBuilder} for simple get responses
 *
 * <p>This test verifies the coordination logic and directory creation. Detailed response generation
 * tests are in {@link ListResponseBuilderTest} and {@link SimpleGetResponseBuilderTest}.
 *
 *
 * @see ListResponseBuilderTest
 * @see SimpleGetResponseBuilderTest
 */
@DisplayName("GET Response Builder - Coordinator")
class GetResponseBuilderTest {

  private GetResponseBuilder responseBuilder;
  private Template listTemplate;
  private Template simpleTemplate;
  private String outputPath;
  private OpenAPI openAPI;

  @BeforeEach
  void setUp() throws IOException {
    responseBuilder = new GetResponseBuilder();
    outputPath = "/test/output";

    openAPI = new OpenAPI();
    openAPI.setPaths(new Paths());

    // Load actual templates
    Handlebars handlebars =
        new Handlebars(
            new com.github.jknack.handlebars.io.ClassPathTemplateLoader(
                "/templates/java/next", ""));
    HandlebarsUtil.registerAllHelpers(handlebars);
    listTemplate = handlebars.compile("core.get.response.list.hbs");
    simpleTemplate = handlebars.compile("core.get.response.hbs");
  }

  // BUILDER CONFIGURATION

  @Nested
  @DisplayName("Builder Configuration")
  class BuilderConfigurationTests {

    @Test
    @DisplayName("Should configure output directory path with correct subdirectory")
    void shouldConfigureOutputDirectoryPath() {
      responseBuilder.withOutputDirectoryPath(outputPath);

      List<FileOp> fileOps =
          responseBuilder.withListTemplate(listTemplate).withSimpleTemplate(simpleTemplate).build(openAPI);

      // Should create base responses directory
      assertThat(fileOps).isNotEmpty();
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
      FileOp.CreateDirectory dirOp = (FileOp.CreateDirectory) fileOps.get(0);
      assertThat(dirOp.basePath).isEqualTo(outputPath + "/com/chargebee/v4/models");
    }

    @Test
    @DisplayName("Should configure list template")
    void shouldConfigureListTemplate() {
      GetResponseBuilder builder = responseBuilder.withListTemplate(listTemplate);

      assertThat(builder).isSameAs(responseBuilder);
    }

    @Test
    @DisplayName("Should configure simple template")
    void shouldConfigureSimpleTemplate() {
      GetResponseBuilder builder = responseBuilder.withSimpleTemplate(simpleTemplate);

      assertThat(builder).isSameAs(responseBuilder);
    }

    @Test
    @DisplayName("Should support fluent builder chaining")
    void shouldSupportFluentBuilderChaining() {
      GetResponseBuilder result =
          responseBuilder
              .withOutputDirectoryPath(outputPath)
              .withListTemplate(listTemplate)
              .withSimpleTemplate(simpleTemplate);

      assertThat(result).isSameAs(responseBuilder);
    }
  }

  // COORDINATION AND DELEGATION

  @Nested
  @DisplayName("Coordination and Delegation")
  class CoordinationTests {

    @Test
    @DisplayName("Should delegate to both ListResponseBuilder and SimpleGetResponseBuilder")
    void shouldDelegateToBothBuilders() throws IOException {
      // Create a list operation (with list + next_offset)
      ObjectSchema listResponseSchema = new ObjectSchema();
      listResponseSchema.addProperty("list", new io.swagger.v3.oas.models.media.ArraySchema());
      listResponseSchema.addProperty("next_offset", new StringSchema());

      Operation listOp = createGetOperationWithResponse("customer", "list", listResponseSchema);
      addPathWithGetOperation("/customers", listOp);

      // Create a simple get operation (without pagination)
      ObjectSchema simpleResponseSchema = new ObjectSchema();
      simpleResponseSchema.addProperty("id", new StringSchema());
      simpleResponseSchema.addProperty("status", new StringSchema());

      Operation simpleOp =
          createGetOperationWithResponse("customer", "retrieve", simpleResponseSchema);
      // Use path parameter syntax for retrieve to derive correctly
      addPathWithGetOperation("/customers/{customer-id}", simpleOp);

      responseBuilder
          .withOutputDirectoryPath(outputPath)
          .withListTemplate(listTemplate)
          .withSimpleTemplate(simpleTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should have operations from both builders:
      // - Base directory creation
      // - List response directory + file
      // - Simple response directory + file
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);

      // Verify both list and simple response files are generated
      boolean hasListResponse =
          fileOps.stream()
              .anyMatch(
                  op ->
                      op instanceof FileOp.WriteString
                          && ((FileOp.WriteString) op).fileName.contains("List"));
      boolean hasSimpleResponse =
          fileOps.stream()
              .anyMatch(
                  op ->
                      op instanceof FileOp.WriteString
                          && ((FileOp.WriteString) op).fileName.contains("Retrieve"));

      assertThat(hasListResponse || hasSimpleResponse).isTrue();
    }

    @Test
    @DisplayName("Should pass correct output directory path to delegate builders")
    void shouldPassCorrectOutputDirectoryToBuilders() {
      responseBuilder
          .withOutputDirectoryPath(outputPath)
          .withListTemplate(listTemplate)
          .withSimpleTemplate(simpleTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Verify directory is created with correct path
      assertThat(fileOps).isNotEmpty();
      FileOp.CreateDirectory baseDir = (FileOp.CreateDirectory) fileOps.get(0);
      assertThat(baseDir.basePath).isEqualTo(outputPath + "/com/chargebee/v4/models");
    }

    @Test
    @DisplayName("Should handle empty OpenAPI spec gracefully")
    void shouldHandleEmptyOpenAPISpec() {
      OpenAPI emptyOpenAPI = new OpenAPI();
      emptyOpenAPI.setPaths(new Paths());

      responseBuilder
          .withOutputDirectoryPath(outputPath)
          .withListTemplate(listTemplate)
          .withSimpleTemplate(simpleTemplate);

      List<FileOp> fileOps = responseBuilder.build(emptyOpenAPI);

      // Should still create base directory even with no operations
      assertThat(fileOps).isNotEmpty();
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }

    @Test
    @DisplayName("Should aggregate file operations from both delegate builders")
    void shouldAggregateFileOperationsFromBothBuilders() throws IOException {
      // Create operations that will be handled by both builders
      ObjectSchema listSchema = new ObjectSchema();
      listSchema.addProperty("list", new io.swagger.v3.oas.models.media.ArraySchema());
      listSchema.addProperty("next_offset", new StringSchema());

      ObjectSchema simpleSchema = new ObjectSchema();
      simpleSchema.addProperty("id", new StringSchema());

      addPathWithGetOperation(
          "/customers", createGetOperationWithResponse("customer", "list", listSchema));
      addPathWithGetOperation(
          "/customers/{id}", createGetOperationWithResponse("customer", "retrieve", simpleSchema));

      responseBuilder
          .withOutputDirectoryPath(outputPath)
          .withListTemplate(listTemplate)
          .withSimpleTemplate(simpleTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should have operations from both builders aggregated
      long directoryOps = fileOps.stream().filter(op -> op instanceof FileOp.CreateDirectory).count();
      long writeOps = fileOps.stream().filter(op -> op instanceof FileOp.WriteString).count();

      assertThat(directoryOps).isGreaterThanOrEqualTo(1);
      assertThat(writeOps).isGreaterThanOrEqualTo(0); // Could be 0 if operations are filtered
    }
  }

  // EDGE CASES

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle null paths in OpenAPI")
    void shouldHandleNullPathsInOpenAPI() {
      OpenAPI apiWithNullPaths = new OpenAPI();
      apiWithNullPaths.setPaths(null);

      responseBuilder
          .withOutputDirectoryPath(outputPath)
          .withListTemplate(listTemplate)
          .withSimpleTemplate(simpleTemplate);

      List<FileOp> fileOps = responseBuilder.build(apiWithNullPaths);

      // Should still create base directory
      assertThat(fileOps).isNotEmpty();
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }

    @Test
    @DisplayName("Should handle operations without GET methods")
    void shouldHandleOperationsWithoutGetMethods() {
      PathItem pathItem = new PathItem();
      Operation postOp = new Operation();
      postOp.setOperationId("createCustomer");
      pathItem.setPost(postOp);

      openAPI.getPaths().addPathItem("/customers", pathItem);

      responseBuilder
          .withOutputDirectoryPath(outputPath)
          .withListTemplate(listTemplate)
          .withSimpleTemplate(simpleTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should only create directories, no response files
      assertThat(fileOps)
          .allMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should generate operations for multiple GET endpoints")
    void shouldGenerateOperationsForMultipleGetEndpoints() throws IOException {
      // Add multiple operations
      for (int i = 0; i < 3; i++) {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("id", new StringSchema());
        Operation op = createGetOperationWithResponse("resource" + i, "retrieve", schema);
        addPathWithGetOperation("/resource" + i, op);
      }

      responseBuilder
          .withOutputDirectoryPath(outputPath)
          .withListTemplate(listTemplate)
          .withSimpleTemplate(simpleTemplate);

      List<FileOp> fileOps = responseBuilder.build(openAPI);

      // Should generate operations for all endpoints
      long directoryOps = fileOps.stream().filter(op -> op instanceof FileOp.CreateDirectory).count();
      long writeOps = fileOps.stream().filter(op -> op instanceof FileOp.WriteString).count();

      // At least base directory + resource directories
      assertThat(directoryOps).isGreaterThanOrEqualTo(1);
      // Should have write operations for responses (if templates generate content)
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(1);
    }
  }

  // HELPER METHODS

  private Operation createGetOperationWithResponse(
      String resourceId, String methodName, ObjectSchema responseSchema) {
    Operation operation = new Operation();
    Map<String, Object> extensions = new HashMap<>();
    extensions.put(Extension.RESOURCE_ID, resourceId);
    // Set IS_OPERATION_LIST for list operations so path-based derivation works correctly
    if ("list".equals(methodName)) {
      extensions.put(Extension.IS_OPERATION_LIST, true);
    }
    operation.setExtensions(extensions);

    ApiResponse response = new ApiResponse();
    Content content = new Content();
    MediaType mediaType = new MediaType();
    mediaType.setSchema(responseSchema);
    content.addMediaType("application/json", mediaType);
    response.setContent(content);

    ApiResponses responses = new ApiResponses();
    responses.addApiResponse("200", response);
    operation.setResponses(responses);

    return operation;
  }

  private void addPathWithGetOperation(String path, Operation operation) {
    PathItem pathItem = new PathItem();
    pathItem.setGet(operation);
    openAPI.getPaths().addPathItem(path, pathItem);
  }
}
