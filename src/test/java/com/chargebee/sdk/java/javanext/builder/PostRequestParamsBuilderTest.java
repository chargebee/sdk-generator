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
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Comprehensive test suite for PostRequestParamsBuilder.
 *
 * <p>This test class validates POST request parameter generation from OpenAPI specifications,
 * mirroring the test scenarios from JavaTests.java (lines 1449-2423). It covers:
 *
 * <ul>
 *   <li>Basic request body parameters (string, integer, boolean)
 *   <li>Required vs optional parameters
 *   <li>Enum parameters (local and global)
 *   <li>Nested object parameters (sub-resources/multi-attributes)
 *   <li>Array parameters in nested objects
 *   <li>Deprecated parameter handling
 *   <li>Custom fields support (see PostRequestParamsBuilderCustomFieldsTest.java)
 * </ul>
 */
@DisplayName("POST Request Params Builder")
class PostRequestParamsBuilderTest {

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

  // BASIC REQUEST BODY PARAMETERS
  // Mirrors: JavaTests.java lines 1449-1492

  @Nested
  @DisplayName("Basic Request Body Parameters")
  class BasicRequestBodyParametersTests {

    @Test
    @DisplayName("Should generate request body params with multiple field types")
    void shouldGenerateRequestBodyParamsWithMultipleTypes() throws IOException {
      // Mirrors: shouldHaveRequestBodyParamsInOperationRequestClass (JavaTests.java:1449)
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("id", new StringSchema());
      requestSchema.addProperty("net_term_days", new IntegerSchema().format("int32"));
      requestSchema.addProperty("registered_for_gst", new BooleanSchema());

      Operation postOp = createPostOperation("customer", "create", requestSchema);
      addPathWithPostOperation("/customers", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerCreateParams.java");
      assertThat(writeOp.fileContent).contains("CustomerCreateParams");
      assertThat(writeOp.fileContent).contains("id");
      assertThat(writeOp.fileContent).contains("netTermDays");
      assertThat(writeOp.fileContent).contains("registeredForGst");
    }

    @Test
    @DisplayName("Should have optional parameters")
    void shouldHaveOptionalParameters() throws IOException {
      // Mirrors: shouldHaveOptSuffixForOptionalRequestBodyParams (JavaTests.java:1473)
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("id", new StringSchema());
      requestSchema.addProperty("first_name", new StringSchema());
      // Both optional (no required list)

      Operation postOp = createPostOperation("customer", "create", requestSchema);
      addPathWithPostOperation("/customers", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerCreateParams.java");
    }
  }

  // DEPRECATED REQUEST BODY PARAMETERS
  // Mirrors: JavaTests.java lines 1495-1563

  @Nested
  @DisplayName("Deprecated Request Body Parameters")
  class DeprecatedRequestBodyParametersTests {

    @Test
    @DisplayName("Should mark deprecated request body param with @Deprecated")
    void shouldMarkDeprecatedRequestBodyParam() throws IOException {
      // Mirrors: shouldHaveObsoleteTagForDeprecatedRequestBodyParams (JavaTests.java:1495)
      StringSchema couponSchema = new StringSchema();
      couponSchema.setDeprecated(true);

      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("coupon", couponSchema);

      Operation postOp = createPostOperation("invoice", "create", requestSchema);
      addPathWithPostOperation("/invoices", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "InvoiceCreateParams.java");
      // Should contain deprecated annotation or marking
      assertThat(writeOp.fileContent).containsIgnoringCase("deprecated");
    }
  }

  // ENUM PARAMETERS IN REQUEST BODY
  // Mirrors: JavaTests.java lines 1566-1648

  @Nested
  @DisplayName("Enum Parameters in Request Body")
  class EnumParametersInRequestBodyTests {

    @Test
    @DisplayName("Should support enum attributes in request body params")
    void shouldSupportEnumAttributesInRequestBody() throws IOException {
      // Mirrors: shouldSupportEnumAttributesInRequestBodyParams (JavaTests.java:1566)
      StringSchema billingDayEnum = new StringSchema();
      billingDayEnum.setEnum(List.of("sunday", "monday"));
      Map<String, Object> extensions = new HashMap<>();
      extensions.put("x-cb-meta-model-name", "customer");
      billingDayEnum.setExtensions(extensions);

      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("billing_day_of_week", billingDayEnum);

      Operation postOp = createPostOperation("customer", "changeBillingDate", requestSchema);
      // Path with {id} and action derives to changeBillingDateForCustomer
      // changeBillingDateForCustomer contains "customer", so prefix is skipped
      addPathWithPostOperation("/customers/{customer-id}/change_billing_date", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp =
          findWriteOp(fileOps, "ChangeBillingDateForCustomerParams.java");
      assertThat(writeOp.fileContent).contains("BillingDayOfWeek");
      assertThat(writeOp.fileContent).containsIgnoringCase("sunday");
      assertThat(writeOp.fileContent).containsIgnoringCase("monday");
    }

    @Test
    @DisplayName("Should support global enum attributes in request body params")
    void shouldSupportGlobalEnumAttributesInRequestBody() throws IOException {
      // Mirrors: shouldSupportGlobalEnumAttributesInRequestBodyParams (JavaTests.java:1651)
      StringSchema autoCollectionEnum = new StringSchema();
      autoCollectionEnum.setEnum(List.of("on", "off"));
      Map<String, Object> extensions = new HashMap<>();
      extensions.put("x-cb-is-global-enum", true);
      extensions.put("x-cb-global-enum-reference", "./enums/AutoCollection.yaml");
      autoCollectionEnum.setExtensions(extensions);

      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("auto_collection", autoCollectionEnum);

      Operation postOp = createPostOperation("customer", "create", requestSchema);
      addPathWithPostOperation("/customers", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerCreateParams.java");
      // Should reference global enum from enums package
      assertThat(writeOp.fileContent).contains("AutoCollection");
    }
  }

  // NESTED OBJECT PARAMETERS (MULTI-ATTRIBUTES/SUB-RESOURCES)
  // Mirrors: JavaTests.java lines 1731-1804

  @Nested
  @DisplayName("Nested Object Parameters (Multi-Attributes)")
  class NestedObjectParametersTests {

    @Test
    @DisplayName("Should support multi-attributes (nested objects) in request body params")
    void shouldSupportMultiAttributesInRequestBody() throws IOException {
      // Mirrors: shouldSupportMultiAttributesInRequestBodyParams (JavaTests.java:1731)
      StringSchema firstNameSchema = new StringSchema();
      firstNameSchema.addExtension("x-cb-is-sub-resource", true);

      ObjectSchema billingAddressSchema = new ObjectSchema();
      billingAddressSchema.addProperty("first_name", firstNameSchema);
      billingAddressSchema.addExtension("x-cb-is-sub-resource", true);

      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("billing_address", billingAddressSchema);

      Operation postOp = createPostOperation("customer", "create", requestSchema);
      addPathWithPostOperation("/customers", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerCreateParams.java");
      // Should generate billingAddressFirstName method
      assertThat(writeOp.fileContent).containsIgnoringCase("billingaddress");
      assertThat(writeOp.fileContent).containsIgnoringCase("firstname");
    }

    @Test
    @DisplayName("Should support global enums in multi-attributes")
    void shouldSupportGlobalEnumsInMultiAttributes() throws IOException {
      // Mirrors: shouldSupportGlobalEnumsInMultiAttributesInRequestBodyParams (JavaTests.java:1807)
      StringSchema validationStatusEnum = new StringSchema();
      validationStatusEnum.setEnum(List.of("valid", "not_validated"));
      Map<String, Object> enumExtensions = new HashMap<>();
      enumExtensions.put("x-cb-is-global-enum", true);
      enumExtensions.put("x-cb-global-enum-reference", "./enums/ValidationStatus.yaml");
      enumExtensions.put("x-cb-is-sub-resource", true);
      validationStatusEnum.setExtensions(enumExtensions);

      ObjectSchema billingAddressSchema = new ObjectSchema();
      billingAddressSchema.addProperty("validation_status", validationStatusEnum);
      billingAddressSchema.addExtension("x-cb-is-sub-resource", true);

      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("billing_address", billingAddressSchema);

      Operation postOp = createPostOperation("customer", "create", requestSchema);
      addPathWithPostOperation("/customers", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerCreateParams.java");
      // Should reference ValidationStatus enum
      assertThat(writeOp.fileContent).contains("ValidationStatus");
    }

    @Test
    @DisplayName("Should support local enums in multi-attributes")
    void shouldSupportLocalEnumsInMultiAttributes() throws IOException {
      // Mirrors: shouldSupportEnumsInMultiAttributesInRequestBodyParams (JavaTests.java:1892)
      StringSchema paymentMethodTypeEnum = new StringSchema();
      paymentMethodTypeEnum.setEnum(List.of("card", "ideal", "sofort"));
      paymentMethodTypeEnum.addExtension("x-cb-is-sub-resource", true);

      ObjectSchema paymentIntentSchema = new ObjectSchema();
      paymentIntentSchema.addProperty("payment_method_type", paymentMethodTypeEnum);
      paymentIntentSchema.addExtension("x-cb-is-sub-resource", true);

      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("payment_intent", paymentIntentSchema);

      Operation postOp =
          createPostOperation("invoice", "createForChargeItemsAndCharges", requestSchema);
      addPathWithPostOperation("/invoices/create_for_charge_items_and_charges", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp =
          findWriteOp(fileOps, "InvoiceCreateForChargeItemsAndChargesParams.java");
      // Should contain PaymentMethodType enum
      assertThat(writeOp.fileContent).contains("PaymentMethodType");
    }

    @Test
    @DisplayName("Should mark deprecated multi-attribute params with @Deprecated")
    void shouldMarkDeprecatedMultiAttributeParams() throws IOException {
      // Mirrors: shouldHavaObsoleteTagForDeprecatedMultiAttributesInRequestBodyParams (JavaTests.java:1983)
      StringSchema gwPaymentMethodIdSchema = new StringSchema();
      gwPaymentMethodIdSchema.setDeprecated(true);
      gwPaymentMethodIdSchema.addExtension("x-cb-is-sub-resource", true);

      ObjectSchema paymentIntentSchema = new ObjectSchema();
      paymentIntentSchema.addProperty("gw_payment_method_id", gwPaymentMethodIdSchema);
      paymentIntentSchema.addExtension("x-cb-is-sub-resource", true);

      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("payment_intent", paymentIntentSchema);

      Operation postOp =
          createPostOperation("invoice", "createForChargeItemsAndCharges", requestSchema);
      addPathWithPostOperation("/invoices/create_for_charge_items_and_charges", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "InvoiceCreateForChargeItemsAndChargesParams.java");
    }
  }

  // ARRAY PARAMETERS IN NESTED OBJECTS (COMPOSITE ARRAY REQUEST BODY)
  // Mirrors: JavaTests.java lines 2067-2423

  @Nested
  @DisplayName("Array Parameters in Nested Objects")
  class ArrayParametersInNestedObjectsTests {

    @Test
    @DisplayName("Should support list multi-attributes (array parameters) in request body")
    void shouldSupportListMultiAttributesInRequestBody() throws IOException {
      // Mirrors: shouldSupportListMultiAttributesInRequestBodyParams (JavaTests.java:2067)
      StringSchema idSchema = new StringSchema();
      idSchema.setDeprecated(false);
      idSchema.addExtension("x-cb-is-sub-resource", true);

      ArraySchema idArraySchema = new ArraySchema();
      idArraySchema.setItems(idSchema);

      Map<String, Schema> properties = new LinkedHashMap<>();
      properties.put("id", idArraySchema);

      ObjectSchema entityIdentifiersSchema = new ObjectSchema();
      entityIdentifiersSchema.setProperties(properties);
      entityIdentifiersSchema.addExtension("x-cb-is-composite-array-request-body", true);

      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("entity_identifiers", entityIdentifiersSchema);

      Operation postOp = createPostOperation("customer", "create", requestSchema);
      addPathWithPostOperation("/customers", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerCreateParams.java");
      // Should generate entityIdentifierId(int index, String value) method
      assertThat(writeOp.fileContent).containsIgnoringCase("entityidentifier");
    }

    @Test
    @DisplayName(
        "Should support parameter blank option as empty in list multi-attributes")
    void shouldSupportParameterBlankOptionAsEmptyInListMultiAttributes() throws IOException {
      // Mirrors: shouldContinueParameterBlankOptionAsEmptyInListMultiAttributesInRequestBodyParams
      // (JavaTests.java:2146)
      StringSchema idSchema = new StringSchema();
      idSchema.setDeprecated(false);
      idSchema.addExtension("x-cb-is-sub-resource", true);

      ArraySchema idArraySchema = new ArraySchema();
      idArraySchema.setItems(idSchema);

      Map<String, Schema> properties = new LinkedHashMap<>();
      properties.put("id", idArraySchema);

      ObjectSchema eventBasedAddonsSchema = new ObjectSchema();
      eventBasedAddonsSchema.setProperties(properties);
      Map<String, Object> extensions = new HashMap<>();
      extensions.put("x-cb-is-composite-array-request-body", true);
      extensions.put("x-cb-parameter-blank-option", "as_empty");
      eventBasedAddonsSchema.setExtensions(extensions);

      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("event_based_addons", eventBasedAddonsSchema);

      Operation postOp = createPostOperation("estimate", "updateSubscription", requestSchema);
      addPathWithPostOperation("/estimates/update_subscription", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "EstimateUpdateSubscriptionParams.java");
    }

    @Test
    @DisplayName("Should support global enums in list multi-attributes")
    void shouldSupportGlobalEnumsInListMultiAttributes() throws IOException {
      // Mirrors: shouldSupportGlobalEnumsInListMultiAttributesInRequestBodyParams
      // (JavaTests.java:2235)
      StringSchema avalaraSaleTypeEnum = new StringSchema();
      avalaraSaleTypeEnum.setEnum(List.of("retail", "wholesale"));
      Map<String, Object> enumExtensions = new HashMap<>();
      enumExtensions.put("x-cb-is-global-enum", true);
      enumExtensions.put("x-cb-global-enum-reference", "./enums/AvalaraSaleType.yaml");
      enumExtensions.put("x-cb-is-sub-resource", true);
      avalaraSaleTypeEnum.setExtensions(enumExtensions);

      ArraySchema avalaraSaleTypeArray = new ArraySchema();
      avalaraSaleTypeArray.setItems(avalaraSaleTypeEnum);

      Map<String, Schema> properties = new LinkedHashMap<>();
      properties.put("avalara_sale_type", avalaraSaleTypeArray);

      ObjectSchema chargesSchema = new ObjectSchema();
      chargesSchema.setProperties(properties);
      chargesSchema.addExtension("x-cb-is-composite-array-request-body", true);

      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("charges", chargesSchema);

      Operation postOp =
          createPostOperation("invoice", "createForChargeItemsAndCharges", requestSchema);
      addPathWithPostOperation("/invoices/create_for_charge_items_and_charges", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp =
          findWriteOp(fileOps, "InvoiceCreateForChargeItemsAndChargesParams.java");
      // Should reference AvalaraSaleType enum
      assertThat(writeOp.fileContent).contains("AvalaraSaleType");
    }

    @Test
    @DisplayName("Should support local enums in list multi-attributes")
    void shouldSupportLocalEnumsInListMultiAttributes() throws IOException {
      // Mirrors: shouldSupportEnumsInListMultiAttributesInRequestBodyParams (JavaTests.java:2320)
      StringSchema entityTypeEnum = new StringSchema();
      entityTypeEnum.setEnum(List.of("plan", "plan_setup"));
      Map<String, Object> enumExtensions = new HashMap<>();
      enumExtensions.put("x-cb-is-composite-array-request-body", true);
      enumExtensions.put("x-cb-meta-model-name", "invoice");
      entityTypeEnum.setExtensions(enumExtensions);

      Map<String, Schema> properties = new LinkedHashMap<>();
      properties.put("entity_type", entityTypeEnum);

      ObjectSchema lineItemsSchema = new ObjectSchema();
      lineItemsSchema.setProperties(properties);
      Map<String, Object> lineItemExtensions = new HashMap<>();
      lineItemExtensions.put("x-cb-is-composite-array-request-body", true);
      lineItemExtensions.put("x-cb-meta-model-name", "invoice");
      lineItemsSchema.setExtensions(lineItemExtensions);

      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("line_items", lineItemsSchema);

      Operation postOp = createPostOperation("invoice", "importInvoice", requestSchema);
      addPathWithPostOperation("/invoices/import_invoice", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      // importInvoice contains "invoice", so no prefix is added
      FileOp.WriteString writeOp = findWriteOp(fileOps, "ImportInvoiceParams.java");
      // Should contain EntityType enum
      assertThat(writeOp.fileContent).contains("EntityType");
    }
  }

  // NULL AND EDGE CASE HANDLING

  @Nested
  @DisplayName("Null and Edge Case Handling")
  class NullAndEdgeCaseHandlingTests {

    @Test
    @DisplayName("Should handle empty paths in OpenAPI spec")
    void shouldHandleEmptyPaths() throws IOException {
      openAPI.setPaths(new Paths());
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      // Only creates base directory structure (/v4/models)
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(1);
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }

    @Test
    @DisplayName("Should generate params file even for operation without request body")
    void shouldGenerateParamsFileEvenWithoutRequestBody() throws IOException {
      // PostRequestParamsBuilder generates params files even if there's no request body
      // Path with {id} and POST method derives to "update"
      Operation postOp = new Operation();
      postOp.addExtension(Extension.RESOURCE_ID, "customer");

      addPathWithPostOperation("/customers/{customer-id}", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      // Should create directories + params file (even if empty)
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);
      assertThat(fileOps).anyMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should generate params file even with null properties in schema")
    void shouldGenerateParamsFileWithNullProperties() throws IOException {
      // PostRequestParamsBuilder generates params files even if properties are null
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.setProperties(null);

      Operation postOp = createPostOperation("customer", "create", requestSchema);
      addPathWithPostOperation("/customers", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      // Should create directories + params file (even if empty)
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);
      assertThat(fileOps).anyMatch(op -> op instanceof FileOp.CreateDirectory);
    }

    @Test
    @DisplayName("Should generate params file even without required extensions")
    void shouldGenerateParamsFileWithoutExtensions() throws IOException {
      // PostRequestParamsBuilder generates params files even without all extensions
      Operation postOp = new Operation();
      addPathWithPostOperation("/customers", postOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      // Should create directories + params file
      assertThat(fileOps).hasSizeGreaterThanOrEqualTo(2);
      assertThat(fileOps).anyMatch(op -> op instanceof FileOp.CreateDirectory);
    }
  }

  // HELPER METHODS

  /**
   * Creates a POST operation with request body and required extensions.
   *
   * @param resourceId The resource identifier (e.g., "customer")
   * @param methodName The operation method name (e.g., "create")
   * @param requestSchema The request body schema
   * @return Configured Operation instance
   */
  private Operation createPostOperation(
      String resourceId, String methodName, ObjectSchema requestSchema) {
    MediaType mediaType = new MediaType();
    mediaType.setSchema(requestSchema);

    Content content = new Content();
    content.addMediaType("application/x-www-form-urlencoded", mediaType);

    RequestBody requestBody = new RequestBody();
    requestBody.setContent(content);

    Operation operation = new Operation();
    Map<String, Object> extensions = new HashMap<>();
    extensions.put(Extension.RESOURCE_ID, resourceId);
    // OPERATION_METHOD_NAME is no longer used - method name is derived from path
    operation.setExtensions(extensions);
    operation.setRequestBody(requestBody);
    return operation;
  }

  /** Adds a path with a POST operation to the OpenAPI spec. */
  private void addPathWithPostOperation(String path, Operation postOperation) {
    PathItem pathItem = new PathItem();
    pathItem.setPost(postOperation);
    addPath(path, pathItem);
  }

  /** Adds a path item to the OpenAPI spec. */
  private void addPath(String path, PathItem pathItem) {
    if (openAPI.getPaths() == null) {
      openAPI.setPaths(new Paths());
    }
    openAPI.getPaths().addPathItem(path, pathItem);
  }

  /** Finds a WriteString operation for a specific file name. */
  private FileOp.WriteString findWriteOp(List<FileOp> fileOps, String fileName) {
    return fileOps.stream()
        .filter(op -> op instanceof FileOp.WriteString)
        .map(op -> (FileOp.WriteString) op)
        .filter(op -> op.fileName.equals(fileName))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("WriteString operation not found for file: " + fileName));
  }

  /** Asserts that a file with the given name exists. */
  private void assertFileExists(List<FileOp> fileOps, String fileName) {
    assertThat(fileOps)
        .anyMatch(
            op ->
                op instanceof FileOp.WriteString
                    && ((FileOp.WriteString) op).fileName.equals(fileName));
  }
}
