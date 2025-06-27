package com.chargebee.sdk.php;

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

public class PhpTests extends LanguageTests {

  public static Php phpSdkGen;

  private final String basePath = "/php/lib";

  private final String modelImportStatements =
      """
      namespace ChargeBee\\ChargeBee\\Models;

      use ChargeBee\\ChargeBee\\Model;
      """;

  void assertPhpModelResourceFileContent(
      FileOp.WriteString fileOp, String body, boolean hasSnippet) {
    String environment = hasSnippet ? "use ChargeBee\\ChargeBee\\Environment;\n" : "";
    assertThat(fileOp.fileContent)
        .startsWith(
            "<?php\n"
                + "\n"
                + modelImportStatements
                + "use ChargeBee\\ChargeBee\\Request;\n"
                + "use ChargeBee\\ChargeBee\\Util;\n"
                + environment
                + "\n"
                + body);
  }

  void assertPhpModelResourceFileContentEvent(
      FileOp.WriteString fileOp, String body, boolean hasSnippet) {
    String environment = hasSnippet ? "use ChargeBee\\ChargeBee\\Environment;\n" : "";
    assertThat(fileOp.fileContent)
        .startsWith(
            "<?php\n"
                + "\n"
                + modelImportStatements
                + "use ChargeBee\\ChargeBee\\Request;\n"
                + "use ChargeBee\\ChargeBee\\Util;\n"
                + environment
                + "use Exception;\n"
                + "use RuntimeException;\n"
                + "\n"
                + body);
  }

  void assertPhpModelSubResourceFileContent(FileOp.WriteString fileOp, String body) {
    assertThat(fileOp.fileContent)
        .isEqualTo("<?php\n" + "\n" + modelImportStatements + "\n" + body + "\n" + "?>");
  }

  void assertPhpResultFileContent(FileOp.WriteString fileOp, String body) throws IOException {
    String expectedContent =
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/php/samples/result_1.phpf")
            .replace("body", body);
    assertThat(fileOp.fileContent).isEqualTo(expectedContent);
  }

  @BeforeAll
  static void beforeAll() {
    phpSdkGen = new Php();
  }

