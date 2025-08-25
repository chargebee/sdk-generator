package com.chargebee.sdk.go;

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

public class GoTests extends LanguageTests {
  private static final String basePath = "/go";
  private static final String enumsDirectoryPath = "/go/enum";
  public static Go goSdkGen;
  private final String modelsDirectoryPath = "/go/models";

  @BeforeAll
  static void beforeAll() {
    goSdkGen = new Go();
  }

  void assertGoEnumFileContent(FileOp.WriteString fileOp, String body) {
    assertThat(fileOp.fileContent).startsWith("package enum\n" + "\n" + body);
  }

  void assertGoActionFileContent(FileOp.WriteString fileOp, String imports, String body) {
    assertThat(fileOp.fileContent).startsWith(imports + "\n" + body);
  }

  void assertGoModelFileContent(FileOp.WriteString fileOp, String body) {
    assertThat(fileOp.fileContent.replaceAll("\\s", "")).isEqualTo(body.replaceAll("\\s", ""));
  }

  void assertGoResultFileContent(FileOp.WriteString fileOp, String imports, String body)
      throws IOException {
    String expectedContent =
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/go/samples/resultTemplate.txt")
            .replace("__imports__", imports)
            .replace("__body__", body);
    assertThat(fileOp.fileContent).startsWith(expectedContent);
  }

  void assertGoModelEnumFileContent(FileOp.WriteString fileOp, String body) {
    assertThat(fileOp.fileContent).startsWith(body);
  }

  @Test
  void baseDirectoryShouldNotBeClearedBeforeGeneration() {
    assertThat(goSdkGen.cleanDirectoryBeforeGenerate()).isFalse();
  }

