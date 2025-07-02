package com.chargebee.sdk.internal_jar;

import static com.chargebee.sdk.test_data.EnumBuilder.buildEnum;
import static com.chargebee.sdk.test_data.OperationBuilder.*;
import static com.chargebee.sdk.test_data.ResourceBuilder.buildResource;
import static com.chargebee.sdk.test_data.ResourceResponseParam.resourceResponseParam;
import static com.chargebee.sdk.test_data.SpecBuilder.buildSpec;
import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.openapi.ApiVersion;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.LanguageTests;
import com.chargebee.sdk.java.GenerationMode;
import com.chargebee.sdk.java.JarType;
import com.chargebee.sdk.java.Java;
import com.chargebee.sdk.test_data.OperationWithPath;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class IntJarTests extends LanguageTests {
  public static Java javaInternalIntApiV1, javaInternalIntApiV2;
  private final String basePath = "/java/src/main/java/com/chargebee";
  private final String basePathForApiV2 = "/java/src/main/java/com/chargebee/v2";

  @BeforeAll
  static void beforeAll() {
    javaInternalIntApiV1 = new Java(GenerationMode.INTERNAL, ApiVersion.V1, JarType.INT);
    javaInternalIntApiV2 = new Java(GenerationMode.INTERNAL, ApiVersion.V2, JarType.INT);
  }

  void assertJavaEnumFileContent(FileOp.WriteString fileOp, String body, ApiVersion apiVersion) {
    String apiV2 = apiVersion.equals(ApiVersion.V2) ? "v2." : "";
    assertThat(fileOp.fileContent)
        .startsWith("package com.chargebee." + apiV2 + "models.enums;\n" + "\n" + body);
  }

  void assertJavaModelFileContent(FileOp.WriteString fileOp, String fileName, String body)
      throws IOException {
    String expectedContent =
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/internal_jar/samples/" + fileName)
            .replace("__body__", body);
    assertThat(fileOp.fileContent).startsWith(expectedContent);
  }

  void assertJavaResultBaseFileContent(FileOp.WriteString fileOp, String fileName, String body)
      throws IOException {
    assertThat(fileOp.fileContent)
        .startsWith(
            FileOp.fetchFileContent(
                    "src/test/java/com/chargebee/sdk/internal_jar/samples/" + fileName)
                .replace("__body__", body));
  }

  @Test
  void baseDirectoryShouldNotBeClearedBeforeGenerationForApiV1IntJar() {
    assertThat(javaInternalIntApiV1.cleanDirectoryBeforeGenerate()).isFalse();
  }

  @Test
  void shouldCreateModelsDirectoryForApiV1Models() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(0), basePath, "/models");
  }

  @Test
  void shouldCreateEnumsDirectoryInsideModelsDirectoryForApiV1Models() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(1), basePath + "/models", "/enums");
  }

  @Test
  void shouldCreateGlobalEnumFilesInEnumsDirectoryForApiV1Models() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), basePath + "/models/enums", "AutoCollection.java");
  }

  @Test
  void shouldCreateGlobalEnumFilesInEnumsDirectoryWithSpecifiedImportStatementsForApiV1Models()
      throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("on", "off")).done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaEnumFileContent(
        writeStringFileOp,
        """
public enum AutoCollection {
    ON,
    OFF,
    _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
    java-client version incompatibility. We suggest you to upgrade to the latest version */
}""",
        ApiVersion.V1);
  }

  @Test
  void shouldIncludeDeprecatedIfParameterBlankOptionExtensionIsSetForApiV1Models()
      throws IOException {
    var paymentMethod =
        buildEnum(
                "PaymentMethod", List.of("card", "app_store", "play_store", "check", "chargeback"))
            .withDeprecatedValues(List.of("app_store", "play_store"))
            .withParameterBlankOption()
            .done();
    var spec = buildSpec().withEnums(paymentMethod).done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaEnumFileContent(
        writeStringFileOp,
        """
public enum PaymentMethod {
    @Deprecated
    APP_STORE,
    @Deprecated
    PLAY_STORE,
    CARD,
    CHECK,
    CHARGEBACK,
    _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
    java-client version incompatibility. We suggest you to upgrade to the latest version */
}""",
        ApiVersion.V1);
  }

  @Test
  void shouldCreateResourceFileInModelsDirectoryForApiV1Models() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), basePath + "/models", "Customer.java");
  }

  @Test
  void eachResourceShouldHaveSeparateDeclarationForApiV1Models() throws IOException {
    var spec = buildSpec().withResources("customer", "advance_invoice_schedule").done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), basePath + "/models", "AdvanceInvoiceSchedule.java");
    assertWriteStringFileOp(fileOps.get(3), basePath + "/models", "Customer.java");
  }

  @Test
  void eachResourceShouldHaveImportStatementsForApiV1Models() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(writeStringFileOp, "import_api_v1.txt", "");
  }

  @Test
  void shouldNotHaveFilterImportStatementsForApiV1Models() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(writeStringFileOp, "import_api_v1.txt", "");
  }

  @Test
  void shouldIncludeHiddenFromSDKResourcesForApiV1Models() throws IOException {
    var customer = buildResource("customer").done();
    var advance_invoice_schedule =
        buildResource("advance_invoice_schedule").asHiddenFromSDKGeneration().done();
    var spec = buildSpec().withResources(customer, advance_invoice_schedule).done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "import_api_v1.txt",
        "public class AdvanceInvoiceSchedule extends Resource<AdvanceInvoiceSchedule> {");
  }

  @Test
  void shouldIncludeBulkOperationsForApiV1Models() throws IOException {
    var token = buildResource("token").withAttribute("id", true).done();
    var createForCardOperation =
        buildPostOperation("createForCard")
            .forResource("token")
            .withResponse(resourceResponseParam("token", token))
            .asBulkOperationFromSDKGeneration()
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .withSortOrder(0)
            .done();
    var spec =
        buildSpec()
            .withResource(token)
            .withPostOperation("/tokens/create_for_card", createForCardOperation)
            .done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "import_api_v1.txt",
        """
        public class Token extends Resource<Token> {

            //Constructors
            //============

            public Token(String jsonStr) {
                super(jsonStr);
            }

            public Token(JSONObject jsonObj) {
                super(jsonObj);
            }

            // Fields
            //=======

            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static CreateForCardRequest createForCard() {
                String uri = uri("tokens", "create_for_card");
                return new CreateForCardRequest(Method.POST, uri);
            }""");
  }

  @Test
  void shouldIgnoreHiddenFromSDKActionsForApiV1Models() throws IOException {
    var token = buildResource("token").withAttribute("id", true).done();
    var createForCardOperation =
        buildPostOperation("createForCard")
            .forResource("token")
            .withResponse(resourceResponseParam("token", token))
            .asHiddenFromSDKGeneration()
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .withSortOrder(0)
            .done();
    var spec =
        buildSpec()
            .withResource(token)
            .withPostOperation("/tokens/create_for_card", createForCardOperation)
            .done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "import_api_v1.txt",
        """
        public class Token extends Resource<Token> {

            //Constructors
            //============

            public Token(String jsonStr) {
                super(jsonStr);
            }

            public Token(JSONObject jsonObj) {
                super(jsonObj);
            }

            // Fields
            //=======

            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static CreateForCardRequest createForCard() {
                String uri = uri("tokens", "create_for_card");
                return new CreateForCardRequest(Method.POST, uri);
            }""");
  }

  @Test
  void shouldCreateResultBaseFileInInternalDirectoryForApiV1Models() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), basePath + "/internal", "ResultBase.java");
  }

  @Test
  void resultBaseFileShouldHaveImportStatementsAndResultBaseClassForApiV1Models()
      throws IOException {
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

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        "resultBase_api_v1.txt",
        """
            public Customer customer() {
                return (Customer)get("customer");
            }\
        """);
  }

  @Test
  void resultBaseFileForApiV1ShouldNotHaveClassDefnsForListResponses() throws IOException {
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

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        "resultBase_api_v1.txt",
        """
            public Invoice invoice() {
                return (Invoice)get("invoice");
            }

            public UnbilledCharge unbilledCharge() {
                return (UnbilledCharge)get("unbilled_charge");
            }\
        """);
  }

  @Test
  void shouldIncludeHiddenFromSDKResourcesInResultBaseForApiV1Models() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var token = buildResource("token").withAttribute("id", true).done();
    var contact =
        buildResource("contact").withAttribute("id", true).asHiddenFromSDKGeneration().done();
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

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        "resultBase_api_v1.txt",
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
  void shouldIncludeThirdPartyResourceForApiV1Models() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var token = buildResource("token").withAttribute("id", true).done();
    var ThirdPartySyncDetail =
        buildResource("third_party_sync_details")
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
            .withResources(customer, token, ThirdPartySyncDetail)
            .withOperations(
                new OperationWithPath("/customers/{customer-id}", retrieveOperation),
                new OperationWithPath(
                    "/third_party_sync_details/retrieve_latest_sync", retrieveLatestSyncOperation))
            .done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        "resultBase_api_v1.txt",
        """
            public Customer customer() {
                return (Customer)get("customer");
            }

            public ThirdPartySyncDetails thirdPartySyncDetails() {
                return (ThirdPartySyncDetails)get("third_party_sync_details");
            }

            public Token token() {
                return (Token)get("token");
            }\
        """);
  }

  @Test
  void baseDirectoryShouldNotBeClearedBeforeGenerationForApiV2IntJar() {
    assertThat(javaInternalIntApiV2.cleanDirectoryBeforeGenerate()).isFalse();
  }

  @Test
  void shouldCreateModelsDirectoryForApiV2Models() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    assertCreateDirectoryFileOp(fileOps.get(0), basePathForApiV2, "/models");
  }

  @Test
  void shouldCreateEnumsDirectoryInsideModelsDirectoryForApiV2Models() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    assertCreateDirectoryFileOp(fileOps.get(1), basePathForApiV2 + "/models", "/enums");
  }

  @Test
  void shouldCreateGlobalEnumFilesInEnumsDirectoryForApiV2Models() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    assertWriteStringFileOp(
        fileOps.get(2), basePathForApiV2 + "/models/enums", "AutoCollection.java");
  }

  @Test
  void shouldCreateGlobalEnumFilesInEnumsDirectoryWithSpecifiedImportStatementsForApiV2Models()
      throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("on", "off")).done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaEnumFileContent(
        writeStringFileOp,
        """
public enum AutoCollection {
    ON,
    OFF,
    _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
    java-client version incompatibility. We suggest you to upgrade to the latest version */
}""",
        ApiVersion.V2);
  }

  @Test
  void shouldIncludeDeprecatedIfParameterBlankOptionExtensionIsSetForApiV2Models()
      throws IOException {
    var paymentMethod =
        buildEnum(
                "PaymentMethod", List.of("card", "app_store", "play_store", "check", "chargeback"))
            .withDeprecatedValues(List.of("app_store", "play_store"))
            .withParameterBlankOption()
            .done();
    var spec = buildSpec().withEnums(paymentMethod).done();

    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaEnumFileContent(
        writeStringFileOp,
        """
public enum PaymentMethod {
    @Deprecated
    APP_STORE,
    @Deprecated
    PLAY_STORE,
    CARD,
    CHECK,
    CHARGEBACK,
    _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
    java-client version incompatibility. We suggest you to upgrade to the latest version */
}""",
        ApiVersion.V1);
  }

  @Test
  void shouldCreateResourceFileInModelsDirectoryForApiV2Models() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    assertWriteStringFileOp(fileOps.get(2), basePathForApiV2 + "/models", "Customer.java");
  }

  @Test
  void eachResourceShouldHaveSeparateDeclarationForApiV2Models() throws IOException {
    var spec = buildSpec().withResources("customer", "advance_invoice_schedule").done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    assertWriteStringFileOp(
        fileOps.get(2), basePathForApiV2 + "/models", "AdvanceInvoiceSchedule.java");
    assertWriteStringFileOp(fileOps.get(3), basePathForApiV2 + "/models", "Customer.java");
  }

  @Test
  void eachResourceShouldHaveImportStatementsForApiV2Models() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(writeStringFileOp, "import_api_v2.txt", "");
  }

  @Test
  void shouldHaveFilterImportStatementsForApiV2Models() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(writeStringFileOp, "import_api_v2.txt", "");
  }

  @Test
  void shouldIncludeHiddenFromSDKResourcesForApiV2Models() throws IOException {
    var customer = buildResource("customer").done();
    var advance_invoice_schedule =
        buildResource("advance_invoice_schedule").asHiddenFromSDKGeneration().done();
    var spec = buildSpec().withResources(customer, advance_invoice_schedule).done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "import_api_v2.txt",
        "public class AdvanceInvoiceSchedule extends Resource<AdvanceInvoiceSchedule> {");
  }

  @Test
  void shouldIncludeBulkOperationsForApiV2Models() throws IOException {
    var token = buildResource("token").withAttribute("id", true).done();
    var createForCardOperation =
        buildPostOperation("createForCard")
            .forResource("token")
            .withResponse(resourceResponseParam("token", token))
            .asBulkOperationFromSDKGeneration()
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .withSortOrder(0)
            .done();
    var spec =
        buildSpec()
            .withResource(token)
            .withPostOperation("/tokens/create_for_card", createForCardOperation)
            .done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "import_api_v2.txt",
        """
        public class Token extends Resource<Token> {

            //Constructors
            //============

            public Token(String jsonStr) {
                super(jsonStr);
            }

            public Token(JSONObject jsonObj) {
                super(jsonObj);
            }

            // Fields
            //=======

            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static CreateForCardRequest createForCard() {
                String uri = uri("tokens", "create_for_card");
                return new CreateForCardRequest(Method.POST, uri);
            }""");
  }

  @Test
  void shouldIgnoreHiddenFromSDKActionsForApiV2Models() throws IOException {
    var token = buildResource("token").withAttribute("id", true).done();
    var createForCardOperation =
        buildPostOperation("createForCard")
            .forResource("token")
            .withResponse(resourceResponseParam("token", token))
            .asHiddenFromSDKGeneration()
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .withSortOrder(0)
            .done();
    var spec =
        buildSpec()
            .withResource(token)
            .withPostOperation("/tokens/create_for_card", createForCardOperation)
            .done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "import_api_v2.txt",
        """
        public class Token extends Resource<Token> {

            //Constructors
            //============

            public Token(String jsonStr) {
                super(jsonStr);
            }

            public Token(JSONObject jsonObj) {
                super(jsonObj);
            }

            // Fields
            //=======

            public String id() {
                return reqString("id");
            }

            // Operations
            //===========

            public static CreateForCardRequest createForCard() {
                String uri = uri("tokens", "create_for_card");
                return new CreateForCardRequest(Method.POST, uri);
            }""");
  }

  @Test
  void shouldCreateResultBaseFileInInternalDirectoryForApiV2Models() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    assertWriteStringFileOp(fileOps.get(2), basePathForApiV2 + "/internal", "ResultBase.java");
  }

  @Test
  void resultBaseFileShouldHaveImportStatementsAndResultBaseClassForApiV2Models()
      throws IOException {
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

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        "resultBase_api_v2.txt",
        """
            public Customer customer() {
                return (Customer)get("customer");
            }\
        """);
  }

  @Test
  void resultBaseFileForApiV2ShouldHaveClassDefnsForListResponses() throws IOException {
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

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        "resultBase_api_v2.txt",
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
  void shouldIncludeHiddenFromSDKResourcesInResultBaseForApiV2Models() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var token = buildResource("token").withAttribute("id", true).done();
    var contact =
        buildResource("contact").withAttribute("id", true).asHiddenFromSDKGeneration().done();
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

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        "resultBase_api_v2.txt",
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
  void shouldIncludeThirdPartyResourceForApiV2Models() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var token = buildResource("token").withAttribute("id", true).done();
    var ThirdPartySyncDetail =
        buildResource("third_party_sync_details")
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
            .withResources(customer, token, ThirdPartySyncDetail)
            .withOperations(
                new OperationWithPath("/customers/{customer-id}", retrieveOperation),
                new OperationWithPath(
                    "/third_party_sync_details/retrieve_latest_sync", retrieveLatestSyncOperation))
            .done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertJavaResultBaseFileContent(
        writeStringFileOp,
        "resultBase_api_v2.txt",
        """
            public Customer customer() {
                return (Customer)get("customer");
            }

            public ThirdPartySyncDetails thirdPartySyncDetails() {
                return (ThirdPartySyncDetails)get("third_party_sync_details");
            }

            public Token token() {
                return (Token)get("token");
            }\
        """);
  }

  @Test
  void sessionResourceShouldNotHaveSpecifiedSnippetIncludedForQaJar() throws IOException {
    var session = buildResource("session").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("export")
            .withPathParam("session-id")
            .withResponse(resourceResponseParam("session", session))
            .done();
    var spec =
        buildSpec().withResource(session).withOperation("/session/{session-id}", operation).done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "import_api_v2.txt",
        """
        public class Session extends Resource<Session> {

            //Constructors
            //============

            public Session(String jsonStr) {
                super(jsonStr);
            }

            public Session(JSONObject jsonObj) {
                super(jsonObj);
            }

            // Fields
            //=======

            public String id() {
                return reqString("id");
            }

            // Operations
            //===========


        }
        """);
  }

  @Test
  void shouldSupportParameterBlankOptionAsEmptyInListMultiAttributesInRequestBodyParams()
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

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "import_api_v2.txt",
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
        return new UpdateSubscriptionRequest(Method.POST, uri);
    }


    // Operation Request Classes
    //==========================

    public static class UpdateSubscriptionRequest extends Request<UpdateSubscriptionRequest> {

        private UpdateSubscriptionRequest(Method httpMeth, String uri) {
            super(httpMeth, uri);
        }
   \s
        public UpdateSubscriptionRequest eventBasedAddons(String eventBasedAddons) {
            params.addOpt("event_based_addons", eventBasedAddons);
            return this;
        }


        public UpdateSubscriptionRequest eventBasedAddonId(int index, String eventBasedAddonId) {
            params.addOpt("event_based_addons[id][" + index + "]", eventBasedAddonId);
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
  void shouldHaveIntegerDataTypeForMoneyColumnAndIntJar() throws IOException {
    var addon =
        buildResource("addon")
            .withAttribute(
                "price",
                new IntegerSchema()
                    .type("integer")
                    .format("int64")
                    .extensions(Map.of("x-cb-is-money-column", true)))
            .done();

    var spec = buildSpec().withResource(addon).done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "import_api_v2.txt",
        """
        public class Addon extends Resource<Addon> {

            //Constructors
            //============

            public Addon(String jsonStr) {
                super(jsonStr);
            }

            public Addon(JSONObject jsonObj) {
                super(jsonObj);
            }

            // Fields
            //=======

            public Integer price() {
                return optInteger("price");
            }

        """);
  }

  @Test
  void shouldHaveLongDataTypeForLongMoneyColumnAndIntJar() throws IOException {
    var addon =
        buildResource("contract_term")
            .withAttribute(
                "total_contract_value",
                new IntegerSchema()
                    .type("integer")
                    .format("int64")
                    .extensions(
                        Map.of("x-cb-is-money-column", true, "x-cb-is-long-money-column", true)),
                true)
            .done();

    var spec = buildSpec().withResource(addon).done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "import_api_v2.txt",
        """
        public class ContractTerm extends Resource<ContractTerm> {

            //Constructors
            //============

            public ContractTerm(String jsonStr) {
                super(jsonStr);
            }

            public ContractTerm(JSONObject jsonObj) {
                super(jsonObj);
            }

            // Fields
            //=======

            public Long totalContractValue() {
                return reqLong("total_contract_value");
            }

        """);
  }

  @Test
  void shouldCreateSpecifiedEnumsForEntityTypeGlobalEnumWithIntJarAndApiVersionV1()
      throws IOException {
    var entityType =
        buildEnum("EntityType", new ArrayList<>(List.of("customer", "subscription")))
            .asGenSeparate()
            .asApi()
            .done();
    var spec = buildSpec().withEnums(entityType).done();

    System.out.println(spec);
    List<FileOp> fileOps = javaInternalIntApiV1.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaEnumFileContent(
        writeStringFileOp,
        """
        public enum EntityType {
            CUSTOMER,
            SUBSCRIPTION,
            INVOICE,
            QUOTE,
            CREDIT_NOTE,
            _UNKNOWN; /*Indicates unexpected value for this enum. You can get this when there is a
            java-client version incompatibility. We suggest you to upgrade to the latest version */
        }""",
        ApiVersion.V1);
  }

  @Test
  void batchOperationShouldHaveNullCheckWithoutEncodingInReturnStatement() throws IOException {
    var ramp = buildResource("ramp").withAttribute("id", true).done();
    var updateBillingInfoRequest =
        buildPostOperation("createForSubscription")
            .forResource("ramp")
            .withPathParam("subscription-id")
            .withRequestBody("vat_number", new StringSchema(), false)
            .withResponse(resourceResponseParam("ramp", ramp))
            .asBatch()
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(ramp)
            .withPostOperation(
                "/subscriptions/{subscription-id}/create_ramp", updateBillingInfoRequest)
            .done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "import_api_v2.txt",
        """
public class Ramp extends Resource<Ramp> {

    //Constructors
    //============

    public Ramp(String jsonStr) {
        super(jsonStr);
    }

    public Ramp(JSONObject jsonObj) {
        super(jsonObj);
    }

    // Fields
    //=======

    public String id() {
        return reqString("id");
    }

    // Operations
    //===========

    public static CreateForSubscriptionRequest createForSubscription(String id) {
        String uri = uri("subscriptions", nullCheck(id), "create_ramp");
        return new CreateForSubscriptionRequest(Method.POST, uri, nullCheckWithoutEncoding(id));
    }""");
  }

  @Test
  void shouldSupportEnumFilterAttributesWithSubResourceTrue() throws IOException {
    var customer = buildResource("item_price").withAttribute("id", true).done();
    var listOperation =
        buildListOperation("listInternal")
            .forResource("item_price")
            .withQueryParam(
                "items",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "item_grouping_category",
                            new ObjectSchema()
                                .properties(
                                    Map.of("is", new StringSchema()._enum(List.of("on", "off"))))
                                .extensions(
                                    Map.of(
                                        "x-cb-parameter-blank-option", "as_null",
                                        "x-cb-is-sub-resource", true,
                                        "x-cb-is-filter-parameter", true,
                                        "x-cb-meta-model-name", "items",
                                        "x-cb-is-api-column", true,
                                        "x-cb-sdk-filter-name", "EnumFilter",
                                        "x-cb-is-multi-value-attribute", false)))))
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .withQueryParam("paid_on_after")
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/item_prices/list_internal", listOperation)
            .done();

    List<FileOp> fileOps = javaInternalIntApiV2.generate(basePathForApiV2, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertJavaModelFileContent(
        writeStringFileOp,
        "import_api_v2.txt",
        "public class ItemPrice extends Resource<ItemPrice> {\n"
            + "\n"
            + "    //Constructors\n"
            + "    //============\n"
            + "\n"
            + "    public ItemPrice(String jsonStr) {\n"
            + "        super(jsonStr);\n"
            + "    }\n"
            + "\n"
            + "    public ItemPrice(JSONObject jsonObj) {\n"
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
            + "    public static ItemPriceListInternalRequest listInternal() {\n"
            + "        String uri = uri(\"item_prices\", \"list_internal\");\n"
            + "        return new ItemPriceListInternalRequest(uri);\n"
            + "    }\n"
            + "\n"
            + "\n"
            + "    // Operation Request Classes\n"
            + "    //==========================\n"
            + "\n"
            + "    public static class ItemPriceListInternalRequest extends"
            + " ListRequest<ItemPriceListInternalRequest> {\n"
            + "\n"
            + "        private ItemPriceListInternalRequest(String uri) {\n"
            + "            super(uri);\n"
            + "        }\n"
            + "    \n"
            + "        public EnumFilter<Item.ItemGroupingCategory, ItemPriceListInternalRequest>"
            + " itemsItemGroupingCategory() {\n"
            + "            return new EnumFilter<Item.ItemGroupingCategory,"
            + " ItemPriceListInternalRequest>(\"items[item_grouping_category]\",this);        \n"
            + "        }");
  }
}
