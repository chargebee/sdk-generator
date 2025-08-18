package com.chargebee.sdk.java;

import static com.chargebee.sdk.test_data.EnumBuilder.buildEnum;
import static com.chargebee.sdk.test_data.OperationBuilder.*;
import static com.chargebee.sdk.test_data.ResourceBuilder.buildResource;
import static com.chargebee.sdk.test_data.ResourceResponseParam.resourceResponseParam;
import static com.chargebee.sdk.test_data.SpecBuilder.buildSpec;
import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.openapi.ApiVersion;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.LanguageTests;
import com.chargebee.sdk.test_data.OperationWithPath;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JavaTests extends LanguageTests {
  public static Java javaSdkGen;
  private final String basePath = "/java/src/main/java/com/chargebee";
  private final String modelsDirectoryPath = "/java/src/main/java/com/chargebee/models";
  private final String enumsDirectoryPath = "/java/src/main/java/com/chargebee/models/enums";
  private final String internalDirectoryPath = "/java/src/main/java/com/chargebee/internal";

  @BeforeAll
  static void beforeAll() {
    javaSdkGen = new Java(GenerationMode.EXTERNAL, ApiVersion.V2, JarType.HVC);
  }

  void assertJavaEnumFileContent(FileOp.WriteString fileOp, String body) {
    assertThat(fileOp.fileContent)
        .startsWith("package com.chargebee.models.enums;\n" + "\n" + body);
  }

  void assertJavaModelFileContent(FileOp.WriteString fileOp, String fileName, String body)
      throws IOException {
    String expectedContent =
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/java/samples/" + fileName)
            .replace("__body__", body);
    // Normalize whitespace for more robust comparison
    String normalizedExpected = expectedContent.replaceAll("\\s+", " ").trim();
    String normalizedActual = fileOp.fileContent.replaceAll("\\s+", " ").trim();
    assertThat(normalizedActual).startsWith(normalizedExpected);
  }

  void assertJavaResultBaseFileContent(FileOp.WriteString fileOp, String body) throws IOException {
    assertThat(fileOp.fileContent)
        .startsWith(
            FileOp.fetchFileContent(
                    "src/test/java/com/chargebee/sdk/java/samples/resultBaseTemplate.txt")
                .replace("__body__", body));
  }

  @Test
  void baseDirectoryShouldNotBeClearedBeforeGeneration() {
    assertThat(javaSdkGen.cleanDirectoryBeforeGenerate()).isFalse();
  }

  @Test
  void shouldCreateModelsDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(0), basePath, "/models");
  }

  @Test
  void shouldCreateEnumsDirectoryInsideModelsDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(1), modelsDirectoryPath, "/enums");
  }

  @Test
  void shouldCreateGlobalEnumFilesInEnumsDirectory() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), enumsDirectoryPath, "AutoCollection.java");
  }

  @Test
  void globalEnumFilesInEnumsDirectoryShouldBeSortedAlphabetically() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var channel = buildEnum("Channel", List.of("app_store", "web", "play_store")).done();
    var spec = buildSpec().withEnums(channel, autoCollection).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), enumsDirectoryPath, "AutoCollection.java");
    assertWriteStringFileOp(fileOps.get(3), enumsDirectoryPath, "Channel.java");
  }

  @Test
  void shouldHaveBigDecimalInImportsIfAnyOneAttributeTypeIsBigDecimal() throws IOException {
    var Transaction =
        buildResource("transaction")
            .withAttribute("exchange_rate", new NumberSchema().format("decimal"))
            .done();
    var spec = buildSpec().withResource(Transaction).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(writeStringFileOp, "importsWithBigDecimal.txt", "");
  }

  @Test
  void shouldHaveBigDecimalInImportsIfAnyOneSubResourceAttributeTypeIsBigDecimal()
      throws IOException {
    var LineItemTax =
        buildResource("line_item_tax")
            .withAttribute("prorated_taxable_amount", new NumberSchema().format("decimal"))
            .done();
    var CreditNote =
        buildResource("credit_note").withSubResourceAttribute("line_item_tax", LineItemTax).done();

    var spec = buildSpec().withResource(CreditNote).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(writeStringFileOp, "importsWithBigDecimal.txt", "");
  }

  @Test
  void shouldHaveUnknownAsDefaultPossibleValue() throws IOException {
    var taxjarExemptionCategory = buildEnum("TaxjarExemptionCategory", List.of()).done();
    var spec = buildSpec().withEnums(taxjarExemptionCategory).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaEnumFileContent(
        writeStringFileOp,
        """
public enum TaxjarExemptionCategory {
    _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
    java-client version incompatibility. We suggest you to upgrade to the latest version */
}""");
  }

  @Test
  void shouldCreateGlobalEnumFilesInEnumsDirectoryWithSpecifiedGlobalEnums() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("on", "off")).done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaEnumFileContent(
        writeStringFileOp,
        """
public enum AutoCollection {
    ON,
    OFF,
    _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
    java-client version incompatibility. We suggest you to upgrade to the latest version */
}""");
  }

  @Test
  void possibleValuesShouldBeInUpperCase() throws IOException {
    var applyOn = buildEnum("ApplyOn", List.of("invoice_amount", "specific_item_price")).done();
    var spec = buildSpec().withEnums(applyOn).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaEnumFileContent(
        writeStringFileOp,
        """
public enum ApplyOn {
    INVOICE_AMOUNT,
    SPECIFIC_ITEM_PRICE,
    _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
    java-client version incompatibility. We suggest you to upgrade to the latest version */
}""");
  }

  @Test
  void shouldMarkDeprecatedEnumValuesWithDeprecatedTag() throws IOException {
    var autoCollection =
        buildEnum("AutoCollection", List.of("on", "pause", "off"))
            .withDeprecatedValues(List.of("pause"))
            .done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaEnumFileContent(
        writeStringFileOp,
        """
public enum AutoCollection {
    @Deprecated
    PAUSE,
    ON,
    OFF,
    _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
    java-client version incompatibility. We suggest you to upgrade to the latest version */
}""");
  }

  @Test
  void shouldIgnoreDeprecatedIfParameterBlankOptionExtensionIsSet() throws IOException {
    var paymentMethod =
        buildEnum(
                "PaymentMethod", List.of("card", "app_store", "play_store", "check", "chargeback"))
            .withDeprecatedValues(List.of("app_store", "play_store"))
            .withParameterBlankOption()
            .done();
    var spec = buildSpec().withEnums(paymentMethod).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaEnumFileContent(
        writeStringFileOp,
        """
public enum PaymentMethod {
    CARD,
    CHECK,
    CHARGEBACK,
    _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
    java-client version incompatibility. We suggest you to upgrade to the latest version */
}""");
  }

  @Test
  void shouldCreateResourceFileInModelsDirectory() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), modelsDirectoryPath, "Customer.java");
  }

  @Test
  void eachResourceShouldHaveSeparateDeclaration() throws IOException {
    var spec = buildSpec().withResources("customer", "advance_invoice_schedule").done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), modelsDirectoryPath, "AdvanceInvoiceSchedule.java");
    assertWriteStringFileOp(fileOps.get(3), modelsDirectoryPath, "Customer.java");
  }

  @Test
  void eachResourceShouldHaveImportStatements() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(writeStringFileOp, "imports.txt", "");
  }

  @Test
  void eachResourceShouldHaveSpecifiedResourceClass() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp, "imports.txt", "public class Customer extends Resource<Customer> {");
  }

  @Test
  void shouldIgnoreHiddenFromSDKResources() throws IOException {
    var customer = buildResource("customer").done();
    var advance_invoice_schedule =
        buildResource("advance_invoice_schedule").asHiddenFromSDKGeneration().done();
    var spec = buildSpec().withResources(customer, advance_invoice_schedule).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp, "imports.txt", "public class Customer extends Resource<Customer> {");
  }

  @Test
  void shouldContainDependentResources() throws IOException {
    var advance_invoice_schedule =
        buildResource("advance_invoice_schedule").asHiddenFromSDKGeneration().done();
    var subscription_estimate = buildResource("subscription_estimate").asDependentResource().done();
    var spec = buildSpec().withResources(advance_invoice_schedule, subscription_estimate).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        "public class SubscriptionEstimate extends Resource<SubscriptionEstimate> {");
  }

  @Test
  void shouldCreateResourceFileWithSpecifiedEnumAttributes() throws IOException {
    var vat_number_status =
        buildEnum("vat_number_status", List.of("valid", "invalid", "not_validated", "undetermined"))
            .asApi()
            .done();
    var resource = buildResource("customer").withEnumAttribute(vat_number_status).done();
    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Customer extends Resource<Customer> {

    public enum VatNumberStatus {
        VALID,
        INVALID,
        NOT_VALIDATED,
        UNDETERMINED,
        _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
        java-client version incompatibility. We suggest you to upgrade to the latest version */
    }""");
  }

  @Test
  void deprecatedTagForDeprecatedEnumsInResourceFiles() throws IOException {
    var vat_number_status =
        buildEnum("vat_number_status", List.of("valid", "invalid", "not_validated", "undetermined"))
            .asApi()
            .done();
    var card_status = buildEnum("card_status", List.of("no_card", "valid")).asApi().done();
    var resource =
        buildResource("customer")
            .withEnumAttribute(vat_number_status)
            .withEnumAttribute(card_status)
            .withDeprecatedAttributes(List.of("card_status"))
            .done();
    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Customer extends Resource<Customer> {

    public enum VatNumberStatus {
        VALID,
        INVALID,
        NOT_VALIDATED,
        UNDETERMINED,
        _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
        java-client version incompatibility. We suggest you to upgrade to the latest version */
    }

    @Deprecated
    public enum CardStatus {
        NO_CARD,
        VALID,
        _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
        java-client version incompatibility. We suggest you to upgrade to the latest version */
    }""");
  }

  @Test
  void deprecatedTagForDeprecatedPossibleValuesInEnumsInResourceFiles() throws IOException {
    var discountType =
        buildEnum("discount_type", List.of("offer_quantity", "fixed_amount", "percentage"))
            .withDeprecatedValues(List.of("offer_quantity"))
            .asApi()
            .done();
    var resource = buildResource("coupon").withEnumAttribute(discountType).done();
    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Coupon extends Resource<Coupon> {

    public enum DiscountType {
        @Deprecated
        OFFER_QUANTITY,
        FIXED_AMOUNT,
        PERCENTAGE,
        _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
        java-client version incompatibility. We suggest you to upgrade to the latest version */
    }""");
  }

  @Test
  void shouldCreateResultBaseFileInInternalDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), internalDirectoryPath, "ResultBase.java");
  }

  @Test
  void resultBaseFileShouldHaveImportStatementsAndResultBaseClass() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            """
            package com.chargebee.internal;

            import com.chargebee.models.*;
            import org.json.JSONException;
            import org.json.JSONObject;
            import org.json.JSONArray;
            import java.util.List;
            import java.util.ArrayList;

            public class ResultBase {

                private JSONObject jsonObj;

                public ResultBase(JSONObject jsonObj) {
                    this.jsonObj = jsonObj;
                }
            """);
  }

  @Test
  void resultBaseFileShouldHaveClassDefnsForResourceResponses() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer_id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}", operation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        """
            public Customer customer() {
                return (Customer)get("customer");
            }\
        """);
  }

  @Test
  void resultBaseFileShouldHaveClassDefnsForMultipleResourceResponses() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var token = buildResource("token").withAttribute("id", true).done();
    var contact = buildResource("contact").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer_id")
            .withResponse(
                resourceResponseParam("contact", contact),
                resourceResponseParam("token", token),
                resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResources(customer, token, contact)
            .withOperation("/customers/{customer-id}", operation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        """
            public Contact contact() {
                return (Contact)get("contact");
            }

            public Customer customer() {
                return (Customer)get("customer");
            }

            public Token token() {
                return (Token)get("token");
            }\
        """);
  }

  @Test
  void shouldIgnoreHiddenFromSDKResourcesInResultBase() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var token = buildResource("token").withAttribute("id", true).done();
    var contact =
        buildResource("contact").withAttribute("id", true).asHiddenFromSDKGeneration().done();
    var operation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer-id")
            .withResponse(
                resourceResponseParam("contact", contact),
                resourceResponseParam("token", token),
                resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResources(customer, token, contact)
            .withOperation("/customers/{customer-id}", operation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        """
            public Customer customer() {
                return (Customer)get("customer");
            }

            public Token token() {
                return (Token)get("token");
            }\
        """);
  }

  @Test
  void shouldIgnoreThirdPartyResource() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var token = buildResource("token").withAttribute("id", true).done();
    var ThirdPartySyncDetail =
        buildResource("third_party_resource")
            .withAttribute("third_party_configuration", false)
            .asThirdPartyFromSDKGeneration()
            .done();
    var retrieveOperation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer_id")
            .withResponse(
                resourceResponseParam("token", token), resourceResponseParam("customer", customer))
            .done();
    var retrieveLatestSyncOperation =
        buildOperation("retrieve_latest_sync")
            .forResource("third_party_sync_details")
            .withResponse(resourceResponseParam("third_party_sync_detail", ThirdPartySyncDetail))
            .done();
    var spec =
        buildSpec()
            .withResources(customer, token)
            .withOperations(
                new OperationWithPath("/customers/{customer-id}", retrieveOperation),
                new OperationWithPath(
                    "/third_party_sync_details/retrieve_latest_sync", retrieveLatestSyncOperation))
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        """
            public Customer customer() {
                return (Customer)get("customer");
            }

            public Token token() {
                return (Token)get("token");
            }\
        """);
  }

  @Test
  void resultBaseFileShouldHaveClassDefnsForListResponses() throws IOException {
    var unbilledCharge =
        buildResource("unbilled_charge").withAttribute("subscription_id", true).done();
    var invoice = buildResource("invoice").withAttribute("id", true).done();
    var operation =
        buildPostOperation("invoice_unbilled_charges")
            .forResource("unbilled_charge")
            .withResponse(
                resourceResponseParam("unbilled_charge", unbilledCharge),
                resourceResponseParam("invoice", invoice),
                resourceResponseParam(
                    "invoices", new ArraySchema().items(new Schema().$ref("Invoice"))))
            .done();
    var spec =
        buildSpec()
            .withResources(unbilledCharge, invoice)
            .withOperation("/unbilled_charges/invoice_unbilled_charges", operation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        """
            public Invoice invoice() {
                return (Invoice)get("invoice");
            }

            public UnbilledCharge unbilledCharge() {
                return (UnbilledCharge)get("unbilled_charge");
            }

            public List<Invoice> invoices() {
                return (List<Invoice>) getList("invoices", "invoice");
            }\
        """);
  }

  @Test
  void shouldHaveEachOperationAsSeperateRequestMethod() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema(), true)
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var retrieveOperation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer-id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers", createOperation)
            .withOperation("/customers/{customer-id}", retrieveOperation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static CreateRequest create() {
                String uri = uri("customers");
                return new CreateRequest(Method.POST, uri).setIdempotency(false);
            }

            public static Request retrieve(String id) {
                String uri = uri("customers", nullCheck(id));
                return new Request(Method.GET, uri);
            }
        """);
  }

  @Test
  void shouldHaveSortOrderForEachOperation() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var HierarchyRequest =
        buildOperation("hierarchy")
            .forResource("customer")
            .withPathParam("customer-id")
            .withQueryParam("hierarchy_operation_type", new StringSchema()._enum(List.of()))
            .withResponse(resourceResponseParam("customer", customer))
            .withSortOrder(14)
            .asInputObjNeeded()
            .done();
    var retrieveOperation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer-id")
            .withResponse(resourceResponseParam("customer", customer))
            .withSortOrder(2)
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}/hierarchy", HierarchyRequest)
            .withOperation("/customers/{customer-id}", retrieveOperation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static Request retrieve(String id) {
                String uri = uri("customers", nullCheck(id));
                return new Request(Method.GET, uri);
            }

            public static HierarchyRequest hierarchy(String id) {
                String uri = uri("customers", nullCheck(id), "hierarchy");
                return new HierarchyRequest(Method.GET, uri);
            }
        """);
  }

  @Test
  void shouldHaveOperationNameAsRequestTypeIfRequestBodyPresent() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema(), true)
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static CreateRequest create() {
                String uri = uri("customers");
                return new CreateRequest(Method.POST, uri).setIdempotency(false);
            }
        """);
  }

  @Test
  void shouldHaveOperationNameAsRequestTypeIfQueryParamsPresent() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var listOperation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("include_deleted", new BooleanSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", listOperation).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static CustomerListRequest list() {
                String uri = uri("customers");
                return new CustomerListRequest(uri);
            }
        """);
  }

  @Test
  void shouldHaveEntityTypeAsRequestTypeIfNoRequestBodyOrQueryParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var retrieveOperation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer-id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}", retrieveOperation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static Request retrieve(String id) {
                String uri = uri("customers", nullCheck(id));
                return new Request(Method.GET, uri);
            }
        """);
  }

  @Test
  void shouldHavePathParamAsArgumentIfPresent() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var updateRequest =
        buildPostOperation("update")
            .forResource("customer")
            .withPathParam("customer-id")
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers/{customer-id}", updateRequest)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static UpdateRequest update(String id) {
                String uri = uri("customers", nullCheck(id));
                return new UpdateRequest(Method.POST, uri).setIdempotency(false);
            }
        """);
  }

  @Test
  void shouldHaveUrlForOperation() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema(), true)
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static CreateRequest create() {
                String uri = uri("customers");
                return new CreateRequest(Method.POST, uri).setIdempotency(false);
            }
        """);
  }

  @Test
  void shouldHavePathParamInUrlIfPresent() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var updateRequest =
        buildPostOperation("update")
            .forResource("customer")
            .withPathParam("customer-id")
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers/{customer-id}", updateRequest)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static UpdateRequest update(String id) {
                String uri = uri("customers", nullCheck(id));
                return new UpdateRequest(Method.POST, uri).setIdempotency(false);
            }
        """);
  }

  @Test
  void shouldHaveUrlSuffixIfPresent() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var UpdateBillingInfoRequest =
        buildPostOperation("updateBillingInfo")
            .forResource("customer")
            .withPathParam("customer-id")
            .withRequestBody("vat_number", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation(
                "/customers/{customer-id}/update_billing_info", UpdateBillingInfoRequest)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static UpdateBillingInfoRequest updateBillingInfo(String id) {
                String uri = uri("customers", nullCheck(id), "update_billing_info");
                return new UpdateBillingInfoRequest(Method.POST, uri).setIdempotency(false);
            }
        """);
  }

  @Test
  void shouldHaveHttpMethodInReturnStatement() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var hierarchyRequest =
        buildOperation("hierarchy")
            .forResource("customer")
            .withPathParam("customer-id")
            .withQueryParam("hierarchy_operation_type", new StringSchema()._enum(List.of()))
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var updateBillingInfoRequest =
        buildPostOperation("updateBillingInfo")
            .forResource("customer")
            .withPathParam("customer-id")
            .withRequestBody("vat_number", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation(
                "/customers/{customer-id}/update_billing_info", updateBillingInfoRequest)
            .withOperation("/customers/{customer-id}/hierarchy", hierarchyRequest)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static UpdateBillingInfoRequest updateBillingInfo(String id) {
                String uri = uri("customers", nullCheck(id), "update_billing_info");
                return new UpdateBillingInfoRequest(Method.POST, uri).setIdempotency(false);
            }

            public static HierarchyRequest hierarchy(String id) {
                String uri = uri("customers", nullCheck(id), "hierarchy");
                return new HierarchyRequest(Method.GET, uri);
            }
        """);
  }

  @Test
  void shouldNotHaveHttpMethodInReturnStatementForListOperation() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var listOperation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("include_deleted", new BooleanSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", listOperation).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static CustomerListRequest list() {
                String uri = uri("customers");
                return new CustomerListRequest(uri);
            }
        """);
  }

  @Test
  void shouldHaveObsoleteTagIfDeprecated() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var addPromotionalCreditsRequest =
        buildPostOperation("addPromotionalCredits")
            .forResource("customer")
            .withPathParam("customer-id")
            .withRequestBody("currency_code", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .isDeprecatedOperation()
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation(
                "/customers/{customer-id}/add_promotional_credits", addPromotionalCreditsRequest)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            @Deprecated
            public static AddPromotionalCreditsRequest addPromotionalCredits(String id) {
                String uri = uri("customers", nullCheck(id), "add_promotional_credits");
                return new AddPromotionalCreditsRequest(Method.POST, uri).setIdempotency(false);
            }
        """);
  }

  @Test
  void shouldIgnoreBulkOperations() throws IOException {
    var transaction = buildResource("transaction").done();
    var virtualBankAccount = buildResource("virtual_bank_account").done();
    var syncFundOperation =
        buildPostOperation("syncFund")
            .forResource("virtual_bank_account")
            .withResponse(resourceResponseParam("transaction", transaction))
            .asBulkOperationFromSDKGeneration()
            .done();
    var spec =
        buildSpec()
            .withResource(virtualBankAccount)
            .withPostOperation(
                "/virtual_bank_accounts/{virtual-bank-account-id}/sync_fund", syncFundOperation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(writeStringFileOp, "imports.txt", "");
  }

  @Test
  void shouldIgnoreHiddenFromGenerationOperations() throws IOException {
    var invoice = buildResource("invoice").done();
    var createAvalaraDocOperation =
        buildPostOperation("createAvalaraDoc")
            .forResource("invoice")
            .withResponse(resourceResponseParam("invoice", invoice))
            .asHiddenFromSDKGeneration()
            .done();
    var spec =
        buildSpec()
            .withResource(invoice)
            .withPostOperation("/invoices/create_avalara_doc", createAvalaraDocOperation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(writeStringFileOp, "imports.txt", "");
  }

  @Test
  void shouldHaveObsoleteTagIfAttributeIsDeprecated() throws IOException {
    var card_status =
        buildEnum("card_status", List.of("NO_CARD", "VALID")).setEnumApiName("CardStatus").done();
    var customer =
        buildResource("customer")
            .withEnumAttribute(card_status, false)
            .withDeprecatedAttributes(List.of("card_status"))
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            @Deprecated
            public CardStatus cardStatus() {
                return optEnum("card_status", CardStatus.class);
            }\
        """);
  }

  @Test
  void enumAttributeShouldHaveEnumKeywordInReturnType() throws IOException {
    var autoCollection =
        buildEnum("auto_collection", List.of("On", "Off")).setEnumApiName("AutoCollection").done();
    var customer = buildResource("customer").withEnumAttribute(autoCollection, true).done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public AutoCollection autoCollection() {
                return reqEnum("auto_collection", AutoCollection.class);
            }\
        """);
  }

  @Test
  void enumAttributeShouldHaveQuestionMarkIfOptional() throws IOException {
    var pii_cleared =
        buildEnum("pii_cleared", List.of("active", "cleared")).setEnumApiName("PiiCleared").done();
    var customer = buildResource("customer").withEnumAttribute(pii_cleared).done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public PiiCleared piiCleared() {
                return optEnum("pii_cleared", PiiCleared.class);
            }\
        """);
  }

  @Test
  void shouldHaveAttributeDeclarationForSubResource() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = javaSdkGen.generate(modelsDirectoryPath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(writeStringFileOp, "customer_billing_address.txt", "");
  }

  @Test
  void shouldFollowSortOrderForSubResourceAttributes() throws IOException {
    var type = buildEnum("type", List.of("quantity", "tiered")).setEnumApiName("Type").done();
    var billingAddress =
        buildResource("billing_address").withAttribute("first_name").withSortOrder(10).done();
    var paymentMethod =
        buildResource("payment_method").withEnumAttribute(type, true).withSortOrder(14).done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("payment_method", paymentMethod)
            .withSubResourceAttribute("billing_address", billingAddress)
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = javaSdkGen.generate(modelsDirectoryPath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp, "customerSortOrderForSubResourceAttributes.txt", "");
  }

  @Test
  void shouldHaveAttributeDeclarationForListSubResource() throws IOException {
    var balance =
        buildResource("balance")
            .withAttribute(
                "promotional_credits", new IntegerSchema().type("integer").format("int64"), true)
            .done();
    var customer =
        buildResource("customer").withSubResourceArrayAttribute("balances", balance).done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = javaSdkGen.generate(modelsDirectoryPath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp, "customerAttributeDeclarationForListSubResource.txt", "");
  }

  @Test
  void shouldSupportBothArrayAndNormalSubResourceAttribute() throws IOException {
    var referralUrl =
        buildResource("referral_url")
            .withAttribute(
                "promotional_credits", new IntegerSchema().type("integer").format("int64"))
            .done();
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var customer =
        buildResource("customer")
            .withSubResourceArrayAttribute("referral_urls", referralUrl)
            .withSubResourceAttribute("billing_address", billingAddress)
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = javaSdkGen.generate(modelsDirectoryPath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp, "customerShouldHaveArrayAndNormalSubResourceAttribute.txt", "");
  }

  @Test
  void eachResourceAttributeShouldHaveType() throws IOException {

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

    var spec = buildSpec().withResources(customer, invoice, coupon).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
        public class Coupon extends Resource<Coupon> {

            //Constructors
            //============

            public Coupon(String jsonStr) {
                super(jsonStr);
            }

            public Coupon(JSONObject jsonObj) {
                super(jsonObj);
            }

            // Fields
            //=======

            public Double discountPercentage() {
                return optDouble("discount_percentage");
            }

            public List<String> planIds() {
                return optList("plan_ids", String.class);
            }

            // Operations
            //===========


        }
        """);

    writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertJavaModelFileContent(
        writeStringFileOp,
        "customer.txt",
        """
            public String id() {
                return reqString("id");
            }

            public Integer netTermDays() {
                return reqInteger("net_term_days");
            }

            public Timestamp vatNumberValidatedTime() {
                return optTimestamp("vat_number_validated_time");
            }

            public Long resourceVersion() {
                return optLong("resource_version");
            }

            public Boolean autoCloseInvoices() {
                return optBoolean("auto_close_invoices");
            }

            public JSONObject metaData() {
                return optJSONObject("meta_data");
            }

            public JSONArray exemptionDetails() {
                return optJSONArray("exemption_details");
            }\
        """);

    writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertJavaModelFileContent(
        writeStringFileOp,
        "importsWithBigDecimal.txt",
        """
        public class Invoice extends Resource<Invoice> {

            //Constructors
            //============

            public Invoice(String jsonStr) {
                super(jsonStr);
            }

            public Invoice(JSONObject jsonObj) {
                super(jsonObj);
            }

            // Fields
            //=======

            public BigDecimal proratedTaxableAmount() {
                return optBigDecimal("prorated_taxable_amount");
            }
        """);
  }

  @Test
  void shouldHaveRequestBodyParamsInOperationRequestClass() throws IOException {
    var customer = buildResource("customer").done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema())
            .withRequestBody(
                "net_term_days", new IntegerSchema().type("integer").format("int32"), false)
            .withRequestBody("registered_for_gst", new BooleanSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp, "customerRequestBodyParamsInOperationRequestClass.txt", "");
  }

  @Test
  void shouldHaveOptSuffixForOptionalRequestBodyParams() throws IOException {
    var customer = buildResource("customer").done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema(), false)
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp, "customerOptSuffixForOptionalRequestBodyParams.txt", "");
  }

  @Test
  void shouldHaveObsoleteTagForDeprecatedRequestBodyParams() throws IOException {
    var invoice = buildResource("invoice").done();
    var createOperation =
        buildPostOperation("create")
            .forResource("invoice")
            .withRequestBody("coupon", new StringSchema().deprecated(true), false)
            .withResponse(resourceResponseParam("invoice", invoice))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(invoice).withPostOperation("/invoices", createOperation).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
        public class Invoice extends Resource<Invoice> {

            //Constructors
            //============

            public Invoice(String jsonStr) {
                super(jsonStr);
            }

            public Invoice(JSONObject jsonObj) {
                super(jsonObj);
            }

            // Fields
            //=======

            // Operations
            //===========

            public static CreateRequest create() {
                String uri = uri("invoices");
                return new CreateRequest(Method.POST, uri).setIdempotency(false);
            }


            // Operation Request Classes
            //==========================

            public static class CreateRequest extends Request<CreateRequest> {

                private CreateRequest(Method httpMeth, String uri) {
                    super(httpMeth, uri);
                }
           \s
                @Deprecated
                public CreateRequest coupon(String coupon) {
                    params.addOpt("coupon", coupon);
                    return this;
                }


                @Override
                public Params params() {
                    return params;
                }
            }

        }""");
  }

  @Test
  void shouldSupportEnumAttributesInRequestBodyParams() throws IOException {
    var customer = buildResource("customer").done();
    var ChangeBillingDateOperation =
        buildPostOperation("changeBillingDate")
            .forResource("customer")
            .withRequestBody(
                "billing_day_of_week",
                new StringSchema()
                    ._enum(List.of("sunday", "monday"))
                    .extensions(Map.of("x-cb-meta-model-name", "customer")))
            .withPathParam("customer-id")
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation(
                "/customers/{customer-id}/change_billing_date", ChangeBillingDateOperation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Customer extends Resource<Customer> {

    public enum BillingDayOfWeek {
        SUNDAY,
        MONDAY,
        _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
        java-client version incompatibility. We suggest you to upgrade to the latest version */
    }

    //Constructors
    //============

    public Customer(String jsonStr) {
        super(jsonStr);
    }

    public Customer(JSONObject jsonObj) {
        super(jsonObj);
    }

    // Fields
    //=======

    // Operations
    //===========

    public static ChangeBillingDateRequest changeBillingDate(String id) {
        String uri = uri("customers", nullCheck(id), "change_billing_date");
        return new ChangeBillingDateRequest(Method.POST, uri).setIdempotency(false);
    }


    // Operation Request Classes
    //==========================

    public static class ChangeBillingDateRequest extends Request<ChangeBillingDateRequest> {

        private ChangeBillingDateRequest(Method httpMeth, String uri) {
            super(httpMeth, uri);
        }
   \s
        public ChangeBillingDateRequest billingDayOfWeek(Customer.BillingDayOfWeek billingDayOfWeek) {
            params.addOpt("billing_day_of_week", billingDayOfWeek);
            return this;
        }


        @Override
        public Params params() {
            return params;
        }
    }

}""");
  }

  @Test
  void shouldSupportGlobalEnumAttributesInRequestBodyParams() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off"));
    var customer = buildResource("customer").done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "auto_collection",
                new StringSchema()
                    ._enum(List.of("on", "off"))
                    .extensions(
                        Map.of(
                            "x-cb-is-global-enum",
                            true,
                            "x-cb-global-enum-reference",
                            "./enums/" + autoCollection.typeName + ".yaml")))
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Customer extends Resource<Customer> {

    //Constructors
    //============

    public Customer(String jsonStr) {
        super(jsonStr);
    }

    public Customer(JSONObject jsonObj) {
        super(jsonObj);
    }

    // Fields
    //=======

    // Operations
    //===========

    public static CreateRequest create() {
        String uri = uri("customers");
        return new CreateRequest(Method.POST, uri).setIdempotency(false);
    }


    // Operation Request Classes
    //==========================

    public static class CreateRequest extends Request<CreateRequest> {

        private CreateRequest(Method httpMeth, String uri) {
            super(httpMeth, uri);
        }
   \s
        public CreateRequest autoCollection(com.chargebee.models.enums.AutoCollection autoCollection) {
            params.addOpt("auto_collection", autoCollection);
            return this;
        }


        @Override
        public Params params() {
            return params;
        }
    }

}""");
  }

  @Test
  void shouldSupportMultiAttributesInRequestBodyParams() throws IOException {
    var customer = buildResource("customer").done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "billing_address",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "first_name",
                            new StringSchema().extensions(Map.of("x-cb-is-sub-resource", true))))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Customer extends Resource<Customer> {

    //Constructors
    //============

    public Customer(String jsonStr) {
        super(jsonStr);
    }

    public Customer(JSONObject jsonObj) {
        super(jsonObj);
    }

    // Fields
    //=======

    // Operations
    //===========

    public static CreateRequest create() {
        String uri = uri("customers");
        return new CreateRequest(Method.POST, uri).setIdempotency(false);
    }


    // Operation Request Classes
    //==========================

    public static class CreateRequest extends Request<CreateRequest> {

        private CreateRequest(Method httpMeth, String uri) {
            super(httpMeth, uri);
        }
   \s
        public CreateRequest billingAddressFirstName(String billingAddressFirstName) {
            params.addOpt("billing_address[first_name]", billingAddressFirstName);
            return this;
        }

        @Override
        public Params params() {
            return params;
        }
    }

}""");
  }

  @Test
  void shouldSupportGlobalEnumsInMultiAttributesInRequestBodyParams() throws IOException {
    var customer = buildResource("customer").done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "billing_address",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "validation_status",
                            new StringSchema()
                                ._enum(List.of("valid", "not_validated"))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-global-enum",
                                        true,
                                        "x-cb-global-enum-reference",
                                        "./enums/ValidationStatus.yaml",
                                        "x-cb-is-sub-resource",
                                        true))))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Customer extends Resource<Customer> {

    //Constructors
    //============

    public Customer(String jsonStr) {
        super(jsonStr);
    }

    public Customer(JSONObject jsonObj) {
        super(jsonObj);
    }

    // Fields
    //=======

    // Operations
    //===========

    public static CreateRequest create() {
        String uri = uri("customers");
        return new CreateRequest(Method.POST, uri).setIdempotency(false);
    }


    // Operation Request Classes
    //==========================

    public static class CreateRequest extends Request<CreateRequest> {

        private CreateRequest(Method httpMeth, String uri) {
            super(httpMeth, uri);
        }
   \s
        public CreateRequest billingAddressValidationStatus(com.chargebee.models.enums.ValidationStatus billingAddressValidationStatus) {
            params.addOpt("billing_address[validation_status]", billingAddressValidationStatus);
            return this;
        }

        @Override
        public Params params() {
            return params;
        }
    }

}""");
  }

  @Test
  void shouldSupportEnumsInMultiAttributesInRequestBodyParams() throws IOException {
    var invoice = buildResource("invoice").done();
    var createForChargeItemsAndChargesOperation =
        buildPostOperation("createForChargeItemsAndCharges")
            .forResource("invoice")
            .withRequestBody(
                "payment_intent",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "payment_method_type",
                            new StringSchema()
                                ._enum(List.of("card", "ideal", "sofort"))
                                .extensions(Map.of("x-cb-is-sub-resource", true))))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("invoice", invoice))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(invoice)
            .withPostOperation(
                "/invoices/create_for_charge_items_and_charges",
                createForChargeItemsAndChargesOperation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Invoice extends Resource<Invoice> {

    public enum PaymentMethodType {
        CARD,
        IDEAL,
        SOFORT,
        _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
        java-client version incompatibility. We suggest you to upgrade to the latest version */
    }

    //Constructors
    //============

    public Invoice(String jsonStr) {
        super(jsonStr);
    }

    public Invoice(JSONObject jsonObj) {
        super(jsonObj);
    }

    // Fields
    //=======

    // Operations
    //===========

    public static CreateForChargeItemsAndChargesRequest createForChargeItemsAndCharges() {
        String uri = uri("invoices", "create_for_charge_items_and_charges");
        return new CreateForChargeItemsAndChargesRequest(Method.POST, uri).setIdempotency(false);
    }


    // Operation Request Classes
    //==========================

    public static class CreateForChargeItemsAndChargesRequest extends Request<CreateForChargeItemsAndChargesRequest> {

        private CreateForChargeItemsAndChargesRequest(Method httpMeth, String uri) {
            super(httpMeth, uri);
        }
   \s
        public CreateForChargeItemsAndChargesRequest paymentIntentPaymentMethodType(PaymentIntent.PaymentMethodType paymentIntentPaymentMethodType) {
            params.addOpt("payment_intent[payment_method_type]", paymentIntentPaymentMethodType);
            return this;
        }

        @Override
        public Params params() {
            return params;
        }
    }

}""");
  }

  @Test
  void shouldHavaObsoleteTagForDeprecatedMultiAttributesInRequestBodyParams() throws IOException {
    var invoice = buildResource("invoice").done();
    var createForChargeItemsAndChargesOperation =
        buildPostOperation("createForChargeItemsAndCharges")
            .forResource("invoice")
            .withRequestBody(
                "payment_intent",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "gw_payment_method_id",
                            new StringSchema()
                                .deprecated(true)
                                .extensions(Map.of("x-cb-is-sub-resource", true))))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("invoice", invoice))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(invoice)
            .withPostOperation(
                "/invoices/create_for_charge_items_and_charges",
                createForChargeItemsAndChargesOperation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Invoice extends Resource<Invoice> {

    //Constructors
    //============

    public Invoice(String jsonStr) {
        super(jsonStr);
    }

    public Invoice(JSONObject jsonObj) {
        super(jsonObj);
    }

    // Fields
    //=======

    // Operations
    //===========

    public static CreateForChargeItemsAndChargesRequest createForChargeItemsAndCharges() {
        String uri = uri("invoices", "create_for_charge_items_and_charges");
        return new CreateForChargeItemsAndChargesRequest(Method.POST, uri).setIdempotency(false);
    }


    // Operation Request Classes
    //==========================

    public static class CreateForChargeItemsAndChargesRequest extends Request<CreateForChargeItemsAndChargesRequest> {

        private CreateForChargeItemsAndChargesRequest(Method httpMeth, String uri) {
            super(httpMeth, uri);
        }
   \s
        @Deprecated
        public CreateForChargeItemsAndChargesRequest paymentIntentGwPaymentMethodId(String paymentIntentGwPaymentMethodId) {
            params.addOpt("payment_intent[gw_payment_method_id]", paymentIntentGwPaymentMethodId);
            return this;
        }

        @Override
        public Params params() {
            return params;
        }
    }

}""");
  }

  @Test
  void shouldSupportListMultiAttributesInRequestBodyParams() throws IOException {
    var customer = buildResource("customer").done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "entity_identifiers",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "id",
                            new ArraySchema()
                                .items(
                                    new StringSchema()
                                        .deprecated(false)
                                        .extensions(Map.of("x-cb-is-sub-resource", true)))))
                    .extensions(Map.of("x-cb-is-composite-array-request-body", true)))
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Customer extends Resource<Customer> {

    //Constructors
    //============

    public Customer(String jsonStr) {
        super(jsonStr);
    }

    public Customer(JSONObject jsonObj) {
        super(jsonObj);
    }

    // Fields
    //=======

    // Operations
    //===========

    public static CreateRequest create() {
        String uri = uri("customers");
        return new CreateRequest(Method.POST, uri).setIdempotency(false);
    }


    // Operation Request Classes
    //==========================

    public static class CreateRequest extends Request<CreateRequest> {

        private CreateRequest(Method httpMeth, String uri) {
            super(httpMeth, uri);
        }
   \s
        public CreateRequest entityIdentifierId(int index, String entityIdentifierId) {
            params.addOpt("entity_identifiers[id][" + index + "]", entityIdentifierId);
            return this;
        }
        @Override
        public Params params() {
            return params;
        }
    }

}""");
  }

  @Test
  void shouldContinueParameterBlankOptionAsEmptyInListMultiAttributesInRequestBodyParams()
      throws IOException {
    var estimate = buildResource("estimate").done();
    var createOperation =
        buildPostOperation("updateSubscription")
            .forResource("estimate")
            .withRequestBody(
                "event_based_addons",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "id",
                            new ArraySchema()
                                .items(
                                    new StringSchema()
                                        .deprecated(false)
                                        .extensions(Map.of("x-cb-is-sub-resource", true)))))
                    .extensions(
                        Map.of(
                            "x-cb-is-composite-array-request-body",
                            true,
                            "x-cb-parameter-blank-option",
                            "as_empty")))
            .withResponse(resourceResponseParam("estimate", estimate))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(estimate)
            .withPostOperation("/estimates/update_subscription", createOperation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Estimate extends Resource<Estimate> {

    //Constructors
    //============

    public Estimate(String jsonStr) {
        super(jsonStr);
    }

    public Estimate(JSONObject jsonObj) {
        super(jsonObj);
    }

    // Fields
    //=======

    // Operations
    //===========

    public static UpdateSubscriptionRequest updateSubscription() {
        String uri = uri("estimates", "update_subscription");
        return new UpdateSubscriptionRequest(Method.POST, uri).setIdempotency(false);
    }


    // Operation Request Classes
    //==========================

    public static class UpdateSubscriptionRequest extends Request<UpdateSubscriptionRequest> {

        private UpdateSubscriptionRequest(Method httpMeth, String uri) {
            super(httpMeth, uri);
        }

        public UpdateSubscriptionRequest eventBasedAddonId(int index, String eventBasedAddonId) {
            params.addOpt("event_based_addons[id][\" + index + \"]\", eventBasedAddonId);
            return this;
        }
        @Override
        public Params params() {
            return params;
        }
    }

}
""");
  }

  @Test
  void shouldSupportGlobalEnumsInListMultiAttributesInRequestBodyParams() throws IOException {
    var invoice = buildResource("invoice").done();
    var createForChargeItemsAndChargesOperation =
        buildPostOperation("createForChargeItemsAndCharges")
            .forResource("invoice")
            .withRequestBody(
                "charges",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "avalara_sale_type",
                            new ArraySchema()
                                .items(
                                    new StringSchema()
                                        ._enum(List.of("retail", "wholesale"))
                                        .extensions(
                                            Map.of(
                                                "x-cb-is-global-enum",
                                                true,
                                                "x-cb-global-enum-reference",
                                                "./enums/AvalaraSalesType.yaml",
                                                "x-cb-is-sub-resource",
                                                true)))))
                    .extensions(Map.of("x-cb-is-composite-array-request-body", true)))
            .withResponse(resourceResponseParam("invoice", invoice))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(invoice)
            .withPostOperation(
                "/invoices/create_for_charge_items_and_charges",
                createForChargeItemsAndChargesOperation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Invoice extends Resource<Invoice> {

    //Constructors
    //============

    public Invoice(String jsonStr) {
        super(jsonStr);
    }

    public Invoice(JSONObject jsonObj) {
        super(jsonObj);
    }

    // Fields
    //=======

    // Operations
    //===========

    public static CreateForChargeItemsAndChargesRequest createForChargeItemsAndCharges() {
        String uri = uri("invoices", "create_for_charge_items_and_charges");
        return new CreateForChargeItemsAndChargesRequest(Method.POST, uri).setIdempotency(false);
    }


    // Operation Request Classes
    //==========================

    public static class CreateForChargeItemsAndChargesRequest extends Request<CreateForChargeItemsAndChargesRequest> {

        private CreateForChargeItemsAndChargesRequest(Method httpMeth, String uri) {
            super(httpMeth, uri);
        }
   \s
        public CreateForChargeItemsAndChargesRequest chargeAvalaraSaleType(int index, com.chargebee.models.enums.AvalaraSaleType chargeAvalaraSaleType) {
            params.addOpt("charges[avalara_sale_type][" + index + "]", chargeAvalaraSaleType);
            return this;
        }
""");
  }

  @Test
  void shouldSupportEnumsInListMultiAttributesInRequestBodyParams() throws IOException {
    var line_item =
        buildResource("line_items")
            .withAttribute(
                "promotional_credits", new IntegerSchema().type("integer").format("int64"))
            .done();
    var invoice =
        buildResource("invoice").withSubResourceArrayAttribute("line_items", line_item).done();
    var importInvoiceOperation =
        buildPostOperation("importInvoice")
            .forResource("invoice")
            .withRequestBody(
                "line_items",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "entity_type",
                            new StringSchema()
                                ._enum(List.of("plan", "plan_setup"))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-composite-array-request-body",
                                        true,
                                        "x-cb-meta-model-name",
                                        "invoice"))))
                    .extensions(
                        Map.of(
                            "x-cb-is-composite-array-request-body",
                            true,
                            "x-cb-meta-model-name",
                            "invoice")))
            .withResponse(resourceResponseParam("invoice", invoice))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();

    var spec =
        buildSpec()
            .withResource(invoice)
            .withPostOperation("/invoices/import_invoice", importInvoiceOperation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Invoice extends Resource<Invoice> {

    public static class LineItem extends Resource<LineItem> {
        public LineItem(JSONObject jsonObj) {
            super(jsonObj);
        }

        public Long promotionalCredits() {
            return optLong("promotional_credits");
        }

    }

    //Constructors
    //============

    public Invoice(String jsonStr) {
        super(jsonStr);
    }

    public Invoice(JSONObject jsonObj) {
        super(jsonObj);
    }

    // Fields
    //=======

    public List<Invoice.LineItem> lineItems() {
        return optList("line_items", Invoice.LineItem.class);
    }

    // Operations
    //===========

    public static ImportInvoiceRequest importInvoice() {
        String uri = uri("invoices", "import_invoice");
        return new ImportInvoiceRequest(Method.POST, uri).setIdempotency(false);
    }


    // Operation Request Classes
    //==========================

    public static class ImportInvoiceRequest extends Request<ImportInvoiceRequest> {

        private ImportInvoiceRequest(Method httpMeth, String uri) {
            super(httpMeth, uri);
        }
   \s
        public ImportInvoiceRequest lineItemEntityType(int index, LineItem.EntityType lineItemEntityType) {
            params.addOpt("line_items[entity_type][" + index + "]", lineItemEntityType);
            return this;
        }
""");
  }

  @Test
  void shouldSupportFilterAttributes() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off"));
    var customer = buildResource("customer").withAttribute("id", true).done();
    var listOperation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam(
                "first_name",
                new ObjectSchema()
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-is-presence-operator-supported",
                            true,
                            "x-cb-sdk-filter-name",
                            "StringFilter",
                            "x-cb-is-sub-resource",
                            true)))
            .withQueryParam(
                "auto_collection",
                new ObjectSchema()
                    .properties(Map.of("is", new StringSchema()._enum(List.of("on", "off"))))
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "EnumFilter",
                            "x-cb-is-sub-resource",
                            true,
                            "x-cb-is-api-column",
                            true,
                            "x-cb-is-global-enum",
                            true,
                            "x-cb-global-enum-reference",
                            "./enums/" + autoCollection.typeName + ".yaml")))
            .withQueryParam(
                "created_at",
                new ObjectSchema()
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "TimestampFilter",
                            "x-cb-is-sub-resource",
                            true)))
            .withQueryParam(
                "auto_close_invoices",
                new ObjectSchema()
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "BooleanFilter",
                            "x-cb-is-sub-resource",
                            true)))
            .withQueryParam(
                "sort_by",
                new ObjectSchema()
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "SortFilter",
                            "x-cb-is-sub-resource",
                            true)))
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", listOperation).done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        "public class Customer extends Resource<Customer> {\n"
            + "\n"
            + "    //Constructors\n"
            + "    //============\n"
            + "\n"
            + "    public Customer(String jsonStr) {\n"
            + "        super(jsonStr);\n"
            + "    }\n"
            + "\n"
            + "    public Customer(JSONObject jsonObj) {\n"
            + "        super(jsonObj);\n"
            + "    }\n"
            + "\n"
            + "    // Fields\n"
            + "    //=======\n"
            + "\n"
            + "    public String id() {\n"
            + "        return reqString(\"id\");\n"
            + "    }\n"
            + "\n"
            + "    // Operations\n"
            + "    //===========\n"
            + "\n"
            + "    public static CustomerListRequest list() {\n"
            + "        String uri = uri(\"customers\");\n"
            + "        return new CustomerListRequest(uri);\n"
            + "    }\n"
            + "\n"
            + "\n"
            + "    // Operation Request Classes\n"
            + "    //==========================\n"
            + "\n"
            + "    public static class CustomerListRequest extends ListRequest<CustomerListRequest>"
            + " {\n"
            + "\n"
            + "        private CustomerListRequest(String uri) {\n"
            + "            super(uri);\n"
            + "        }\n"
            + "    \n"
            + "        public StringFilter<CustomerListRequest> firstName() {\n"
            + "            return new"
            + " StringFilter<CustomerListRequest>(\"first_name\",this).supportsPresenceOperator(true);"
            + "        \n"
            + "        }\n"
            + "\n"
            + "\n"
            + "        public EnumFilter<com.chargebee.models.enums.AutoCollection,"
            + " CustomerListRequest> autoCollection() {\n"
            + "            return new EnumFilter<com.chargebee.models.enums.AutoCollection,"
            + " CustomerListRequest>(\"auto_collection\",this);        \n"
            + "        }\n"
            + "\n"
            + "\n"
            + "        public TimestampFilter<CustomerListRequest> createdAt() {\n"
            + "            return new TimestampFilter<CustomerListRequest>(\"created_at\",this);   "
            + "     \n"
            + "        }\n"
            + "\n"
            + "\n"
            + "        public BooleanFilter<CustomerListRequest> autoCloseInvoices() {\n"
            + "            return new"
            + " BooleanFilter<CustomerListRequest>(\"auto_close_invoices\",this);        \n"
            + "        }\n"
            + "\n"
            + "\n"
            + "        public SortFilter<CustomerListRequest> sortBy() {\n"
            + "            return new SortFilter<CustomerListRequest>(\"sort_by\",this);        \n"
            + "        }\n"
            + "\n"
            + "\n"
            + "        @Override\n"
            + "        public Params params() {\n"
            + "            return params;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "}");
  }

  @Test
  void shouldSupportSchemalessGlobalEnum() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var card = buildResource("card").withAttribute("cvv", false).done();
    var createCustomer =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "card",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "preferred_scheme",
                            new StringSchema()
                                ._enum(List.of("plan", "plan_setup"))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-global-enum",
                                        false,
                                        "x-cb-meta-model-name",
                                        "cards")))))
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();

    var spec =
        buildSpec()
            .withTwoResources(customer, card)
            .withPostOperation("/customer/create", createCustomer)
            .done();
    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        "public class Card extends Resource<Card> {\n"
            + "\n"
            + "    public enum PreferredScheme {\n"
            + "        PLAN,\n"
            + "        PLAN_SETUP,\n"
            + "        _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when"
            + " there is a\n"
            + "        java-client version incompatibility. We suggest you to upgrade to the latest"
            + " version */\n"
            + "    }\n"
            + "\n"
            + "    //Constructors\n"
            + "    //============\n"
            + "\n"
            + "    public Card(String jsonStr) {\n"
            + "        super(jsonStr);\n"
            + "    }\n"
            + "\n"
            + "    public Card(JSONObject jsonObj) {\n"
            + "        super(jsonObj);\n"
            + "    }\n"
            + "\n"
            + "    // Fields\n"
            + "    //=======\n"
            + "\n"
            + "    public String cvv() {\n"
            + "        return optString(\"cvv\");\n"
            + "    }\n"
            + "\n"
            + "    // Operations\n"
            + "    //===========\n"
            + "\n"
            + "\n"
            + "}");
  }

  @Test
  void shouldSupportSchemalessLocalEnum() throws IOException {
    var preferredSchemeEnum =
        buildEnum("AutoCollection", List.of("On", "Off"))
            .asGlobalEnum(false)
            .withMetaModelName("cards")
            .done();
    var card =
        buildResource("card")
            .withAttribute("abc", new IntegerSchema().type("integer").format("int64"))
            .withEnumAttribute(preferredSchemeEnum)
            .done();

    var customer =
        buildResource("customer")
            .withAttribute("id", true)
            .withSubResourceAttribute("card", card)
            .done();
    var createCustomer =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "card",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "preferred_scheme",
                            new StringSchema()
                                ._enum(List.of("plan", "plan_setup"))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-global-enum",
                                        false,
                                        "x-cb-meta-model-name",
                                        "cards")))))
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();

    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customer/create", createCustomer)
            .done();
    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        "public class Customer extends Resource<Customer> {\n"
            + "\n"
            + "    public static class Card extends Resource<Card> {\n"
            + "        public enum PreferredScheme {\n"
            + "            PLAN,PLAN_SETUP,\n"
            + "            _UNKNOWN; /*Indicates unexpected value for this enum. You can get this"
            + " when there is a\n"
            + "            java-client version incompatibility. We suggest you to upgrade to the"
            + " latest version */ \n"
            + "        }\n"
            + "\n"
            + "        public Card(JSONObject jsonObj) {\n"
            + "            super(jsonObj);\n"
            + "        }\n"
            + "\n"
            + "        public Long abc() {\n"
            + "            return optLong(\"abc\");\n"
            + "        }\n"
            + "\n"
            + "        public  autocollection() {\n"
            + "            return optEnum(\"AutoCollection\", null.class);\n"
            + "        }\n"
            + "\n"
            + "    }\n"
            + "\n"
            + "    //Constructors\n"
            + "    //============\n"
            + "\n"
            + "    public Customer(String jsonStr) {\n"
            + "        super(jsonStr);\n"
            + "    }\n"
            + "\n"
            + "    public Customer(JSONObject jsonObj) {\n"
            + "        super(jsonObj);\n"
            + "    }\n"
            + "\n"
            + "    // Fields\n"
            + "    //=======\n"
            + "\n"
            + "    public String id() {\n"
            + "        return reqString(\"id\");\n"
            + "    }\n"
            + "\n"
            + "    public Customer.Card card() {\n"
            + "        return optSubResource(\"card\", Customer.Card.class);\n"
            + "    }\n"
            + "\n"
            + "    // Operations\n"
            + "    //===========\n"
            + "\n"
            + "    public static Request create() {\n"
            + "        String uri = uri(\"customer\", \"create\");\n"
            + "        return new Request(Method.POST, uri).setIdempotency(false);\n"
            + "    }\n"
            + "\n"
            + "\n"
            + "    // Operation Request Classes\n"
            + "    //==========================\n"
            + "\n"
            + "    public static class CreateRequest extends Request<CreateRequest> {\n"
            + "\n"
            + "        private CreateRequest(Method httpMeth, String uri) {\n"
            + "            super(httpMeth, uri);\n"
            + "        }\n"
            + "    \n"
            + "        public CreateRequest card(StringFilter card) {\n"
            + "            params.addOpt(\"card\", card);\n"
            + "            return this;\n"
            + "        }\n"
            + "\n"
            + "\n"
            + "        public CreateRequest cardPreferredScheme(Card.PreferredScheme"
            + " cardPreferredScheme) {\n"
            + "            params.addOpt(\"card[preferred_scheme]\", cardPreferredScheme);\n"
            + "            return this;\n"
            + "        }\n"
            + "\n"
            + "        @Override\n"
            + "        public Params params() {\n"
            + "            return params;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "}");
  }

  @Test
  void shouldGenerateSubDomainWhenGivenInSpec() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createCustomer =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("card", new ObjectSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .withSubDomain("test-domain")
            .asInputObjNeeded()
            .done();
    var listCustomer =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("paid_on_after")
            .withResponse(resourceResponseParam("customer", customer))
            .withSubDomain("test-domain")
            .done();

    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customer/create", createCustomer)
            .withOperation("/customer/list", listCustomer)
            .done();
    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .contains("super(httpMeth, uri, null, \"test-domain\")")
        .contains("super(uri, \"test-domain\")");
  }

  @Test
  void shouldGenerateContentTypeJsonSupportedObject() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createCustomer =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("first_name", new StringSchema())
            .withRequestBody("last_name", new StringSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .withSubDomain("test-domain")
            .isContentTypeJson(true)
            .asInputObjNeeded()
            .done();

    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customer/create", createCustomer)
            .done();
    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .contains("super(httpMeth, uri, null, \"test-domain\",true)");
  }

  @Test
  void shouldSupportBatchWithContentTypeJsonRequest() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createCustomer =
        buildPostOperation("create")
            .forResource("customer")
            .withCompositeArrayRequestBody(
                "events",
                new ObjectSchema()
                    .properties(
                        Map.of("first_name", new StringSchema(), "last_name", new StringSchema())))
            .withResponse(resourceResponseParam("customer", customer))
            .withSubDomain("test-domain")
            .isContentTypeJson(true)
            .isBatch(true)
            .asInputObjNeeded()
            .done();

    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customer/create", createCustomer)
            .done();
    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .contains(
            "public CreateRequest events(List<EventCreateInputParams > array) {\n"
                + "            JSONArray jarray = new JSONArray();\n"
                + "            for (EventCreateInputParams item : array) {\n"
                + "                jarray.put(item.toJObject());\n"
                + "            }\n"
                + "            params.add(\"events\", jarray);\n"
                + "            return this;}\n"
                + "        \n"
                + "        @Override\n"
                + "        public Params params() {\n"
                + "            return params;\n"
                + "        }\n"
                + "    }")
        .contains("EventCreateInputParams")
        .contains("EventCreateInputParamsBuilder");
  }

  @Test
  void shouldSupportBatchWithRegularFlow() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createCustomer =
        buildPostOperation("batch")
            .forResource("customer")
            .withCompositeArrayRequestBody(
                "events",
                new ObjectSchema()
                    .properties(
                        Map.of("first_name", new StringSchema(), "last_name", new StringSchema())))
            .withResponse(resourceResponseParam("customer", customer))
            .withSubDomain("test-domain")
            .isBatch(true)
            .asInputObjNeeded()
            .done();

    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customer/create", createCustomer)
            .done();
    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .contains(
            "paramsV2.addOpt(new CompositeArrayParameter(\"events\", \"last_name\", index),"
                + " eventLastName);")
        .contains("public ParamsV2 paramsV2() {");
  }

  @Test
  void objectWithContentTypeJsonShouldBeConvertedToMap() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createCustomer =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("first_name", new StringSchema())
            .withRequestBody(
                "properties", new ObjectSchema().type("object").additionalProperties(true), false)
            .withResponse(resourceResponseParam("customer", customer))
            .withSubDomain("test-domain")
            .isContentTypeJson(true)
            .asInputObjNeeded()
            .done();

    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customer/create", createCustomer)
            .done();
    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .contains("public CreateRequest properties(Map<String, Object> properties)");
  }

  @Test
  void shouldSupportSchemaLessEnumAtTopLevel() throws IOException {
    var customer = buildResource("customer").done();
    var ChangeBillingDateOperation =
        buildPostOperation("changeBillingDate")
            .forResource("customer")
            .withRequestBody(
                "billing_day_of_week", new StringSchema()._enum(List.of("sunday", "monday")))
            .withPathParam("customer-id")
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation(
                "/customers/{customer-id}/change_billing_date", ChangeBillingDateOperation)
            .done();

    List<FileOp> fileOps = javaSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "imports.txt",
        """
public class Customer extends Resource<Customer> {

    public enum BillingDayOfWeek {
        SUNDAY,
        MONDAY,
        _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
        java-client version incompatibility. We suggest you to upgrade to the latest version */
    }

    //Constructors
    //============

    public Customer(String jsonStr) {
        super(jsonStr);
    }

    public Customer(JSONObject jsonObj) {
        super(jsonObj);
    }

    // Fields
    //=======

    // Operations
    //===========

    public static ChangeBillingDateRequest changeBillingDate(String id) {
        String uri = uri("customers", nullCheck(id), "change_billing_date");
        return new ChangeBillingDateRequest(Method.POST, uri).setIdempotency(false);
    }


    // Operation Request Classes
    //==========================

    public static class ChangeBillingDateRequest extends Request<ChangeBillingDateRequest> {

        private ChangeBillingDateRequest(Method httpMeth, String uri) {
            super(httpMeth, uri);
        }
   \s
        public ChangeBillingDateRequest billingDayOfWeek(BillingDayOfWeek billingDayOfWeek) {
            params.addOpt("billing_day_of_week", billingDayOfWeek);
            return this;
        }


        @Override
        public Params params() {
            return params;
        }
    }

}""");
  }
}