  @Test
  void shouldCreateEnumsDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(0), basePath, "/enum");
  }

  @Test
  void shouldCreateGlobalEnumFilesInEnumsDirectory() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(3), enumsDirectoryPath, "auto_collection.go");
  }

  @Test
  void globalEnumFilesInEnumsDirectoryShouldBeSortedAlphabetically() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var channel = buildEnum("Channel", List.of("app_store", "web", "play_store")).done();
    var spec = buildSpec().withEnums(channel, autoCollection).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(3), enumsDirectoryPath, "auto_collection.go");
    assertWriteStringFileOp(fileOps.get(4), enumsDirectoryPath, "channel.go");
  }

  @Test
  void shouldCreateGlobalEnumFilesInEnumsDirectoryWithSpecifiedGlobalEnums() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("on", "off")).done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertGoEnumFileContent(
        writeStringFileOp,
        """
        type AutoCollection string

        const (
            AutoCollectionOn AutoCollection = "on"
            AutoCollectionOff AutoCollection = "off"
        )""");
  }

  @Test
  void possibleValuesShouldBeInSnakeCase() throws IOException {
    var applyOn = buildEnum("ApplyOn", List.of("invoice_amount", "specific_item_price")).done();
    var spec = buildSpec().withEnums(applyOn).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertGoEnumFileContent(
        writeStringFileOp,
        """
        type ApplyOn string

        const (
            ApplyOnInvoiceAmount ApplyOn = "invoice_amount"
            ApplyOnSpecificItemPrice ApplyOn = "specific_item_price"
        )""");
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var createDirectoryFileOp = (FileOp.CreateDirectory) fileOps.get(1);
    assertCreateDirectoryFileOp(createDirectoryFileOp, basePath, "/actions");
  }

  @Test
  void shouldHaveSeparateDirectoryForEachResourceOperations() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema(), true)
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var createDirectoryFileOp = (FileOp.CreateDirectory) fileOps.get(3);
    assertCreateDirectoryFileOp(createDirectoryFileOp, basePath + "/actions", "customer");
  }

  @Test
  void shouldHaveOperationsFileInSeparateDirectory() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var retrieveOperation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer_id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}", retrieveOperation)
            .done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertWriteStringFileOp(writeStringFileOp, basePath + "/actions/customer", "customer.go");
  }

  @Test
  void shouldHaveEachOperationAsASeparateRequestObject() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema(), true)
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var retrieveOperation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer_id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers", createOperation)
            .withOperation("/customers/{customer-id}", retrieveOperation)
            .done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertGoActionFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/go/samples/customers_imports.txt"),
        """
func Create(params *customer.CreateRequestParams) chargebee.RequestObj {
    return chargebee.Send("POST", fmt.Sprintf("/customers"), params)
}
func Retrieve(id string) chargebee.RequestObj {
    return chargebee.Send("GET", fmt.Sprintf("/customers/%v", url.PathEscape(id)), nil)
}""");
  }

  @Test
  void shouldHaveUrlInImportsOnlyIfThereIsAtleastOneOperationWithPathParam() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema(), true)
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var retrieveOperation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer_id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers", createOperation)
            .withOperation("/customers/{customer-id}", retrieveOperation)
            .done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertGoActionFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/go/samples/customers_imports.txt"),
        "");
  }

  @Test
  void shouldHaveSpecialSnippetsAndImportsForEventsResource() throws IOException {
    var event = buildResource("event").done();
    var retrieveOperation =
        buildOperation("retrieve")
            .forResource("event")
            .withPathParam("event_id")
            .withResponse(resourceResponseParam("event", event))
            .done();
    var spec =
        buildSpec()
            .withResource(event)
            .withOperation("/events/{event-id}", retrieveOperation)
            .done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertThat(
        writeStringFileOp.equals(
            FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/go/samples/events.txt")));
  }

  @Test
  void exportResourceShouldHaveSpecifiedSnippetAndImports() throws IOException {
    var export = buildResource("export").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("export")
            .withPathParam("export-id")
            .withResponse(resourceResponseParam("export", export))
            .done();
    var spec =
        buildSpec().withResource(export).withOperation("/exports/{export-id}", operation).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertThat(
        writeStringFileOp.equals(
            FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/go/samples/exports.txt")));
  }

  @Test
  void timeMachineResourceShouldHaveSpecifiedSnippetAndImports() throws IOException {
    var timeMachine = buildResource("time_machine").withAttribute("name", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("time_machine")
            .withPathParam("time-machine-name")
            .withResponse(resourceResponseParam("time_machine", timeMachine))
            .done();
    var spec =
        buildSpec()
            .withResource(timeMachine)
            .withOperation("/time_machines/{time-machine-name}", operation)
            .done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertThat(
        writeStringFileOp.equals(
            FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/go/samples/timeMachine.txt")));
  }

  @Test
  void hostedPageResourceShouldHaveSpecifiedSnippetAndImports() throws IOException {
    var hosted_page = buildResource("hosted_page").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("hosted_page")
            .withPathParam("hosted-page-id")
            .withResponse(resourceResponseParam("hosted_page", hosted_page))
            .done();
    var spec =
        buildSpec()
            .withResource(hosted_page)
            .withOperation("/hosted_pages/{hosted-page-id}", operation)
            .done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertThat(
        writeStringFileOp.equals(
            FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/go/samples/hostedPages.txt")));
  }

  @Test
  void shouldHaveOperationNameIfRequestBodyPresent() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema(), true)
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertGoActionFileContent(
        writeStringFileOp,
        """
        package customer

        import (
            "fmt"
            "github.com/chargebee/chargebee-go/v3"
            "github.com/chargebee/chargebee-go/v3/models/customer"
        )
        """,
        "func Create(params *customer.CreateRequestParams) chargebee.RequestObj {");
  }

  @Test
  void shouldHaveOperationNameIfQueryParamsPresent() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var listOperation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("include_deleted", new BooleanSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", listOperation).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertGoActionFileContent(
        writeStringFileOp,
        """
        package customer

        import (
            "fmt"
            "github.com/chargebee/chargebee-go/v3"
            "github.com/chargebee/chargebee-go/v3/models/customer"
        )
        """,
        "func List(params *customer.ListRequestParams) chargebee.ListRequestObj {");
  }

  @Test
  void shouldHavePathParamAsArgumentIfPresent() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var updateRequest =
        buildPostOperation("update")
            .forResource("customer")
            .withPathParam("customer_id")
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers/{customer-id}", updateRequest)
            .done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertGoActionFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/go/samples/customers_imports.txt"),
        """
func Update(id string, params *customer.UpdateRequestParams) chargebee.RequestObj {
    return chargebee.Send("POST", fmt.Sprintf("/customers/%v", url.PathEscape(id)), params)
}""");
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
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertGoActionFileContent(
        writeStringFileOp,
        """
        package customer

        import (
            "fmt"
            "github.com/chargebee/chargebee-go/v3"
            "github.com/chargebee/chargebee-go/v3/models/customer"
        )
        """,
        """
        func Create(params *customer.CreateRequestParams) chargebee.RequestObj {
            return chargebee.Send("POST", fmt.Sprintf("/customers"), params)
        }""");
  }

  @Test
  void shouldHavePathParamInUrlIfPresent() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var updateRequest =
        buildPostOperation("update")
            .forResource("customer")
            .withPathParam("customer_id")
            .withRequestBody("first_name", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers/{customer-id}", updateRequest)
            .done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertGoActionFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/go/samples/customers_imports.txt"),
        """
func Update(id string, params *customer.UpdateRequestParams) chargebee.RequestObj {
    return chargebee.Send("POST", fmt.Sprintf("/customers/%v", url.PathEscape(id)), params)
}""");
  }

  @Test
  void shouldHaveUrlSuffixIfPresent() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var UpdateBillingInfoRequest =
        buildPostOperation("updateBillingInfo")
            .forResource("customer")
            .withPathParam("customer_id")
            .withRequestBody("vat_number", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation(
                "/customers/{customer-id}/update_billing_info", UpdateBillingInfoRequest)
            .done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertGoActionFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/go/samples/customers_imports.txt"),
        """
func UpdateBillingInfo(id string, params *customer.UpdateBillingInfoRequestParams) chargebee.RequestObj {
    return chargebee.Send("POST", fmt.Sprintf("/customers/%v/update_billing_info", url.PathEscape(id)), params)
}""");
  }

  @Test
  void shouldHaveHttpMethodInReturnStatement() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var hierarchyRequest =
        buildOperation("hierarchy")
            .forResource("customer")
            .withPathParam("customer_id")
            .withQueryParam("hierarchy_operation_type", new StringSchema()._enum(List.of()))
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var updateBillingInfoRequest =
        buildPostOperation("updateBillingInfo")
            .forResource("customer")
            .withPathParam("customer_id")
            .withRequestBody("vat_number", new StringSchema(), false)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation(
                "/customers/{customer-id}/update_billing_info", updateBillingInfoRequest)
            .withOperation("/customers/{customer-id}/hierarchy", hierarchyRequest)
            .done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertGoActionFileContent(
        writeStringFileOp,
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/go/samples/customers_imports.txt"),
        """
func UpdateBillingInfo(id string, params *customer.UpdateBillingInfoRequestParams) chargebee.RequestObj {
    return chargebee.Send("POST", fmt.Sprintf("/customers/%v/update_billing_info", url.PathEscape(id)), params)
}
func Hierarchy(id string, params *customer.HierarchyRequestParams) chargebee.RequestObj {
    return chargebee.Send("GET", fmt.Sprintf("/customers/%v/hierarchy", url.PathEscape(id)), params)
}""");
  }

  @Test
  void shouldNotHaveHttpMethodInReturnStatementForListOperation() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var listOperation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("include_deleted", new BooleanSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", listOperation).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertGoActionFileContent(
        writeStringFileOp,
        """
        package customer

        import (
            "fmt"
            "github.com/chargebee/chargebee-go/v3"
            "github.com/chargebee/chargebee-go/v3/models/customer"
        )
        """,
        """
        func List(params *customer.ListRequestParams) chargebee.ListRequestObj {
            return chargebee.SendList("GET", fmt.Sprintf("/customers"), params)
        }""");
  }

  @Test
  void shouldIgnoreBulkOperations() throws IOException {
    var token = buildResource("token").withAttribute("id", true).done();
    var createForCardOperation =
        buildPostOperation("create_for_card")
            .forResource("token")
            .withResponse(resourceResponseParam("token", token))
            .asBulkOperationFromSDKGeneration()
            .withSortOrder(0)
            .done();
    var spec =
        buildSpec()
            .withResource(token)
            .withPostOperation("/tokens/create_for_card", createForCardOperation)
            .done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package token

        type Token struct {
        \tId     string `json:"id"`
        \tObject string `json:"object"`
        }
        """);
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package invoice

        type Invoice struct {
        \tObject string `json:"object"`
        }
        """);
  }

  @Test
  void enumAttributeShouldHaveEnumKeywordInReturnType() throws IOException {
    var autoCollection =
        buildEnum("auto_collection", List.of("On", "Off"))
            .setEnumApiName("AutoCollection")
            .asGenSeparate()
            .done();
    var customer = buildResource("customer").withEnumAttribute(autoCollection, true).done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package customer

        import(
        \t"github.com/chargebee/chargebee-go/v3/enum"
        )

        type Customer struct {
        \tAutoCollection enum.AutoCollection    `json:"auto_collection"`
        \tConsents       map[string]interface{} `json:"consents"`
        \tObject         string                 `json:"object"`
        }""");
  }

  @Test
  void enumAttributeShouldHaveQuestionMarkIfOptional() throws IOException {
    var pii_cleared =
        buildEnum("pii_cleared", List.of("active", "cleared")).setEnumApiName("PiiCleared").done();
    var customer = buildResource("customer").withEnumAttribute(pii_cleared).done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package customer

        import (
        \tcustomerEnum "github.com/chargebee/chargebee-go/v3/models/customer/enum"
        )

        type Customer struct {
        \tPiiCleared customerEnum.PiiCleared `json:"pii_cleared"`
        \tConsents   map[string]interface{}  `json:"consents"`
        \tObject     string                  `json:"object"`
        }""");
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package addon

        import (
        \taddonEnum "github.com/chargebee/chargebee-go/v3/models/addon/enum"
        )

        type Addon struct {
        \tType   addonEnum.Type `json:"type"`
        \tObject string         `json:"object"`
        }
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package customer

        type Customer struct {
        \tBillingAddress *BillingAddress        `json:"billing_address"`
        \tConsents       map[string]interface{} `json:"consents"`
        \tObject         string                 `json:"object"`
        }
        type BillingAddress struct {
        \tFirstName string `json:"first_name"`
        \tObject    string `json:"object"`
        }
        """);
  }

  @Test
  void shouldFollowSortOrderForSubResourceAttributes() throws IOException {
    var billingAddress =
        buildResource("billing_address").withAttribute("first_name").withSortOrder(10).done();
    var type =
        buildEnum("type", List.of("quantity", "tiered"))
            .setEnumApiName("Type")
            .asGenSeparate()
            .done();
    var paymentMethod =
        buildResource("payment_method").withEnumAttribute(type, true).withSortOrder(14).done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("payment_method", paymentMethod)
            .withSubResourceAttribute("billing_address", billingAddress)
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package customer

        import (
        \t"github.com/chargebee/chargebee-go/v3/enum"
        )

        type Customer struct {
        \tBillingAddress *BillingAddress        `json:"billing_address"`
        \tPaymentMethod  *PaymentMethod         `json:"payment_method"`
        \tConsents       map[string]interface{} `json:"consents"`
        \tObject         string                 `json:"object"`
        }
        type PaymentMethod struct {
        \tType   enum.Type `json:"type"`
        \tObject string    `json:"object"`
        }
        type BillingAddress struct {
        \tFirstName string `json:"first_name"`
        \tObject    string `json:"object"`
        }
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package customer

        type Customer struct {
        \tBalances []*Balance             `json:"balances"`
        \tConsents map[string]interface{} `json:"consents"`
        \tObject   string                 `json:"object"`
        }
        type Balance struct {
        \tPromotionalCredits int64  `json:"promotional_credits"`
        \tObject             string `json:"object"`
        }
        """);
  }

  @Test
  void shouldSupportBothArrayAndNormalSubResourceAttribute() throws IOException {
    var referralUrl =
        buildResource("referral_url")
            .withAttribute("created_at", new IntegerSchema().type("integer").format("int64"))
            .done();
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .withSubResourceArrayAttribute("referral_urls", referralUrl)
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package customer

        type Customer struct {
        \tBillingAddress *BillingAddress        `json:"billing_address"`
        \tReferralUrls   []*ReferralUrl         `json:"referral_urls"`
        \tConsents       map[string]interface{} `json:"consents"`
        \tObject         string                 `json:"object"`
        }
        type BillingAddress struct {
        \tFirstName string `json:"first_name"`
        \tObject    string `json:"object"`
        }
        type ReferralUrl struct {
        \tCreatedAt int64  `json:"created_at"`
        \tObject    string `json:"object"`
        }
        """);
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
            .withAttribute("exchange_rate", new NumberSchema().format("decimal"))
            .done();

    var coupon =
        buildResource("coupon")
            .withAttribute("discount_percentage", new NumberSchema().format("double"), false)
            .withAttribute("plan_ids", new ArraySchema().items(new StringSchema()), false)
            .done();

    var spec = buildSpec().withResources(customer, invoice, coupon).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package coupon

        type Coupon struct {
        \tDiscountPercentage float64  `json:"discount_percentage"`
        \tPlanIds            []string `json:"plan_ids"`
        \tObject             string   `json:"object"`
        }
        """);

    writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package customer

        import (
        \t"encoding/json"
        )

        type Customer struct {
        \tId                     string                 `json:"id"`
        \tNetTermDays            int32                  `json:"net_term_days"`
        \tVatNumberValidatedTime int64                  `json:"vat_number_validated_time"`
        \tResourceVersion        int64                  `json:"resource_version"`
        \tAutoCloseInvoices      bool                   `json:"auto_close_invoices"`
        \tMetaData               json.RawMessage        `json:"meta_data"`
        \tExemptionDetails       json.RawMessage        `json:"exemption_details"`
        \tConsents               map[string]interface{} `json:"consents"`
        \tObject                 string                 `json:"object"`
        }
        """);

    writeStringFileOp = (FileOp.WriteString) fileOps.get(9);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package invoice

        type Invoice struct {
        \tExchangeRate float64 `json:"exchange_rate"`
        \tObject       string  `json:"object"`
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package customer

        type Customer struct {
        \tConsents map[string]interface{} `json:"consents"`
        \tObject   string                 `json:"object"`
        }
        type CreateRequestParams struct {
        \tId               string `json:"id,omitempty"`
        \tNetTermDays      *int32 `json:"net_term_days,omitempty"`
        \tRegisteredForGst *bool  `json:"registered_for_gst,omitempty"`
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
package customer

import (
\tcustomerEnum "github.com/chargebee/chargebee-go/v3/models/customer/enum"
)

type Customer struct {
\tConsents map[string]interface{} `json:"consents"`
\tObject   string                 `json:"object"`
}
type ChangeBillingDateRequestParams struct {
\tBillingDayOfWeek customerEnum.BillingDayOfWeek `json:"billing_day_of_week,omitempty"`
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
                            "./enums/" + autoCollection.typeName + ".yaml",
                            "x-cb-is-gen-separate",
                            true)))
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package customer

        type Customer struct {
        \tConsents map[string]interface{} `json:"consents"`
        \tObject   string                 `json:"object"`
        }
        type CreateRequestParams struct {
        \tAutoCollection enum.AutoCollection `json:"auto_collection,omitempty"`
        }
        """);
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package customer

        type Customer struct {
        \tConsents map[string]interface{} `json:"consents"`
        \tObject   string                 `json:"object"`
        }
        type CreateRequestParams struct {
        \tBillingAddress *CreateBillingAddressParams `json:"billing_address,omitempty"`
        }
        type CreateBillingAddressParams struct {
        \tFirstName string `json:"first_name,omitempty"`
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package customer

        type Customer struct {
        \tConsents map[string]interface{} `json:"consents"`
        \tObject   string                 `json:"object"`
        }
        type CreateRequestParams struct {
        \tBillingAddress *CreateBillingAddressParams `json:"billing_address,omitempty"`
        }
        type CreateBillingAddressParams struct {
        \tValidationStatus enum.ValidationStatus `json:"validation_status,omitempty"`
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
                new StringSchema()
                    .properties(
                        Map.of(
                            "payment_method_type",
                            new StringSchema()
                                ._enum(List.of("card", "ideal", "sofort"))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-sub-resource",
                                        true,
                                        "x-cb-meta-model-name",
                                        "payment_intent"))))
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
package invoice

