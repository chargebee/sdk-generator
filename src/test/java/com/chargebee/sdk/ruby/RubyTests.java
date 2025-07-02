package com.chargebee.sdk.ruby;

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

public class RubyTests extends LanguageTests {

  public static Ruby rubySdkGen;

  private final String basePath = "/ruby/lib/chargebee";

  private final String modelsDirectoryPath = "/ruby/lib/chargebee/models";

  void assertRubyModelFileContent(FileOp.WriteString fileOp, String body) {
    assertThat(fileOp.fileContent).startsWith("module ChargeBee\n" + body);
  }

  void assertRubyResultFileContent(FileOp.WriteString fileOp, String body) throws IOException {
    String expectedContent =
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/ruby/samples/resultTemplate.txt")
            .replace("__body__", body);
    assertThat(fileOp.fileContent).isEqualTo(expectedContent);
  }

  @BeforeAll
  static void beforeAll() {
    rubySdkGen = new Ruby();
  }

  @Test
  void baseDirectoryShouldNotBeClearedBeforeGeneration() {
    assertThat(rubySdkGen.cleanDirectoryBeforeGenerate()).isFalse();
  }

  @Test
  void shouldCreateModelsDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(0), basePath, "/models");
  }

  @Test
  void shouldCreateResourceFileInModelsDirectory() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(1), modelsDirectoryPath, "customer.rb");
  }

  @Test
  void eachResourceShouldHaveSeparateDeclaration() throws IOException {
    var spec = buildSpec().withResources("customer", "advance_invoice_schedule").done();

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(1), modelsDirectoryPath, "advance_invoice_schedule.rb");
    assertWriteStringFileOp(fileOps.get(2), modelsDirectoryPath, "customer.rb");
  }

  @Test
  void eachResourceShouldHaveSpecifiedResourceClass() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(writeStringFileOp, "  class Customer < Model");
  }

  @Test
  void shouldIgnoreHiddenFromSDKResources() throws IOException {
    var customer = buildResource("customer").done();
    var advance_invoice_schedule =
        buildResource("advance_invoice_schedule").asHiddenFromSDKGeneration().done();
    var spec = buildSpec().withResources(customer, advance_invoice_schedule).done();

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(writeStringFileOp, "  class Customer < Model");
  }

  @Test
  void shouldContainDependentResources() throws IOException {
    var advance_invoice_schedule =
        buildResource("advance_invoice_schedule").asHiddenFromSDKGeneration().done();
    var credit_note = buildResource("credit_note").asDependentResource().done();
    var spec = buildSpec().withResources(advance_invoice_schedule, credit_note).done();

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(writeStringFileOp, "  class CreditNote < Model");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
          class Customer < Model

          attr_accessor :id, :first_name, :last_name
        """);
  }

  @Test
  void fieldsEntriesShouldFollowCharacterConstraintForEachLine() throws IOException {
    var resource =
        buildResource("address")
            .withAttribute("label")
            .withAttribute("first_name")
            .withAttribute("last_name")
            .withAttribute("email")
            .withAttribute("company")
            .withAttribute("phone")
            .withAttribute("addr")
            .withAttribute("extended_addr")
            .withAttribute("extended_addr2")
            .withAttribute("city")
            .withAttribute("state_code")
            .withAttribute("state")
            .withAttribute("country")
            .withAttribute("zip")
            .withAttribute("validation_status")
            .withAttribute("subscription_id")
            .done();
    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Address < Model

  attr_accessor :label, :first_name, :last_name, :email, :company, :phone, :addr, :extended_addr,
  :extended_addr2, :city, :state_code, :state, :country, :zip, :validation_status, :subscription_id\
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
          class Customer < Model

            class BillingAddress < Model\
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
          class Customer < Model

            class BillingAddress < Model
              attr_accessor :first_name
            end

            class PaymentMethod < Model
              attr_accessor :type
            end

          attr_accessor :billing_address, :payment_method, :first_name\
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.retrieve(id, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('get', uri_path("customers",id.to_s), {}, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.clear_personal_data(id, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('post', uri_path("customers",id.to_s,"clear_personal_data"), {}, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.hierarchy(id, params, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('get', uri_path("customers",id.to_s,"hierarchy"), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.merge(params, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('post', uri_path("customers","merge"), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Quote < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.create_for_charge_items_and_charges(params, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('post', uri_path("quotes","create_for_charge_items_and_charges"), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Quote
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Quote < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.create_for_charge_items_and_charges(params, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('post', uri_path("quotes","create_for_charge_items_and_charges"), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Quote
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.list(params={}, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send_list_request('get', uri_path("customers"), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.create(params={}, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('post', uri_path("customers"), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.retrieve(id, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('get', uri_path("customers",id.to_s), {}, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(modelsDirectoryPath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.create(params={}, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('post', uri_path("customers"), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
  }

  @Test
  void sendListRequestMethodOnlyForListOperationNamedList() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("limit", new IntegerSchema())
            .withQueryParam("offset")
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", operation).done();

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.list(params={}, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send_list_request('get', uri_path("customers"), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.contacts_for_customer(id, params={}, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('get', uri_path("customers",id.to_s,"contacts"), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.contacts_for_customer(id, params={}, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('get', uri_path("customers",id.to_s,"contacts"), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.update_payment_method(id, params, env=nil, headers={})
    jsonKeys = {\s
        :type => 1,
    }
    options = {}
    Request.send('post', uri_path("customers",id.to_s,"update_payment_method"), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertWriteStringFileOp(fileOps.get(1), modelsDirectoryPath, "customer.rb");
    assertRubyModelFileContent(
        writeStringFileOp,
        """
  class Customer < Model

  attr_accessor :id

  # OPERATIONS
  #-----------

  def self.create(params={}, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('post', uri_path("customers"), params, env, headers,nil, false, jsonKeys, options)
  end

  def self.update(id, params={}, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('post', uri_path("customers",id.to_s), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(1),
        modelsDirectoryPath,
        "customer.rb",
        """
module ChargeBee
  class Customer < Model

    class BillingAddress < Model
      attr_accessor :first_name
    end

    class PaymentMethod < Model
      attr_accessor :type
    end

  attr_accessor :id, :first_name, :billing_address, :payment_method

  # OPERATIONS
  #-----------

  def self.create(params={}, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('post', uri_path("customers"), params, env, headers,nil, false, jsonKeys, options)
  end

  def self.update(id, params={}, env=nil, headers={})
    jsonKeys = {\s
    }
    options = {}
    Request.send('post', uri_path("customers",id.to_s), params, env, headers,nil, false, jsonKeys, options)
  end

  end # ~Customer
end # ~ChargeBee""");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        "  class Event < Model\n"
            + "\n"
            + "  attr_accessor :id\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/ruby/event.rb.hbs")
            + "\n"
            + "  # OPERATIONS\n"
            + "  #-----------\n"
            + "\n"
            + "  def self.retrieve(id, env=nil, headers={})\n");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        "  class Export < Model\n"
            + "\n"
            + "  attr_accessor :id\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/ruby/export.rb.hbs")
            + "\n"
            + "  # OPERATIONS\n"
            + "  #-----------\n"
            + "\n"
            + "  def self.retrieve(id, env=nil, headers={})\n");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        "  class HostedPage < Model\n"
            + "\n"
            + "  attr_accessor :id\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/ruby/hostedPage.rb.hbs")
            + "\n"
            + "  # OPERATIONS\n"
            + "  #-----------\n"
            + "\n");
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertRubyModelFileContent(
        writeStringFileOp,
        "  class TimeMachine < Model\n"
            + "\n"
            + "  attr_accessor :name\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/ruby/timeMachine.rb.hbs")
            + "\n"
            + "  # OPERATIONS\n"
            + "  #-----------\n"
            + "\n");
  }

  @Test
  void shouldGenerateResultFileInChargebeeDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(1), basePath, "result.rb");
  }

  @Test
  void resultFileShouldHaveImportStatementsAndBareBone() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            """
            module ChargeBee
              class Result

                IDEMPOTENCY_REPLAYED_HEADER = :chargebee_idempotency_replayed

                def initialize(response, rheaders = nil, http_status_code=nil)
                  @response = response
                    @rheaders = rheaders
                    @http_status_code = http_status_code
                end

                def get_response_headers()
                    @rheaders
                end

                def get_raw_response()
                    @response
                end

                def get_http_status_code()
                    @http_status_code
                end

                def is_idempotency_replayed()
                    replayed_header = get_response_headers[IDEMPOTENCY_REPLAYED_HEADER]
                    if replayed_header != nil
                       return !!replayed_header
                    else
                       return false
                    end
                end
            """);
  }

  @Test
  void resultFileShouldHaveResourceClassDefinition() throws IOException {
    var spec = buildSpec().withResources("virtual_bank_account").done();

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertRubyResultFileContent(
        writeStringFileOp,
        """
            def virtual_bank_account()\s
                virtual_bank_account = get(:virtual_bank_account, VirtualBankAccount);
                return virtual_bank_account;
            end\
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertRubyResultFileContent(
        writeStringFileOp,
        """
            def credit_note()\s
                credit_note = get(:credit_note, CreditNote,
                {:einvoice => CreditNote::Einvoice, :line_items => CreditNote::LineItem});
                return credit_note;
            end\
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertRubyResultFileContent(
        writeStringFileOp,
        """
    def gift()\s
        gift = get(:gift, Gift,
        {:gifter => Gift::Gifter, :gift_receiver => Gift::GiftReceiver, :gift_timelines => Gift::GiftTimeline});
        return gift;
    end\
""");
  }

  @Test
  void resourcesShouldFollowSortOrderInResultFile() throws IOException {
    var token = buildResource("token").withSortOrder(5).done();
    var discount = buildResource("discount").withSortOrder(2).done();
    var spec = buildSpec().withResources(token, discount).done();

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertRubyResultFileContent(
        writeStringFileOp,
        """
            def discount()\s
                discount = get(:discount, Discount);
                return discount;
            end

            def token()\s
                token = get(:token, Token);
                return token;
            end\
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertRubyResultFileContent(
        writeStringFileOp,
        """
            def invoice()\s
                invoice = get(:invoice, Invoice,
                {:line_items => Invoice::LineItem, :discounts => Invoice::Discount});
                return invoice;
            end

            def unbilled_charge()\s
                unbilled_charge = get(:unbilled_charge, UnbilledCharge);
                return unbilled_charge;
            end

            def invoices()\s
                invoices = get_list(:invoices, Invoice,
                {:line_items => Invoice::LineItem, :discounts => Invoice::Discount});
                return invoices;
            end\
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

    List<FileOp> fileOps = rubySdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertRubyResultFileContent(
        writeStringFileOp,
        """
    def estimate()\s
        estimate = get(:estimate, Estimate, {},
        {:subscription_estimate => SubscriptionEstimate, :subscription_estimates => SubscriptionEstimate});
        estimate.init_dependant(@response[:estimate], :subscription_estimate,
        {:shipping_address => SubscriptionEstimate::ShippingAddress, :contract_term => SubscriptionEstimate::ContractTerm});
        estimate.init_dependant_list(@response[:estimate], :subscription_estimates,
        {:shipping_address => SubscriptionEstimate::ShippingAddress, :contract_term => SubscriptionEstimate::ContractTerm});
        return estimate;
    end\
""");
  }
}
