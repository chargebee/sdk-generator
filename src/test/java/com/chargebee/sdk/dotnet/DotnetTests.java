package com.chargebee.sdk.dotnet;

import static com.chargebee.sdk.test_data.EnumBuilder.buildEnum;
import static com.chargebee.sdk.test_data.OperationBuilder.*;
import static com.chargebee.sdk.test_data.ResourceBuilder.buildResource;
import static com.chargebee.sdk.test_data.ResourceResponseParam.resourceResponseParam;
import static com.chargebee.sdk.test_data.SpecBuilder.buildSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.LanguageTests;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DotnetTests extends LanguageTests {
  public static Dotnet dotnetSdkGen;
  private final String basePath = "/dotnet/ChargeBee";
  private final String modelsDirectoryPath = "/dotnet/ChargeBee/Models";
  private final String enumsDirectoryPath = "/dotnet/ChargeBee/Models/Enums";
  private final String internalDirectoryPath = "/dotnet/ChargeBee/Internal";

  @BeforeAll
  static void beforeAll() {
    dotnetSdkGen = new Dotnet();
  }

  void assertDotnetEnumFileContent(FileOp.WriteString fileOp, String body) {
    assertThat(fileOp.fileContent)
        .startsWith(
            "using System.ComponentModel;\n"
                + "using System.Runtime.Serialization;\n"
                + "\n"
                + "namespace ChargeBee.Models.Enums\n"
                + "{"
                + "\n"
                + body);
  }

  void assertDotnetModelFileContent(FileOp.WriteString fileOp, String body) throws IOException {
    String imports =
        fileOp.fileName.equals("TimeMachine.cs") || fileOp.fileName.equals("Export.cs")
            ? """

using System.Threading.Tasks;
using System.Net;"""
            : "";
    String constructors =
        FileOp.fetchFileContent(
                "src/test/java/com/chargebee/sdk/dotnet/samples/customerConstructors.txt")
            .replace("__delimiter__", "");
    assertThat(fileOp.fileContent)
        .startsWith(
            FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/dotnet/samples/imports.txt")
                + imports
                + "\n"
                + "\n"
                + constructors
                + "\n"
                + "\n"
                + body);
  }

  void assertDotnetModelFileContent(FileOp.WriteString fileOp, String constructors, String body)
      throws IOException {
    String imports =
        fileOp.fileName.equals("TimeMachine.cs") || fileOp.fileName.equals("Export.cs")
            ? """

using System.Threading.Tasks;
using System.Net;"""
            : "";
    assertThat(fileOp.fileContent)
        .startsWith(
            FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/dotnet/samples/imports.txt")
                + imports
                + "\n"
                + "\n"
                + constructors
                + "\n"
                + "\n"
                + body);
  }

  void assertDotnetResultFileContents(FileOp.WriteString fileOp, String body) throws IOException {
    String expectedContent =
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/dotnet/samples/resultTemplate.txt")
            .replace("__body__", body);
    assertThat(fileOp.fileContent).containsIgnoringNewLines(expectedContent);
  }

  @Test
  void baseDirectoryShouldNotBeClearedBeforeGeneration() {
    assertThat(dotnetSdkGen.cleanDirectoryBeforeGenerate()).isFalse();
  }

  @Test
  void shouldCreateModelsDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(0), basePath, "/Models");
  }

  @Test
  void shouldCreateEnumsDirectoryInsideModelsDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(1), modelsDirectoryPath, "/Enums");
  }

  @Test
  void shouldCreateGlobalEnumFilesInEnumsDirectory() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), enumsDirectoryPath, "AutoCollectionEnum.cs");
  }

  @Test
  void globalEnumFilesInEnumsDirectoryShouldBeSortedAlphabetically() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var channel = buildEnum("Channel", List.of("app_store", "web", "play_store")).done();
    var spec = buildSpec().withEnums(channel, autoCollection).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), enumsDirectoryPath, "AutoCollectionEnum.cs");
    assertWriteStringFileOp(fileOps.get(3), enumsDirectoryPath, "ChannelEnum.cs");
  }

  @Test
  void shouldHaveUnknownAsDefaultPossibleValue() throws IOException {
    var taxjarExemptionCategory = buildEnum("TaxjarExemptionCategory", List.of()).done();
    var spec = buildSpec().withEnums(taxjarExemptionCategory).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetEnumFileContent(
        writeStringFileOp,
        """
    public enum TaxjarExemptionCategoryEnum
    {

        [EnumMember(Value = "Unknown Enum")]
        UnKnown, /*Indicates unexpected value for this enum. You can get this when there is a
                dotnet-client version incompatibility. We suggest you to upgrade to the latest version */

    }\
""");
  }

  @Test
  void shouldCreateGlobalEnumFilesInEnumsDirectoryWithSpecifiedGlobalEnums() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("on", "off")).done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetEnumFileContent(
        writeStringFileOp,
        """
    public enum AutoCollectionEnum
    {

        [EnumMember(Value = "Unknown Enum")]
        UnKnown, /*Indicates unexpected value for this enum. You can get this when there is a
                dotnet-client version incompatibility. We suggest you to upgrade to the latest version */

        [EnumMember(Value = "on")]
         On,

        [EnumMember(Value = "off")]
         Off,

    }\
""");
  }

  @Test
  void possibleValuesShouldBeInPascalCase() throws IOException {
    var applyOn = buildEnum("ApplyOn", List.of("invoice_amount", "specific_item_price")).done();
    var spec = buildSpec().withEnums(applyOn).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetEnumFileContent(
        writeStringFileOp,
        """
    public enum ApplyOnEnum
    {

        [EnumMember(Value = "Unknown Enum")]
        UnKnown, /*Indicates unexpected value for this enum. You can get this when there is a
                dotnet-client version incompatibility. We suggest you to upgrade to the latest version */

        [EnumMember(Value = "invoice_amount")]
         InvoiceAmount,

        [EnumMember(Value = "specific_item_price")]
         SpecificItemPrice,

    }\
""");
  }

  @Test
  void shouldCreateResourceFileInModelsDirectory() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), modelsDirectoryPath, "Customer.cs");
  }

  @Test
  void eachResourceShouldHaveSeparateDeclaration() throws IOException {
    var spec = buildSpec().withResources("customer", "advance_invoice_schedule").done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), modelsDirectoryPath, "AdvanceInvoiceSchedule.cs");
    assertWriteStringFileOp(fileOps.get(3), modelsDirectoryPath, "Customer.cs");
  }

  @Test
  void eachResourceShouldHaveImportStatements() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(writeStringFileOp, "");
  }

  @Test
  void exportAndTimeMachineResourcesHaveSpecialImports() throws IOException {
    var spec = buildSpec().withResources("export", "time_machine").done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), modelsDirectoryPath, "Export.cs");
    assertWriteStringFileOp(fileOps.get(3), modelsDirectoryPath, "TimeMachine.cs");
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/dotnet/samples/imports.txt")
                + "\nusing System.Threading.Tasks;\n"
                + "using System.Net;");
    writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/dotnet/samples/imports.txt")
                + "\nusing System.Threading.Tasks;\n"
                + "using System.Net;");
  }

  @Test
  void eachResourceModelShouldHaveConstructors() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), modelsDirectoryPath, "Customer.cs");
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(writeStringFileOp, "");
  }

  @Test
  void shouldHaveRegionDefinedForOperations() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp, "        #region Methods\n" + "        #endregion");
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                public static CreateRequest Create()
                {
                    string url = ApiUtil.BuildUrl("customers");
                    return new CreateRequest(url, HttpMethod.POST).SetIdempotent(false);
                }
                public static EntityRequest<Type> Retrieve(string id)
                {
                    string url = ApiUtil.BuildUrl("customers", CheckNull(id));
                    return new EntityRequest<Type>(url, HttpMethod.GET);
                }\
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                public static EntityRequest<Type> Retrieve(string id)
                {
                    string url = ApiUtil.BuildUrl("customers", CheckNull(id));
                    return new EntityRequest<Type>(url, HttpMethod.GET);
                }
                public static HierarchyRequest Hierarchy(string id)
                {
                    string url = ApiUtil.BuildUrl("customers", CheckNull(id), "hierarchy");
                    return new HierarchyRequest(url, HttpMethod.GET);
                }\
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                public static CreateRequest Create()
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                public static CustomerListRequest List()
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                public static EntityRequest<Type> Retrieve(string id)
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                public static UpdateRequest Update(string id)
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                public static CreateRequest Create()
                {
                    string url = ApiUtil.BuildUrl("customers");
                    return new CreateRequest(url, HttpMethod.POST).SetIdempotent(false);
                }\
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                public static UpdateRequest Update(string id)
                {
                    string url = ApiUtil.BuildUrl("customers", CheckNull(id));
                    return new UpdateRequest(url, HttpMethod.POST).SetIdempotent(false);
                }\
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
        #region Methods
        public static UpdateBillingInfoRequest UpdateBillingInfo(string id)
        {
            string url = ApiUtil.BuildUrl("customers", CheckNull(id), "update_billing_info");
            return new UpdateBillingInfoRequest(url, HttpMethod.POST).SetIdempotent(false);
        }\
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
        #region Methods
        public static UpdateBillingInfoRequest UpdateBillingInfo(string id)
        {
            string url = ApiUtil.BuildUrl("customers", CheckNull(id), "update_billing_info");
            return new UpdateBillingInfoRequest(url, HttpMethod.POST).SetIdempotent(false);
        }
        public static HierarchyRequest Hierarchy(string id)
        {
            string url = ApiUtil.BuildUrl("customers", CheckNull(id), "hierarchy");
            return new HierarchyRequest(url, HttpMethod.GET);
        }\
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                public static CustomerListRequest List()
                {
                    string url = ApiUtil.BuildUrl("customers");
                    return new CustomerListRequest(url);
                }\
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
        #region Methods
        [Obsolete]
        public static AddPromotionalCreditsRequest AddPromotionalCredits(string id)
        {
            string url = ApiUtil.BuildUrl("customers", CheckNull(id), "add_promotional_credits");
            return new AddPromotionalCreditsRequest(url, HttpMethod.POST).SetIdempotent(false);
        }\
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/dotnet/samples/constructors_2.txt")
            .replace("__delimiter__", ""),
        "");
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent(
                "src/test/java/com/chargebee/sdk/dotnet/samples/invoiceConstructors.txt")
            .replace("__delimiter__", ""),
        "");
  }

  @Test
  void shouldHaveObsoleteTagIfAttributeIsDeprecated() throws IOException {
    var card_status =
        buildEnum("card_status", List.of("NO_CARD", "VALID")).setEnumApiName("CardStatus").done();
    var customer =
        buildResource("customer")
            .withEnumAttribute(card_status, true)
            .withDeprecatedAttributes(List.of("card_status"))
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                #endregion
               \s
                #region Properties
                [Obsolete]
                public CardStatusEnum CardStatus\s
                {
                    get { return GetEnum<CardStatusEnum>("card_status", true); }
                }
               \s
                #endregion
               \s
        """);
  }

  @Test
  void enumAttributeShouldHaveEnumKeywordInReturnType() throws IOException {
    var autoCollection =
        buildEnum("auto_collection", List.of("On", "Off")).setEnumApiName("AutoCollection").done();
    var customer = buildResource("customer").withEnumAttribute(autoCollection, true).done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                #endregion
               \s
                #region Properties
                public AutoCollectionEnum AutoCollection\s
                {
                    get { return GetEnum<AutoCollectionEnum>("auto_collection", true); }
                }
               \s
                #endregion
               \s
        """);
  }

  @Test
  void enumAttributeShouldHaveQuestionMarkIfOptional() throws IOException {
    var pii_cleared =
        buildEnum("pii_cleared", List.of("active", "cleared")).setEnumApiName("PiiCleared").done();
    var customer = buildResource("customer").withEnumAttribute(pii_cleared).done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                #endregion
               \s
                #region Properties
                public PiiClearedEnum? PiiCleared\s
                {
                    get { return GetEnum<PiiClearedEnum>("pii_cleared", false); }
                }
               \s
                #endregion
               \s
        """);
  }

  @Test
  void typeEnumAttributeShouldHaveResourceNamePrepended() throws IOException {
    var type = buildEnum("type", List.of("quantity", "tiered")).setEnumApiName("Type").done();
    var addon =
        buildResource("addon")
            .withEnumAttribute(type, true)
            .withDeprecatedAttributes(List.of("type"))
            .done();

    var spec = buildSpec().withResource(addon).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent(
                "src/test/java/com/chargebee/sdk/dotnet/samples/addonConstructors.txt")
            .replace("__delimiter__", ""),
        """
                #region Methods
                #endregion
               \s
                #region Properties
                [Obsolete]
                public TypeEnum AddonType\s
                {
                    get { return GetEnum<TypeEnum>("type", true); }
                }
               \s
                #endregion
               \s
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

    List<FileOp> fileOps = dotnetSdkGen.generate(modelsDirectoryPath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
        #region Methods
        #endregion
       \s
        #region Properties
        public CustomerBillingAddress BillingAddress\s
        {
            get { return GetSubResource<CustomerBillingAddress>("billing_address"); }
        }
       \s
        #endregion
       \s
""");
  }

  @Test
  void shouldFollowSortOrderForSubResourceAttributes() throws IOException {
    var billingAddress =
        buildResource("billing_address").withAttribute("first_name").withSortOrder(10).done();
    var paymentMethod =
        buildResource("payment_method").withAttribute("type").withSortOrder(14).done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("payment_method", paymentMethod)
            .withSubResourceAttribute("billing_address", billingAddress)
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(modelsDirectoryPath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
        #region Methods
        #endregion
       \s
        #region Properties
        public CustomerBillingAddress BillingAddress\s
        {
            get { return GetSubResource<CustomerBillingAddress>("billing_address"); }
        }
        public CustomerPaymentMethod PaymentMethod\s
        {
            get { return GetSubResource<CustomerPaymentMethod>("payment_method"); }
        }
       \s
        #endregion
       \s
""");
  }

  @Test
  void shouldHaveAttributeDeclarationForListSubResource() throws IOException {
    var balance =
        buildResource("balance")
            .withAttribute(
                "promotional_credits", new IntegerSchema().type("integer").format("int64"))
            .done();
    var customer =
        buildResource("customer").withSubResourceArrayAttribute("balances", balance).done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(modelsDirectoryPath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                #endregion
               \s
                #region Properties
                public List<CustomerBalance> Balances\s
                {
                    get { return GetResourceList<CustomerBalance>("balances"); }
                }
               \s
                #endregion
               \s
        """);
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

    List<FileOp> fileOps = dotnetSdkGen.generate(modelsDirectoryPath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
        #region Methods
        #endregion
       \s
        #region Properties
        public List<CustomerReferralUrl> ReferralUrls\s
        {
            get { return GetResourceList<CustomerReferralUrl>("referral_urls"); }
        }
        public CustomerBillingAddress BillingAddress\s
        {
            get { return GetSubResource<CustomerBillingAddress>("billing_address"); }
        }
       \s
        #endregion
       \s
""");
  }

  @Test
  void eachResourceAttributeShouldHaveType() throws IOException {

    var customer =
        buildResource("customer")
            .withAttribute("id", true)
            .withAttribute(
                "vat_number_validated_time",
                new IntegerSchema().format("unix-time").pattern("^[0-9]{10}$"),
                false)
            .withAttribute("net_term_days", new IntegerSchema(), true)
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent(
                "src/test/java/com/chargebee/sdk/dotnet/samples/couponConstructors.txt")
            .replace("__delimiter__", ""),
        """
                #region Methods
                #endregion
               \s
                #region Properties
                public double? DiscountPercentage\s
                {
                    get { return GetValue<double?>("discount_percentage", false); }
                }
                public List<string> PlanIds\s
                {
                    get { return GetList<string>("plan_ids"); }
                }
        """);

    writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                #endregion
               \s
                #region Properties
                public string Id\s
                {
                    get { return GetValue<string>("id", true); }
                }
                public DateTime? VatNumberValidatedTime\s
                {
                    get { return GetDateTime("vat_number_validated_time", false); }
                }
                public int NetTermDays\s
                {
                    get { return GetValue<int>("net_term_days", true); }
                }
                public long? ResourceVersion\s
                {
                    get { return GetValue<long?>("resource_version", false); }
                }
                public bool? AutoCloseInvoices\s
                {
                    get { return GetValue<bool?>("auto_close_invoices", false); }
                }
                public JToken MetaData\s
                {
                    get { return GetJToken("meta_data", false); }
                }
                public JArray ExemptionDetails\s
                {
                    get { return GetJArray("exemption_details", false); }
                }
               \s
                #endregion
               \s
        """);

    writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertDotnetModelFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent(
                "src/test/java/com/chargebee/sdk/dotnet/samples/invoiceConstructors.txt")
            .replace("__delimiter__", ""),
        """
                #region Methods
                #endregion
               \s
                #region Properties
                public decimal? ProratedTaxableAmount\s
                {
                    get { return GetValue<decimal?>("prorated_taxable_amount", false); }
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                public static CreateRequest Create()
                {
                    string url = ApiUtil.BuildUrl("customers");
                    return new CreateRequest(url, HttpMethod.POST).SetIdempotent(false);
                }
                #endregion
               \s
                #region Properties
               \s
                #endregion
               \s
                #region Requests
                public class CreateRequest : EntityRequest<CreateRequest>\s
                {
                    public CreateRequest(string url, HttpMethod method)\s
                            : base(url, method)
                    {
                    }

                    public CreateRequest Id(string id)\s
                    {
                        m_params.AddOpt("id", id);
                        return this;
                    }
                    public CreateRequest NetTermDays(int netTermDays)\s
                    {
                        m_params.AddOpt("net_term_days", netTermDays);
                        return this;
                    }
                    public CreateRequest RegisteredForGst(bool registeredForGst)\s
                    {
                        m_params.AddOpt("registered_for_gst", registeredForGst);
                        return this;
                    }
        """);
  }

  @Test
  void shouldHaveOptSuffixForOptionalRequestBodyParams() throws IOException {
    var customer = buildResource("customer").done();
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
                #region Methods
                public static CreateRequest Create()
                {
                    string url = ApiUtil.BuildUrl("customers");
                    return new CreateRequest(url, HttpMethod.POST).SetIdempotent(false);
                }
                #endregion
               \s
                #region Properties
               \s
                #endregion
               \s
                #region Requests
                public class CreateRequest : EntityRequest<CreateRequest>\s
                {
                    public CreateRequest(string url, HttpMethod method)\s
                            : base(url, method)
                    {
                    }

                    public CreateRequest Id(string id)\s
                    {
                        m_params.Add("id", id);
                        return this;
                    }
                    public CreateRequest FirstName(string firstName)\s
                    {
                        m_params.AddOpt("first_name", firstName);
                        return this;
                    }
        """);
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent(
                "src/test/java/com/chargebee/sdk/dotnet/samples/invoiceConstructors.txt")
            .replace("__delimiter__", ""),
        """
                #region Methods
                public static CreateRequest Create()
                {
                    string url = ApiUtil.BuildUrl("invoices");
                    return new CreateRequest(url, HttpMethod.POST).SetIdempotent(false);
                }
                #endregion
               \s
                #region Properties
               \s
                #endregion
               \s
                #region Requests
                public class CreateRequest : EntityRequest<CreateRequest>\s
                {
                    public CreateRequest(string url, HttpMethod method)\s
                            : base(url, method)
                    {
                    }

                    [Obsolete]
                    public CreateRequest Coupon(string coupon)\s
                    {
                        m_params.AddOpt("coupon", coupon);
                        return this;
                    }
        """);
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
            //        "x-cb-is-sub-resource", true, "x-cb-meta-model-name", true
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
        #region Methods
        public static ChangeBillingDateRequest ChangeBillingDate(string id)
        {
            string url = ApiUtil.BuildUrl("customers", CheckNull(id), "change_billing_date");
            return new ChangeBillingDateRequest(url, HttpMethod.POST).SetIdempotent(false);
        }
        #endregion
       \s
        #region Properties
       \s
        #endregion
       \s
        #region Requests
        public class ChangeBillingDateRequest : EntityRequest<ChangeBillingDateRequest>\s
        {
            public ChangeBillingDateRequest(string url, HttpMethod method)\s
                    : base(url, method)
            {
            }

            public ChangeBillingDateRequest BillingDayOfWeek(Customer.BillingDayOfWeekEnum billingDayOfWeek)\s
            {
                m_params.AddOpt("billing_day_of_week", billingDayOfWeek);
                return this;
            }
""");
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
        #region Methods
        public static CreateRequest Create()
        {
            string url = ApiUtil.BuildUrl("customers");
            return new CreateRequest(url, HttpMethod.POST).SetIdempotent(false);
        }
        #endregion
       \s
        #region Properties
       \s
        #endregion
       \s
        #region Requests
        public class CreateRequest : EntityRequest<CreateRequest>\s
        {
            public CreateRequest(string url, HttpMethod method)\s
                    : base(url, method)
            {
            }

            public CreateRequest AutoCollection(ChargeBee.Models.Enums.AutoCollectionEnum autoCollection)\s
            {
                m_params.AddOpt("auto_collection", autoCollection);
                return this;
            }
""");
  }

  // can take it from here //
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
        #region Methods
        public static CreateRequest Create()
        {
            string url = ApiUtil.BuildUrl("customers");
            return new CreateRequest(url, HttpMethod.POST).SetIdempotent(false);
        }
        #endregion
       \s
        #region Properties
       \s
        #endregion
       \s
        #region Requests
        public class CreateRequest : EntityRequest<CreateRequest>\s
        {
            public CreateRequest(string url, HttpMethod method)\s
                    : base(url, method)
            {
            }

            public CreateRequest BillingAddressFirstName(string billingAddressFirstName)\s
            {
                m_params.AddOpt("billing_address[first_name]", billingAddressFirstName);
                return this;
            }
""");
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
        #region Methods
        public static CreateRequest Create()
        {
            string url = ApiUtil.BuildUrl("customers");
            return new CreateRequest(url, HttpMethod.POST).SetIdempotent(false);
        }
        #endregion
       \s
        #region Properties
       \s
        #endregion
       \s
        #region Requests
        public class CreateRequest : EntityRequest<CreateRequest>\s
        {
            public CreateRequest(string url, HttpMethod method)\s
                    : base(url, method)
            {
            }

            public CreateRequest BillingAddressValidationStatus(ChargeBee.Models.Enums.ValidationStatusEnum billingAddressValidationStatus)\s
            {
                m_params.AddOpt("billing_address[validation_status]", billingAddressValidationStatus);
                return this;
            }
""");
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent(
                "src/test/java/com/chargebee/sdk/dotnet/samples/invoiceConstructors.txt")
            .replace("__delimiter__", ""),
        """
        #region Methods
        public static CreateForChargeItemsAndChargesRequest CreateForChargeItemsAndCharges()
        {
            string url = ApiUtil.BuildUrl("invoices", "create_for_charge_items_and_charges");
            return new CreateForChargeItemsAndChargesRequest(url, HttpMethod.POST).SetIdempotent(false);
        }
        #endregion
       \s
        #region Properties
       \s
        #endregion
       \s
        #region Requests
        public class CreateForChargeItemsAndChargesRequest : EntityRequest<CreateForChargeItemsAndChargesRequest>\s
        {
            public CreateForChargeItemsAndChargesRequest(string url, HttpMethod method)\s
                    : base(url, method)
            {
            }

            public CreateForChargeItemsAndChargesRequest PaymentIntentPaymentMethodType(PaymentIntent.PaymentMethodTypeEnum paymentIntentPaymentMethodType)\s
            {
                m_params.AddOpt("payment_intent[payment_method_type]", paymentIntentPaymentMethodType);
                return this;
            }
""");
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent(
                "src/test/java/com/chargebee/sdk/dotnet/samples/invoiceConstructors.txt")
            .replace("__delimiter__", ""),
        """
        #region Methods
        public static CreateForChargeItemsAndChargesRequest CreateForChargeItemsAndCharges()
        {
            string url = ApiUtil.BuildUrl("invoices", "create_for_charge_items_and_charges");
            return new CreateForChargeItemsAndChargesRequest(url, HttpMethod.POST).SetIdempotent(false);
        }
        #endregion
       \s
        #region Properties
       \s
        #endregion
       \s
        #region Requests
        public class CreateForChargeItemsAndChargesRequest : EntityRequest<CreateForChargeItemsAndChargesRequest>\s
        {
            public CreateForChargeItemsAndChargesRequest(string url, HttpMethod method)\s
                    : base(url, method)
            {
            }

            [Obsolete]
            public CreateForChargeItemsAndChargesRequest PaymentIntentGwPaymentMethodId(string paymentIntentGwPaymentMethodId)\s
            {
                m_params.AddOpt("payment_intent[gw_payment_method_id]", paymentIntentGwPaymentMethodId);
                return this;
            }
""");
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
        #region Methods
        public static CreateRequest Create()
        {
            string url = ApiUtil.BuildUrl("customers");
            return new CreateRequest(url, HttpMethod.POST).SetIdempotent(false);
        }
        #endregion
       \s
        #region Properties
       \s
        #endregion
       \s
        #region Requests
        public class CreateRequest : EntityRequest<CreateRequest>\s
        {
            public CreateRequest(string url, HttpMethod method)\s
                    : base(url, method)
            {
            }

            public CreateRequest EntityIdentifierId(int index, string entityIdentifierId)\s
            {
                m_params.AddOpt("entity_identifiers[id][" + index + "]", entityIdentifierId);
                return this;
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent(
                "src/test/java/com/chargebee/sdk/dotnet/samples/invoiceConstructors.txt")
            .replace("__delimiter__", ""),
        """
        #region Methods
        public static CreateForChargeItemsAndChargesRequest CreateForChargeItemsAndCharges()
        {
            string url = ApiUtil.BuildUrl("invoices", "create_for_charge_items_and_charges");
            return new CreateForChargeItemsAndChargesRequest(url, HttpMethod.POST).SetIdempotent(false);
        }
        #endregion
       \s
        #region Properties
       \s
        #endregion
       \s
        #region Requests
        public class CreateForChargeItemsAndChargesRequest : EntityRequest<CreateForChargeItemsAndChargesRequest>\s
        {
            public CreateForChargeItemsAndChargesRequest(string url, HttpMethod method)\s
                    : base(url, method)
            {
            }

            public CreateForChargeItemsAndChargesRequest ChargeAvalaraSaleType(int index, ChargeBee.Models.Enums.AvalaraSaleTypeEnum chargeAvalaraSaleType)\s
            {
                m_params.AddOpt("charges[avalara_sale_type][" + index + "]", chargeAvalaraSaleType);
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent(
                "src/test/java/com/chargebee/sdk/dotnet/samples/invoiceConstructors.txt")
            .replace("__delimiter__", ""),
        """
        #region Methods
        public static ImportInvoiceRequest ImportInvoice()
        {
            string url = ApiUtil.BuildUrl("invoices", "import_invoice");
            return new ImportInvoiceRequest(url, HttpMethod.POST).SetIdempotent(false);
        }
        #endregion
       \s
        #region Properties
        public List<InvoiceLineItem> LineItems\s
        {
            get { return GetResourceList<InvoiceLineItem>("line_items"); }
        }
       \s
        #endregion
       \s
        #region Requests
        public class ImportInvoiceRequest : EntityRequest<ImportInvoiceRequest>\s
        {
            public ImportInvoiceRequest(string url, HttpMethod method)\s
                    : base(url, method)
            {
            }

            public ImportInvoiceRequest LineItemEntityType(int index, InvoiceLineItem.EntityTypeEnum lineItemEntityType)\s
            {
                m_params.AddOpt("line_items[entity_type][" + index + "]", lineItemEntityType);
                return this;
            }
""");
  }

  @Test
  void shouldHaveResultBaseFileUnderInternalDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), internalDirectoryPath, "ResultBase.cs");
  }

  @Test
  void shouldHaveResourceResponsesInResultBaseFile() throws IOException {
    var customer = buildResource("customer").done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec().withResources("customer").withOperation("/customer", createOperation).done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertDotnetResultFileContents(
        writeStringFileOp,
        """
                public Customer Customer
                {
                    get {  return GetResource<Customer>("customer"); }
                }\
        """);
  }

  @Test
  void shouldFollowSortOrderForResourceResponsesInResultBaseFile() throws IOException {

    var customer = buildResource("customer").withAttribute("id", true).withSortOrder(14).done();
    var invoice = buildResource("invoice").withAttribute("id", true).withSortOrder(34).done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var listOperation =
        buildListOperation("list_invoices")
            .forResource("invoice")
            .withResponse(resourceResponseParam("invoice", invoice))
            .done();
    var spec =
        buildSpec()
            .withResources(customer, invoice)
            .withOperation("/customers", createOperation)
            .withOperation("/invoices", listOperation)
            .done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertDotnetResultFileContents(
        writeStringFileOp,
        """
                public Customer Customer
                {
                    get {  return GetResource<Customer>("customer"); }
                }
                public Invoice Invoice
                {
                    get {  return GetResource<Invoice>("invoice"); }
                }\
        """);
  }

  @Test
  void shouldHaveListResourceResponsesInResultBaseFile() throws IOException {
    var invoice = buildResource("invoice").withAttribute("id", true).withSortOrder(34).done();
    var createAnInvoiceForUnbilledCharges =
        buildPostOperation("create_an_invoice_for_unbilled_charges")
            .forResource("invoice")
            .withResponse(
                resourceResponseParam(
                    "invoices", new ArraySchema().items(new Schema().$ref("Invoice"))))
            .done();
    var spec =
        buildSpec()
            .withResources(invoice)
            .withOperation(
                "/unbilled_charges/invoice_unbilled_charges", createAnInvoiceForUnbilledCharges)
            .done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertDotnetResultFileContents(
        writeStringFileOp,
        """
        public Invoice Invoice
        {
            get {  return GetResource<Invoice>("invoice"); }
        }

        public List<Invoice> Invoices
        {
            get {  return (List<Invoice>)GetResourceList<Invoice>("invoices", "invoice"); }
        }\
""");
  }

  @Test
  void shouldHaveSpecialDefinitionForAttributeResourceResponse() throws IOException {
    var spec = buildSpec().withResources("attribute").done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertDotnetResultFileContents(
        writeStringFileOp,
        """
                public ChargeBee.Models.Attribute Attribute
                {
                    get {  return GetResource<ChargeBee.Models.Attribute>("attribute"); }
                }\
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

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertDotnetModelFileContent(
        writeStringFileOp,
        """
        #region Methods
        public static CustomerListRequest List()
        {
            string url = ApiUtil.BuildUrl("customers");
            return new CustomerListRequest(url);
        }
        #endregion
       \s
        #region Properties
        public string Id\s
        {
            get { return GetValue<string>("id", true); }
        }
       \s
        #endregion
       \s
        #region Requests
        public class CustomerListRequest : ListRequestBase<CustomerListRequest>\s
        {
            public CustomerListRequest(string url)\s
                    : base(url)
            {
            }

            public StringFilter<CustomerListRequest> FirstName()\s
            {
                return new StringFilter<CustomerListRequest>("first_name", this).SupportsPresenceOperator(true);       \s
            }
            public EnumFilter<ChargeBee.Models.Enums.AutoCollectionEnum, CustomerListRequest> AutoCollection()\s
            {
                return new EnumFilter<ChargeBee.Models.Enums.AutoCollectionEnum, CustomerListRequest>("auto_collection", this);       \s
            }
            public TimestampFilter<CustomerListRequest> CreatedAt()\s
            {
                return new TimestampFilter<CustomerListRequest>("created_at", this);       \s
            }
            public BooleanFilter<CustomerListRequest> AutoCloseInvoices()\s
            {
                return new BooleanFilter<CustomerListRequest>("auto_close_invoices", this);       \s
            }
            public SortFilter<CustomerListRequest> SortBy()\s
            {
                return new SortFilter<CustomerListRequest>("sort_by", this);       \s
            }
        }
        #endregion
""");
  }

  @Test
  void PostOperationWithFilterParamsShouldBehaveLikeGetOperation() throws IOException {
    var export = buildResource("customer").done();
    var exportOperation =
        buildPostOperation("subscription")
            .forResource("customer")
            .withRequestBody(
                "item_price",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "id",
                            new ObjectSchema()
                                .properties(
                                    Map.of(
                                        "is",
                                        new StringSchema().extensions(Map.of("type", "string"))))))
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "StringFilter")))
            .withRequestBody(
                "item_price2",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "id",
                            new ObjectSchema()
                                .properties(
                                    Map.of(
                                        "is",
                                        new StringSchema().extensions(Map.of("type", "string"))))))
                    .extensions(Map.of("x-cb-is-filter-parameter", false)))
            .withResponse(resourceResponseParam("any", export))
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(export)
            .withPostOperation("customer/subscription", exportOperation)
            .done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertTrue(
        writeStringFileOp.fileContent.contains(
            "SubscriptionRequest(string url, HttpMethod method, bool supportsFilter=false)"));
    assertTrue(writeStringFileOp.fileContent.contains("base(url, method, supportsFilter)"));
  }

  @Test
  void PostOperationWithSubFilterParamsShouldBehaveLikeGetOperation() throws IOException {
    var export = buildResource("customer").done();
    var exportOperation =
        buildPostOperation("subscription")
            .forResource("customer")
            .withRequestBody(
                "item_price",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "id",
                            new ObjectSchema()
                                .properties(
                                    Map.of(
                                        "is",
                                        new StringSchema().extensions(Map.of("type", "string"))))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-filter-parameter",
                                        true,
                                        "x-cb-sdk-filter-name",
                                        "StringFilter")))))
            .withResponse(resourceResponseParam("any", export))
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(export)
            .withPostOperation("customer/subscription", exportOperation)
            .done();

    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertTrue(
        writeStringFileOp.fileContent.contains(
            "SubscriptionRequest(string url, HttpMethod method, bool supportsFilter=false)"));
    assertTrue(writeStringFileOp.fileContent.contains("base(url, method, supportsFilter)"));
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
    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertTrue(writeStringFileOp.fileContent.contains("[EnumMember(Value = \"plan\")]"));
    assertTrue(writeStringFileOp.fileContent.contains("[EnumMember(Value = \"plan_setup\")]"));
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
    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertTrue(writeStringFileOp.fileContent.contains("[EnumMember(Value = \"plan\")]"));
    assertTrue(writeStringFileOp.fileContent.contains("[EnumMember(Value = \"plan_setup\")]"));
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
    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .contains(
            "return new CreateRequest(url,"
                + " HttpMethod.POST).SetSubDomain(\"test-domain\").IsJsonRequest(true).SetIdempotent(false);");
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
    List<FileOp> fileOps = dotnetSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .contains(
            "\n"
                + "        public CreateRequest Events(List<EventCreateInputParams> array){\n"
                + "            JArray jArray = new JArray();\n"
                + "            foreach (var item in array){\n"
                + "                jArray.Add(item.ToJObject());\n"
                + "            }\n"
                + "            m_params.Add(\"events\", jArray);\n"
                + "            return this;\n"
                + "        }")
        .contains("EventCreateInputParams")
        .contains("EventCreateInputParamsBuilder");
  }
}