import (
\tpaymentIntentEnum "github.com/chargebee/chargebee-go/v3/models/paymentintent/enum"
)

type Invoice struct {
\tObject string `json:"object"`
}
type CreateForChargeItemsAndChargesRequestParams struct {
\tPaymentIntent *CreateForChargeItemsAndChargesPaymentIntentParams `json:"payment_intent,omitempty"`
}
type CreateForChargeItemsAndChargesPaymentIntentParams struct {
\tPaymentMethodType paymentIntentEnum.PaymentMethodType `json:"payment_method_type,omitempty"`
}""");
  }

  @Test
  void shouldGenerateEnumsFileForEnumsInMultiAttributesInRequestBodyParams() throws IOException {
    var lineItemDiscounts =
        buildResource("line_item_discounts")
            .withEnumAttribute(
                "discount_type",
                List.of(
                    "item_level_coupon",
                    "document_level_coupon",
                    "promotional_credits",
                    "prorated_credits",
                    "item_level_discount",
                    "document_level_discount"),
                false)
            .done();
    var invoiceEstimates =
        buildResource("invoice_estimate")
            .withSubResourceArrayAttribute("line_item_discounts", lineItemDiscounts)
            .done();
    var spec = buildSpec().withResource(invoiceEstimates).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertThat(writeStringFileOp.fileName).isEqualTo("line_item_discount_discount_type.go");
    assertGoModelFileContent(
        writeStringFileOp,
        """
