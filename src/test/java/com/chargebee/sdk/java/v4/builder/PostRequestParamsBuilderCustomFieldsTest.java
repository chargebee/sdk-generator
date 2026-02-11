package com.chargebee.sdk.java.v4.builder;

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
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.*;

/**
 * Test suite for custom fields support in POST request parameters.
 */
class PostRequestParamsBuilderCustomFieldsTest {

  private PostRequestParamsBuilder paramsBuilder;
  private Template mockTemplate;
  private OpenAPI openAPI;
  private String outputPath;

  @BeforeEach
  void setUp() throws IOException {
    paramsBuilder = new PostRequestParamsBuilder();
    outputPath = "/test/output";
    openAPI = new OpenAPI();

    Handlebars handlebars =
        new Handlebars(
            new com.github.jknack.handlebars.io.ClassPathTemplateLoader(
                "/templates/java/next", ""));
    HandlebarsUtil.registerAllHelpers(handlebars);
    mockTemplate = handlebars.compile("core.post.params.builder.hbs");
  }

  @Nested
  @DisplayName("Custom Fields Support in POST Params")
  class CustomFieldsSupportTests {

    @Test
    void shouldAddCustomFieldMethodsToParamsWithCustomFieldsExtension() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("first_name", new StringSchema());
      requestSchema.addProperty("email", new StringSchema());
      requestSchema.setAdditionalProperties(true);
      requestSchema.addExtension("x-cb-is-custom-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create_customer");
      postOp.addExtension(Extension.SDK_METHOD_NAME, "create");
      postOp.addExtension(Extension.RESOURCE_ID, "customer");
      postOp.setRequestBody(requestBody);

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/customers", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerCreateParams.java");
      assertThat(writeOp.fileContent).contains("public CustomerCreateBuilder customField(");
      assertThat(writeOp.fileContent).contains("public CustomerCreateBuilder customFields(");
      assertThat(writeOp.fileContent).contains("Custom field name must start with 'cf_'");
    }

    @Test
    void shouldNotAddCustomFieldMethodsToParamsWithoutExtension() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("street", new StringSchema());
      requestSchema.addProperty("city", new StringSchema());

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create_address");
      postOp.addExtension(Extension.SDK_METHOD_NAME, "create");
      postOp.addExtension(Extension.RESOURCE_ID, "address");
      postOp.setRequestBody(requestBody);

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/addresses", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "AddressCreateParams.java");
      assertThat(writeOp.fileContent).doesNotContain("customField");
      assertThat(writeOp.fileContent).doesNotContain("customFields");
    }

    @Test
    void shouldIncludeValidationForCustomFieldNames() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("id", new StringSchema());
      requestSchema.setAdditionalProperties(true);
      requestSchema.addExtension("x-cb-is-custom-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create");
      postOp.addExtension(Extension.SDK_METHOD_NAME, "create");
      postOp.addExtension(Extension.RESOURCE_ID, "subscription");
      postOp.setRequestBody(requestBody);

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/subscriptions", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "SubscriptionCreateParams.java");
      assertThat(writeOp.fileContent)
          .contains("if (fieldName == null || !fieldName.startsWith(\"cf_\"))");
      assertThat(writeOp.fileContent).contains("throw new IllegalArgumentException");
    }

    @Test
    void shouldSupportBulkCustomFieldsMethod() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("email", new StringSchema());
      requestSchema.setAdditionalProperties(true);
      requestSchema.addExtension("x-cb-is-custom-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("update");
      postOp.addExtension(Extension.SDK_METHOD_NAME, "update");
      postOp.addExtension(Extension.RESOURCE_ID, "customer");
      postOp.setRequestBody(requestBody);

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/customers/{customer-id}", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerUpdateParams.java");
      assertThat(writeOp.fileContent)
          .contains("public CustomerUpdateBuilder customFields(Map<String, String> customFields)");
      assertThat(writeOp.fileContent)
          .contains("for (Map.Entry<String, String> entry : customFields.entrySet())");
    }

    @Test
    void shouldHandleCustomFieldsExtensionSetToFalse() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("name", new StringSchema());
      requestSchema.setAdditionalProperties(true);
      requestSchema.addExtension("x-cb-is-custom-fields-supported", false);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create");
      postOp.addExtension(Extension.SDK_METHOD_NAME, "create");
      postOp.addExtension(Extension.RESOURCE_ID, "test");
      postOp.setRequestBody(requestBody);

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/test", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "TestCreateParams.java");
      assertThat(writeOp.fileContent).doesNotContain("customField");
    }

    @Test
    @DisplayName(
        "Should add custom field methods to sub-params when x-cb-is-custom-fields-supported is true"
            + " at sub-params level")
    void shouldAddCustomFieldMethodsToSubParamsWithCustomFieldsExtension() throws IOException {
      // Create a nested object schema (billing_address) with custom fields support
      ObjectSchema billingAddressSchema = new ObjectSchema();
      billingAddressSchema.addProperty("line1", new StringSchema());
      billingAddressSchema.addProperty("city", new StringSchema());
      billingAddressSchema.addExtension("x-cb-is-custom-fields-supported", true);

      // Parent request schema without custom fields support
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("first_name", new StringSchema());
      requestSchema.addProperty("billing_address", billingAddressSchema);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create_customer");
      postOp.addExtension(Extension.SDK_METHOD_NAME, "create");
      postOp.addExtension(Extension.RESOURCE_ID, "customer");
      postOp.setRequestBody(requestBody);

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/customers", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerCreateParams.java");
      // Parent should NOT have custom fields
      assertThat(writeOp.fileContent).doesNotContain("public CustomerCreateBuilder customField(");
      // Sub-params (BillingAddress) should have custom fields
      assertThat(writeOp.fileContent).contains("public BillingAddressBuilder customField(");
      assertThat(writeOp.fileContent).contains("public BillingAddressBuilder customFields(");
    }

    @Test
    @DisplayName(
        "Should not add custom field methods to sub-params without x-cb-is-custom-fields-supported")
    void shouldNotAddCustomFieldMethodsToSubParamsWithoutExtension() throws IOException {
      // Create a nested object schema (billing_address) without custom fields support
      ObjectSchema billingAddressSchema = new ObjectSchema();
      billingAddressSchema.addProperty("line1", new StringSchema());
      billingAddressSchema.addProperty("city", new StringSchema());

      // Parent request schema with custom fields support
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("first_name", new StringSchema());
      requestSchema.addProperty("billing_address", billingAddressSchema);
      requestSchema.addExtension("x-cb-is-custom-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create_customer");
      postOp.addExtension(Extension.SDK_METHOD_NAME, "create");
      postOp.addExtension(Extension.RESOURCE_ID, "customer");
      postOp.setRequestBody(requestBody);

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/customers", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerCreateParams.java");
      // Parent should have custom fields
      assertThat(writeOp.fileContent).contains("public CustomerCreateBuilder customField(");
      // Sub-params (BillingAddress) should NOT have custom fields
      assertThat(writeOp.fileContent).doesNotContain("public BillingAddressBuilder customField(");
    }
  }

  private FileOp.WriteString findWriteOp(List<FileOp> fileOps, String fileName) {
    return fileOps.stream()
        .filter(op -> op instanceof FileOp.WriteString)
        .map(op -> (FileOp.WriteString) op)
        .filter(op -> op.fileName.equals(fileName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("File not found: " + fileName));
  }
}