  @Test
  void shouldCreateChargeBeeAndModelsDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(3);
    assertCreateDirectoryFileOp(fileOps.get(0), basePath, "/ChargeBee/Models");
  }

  @Test
  void shouldCreateResultFileInChargeBeeDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(3);
    assertWriteStringFileOp(fileOps.get(1), "/php/lib/ChargeBee", "Result.php");
  }

  @Test
  void shouldCreateInitFileInLibDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(3);
    assertWriteStringFileOp(fileOps.get(2), basePath, "init.php");
  }

  @Test
  void eachResourceShouldHaveSeparateDeclaration() throws IOException {
    var spec = buildSpec().withResources("customer", "address").done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(5);
    assertWriteStringFileOp(fileOps.get(1), "/php/lib/ChargeBee/Models", "Address.php");
    assertWriteStringFileOp(fileOps.get(2), "/php/lib/ChargeBee/Models", "Customer.php");
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(1), "/php/lib/ChargeBee/Models", "AdvanceInvoiceSchedule.php");
    assertWriteStringFileOp(
        fileOps.get(2),
        "/php/lib/ChargeBee/Models",
        "AdvanceInvoiceScheduleFixedIntervalSchedule.php");
    assertWriteStringFileOp(
        fileOps.get(3),
        "/php/lib/ChargeBee/Models",
        "AdvanceInvoiceScheduleSpecificDatesSchedule.php");
  }

  @Test
  void shouldWriteInitFileWithSpecifiedResources() throws IOException {
    var fixedIntervalSchedule = buildResource("fixed_interval_schedule").done();
    var specificDatesSchedule = buildResource("specific_dates_schedule").done();
    var advanceInvoiceSchedule =
        buildResource("advance_invoice_schedule")
            .withSubResourceAttribute("fixed_interval_schedule", fixedIntervalSchedule)
            .withSubResourceAttribute("specific_dates_schedule", specificDatesSchedule)
            .done();

    var spec = buildSpec().withResources(advanceInvoiceSchedule).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(5),
        basePath,
        "init.php",
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/php/samples/init_1.phpf"));
  }

  @Test
  void eachResourceShouldHaveSpecifiedResourceClass() throws IOException {
    var advanceInvoiceSchedule = buildResource("advance_invoice_schedule").done();

    var spec = buildSpec().withResources(advanceInvoiceSchedule).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
        class AdvanceInvoiceSchedule extends Model
        {

          protected $allowed = [
          ];



          # OPERATIONS
          #-----------

         }
        """,
        false);
  }

  @Test
  void eachSubResourceShouldHaveSpecifiedSubResourceClass() throws IOException {
    var fixedIntervalSchedule = buildResource("fixed_interval_schedule").done();
    var advanceInvoiceSchedule =
        buildResource("advance_invoice_schedule")
            .withSubResourceAttribute("fixed_interval_schedule", fixedIntervalSchedule)
            .done();

    var spec = buildSpec().withResources(advanceInvoiceSchedule).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPhpModelSubResourceFileContent(
        writeStringFileOp,
        """
        class AdvanceInvoiceScheduleFixedIntervalSchedule extends Model
        {
          protected $allowed = [
          ];

        }
        """);
  }

  @Test
  void shouldIgnoreHiddenFromSDKResources() throws IOException {
    var subscription = buildResource("subscription").done();
    var subscription_preview =
        buildResource("subscription_preview").asHiddenFromSDKGeneration().done();

    var spec = buildSpec().withResources(subscription, subscription_preview).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(4);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
        class Subscription extends Model
        {

          protected $allowed = [
          ];



          # OPERATIONS
          #-----------

         }
        """,
        false);
  }

  @Test
  void shouldContainDependentResources() throws IOException {
    var contract_term = buildResource("contract_term").done();
    var credit_note_estimate = buildResource("credit_note_estimate").asDependentResource().done();

    var spec = buildSpec().withResources(contract_term, credit_note_estimate).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertThat(fileOps).hasSize(5);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
        class CreditNoteEstimate extends Model
        {

          protected $allowed = [
          ];



          # OPERATIONS
          #-----------

         }
        """,
        false);
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
        class AdvanceInvoiceSchedule extends Model
        {

          protected $allowed = [
            'id',
            'scheduleType',
            'fixedIntervalSchedule',
            'specificDatesSchedule',
          ];



          # OPERATIONS
          #-----------

         }
        """,
        false);
  }

  @Test
  void shouldCreateSubResourceClassWithSpecifiedSubResourceAttributes() throws IOException {
    var fixedIntervalSchedule =
        buildResource("fixed_interval_schedule")
            .withAttribute("end_schedule_on")
            .withAttribute("number_of_occurrences")
            .withAttribute("days_before_renewal")
            .withAttribute("end_date")
            .withAttribute("created_at")
            .withAttribute("terms_to_change")
            .done();
    var advanceInvoiceSchedule =
        buildResource("advance_invoice_schedule")
            .withSubResourceAttribute("fixed_interval_schedule", fixedIntervalSchedule)
            .done();

    var spec = buildSpec().withResources(advanceInvoiceSchedule).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPhpModelSubResourceFileContent(
        writeStringFileOp,
        """
        class AdvanceInvoiceScheduleFixedIntervalSchedule extends Model
        {
          protected $allowed = [
            'endScheduleOn',
            'numberOfOccurrences',
            'daysBeforeRenewal',
            'endDate',
            'createdAt',
            'termsToChange',
          ];

        }
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function retrieve($id, $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::GET, Util::encodeURIPath("customers",$id), array(), $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
  }

  @Test
  void noParamsInArgsIfNoRequestBodyParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id").done();
    var operation =
        buildPostOperation("clearPersonalData")
            .forResource("customer")
            .withPathParam("customer_id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers/{customer-id}/clear_personal_data", operation)
            .done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function clearPersonalData($id, $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::POST, Util::encodeURIPath("customers",$id,"clear_personal_data"), array(), $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function hierarchy($id, $params, $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::GET, Util::encodeURIPath("customers",$id,"hierarchy"), $params, $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function merge($params, $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::POST, Util::encodeURIPath("customers","merge"), $params, $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
  }

  @Test
  void paramsInArgsIfAtleastOneSubRequestBodyParamIsRequired() throws IOException {
    var quote = buildResource("quote").withAttribute("id", true).done();
    var operation =
        buildPostOperation("createForChargeItemsAndCharges")
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Quote extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function createForChargeItemsAndCharges($params, $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::POST, Util::encodeURIPath("quotes","create_for_charge_items_and_charges"), $params, $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
  }

  @Test
  void paramsInArgsEvenIfSubRequestBodyParamIsHiddenFromSDKButRequired() throws IOException {
    var quote = buildResource("quote").withAttribute("id", true).done();
    var operation =
        buildPostOperation("createForChargeItemsAndCharges")
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Quote extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function createForChargeItemsAndCharges($params, $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::POST, Util::encodeURIPath("quotes","create_for_charge_items_and_charges"), $params, $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
  }

  @Test
  void paramsEqualArrayInArgsIfAllQueryParamsOptional() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("limit")
            .withQueryParam("offset")
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec = buildSpec().withResource(customer).withOperation("/customers", operation).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function all($params = array(), $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::sendListRequest(Request::GET, Util::encodeURIPath("customers"), $params, $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
  }

  @Test
  void paramsEqualArrayInArgsIfAllRequestBodyParamsOptional() throws IOException {
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function create($params = array(), $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::POST, Util::encodeURIPath("customers"), $params, $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function retrieve($id, $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::GET, Util::encodeURIPath("customers",$id), array(), $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function create($params = array(), $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::POST, Util::encodeURIPath("customers"), $params, $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function all($params = array(), $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::sendListRequest(Request::GET, Util::encodeURIPath("customers"), $params, $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
  }

  @Test
  void notAllListOperationsShouldHaveSendListRequestMethod() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperation("contactsForCustomer")
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function contactsForCustomer($id, $params = array(), $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::GET, Util::encodeURIPath("customers",$id,"contacts"), $params, $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
  }

  @Test
  void shouldSupportGetActionWithPathParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id").done();
    var operation =
        buildOperation("contactsForCustomer")
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function contactsForCustomer($id, $params = array(), $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::GET, Util::encodeURIPath("customers",$id,"contacts"), $params, $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
  }

  @Test
  void shouldSupportPostActionWithPathParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id").done();
    var operation =
        buildPostOperation("updatePaymentMethod")
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function updatePaymentMethod($id, $params, $env = null, $headers = array())
  {
    $jsonKeys = array(
        "type" => 1,
    );
    return Request::send(Request::POST, Util::encodeURIPath("customers",$id,"update_payment_method"), $params, $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
  }

  @Test
  void shouldFollowSortOrderOfOperations() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var updateOperation =
        buildPostOperation("update")
            .withPathParam("customer_id")
            .forResource("customer")
            .withRequestBody("first_name", new StringSchema())
            .withSortOrder(3)
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withResponse(resourceResponseParam("customer", customer))
            .withRequestBody("id", new StringSchema())
            .withSortOrder(0)
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperations(
                new OperationWithPath("/customers", createOperation),
                new OperationWithPath("/customers/{customer-id}", updateOperation))
            .done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        """
class Customer extends Model
{

  protected $allowed = [
    'id',
  ];



  # OPERATIONS
  #-----------

  public static function create($params = array(), $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::POST, Util::encodeURIPath("customers"), $params, $env, $headers, null, false, $jsonKeys);
  }

  public static function update($id, $params = array(), $env = null, $headers = array())
  {
    $jsonKeys = array(
    );
    return Request::send(Request::POST, Util::encodeURIPath("customers",$id), $params, $env, $headers, null, false, $jsonKeys);
  }

 }
""",
        false);
  }

  @Test
  void exportResourceShouldHaveSpecifiedSnippet() throws IOException {
    var export = buildResource("export").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("export")
            .withPathParam("export-id")
            .withResponse(resourceResponseParam("export", export))
            .done();
    var spec =
        buildSpec().withResource(export).withOperation("/exports/{export-id}", operation).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        "class Export extends Model\n"
            + "{\n"
            + "\n"
            + "  protected $allowed = [\n"
            + "    'id',\n"
            + "  ];\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/php/export.php.hbs")
            + "\n"
            + "\n"
            + "\n"
            + "  # OPERATIONS\n"
            + "  #-----------\n"
            + "\n"
            + "  public static function retrieve($id, $env = null, $headers ="
            + " array())\n",
        true);
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        "class TimeMachine extends Model\n"
            + "{\n"
            + "\n"
            + "  protected $allowed = [\n"
            + "    'name',\n"
            + "  ];\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/php/timeMachine.php.hbs")
            + "\n"
            + "\n"
            + "\n"
            + "  # OPERATIONS\n"
            + "  #-----------\n"
            + "\n"
            + "  public static function retrieve($id, $env = null, $headers ="
            + " array())\n",
        true);
  }

  @Test
  void eventResourceShouldHaveSpecifiedSnippet() throws IOException {
    var event = buildResource("event").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("event")
            .withPathParam("event-id")
            .withResponse(resourceResponseParam("event", event))
            .done();
    var spec =
        buildSpec().withResource(event).withOperation("/events/{event-id}", operation).done();

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContentEvent(
        writeStringFileOp,
        "class Event extends Model\n"
            + "{\n"
            + "\n"
            + "  protected $allowed = [\n"
            + "    'id',\n"
            + "  ];\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/php/event.php.hbs")
            + "\n"
            + "\n"
            + "\n"
            + "  # OPERATIONS\n"
            + "  #-----------\n"
            + "\n"
            + "  public static function retrieve($id, $env = null, $headers ="
            + " array())",
        true);
  }

  @Test
  void hostedPageResourceShouldHaveSpecifiedSnippet() throws IOException {
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpModelResourceFileContent(
        writeStringFileOp,
        "class HostedPage extends Model\n"
            + "{\n"
            + "\n"
            + "  protected $allowed = [\n"
            + "    'id',\n"
            + "  ];\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/php/hostedPage.php.hbs")
            + "\n"
            + "\n"
            + "\n"
            + "  # OPERATIONS\n"
            + "  #-----------\n"
            + "\n"
            + "  public static function retrieve($id, $env = null, $headers ="
            + " array())\n",
        false);
  }

  @Test
  void resultFileShouldHaveImportStatementsAndBareBone() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = phpSdkGen.generate("/php/lib/ChargeBee", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(1);
    assertPhpResultFileContent(writeStringFileOp, "");
  }

  @Test
  void resultFileShouldHaveResourceFunction() throws IOException {
    var spec = buildSpec().withResources("contract_term").done();

    List<FileOp> fileOps = phpSdkGen.generate("/php/lib/ChargeBee", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPhpResultFileContent(
        writeStringFileOp,
        """

    public function contractTerm()\s
    {
        $contract_term = $this->_get('contract_term', Models\\ContractTerm::class);
        return $contract_term;
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

    List<FileOp> fileOps = phpSdkGen.generate("/php/lib/ChargeBee", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(4);
    assertPhpResultFileContent(
        writeStringFileOp,
        """

    public function creditNote()\s
    {
        $credit_note = $this->_get('credit_note', Models\\CreditNote::class,\s
        array(\s
\t\t\t'einvoice' => Models\\CreditNoteEinvoice::class,\s
\t\t\t'line_items' => Models\\CreditNoteLineItem::class
		));
        return $credit_note;
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

    List<FileOp> fileOps = phpSdkGen.generate("/php/lib/ChargeBee", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertPhpResultFileContent(
        writeStringFileOp,
        """

    public function gift()\s
    {
        $gift = $this->_get('gift', Models\\Gift::class,\s
        array(\s
\t\t\t'gifter' => Models\\GiftGifter::class,\s
\t\t\t'gift_receiver' => Models\\GiftGiftReceiver::class,\s
\t\t\t'gift_timelines' => Models\\GiftGiftTimeline::class
		));
        return $gift;
    }
""");
  }

  @Test
  void resourcesShouldFollowSortOrderInResultFile() throws IOException {
    var token = buildResource("token").withSortOrder(5).done();
    var discount = buildResource("discount").withSortOrder(2).done();
    var spec = buildSpec().withResources(token, discount).done();

    List<FileOp> fileOps = phpSdkGen.generate("/php/lib/ChargeBee", spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(3);
    assertPhpResultFileContent(
        writeStringFileOp,
        """

    public function discount()\s
    {
        $discount = $this->_get('discount', Models\\Discount::class);
        return $discount;
    }

    public function token()\s
    {
        $token = $this->_get('token', Models\\Token::class);
        return $token;
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(5),
        "/php/lib/ChargeBee",
        "Result.php",
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/php/samples/result_2.phpf"));
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

    List<FileOp> fileOps = phpSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertPhpResultFileContent(
        writeStringFileOp,
        """

    public function invoice()\s
    {
        $invoice = $this->_get('invoice', Models\\Invoice::class,\s
        array(\s
\t\t\t'line_items' => Models\\InvoiceLineItem::class,\s
\t\t\t'discounts' => Models\\InvoiceDiscount::class
\t\t));
        return $invoice;
    }

    public function unbilledCharge()\s
    {
        $unbilled_charge = $this->_get('unbilled_charge', Models\\UnbilledCharge::class);
        return $unbilled_charge;
    }

    public function invoices()
    {
        $invoices = $this->_getList('invoices', Models\\Invoice::class,
        array(\s
\t\t\t'line_items' => Models\\InvoiceLineItem::class,\s
\t\t\t'discounts' => Models\\InvoiceDiscount::class
\t\t));
        return $invoices;
    }
""");
  }
}