package enum

type LineItemDiscountDiscountType string

const (
	LineItemDiscountDiscountTypeItemLevelCoupon       LineItemDiscountDiscountType = "item_level_coupon"
	LineItemDiscountDiscountTypeDocumentLevelCoupon   LineItemDiscountDiscountType = "document_level_coupon"
	LineItemDiscountDiscountTypePromotionalCredits    LineItemDiscountDiscountType = "promotional_credits"
	LineItemDiscountDiscountTypeProratedCredits       LineItemDiscountDiscountType = "prorated_credits"
	LineItemDiscountDiscountTypeItemLevelDiscount     LineItemDiscountDiscountType = "item_level_discount"
	LineItemDiscountDiscountTypeDocumentLevelDiscount LineItemDiscountDiscountType = "document_level_discount"
)""");
  }

  @Test
  void shouldHavaOmitemptyForDeprecatedMultiAttributesInRequestBodyParams() throws IOException {
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
package invoice

type Invoice struct {
\tObject string `json:"object"`
}
type CreateForChargeItemsAndChargesRequestParams struct {
\tPaymentIntent *CreateForChargeItemsAndChargesPaymentIntentParams `json:"payment_intent,omitempty"`
}
type CreateForChargeItemsAndChargesPaymentIntentParams struct {
\tGwPaymentMethodId string `json:"gw_payment_method_id,omitempty"`
}""");
  }

  @Test
  void shouldSupportListMultiAttributesInRequestBodyParams() throws IOException {
    var customer = buildResource("customer").done();
    //        var createOperation = buildPostOperation("create").forResource("customer")
    //                .withRequestBody("entity_identifiers",
    //                        new ObjectSchema().properties(Map.of("id", new
    // ArraySchema().items(new
    // StringSchema().deprecated(false).extensions(Map.of("x-cb-is-sub-resource" , true,
    // "x-cb-is-dependent-attribute",
    // true))))).extensions(Map.of("x-cb-is-composite-array-request-body", true,
    // "x-cb-is-dependent-attribute", true)))
    //                .withResponse(resourceResponseParam("customer", customer))
    //                .withQueryParam("paid_on_after")
    //                .asInputObjNeeded()
    //                .done();

    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "entity_identifiers",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "id",
                            new StringSchema()
                                .items(
                                    new StringSchema()
                                        .extensions(
                                            Map.of(
                                                "x-cb-is-global-enum",
                                                true,
                                                "x-cb-global-enum-reference",
                                                "./enums/AvalaraSalesType.yaml",
                                                "x-cb-is-sub-resource",
                                                true)))))
                    .extensions(Map.of("x-cb-is-composite-array-request-body", true)))
            .withResponse(resourceResponseParam("customer", customer))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
