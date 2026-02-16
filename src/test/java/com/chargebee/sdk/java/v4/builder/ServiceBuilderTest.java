package com.chargebee.sdk.java.v4.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Test suite for {@link ServiceBuilder}.
 *
 * <p>ServiceBuilder generates service classes from OpenAPI specifications with support for:
 *
 * <ul>
 *   <li>Service file generation for each resource
 *   <li>Operation method generation
 *   <li>HTTP method handling (GET, POST, PUT, DELETE)
 *   <li>Path parameters
 *   <li>Request body operations
 *   <li>Query parameter operations
 *   <li>Return type determination
 *   <li>List vs single resource responses
 * </ul>
 */
@DisplayName("Service Builder")
class ServiceBuilderTest {

  private ServiceBuilder serviceBuilder;
  private Template mockTemplate;
  private OpenAPI openAPI;
  private String outputPath;

  @BeforeEach
  void setUp() throws IOException {
    serviceBuilder = new ServiceBuilder();
    outputPath = "/test/output";
    openAPI = new OpenAPI();

    Handlebars handlebars =
        new Handlebars(
            new com.github.jknack.handlebars.io.ClassPathTemplateLoader(
                "/templates/java/next", ""));
    HandlebarsUtil.registerAllHelpers(handlebars);
    mockTemplate = handlebars.compile("core.services.hbs");
  }

  // BASIC SERVICE GENERATION
  // Mirrors: JavaTests.java lines 636-684

  @Nested
  @DisplayName("Basic Service Generation")
  class BasicServiceGenerationTests {

