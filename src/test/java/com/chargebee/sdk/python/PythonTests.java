package com.chargebee.sdk.python;

import static com.chargebee.sdk.test_data.OperationBuilder.*;
import static com.chargebee.sdk.test_data.ResourceBuilder.buildResource;
import static com.chargebee.sdk.test_data.ResourceResponseParam.resourceResponseParam;
import static com.chargebee.sdk.test_data.SpecBuilder.buildSpec;
import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.LanguageTests;
import com.chargebee.sdk.test_data.OperationWithPath;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PythonTests extends LanguageTests {

  public static Python pythonSdkGen;

  private final String basePath = "/python/chargebee";

  private final String modelsDirectoryPath = "/python/chargebee/models";

  void assertPythonModelFileContent(FileOp.WriteString fileOp, String body) {
    String imports =
        fileOp.fileName.equals("time_machine.py")
            ? "from chargebee import OperationFailedError\n"
            : fileOp.fileName.equals("event.py") ? "from chargebee.main import Environment\n" : "";
    assertThat(fileOp.fileContent)
        .startsWith(
            "import json\n"
                + "from chargebee.model import Model\n"
                + "from chargebee import request\n"
                + "from chargebee import APIError\n"
                + imports
                + "\n"
                + body);
  }

  void assertPythonResultFileContent(FileOp.WriteString fileOp, String body) throws IOException {
    String expectedContent =
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/python/samples/resultTemplate.txt")
            .replace("__body__", body);
    assertThat(fileOp.fileContent).isEqualTo(expectedContent);
  }

  @BeforeAll
  static void beforeAll() {
    pythonSdkGen = new Python();
  }

  @Test
  void baseDirectoryShouldNotBeClearedBeforeGeneration() {
    assertThat(pythonSdkGen.cleanDirectoryBeforeGenerate()).isFalse();
  }

  @Test
  void shouldCreateModelsDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(0), basePath, "/models");
  }

  @Test
  void shouldCreateInitFileInModelsDirectoryWithSpecifiedResources() throws IOException {
    var spec = buildSpec().withResources("customer", "advance_invoice_schedule").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(1),
        modelsDirectoryPath,
        "__init__.py",
        """
        from chargebee.models.advance_invoice_schedule import AdvanceInvoiceSchedule
        from chargebee.models.customer import Customer
        from chargebee.models.content import Content
        """);
  }

  @Test
  void shouldHaveContentResourceByDefaultInInitFile() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(1),
        modelsDirectoryPath,
        "__init__.py",
        "from chargebee.models.content import Content\n");
  }

  @Test
  void shouldCreateResourceFileInModelsDirectory() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), modelsDirectoryPath, "customer.py");
  }

  @Test
  void eachResourceShouldHaveSeparateDeclaration() throws IOException {
    var spec = buildSpec().withResources("customer", "advance_invoice_schedule").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), modelsDirectoryPath, "advance_invoice_schedule.py");
    assertWriteStringFileOp(fileOps.get(3), modelsDirectoryPath, "customer.py");
  }

  @Test
  void eachResourceShouldHaveImportStatements() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            """
            import json
            from chargebee.model import Model
            from chargebee import request
            from chargebee import APIError
            """);
  }

  @Test
  void EventsResourceShouldHaveEnvironmentInImportStatements() throws IOException {
    var spec = buildSpec().withResources("event").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            """
            import json
            from chargebee.model import Model
            from chargebee import request
            from chargebee import APIError
            from chargebee.main import Environment""");
  }

  @Test
  void TimeMachineResourceShouldHaveErrorHandlingInImportStatements() throws IOException {
    var spec = buildSpec().withResources("time_machine").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            """
            import json
            from chargebee.model import Model
            from chargebee import request
            from chargebee import APIError
            from chargebee import OperationFailedError""");
  }

  @Test
  void eachResourceShouldHaveSpecifiedResourceClass() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(writeStringFileOp, "class Customer(Model):");
  }

  @Test
  void shouldIgnoreHiddenFromSDKResources() throws IOException {
    var customer = buildResource("customer").done();
    var advance_invoice_schedule =
        buildResource("advance_invoice_schedule").asHiddenFromSDKGeneration().done();
    var spec = buildSpec().withResources(customer, advance_invoice_schedule).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(writeStringFileOp, "class Customer(Model):");
  }

  @Test
  void shouldContainDependentResources() throws IOException {
    var advance_invoice_schedule =
        buildResource("advance_invoice_schedule").asHiddenFromSDKGeneration().done();
    var credit_note = buildResource("credit_note").asDependentResource().done();
    var spec = buildSpec().withResources(advance_invoice_schedule, credit_note).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(writeStringFileOp, "class CreditNote(Model):");
  }

  @Test
  void shouldCreateResourceFileWithSpecifiedAttributes() throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute("id", true)
            .withAttribute("first_name", true)
            .withAttribute("last_name", true)
            .done();
    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
        class Customer(Model):

            fields = ["id", "first_name", "last_name"]
        """);
  }

  @Test
  void fieldsEntriesShouldFollowCharacterConstraintForEachLine() throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute("id")
            .withAttribute("first_name")
            .withAttribute("last_name")
            .withAttribute("email")
            .withAttribute("phone")
            .withAttribute("company")
            .withAttribute("vat_number")
            .withAttribute("auto_collection")
            .withAttribute("offline_payment_method")
            .withAttribute("net_term_days")
            .done();
    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id", "first_name", "last_name", "email", "phone", "company", "vat_number", "auto_collection", \\
    "offline_payment_method", "net_term_days"]
""");
  }

  @Test
  void shouldCreateResourceFileWithSpecifiedSubResources() throws IOException {
    var billingAddress = buildResource("billing_address").done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .done();
    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
        class Customer(Model):
            class BillingAddress(Model):
        """);
  }

  @Test
  void shouldCreateResourceFileWithSpecifiedSubResourceAttributes() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var paymentMethod = buildResource("payment_method").withAttribute("type").done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .withSubResourceAttribute("payment_method", paymentMethod)
            .withAttribute("first_name", true)
            .done();
    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
        class Customer(Model):
            class BillingAddress(Model):
              fields = ["first_name"]
              pass
            class PaymentMethod(Model):
              fields = ["type"]
              pass

            fields = ["billing_address", "payment_method", "first_name"]
        """);
  }

  @Test
  void noParamsInArgsIfNoQueryParams() throws IOException {
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

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def retrieve(id, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('get', request.uri_path("customers",id), None, env, headers, None, False,json_keys)
""");
  }

  @Test
  void noParamsInArgsIfNoRequestBodyParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id").done();
    var operation =
        buildPostOperation("clear_personal_data")
            .forResource("customer")
            .withPathParam("customer_id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers/{customer-id}/clear_personal_data", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def clear_personal_data(id, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('post', request.uri_path("customers",id,"clear_personal_data"), None, env, headers, None, False,json_keys)
""");
  }

  @Test
  void paramsInArgsIfAtleastOneQueryParamRequired() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildOperation("hierarchy")
            .forResource("customer")
            .withPathParam("customer_id")
            .withQueryParam("hierarchy_operation_type", true)
            .withQueryParam("hierarchy_type")
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}/hierarchy", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def hierarchy(id, params, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('get', request.uri_path("customers",id,"hierarchy"), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void paramsInArgsIfAtleastOneRequestBodyParamRequired() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("merge")
            .forResource("customer")
            .withRequestBody("from_customer_id", new StringSchema(), true)
            .withRequestBody("to_customer_id", new StringSchema(), true)
            .withRequestBody("customer_type", new StringSchema()._enum(List.of()), false)
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers/merge", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def merge(params, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('post', request.uri_path("customers","merge"), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void paramsInArgsIfAtleastOneSubRequestBodyParamIsRequired() throws IOException {
    var quote = buildResource("quote").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create_for_charge_items_and_charges")
            .forResource("quote")
            .withRequestBody("name", new StringSchema(), false)
            .withCompositeArrayRequestBody(
                "discounts",
                new ObjectSchema()
                    .properties(
                        Map.of("apply_on", new StringSchema(), "amount", new IntegerSchema()))
                    .required(List.of("apply_on")))
            .withResponse(resourceResponseParam("quotes", quote))
            .done();

    var spec =
        buildSpec()
            .withResource(quote)
            .withPostOperation("/quotes/create_for_charge_items_and_charges", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Quote(Model):

    fields = ["id"]


    @staticmethod
    def create_for_charge_items_and_charges(params, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('post', request.uri_path("quotes","create_for_charge_items_and_charges"), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void paramsInArgsEvenIfSubRequestBodyParamIsHiddenFromSDKButRequired() throws IOException {
    var quote = buildResource("quote").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create_for_charge_items_and_charges")
            .forResource("quote")
            .withRequestBody("name", new StringSchema(), false)
            .withCompositeArrayRequestBody(
                "discounts",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "apply_on", hiddenFromSDKPropSchema(), "amount", new IntegerSchema()))
                    .required(List.of("apply_on")))
            .withResponse(resourceResponseParam("quotes", quote))
            .done();

    var spec =
        buildSpec()
            .withResource(quote)
            .withPostOperation("/quotes/create_for_charge_items_and_charges", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Quote(Model):

    fields = ["id"]


    @staticmethod
    def create_for_charge_items_and_charges(params, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('post', request.uri_path("quotes","create_for_charge_items_and_charges"), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void paramsWithNoneInArgsIfAllQueryParamsOptional() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("limit")
            .withQueryParam("offset")
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec = buildSpec().withResource(customer).withOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def list(params=None, env=None, headers=None):
        json_keys = {\s
        }
        return request.send_list_request('get', request.uri_path("customers"), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void paramsWithNoneInArgsIfAllRequestBodyParamsOptional() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema())
            .withRequestBody("first_name", new StringSchema())
            .withRequestBody("auto_collection", new StringSchema()._enum(List.of("on", "off")))
            .withRequestBody("net_term_days", new IntegerSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def create(params=None, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('post', request.uri_path("customers"), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void shouldSupportGetAction() throws IOException {
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

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def retrieve(id, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('get', request.uri_path("customers",id), None, env, headers, None, False,json_keys)
""");
  }

  @Test
  void shouldSupportPostAction() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "billing_address",
                new ObjectSchema()
                    .properties(
                        Map.of("first_name", new StringSchema(), "last_name", new StringSchema())))
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(modelsDirectoryPath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def create(params=None, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('post', request.uri_path("customers"), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void sendListRequestMethodOnlyForListOperationNamedList() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("limit", new IntegerSchema())
            .withQueryParam("offset", new StringSchema())
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def list(params=None, env=None, headers=None):
        json_keys = {\s
        }
        return request.send_list_request('get', request.uri_path("customers"), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void NotAllListOperationsShouldHaveSendListRequestMethod() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperation("contacts_for_customer")
            .forResource("customer")
            .withPathParam("customer-id")
            .withQueryParam("limit", new IntegerSchema())
            .withQueryParam("offset", new StringSchema())
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}/contacts", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def contacts_for_customer(id, params=None, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('get', request.uri_path("customers",id,"contacts"), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void shouldSupportGetActionWithPathParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id").done();
    var operation =
        buildOperation("contacts_for_customer")
            .forResource("customer")
            .withPathParam("customer_id")
            .withQueryParam("limit", new IntegerSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}/contacts", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def contacts_for_customer(id, params=None, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('get', request.uri_path("customers",id,"contacts"), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void shouldSupportPostActionWithPathParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id").done();
    var operation =
        buildPostOperation("update_payment_method")
            .forResource("customer")
            .withPathParam("customer_id")
            .withCompositeArrayRequestBody(
                "payment_method",
                new ObjectSchema()
                    .required(List.of("type"))
                    .properties(
                        Map.of("type", new ObjectSchema(), "reference_id", new StringSchema())))
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers/{customer-id}/update_payment_method", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def update_payment_method(id, params, env=None, headers=None):
        json_keys = {\s
            "type": 1,
        }
        return request.send('post', request.uri_path("customers",id,"update_payment_method"), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void shouldFollowSortOrderOfOperations() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var updateOperation =
        buildPostOperation("update")
            .withPathParam("customer_id")
            .forResource("customer")
            .withRequestBody("first_name", new StringSchema())
            .withSortOrder(22)
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withResponse(resourceResponseParam("customer", customer))
            .withRequestBody("id", new StringSchema())
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperations(
                new OperationWithPath("/customers", createOperation),
                new OperationWithPath("/customers/{customer-id}", updateOperation))
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertWriteStringFileOp(fileOps.get(2), modelsDirectoryPath, "customer.py");
    assertPythonModelFileContent(
        writeStringFileOp,
        """
class Customer(Model):

    fields = ["id"]


    @staticmethod
    def create(params=None, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('post', request.uri_path("customers"), params, env, headers, None, False,json_keys)

    @staticmethod
    def update(id, params=None, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('post', request.uri_path("customers",id), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void shouldCreateResourceFileWithSubResources_Fields_Actions() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var paymentMethod = buildResource("payment_method").withAttribute("type").done();
    var customer =
        buildResource("customer")
            .withAttribute("id", true)
            .withAttribute("first_name", false)
            .withSubResourceAttribute("billing_address", billingAddress)
            .withSubResourceAttribute("payment_method", paymentMethod)
            .done();
    var updateOperation =
        buildPostOperation("update")
            .withPathParam("customer_id")
            .forResource("customer")
            .withSortOrder(22)
            .withRequestBody("first_name", new StringSchema())
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperations(
                new OperationWithPath("/customers", createOperation),
                new OperationWithPath("/customers/{customer-id}", updateOperation))
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(2),
        modelsDirectoryPath,
        "customer.py",
        """
import json
from chargebee.model import Model
from chargebee import request
from chargebee import APIError

class Customer(Model):
    class BillingAddress(Model):
      fields = ["first_name"]
      pass
    class PaymentMethod(Model):
      fields = ["type"]
      pass

    fields = ["id", "first_name", "billing_address", "payment_method"]


    @staticmethod
    def create(params=None, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('post', request.uri_path("customers"), params, env, headers, None, False,json_keys)

    @staticmethod
    def update(id, params=None, env=None, headers=None):
        json_keys = {\s
        }
        return request.send('post', request.uri_path("customers",id), params, env, headers, None, False,json_keys)
""");
  }

  @Test
  void shouldHaveSpecifiedSnippetIneEventsResource() throws IOException {
    var event = buildResource("event").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("event")
            .withPathParam("event_id")
            .withResponse(resourceResponseParam("event", event))
            .done();
    var spec =
        buildSpec().withResource(event).withOperation("/events/{event-id}", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        "class Event(Model):\n"
            + "\n"
            + "    fields = [\"id\"]\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/python/event.py.hbs")
            + "\n"
            + "\n"
            + "    @staticmethod\n"
            + "    def retrieve(id, env=None, headers=None):\n");
  }

  @Test
  void shouldHaveSpecifiedSnippetInExportsResource() throws IOException {
    var export = buildResource("export").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("export")
            .withPathParam("export-id")
            .withResponse(resourceResponseParam("export", export))
            .done();
    var spec =
        buildSpec().withResource(export).withOperation("/exports/{export-id}", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        "class Export(Model):\n"
            + "\n"
            + "    fields = [\"id\"]\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/python/export.py.hbs")
            + "\n"
            + "\n"
            + "    @staticmethod\n"
            + "    def retrieve(id, env=None, headers=None):\n");
  }

  @Test
  void shouldHaveSpecifiedSnippetsInHostedPagesResource() throws IOException {
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

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        "class HostedPage(Model):\n"
            + "\n"
            + "    fields = [\"id\"]\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/python/hostedPage.py.hbs")
            + "\n"
            + "\n"
            + "    @staticmethod\n"
            + "    def retrieve(id, env=None, headers=None):\n");
  }

  @Test
  void timeMachineResourceShouldHaveSpecifiedSnippet() throws IOException {
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

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonModelFileContent(
        writeStringFileOp,
        "class TimeMachine(Model):\n"
            + "\n"
            + "    fields = [\"name\"]\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/python/timeMachine.py.hbs")
            + "\n"
            + "\n"
            + "    @staticmethod\n"
            + "    def retrieve(id, env=None, headers=None):\n");
  }

  @Test
  void shouldGenerateResultFileInChargebeeDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), basePath, "result.py");
  }

  @Test
  void resultFileShouldHaveImportStatementsAndBareBone() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            """
            from chargebee.compat import json
            from chargebee.models import *


            class Result(object):

                IDEMPOTENCY_REPLAYED_HEADER = 'chargebee-idempotency-replayed'

                def __init__(self, response, response_header=None, http_status_code=None):
                    self._response = response
                    self._response_obj = {}
                    self._response_header = response_header
                    self._http_status_code = http_status_code

                @property
                def get_response_headers(self):
                    return self._response_header

                @property
                def get_http_status_code(self):
                    return self._http_status_code

                @property
                def is_idempotency_replayed(self):
                    value = self._response_header.get(self.IDEMPOTENCY_REPLAYED_HEADER)
                    if value is not None:
                        return bool(value)
                    else:
                        return False
            """);
  }

  @Test
  void resultFileShouldHaveResourceClassDefinition() throws IOException {
    var spec = buildSpec().withResources("virtual_bank_account").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertPythonResultFileContent(
        writeStringFileOp,
        """
    @property
    def virtual_bank_account(self):
        virtual_bank_account = self._get('virtual_bank_account', VirtualBankAccount);
        return virtual_bank_account;\
""");
  }

  @Test
  void resultFileShouldHaveResourceClassDefinitionWithSubResources() throws IOException {
    var einvoice = buildResource("einvoice").done();
    var line_item = buildResource("line_items").done();
    var credit_note =
        buildResource("credit_note")
            .withSubResourceAttribute("einvoice", einvoice)
            .withSubResourceAttribute("line_items", line_item)
            .done();
    var spec = buildSpec().withResources(credit_note).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertPythonResultFileContent(
        writeStringFileOp,
        """
            @property
            def credit_note(self):
                credit_note = self._get('credit_note', CreditNote,
                {'einvoice' : CreditNote.Einvoice, 'line_items' : CreditNote.LineItem});
                return credit_note;\
        """);
  }

  @Test
  void dependantResourcesShouldNotHaveResourceDefinitionInResultFile() throws IOException {
    var subscription_estimates =
        buildResource("subscription_estimates").asDependentResource().done();
    var gifter = buildResource("gifter").done();
    var giftReceiver = buildResource("gift_receiver").done();
    var giftTimelines = buildResource("gift_timelines").done();
    var gift =
        buildResource("gift")
            .withSubResourceAttribute("gifter", gifter)
            .withSubResourceAttribute("gift_receiver", giftReceiver)
            .withSubResourceAttribute("gift_timelines", giftTimelines)
            .done();
    var spec = buildSpec().withResources(gift, subscription_estimates).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertPythonResultFileContent(
        writeStringFileOp,
        """
    @property
    def gift(self):
        gift = self._get('gift', Gift,
        {'gifter' : Gift.Gifter, 'gift_receiver' : Gift.GiftReceiver, 'gift_timelines' : Gift.GiftTimeline});
        return gift;\
""");
  }

  @Test
  void resourcesShouldFollowSortOrderInResultFile() throws IOException {
    var token = buildResource("token").withSortOrder(5).done();
    var discount = buildResource("discount").withSortOrder(2).done();
    var spec = buildSpec().withResources(token, discount).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertPythonResultFileContent(
        writeStringFileOp,
        """
            @property
            def discount(self):
                discount = self._get('discount', Discount);
                return discount;

            @property
            def token(self):
                token = self._get('token', Token);
                return token;\
        """);
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

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertPythonResultFileContent(
        writeStringFileOp,
        """
            @property
            def invoice(self):
                invoice = self._get('invoice', Invoice,
                {'line_items' : Invoice.LineItem, 'discounts' : Invoice.Discount});
                return invoice;

            @property
            def unbilled_charge(self):
                unbilled_charge = self._get('unbilled_charge', UnbilledCharge);
                return unbilled_charge;

            @property
            def invoices(self):
                invoices = self._get_list('invoices', Invoice,
                {'line_items' : Invoice.LineItem, 'discounts' : Invoice.Discount});
                return invoices;\
        """);
  }

  @Test
  void shouldSupportSingularAndListDependentResources() throws IOException {
    var shipping_address = buildResource("shipping_address").done();
    var contract_term = buildResource("contract_term").done();
    var subscriptionEstimate =
        buildResource("subscription_estimate")
            .asDependentAttribute()
            .asDependentResource()
            .withSubResourceAttribute("shipping_address", shipping_address)
            .withSubResourceAttribute("contract_term", contract_term)
            .done();
    var estimate =
        buildResource("estimate")
            .withSubResourceAttributeReference("subscription_estimate", subscriptionEstimate)
            .withSubResourceArrayAttributeReference("subscription_estimates", subscriptionEstimate)
            .done();
    var spec = buildSpec().withResources(subscriptionEstimate, estimate).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertPythonResultFileContent(
        writeStringFileOp,
        """
    @property
    def estimate(self):
        estimate = self._get('estimate', Estimate, {},
        {'subscription_estimate' : SubscriptionEstimate, 'subscription_estimates' : SubscriptionEstimate});
        estimate.init_dependant(self._response['estimate'], 'subscription_estimate',
        {'shipping_address' : SubscriptionEstimate.ShippingAddress, 'contract_term' : SubscriptionEstimate.ContractTerm});
        estimate.init_dependant_list(self._response['estimate'], 'subscription_estimates',
        {'shipping_address' : SubscriptionEstimate.ShippingAddress, 'contract_term' : SubscriptionEstimate.ContractTerm});
        return estimate;\
""");
  }
}