package customer

type Customer struct {
\tConsents map[string]interface{} `json:"consents"`
\tObject   string                 `json:"object"`
}
type CreateRequestParams struct {
\tEntityIdentifiers []*CreateEntityIdentifierParams `json:"entity_identifiers,omitempty"`
}
type CreateEntityIdentifierParams struct {
\tId string `json:"id,omitempty"`
}""");
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package invoice

        type Invoice struct {
        \tObject string `json:"object"`
        }
        type CreateForChargeItemsAndChargesRequestParams struct {
        \tCharges []*CreateForChargeItemsAndChargesChargeParams `json:"charges,omitempty"`
        }
        type CreateForChargeItemsAndChargesChargeParams struct {
        \tAvalaraSaleType enum.AvalaraSaleType `json:"avalara_sale_type,omitempty"`
        }""");
  }

  @Test
  void shouldSupportEnumsInListMultiAttributesInRequestBodyParams() throws IOException {
    var line_item =
        buildResource("line_items")
            .withAttribute("tax_amount", new IntegerSchema().type("integer").format("int64"))
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package invoice

        type Invoice struct {
        \tLineItems []*LineItem `json:"line_items"`
        \tObject    string      `json:"object"`
        }
        type LineItem struct {
        \tTaxAmount int64  `json:"tax_amount"`
        \tObject    string `json:"object"`
        }
        type ImportInvoiceRequestParams struct {
        \tLineItems []*ImportInvoiceLineItemParams `json:"line_items,omitempty"`
        }
        """);
  }

  @Test
  void shouldGenerateResultFileInGoDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(3), basePath, "result.go");
  }

  @Test
  void resultFileShouldBeUnderPackageChargebee() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertThat(writeStringFileOp.fileContent).startsWith("package chargebee");
  }

  @Test
  void resultFileShouldHaveResourceResponsesInImports() throws IOException {
    var spec = buildSpec().withResources("virtual_bank_account").done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            """
            package chargebee

            import (
                "github.com/chargebee/chargebee-go/v3/models/virtualbankaccount"
                "net/http"
                "strconv"
            )
            """);
  }

  @Test
  void resultFileShouldHaveStructAttributeForResourceResponses() throws IOException {
    var spec = buildSpec().withResources("customer", "virtual_bank_account").done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertGoResultFileContent(
        writeStringFileOp,
        """
        import (
            "github.com/chargebee/chargebee-go/v3/models/customer"
            "github.com/chargebee/chargebee-go/v3/models/virtualbankaccount"
            "net/http"
            "strconv"
        )""",
        """
type Result struct {
    Customer    *customer.Customer   `json:"customer,omitempty"`
    VirtualBankAccount    *virtualbankaccount.VirtualBankAccount   `json:"virtual_bank_account,omitempty"`
    responseHeaders http.Header
    httpStatusCode int
}""");
  }

  @Test
  void resultBaseFileShouldHaveClassDefnsForListResponses() throws IOException {
    var unbilledCharge =
        buildResource("unbilled_charge").withAttribute("subscription_id", true).done();
    var lineItems = buildResource("line_items").done();
    var discounts = buildResource("discounts").done();
    var invoice =
        buildResource("invoice")
            .withAttribute("id", true)
            .withSubResourceAttribute("line_items", lineItems)
            .withSubResourceAttribute("discounts", discounts)
            .done();
    var operation =
        buildPostOperation("invoice_unbilled_charges")
            .forResource("unbilled_charge")
            .withResponse(
                resourceResponseParam(
                    "invoices", new ArraySchema().items(new Schema().$ref("Invoice"))))
            .done();

    var spec =
        buildSpec()
            .withResources(unbilledCharge, invoice)
            .withOperation("/unbilled_charges/invoice_unbilled_charges", operation)
            .done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertGoResultFileContent(
        writeStringFileOp,
        """
        import (
            "github.com/chargebee/chargebee-go/v3/models/invoice"
            "github.com/chargebee/chargebee-go/v3/models/unbilledcharge"
            "net/http"
            "strconv"
        )""",
        """
type Result struct {
    Invoice    *invoice.Invoice   `json:"invoice,omitempty"`
    UnbilledCharge    *unbilledcharge.UnbilledCharge   `json:"unbilled_charge,omitempty"`
    Invoices    []*invoice.Invoice   `json:"invoices,omitempty"`
    responseHeaders http.Header
    httpStatusCode int
}""");
  }

  @Test
  void shouldCreateModelsDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(2), basePath, "/models");
  }

  @Test
  void shouldCreateResourceDirectoriesUnderModelsDirectory() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(4), modelsDirectoryPath, "customer");
  }

  @Test
  void shouldCreateEnumDirectoryIfResourceHasEnums() throws IOException {
    var customer =
        buildResource("customer")
            .withEnumAttribute("card_status", List.of("NO_CARD", "VALID"), true)
            .withDeprecatedAttributes(List.of("card_status"))
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(5), modelsDirectoryPath + "/customer", "enum");
  }

  @Test
  void shouldCreateEnumFileUnderEnumDirectoryIfResourceHasEnums() throws IOException {
    var customer =
        buildResource("customer")
            .withEnumAttribute("card_status", List.of("NO_CARD", "VALID"), true)
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(6), modelsDirectoryPath + "/customer/enum", "card_status.go");
  }

  @Test
  void shouldCreateResourceFileInsideResourceDirectoriesUnderModelsDirectory() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(5), "/go/models/customer", "customer.go");
  }

  @Test
  void shouldHavePossibleEnumValues() throws IOException {
    var customer =
        buildResource("customer")
            .withEnumAttribute("pii_cleared", List.of("active", "cleared"), false)
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertGoModelEnumFileContent(
        writeStringFileOp,
        """
        package enum

        type PiiCleared string

        const (
            PiiClearedActive PiiCleared = "active"
            PiiClearedCleared PiiCleared = "cleared"
        )""");
  }

  @Test
  void shouldHaveDeprecatedEnumValues() throws IOException {
    var customer =
        buildResource("customer")
            .withEnumAttribute("card_status", List.of("no_card", "valid"), true)
            .withDeprecatedAttributes(List.of("card_status"))
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertGoModelEnumFileContent(
        writeStringFileOp,
        """
        package enum

        type CardStatus string

        const (
            CardStatusNoCard CardStatus = "no_card"
            CardStatusValid CardStatus = "valid"
        )""");
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

    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(7);
    assertGoModelFileContent(
        writeStringFileOp,
        """
        package customer

         import (
             "github.com/chargebee/chargebee-go/v3/filter"
         )

         type Customer struct {
             Id       string                 `json:"id"`
             Consents map[string]interface{} `json:"consents"`
             Object   string                 `json:"object"`
         }
         type ListRequestParams struct {
             FirstName         *ListFirstNameParams        `json:"first_name,omitempty"`
             AutoCollection    *ListAutoCollectionParams   `json:"auto_collection,omitempty"`
             CreatedAt         *ListCreatedAtParams        `json:"created_at,omitempty"`
             AutoCloseInvoices *ListAutoCloseInvoiceParams `json:"auto_close_invoices,omitempty"`
             SortBy            *ListSortByParams           `json:"sort_by,omitempty"`
         }
         type ListAutoCollectionParams struct {

         }

         type ListAutoCloseInvoicesParams struct {

         }

         type ListCreatedAtParams struct {

         }

         type ListSortByParams struct {

         }

         type ListFirstNameParams struct {

         }


        """);
  }

  @Test
  void shouldSupportSchemalessGlobalEnum() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var customer =
        buildResource("customer")
            .withAttribute("id", true)
            .withEnumAttribute(autoCollection)
            .done();
    var card =
        buildResource("card").withEnumAttribute(autoCollection).withAttribute("cvv", false).done();
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
    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(9);
    assertGoModelFileContent(
        writeStringFileOp,
        "packageenumtypePreferredSchemestringconst(PreferredSchemePlanPreferredScheme=\"plan\"PreferredSchemePlanSetupPreferredScheme=\"plan_setup\")");
  }

  @Test
  void shouldSupportSchemalessGlobalEnumNegativeBranch() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var customer =
        buildResource("customer")
            .withAttribute("id", true)
            .withEnumAttribute(autoCollection)
            .done();
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
                                        true,
                                        "x-cb-meta-model-name",
                                        "cards")))))
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
                                        true,
                                        "x-cb-meta-model-name",
                                        "abc")))))
            .withRequestBody("id", new StringSchema(), true)
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();

    var spec =
        buildSpec()
            .withTwoResources(customer, card)
            .withPostOperation("/customer/create", createCustomer)
            .done();
    List<FileOp> fileOps = goSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(10);
    assertGoModelFileContent(
        writeStringFileOp,
        "packageenumtypeAutocollectionstringconst(AutocollectionOnAutocollection=\"On\"AutocollectionOffAutocollection=\"Off\")");
  }
}