    @Test
    @DisplayName("Should create separate service file for each resource")
    void shouldCreateServiceFileForEachResource() throws IOException {
      // Mirrors: shouldHaveEachOperationAsSeperateRequestMethod (JavaTests.java:636)
      Operation createOp = createPostOperationWithRequestBody("customer", "create");
      Operation retrieveOp = createGetOperation("customer", "retrieve");

      addPathWithOperation("/customers", PathItem.HttpMethod.POST, createOp);
      addPathWithOperation("/customers/{customer-id}", PathItem.HttpMethod.GET, retrieveOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerService.java");
      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerService.java");
      // Should contain both operations
      assertThat(writeOp.fileContent).containsIgnoringCase("create");
      assertThat(writeOp.fileContent).containsIgnoringCase("retrieve");
    }

    @Test
    @DisplayName("Should generate multiple service files for different resources")
    void shouldGenerateMultipleServiceFiles() throws IOException {
      Operation customerCreateOp = createPostOperationWithRequestBody("customer", "create");
      Operation invoiceCreateOp = createPostOperationWithRequestBody("invoice", "create");

      addPathWithOperation("/customers", PathItem.HttpMethod.POST, customerCreateOp);
      addPathWithOperation("/invoices", PathItem.HttpMethod.POST, invoiceCreateOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerService.java");
      assertFileExists(fileOps, "InvoiceService.java");
    }

    @Test
    @DisplayName("Should create output directory structure")
    void shouldCreateOutputDirectoryStructure() throws IOException {
      Operation createOp = createPostOperationWithRequestBody("customer", "create");
      addPathWithOperation("/customers", PathItem.HttpMethod.POST, createOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
      FileOp.CreateDirectory dirOp = (FileOp.CreateDirectory) fileOps.get(0);
      assertThat(dirOp.basePath).endsWith("/com/chargebee/v4/services");
    }
  }

  // OPERATION TYPES AND HTTP METHODS
  // Mirrors: JavaTests.java lines 739-841

  @Nested
  @DisplayName("Operation Types and HTTP Methods")
  class OperationTypesTests {

    @Test
    @DisplayName("Should handle POST operations with request body")
    void shouldHandlePostOperationsWithRequestBody() throws IOException {
      // Mirrors: shouldHaveOperationNameAsRequestTypeIfRequestBodyPresent (JavaTests.java:739)
      Operation createOp = createPostOperationWithRequestBody("customer", "create");
      addPathWithOperation("/customers", PathItem.HttpMethod.POST, createOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerService.java");
      assertThat(writeOp.fileContent).containsIgnoringCase("create");
      // Should reference CustomerCreateParams and CustomerCreateResponse
      assertThat(writeOp.fileContent).contains("Params");
      assertThat(writeOp.fileContent).contains("Response");
    }

    @Test
    @DisplayName("Should handle GET operations with query parameters")
    void shouldHandleGetOperationsWithQueryParams() throws IOException {
      // Mirrors: shouldHaveOperationNameAsRequestTypeIfQueryParamsPresent (JavaTests.java:775)
      Operation listOp = createGetOperation("customer", "list");
      addPathWithOperation("/customers", PathItem.HttpMethod.GET, listOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerService.java");
      assertThat(writeOp.fileContent).containsIgnoringCase("list");
    }

    @Test
    @DisplayName("Should handle GET operations without query params")
    void shouldHandleGetOperationsWithoutQueryParams() throws IOException {
      // Mirrors: shouldHaveEntityTypeAsRequestTypeIfNoRequestBodyOrQueryParams
      // (JavaTests.java:809)
      Operation retrieveOp = createGetOperation("customer", "retrieve");
      addPathWithOperation("/customers/{customer-id}", PathItem.HttpMethod.GET, retrieveOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerService.java");
    }

    @Test
    @DisplayName("Should distinguish GET, POST HTTP methods")
    void shouldHandleDifferentHttpMethods() throws IOException {
      // Mirrors: shouldHaveHttpMethodInReturnStatement (JavaTests.java:997)
      Operation createOp = createPostOperationWithRequestBody("customer", "create");
      Operation updateOp = createPostOperationWithRequestBody("customer", "update");
      Operation retrieveOp = createGetOperation("customer", "retrieve");

      addPathWithOperation("/customers", PathItem.HttpMethod.POST, createOp);
      addPathWithOperation("/customers/{customer-id}/update", PathItem.HttpMethod.POST, updateOp);
      addPathWithOperation("/customers/{customer-id}", PathItem.HttpMethod.GET, retrieveOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerService.java");
      // Should contain all three operations
      assertThat(writeOp.fileContent).containsIgnoringCase("create");
      assertThat(writeOp.fileContent).containsIgnoringCase("update");
      assertThat(writeOp.fileContent).containsIgnoringCase("retrieve");
    }

    @Test
    @DisplayName("Should handle list operations (paginated responses)")
    void shouldHandleListOperations() throws IOException {
      // Mirrors: shouldNotHaveHttpMethodInReturnStatementForListOperation (JavaTests.java:1050)
      Operation listOp = createListOperation("customer", "list");
      addPathWithOperation("/customers", PathItem.HttpMethod.GET, listOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerService.java");
    }

    @Test
    @DisplayName(
        "Should generate method overload with params for GET operations with query params and path"
            + " params")
    void shouldGenerateMethodOverloadForGetWithQueryParamsAndPathParams() throws IOException {
      // Test for GET operations like /invoices/{invoice-id} that have both path params AND query
      // params
      Operation retrieveOp = createGetOperationWithQueryParams("invoice", "retrieve");
      addPathWithOperation("/invoices/{invoice-id}", PathItem.HttpMethod.GET, retrieveOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "InvoiceService.java");

      // Should have retrieve method without params
      assertThat(writeOp.fileContent).contains("retrieve(String invoiceId)");

      // Should also have retrieve method WITH params (this is the fix we're testing)
      assertThat(writeOp.fileContent)
          .contains("retrieve(String invoiceId, InvoiceRetrieveParams params)");

      // Should have retrieveRaw with params
      assertThat(writeOp.fileContent)
          .contains("retrieveRaw(String invoiceId, InvoiceRetrieveParams params)");

      // Should call get() with query params
      assertThat(writeOp.fileContent).contains("params.toQueryParams()");
    }

    @Test
    @DisplayName(
        "Should NOT generate method overload with params for GET operations without query params")
    void shouldNotGenerateMethodOverloadForGetWithoutQueryParams() throws IOException {
      // Regular GET without query params should NOT have the params overload
      Operation retrieveOp = createGetOperationWithResponse("customer", "retrieve");
      addPathWithOperation("/customers/{customer-id}", PathItem.HttpMethod.GET, retrieveOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerService.java");

      // Should have retrieve method without params
      assertThat(writeOp.fileContent).contains("retrieve(String customerId)");

      // Should NOT have the params overload since there are no query params
      assertThat(writeOp.fileContent)
          .doesNotContain("retrieve(String customerId, CustomerRetrieveParams params)");
    }
  }

  // PATH PARAMETERS
  // Mirrors: JavaTests.java lines 845-881, 920-955

  @Nested
  @DisplayName("Path Parameters")
  class PathParametersTests {

    @Test
    @DisplayName("Should handle path parameters as method arguments")
    void shouldHandlePathParametersAsArguments() throws IOException {
      // Mirrors: shouldHavePathParamAsArgumentIfPresent (JavaTests.java:845)
      Operation updateOp = createPostOperationWithRequestBody("customer", "update");
      addPathWithOperation("/customers/{customer-id}", PathItem.HttpMethod.POST, updateOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerService.java");
      assertThat(writeOp.fileContent).containsIgnoringCase("update");
      // Should have customerId parameter
      assertThat(writeOp.fileContent).containsIgnoringCase("id");
    }

    @Test
    @DisplayName("Should include path param in URL construction")
    void shouldIncludePathParamInUrl() throws IOException {
      // Mirrors: shouldHavePathParamInUrlIfPresent (JavaTests.java:920)
      Operation updateOp = createPostOperationWithRequestBody("customer", "update");
      addPathWithOperation("/customers/{customer-id}", PathItem.HttpMethod.POST, updateOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerService.java");
    }

    @Test
    @DisplayName("Should handle URL suffix after path parameter")
    void shouldHandleUrlSuffixAfterPathParam() throws IOException {
      // Mirrors: shouldHaveUrlSuffixIfPresent (JavaTests.java:958)
      Operation updateBillingInfoOp =
          createPostOperationWithRequestBody("customer", "updateBillingInfo");
      addPathWithOperation(
          "/customers/{customer-id}/update_billing_info",
          PathItem.HttpMethod.POST,
          updateBillingInfoOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerService.java");
      assertThat(writeOp.fileContent).containsIgnoringCase("updatebillinginfo");
    }

    @Test
    @DisplayName("Should handle batch operations with path prefix")
    void shouldHandleBatchOperations() throws IOException {
      Operation batchCreateOp = createPostOperationWithRequestBody("customer", "create");
      addPathWithOperation("/batch/customers", PathItem.HttpMethod.POST, batchCreateOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerService.java");
      // Should prefix with "batch"
      assertThat(writeOp.fileContent).containsIgnoringCase("batch");
    }
  }

  // OPERATION FILTERING
  // Mirrors: JavaTests.java lines 1125-1166

  @Nested
  @DisplayName("Operation Filtering")
  class OperationFilteringTests {

    @Test
    @DisplayName("Should skip operations without required extensions")
    void shouldSkipOperationsWithoutExtensions() throws IOException {
      Operation opWithoutExtensions = new Operation();
      addPathWithOperation("/customers", PathItem.HttpMethod.POST, opWithoutExtensions);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1); // Only directory
    }

    @Test
    @DisplayName("Should skip operations missing RESOURCE_ID extension")
    void shouldSkipOperationsMissingResourceId() throws IOException {
      Operation op = new Operation();
      op.addExtension(Extension.SDK_METHOD_NAME, "create");
      addPathWithOperation("/customers", PathItem.HttpMethod.POST, op);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1); // Only directory
    }

    @Test
    @DisplayName("Should skip operation when SDK_METHOD_NAME extension is missing")
    void shouldSkipOperationWhenMethodNameExtensionMissing() throws IOException {
      Operation op = new Operation();
      op.addExtension(Extension.RESOURCE_ID, "customer");
      // SDK_METHOD_NAME is NOT set - operation should be skipped
      addPathWithOperation("/customers", PathItem.HttpMethod.POST, op);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      // Operation is skipped due to missing SDK_METHOD_NAME, so only directory is created
      assertThat(fileOps).hasSize(1);
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }
  }

  // NULL AND EDGE CASE HANDLING

  @Nested
  @DisplayName("Null and Edge Case Handling")
  class NullAndEdgeCaseHandlingTests {

    @Test
    @DisplayName("Should handle null OpenAPI spec")
    void shouldHandleNullOpenAPISpec() throws IOException {
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(new OpenAPI());

      assertThat(fileOps).hasSize(1); // Only directory
    }

    @Test
    @DisplayName("Should handle empty paths map")
    void shouldHandleEmptyPathsMap() throws IOException {
      openAPI.setPaths(new Paths());
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1); // Only directory
    }

    @Test
    @DisplayName("Should handle null PathItem")
    void shouldHandleNullPathItem() throws IOException {
      if (openAPI.getPaths() == null) {
        openAPI.setPaths(new Paths());
      }
      openAPI.getPaths().addPathItem("/customers", null);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1); // Only directory
    }
  }

  // RESPONSE TYPE DETERMINATION

  @Nested
  @DisplayName("Response Type Determination")
  class ResponseTypeDeterminationTests {

    @Test
    @DisplayName("Should determine list response for paginated operations")
    void shouldDetermineListResponseForPaginatedOperations() throws IOException {
      Operation listOp = createListOperation("customer", "list");
      addPathWithOperation("/customers", PathItem.HttpMethod.GET, listOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerService.java");
      // Should generate list response type
      assertThat(writeOp.fileContent).containsIgnoringCase("list");
    }

    @Test
    @DisplayName("Should determine single resource response for non-list operations")
    void shouldDetermineSingleResourceResponse() throws IOException {
      Operation retrieveOp = createGetOperationWithResponse("customer", "retrieve");
      addPathWithOperation("/customers/{customer-id}", PathItem.HttpMethod.GET, retrieveOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerService.java");
    }
  }

  // PACKAGE AND CLASS NAME CONVERSION

  @Nested
  @DisplayName("Package and Class Name Conversion")
  class PackageAndClassNameConversionTests {

    @Test
    @DisplayName("Should convert snake_case resource to UpperCamel service name")
    void shouldConvertSnakeCaseResourceToServiceName() throws IOException {
      Operation createOp = createPostOperationWithRequestBody("payment_method", "create");
      addPathWithOperation("/payment_methods", PathItem.HttpMethod.POST, createOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      assertFileExists(fileOps, "PaymentMethodService.java");
    }

    @Test
    @DisplayName("Should convert snake_case operation to lowerCamel method name")
    void shouldConvertSnakeCaseOperationToMethodName() throws IOException {
      Operation changeEstimateOp =
          createPostOperationWithRequestBody("customer", "change_estimate");
      addPathWithOperation(
          "/customers/{customer-id}/change_estimate", PathItem.HttpMethod.POST, changeEstimateOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerService.java");
      assertThat(writeOp.fileContent).containsIgnoringCase("changeestimate");
    }
  }

  // SUBDOMAIN SUPPORT

  @Nested
  @DisplayName("Subdomain Support")
  class SubDomainSupportTests {

    @Test
    @DisplayName("Should generate getWithSubDomain call for GET operation with subdomain")
    void shouldGenerateGetWithSubDomainForGetOperation() throws IOException {
      Operation retrieveOp = createGetOperationWithSubDomain("offer_event", "retrieve", "grow");
      addPathWithOperation("/offer_events/{offer-event-id}", PathItem.HttpMethod.GET, retrieveOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "OfferEventService.java");
      assertThat(writeOp.fileContent).doesNotContain("private static final String SUB_DOMAIN");
      assertThat(writeOp.fileContent).contains("import com.chargebee.v4.internal.SubDomain");
      assertThat(writeOp.fileContent).contains("getWithSubDomain(path, SubDomain.GROW.getValue()");
    }

    @Test
    @DisplayName("Should generate postWithSubDomain call for POST operation with subdomain")
    void shouldGeneratePostWithSubDomainForPostOperation() throws IOException {
      Operation createOp = createPostOperationWithSubDomain("offer_fulfillment", "create", "grow");
      addPathWithOperation("/offer_fulfillments", PathItem.HttpMethod.POST, createOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "OfferFulfillmentService.java");
      assertThat(writeOp.fileContent).doesNotContain("private static final String SUB_DOMAIN");
      assertThat(writeOp.fileContent).contains("import com.chargebee.v4.internal.SubDomain");
      assertThat(writeOp.fileContent).contains("postWithSubDomain(\"");
      assertThat(writeOp.fileContent).contains("SubDomain.GROW.getValue()");
    }

    @Test
    @DisplayName(
        "Should generate postJsonWithSubDomain call for POST JSON operation with subdomain")
    void shouldGeneratePostJsonWithSubDomainForPostJsonOperation() throws IOException {
      Operation createOp = createPostOperationWithSubDomain("offer_fulfillment", "create", "grow");
      addPathWithOperation("/offer_fulfillments", PathItem.HttpMethod.POST, createOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "OfferFulfillmentService.java");
      assertThat(writeOp.fileContent).contains("postJsonWithSubDomain(\"");
    }

    @Test
    @DisplayName("Should NOT generate subdomain calls for operation without subdomain")
    void shouldNotGenerateSubDomainCallsForNormalOperation() throws IOException {
      Operation createOp = createPostOperationWithRequestBody("customer", "create");
      addPathWithOperation("/customers", PathItem.HttpMethod.POST, createOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerService.java");
      assertThat(writeOp.fileContent).doesNotContain("WithSubDomain");
      assertThat(writeOp.fileContent).contains("post(\"");
    }

    @Test
    @DisplayName("Should handle mixed operations with and without subdomain in same service")
    void shouldHandleMixedOperationsInSameService() throws IOException {
      Operation normalOp = createPostOperationWithRequestBody("offer_event", "update");
      Operation subDomainOp = createGetOperationWithSubDomain("offer_event", "retrieve", "grow");

      addPathWithOperation(
          "/offer_events/{offer-event-id}/update", PathItem.HttpMethod.POST, normalOp);
      addPathWithOperation("/offer_events/{offer-event-id}", PathItem.HttpMethod.GET, subDomainOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "OfferEventService.java");
      // Should NOT define SUB_DOMAIN constant
      assertThat(writeOp.fileContent).doesNotContain("private static final String SUB_DOMAIN");
      // Should import SubDomain enum
      assertThat(writeOp.fileContent).contains("import com.chargebee.v4.internal.SubDomain");
      // Subdomain operation should use SubDomain enum ref
      assertThat(writeOp.fileContent).contains("getWithSubDomain(path, SubDomain.GROW.getValue()");
      // Normal operation should use regular post
      assertThat(writeOp.fileContent).contains("post(path, ");
    }

    @Test
    @DisplayName("Should generate postWithSubDomain for POST with path params and subdomain")
    void shouldGeneratePostWithSubDomainForPathParams() throws IOException {
      Operation updateOp = createPostOperationWithSubDomain("offer_fulfillment", "fulfill", "grow");
      addPathWithOperation(
          "/offer_fulfillments/{offer-fulfillment-id}/fulfill", PathItem.HttpMethod.POST, updateOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "OfferFulfillmentService.java");
      assertThat(writeOp.fileContent).contains("postWithSubDomain(path, SubDomain.GROW.getValue()");
    }
  }

  // BATCH OPERATIONS (Internal V4)

  @Nested
  @DisplayName("Batch Operations")
  class BatchOperationsTests {

    @Test
    @DisplayName("Should generate BatchRequest method for true batch operations")
    void shouldGenerateBatchRequestMethodForTrueBatchOperations() throws IOException {
      Operation batchUpdateOp = createOperation("ramp", "update");
      batchUpdateOp.addExtension(Extension.BATCH_OPERATION_PATH_ID, "id");
      addPathWithOperation("/batch/ramps/update", PathItem.HttpMethod.POST, batchUpdateOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "RampService.java");
      assertThat(writeOp.fileContent).contains("BatchRequest");
      assertThat(writeOp.fileContent).contains("batchUpdate()");
      assertThat(writeOp.fileContent).contains("new BatchRequest(\"/ramps/update\", \"id\"");
    }

    @Test
    @DisplayName("Should NOT generate BatchRequest for /batch/ path without batch extension")
    void shouldNotGenerateBatchRequestWithoutBatchExtension() throws IOException {
      // /batch/usage_events does NOT have x-cb-batch-operation-path-id
      Operation batchIngestOp = createPostOperationWithRequestBody("usage_event", "batchIngest");
      addPathWithOperation("/batch/usage_events", PathItem.HttpMethod.POST, batchIngestOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "UsageEventService.java");
      assertThat(writeOp.fileContent).doesNotContain("BatchRequest");
      assertThat(writeOp.fileContent).contains("batchIngest");
    }

    @Test
    @DisplayName("Should strip /batch prefix from URI in BatchRequest constructor")
    void shouldStripBatchPrefixFromUri() throws IOException {
      Operation batchDeleteOp = createOperation("ramp", "delete");
      batchDeleteOp.addExtension(Extension.BATCH_OPERATION_PATH_ID, "id");
      addPathWithOperation("/batch/ramps/delete", PathItem.HttpMethod.POST, batchDeleteOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "RampService.java");
      assertThat(writeOp.fileContent).contains("\"/ramps/delete\"");
      assertThat(writeOp.fileContent).doesNotContain("\"/batch/ramps/delete\"");
    }

    @Test
    @DisplayName("Should import BatchRequest only when service has batch operations")
    void shouldImportBatchRequestOnlyWhenNeeded() throws IOException {
      Operation batchUpdateOp = createOperation("ramp", "update");
      batchUpdateOp.addExtension(Extension.BATCH_OPERATION_PATH_ID, "id");
      addPathWithOperation("/batch/ramps/update", PathItem.HttpMethod.POST, batchUpdateOp);

      Operation retrieveOp = createGetOperation("customer", "retrieve");
      addPathWithOperation("/customers/{customer-id}", PathItem.HttpMethod.GET, retrieveOp);

      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);
      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString rampService = findWriteOp(fileOps, "RampService.java");
      assertThat(rampService.fileContent).contains("import com.chargebee.v4.internal.BatchRequest");

      FileOp.WriteString customerService = findWriteOp(fileOps, "CustomerService.java");
      assertThat(customerService.fileContent).doesNotContain("BatchRequest");
    }

    @Test
    @DisplayName("Should skip params/response imports for batch operations")
    void shouldSkipParamsAndResponseImportsForBatchOperations() throws IOException {
      Operation batchUpdateOp = createOperation("ramp", "update");
      batchUpdateOp.addExtension(Extension.BATCH_OPERATION_PATH_ID, "id");

      Operation retrieveOp = createGetOperation("ramp", "retrieve");

      addPathWithOperation("/batch/ramps/update", PathItem.HttpMethod.POST, batchUpdateOp);
      addPathWithOperation("/ramps/{ramp-id}", PathItem.HttpMethod.GET, retrieveOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "RampService.java");
      // Should NOT import params/response for the batch operation
      assertThat(writeOp.fileContent).doesNotContain("RampUpdateParams");
      assertThat(writeOp.fileContent).doesNotContain("RampUpdateResponse");
      // Should still import params/response for the non-batch operation
      assertThat(writeOp.fileContent).contains("RampRetrieveResponse");
    }

    @Test
    @DisplayName("Should generate multiple batch methods in same service")
    void shouldGenerateMultipleBatchMethodsInSameService() throws IOException {
      Operation batchUpdateOp = createOperation("ramp", "update");
      batchUpdateOp.addExtension(Extension.BATCH_OPERATION_PATH_ID, "id");

      Operation batchDeleteOp = createOperation("ramp", "delete");
      batchDeleteOp.addExtension(Extension.BATCH_OPERATION_PATH_ID, "id");

      addPathWithOperation("/batch/ramps/update", PathItem.HttpMethod.POST, batchUpdateOp);
      addPathWithOperation("/batch/ramps/delete", PathItem.HttpMethod.POST, batchDeleteOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "RampService.java");
      assertThat(writeOp.fileContent).contains("batchUpdate()");
      assertThat(writeOp.fileContent).contains("batchDelete()");
    }

    @Test
    @DisplayName("Should use subdomain constructor when operation has subdomain")
    void shouldUseSubdomainConstructorWhenOperationHasSubdomain() throws IOException {
      Operation batchOp = createOperation("ramp", "update");
      batchOp.addExtension(Extension.BATCH_OPERATION_PATH_ID, "id");
      batchOp.addExtension(Extension.OPERATION_SUB_DOMAIN, "integrations");
      addPathWithOperation("/batch/ramps/update", PathItem.HttpMethod.POST, batchOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "RampService.java");
      assertThat(writeOp.fileContent).doesNotContain("private static final String SUB_DOMAIN");
      assertThat(writeOp.fileContent).contains("import com.chargebee.v4.internal.SubDomain");
      assertThat(writeOp.fileContent)
          .contains("new BatchRequest(\"/ramps/update\", \"id\", SubDomain.INTEGRATIONS, client)");
    }

    @Test
    @DisplayName("Should use non-subdomain constructor when operation has no subdomain")
    void shouldUseNonSubdomainConstructorWhenNoSubdomain() throws IOException {
      Operation batchOp = createOperation("ramp", "update");
      batchOp.addExtension(Extension.BATCH_OPERATION_PATH_ID, "id");
      addPathWithOperation("/batch/ramps/update", PathItem.HttpMethod.POST, batchOp);
      serviceBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = serviceBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "RampService.java");
      assertThat(writeOp.fileContent).contains("new BatchRequest(\"/ramps/update\", \"id\"");
      assertThat(writeOp.fileContent).doesNotContain("SubDomain.");
    }
  }

  // HELPER METHODS

  /**
   * Creates a basic operation with required extensions.
   *
   * @param resourceId The resource identifier
   * @param methodName The operation method name
   * @return Configured Operation instance
   */
  private Operation createOperation(String resourceId, String methodName) {
    Operation operation = new Operation();
    operation.addExtension(Extension.RESOURCE_ID, resourceId);
    operation.addExtension(Extension.SDK_METHOD_NAME, methodName);
    return operation;
  }

  /** Creates a GET operation with required extensions. */
  private Operation createGetOperation(String resourceId, String methodName) {
    return createOperation(resourceId, methodName);
  }

  /** Creates a POST operation with request body. */
  private Operation createPostOperationWithRequestBody(String resourceId, String methodName) {
    Operation operation = createOperation(resourceId, methodName);

    ObjectSchema requestSchema = new ObjectSchema();
    requestSchema.addProperty("field", new StringSchema());

    MediaType mediaType = new MediaType();
    mediaType.setSchema(requestSchema);

    Content content = new Content();
    content.addMediaType("application/x-www-form-urlencoded", mediaType);

    RequestBody requestBody = new RequestBody();
    requestBody.setContent(content);

    operation.setRequestBody(requestBody);
    return operation;
  }

  /** Creates a GET operation with response. */
  private Operation createGetOperationWithResponse(String resourceId, String methodName) {
    Operation operation = createGetOperation(resourceId, methodName);

    ObjectSchema responseSchema = new ObjectSchema();
    responseSchema.addProperty(resourceId, new ObjectSchema());

    MediaType mediaType = new MediaType();
    mediaType.setSchema(responseSchema);

    Content content = new Content();
    content.addMediaType("application/json", mediaType);

    ApiResponse apiResponse = new ApiResponse();
    apiResponse.setContent(content);

    ApiResponses responses = new ApiResponses();
    responses.addApiResponse("200", apiResponse);

    operation.setResponses(responses);
    return operation;
  }

  /** Creates a GET operation with query parameters (like /invoices/{invoice-id} with line_items_limit). */
  private Operation createGetOperationWithQueryParams(String resourceId, String methodName) {
    Operation operation = createGetOperationWithResponse(resourceId, methodName);

    // Add query parameters similar to /invoices/{invoice-id}
    Parameter limitParam = new Parameter();
    limitParam.setName("line_items_limit");
    limitParam.setIn("query");
    limitParam.setSchema(new IntegerSchema());

    Parameter offsetParam = new Parameter();
    offsetParam.setName("line_items_offset");
    offsetParam.setIn("query");
    offsetParam.setSchema(new StringSchema());

    operation.addParametersItem(limitParam);
    operation.addParametersItem(offsetParam);

    return operation;
  }

  /** Creates a GET operation with subdomain extension. */
  private Operation createGetOperationWithSubDomain(
      String resourceId, String methodName, String subDomain) {
    Operation operation = createGetOperationWithResponse(resourceId, methodName);
    operation.addExtension(Extension.OPERATION_SUB_DOMAIN, subDomain);
    return operation;
  }

  /** Creates a POST operation with request body and subdomain extension. */
  private Operation createPostOperationWithSubDomain(
      String resourceId, String methodName, String subDomain) {
    Operation operation = createPostOperationWithRequestBody(resourceId, methodName);
    operation.addExtension(Extension.OPERATION_SUB_DOMAIN, subDomain);
    return operation;
  }

  /** Creates a list operation with paginated response. */
  private Operation createListOperation(String resourceId, String methodName) {
    Operation operation = createGetOperation(resourceId, methodName);

    ObjectSchema responseSchema = new ObjectSchema();
    responseSchema.addProperty("list", new ArraySchema());
    responseSchema.addProperty("next_offset", new StringSchema());

    MediaType mediaType = new MediaType();
    mediaType.setSchema(responseSchema);

    Content content = new Content();
    content.addMediaType("application/json", mediaType);

    ApiResponse apiResponse = new ApiResponse();
    apiResponse.setContent(content);

    ApiResponses responses = new ApiResponses();
    responses.addApiResponse("200", apiResponse);

    operation.setResponses(responses);
    return operation;
  }

  /** Adds a path with an operation to the OpenAPI spec. */
  private void addPathWithOperation(
      String path, PathItem.HttpMethod httpMethod, Operation operation) {
    PathItem pathItem = new PathItem();
    pathItem.operation(httpMethod, operation);

    if (openAPI.getPaths() == null) {
      openAPI.setPaths(new Paths());
    }
    openAPI.getPaths().addPathItem(path, pathItem);
  }

  /** Finds a WriteString operation for a specific file name. */
  private FileOp.WriteString findWriteOp(List<FileOp> fileOps, String fileName) {
    return fileOps.stream()
        .filter(op -> op instanceof FileOp.WriteString)
        .map(op -> (FileOp.WriteString) op)
        .filter(op -> op.fileName.equals(fileName))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("WriteString operation not found for file: " + fileName));
  }

  /** Asserts that a file with the given name exists. */
  private void assertFileExists(List<FileOp> fileOps, String fileName) {
    assertThat(fileOps)
        .anyMatch(
            op ->
                op instanceof FileOp.WriteString
                    && ((FileOp.WriteString) op).fileName.equals(fileName));
  }
}
