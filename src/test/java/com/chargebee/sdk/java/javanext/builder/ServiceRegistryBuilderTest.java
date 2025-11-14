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
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test suite for {@link ServiceRegistryBuilder}.
 *
 * <p>ServiceRegistryBuilder generates the ServiceRegistry class that manages dependency injection
 * and service lifecycle for all SDK services.
 */
@DisplayName("Service Registry Builder")
class ServiceRegistryBuilderTest {

  private ServiceRegistryBuilder registryBuilder;
  private Template template;
  private String outputPath;
  private OpenAPI openAPI;

  @BeforeEach
  void setUp() throws IOException {
    registryBuilder = new ServiceRegistryBuilder();
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
    template = handlebars.compile("core.service.registry.hbs");
  }

  // BUILDER CONFIGURATION AND VALIDATION

  @Nested
  @DisplayName("Builder Configuration")
  class BuilderConfigurationTests {

    @Test
    @DisplayName("Should configure output directory path with client subdirectory")
    void shouldConfigureOutputDirectoryPath() throws IOException {
      registryBuilder.withOutputDirectoryPath(outputPath);

      List<FileOp> fileOps = registryBuilder.withTemplate(template).build(openAPI);

      // Should create client directory
      assertThat(fileOps).isNotEmpty();
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
      FileOp.CreateDirectory dirOp = (FileOp.CreateDirectory) fileOps.get(0);
      assertThat(dirOp.basePath).isEqualTo(outputPath + "/v4/client");
    }

    @Test
    @DisplayName("Should configure template")
    void shouldConfigureTemplate() {
      ServiceRegistryBuilder builder = registryBuilder.withTemplate(template);

      assertThat(builder).isSameAs(registryBuilder);
    }

    @Test
    @DisplayName("Should support fluent builder chaining")
    void shouldSupportFluentBuilderChaining() {
      ServiceRegistryBuilder result =
          registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      assertThat(result).isSameAs(registryBuilder);
    }

    @Test
    @DisplayName("Should throw exception when output directory not configured")
    void shouldThrowExceptionWhenOutputDirectoryNotConfigured() {
      assertThatThrownBy(() -> registryBuilder.withTemplate(template).build(openAPI))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Output directory");
    }

    @Test
    @DisplayName("Should throw exception when template not configured")
    void shouldThrowExceptionWhenTemplateNotConfigured() {
      assertThatThrownBy(() -> registryBuilder.withOutputDirectoryPath(outputPath).build(openAPI))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Template");
    }

