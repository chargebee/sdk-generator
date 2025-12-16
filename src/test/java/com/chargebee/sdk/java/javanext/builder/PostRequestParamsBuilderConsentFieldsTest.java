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
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.*;

/**
 * Test suite for consent fields support in POST request parameters.
 * Consent fields are prefixed with "cs_" and can hold boolean or option values.
 */
class PostRequestParamsBuilderConsentFieldsTest {

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
  @DisplayName("Consent Fields Support in POST Params")
  class ConsentFieldsSupportTests {

    @Test
    void shouldAddConsentFieldMethodsToAllParams() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("first_name", new StringSchema());
      requestSchema.addProperty("email", new StringSchema());
      requestSchema.addExtension("x-cb-is-consent-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create_customer");
      postOp.addExtension(Extension.OPERATION_METHOD_NAME, "create");
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
      // Consent fields should always be available
      assertThat(writeOp.fileContent).contains("public CustomerCreateBuilder consentField(");
      assertThat(writeOp.fileContent).contains("public CustomerCreateBuilder consentFields(");
      assertThat(writeOp.fileContent).contains("Consent field name must start with 'cs_'");
    }

    @Test
    void shouldIncludeValidationForConsentFieldNames() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("id", new StringSchema());
      requestSchema.addExtension("x-cb-is-consent-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create");
      postOp.addExtension(Extension.OPERATION_METHOD_NAME, "create");
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
          .contains("if (fieldName == null || !fieldName.startsWith(\"cs_\"))");
      assertThat(writeOp.fileContent).contains("throw new IllegalArgumentException");
    }

    @Test
    void shouldSupportBulkConsentFieldsMethod() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("email", new StringSchema());
      requestSchema.addExtension("x-cb-is-consent-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("update");
      postOp.addExtension(Extension.OPERATION_METHOD_NAME, "update");
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
          .contains(
              "public CustomerUpdateBuilder consentFields(Map<String, Object> consentFields)");
      assertThat(writeOp.fileContent)
          .contains("for (Map.Entry<String, Object> entry : consentFields.entrySet())");
    }

    @Test
    void shouldSupportBooleanConsentFieldMethod() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("name", new StringSchema());
      requestSchema.addExtension("x-cb-is-consent-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create");
      postOp.addExtension(Extension.OPERATION_METHOD_NAME, "create");
      postOp.addExtension(Extension.RESOURCE_ID, "contact");
      postOp.setRequestBody(requestBody);

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/contacts", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "ContactCreateParams.java");
      // Should have overloaded method for Boolean type
      assertThat(writeOp.fileContent)
          .contains("public ContactCreateBuilder consentField(String fieldName, Boolean value)");
    }

    @Test
    void shouldIncludeConsentFieldsInFormData() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("id", new StringSchema());
      requestSchema.addExtension("x-cb-is-consent-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create");
      postOp.addExtension(Extension.OPERATION_METHOD_NAME, "create");
      postOp.addExtension(Extension.RESOURCE_ID, "lead");
      postOp.setRequestBody(requestBody);

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/leads", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "LeadCreateParams.java");
      // Consent fields should be included in toFormData()
      assertThat(writeOp.fileContent).contains("formData.putAll(consentFields)");
    }

    @Test
    void shouldHaveConsentFieldsMapInBuilder() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("name", new StringSchema());
      requestSchema.addExtension("x-cb-is-consent-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create");
      postOp.addExtension(Extension.OPERATION_METHOD_NAME, "create");
      postOp.addExtension(Extension.RESOURCE_ID, "prospect");
      postOp.setRequestBody(requestBody);

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/prospects", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "ProspectCreateParams.java");
      // Builder should have consent fields map
      assertThat(writeOp.fileContent)
          .contains("private Map<String, Object> consentFields = new LinkedHashMap<>()");
    }

    @Test
    void shouldHaveConsentFieldsGetterInParams() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("email", new StringSchema());
      requestSchema.addExtension("x-cb-is-consent-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create");
      postOp.addExtension(Extension.OPERATION_METHOD_NAME, "create");
      postOp.addExtension(Extension.RESOURCE_ID, "user");
      postOp.setRequestBody(requestBody);

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/users", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "UserCreateParams.java");
      // Params should have getter for consent fields
      assertThat(writeOp.fileContent).contains("public Map<String, Object> consentFields()");
    }

    @Test
    void shouldNotHaveConsentFieldsWithoutRequestSchema() throws IOException {
      Operation postOp = new Operation();
      postOp.setOperationId("trigger");
      postOp.addExtension(Extension.OPERATION_METHOD_NAME, "trigger");
      postOp.addExtension(Extension.RESOURCE_ID, "workflow");
      // No request body - consent fields support cannot be determined

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/workflows/trigger", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "WorkflowTriggerParams.java");
      // Consent fields should NOT be available without consent fields extension
      assertThat(writeOp.fileContent).doesNotContain("consentField(");
      assertThat(writeOp.fileContent).doesNotContain("consentFields(");
    }
  }

  @Nested
  @DisplayName("Consent Fields Coexistence with Custom Fields")
  class ConsentAndCustomFieldsCoexistenceTests {

    @Test
    void shouldSupportBothCustomAndConsentFieldsWhenBothEnabled() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("name", new StringSchema());
      requestSchema.setAdditionalProperties(true);
      requestSchema.addExtension("x-cb-is-custom-fields-supported", true);
      requestSchema.addExtension("x-cb-is-consent-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create");
      postOp.addExtension(Extension.OPERATION_METHOD_NAME, "create");
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
      // Should have both custom and consent field methods
      assertThat(writeOp.fileContent).contains("customField(");
      assertThat(writeOp.fileContent).contains("customFields(");
      assertThat(writeOp.fileContent).contains("consentField(");
      assertThat(writeOp.fileContent).contains("consentFields(");
      // Should include both in form data
      assertThat(writeOp.fileContent).contains("formData.putAll(customFields)");
      assertThat(writeOp.fileContent).contains("formData.putAll(consentFields)");
    }

    @Test
    void shouldSupportConsentFieldsEvenWhenCustomFieldsNotEnabled() throws IOException {
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("name", new StringSchema());
      // Custom fields NOT enabled, but consent fields enabled
      requestSchema.addExtension("x-cb-is-consent-fields-supported", true);

      MediaType mediaType = new MediaType();
      mediaType.setSchema(requestSchema);

      Content content = new Content();
      content.addMediaType("application/x-www-form-urlencoded", mediaType);

      RequestBody requestBody = new RequestBody();
      requestBody.setContent(content);

      Operation postOp = new Operation();
      postOp.setOperationId("create");
      postOp.addExtension(Extension.OPERATION_METHOD_NAME, "create");
      postOp.addExtension(Extension.RESOURCE_ID, "invoice");
      postOp.setRequestBody(requestBody);

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOp);

      Paths paths = new Paths();
      paths.addPathItem("/invoices", pathItem);
      openAPI.setPaths(paths);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "InvoiceCreateParams.java");
      // Should NOT have custom field methods
      assertThat(writeOp.fileContent).doesNotContain("customField(");
      // But SHOULD have consent field methods
      assertThat(writeOp.fileContent).contains("consentField(");
      assertThat(writeOp.fileContent).contains("consentFields(");
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
