package com.chargebee.sdk.ts;

import static com.chargebee.sdk.test_data.OperationBuilder.*;
import static com.chargebee.sdk.test_data.OperationBuilder.buildListOperation;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TypeScriptTests extends LanguageTests {
  public static TypeScript typeScriptSdkGen;

  @BeforeAll
  static void beforeAll() {
    typeScriptSdkGen = new TypeScript();
  }

  void assertTsResultFileContent(FileOp.WriteString fileOp, String body) throws IOException {
    String expectedContent =
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/ts/samples/result_1.tsf")
            .replace("body", body);
    assertThat(fileOp.fileContent).isEqualTo(expectedContent);
  }

  @Test
  void shouldCreateResourceDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertThat(fileOps).hasSize(4);
    assertCreateDirectoryFileOp(fileOps.get(0), "/typescript/src", "/resources");
  }

  void assertTypeScriptResourceFileContent(FileOp.WriteString fileOp, String fileName, String body)
      throws IOException {
    String expectedContent =
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/ts/samples/" + fileName)
            .replace("body", body);
    assertThat(fileOp.fileContent.replaceAll("\\s+", ""))
        .contains(expectedContent.replaceAll("\\s+", ""));
  }

  @Test
  void shouldCreateIndexFileInResourceDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(fileOps.get(1), "/typescript/src/resources", "index.ts");
  }

  @Test
  void shouldCreateChargebeeFileInSrcDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(fileOps.get(2), "/typescript/src", "chargebee.ts");
  }

  @Test
  void shouldCreateResultFileInSrcDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(fileOps.get(3), "/typescript/src", "result.ts");
  }

  @Test
  void eachResourceShouldHaveExportStatementAndGetMethodInChangeBeeFile() throws IOException {
    var spec = buildSpec().withResources("subscription").done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertWriteStringFileOp(
        fileOps.get(3),
        "/typescript/src",
        "chargebee.ts",
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/ts/samples/chargebee_1.tsf"));
  }

  @Test
  void eachResourceShouldBeOrderedBasedOnTheirSortOrderInChangeBeeFile() throws IOException {
    var subscription = buildResource("subscription").withSortOrder(0).done();
    var contract_term = buildResource("contract_term").withSortOrder(2).done();

    var spec = buildSpec().withResources(subscription, contract_term).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertWriteStringFileOp(
        fileOps.get(4),
        "/typescript/src",
        "chargebee.ts",
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/ts/samples/chargebee_2.tsf"));
  }

  @Test
  void shouldIgnoreDependentResourcesInChangeBeeFile() throws IOException {
    var subscription = buildResource("subscription").withSortOrder(0).done();
    var credit_note_estimate =
        buildResource("credit_note_estimate").asDependentResource().withSortOrder(26).done();

    var spec = buildSpec().withResources(subscription, credit_note_estimate).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertWriteStringFileOp(
        fileOps.get(4),
        "/typescript/src",
        "chargebee.ts",
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/ts/samples/chargebee_1.tsf"));
  }

  @Test
  void shouldExportEachResourceInIndexFile() throws IOException {
    var spec = buildSpec().withResources("subscription").done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertWriteStringFileOp(
        fileOps.get(2),
        "/typescript/src/resources",
        "index.ts",
        "// List of models\n" + "export {Subscription} from \"./subscription\";");
  }

  @Test
  void shouldExportEachSubResourceOfResourceInIndexFile() throws IOException {
    var fixedIntervalSchedule = buildResource("fixed_interval_schedule").done();
    var specificDatesSchedule = buildResource("specific_dates_schedule").done();
    var advanceInvoiceSchedule =
        buildResource("advance_invoice_schedule")
            .withSubResourceAttribute("fixed_interval_schedule", fixedIntervalSchedule)
            .withSubResourceAttribute("specific_dates_schedule", specificDatesSchedule)
            .done();

    var spec = buildSpec().withResources(advanceInvoiceSchedule).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertWriteStringFileOp(
        fileOps.get(2),
        "/typescript/src/resources",
        "index.ts",
        """
// List of models
export {AdvanceInvoiceSchedule} from "./advance_invoice_schedule";
export {FixedIntervalSchedule as AdvanceInvoiceScheduleFixedIntervalSchedule} from "./advance_invoice_schedule";
export {SpecificDatesSchedule as AdvanceInvoiceScheduleSpecificDatesSchedule} from "./advance_invoice_schedule";""");
  }

  @Test
  void eachResourceShouldBeOrderedBasedOnTheirSortOrderInIndexFile() throws IOException {
    var subscription = buildResource("subscription").withSortOrder(0).done();
    var contract_term = buildResource("contract_term").withSortOrder(2).done();

    var spec = buildSpec().withResources(subscription, contract_term).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertWriteStringFileOp(
        fileOps.get(3),
        "/typescript/src/resources",
        "index.ts",
        """
        // List of models
        export {Subscription} from "./subscription";
        export {ContractTerm} from "./contract_term";""");
  }

  @Test
  void shouldContainDependentResourcesInIndexFile() throws IOException {
    var subscription = buildResource("subscription").withSortOrder(0).done();
    var credit_note_estimate =
        buildResource("credit_note_estimate").asDependentResource().withSortOrder(26).done();

    var spec = buildSpec().withResources(subscription, credit_note_estimate).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertWriteStringFileOp(
        fileOps.get(3),
        "/typescript/src/resources",
        "index.ts",
        """
        // List of models
        export {Subscription} from "./subscription";
        export {CreditNoteEstimate} from "./credit_note_estimate";""");
  }

  @Test
  void eachResourceShouldHaveSeparateDeclaration() throws IOException {
    var spec = buildSpec().withResources("customer", "address").done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertWriteStringFileOp(fileOps.get(1), "/typescript/src/resources", "address.ts");
    assertWriteStringFileOp(fileOps.get(2), "/typescript/src/resources", "customer.ts");
  }

  @Test
  void shouldIgnoreHiddenFromSDKResources() throws IOException {
    var subscription = buildResource("subscription").done();
    var subscription_preview =
        buildResource("subscription_preview").asHiddenFromSDKGeneration().done();

    var spec = buildSpec().withResources(subscription, subscription_preview).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Subscription extends Model {

         \s

          // OPERATIONS
          //-----------

        } // ~Subscription



          // REQUEST PARAMS
          //---------------

        export namespace _subscription {
        }
        """);
  }

  @Test
  void shouldContainDependentResources() throws IOException {
    var contract_term = buildResource("contract_term").done();
    var credit_note_estimate = buildResource("credit_note_estimate").asDependentResource().done();

    var spec = buildSpec().withResources(contract_term, credit_note_estimate).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class CreditNoteEstimate extends Model {

         \s

          // OPERATIONS
          //-----------

        } // ~CreditNoteEstimate



          // REQUEST PARAMS
          //---------------

        export namespace _credit_note_estimate {
        }
        """);
  }

  @Test
  void shouldIgnoreThirdPartyFromSDKResources() throws IOException {
    var subscription = buildResource("subscription").done();
    var subscription_preview =
        buildResource("third_party_sync_details").asThirdPartyFromSDKGeneration().done();

    var spec = buildSpec().withResources(subscription, subscription_preview).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Subscription extends Model {

         \s

          // OPERATIONS
          //-----------

        } // ~Subscription



          // REQUEST PARAMS
          //---------------

        export namespace _subscription {
        }
        """);
  }

  @Test
  void eachResourceShouldHaveSpecifiedResourceClass() throws IOException {
    var spec = buildSpec().withResources("advance_invoice_schedule").done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class AdvanceInvoiceSchedule extends Model {

         \s

          // OPERATIONS
          //-----------

        } // ~AdvanceInvoiceSchedule



          // REQUEST PARAMS
          //---------------

        export namespace _advance_invoice_schedule {
        }
        """);
  }

  @Test
  void eachResourceShouldHaveSpecifiedAttributesWithType() throws IOException {
    var advanceInvoiceSchedule =
        buildResource("advance_invoice_schedule").withAttribute("id", true).done();

    var spec = buildSpec().withResources(advanceInvoiceSchedule).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class AdvanceInvoiceSchedule extends Model {
          public id: string;

         \s

          // OPERATIONS
          //-----------

        } // ~AdvanceInvoiceSchedule



          // REQUEST PARAMS
          //---------------

        export namespace _advance_invoice_schedule {
        }
        """);
  }

  @Test
  void shouldHaveQuestionMarkForOptionalAttributes() throws IOException {
    var advanceInvoiceSchedule =
        buildResource("advance_invoice_schedule")
            .withAttribute("id", true)
            .withAttribute("schedule_type")
            .done();

    var spec = buildSpec().withResources(advanceInvoiceSchedule).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class AdvanceInvoiceSchedule extends Model {
          public id: string;
          public schedule_type?: string;

         \s

          // OPERATIONS
          //-----------

        } // ~AdvanceInvoiceSchedule



          // REQUEST PARAMS
          //---------------

        export namespace _advance_invoice_schedule {
        }
        """);
  }

  @Test
  void shouldSupportBooleanType() throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute("allow_direct_debit", new BooleanSchema(), true)
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Customer extends Model {
          public allow_direct_debit: boolean;

         \s

          // OPERATIONS
          //-----------

        } // ~Customer



          // REQUEST PARAMS
          //---------------

        export namespace _customer {
        }
        """);
  }

  @Test
  void shouldSupportNumberType() throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute("net_term_days", new IntegerSchema(), true)
            .withAttribute(
                "created_at", new IntegerSchema().format("unix-time").pattern("^[0-9]{10}$"), true)
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Customer extends Model {
          public net_term_days: number;
          public created_at: number;

         \s

          // OPERATIONS
          //-----------

        } // ~Customer



          // REQUEST PARAMS
          //---------------

        export namespace _customer {
        }
        """);
  }

  @Test
  void shouldSupportArrayType() throws IOException {
    var resource =
        buildResource("purchase")
            .withAttribute("subscription_ids", new ArraySchema().items(new StringSchema()))
            .withAttribute("invoice_ids", new ArraySchema().items(new StringSchema()))
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Purchase extends Model {
          public subscription_ids?: Array<string>;
          public invoice_ids?: Array<string>;

         \s

          // OPERATIONS
          //-----------

        } // ~Purchase



          // REQUEST PARAMS
          //---------------

        export namespace _purchase {
        }
        """);
  }

  @Test
  void shouldSupportJsonObjectAndJsonArrayType() throws IOException {
    var resource =
        buildResource("customer")
            .withAttribute("metadata", new ObjectSchema().additionalProperties(true))
            .withAttribute("exemption_details", new ArraySchema().items(new Schema<>()))
            .done();

    var spec = buildSpec().withResource(resource).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Customer extends Model {
          public metadata?: any;
          public exemption_details?: any;

         \s

          // OPERATIONS
          //-----------

        } // ~Customer



          // REQUEST PARAMS
          //---------------

        export namespace _customer {
        }
        """);
  }

  @Test
  void shouldSupportSubResourcesAttributesType() throws IOException {
    var billingAddress = buildResource("billing_address").done();
    var paymentMethod = buildResource("payment_method").done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .withSubResourceAttribute("payment_method", paymentMethod)
            .done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Customer extends Model {
          public billing_address?: BillingAddress;
          public payment_method?: PaymentMethod;

         \s

          // OPERATIONS
          //-----------

        } // ~Customer

        export class BillingAddress extends Model {
        } // ~BillingAddress

        export class PaymentMethod extends Model {
        } // ~PaymentMethod



          // REQUEST PARAMS
          //---------------

        export namespace _customer {
        }
        """);
  }

  @Test
  void shouldSupportArrayOfSubResourcesAttributesType() throws IOException {
    var contact = buildResource("contact").done();
    var customer =
        buildResource("customer").withSubResourceArrayAttribute("contacts", contact).done();

    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Customer extends Model {
          public contacts?: Array<Contact>;

         \s

          // OPERATIONS
          //-----------

        } // ~Customer

        export class Contact extends Model {
        } // ~Contact



          // REQUEST PARAMS
          //---------------

        export namespace _customer {
        }
        """);
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

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Estimate extends Model {
          public subscription_estimate?: resources.SubscriptionEstimate;
          public subscription_estimates?: Array<resources.SubscriptionEstimate>;

         \s

          // OPERATIONS
          //-----------

        } // ~Estimate



          // REQUEST PARAMS
          //---------------

        export namespace _estimate {
        }
        """);
  }

  @Test
  void eachActionOfResourceShouldBeOrderedBasedOnTheirSortOrder() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var createWithItemsOperation =
        buildPostOperation("createWithItems")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withPathParam("customer-id")
            .withSortOrder(1)
            .asInputObjNeeded()
            .done();
    var listSubscriptionOperation =
        buildListOperation("list")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withSortOrder(3)
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(subscription)
            .withPostOperation(
                "/customers/{customer-id}/subscription_for_items", createWithItemsOperation)
            .withOperation("/subscriptions", listSubscriptionOperation)
            .done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(writeStringFileOp, "subscription.tsf", "");
  }

  @Test
  void shouldIgnoreHiddenFromSDKActions() throws IOException {
    var token = buildResource("token").withAttribute("id", true).done();
    var createForCardOperation =
        buildPostOperation("create_for_card")
            .forResource("token")
            .withResponse(resourceResponseParam("token", token))
            .asHiddenFromSDKGeneration()
            .withSortOrder(0)
            .done();
    var spec =
        buildSpec()
            .withResource(token)
            .withPostOperation("/tokens/create_for_card", createForCardOperation)
            .done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Token extends Model {
          public id: string;

         \s

          // OPERATIONS
          //-----------

        } // ~Token



          // REQUEST PARAMS
          //---------------

        export namespace _token {
        }
        """);
  }

  @Test
  void shouldIgnoreBulkOperationFromSDKActions() throws IOException {
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

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Token extends Model {
          public id: string;

         \s

          // OPERATIONS
          //-----------

        } // ~Token



          // REQUEST PARAMS
          //---------------

        export namespace _token {
        }
        """);
  }

  @Test
  void shouldSupportPostAction() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var createWithItemsOperation =
        buildPostOperation("createWithItems")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withPathParam("customer-id")
            .asInputObjNeeded()
            .withSortOrder(1)
            .done();

    var spec =
        buildSpec()
            .withResource(subscription)
            .withPostOperation(
                "/customers/{customer-id}/subscription_for_items", createWithItemsOperation)
            .done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp, "subscription_create_with_items.tsf", "");
  }

  @Test
  void shouldSupportGetAction() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withSortOrder(3)
            .withPathParam("subscription-id")
            .done();
    var spec =
        buildSpec()
            .withResource(subscription)
            .withOperation("/subscriptions/{subscription-id}", operation)
            .done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Subscription extends Model {
          public id: string;

         \s

          // OPERATIONS
          //-----------

          public static retrieve(subscription_id: string, params?: any):RequestWrapper {
            return new RequestWrapper([subscription_id, params], {
              'methodName': 'retrieve',
              'httpMethod': 'GET',
              'urlPrefix': '/subscriptions',
              'urlSuffix': null,
              'hasIdInUrl': true,
              'isListReq': false,
              'subDomain': null,
              'isOperationNeedsJsonInput': false,
              'jsonKeys': {\s
              }
            }, ChargeBee._env)
          }

        } // ~Subscription



          // REQUEST PARAMS
          //---------------

        export namespace _subscription {
        }
        """);
  }

  @Test
  void eachActionShouldHaveUrlPrefix() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withSortOrder(3)
            .withPathParam("subscription-id")
            .done();
    var spec =
        buildSpec()
            .withResource(subscription)
            .withOperation("/subscriptions/{subscription-id}", operation)
            .done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_1.tsf",
        """
        export class Subscription extends Model {
          public id: string;

         \s

          // OPERATIONS
          //-----------

          public static retrieve(subscription_id: string, params?: any):RequestWrapper {
            return new RequestWrapper([subscription_id, params], {
              'methodName': 'retrieve',
              'httpMethod': 'GET',
              'urlPrefix': '/subscriptions',
              'urlSuffix': null,
              'hasIdInUrl': true,
              'isListReq': false,
              'subDomain': null,
              'isOperationNeedsJsonInput': false,
              'jsonKeys': {\s
              }
            }, ChargeBee._env)
          }

        } // ~Subscription



          // REQUEST PARAMS
          //---------------

        export namespace _subscription {
        }
        """);
  }

  @Test
  void ifActionHasUrlSuffixItWillShowUpTheUrlSuffix() throws IOException {
    var subscription =
        buildResource("subscription").withAttribute("id", true).withSortOrder(0).done();
    var createWithItemsOperation =
        buildPostOperation("createWithItems")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withPathParam("customer-id")
            .withSortOrder(1)
            .asInputObjNeeded()
            .done();

    var subscriptionEntitlement =
        buildResource("subscription_entitlement")
            .withAttribute("subscription_id", true)
            .withSortOrder(0)
            .done();
    var setSubscriptionEntitlementAvailabilityOperation =
        buildPostOperation("setSubscriptionEntitlementAvailability")
            .forResource("subscription_entitlement")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withPathParam("subscription-id")
            .asInputObjNeeded()
            .withSortOrder(71)
            .done();

    var spec =
        buildSpec()
            .withResources(subscription, subscriptionEntitlement)
            .withPostOperation(
                "/customers/{customer-id}/subscription_for_items", createWithItemsOperation)
            .withPostOperation(
                "/subscriptions/{subscription-id}/subscription_entitlements/set_availability",
                setSubscriptionEntitlementAvailabilityOperation)
            .done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp, "subscription_create_with_items.tsf", "");

    var writeStringFileOp2 = (FileOp.WriteString) fileOps.get(2);
    assertTypeScriptResourceFileContent(
        writeStringFileOp2,
        "resources_1.tsf",
        """
export class SubscriptionEntitlement extends Model {
  public subscription_id: string;

 \s

  // OPERATIONS
  //-----------

  public static set_subscription_entitlement_availability(subscription_id: string, params?: _subscription_entitlement.set_subscription_entitlement_availability_params):RequestWrapper {
    return new RequestWrapper([subscription_id, params], {
      'methodName': 'set_subscription_entitlement_availability',
      'httpMethod': 'POST',
      'urlPrefix': '/subscriptions',
      'urlSuffix': '/subscription_entitlements/set_availability',
      'hasIdInUrl': true,
      'isListReq': false,
      'subDomain': null,
      'isOperationNeedsJsonInput': false,
      'jsonKeys': {\s
      }
    }, ChargeBee._env)
  }

} // ~SubscriptionEntitlement



  // REQUEST PARAMS
  //---------------

export namespace _subscription_entitlement {
}
""");
  }

  @Test
  void ifActionHasNoUrlSuffixItWillShowUpAsNull() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var listSubscriptionOperation =
        buildListOperation("list")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withSortOrder(3)
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(subscription)
            .withOperation("/subscriptions", listSubscriptionOperation)
            .done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_list.tsf",
        """
export class Subscription extends Model {
  public id: string;

 \s

  // OPERATIONS
  //-----------

  public static list(params?: _subscription.subscription_list_params):RequestWrapper<ListResult> {
    return new RequestWrapper([params], {
      'methodName': 'list',
      'httpMethod': 'GET',
      'urlPrefix': '/subscriptions',
      'urlSuffix': null,
      'hasIdInUrl': false,
      'isListReq': true,
      'subDomain': null,
      'isOperationNeedsJsonInput': false,
      'jsonKeys': {}
    }, ChargeBee._env)
  }

} // ~Subscription



  // REQUEST PARAMS
  //---------------

export namespace _subscription {
}
""");
  }

  @Test
  void ifActionHasPathParamItWillShowUpAsTrue() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var createWithItemsOperation =
        buildPostOperation("createWithItems")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withPathParam("customer-id")
            .withSortOrder(1)
            .asInputObjNeeded()
            .done();

    var spec =
        buildSpec()
            .withResource(subscription)
            .withPostOperation(
                "/customers/{customer-id}/subscription_for_items", createWithItemsOperation)
            .done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp, "subscription_create_with_items.tsf", "");
  }

  @Test
  void ifActionHasNoPathParamItWillShowUpAsFalse() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var listSubscriptionOperation =
        buildListOperation("list")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withSortOrder(3)
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(subscription)
            .withOperation("/subscriptions", listSubscriptionOperation)
            .done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(
        writeStringFileOp,
        "resources_list.tsf",
        """
export class Subscription extends Model {
  public id: string;

 \s

  // OPERATIONS
  //-----------

  public static list(params?: _subscription.subscription_list_params):RequestWrapper<ListResult> {
    return new RequestWrapper([params], {
      'methodName': 'list',
      'httpMethod': 'GET',
      'urlPrefix': '/subscriptions',
      'urlSuffix': null,
      'hasIdInUrl': false,
      'isListReq': true,
      'subDomain': null,
      'isOperationNeedsJsonInput': false,
      'jsonKeys': {\s
      }
    }, ChargeBee._env)
  }

} // ~Subscription



  // REQUEST PARAMS
  //---------------

export namespace _subscription {
}
""");
  }

  @Test
  void shouldCreateModelsFileWithSubResources() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var paymentMethod = buildResource("payment_method").withAttribute("type", true).done();
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
            .asInputObjNeeded()
            .withRequestBody("first_name", new StringSchema())
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperations(
                new OperationWithPath("/customers", createOperation),
                new OperationWithPath("/customers/{customer-id}", updateOperation))
            .done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(writeStringFileOp, "customer.tsf", "");
  }

  @Test
  void shouldCreateModelsWithOperationRequestParameter() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var paymentMethod = buildResource("payment_method").withAttribute("type", true).done();
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
            .asInputObjNeeded()
            .withRequestBody("first_name", new StringSchema())
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperations(
                new OperationWithPath("/customers", createOperation),
                new OperationWithPath("/customers/{customer-id}", updateOperation))
            .done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src/resources", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertTypeScriptResourceFileContent(writeStringFileOp, "customer.tsf", "");
  }

  @Test
  void resultFileShouldHaveImportStatementsAndBareBone() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertTsResultFileContent(writeStringFileOp, "");
  }

  @Test
  void resultFileShouldHaveResourceFunction() throws IOException {
    var spec = buildSpec().withResources("contract_term").done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertTsResultFileContent(
        writeStringFileOp,
        """
            get contract_term(): resources.ContractTerm {
                let _contract_term = this.get(
                    'contract_term',
                    'ContractTerm'
                );
                return _contract_term;
            }
        """);
  }

  @Test
  void shouldHaveResourceFunctionWithSubResourcesInResultFile() throws IOException {
    var einvoice = buildResource("einvoice").done();
    var line_item = buildResource("line_items").done();
    var credit_note =
        buildResource("credit_note")
            .withSubResourceAttribute("einvoice", einvoice)
            .withSubResourceAttribute("line_items", line_item)
            .done();
    var spec = buildSpec().withResources(credit_note).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertTsResultFileContent(
        writeStringFileOp,
        """
            get credit_note(): resources.CreditNote {
                let _credit_note = this.get(
                    'credit_note',
                    'CreditNote',
                    {'einvoice': 'CreditNoteEinvoice', 'line_items': 'CreditNoteLineItem'}
                );
                return _credit_note;
            }
        """);
  }

  @Test
  void shouldIgnoreDependantResourcesInResultFile() throws IOException {
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

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertTsResultFileContent(
        writeStringFileOp,
        """
    get gift(): resources.Gift {
        let _gift = this.get(
            'gift',
            'Gift',
            {'gifter': 'GiftGifter', 'gift_receiver': 'GiftGiftReceiver', 'gift_timelines': 'GiftGiftTimeline'}
        );
        return _gift;
    }
""");
  }

  @Test
  void resourcesShouldFollowSortOrderInResultFile() throws IOException {
    var token = buildResource("token").withSortOrder(5).done();
    var discount = buildResource("discount").withSortOrder(2).done();
    var spec = buildSpec().withResources(token, discount).done();

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertTsResultFileContent(
        writeStringFileOp,
        """
            get discount(): resources.Discount {
                let _discount = this.get(
                    'discount',
                    'Discount'
                );
                return _discount;
            }
            get token(): resources.Token {
                let _token = this.get(
                    'token',
                    'Token'
                );
                return _token;
            }
        """);
  }

  @Test
  void shouldSupportSingularAndListDependentResourcesInResultFile() throws IOException {
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

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    assertWriteStringFileOp(
        fileOps.get(5),
        "/typescript/src",
        "result.ts",
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/ts/samples/result_2.tsf"));
  }

  @Test
  void shouldHaveClassDefinitionsForListResponsesInResultFile() throws IOException {
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

    List<FileOp> fileOps = typeScriptSdkGen.generate("/typescript/src", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertTsResultFileContent(
        writeStringFileOp,
        """
            get invoice(): resources.Invoice {
                let _invoice = this.get(
                    'invoice',
                    'Invoice',
                    {'line_items': 'InvoiceLineItem', 'discounts': 'InvoiceDiscount'}
                );
                return _invoice;
            }
            get unbilled_charge(): resources.UnbilledCharge {
                let _unbilled_charge = this.get(
                    'unbilled_charge',
                    'UnbilledCharge'
                );
                return _unbilled_charge;
            }

            get invoices(): resources.Invoice[] {
                let _invoices = this.get_list(
                    'invoices',
                    'Invoice',
                    {'line_items': 'InvoiceLineItem', 'discounts': 'InvoiceDiscount'}
                );
                return _invoices;
            }\
        """);
  }
}
