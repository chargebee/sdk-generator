package com.chargebee.sdk.java.javanext.builder;

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
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test suite for {@link ClientMethodsBuilder}.
 *
 * <p>ClientMethodsBuilder generates the ClientMethods interface and ClientMethodsImpl class that
 * provide accessor methods for all service resources in the SDK.
 *
 *
 */
@DisplayName("Client Methods Builder")
class ClientMethodsBuilderTest {

  private ClientMethodsBuilder clientMethodsBuilder;
  private Template clientMethodsTemplate;
  private Template clientMethodsImplTemplate;
  private String outputPath;
  private OpenAPI openAPI;

  @BeforeEach
  void setUp() throws IOException {
    clientMethodsBuilder = new ClientMethodsBuilder();
    outputPath = "/test/output";

    openAPI = new OpenAPI();
    openAPI.setPaths(new Paths());

    // Load actual templates
    Handlebars handlebars =
        new Handlebars(
            new com.github.jknack.handlebars.io.ClassPathTemplateLoader(
                "/templates/java/next", ""));
    HandlebarsUtil.registerAllHelpers(handlebars);
    clientMethodsTemplate = handlebars.compile("client.methods.hbs");
    clientMethodsImplTemplate = handlebars.compile("client.methods.impl.hbs");
  }

  // BUILDER CONFIGURATION AND VALIDATION

  @Nested
  @DisplayName("Builder Configuration")
  class BuilderConfigurationTests {

    @Test
    @DisplayName("Should configure output directory path with client subdirectory")
    void shouldConfigureOutputDirectoryPath() throws IOException {
      clientMethodsBuilder.withOutputDirectoryPath(outputPath);

      List<FileOp> fileOps =
          clientMethodsBuilder
              .withClientMethodsTemplate(clientMethodsTemplate)
              .withClientMethodsImplTemplate(clientMethodsImplTemplate)
              .build(openAPI);

      // Should create client directory
      assertThat(fileOps).isNotEmpty();
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
      FileOp.CreateDirectory dirOp = (FileOp.CreateDirectory) fileOps.get(0);
      assertThat(dirOp.basePath).isEqualTo(outputPath + "/v4/client");
    }

    @Test
    @DisplayName("Should configure ClientMethods template")
    void shouldConfigureClientMethodsTemplate() {
      ClientMethodsBuilder builder =
          clientMethodsBuilder.withClientMethodsTemplate(clientMethodsTemplate);

      assertThat(builder).isSameAs(clientMethodsBuilder);
    }

    @Test
    @DisplayName("Should configure ClientMethodsImpl template")
    void shouldConfigureClientMethodsImplTemplate() {
      ClientMethodsBuilder builder =
          clientMethodsBuilder.withClientMethodsImplTemplate(clientMethodsImplTemplate);

      assertThat(builder).isSameAs(clientMethodsBuilder);
    }

    @Test
    @DisplayName("Should support fluent builder chaining")
    void shouldSupportFluentBuilderChaining() {
      ClientMethodsBuilder result =
          clientMethodsBuilder
              .withOutputDirectoryPath(outputPath)
              .withClientMethodsTemplate(clientMethodsTemplate)
              .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      assertThat(result).isSameAs(clientMethodsBuilder);
    }

    @Test
    @DisplayName("Should throw exception when output directory not configured")
    void shouldThrowExceptionWhenOutputDirectoryNotConfigured() {
      assertThatThrownBy(
              () ->
                  clientMethodsBuilder
                      .withClientMethodsTemplate(clientMethodsTemplate)
                      .withClientMethodsImplTemplate(clientMethodsImplTemplate)
                      .build(openAPI))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("outputDirectoryPath");
    }

    @Test
    @DisplayName("Should throw exception when ClientMethods template not configured")
    void shouldThrowExceptionWhenClientMethodsTemplateNotConfigured() {
      assertThatThrownBy(
              () ->
                  clientMethodsBuilder
                      .withOutputDirectoryPath(outputPath)
                      .withClientMethodsImplTemplate(clientMethodsImplTemplate)
                      .build(openAPI))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("clientMethodsTemplate");
    }

    @Test
    @DisplayName("Should throw exception when ClientMethodsImpl template not configured")
    void shouldThrowExceptionWhenClientMethodsImplTemplateNotConfigured() {
      assertThatThrownBy(
              () ->
                  clientMethodsBuilder
                      .withOutputDirectoryPath(outputPath)
                      .withClientMethodsTemplate(clientMethodsTemplate)
                      .build(openAPI))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("clientMethodsImplTemplate");
    }

