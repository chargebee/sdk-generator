package com.chargebee.sdk.ts.typings;

import static com.chargebee.sdk.test_data.EnumBuilder.buildEnum;
import static com.chargebee.sdk.test_data.OperationBuilder.*;
import static com.chargebee.sdk.test_data.ResourceBuilder.buildResource;
import static com.chargebee.sdk.test_data.ResourceResponseParam.resourceResponseParam;
import static com.chargebee.sdk.test_data.SpecBuilder.buildSpec;
import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.openapi.ApiVersion;
import com.chargebee.openapi.ProductCatalogVersion;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.LanguageTests;
import com.chargebee.sdk.ts.typing.TypeScriptTyping;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TypeScriptTypingTests extends LanguageTests {

  public static TypeScriptTyping typeScriptTyping;

  @BeforeAll
  static void beforeAll() {
    typeScriptTyping = new TypeScriptTyping();
  }

  private String coreFileContent(String bodyToAppend) {
    return "declare__module__'chargebee'__{__export__class__RequestConfig__{__site__?:__string__api_key__?:__string__timeout__?:__number__timemachineWaitInMillis__?:__number__exportWaitInMillis__?:__number__}__export__class__ChargebeeRequest<T>__{__setIdempotencyKey(idempotencyKey:__string):__this;__request(config__?:__RequestConfig):__Promise<T>;__headers(headers__:__{[key__:__string]__:__string}):__this;__}"
        + (bodyToAppend.equals("") ? "" : "__" + bodyToAppend)
        + "__}";
  }

  protected void assertWriteStringFileOp(
      FileOp fileOp,
      String expectedBaseFilePath,
      String expectedFileName,
      String expectedFileContent) {
    assertThat(fileOp).isInstanceOf(FileOp.WriteString.class);
    var writeStringFileOp = (FileOp.WriteString) fileOp;
    assertThat(writeStringFileOp.baseFilePath).isEqualTo(expectedBaseFilePath);
    assertThat(writeStringFileOp.fileName).isEqualTo(expectedFileName);
    if (!writeStringFileOp.fileName.equals("index.d.ts")
        && !writeStringFileOp.fileName.equals("core.d.ts")) {
      assertThat(writeStringFileOp.fileContent).startsWith("///<reference path='./../core.d.ts'/>");
    }
    // Replacing whitespace characters with __ for easier testing.
    assertThat(
            writeStringFileOp
                .fileContent
                .replaceFirst("///<reference path='./../core.d.ts'/>", "")
                .replaceFirst("///<reference path='./../index.d.ts'/>", "")
                .trim()
                .replaceAll("\\s+", "__"))
        .isEqualTo(expectedFileContent);
  }

  @Test
  void shouldCreateResourcesDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(3);
    assertCreateDirectoryFileOp(fileOps.get(0), "/tmp", "/resources");
  }

  @Test
  void shouldWriteCoreFile() throws IOException {
    var spec = buildSpec().done();
    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(3);
    assertWriteStringFileOp(fileOps.get(1), "/tmp", "core.d.ts", coreFileContent(""));
  }

  @Test
  void shouldWriteIndexFile() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(3);
    assertWriteStringFileOp(
        fileOps.get(2),
        "/tmp",
        "index.d.ts",
        "declare__module__'chargebee'__{__export__default__class__{__static__configure({__site,__api_key__}:__{__site:__string;__api_key:__string__}):__void;__}__}");
  }

  @Test
  void eachResourceShouldHaveSeparateDeclaration() throws IOException {
    var spec = buildSpec().withResources("customer", "address").done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Address.d.ts",
        "declare__module__'chargebee'__{__export__interface__Address__{__}__}");
    assertWriteStringFileOp(
        fileOps.get(2),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__}__}");
    assertThat(fileOps.get(4)).isInstanceOf(FileOp.WriteString.class);
    FileOp.WriteString writeIndex = (FileOp.WriteString) fileOps.get(4);
    assertThat(writeIndex.fileName).isEqualTo("index.d.ts");
    assertThat(writeIndex.baseFilePath).isEqualTo("/tmp");
    assertThat(writeIndex.fileContent)
        .startsWith(
            "///<reference path='./resources/Address.d.ts' />\n"
                + "///<reference path='./resources/Customer.d.ts' />");
  }

  @Test
  void shouldIgnoreHiddenFromSDKResources() throws IOException {
    var customFieldConfig = buildResource("custom_field_config").asHiddenFromSDKGeneration().done();
    var spec = buildSpec().withResource(customFieldConfig).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(3);
  }

  @Test
  void ifResourceHasProductCatalogVersionItWillShowUpOnlyIfSpecAlsoFromPCVersion()
      throws IOException {
    var resource =
        buildResource("plan").withProductCatalogVersion(ProductCatalogVersion.PC1).done();
    var spec =
        buildSpec()
            .withVersion(ApiVersion.V2, ProductCatalogVersion.PC1)
            .withResource(resource)
            .done();
    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Plan.d.ts",
        "declare__module__'chargebee'__{__export__interface__Plan__{__}__}");
  }

  @Test
  void ifResourceHasProductCatalogVersionItWillNotShowIfSpecHasDifferentPCVersion()
      throws IOException {
    var resource =
        buildResource("plan").withProductCatalogVersion(ProductCatalogVersion.PC1).done();
    var spec =
        buildSpec()
            .withVersion(ApiVersion.V2, ProductCatalogVersion.PC2)
            .withResource(resource)
            .done();
    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(3);
    assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    assertThat(fileOps.get(1)).isInstanceOf(FileOp.WriteString.class);
    assertThat(((FileOp.WriteString) fileOps.get(1)).fileName).isEqualTo("core.d.ts");
    assertThat(fileOps.get(2)).isInstanceOf(FileOp.WriteString.class);
    assertThat(((FileOp.WriteString) fileOps.get(2)).fileName).isEqualTo("index.d.ts");
  }

  @Test
  void eachStringAttributesOfResourceShouldBeAvailableInResourceInterface() throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute("id", true)
            .withAttribute("first_name")
            .withAttribute("last_name")
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__first_name?:string;__last_name?:string;__}__}");
  }

  @Test
  void eachAttributeOfResourceShouldHaveDescriptionAvailableInResourceInterface()
      throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute(
                "first_name",
                new StringSchema()
                    .description(
                        "First name of the <a"
                            + " href=\"https://apidocs.chargebee.com/docs/api/customers\">customer</a>"))
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__/**__*__@description__First__name__of__the__[customer](https://apidocs.chargebee.com/docs/api/customers)__*/__first_name?:string;__}__}");
  }

  @Test
  void passwordAndEmailAttributesShouldBeTreatedAsString() throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute("id", true)
            .withEmailAttribute("email")
            .withPasswordAttribute("password")
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__email?:string;__password?:string;__}__}");
  }

  @Test
  void shouldSupportNumberType() throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute("net_term_days", new IntegerSchema(), true)
            .withAttribute(
                "created_at", new IntegerSchema().format("unix-time").pattern("^[0-9]{10}$"))
            .withAttribute("discount", new NumberSchema().format("double"))
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__net_term_days:number;__created_at?:number;__discount?:number;__}__}");
  }

  @Test
  void shouldSupportCustomFields() throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute("net_term_days", new IntegerSchema(), true)
            .withCustomFieldSupport()
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__[key__:__string]__:__any;__net_term_days:number;__}__}");
  }

  @Test
  void shouldSupportBooleanType() throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute("allow_direct_debit", new BooleanSchema(), true)
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__allow_direct_debit:boolean;__}__}");
  }

  @Test
  void shouldSupportArrayType() throws IOException {
    var resource =
        buildResource("purchase")
            .withAttribute("subscription_ids", new ArraySchema().items(new StringSchema()), true)
            .withAttribute("invoice_ids", new ArraySchema().items(new StringSchema()))
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Purchase.d.ts",
        "declare__module__'chargebee'__{__export__interface__Purchase__{__subscription_ids:string[];__invoice_ids?:string[];__}__}");
  }

  @Test
  void shouldSupportJsonObjectAndJsonArrayType() throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute("metadata", new MapSchema().additionalProperties(true), true)
            .withAttribute("exemption_details", new ArraySchema().items(new Schema<>()), true)
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__metadata:object;__exemption_details:any[];__}__}");
  }

  @Test
  void shouldTurnGetOperationIntoAMethodInResource() throws IOException {
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

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Card.d.ts",
        "declare__module__'chargebee'__{__export__interface__Card__{__id:string;__last4:string;__}__}");
    assertWriteStringFileOp(
        fileOps.get(2),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__email:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__retrieve(customer_id:string):ChargebeeRequest<RetrieveResponse>;__}__export__interface__RetrieveResponse__{__customer:Customer;__card?:Card;__}__}__}");
    assertWriteStringFileOp(
        fileOps.get(4),
        "/tmp",
        "index.d.ts",
        "///<reference__path='./resources/Card.d.ts'__/>__///<reference__path='./resources/Customer.d.ts'__/>__declare__module__'chargebee'__{__export__default__class__{__static__configure({__site,__api_key__}:__{__site:__string;__api_key:__string__}):__void;__static__customer:__Customer.CustomerResource;__}__}");
  }

  @Test
  void shouldTurnGetOperationQueryParamsIntoMethodParamInResource() throws IOException {
    var address = buildResource("address").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("address")
            .withQueryParam("subscription_id", true)
            .withQueryParam("label")
            .withResponse(resourceResponseParam("address", address))
            .done();
    var spec = buildSpec().withResource(address).withOperation("/addresses", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Address.d.ts",
        "declare__module__'chargebee'__{__export__interface__Address__{__id:string;__}__export__namespace__Address__{__export__class__AddressResource__{__retrieve(input:RetrieveInputParam):ChargebeeRequest<RetrieveResponse>;__}__export__interface__RetrieveResponse__{__address:Address;__}__export__interface__RetrieveInputParam__{__subscription_id:string;__label?:string;__}__}__}");
  }

  @Test
  void shouldSupportGetOperationWithBothPathParamAndQueryParam() throws IOException {
    var usage = buildResource("usage").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("usage")
            .withPathParam("subscription_id")
            .withQueryParam("id", true)
            .withResponse(resourceResponseParam("usage", usage))
            .done();
    var spec = buildSpec().withResource(usage).withOperation("/usages", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Usage.d.ts",
        "declare__module__'chargebee'__{__export__interface__Usage__{__id:string;__}__export__namespace__Usage__{__export__class__UsageResource__{__retrieve(subscription_id:string,__input:RetrieveInputParam):ChargebeeRequest<RetrieveResponse>;__}__export__interface__RetrieveResponse__{__usage:Usage;__}__export__interface__RetrieveInputParam__{__id:string;__}__}__}");
  }

  @Test
  void ifAllQueryParamsAreOptionalThenMethodParameterShouldBeOptional() throws IOException {
    var address = buildResource("address").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("address")
            .withQueryParam("subscription_id")
            .withQueryParam("label")
            .withResponse(resourceResponseParam("address", address))
            .done();
    var spec = buildSpec().withResource(address).withOperation("/addresses", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Address.d.ts",
        "declare__module__'chargebee'__{__export__interface__Address__{__id:string;__}__export__namespace__Address__{__export__class__AddressResource__{__retrieve(input?:RetrieveInputParam):ChargebeeRequest<RetrieveResponse>;__}__export__interface__RetrieveResponse__{__address:Address;__}__export__interface__RetrieveInputParam__{__subscription_id?:string;__label?:string;__}__}__}");
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

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__hierarchy(customer_id:string):ChargebeeRequest<HierarchyResponse>;__}__export__interface__HierarchyResponse__{__hierarchies:Hierarchy[];__}__}__}");
    assertWriteStringFileOp(
        fileOps.get(2),
        "/tmp/resources",
        "Hierarchy.d.ts",
        "declare__module__'chargebee'__{__export__interface__Hierarchy__{__id:string;__}__}");
  }

  @Test
  void shouldGenerateTypesForAllGlobalEnums() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var channel = buildEnum("Channel", List.of("app_store", "web", "play_store")).done();
    var spec = buildSpec().withEnums(autoCollection, channel).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(3);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp",
        "core.d.ts",
        coreFileContent(
            "type__AutoCollection__=__'On'__|__'Off'__type__Channel__=__'app_store'__|__'web'__|__'play_store'"));
  }

  @Test
  void shouldIgnoreDeprecatedEnumValues() throws IOException {
    var autoCollection =
        buildEnum("AutoCollection", List.of("On", "Pause", "Off"))
            .withDeprecatedValues(List.of("Pause"))
            .done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(3);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp",
        "core.d.ts",
        coreFileContent("type__AutoCollection__=__'On'__|__'Off'"));
  }

  @Test
  void resourcesCanReferGlobalEnums() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off"));
    var customer =
        buildResource("customer")
            .withEnumRefAttribute("auto_collection", autoCollection.typeName)
            .done();
    var spec = buildSpec().withEnums(autoCollection.done()).withResource(customer).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__auto_collection?:AutoCollection;__}__}");
    assertWriteStringFileOp(
        fileOps.get(2),
        "/tmp",
        "core.d.ts",
        coreFileContent("type__AutoCollection__=__'On'__|__'Off'"));
  }

  @Test
  void resourcesCanHaveInlineEnums() throws IOException {
    var customer =
        buildResource("customer").withEnumAttribute("auto_collection", List.of("on", "off")).done();
    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__auto_collection?:'on'__|__'off';__}__}");
    assertWriteStringFileOp(fileOps.get(2), "/tmp", "core.d.ts", coreFileContent(""));
  }

  @Test
  void shouldGenerateTypesForSubResourcesAttributesOfResourceAndReferTheSame() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("country").done();
    var paymentMethod = buildResource("payment_method").withAttribute("reference_id").done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .withSubResourceAttribute("payment_method", paymentMethod)
            .done();

    var spec = buildSpec().withEnums(customer).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__billing_address?:Customer.BillingAddress;__payment_method?:Customer.PaymentMethod;__}__export__namespace__Customer__{__export__interface__BillingAddress__{__country?:string;__}__export__interface__PaymentMethod__{__reference_id?:string;__}__}__}");
  }

  @Test
  void shouldGenerateTypesForArrayOfSubResourcesAttributesOfResourceAndReferTheSame()
      throws IOException {
    var contact = buildResource("contact").withAttribute("first_name").done();
    var customer =
        buildResource("customer").withSubResourceArrayAttribute("contacts", contact).done();

    var spec = buildSpec().withEnums(customer).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__contacts?:Customer.Contact[];__}__export__namespace__Customer__{__export__interface__Contact__{__first_name?:string;__}__}__}");
  }

  @Test
  void subResourceAttributeCanReferGlobalResource() throws IOException {
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

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Estimate.d.ts",
        "declare__module__'chargebee'__{__export__interface__Estimate__{__subscription_estimate?:SubscriptionEstimate;__subscription_estimates?:SubscriptionEstimate[];__}__}");
    assertWriteStringFileOp(
        fileOps.get(2),
        "/tmp/resources",
        "SubscriptionEstimate.d.ts",
        "declare__module__'chargebee'__{__export__interface__SubscriptionEstimate__{__id:string;__}__}");
  }

  @Test
  void shouldSupportListOperationResponses() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var card = buildResource("card").withAttribute("id", true).done();
    var listCustomerOperation =
        buildListOperation("list")
            .forResource("customer")
            .withResponse(
                resourceResponseParam("customer", customer),
                resourceResponseParam("card", card, false))
            .done();

    var spec =
        buildSpec()
            .withResources(customer, card)
            .withOperation("/customers", listCustomerOperation)
            .done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(2),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__list():ChargebeeRequest<ListResponse>;__}__export__interface__ListResponse__{__list:{customer:Customer,card?:Card}[];__next_offset?:string;__}__}__}");
  }

  @Test
  void shouldSupportListOperationInputParam() throws IOException {
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
                        Map.of("is_present", new StringSchema()._enum(List.of("true", "false")))))
            .withQueryParam(
                "relationship",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "parent_id",
                            new ObjectSchema().properties(Map.of("is", new StringSchema())))))
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__list(input?:ListInputParam):ChargebeeRequest<ListResponse>;__}__export__interface__ListResponse__{__list:{customers:Customer}[];__next_offset?:string;__}__export__interface__ListInputParam__{__[key__:__string]:__any;__limit?:number;__offset?:string;__first_name?:{is_present?:'true'__|__'false'};__relationship?:{parent_id?:{is?:string}};__}__}__}");
  }

  @Test
  void shouldSupportPostActionWithRequestBodyContainingPrimitiveParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("email", new StringSchema(), true)
            .withRequestBody("auto_collection", new StringSchema()._enum(List.of("on", "off")))
            .withRequestBody("net_term_days", new IntegerSchema())
            .withRequestBody("allow_direct_debit", new BooleanSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__create(input:CreateInputParam):ChargebeeRequest<CreateResponse>;__}__export__interface__CreateResponse__{__customer:Customer;__}__export__interface__CreateInputParam__{__email:string;__auto_collection?:'on'__|__'off';__net_term_days?:number;__allow_direct_debit?:boolean;__}__}__}");
  }

  @Test
  void shouldSupportPostActionWithRequestBodySupportingCustomFields() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperationThatSupportsCustomFields("create")
            .forResource("customer")
            .withRequestBody("email", new StringSchema(), true)
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__create(input:CreateInputParam):ChargebeeRequest<CreateResponse>;__}__export__interface__CreateResponse__{__customer:Customer;__}__export__interface__CreateInputParam__{__[key__:__string]__:__any;__email:string;__}__}__}");
  }

  @Test
  void shouldSupportPostActionWithRequestBodyContainingObject() throws IOException {
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

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__create(input?:CreateInputParam):ChargebeeRequest<CreateResponse>;__}__export__interface__CreateResponse__{__customer:Customer;__}__export__interface__CreateInputParam__{__billing_address?:{first_name?:string,last_name?:string};__}__}__}");
  }

  @Test
  void shouldSupportPostActionWithRequestBodyContainingObjectArray() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var operation =
        buildPostOperation("createWithItems")
            .forResource("subscription")
            .withCompositeArrayRequestBody(
                "subscription_items",
                new ObjectSchema()
                    .required(List.of("item_price_id"))
                    .properties(
                        Map.of(
                            "item_price_id",
                            new StringSchema(),
                            "unit_price",
                            new IntegerSchema())))
            .withResponse(resourceResponseParam("subscription", subscription))
            .done();

    var spec =
        buildSpec()
            .withResource(subscription)
            .withPostOperation("/subscriptions", operation)
            .done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Subscription.d.ts",
        "declare__module__'chargebee'__{__export__interface__Subscription__{__id:string;__/**__*__@deprecated__metadata__is__deprecated__please__use__meta_data__instead__*/__metadata?:object;__}__export__namespace__Subscription__{__export__class__SubscriptionResource__{__create_with_items(input:CreateWithItemsInputParam):ChargebeeRequest<CreateWithItemsResponse>;__}__export__interface__CreateWithItemsResponse__{__subscription:Subscription;__}__export__interface__CreateWithItemsInputParam__{__subscription_items:{item_price_id:string,unit_price?:number}[];__}__}__}");
  }

  @Test
  void operationShouldHaveDescriptionAvailable() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var operation =
        buildPostOperation("createWithItems", "Create subscription for Items")
            .forResource("subscription")
            .withCompositeArrayRequestBody(
                "subscription_items",
                "Subscription input body",
                new ObjectSchema()
                    .required(List.of("item_price_id"))
                    .properties(
                        Map.of(
                            "item_price_id",
                            new StringSchema(),
                            "unit_price",
                            new IntegerSchema())))
            .withResponse(resourceResponseParam("subscription", subscription))
            .done();

    var spec =
        buildSpec()
            .withResource(subscription)
            .withPostOperation("/subscriptions", operation)
            .done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Subscription.d.ts",
        "declare__module__'chargebee'__{__export__interface__Subscription__{__id:string;__/**__*__@deprecated__metadata__is__deprecated__please__use__meta_data__instead__*/__metadata?:object;__}__export__namespace__Subscription__{__export__class__SubscriptionResource__{__/**__*__@description__Create__subscription__for__Items__*/__create_with_items(input:CreateWithItemsInputParam):ChargebeeRequest<CreateWithItemsResponse>;__}__export__interface__CreateWithItemsResponse__{__subscription:Subscription;__}__export__interface__CreateWithItemsInputParam__{__/**__*__@description__Subscription__input__body__*/__subscription_items:{item_price_id:string,unit_price?:number}[];__}__}__}");
  }

  @Test
  void requestBodyShouldIgnoreHiddenFromSDKProperties() throws IOException {
    var quote = buildResource("quote").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("quote")
            .withRequestBody("invoice_date", new IntegerSchema(), true)
            .withRequestBody("invoice_immediately", hiddenFromSDKPropSchema())
            .withResponse(resourceResponseParam("quote", quote))
            .done();

    var spec = buildSpec().withResource(quote).withPostOperation("/quotes", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Quote.d.ts",
        "declare__module__'chargebee'__{__export__interface__Quote__{__id:string;__}__export__namespace__Quote__{__export__class__QuoteResource__{__create(input:CreateInputParam):ChargebeeRequest<CreateResponse>;__}__export__interface__CreateResponse__{__quote:Quote;__}__export__interface__CreateInputParam__{__invoice_date:number;__}__}__}");
  }

  @Test
  void requestBodyShouldIgnoreHiddenFromSDKPropertiesOfObjectInRequestBody() throws IOException {
    var quote = buildResource("quote").withAttribute("id", true).done();
    var operation =
        buildPostOperation("createForChargeItemsAndCharges")
            .forResource("quote")
            .withCompositeArrayRequestBody(
                "charges",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "discount_amount",
                            hiddenFromSDKPropSchema(),
                            "discount_percentage",
                            hiddenFromSDKPropSchema(),
                            "date_from",
                            new IntegerSchema(),
                            "date_to",
                            new IntegerSchema())))
            .withResponse(resourceResponseParam("quote", quote))
            .done();

    var spec = buildSpec().withResource(quote).withPostOperation("/quotes", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Quote.d.ts",
        "declare__module__'chargebee'__{__export__interface__Quote__{__id:string;__}__export__namespace__Quote__{__export__class__QuoteResource__{__create_for_charge_items_and_charges(input?:CreateForChargeItemsAndChargesInputParam):ChargebeeRequest<CreateForChargeItemsAndChargesResponse>;__}__export__interface__CreateForChargeItemsAndChargesResponse__{__quote:Quote;__}__export__interface__CreateForChargeItemsAndChargesInputParam__{__charges?:{date_from?:number,date_to?:number}[];__}__}__}");
  }
}