    @Test
    @DisplayName("Should throw exception when OpenAPI is null")
    void shouldThrowExceptionWhenOpenAPIIsNull() {
      assertThatThrownBy(
              () -> registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template).build(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("openApi");
    }

    @Test
    @DisplayName("Should throw exception when output directory is null")
    void shouldThrowExceptionWhenOutputDirectoryIsNull() {
      assertThatThrownBy(() -> registryBuilder.withOutputDirectoryPath(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("outputDirectoryPath");
    }

    @Test
    @DisplayName("Should throw exception when output directory is blank")
    void shouldThrowExceptionWhenOutputDirectoryIsBlank() {
      assertThatThrownBy(() -> registryBuilder.withOutputDirectoryPath("   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("outputDirectoryPath");
    }
  }

  // SERVICE REGISTRY GENERATION

  @Nested
  @DisplayName("Service Registry Generation")
  class ServiceRegistryGenerationTests {

    @Test
    @DisplayName("Should generate ServiceRegistry.java file")
    void shouldGenerateServiceRegistryFile() throws IOException {
      addResourceWithOperation("customer");

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      // Should have: CreateDirectory + ServiceRegistry.java
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);
      assertFileExists(fileOps, "ServiceRegistry.java");
    }

    @Test
    @DisplayName("Should include single resource in registry")
    void shouldIncludeSingleResourceInRegistry() throws IOException {
      addResourceWithOperation("customer");

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      FileOp.WriteString registryFile = findWriteOp(fileOps, "ServiceRegistry.java");

      // Should contain customer service references
      assertThat(registryFile.fileContent).containsIgnoringCase("customer");
      assertThat(registryFile.fileContent).containsIgnoringCase("CustomerService");
    }

    @Test
    @DisplayName("Should include multiple resources in registry")
    void shouldIncludeMultipleResourcesInRegistry() throws IOException {
      addResourceWithOperation("customer");
      addResourceWithOperation("invoice");
      addResourceWithOperation("subscription");

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      FileOp.WriteString registryFile = findWriteOp(fileOps, "ServiceRegistry.java");

      // All resources should be present
      assertThat(registryFile.fileContent).containsIgnoringCase("customer");
      assertThat(registryFile.fileContent).containsIgnoringCase("invoice");
      assertThat(registryFile.fileContent).containsIgnoringCase("subscription");

      assertThat(registryFile.fileContent).containsIgnoringCase("CustomerService");
      assertThat(registryFile.fileContent).containsIgnoringCase("InvoiceService");
      assertThat(registryFile.fileContent).containsIgnoringCase("SubscriptionService");
    }

    @Test
    @DisplayName("Should convert snake_case resource names correctly")
    void shouldConvertSnakeCaseResourceNames() throws IOException {
      addResourceWithOperation("payment_source");
      addResourceWithOperation("credit_note");

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      FileOp.WriteString registryFile = findWriteOp(fileOps, "ServiceRegistry.java");

      // Should convert payment_source → PaymentSourceService
      // Should convert credit_note → CreditNoteService
      assertThat(registryFile.fileContent).containsIgnoringCase("PaymentSourceService");
      assertThat(registryFile.fileContent).containsIgnoringCase("CreditNoteService");
    }

    @Test
    @DisplayName("Should only include resources that have operations")
    void shouldOnlyIncludeResourcesWithOperations() throws IOException {
      // Add a schema without any operations
      Schema<?> orphanSchema = new ObjectSchema();
      orphanSchema.addProperty("id", new StringSchema());
      openAPI.getComponents().getSchemas().put("OrphanResource", orphanSchema);

      // Add a resource with an operation
      addResourceWithOperation("customer");

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      FileOp.WriteString registryFile = findWriteOp(fileOps, "ServiceRegistry.java");

      // Should include customer (has operations)
      assertThat(registryFile.fileContent).containsIgnoringCase("customer");

      // Should NOT include OrphanResource (no operations)
      // (this depends on template implementation)
    }

    @Test
    @DisplayName("Should handle resources with operations but no schema definition")
    void shouldHandleResourcesWithOperationsButNoSchema() throws IOException {
      // Add operation for a resource that doesn't have a schema
      Operation operation = new Operation();
      operation.setOperationId("custom_operation");

      Map<String, Object> extensions = new HashMap<>();
      extensions.put(Extension.RESOURCE_ID, "custom_resource");
      extensions.put(Extension.OPERATION_METHOD_NAME, "customMethod");
      operation.setExtensions(extensions);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/custom-resource", pathItem);

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      FileOp.WriteString registryFile = findWriteOp(fileOps, "ServiceRegistry.java");

      // Should include custom_resource even without schema
      assertThat(registryFile.fileContent).containsIgnoringCase("customResource");
      assertThat(registryFile.fileContent).containsIgnoringCase("CustomResourceService");
    }

    @Test
    @DisplayName("Should deduplicate resources from multiple operations")
    void shouldDeduplicateResourcesFromMultipleOperations() throws IOException {
      // Add multiple operations for the same resource
      addResourceWithOperation("customer", "/customers", PathItem.HttpMethod.POST);
      addResourceWithOperation("customer", "/customers/{id}", PathItem.HttpMethod.GET);
      addResourceWithOperation("customer", "/customers/{id}", PathItem.HttpMethod.DELETE);

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      FileOp.WriteString registryFile = findWriteOp(fileOps, "ServiceRegistry.java");

      // Should only register customer service once
      assertThat(registryFile.fileContent).containsIgnoringCase("customer");
    }

    @Test
    @DisplayName("Should filter out error schemas (4xx, 5xx)")
    void shouldFilterOutErrorSchemas() throws IOException {
      // Add error schemas (should be filtered)
      ObjectSchema error400 = new ObjectSchema();
      error400.addProperty("message", new StringSchema());
      openAPI.getComponents().getSchemas().put("400Error", error400);

      ObjectSchema error500 = new ObjectSchema();
      error500.addProperty("message", new StringSchema());
      openAPI.getComponents().getSchemas().put("500Error", error500);

      // Add valid resource
      addResourceWithOperation("customer");

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      FileOp.WriteString registryFile = findWriteOp(fileOps, "ServiceRegistry.java");

      // Should include customer
      assertThat(registryFile.fileContent).containsIgnoringCase("customer");

      // Should NOT include error schemas
      assertThat(registryFile.fileContent).doesNotContain("400Error");
      assertThat(registryFile.fileContent).doesNotContain("500Error");
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
      emptyOpenAPI.setComponents(new Components().schemas(new HashMap<>()));

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(emptyOpenAPI);

      // Should still generate ServiceRegistry.java even with no resources
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);
      assertFileExists(fileOps, "ServiceRegistry.java");
    }

    @Test
    @DisplayName("Should handle null paths in OpenAPI")
    void shouldHandleNullPathsInOpenAPI() throws IOException {
      OpenAPI apiWithNullPaths = new OpenAPI();
      apiWithNullPaths.setPaths(null);
      apiWithNullPaths.setComponents(new Components().schemas(new HashMap<>()));

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(apiWithNullPaths);

      // Should still generate file
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);
      assertFileExists(fileOps, "ServiceRegistry.java");
    }

    @Test
    @DisplayName("Should handle null components in OpenAPI")
    void shouldHandleNullComponentsInOpenAPI() throws IOException {
      OpenAPI apiWithNullComponents = new OpenAPI();
      apiWithNullComponents.setPaths(new Paths());
      apiWithNullComponents.setComponents(null);

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(apiWithNullComponents);

      // Should still generate file
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);
      assertFileExists(fileOps, "ServiceRegistry.java");
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

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      // Should generate file without errors
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);
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

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      // Should generate file without errors
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should handle operations missing OPERATION_METHOD_NAME extension")
    void shouldHandleOperationsMissingMethodNameExtension() throws IOException {
      Operation operation = new Operation();
      operation.setOperationId("someOperation");

      Map<String, Object> extensions = new HashMap<>();
      extensions.put(Extension.RESOURCE_ID, "customer");
      // Missing OPERATION_METHOD_NAME
      operation.setExtensions(extensions);

      PathItem pathItem = new PathItem();
      pathItem.setGet(operation);
      openAPI.getPaths().addPathItem("/customers", pathItem);

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      // Should generate file but not include this resource (requires both extensions)
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should handle all HTTP methods (GET, POST, PUT, DELETE, PATCH)")
    void shouldHandleAllHttpMethods() throws IOException {
      addResourceWithOperation("customer", "/customers", PathItem.HttpMethod.GET);
      addResourceWithOperation("invoice", "/invoices", PathItem.HttpMethod.POST);
      addResourceWithOperation("subscription", "/subscriptions", PathItem.HttpMethod.PUT);
      addResourceWithOperation("plan", "/plans", PathItem.HttpMethod.DELETE);
      addResourceWithOperation("addon", "/addons", PathItem.HttpMethod.PATCH);

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      FileOp.WriteString registryFile = findWriteOp(fileOps, "ServiceRegistry.java");

      // All resources should be discovered regardless of HTTP method
      assertThat(registryFile.fileContent).containsIgnoringCase("customer");
      assertThat(registryFile.fileContent).containsIgnoringCase("invoice");
      assertThat(registryFile.fileContent).containsIgnoringCase("subscription");
      assertThat(registryFile.fileContent).containsIgnoringCase("plan");
      assertThat(registryFile.fileContent).containsIgnoringCase("addon");
    }

    @Test
    @DisplayName("Should handle schemas without properties")
    void shouldHandleSchemasWithoutProperties() throws IOException {
      // Add schema without properties
      Schema<?> emptySchema = new Schema<>();
      openAPI.getComponents().getSchemas().put("EmptySchema", emptySchema);

      // Add valid resource
      addResourceWithOperation("customer");

      registryBuilder.withOutputDirectoryPath(outputPath).withTemplate(template);

      List<FileOp> fileOps = registryBuilder.build(openAPI);

      // Should generate file without errors
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);
    }
  }

  // HELPER METHODS

  private void addResourceWithOperation(String resourceId) {
    addResourceWithOperation(resourceId, "/" + resourceId + "s", PathItem.HttpMethod.POST);
  }

  private void addResourceWithOperation(String resourceId, String path, PathItem.HttpMethod method) {
    // Add schema
    ObjectSchema schema = new ObjectSchema();
    schema.addProperty("id", new StringSchema());
    String schemaName = toUpperCamelCase(resourceId);
    openAPI.getComponents().getSchemas().put(schemaName, schema);

    // Add operation
    Operation operation = new Operation();
    operation.setOperationId(resourceId + "_operation");

    Map<String, Object> extensions = new HashMap<>();
    extensions.put(Extension.RESOURCE_ID, resourceId);
    extensions.put(Extension.OPERATION_METHOD_NAME, "someMethod");
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

  private String toUpperCamelCase(String snakeCase) {
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;
    for (char c : snakeCase.toCharArray()) {
      if (c == '_') {
        capitalizeNext = true;
      } else {
        result.append(capitalizeNext ? Character.toUpperCase(c) : c);
        capitalizeNext = false;
      }
    }
    return result.toString();
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