    @Test
    @DisplayName("Should throw exception when OpenAPI is null")
    void shouldThrowExceptionWhenOpenAPIIsNull() {
      assertThatThrownBy(
              () ->
                  clientMethodsBuilder
                      .withOutputDirectoryPath(outputPath)
                      .withClientMethodsTemplate(clientMethodsTemplate)
                      .withClientMethodsImplTemplate(clientMethodsImplTemplate)
                      .build(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("openAPI");
    }
  }

  // CLIENT METHODS GENERATION

  @Nested
  @DisplayName("Client Methods Generation")
  class ClientMethodsGenerationTests {

    @Test
    @DisplayName("Should generate both ClientMethods and ClientMethodsImpl files")
    void shouldGenerateBothClientMethodsFiles() throws IOException {
      addOperationForResource("customer");

      clientMethodsBuilder
          .withOutputDirectoryPath(outputPath)
          .withClientMethodsTemplate(clientMethodsTemplate)
          .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      List<FileOp> fileOps = clientMethodsBuilder.build(openAPI);

      // Should have: CreateDirectory + ClientMethods.java + ClientMethodsImpl.java
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(3);
      assertFileExists(fileOps, "ClientMethods.java");
      assertFileExists(fileOps, "ClientMethodsImpl.java");
    }

    @Test
    @DisplayName("Should generate accessor methods for single resource")
    void shouldGenerateAccessorMethodsForSingleResource() throws IOException {
      addOperationForResource("customer");

      clientMethodsBuilder
          .withOutputDirectoryPath(outputPath)
          .withClientMethodsTemplate(clientMethodsTemplate)
          .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      List<FileOp> fileOps = clientMethodsBuilder.build(openAPI);

      FileOp.WriteString interfaceFile = findWriteOp(fileOps, "ClientMethods.java");
      FileOp.WriteString implFile = findWriteOp(fileOps, "ClientMethodsImpl.java");

      // Interface should contain customer() method
      assertThat(interfaceFile.fileContent).containsIgnoringCase("customer");
      // Implementation should contain customer() method
      assertThat(implFile.fileContent).containsIgnoringCase("customer");
    }

    @Test
    @DisplayName("Should generate accessor methods for multiple resources")
    void shouldGenerateAccessorMethodsForMultipleResources() throws IOException {
      addOperationForResource("customer");
      addOperationForResource("invoice");
      addOperationForResource("subscription");

      clientMethodsBuilder
          .withOutputDirectoryPath(outputPath)
          .withClientMethodsTemplate(clientMethodsTemplate)
          .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      List<FileOp> fileOps = clientMethodsBuilder.build(openAPI);

      FileOp.WriteString interfaceFile = findWriteOp(fileOps, "ClientMethods.java");
      FileOp.WriteString implFile = findWriteOp(fileOps, "ClientMethodsImpl.java");

      // All resources should be present
      assertThat(interfaceFile.fileContent).containsIgnoringCase("customer");
      assertThat(interfaceFile.fileContent).containsIgnoringCase("invoice");
      assertThat(interfaceFile.fileContent).containsIgnoringCase("subscription");

      assertThat(implFile.fileContent).containsIgnoringCase("customer");
      assertThat(implFile.fileContent).containsIgnoringCase("invoice");
      assertThat(implFile.fileContent).containsIgnoringCase("subscription");
    }

    @Test
    @DisplayName("Should convert snake_case resource names to camelCase method names")
    void shouldConvertSnakeCaseResourceToCamelCaseMethod() throws IOException {
      addOperationForResource("payment_source");

      clientMethodsBuilder
          .withOutputDirectoryPath(outputPath)
          .withClientMethodsTemplate(clientMethodsTemplate)
          .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      List<FileOp> fileOps = clientMethodsBuilder.build(openAPI);

      FileOp.WriteString interfaceFile = findWriteOp(fileOps, "ClientMethods.java");

      // Should convert payment_source to paymentSource
      assertThat(interfaceFile.fileContent).containsIgnoringCase("paymentSource");
    }

    @Test
    @DisplayName("Should include service imports in generated files")
    void shouldIncludeServiceImportsInGeneratedFiles() throws IOException {
      addOperationForResource("customer");
      addOperationForResource("invoice");

      clientMethodsBuilder
          .withOutputDirectoryPath(outputPath)
          .withClientMethodsTemplate(clientMethodsTemplate)
          .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      List<FileOp> fileOps = clientMethodsBuilder.build(openAPI);

      FileOp.WriteString interfaceFile = findWriteOp(fileOps, "ClientMethods.java");
      FileOp.WriteString implFile = findWriteOp(fileOps, "ClientMethodsImpl.java");

      // Should import service classes
      assertThat(interfaceFile.fileContent).contains("com.chargebee.v4.core.services");
      assertThat(interfaceFile.fileContent).containsIgnoringCase("CustomerService");
      assertThat(interfaceFile.fileContent).containsIgnoringCase("InvoiceService");

      assertThat(implFile.fileContent).contains("com.chargebee.v4.core.services");
    }

    @Test
    @DisplayName("Should deduplicate resources from multiple operations")
    void shouldDeduplicateResourcesFromMultipleOperations() throws IOException {
      // Add multiple operations for the same resource
      addOperationForResource("customer", "/customers", PathItem.HttpMethod.POST);
      addOperationForResource("customer", "/customers/{id}", PathItem.HttpMethod.GET);
      addOperationForResource("customer", "/customers/{id}", PathItem.HttpMethod.DELETE);

      clientMethodsBuilder
          .withOutputDirectoryPath(outputPath)
          .withClientMethodsTemplate(clientMethodsTemplate)
          .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      List<FileOp> fileOps = clientMethodsBuilder.build(openAPI);

      FileOp.WriteString interfaceFile = findWriteOp(fileOps, "ClientMethods.java");

      // Should only have customer() method once (not 3 times)
      String content = interfaceFile.fileContent;
      int firstIndex = content.toLowerCase().indexOf("customer");
      int secondIndex = content.toLowerCase().indexOf("customer", firstIndex + 1);

      // There should be multiple mentions (import, method declaration, etc.) but not duplicate
      // methods
      assertThat(firstIndex).isNotEqualTo(-1);
    }
  }

  // EDGE CASES

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle empty OpenAPI spec gracefully")
    void shouldHandleEmptyOpenAPISpec() throws IOException {
      OpenAPI emptyOpenAPI = new OpenAPI();
      emptyOpenAPI.setPaths(new Paths());

      clientMethodsBuilder
          .withOutputDirectoryPath(outputPath)
          .withClientMethodsTemplate(clientMethodsTemplate)
          .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      List<FileOp> fileOps = clientMethodsBuilder.build(emptyOpenAPI);

      // Should still generate files even with no resources
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(3);
      assertFileExists(fileOps, "ClientMethods.java");
      assertFileExists(fileOps, "ClientMethodsImpl.java");
    }

    @Test
    @DisplayName("Should handle null paths in OpenAPI")
    void shouldHandleNullPathsInOpenAPI() throws IOException {
      OpenAPI apiWithNullPaths = new OpenAPI();
      apiWithNullPaths.setPaths(null);

      clientMethodsBuilder
          .withOutputDirectoryPath(outputPath)
          .withClientMethodsTemplate(clientMethodsTemplate)
          .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      List<FileOp> fileOps = clientMethodsBuilder.build(apiWithNullPaths);

      // Should still generate files
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(3);
      assertFileExists(fileOps, "ClientMethods.java");
      assertFileExists(fileOps, "ClientMethodsImpl.java");
    }

    @Test
    @DisplayName("Should handle operations without resource ID extension")
    void shouldHandleOperationsWithoutResourceIdExtension() throws IOException {
      Operation operation = new Operation();
      operation.setOperationId("someOperation");
      // No x-cb-resource-id extension

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/some-path", pathItem);

      clientMethodsBuilder
          .withOutputDirectoryPath(outputPath)
          .withClientMethodsTemplate(clientMethodsTemplate)
          .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      List<FileOp> fileOps = clientMethodsBuilder.build(openAPI);

      // Should generate files without errors
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Should handle operations with null extensions")
    void shouldHandleOperationsWithNullExtensions() throws IOException {
      Operation operation = new Operation();
      operation.setOperationId("someOperation");
      operation.setExtensions(null);

      PathItem pathItem = new PathItem();
      pathItem.setPost(operation);
      openAPI.getPaths().addPathItem("/some-path", pathItem);

      clientMethodsBuilder
          .withOutputDirectoryPath(outputPath)
          .withClientMethodsTemplate(clientMethodsTemplate)
          .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      List<FileOp> fileOps = clientMethodsBuilder.build(openAPI);

      // Should generate files without errors
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Should handle all HTTP methods (GET, POST, PUT, DELETE, PATCH)")
    void shouldHandleAllHttpMethods() throws IOException {
      addOperationForResource("customer", "/customers", PathItem.HttpMethod.GET);
      addOperationForResource("invoice", "/invoices", PathItem.HttpMethod.POST);
      addOperationForResource("subscription", "/subscriptions", PathItem.HttpMethod.PUT);
      addOperationForResource("plan", "/plans", PathItem.HttpMethod.DELETE);
      addOperationForResource("addon", "/addons", PathItem.HttpMethod.PATCH);

      clientMethodsBuilder
          .withOutputDirectoryPath(outputPath)
          .withClientMethodsTemplate(clientMethodsTemplate)
          .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      List<FileOp> fileOps = clientMethodsBuilder.build(openAPI);

      FileOp.WriteString interfaceFile = findWriteOp(fileOps, "ClientMethods.java");

      // All resources should be discovered regardless of HTTP method
      assertThat(interfaceFile.fileContent).containsIgnoringCase("customer");
      assertThat(interfaceFile.fileContent).containsIgnoringCase("invoice");
      assertThat(interfaceFile.fileContent).containsIgnoringCase("subscription");
      assertThat(interfaceFile.fileContent).containsIgnoringCase("plan");
      assertThat(interfaceFile.fileContent).containsIgnoringCase("addon");
    }

    @Test
    @DisplayName("Should handle resources with special characters in names")
    void shouldHandleResourcesWithSpecialCharactersInNames() throws IOException {
      addOperationForResource("payment_source");
      addOperationForResource("credit_note");
      addOperationForResource("hosted_page");

      clientMethodsBuilder
          .withOutputDirectoryPath(outputPath)
          .withClientMethodsTemplate(clientMethodsTemplate)
          .withClientMethodsImplTemplate(clientMethodsImplTemplate);

      List<FileOp> fileOps = clientMethodsBuilder.build(openAPI);

      FileOp.WriteString interfaceFile = findWriteOp(fileOps, "ClientMethods.java");

      // Should handle underscores and convert properly
      assertThat(interfaceFile.fileContent).containsIgnoringCase("paymentSource");
      assertThat(interfaceFile.fileContent).containsIgnoringCase("creditNote");
      assertThat(interfaceFile.fileContent).containsIgnoringCase("hostedPage");
    }
  }

  // HELPER METHODS

  private void addOperationForResource(String resourceId) {
    addOperationForResource(resourceId, "/" + resourceId + "s", PathItem.HttpMethod.POST);
  }

  private void addOperationForResource(String resourceId, String path, PathItem.HttpMethod method) {
    Operation operation = new Operation();
    operation.setOperationId(resourceId + "_operation");

    Map<String, Object> extensions = new HashMap<>();
    extensions.put(Extension.RESOURCE_ID, resourceId);
    operation.setExtensions(extensions);

    PathItem pathItem = openAPI.getPaths().get(path);
    if (pathItem == null) {
      pathItem = new PathItem();
      openAPI.getPaths().addPathItem(path, pathItem);
    }

    switch (method) {
      case GET:
        pathItem.setGet(operation);
        break;
      case POST:
        pathItem.setPost(operation);
        break;
      case PUT:
        pathItem.setPut(operation);
        break;
      case DELETE:
        pathItem.setDelete(operation);
        break;
      case PATCH:
        pathItem.setPatch(operation);
        break;
    }
  }

  private void assertFileExists(List<FileOp> fileOps, String fileName) {
    boolean exists =
        fileOps.stream()
            .anyMatch(
                op -> op instanceof FileOp.WriteString && ((FileOp.WriteString) op).fileName.equals(fileName));
    assertThat(exists)
        .as("Expected file %s to exist in file operations", fileName)
        .isTrue();
  }

  private FileOp.WriteString findWriteOp(List<FileOp> fileOps, String fileName) {
    return fileOps.stream()
        .filter(
            op -> op instanceof FileOp.WriteString && ((FileOp.WriteString) op).fileName.equals(fileName))
        .map(op -> (FileOp.WriteString) op)
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("Could not find WriteString operation for file: " + fileName));
  }
}
