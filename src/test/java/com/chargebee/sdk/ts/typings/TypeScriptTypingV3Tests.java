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
import com.chargebee.sdk.ts.typing.V3.TypeScriptTypings;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TypeScriptTypingV3Tests extends LanguageTests {

  public static TypeScriptTypings typeScriptTyping;

  @BeforeAll
  static void beforeAll() {
    typeScriptTyping = new TypeScriptTypings();
  }

  private String coreFileContent(String bodyToAppend) {
    return "declare__module__'chargebee'__{__export__class__RequestConfig__{__site__?:__string__apiKey__?:__string__timeout__?:__number__timemachineWaitInMillis__?:__number__exportWaitInMillis__?:__number__}__export__type__ChargebeeResponse<T>__=__T__&__{__headers:__{__[key:__string]:__string__};__isIdempotencyReplayed?:__boolean__|__string;__httpStatusCode:__number__|__null;__}__export__type__ChargebeeRequestHeader__=__{__[key:__string]:__string__|__undefined;__'chargebee-idempotency-key'?:__string;__'chargebee-event-email'?:__string;__'chargebee-request-origin-ip'?__:string;__'chargebee-request-origin-user'?:__string;__'chargebee-request-origin-user-encoded'?:__string;__'chargebee-request-origin-device'?:__string;__};"
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
                .replaceFirst("///<reference path='./resources/Content.d.ts' />", "")
                .trim()
                .replaceAll("\\s+", "__"))
        .isEqualTo(expectedFileContent);
  }

  @Test
  void shouldCreateResourcesDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertCreateDirectoryFileOp(fileOps.get(0), "/tmp", "/resources");
  }

  @Test
  void shouldWriteCoreFile() throws IOException {
    var spec = buildSpec().done();
    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(fileOps.get(1), "/tmp", "core.d.ts", coreFileContent(""));
  }

  @Test
  void shouldWriteIndexFile() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(2),
        "/tmp",
        "index.d.ts",
        "export__type__Config__=__{__/**__*__@apiKey__api__key__for__the__site.__*/__apiKey:__string;__/**__*__@site__api__site__name.__*/__site:__string;__/**__*__@apiPath__this__value__indicates__the__api__version,__default__value__is__/api/v2.__*/__apiPath?:__'/api/v2'__|__'/api/v1';__/**__*__@timeout__client__side__request__timeout__in__milliseconds,__default__value__is__80000ms.__*/__timeout?:__number;__/**__*__@port__url__port__*/__port?:__number;__/**__*__@timemachineWaitInMillis__time__interval__at__which__two__subsequent__retrieve__timemachine__call__in__milliseconds,__default__value__is__3000ms.__*/__timemachineWaitInMillis?:__number;__/**__*__@exportWaitInMillis__time__interval__at__which__two__subsequent__retrieve__export__call__in__milliseconds,__default__value__is__3000ms.__*/__exportWaitInMillis?:__number;__/**__*__@protocol__http__protocol,__default__value__is__https__*/__protocol?:__'https'__|__'http';__/**__*__@hostSuffix__url__host__suffix,__default__value__is__.chargebee.com__*/__hostSuffix?:__string;__/**__*__@retryConfig__retry__configuration__for__the__client,__default__value__is__{__enabled:__false,__maxRetries:__3,__delayMs:__1000,__retryOn:__[500,__502,__503,__504]}__*/__retryConfig?:__RetryConfig;__/**__*__@enableDebugLogs__whether__to__enable__debug__logs,__default__value__is__false__*/__enableDebugLogs?:__boolean;__/**__*__@userAgentSuffix__optional__string__appended__to__the__User-Agent__header__for__additional__logging__*/__userAgentSuffix?:__string;__};__export__type__RetryConfig__=__{__/**__*__@enabled__whether__to__enable__retry__logic,__default__value__is__false__*__@maxRetries__maximum__number__of__retries,__default__value__is__3__*__@delayMs__delay__in__milliseconds__between__retries,__default__value__is__1000ms__*__@retryOn__array__of__HTTP__status__codes__to__retry__on,__default__value__is__[500,__502,__503,__504]__*/__enabled?:__boolean;__maxRetries?:__number;__delayMs?:__number;__retryOn?:__Array<number>;__};__declare__module__'chargebee'__{__export__default__class__Chargebee__{__constructor(config:__Config);;__}__}");
  }

  @Test
  void eachResourceShouldHaveSeparateDeclaration() throws IOException {
    var spec = buildSpec().withResources("customer", "address").done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(6);
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

    assertThat(fileOps).hasSize(4);
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

    assertThat(fileOps).hasSize(5);
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

    assertThat(fileOps).hasSize(5);
    assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    assertThat(fileOps.get(1)).isInstanceOf(FileOp.WriteString.class);
    assertThat(((FileOp.WriteString) fileOps.get(2)).fileName).isEqualTo("core.d.ts");
    assertThat(fileOps.get(2)).isInstanceOf(FileOp.WriteString.class);
    assertThat(((FileOp.WriteString) fileOps.get(3)).fileName).isEqualTo("index.d.ts");
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

    assertThat(fileOps).hasSize(5);
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

    assertThat(fileOps).hasSize(5);
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

    assertThat(fileOps).hasSize(5);
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

    assertThat(fileOps).hasSize(5);
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

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__[key__:__string]__:__unknown;__net_term_days:number;__}__}");
  }

  @Test
  void shouldSupportBooleanType() throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute("allow_direct_debit", new BooleanSchema(), true)
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
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

    assertThat(fileOps).hasSize(5);
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
            .withAttribute("metadata", new ObjectSchema().additionalProperties(true), true)
            .withAttribute("exemption_details", new ArraySchema().items(new Schema<>()), true)
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__metadata:any;__exemption_details:any;__}__}");
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

    assertThat(fileOps).hasSize(6);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Card.d.ts",
        "declare__module__'chargebee'__{__export__interface__Card__{__id:string;__last4:string;__}__}");
    assertWriteStringFileOp(
        fileOps.get(2),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__email:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__retrieve(customer_id:string,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<RetrieveResponse>>;__}__export__interface__RetrieveResponse__{__customer:Customer;__card?:Card;__}__//__REQUEST__PARAMS__//---------------__}__}");
    assertWriteStringFileOp(
        fileOps.get(4),
        "/tmp",
        "index.d.ts",
        "///<reference__path='./resources/Card.d.ts'__/>__///<reference__path='./resources/Customer.d.ts'__/>__export__type__Config__=__{__/**__*__@apiKey__api__key__for__the__site.__*/__apiKey:__string;__/**__*__@site__api__site__name.__*/__site:__string;__/**__*__@apiPath__this__value__indicates__the__api__version,__default__value__is__/api/v2.__*/__apiPath?:__'/api/v2'__|__'/api/v1';__/**__*__@timeout__client__side__request__timeout__in__milliseconds,__default__value__is__80000ms.__*/__timeout?:__number;__/**__*__@port__url__port__*/__port?:__number;__/**__*__@timemachineWaitInMillis__time__interval__at__which__two__subsequent__retrieve__timemachine__call__in__milliseconds,__default__value__is__3000ms.__*/__timemachineWaitInMillis?:__number;__/**__*__@exportWaitInMillis__time__interval__at__which__two__subsequent__retrieve__export__call__in__milliseconds,__default__value__is__3000ms.__*/__exportWaitInMillis?:__number;__/**__*__@protocol__http__protocol,__default__value__is__https__*/__protocol?:__'https'__|__'http';__/**__*__@hostSuffix__url__host__suffix,__default__value__is__.chargebee.com__*/__hostSuffix?:__string;__/**__*__@retryConfig__retry__configuration__for__the__client,__default__value__is__{__enabled:__false,__maxRetries:__3,__delayMs:__1000,__retryOn:__[500,__502,__503,__504]}__*/__retryConfig?:__RetryConfig;__/**__*__@enableDebugLogs__whether__to__enable__debug__logs,__default__value__is__false__*/__enableDebugLogs?:__boolean;__/**__*__@userAgentSuffix__optional__string__appended__to__the__User-Agent__header__for__additional__logging__*/__userAgentSuffix?:__string;__};__export__type__RetryConfig__=__{__/**__*__@enabled__whether__to__enable__retry__logic,__default__value__is__false__*__@maxRetries__maximum__number__of__retries,__default__value__is__3__*__@delayMs__delay__in__milliseconds__between__retries,__default__value__is__1000ms__*__@retryOn__array__of__HTTP__status__codes__to__retry__on,__default__value__is__[500,__502,__503,__504]__*/__enabled?:__boolean;__maxRetries?:__number;__delayMs?:__number;__retryOn?:__Array<number>;__};__declare__module__'chargebee'__{__export__default__class__Chargebee__{__constructor(config:__Config);;__customer:__Customer.CustomerResource;__}__}");
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
    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Address.d.ts",
        "declare__module__'chargebee'__{__export__interface__Address__{__id:string;__}__export__namespace__Address__{__export__class__AddressResource__{__retrieve(input:RetrieveInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<RetrieveResponse>>;__}__export__interface__RetrieveResponse__{__address:Address;__}__//__REQUEST__PARAMS__//---------------__export__interface__RetrieveInputParam__{__subscription_id:__string;__label?:__string;__}__}__}");
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
    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Usage.d.ts",
        "declare__module__'chargebee'__{__export__interface__Usage__{__id:string;__}__export__namespace__Usage__{__export__class__UsageResource__{__retrieve(subscription_id:string,input:RetrieveInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<RetrieveResponse>>;__}__export__interface__RetrieveResponse__{__usage:Usage;__}__//__REQUEST__PARAMS__//---------------__export__interface__RetrieveInputParam__{__id:__string;__}__}__}");
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
    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Address.d.ts",
        "declare__module__'chargebee'__{__export__interface__Address__{__id:string;__}__export__namespace__Address__{__export__class__AddressResource__{__retrieve(input?:RetrieveInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<RetrieveResponse>>;__}__export__interface__RetrieveResponse__{__address:Address;__}__//__REQUEST__PARAMS__//---------------__export__interface__RetrieveInputParam__{__subscription_id?:__string;__label?:__string;__}__}__}");
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

    assertThat(fileOps).hasSize(6);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__hierarchy(customer_id:string,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<HierarchyResponse>>;__}__export__interface__HierarchyResponse__{__hierarchies:Hierarchy[];__}__//__REQUEST__PARAMS__//---------------__}__}");
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

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp",
        "core.d.ts",
        coreFileContent(
            "type__AutoCollectionEnum__=__'On'__|__'Off'__type__ChannelEnum__=__'app_store'__|__'web'__|__'play_store'"));
  }

  @Test
  void shouldIgnoreDeprecatedEnumValues() throws IOException {
    var autoCollection =
        buildEnum("AutoCollection", List.of("On", "Pause", "Off"))
            .withDeprecatedValues(List.of("Pause"))
            .done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp",
        "core.d.ts",
        coreFileContent("type__AutoCollectionEnum__=__'On'__|__'Off'"));
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

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__auto_collection?:AutoCollectionEnum;__}__}");
    assertWriteStringFileOp(
        fileOps.get(2),
        "/tmp",
        "core.d.ts",
        coreFileContent("type__AutoCollectionEnum__=__'On'__|__'Off'"));
  }

  @Test
  void resourcesCanHaveInlineEnums() throws IOException {
    var customer =
        buildResource("customer").withEnumAttribute("auto_collection", List.of("on", "off")).done();
    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
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

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__billing_address?:Customer.BillingAddress;__payment_method?:Customer.PaymentMethod;__}__export__namespace__Customer__{__export__interface__BillingAddress__{__country?:string;__}__export__interface__PaymentMethod__{__reference_id?:string;__}__//__REQUEST__PARAMS__//---------------__}__}");
  }

  @Test
  void shouldGenerateTypesForArrayOfSubResourcesAttributesOfResourceAndReferTheSame()
      throws IOException {
    var contact = buildResource("contact").withAttribute("first_name").done();
    var customer =
        buildResource("customer").withSubResourceArrayAttribute("contacts", contact).done();

    var spec = buildSpec().withEnums(customer).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__contacts?:Customer.Contact[];__}__export__namespace__Customer__{__export__interface__Contact__{__first_name?:string;__}__//__REQUEST__PARAMS__//---------------__}__}");
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

    assertThat(fileOps).hasSize(6);
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

    assertThat(fileOps).hasSize(6);
    assertWriteStringFileOp(
        fileOps.get(2),
        "/tmp/resources",
        "Customer.d.ts",
        "///<reference__path='./filter.d.ts'/>__declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__list(__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<ListResponse>>;__}__export__interface__ListResponse__{__list:{customer:Customer,card?:Card}[];__next_offset?:string;__}__//__REQUEST__PARAMS__//---------------__}__}");
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

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "///<reference__path='./filter.d.ts'/>__declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__list(input?:ListInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<ListResponse>>;__}__export__interface__ListResponse__{__list:{customers:Customer}[];__next_offset?:string;__}__//__REQUEST__PARAMS__//---------------__export__interface__ListInputParam__{__limit?:__number;__offset?:__string;__}__}__}");
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

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__create(input:CreateInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<CreateResponse>>;__}__export__interface__CreateResponse__{__customer:Customer;__}__//__REQUEST__PARAMS__//---------------__export__interface__CreateInputParam__{__email:__string;__auto_collection?:__'on'__|__'off';__net_term_days?:__number;__allow_direct_debit?:__boolean;__}__}__}");
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

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__create(input:CreateInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<CreateResponse>>;__}__export__interface__CreateResponse__{__customer:Customer;__}__//__REQUEST__PARAMS__//---------------__export__interface__CreateInputParam__{__email:__string;__}__}__}");
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
                    .properties(Map.of("item_price_id", new StringSchema())))
            .withResponse(resourceResponseParam("subscription", subscription))
            .done();

    var spec =
        buildSpec()
            .withResource(subscription)
            .withPostOperation("/subscriptions", operation)
            .done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Subscription.d.ts",
        "declare__module__'chargebee'__{__export__interface__Subscription__{__id:string;__}__export__namespace__Subscription__{__export__class__SubscriptionResource__{__createWithItems(input:CreateWithItemsInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<CreateWithItemsResponse>>;__}__export__interface__CreateWithItemsResponse__{__subscription:Subscription;__}__//__REQUEST__PARAMS__//---------------__export__interface__CreateWithItemsInputParam__{__subscription_items?:__SubscriptionItemsCreateWithItemsInputParam[];__}__export__interface__SubscriptionItemsCreateWithItemsInputParam{__item_price_id:__string;__}__}__}");
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

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Quote.d.ts",
        "declare__module__'chargebee'__{__export__interface__Quote__{__id:string;__}__export__namespace__Quote__{__export__class__QuoteResource__{__create(input:CreateInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<CreateResponse>>;__}__export__interface__CreateResponse__{__quote:Quote;__}__//__REQUEST__PARAMS__//---------------__export__interface__CreateInputParam__{__invoice_date:__number;__}__}__}");
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
                            new IntegerSchema())))
            .withResponse(resourceResponseParam("quote", quote))
            .done();

    var spec = buildSpec().withResource(quote).withPostOperation("/quotes", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Quote.d.ts",
        "declare__module__'chargebee'__{__export__interface__Quote__{__id:string;__}__export__namespace__Quote__{__export__class__QuoteResource__{__createForChargeItemsAndCharges(input?:CreateForChargeItemsAndChargesInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<CreateForChargeItemsAndChargesResponse>>;__}__export__interface__CreateForChargeItemsAndChargesResponse__{__quote:Quote;__}__//__REQUEST__PARAMS__//---------------__export__interface__CreateForChargeItemsAndChargesInputParam__{__charges?:__ChargesCreateForChargeItemsAndChargesInputParam[];__}__export__interface__ChargesCreateForChargeItemsAndChargesInputParam{__date_from?:__number;__}__}__}");
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

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "///<reference__path='./filter.d.ts'/>__declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__list(input?:ListInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<ListResponse>>;__}__export__interface__ListResponse__{__list:{customers:Customer}[];__next_offset?:string;__}__//__REQUEST__PARAMS__//---------------__export__interface__ListInputParam__{__limit?:__number;__offset?:__string;__first_name?:__filter.String;__created_at?:__filter.Timestamp;__has_card?:__filter.Boolean;__has_card?:__filter.Number;__has_card?:__filter.Enum;__another_one?:__filter.Date;__has_card?:__filter.String;__}__}__}");
  }

  @Test
  void shouldCreateInterfaceForSubParams() throws IOException {
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
                        Map.of(
                            "gateway",
                            new ObjectSchema()
                                .properties(Map.of("hello", new StringSchema()))
                                .extensions(Map.of("x-cb-is-sub-resource", true))))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "///<reference__path='./filter.d.ts'/>__declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__list(input?:ListInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<ListResponse>>;__}__export__interface__ListResponse__{__list:{customers:Customer}[];__next_offset?:string;__}__//__REQUEST__PARAMS__//---------------__export__interface__ListInputParam__{__limit?:__number;__offset?:__string;__first_name?:__FirstNameListInputParam;__}__export__interface__FirstNameCustomerListInputParam__{__gateway?:__any;__}__}__}");
  }

  @Test
  void shouldSupportListOperationInputParamWithCustomField() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperationWithCustomField("list", true)
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

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "///<reference__path='./filter.d.ts'/>__declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__list(input?:ListInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<ListResponse>>;__}__export__interface__ListResponse__{__list:{customers:Customer}[];__next_offset?:string;__}__//__REQUEST__PARAMS__//---------------__export__interface__ListInputParam__{__limit?:__number;__offset?:__string;__[key:__`cf_${string}`]:__unknown;__}__}__}");
  }

  @Test
  void shouldIgnoreListOperationInputParamWithCustomFieldEqualsFalse() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperationWithCustomField("list", false)
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

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "///<reference__path='./filter.d.ts'/>__declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__list(input?:ListInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<ListResponse>>;__}__export__interface__ListResponse__{__list:{customers:Customer}[];__next_offset?:string;__}__//__REQUEST__PARAMS__//---------------__export__interface__ListInputParam__{__limit?:__number;__offset?:__string;__}__}__}");
  }

  @Test
  void shouldSupportPostActionWithRequestBodySupportingCbCustomFields() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperationWitCbCustomField("create", null, true)
            .forResource("customer")
            .withRequestBody("email", new StringSchema(), true)
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__create(input:CreateInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<CreateResponse>>;__}__export__interface__CreateResponse__{__customer:Customer;__}__//__REQUEST__PARAMS__//---------------__export__interface__CreateInputParam__{__email:__string;__[key:__`cf_${string}`]:__unknown;__}__}__}");
  }

  @Test
  void shouldIgnorePostActionFalseCustomFieldRequestBody() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperationWitCbCustomField("create", null, false)
            .forResource("customer")
            .withRequestBody("email", new StringSchema(), true)
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__create(input:CreateInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<CreateResponse>>;__}__export__interface__CreateResponse__{__customer:Customer;__}__//__REQUEST__PARAMS__//---------------__export__interface__CreateInputParam__{__email:__string;__}__}__}");
  }

  @Test
  void shouldIgnorePostActionNullCustomFieldRequestBody() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperationWitNullCbCustomField("create", null)
            .forResource("customer")
            .withRequestBody("email", new StringSchema(), true)
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = typeScriptTyping.generate("/tmp", spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(
        fileOps.get(1),
        "/tmp/resources",
        "Customer.d.ts",
        "declare__module__'chargebee'__{__export__interface__Customer__{__id:string;__}__export__namespace__Customer__{__export__class__CustomerResource__{__create(input:CreateInputParam,__headers?:ChargebeeRequestHeader):Promise<ChargebeeResponse<CreateResponse>>;__}__export__interface__CreateResponse__{__customer:Customer;__}__//__REQUEST__PARAMS__//---------------__export__interface__CreateInputParam__{__email:__string;__}__}__}");
  }

  @Test
  void OperationWithOutMethodNameShouldThrowException() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    try {
      var operation =
          buildPostOperationWitCbCustomField(null, null, true)
              .forResource("customer")
              .withRequestBody("email", new StringSchema(), true)
              .withResponse(resourceResponseParam("customer", customer))
              .done();
      var spec =
          buildSpec().withResource(customer).withPostOperation("/customers", operation).done();
      typeScriptTyping.generate("/tmp", spec);
    } catch (IllegalArgumentException err) {
      assertThat(err.getMessage()).isEqualTo("Operation method name not found");
    }
  }

  @Test
  void OperationWithoutExtensionShouldThrowException() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    try {
      var operation =
          buildPostOperationWitCbCustomField("create", null, true)
              .forResource("customer")
              .withRequestBody("email", new StringSchema(), true)
              .withResponse(resourceResponseParam("customer", customer))
              .done();
      var spec =
          buildSpec()
              .withResource(customer)
              .withPostOperation("/customers", operation.extensions(null))
              .done();
      typeScriptTyping.generate("/tmp", spec);
    } catch (IllegalArgumentException err) {
      assertThat(err.getMessage()).isEqualTo("Operation Extensions not found");
    }
  }
}
