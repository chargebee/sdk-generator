package com.chargebee.sdk.php.v4;

import static com.chargebee.sdk.test_data.EnumBuilder.buildEnum;
import static com.chargebee.sdk.test_data.OperationBuilder.*;
import static com.chargebee.sdk.test_data.ResourceBuilder.buildResource;
import static com.chargebee.sdk.test_data.ResourceResponseParam.resourceResponseParam;
import static com.chargebee.sdk.test_data.SpecBuilder.buildSpec;
import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.LanguageTests;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Php_v4Tests extends LanguageTests {

  public static PHP_V4 phpSdkGen;

  private final String basePath = "/php/src";
  private final String basePathForResources = "/php/src/Resources";
  private final String sampleDirectoryPath = "src/test/java/com/chargebee/sdk/php/v4/samplesv4";

  void assertPhpModelResourceFileContent(
      FileOp.WriteString fileOp, String body, boolean hasSnippet) {
    assertThat(fileOp.fileContent.replaceAll("\\s+", "")).contains(body.replaceAll("\\s+", ""));
  }

  void assertPhpModelSubResourceFileContent(FileOp.WriteString fileOp, String body) {
    assertThat(fileOp.fileContent.replaceAll("\\s+", "")).isEqualTo(body.replaceAll("\\s+", ""));
  }

  @BeforeAll
  static void beforeAll() {
    phpSdkGen = new PHP_V4();
  }

  @Test
  void shouldCreateResourcesDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(8);
    assertCreateDirectoryFileOp(fileOps.get(0), basePath, "/Resources");
  }

  @Test
  void shouldCreateEnumsDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(8);
    assertCreateDirectoryFileOp(fileOps.get(1), basePath, "/Enums");
  }

  @Test
  void shouldCreateContentDirectoryInResources() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(8);
    assertCreateDirectoryFileOp(fileOps.get(5), basePathForResources, "Content");
  }

  @Test
  void shouldCreateAContentFileInContentDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(8);
    assertWriteStringFileOp(fileOps.get(6), basePathForResources + "/Content", "Content.php");
  }

  @Test
  void eachResourceShouldHaveSeparateDirectory() throws IOException {
    var spec = buildSpec().withResources("customer", "address").done();
    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);
    assertThat(fileOps).hasSize(12);
    assertCreateDirectoryFileOp(fileOps.get(7), basePathForResources, "Customer");
    assertCreateDirectoryFileOp(fileOps.get(5), basePathForResources, "Address");
  }

  @Test
  void eachResourceShouldHaveSeparateDeclaration() throws IOException {
    var spec = buildSpec().withResources("customer", "address").done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(12);
    assertWriteStringFileOp(fileOps.get(6), basePathForResources + "/Address", "Address.php");
    assertWriteStringFileOp(fileOps.get(8), basePathForResources + "/Customer", "Customer.php");
  }

  @Test
  void eachSubResourceOfResourceShouldHaveSeparateDeclaration() throws IOException {
    var fixedIntervalSchedule = buildResource("fixed_interval_schedule").done();
    var specificDatesSchedule = buildResource("specific_dates_schedule").done();
    var advanceInvoiceSchedule =
        buildResource("advance_invoice_schedule")
            .withSubResourceAttribute("fixed_interval_schedule", fixedIntervalSchedule)
            .withSubResourceAttribute("specific_dates_schedule", specificDatesSchedule)
            .done();

    var spec = buildSpec().withResources(advanceInvoiceSchedule).done();
    String basePathForThisResource = basePathForResources + "/AdvanceInvoiceSchedule";

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);
    assertThat(fileOps).hasSize(12);
    assertWriteStringFileOp(fileOps.get(6), basePathForThisResource, "AdvanceInvoiceSchedule.php");
    assertWriteStringFileOp(fileOps.get(7), basePathForThisResource, "FixedIntervalSchedule.php");
    assertWriteStringFileOp(fileOps.get(8), basePathForThisResource, "SpecificDatesSchedule.php");
  }

  @Test
  void eachResourceShouldHaveSpecifiedResourceClass() throws IOException {
    var advanceInvoiceSchedule = buildResource("advance_invoice_schedule").done();

    var spec = buildSpec().withResources(advanceInvoiceSchedule).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);
    String expectedContent = FileOp.fetchFileContent(sampleDirectoryPath + "/advInvSched1.txt");

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContent, false);
  }

  @Test
  void eachSubResourceShouldHaveSpecifiedSubResourceClass() throws IOException {
    var fixedIntervalSchedule = buildResource("fixed_interval_schedule").done();
    var advanceInvoiceSchedule =
        buildResource("advance_invoice_schedule")
            .withSubResourceAttribute("fixed_interval_schedule", fixedIntervalSchedule)
            .done();

    var spec = buildSpec().withResources(advanceInvoiceSchedule).done();
    String expectedContent = FileOp.fetchFileContent(sampleDirectoryPath + "/fixInvSched1.txt");
    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertPhpModelSubResourceFileContent(writeStringFileOp, expectedContent);
  }

  @Test
  void shouldIgnoreHiddenFromSDKResources() throws IOException {
    var subscription = buildResource("subscription").done();
    var subscription_preview =
        buildResource("subscription_preview").asHiddenFromSDKGeneration().done();

    var spec = buildSpec().withResources(subscription, subscription_preview).done();
    String expectedContent = FileOp.fetchFileContent(sampleDirectoryPath + "/subscription1.txt");

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(10);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContent, false);
  }

  @Test
  void shouldContainDependentResources() throws IOException {
    var contract_term = buildResource("contract_term").done();
    var credit_note_estimate = buildResource("credit_note_estimate").asDependentResource().done();

    var spec = buildSpec().withResources(contract_term, credit_note_estimate).done();
    String expectedContent = FileOp.fetchFileContent(sampleDirectoryPath + "/credNoteEst1.txt");
    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(12);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(8);
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContent, false);
  }

  @Test
  void shouldCreateResourceClassWithSpecifiedAttributes() throws IOException {
    var fixedIntervalSchedule = buildResource("fixed_interval_schedule").done();
    var specificDatesSchedule = buildResource("specific_dates_schedule").done();
    var advanceInvoiceSchedule =
        buildResource("advance_invoice_schedule")
            .withAttribute("id")
            .withAttribute("schedule_type")
            .withSubResourceAttribute("fixed_interval_schedule", fixedIntervalSchedule)
            .withSubResourceAttribute("specific_dates_schedule", specificDatesSchedule)
            .done();

    var spec = buildSpec().withResources(advanceInvoiceSchedule).done();
    String expectedContent = FileOp.fetchFileContent(sampleDirectoryPath + "/advInvSched2.txt");
    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContent, false);
  }

  @Test
  void shouldCreateSubResourceClassWithSpecifiedSubResourceAttributes() throws IOException {
    var fixedIntervalSchedule =
        buildResource("fixed_interval_schedule")
            .withAttribute("end_schedule_on", true)
            .withAttribute("number_of_occurrences")
            .withAttribute("days_before_renewal")
            .withAttribute("end_date")
            .withAttribute("created_at", true)
            .withAttribute("terms_to_change")
            .done();
    var advanceInvoiceSchedule =
        buildResource("advance_invoice_schedule")
            .withSubResourceAttribute("fixed_interval_schedule", fixedIntervalSchedule)
            .done();

    var spec = buildSpec().withResources(advanceInvoiceSchedule).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);
    String expectedContent = FileOp.fetchFileContent(sampleDirectoryPath + "/fixInvSched2.txt");
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertPhpModelSubResourceFileContent(writeStringFileOp, expectedContent);
  }

  @Test
  void eachResourceAttributeShouldHaveType() throws IOException {
    var type = buildEnum("type", List.of("quantity", "tiered")).setEnumApiName("Type").done();
    var customer =
        buildResource("customer")
            .withAttribute("id", true)
            .withAttribute("net_term_days", new IntegerSchema(), true)
            .withAttribute(
                "vat_number_validated_time",
                new IntegerSchema().format("unix-time").pattern("^[0-9]{10}$"),
                false)
            .withAttribute(
                "resource_version", new IntegerSchema().type("integer").format("int64"), false)
            .withAttribute("auto_close_invoices", new BooleanSchema(), false)
            .withEnumAttribute(type)
            .withAttribute(
                "meta_data", new ObjectSchema().type("object").additionalProperties(true), false)
            .withAttribute(
                "exemption_details", new ArraySchema().items(new ObjectSchema().type(null)), false)
            .done();

    var invoice =
        buildResource("invoice")
            .withAttribute("prorated_taxable_amount", new NumberSchema().format("decimal"))
            .done();

    var coupon =
        buildResource("coupon")
            .withAttribute("discount_percentage", new NumberSchema().format("double"), false)
            .withAttribute("plan_ids", new ArraySchema().items(new StringSchema()), false)
            .done();

    String expectedCouponContent = FileOp.fetchFileContent(sampleDirectoryPath + "/coupon1.txt");
    String expectedInvoiceContent = FileOp.fetchFileContent(sampleDirectoryPath + "/invoice1.txt");
    String expectedCustomerContent =
        FileOp.fetchFileContent(sampleDirectoryPath + "/customer1.txt");

    var spec = buildSpec().withResources(customer, invoice, coupon).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertPhpModelResourceFileContent(writeStringFileOp, expectedCouponContent, false);

    writeStringFileOp = (FileOp.WriteString) fileOps.get(8);
    assertPhpModelResourceFileContent(writeStringFileOp, expectedCustomerContent, false);

    writeStringFileOp = (FileOp.WriteString) fileOps.get(12);
    assertPhpModelResourceFileContent(writeStringFileOp, expectedInvoiceContent, false);
  }

  @Test
  void shouldSupportGlobalResourceAndArrayTypeSubResourceAttribute() throws IOException {
    var subscriptionEstimate =
        buildResource("subscription_estimate")
            .asDependentResource()
            .withAttribute("id", true)
            .done();
    var estimate =
        buildResource("estimate")
            .withSubResourceAttributeReference("subscription_estimate", subscriptionEstimate)
            .withSubResourceArrayAttributeReference("subscription_estimates", subscriptionEstimate)
            .done();
    var spec = buildSpec().withResources(subscriptionEstimate, estimate).done();
    String expectedEstimateContent =
        FileOp.fetchFileContent(sampleDirectoryPath + "/estimate1.txt");

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertPhpModelResourceFileContent(writeStringFileOp, expectedEstimateContent, false);
  }

  @Test
  void contentFileShouldContainsAllTheResources() throws IOException {
    var invoice =
        buildResource("invoice")
            .withAttribute("prorated_taxable_amount", new NumberSchema().format("decimal"))
            .done();

    var coupon =
        buildResource("coupon")
            .withAttribute("discount_percentage", new NumberSchema().format("double"), false)
            .withAttribute("plan_ids", new ArraySchema().items(new StringSchema()), false)
            .done();

    String expectedContentFile = FileOp.fetchFileContent(sampleDirectoryPath + "/content1.txt");

    var spec = buildSpec().withResources(invoice, coupon).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(10);
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContentFile, false);
  }

  @Test
  void contentAttributeShouldGetGeneratedForEventsAndHostedPages() throws IOException {
    var event =
        buildResource("event")
            .withAttribute(
                "content", new ObjectSchema().type("object").additionalProperties(true), false)
            .withAttribute("discount_percentage", new NumberSchema().format("double"), false)
            .withAttribute("plan_ids", new ArraySchema().items(new StringSchema()), false)
            .done();

    String expectedContentFile = FileOp.fetchFileContent(sampleDirectoryPath + "/event1.txt");

    var spec = buildSpec().withResources(event).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContentFile, false);
  }

  @Test
  void shouldCreateActionsDirectory() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema(), true)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();
    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);
    var createDirectoryFileOp = (FileOp.CreateDirectory) fileOps.get(2);
    assertCreateDirectoryFileOp(createDirectoryFileOp, basePath, "/Actions");
  }

  @Test
  void shouldGenerateTypesForSimpleParameters() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema(), true)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);
    String expectedContentFile = FileOp.fetchFileContent(sampleDirectoryPath + "/simpleAction.txt");
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(9);
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContentFile, false);
  }

  @Test
  void shouldSupportFilterAttributes() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("limit", new IntegerSchema())
            .withQueryParam("offset")
            .withQueryParam(
                "first_name",
                new ObjectSchema()
                    .properties(
                        Map.of("is_present", new StringSchema()._enum(List.of("true", "false"))))
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "StringFilter")))
            .withQueryParam(
                "created_at",
                new ObjectSchema()
                    .properties(
                        Map.of("is_present", new StringSchema()._enum(List.of("true", "false"))))
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "TimestampFilter")))
            .withQueryParam(
                "has_card",
                new ObjectSchema()
                    .properties(
                        Map.of("is_present", new StringSchema()._enum(List.of("true", "false"))))
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "BooleanFilter")))
            .withQueryParam(
                "has_card",
                new ObjectSchema()
                    .properties(
                        Map.of("is_present", new StringSchema()._enum(List.of("true", "false"))))
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "NumberFilter")))
            .withQueryParam(
                "has_card",
                new ObjectSchema()
                    .properties(
                        Map.of("is_present", new StringSchema()._enum(List.of("true", "false"))))
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "EnumFilter")))
            .withQueryParam(
                "another_one",
                new ObjectSchema()
                    .properties(
                        Map.of("is_present", new StringSchema()._enum(List.of("true", "false"))))
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "DateFilter")))
            .withQueryParam(
                "has_card",
                new ObjectSchema()
                    .properties(
                        Map.of("is_present", new StringSchema()._enum(List.of("true", "false"))))
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "CoverageCoverage")))
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", operation).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(9);
    String expectedContentFile =
        FileOp.fetchFileContent(sampleDirectoryPath + "/actionWithFilter.txt");
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContentFile, false);
  }

  @Test
  void shouldSupportActionWithObjectInRequestBodyParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperation("list")
            .forResource("customer")
            .forResource("customer")
            .withQueryParam("limit", new IntegerSchema())
            .withQueryParam("offset")
            .withQueryParam(
                "first_name",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "gateway",
                            new ObjectSchema()
                                .properties(Map.of("hello", new StringSchema()))
                                .extensions(Map.of("x-cb-is-sub-resource", true))))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", operation).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(9);
    String expectedContentFile =
        FileOp.fetchFileContent(sampleDirectoryPath + "/actionWithSubResourceParams.txt");
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContentFile, false);
  }

  @Test
  void shouldSupportPostActionWithRequestBodyContainingCompositeArrayRequestBody()
      throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create_with_items")
            .forResource("subscription")
            .withCompositeArrayRequestBody(
                "subscription_items",
                new ObjectSchema()
                    .required(List.of("item_price_id"))
                    .properties(Map.of("item_price_id", new StringSchema())))
            .withResponse(resourceResponseParam("subscription", subscription))
            .done();

    var spec =
        buildSpec()
            .withResource(subscription)
            .withPostOperation("/subscriptions", operation)
            .done();
    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(9);
    String expectedContentFile =
        FileOp.fetchFileContent(sampleDirectoryPath + "/actionWithCompositeArrayRequestBody.txt");
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContentFile, false);
  }

  @Test
  void shouldSupportActionWithArrayInRequestBody() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("limit", new IntegerSchema())
            .withQueryParam("coupons", new ArraySchema().items(new StringSchema()))
            .withQueryParam("offset")
            .withQueryParam(
                "first_name",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "gateway",
                            new ObjectSchema()
                                .properties(Map.of("hello", new StringSchema()))
                                .properties(
                                    Map.of(
                                        "couponsIds", new ArraySchema().items(new StringSchema())))
                                .extensions(Map.of("x-cb-is-sub-resource", true)))))
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", operation).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(9);
    String expectedContentFile =
        FileOp.fetchFileContent(sampleDirectoryPath + "/actionWithSimpleArrayInRequestBody.txt");
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContentFile, false);
  }

  @Test
  void shouldSupportResourceAsResponse() throws IOException {
    var customer =
        buildResource("customer").withAttribute("id", true).withAttribute("email", true).done();
    var card = buildResource("card").withAttribute("id", true).withAttribute("last4", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer-id")
            .withResponse(
                resourceResponseParam("customer", customer),
                resourceResponseParam("card", card, false))
            .done();
    var spec =
        buildSpec()
            .withResources(customer, card)
            .withOperation("/customers/{customer-id}", operation)
            .done();
    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(13);
    String expectedContentFile =
        FileOp.fetchFileContent(sampleDirectoryPath + "/simpleResponse.txt");
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContentFile, false);
  }

  @Test
  void shouldSupportListResponse() throws IOException {
    var customer =
        buildResource("customer").withAttribute("id", true).withAttribute("email", true).done();
    var card = buildResource("card").withAttribute("id", true).withAttribute("last4", true).done();
    var operation =
        buildListOperation("list")
            .forResource("customer")
            .withResponse(
                resourceResponseParam("customer", customer),
                resourceResponseParam("card", card, false))
            .done();
    var spec =
        buildSpec().withResources(customer, card).withOperation("/customers", operation).done();
    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(13);
    assertPhpModelResourceFileContent(writeStringFileOp, "ListCustomerResponseListObject", false);
  }

  @Test
  void shouldSupportArrayTypeInReturnResponse() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var hierarchy = buildResource("hierarchy").withAttribute("id", true);
    var operation =
        buildOperation("hierarchy")
            .forResource("customer")
            .withPathParam("customer-id")
            .withResponse(resourceResponseParam("hierarchies", hierarchy.arraySchema()))
            .done();
    var spec =
        buildSpec()
            .withResources(customer, hierarchy.done())
            .withOperation("/customers/{customer-id}/hierarchy", operation)
            .done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(13);
    String expectedContentFile =
        FileOp.fetchFileContent(sampleDirectoryPath + "/responseWithListOfResources.txt");
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContentFile, false);
  }

  @Test
  void shouldCreateGlobalEnumFilesInEnumsDirectoryWithSpecifiedGlobalEnums() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("on", "off")).done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    String expectedContentFile = FileOp.fetchFileContent(sampleDirectoryPath + "/simpleEnum.txt");
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContentFile, false);
  }

  @Test
  void shouldCreateAclientFile() throws IOException {
    var customer =
        buildResource("customer").withAttribute("id", true).withAttribute("email", true).done();
    var card = buildResource("card").withAttribute("id", true).withAttribute("last4", true).done();
    var operation =
        buildListOperation("list")
            .forResource("customer")
            .withResponse(
                resourceResponseParam("customer", customer),
                resourceResponseParam("card", card, false))
            .done();
    var spec =
        buildSpec().withResources(customer, card).withOperation("/customers", operation).done();
    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(16);
    String expectedContentFile = FileOp.fetchFileContent(sampleDirectoryPath + "/clientFile.txt");
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContentFile, false);
  }

  @Test
  void eachResourceShouldGenerateLocalEnumFileAndDirectory() throws IOException {
    var type = buildEnum("type", List.of("quantity", "tiered")).setEnumApiName("Type").done();
    var customer =
        buildResource("customer")
            .withAttribute("id", true)
            .withAttribute("net_term_days", new IntegerSchema(), true)
            .withAttribute(
                "vat_number_validated_time",
                new IntegerSchema().format("unix-time").pattern("^[0-9]{10}$"),
                false)
            .withAttribute(
                "resource_version", new IntegerSchema().type("integer").format("int64"), false)
            .withAttribute("auto_close_invoices", new BooleanSchema(), false)
            .withEnumAttribute(type)
            .withAttribute(
                "meta_data", new ObjectSchema().type("object").additionalProperties(true), false)
            .withAttribute(
                "exemption_details", new ArraySchema().items(new ObjectSchema().type(null)), false)
            .done();

    String expectedEnumContent = FileOp.fetchFileContent(sampleDirectoryPath + "/localEnums.txt");

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(8);
    assertPhpModelResourceFileContent(writeStringFileOp, expectedEnumContent, false);
    var createDirectoryFileOp = (FileOp.CreateDirectory) fileOps.get(7);
    assertCreateDirectoryFileOp(createDirectoryFileOp, basePath + "/Resources/Customer", "Enums");
    assertThat(writeStringFileOp.fileName).isEqualTo("Type.php");
  }

  @Test
  void shouldCreateListResponseObjectForListRequests() throws IOException {
    var customer =
        buildResource("customer").withAttribute("id", true).withAttribute("email", true).done();
    var card = buildResource("card").withAttribute("id", true).withAttribute("last4", true).done();
    var operation =
        buildListOperation("list")
            .forResource("customer")
            .withResponse(
                resourceResponseParam("customer", customer),
                resourceResponseParam("card", card, false))
            .done();
    var spec =
        buildSpec().withResources(customer, card).withOperation("/customers", operation).done();
    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(15);
    String expectedContentFile =
        FileOp.fetchFileContent(sampleDirectoryPath + "/ListCustomerResponseListObjectSample.txt");
    assertPhpModelResourceFileContent(writeStringFileOp, expectedContentFile, false);
  }
}
