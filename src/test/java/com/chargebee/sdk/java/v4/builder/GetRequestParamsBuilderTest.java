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
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Comprehensive test suite for GetRequestParamsBuilder.
 *
 * <p>This class tests the generation of type-safe parameter builders for GET operations from
 * OpenAPI specifications. It covers:
 * <ul>
 *   <li>Basic parameter generation (strings, integers, etc.)</li>
 *   <li>Filter parameters with operations (is, is_not, starts_with, etc.)</li>
 *   <li>Sort parameters with direction control (asc/desc)</li>
 *   <li>Enum parameters and their code generation</li>
 *   <li>Nested submodel parameters</li>
 *   <li>Edge cases and validation scenarios</li>
 * </ul>
 */
class GetRequestParamsBuilderTest {

  private GetRequestParamsBuilder paramsBuilder;
  private Template mockTemplate;
  private OpenAPI openAPI;
  private String outputPath;

  @BeforeEach
  void setUp() throws IOException {
    paramsBuilder = new GetRequestParamsBuilder();
    outputPath = "/test/output";
    openAPI = new OpenAPI();

    Handlebars handlebars =
        new Handlebars(
            new com.github.jknack.handlebars.io.ClassPathTemplateLoader(
                "/templates/java/next", ""));
    HandlebarsUtil.registerAllHelpers(handlebars);
    mockTemplate = handlebars.compile("core.get.params.builder.hbs");
  }

  // BUILDER CONFIGURATION TESTS

  @Nested
  @DisplayName("Builder Configuration")
  class BuilderConfigurationTests {

    @Test
    @DisplayName("Should configure output directory path")
    void shouldConfigureOutputDirectoryPath() {
      GetRequestParamsBuilder result = paramsBuilder.withOutputDirectoryPath(outputPath);

      assertThat(result).isSameAs(paramsBuilder);
    }

    @Test
    @DisplayName("Should configure template")
    void shouldConfigureTemplate() {
      GetRequestParamsBuilder result = paramsBuilder.withTemplate(mockTemplate);

      assertThat(result).isSameAs(paramsBuilder);
    }

    @Test
    @DisplayName("Should create output directory structure on build")
    void shouldCreateOutputDirectoryOnBuild() throws IOException {
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
      FileOp.CreateDirectory dirOp = (FileOp.CreateDirectory) fileOps.get(0);
      assertThat(dirOp.basePath).endsWith("/v4/models");
    }
  }

  // BASIC PARAMETER GENERATION TESTS

  @Nested
  @DisplayName("Basic Parameter Generation")
  class BasicParameterGenerationTests {

    @Test
    @DisplayName("Should generate params class for GET operation with query parameters")
    void shouldGenerateParamsForGetOperationWithQueryParams() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addStringQueryParam(getOperation, "first_name");
      addStringQueryParam(getOperation, "last_name");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public final class CustomerListParams");
      assertThat(writeOp.fileContent)
          .contains("public CustomerListBuilder firstName(String value)");
      assertThat(writeOp.fileContent).contains("public CustomerListBuilder lastName(String value)");
    }

    @Test
    @DisplayName("Should skip operations without query parameters")
    void shouldSkipOperationsWithoutQueryParams() throws IOException {
      Operation getOperation = createGetOperation("customer", "retrieve");

      addPathWithOperation("/customers/{customer-id}", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1); // Only directory creation
    }

    @Test
    @DisplayName("Should skip non-GET operations")
    void shouldSkipNonGetOperations() throws IOException {
      Operation postOperation = createPostOperation("customer", "create");
      addStringQueryParam(postOperation, "first_name");

      PathItem pathItem = new PathItem();
      pathItem.setPost(postOperation);
      addPath("/customers", pathItem);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1); // Only directory creation
    }

    @Test
    @DisplayName("Should create proper directory structure for params")
    void shouldCreateProperDirectoryStructure() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addStringQueryParam(getOperation, "name");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("params"));
    }

    @Test
    @DisplayName("Should generate multiple param classes for different operations")
    void shouldGenerateMultipleParamClasses() throws IOException {
      Operation customerListOp = createGetOperation("customer", "list");
      addStringQueryParam(customerListOp, "name");

      Operation invoiceListOp = createGetOperation("invoice", "list");
      addStringQueryParam(invoiceListOp, "status");

      addPathWithOperation("/customers", customerListOp);
      addPathWithOperation("/invoices", invoiceListOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
      assertFileExists(fileOps, "InvoiceListParams.java");
    }
  }

  // FILTER PARAMETERS - CORE FUNCTIONALITY

  @Nested
  @DisplayName("Filter Parameters - Core Functionality")
  class FilterParametersTests {

    @Test
    @DisplayName("Should generate string filter with basic operations (is, is_not, starts_with)")
    void shouldGenerateStringFilterParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addStringFilterParam(getOperation, "first_name", List.of("is", "is_not", "starts_with"));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public FirstNameFilter firstName()");
      assertThat(writeOp.fileContent)
          .contains(
              "public static final class FirstNameFilter extends"
                  + " StringFilter<CustomerListBuilder>");
    }

    @Test
    @DisplayName("Should generate string filter with list operations (in, not_in)")
    void shouldGenerateStringFilterWithInOperation() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addStringFilterParam(getOperation, "id", List.of("in", "not_in"));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public IdFilter id()");
      assertThat(writeOp.fileContent)
          .contains("public static final class IdFilter extends StringFilter<CustomerListBuilder>");
    }

    @Test
    @DisplayName("Should generate string filter with presence operator (is_present)")
    void shouldGenerateStringFilterWithPresenceOperator() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addStringFilterParam(getOperation, "first_name", List.of("is", "is_not", "is_present"));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public FirstNameFilter firstName()");
      assertThat(writeOp.fileContent)
          .contains(
              "public static final class FirstNameFilter extends"
                  + " StringFilter<CustomerListBuilder>");
    }

    @Test
    @DisplayName(
        "Should generate timestamp filter with date operations (after, before, on, between)")
    void shouldGenerateTimestampFilterParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addTimestampFilterParam(getOperation, "created_at");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public CreatedAtFilter createdAt()");
      assertThat(writeOp.fileContent)
          .contains(
              "public static final class CreatedAtFilter extends"
                  + " StringFilter<CustomerListBuilder>");
    }

    @Test
    @DisplayName("Should generate timestamp filter extending StringFilter")
    void shouldGenerateTimestampFilterExtendingStringFilter() throws IOException {
      Operation getOperation = createGetOperation("subscription", "list");
      addTimestampFilterParam(getOperation, "created_at");

      addPathWithOperation("/subscriptions", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "SubscriptionListParams.java");
      assertThat(writeOp.fileContent).contains("public CreatedAtFilter createdAt()");
      assertThat(writeOp.fileContent)
          .contains(
              "public static final class CreatedAtFilter extends"
                  + " StringFilter<SubscriptionListBuilder>");
    }

    @Test
    @DisplayName("Should generate boolean filter parameter")
    void shouldGenerateBooleanFilterParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addBooleanFilterParam(getOperation, "auto_close_invoices");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent)
          .contains("public AutoCloseInvoicesFilter autoCloseInvoices()");
      assertThat(writeOp.fileContent)
          .contains(
              "public static final class AutoCloseInvoicesFilter extends"
                  + " StringFilter<CustomerListBuilder>");
    }

    @Test
    @DisplayName("Should handle multiple filter parameters in single operation")
    void shouldHandleMultipleFilterParameters() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addStringFilterParam(getOperation, "first_name", List.of("is", "starts_with"));
      addStringFilterParam(getOperation, "email", List.of("is", "is_not"));
      addTimestampFilterParam(getOperation, "created_at");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public FirstNameFilter firstName()");
      assertThat(writeOp.fileContent).contains("public EmailFilter email()");
      assertThat(writeOp.fileContent).contains("public CreatedAtFilter createdAt()");
    }
  }

  // FILTER PARAMETERS - OPERATION DETECTION

  @Nested
  @DisplayName("Filter Parameters - Operation Detection")
  class FilterOperationDetectionTests {

    @Test
    @DisplayName("Should recognize all standard string filter operations")
    void shouldRecognizeAllStringFilterOperations() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema filterSchema = new ObjectSchema();
      filterSchema.setType("object");
      filterSchema.addExtension(Extension.IS_FILTER_PARAMETER, true);
      filterSchema.addProperty("is", new StringSchema());
      filterSchema.addProperty("is_not", new StringSchema());
      filterSchema.addProperty("starts_with", new StringSchema());
      filterSchema.addProperty("in", new StringSchema());
      filterSchema.addProperty("not_in", new StringSchema());
      filterSchema.addProperty("is_present", new StringSchema());

      Parameter param = new Parameter();
      param.setName("email");
      param.setIn("query");
      param.setSchema(filterSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public EmailFilter email()");
      assertThat(writeOp.fileContent)
          .contains(
              "public static final class EmailFilter extends StringFilter<CustomerListBuilder>");
    }

    @Test
    @DisplayName("Should recognize date filter operations (after, before, on, between)")
    void shouldRecognizeAfterBeforeOnBetweenAsFilterOperations() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema dateFilterSchema = new ObjectSchema();
      dateFilterSchema.setType("object");
      dateFilterSchema.addExtension(Extension.IS_FILTER_PARAMETER, true);
      dateFilterSchema.addProperty("after", new IntegerSchema());
      dateFilterSchema.addProperty("before", new IntegerSchema());
      dateFilterSchema.addProperty("on", new IntegerSchema());
      dateFilterSchema.addProperty("between", new StringSchema());

      Parameter param = new Parameter();
      param.setName("created_at");
      param.setIn("query");
      param.setSchema(dateFilterSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public CreatedAtFilter createdAt()");
      assertThat(writeOp.fileContent)
          .contains(
              "public static final class CreatedAtFilter extends"
                  + " StringFilter<CustomerListBuilder>");
    }

    @Test
    @DisplayName("Should not treat non-filter properties as filter")
    void shouldNotTreatNonFilterPropertiesAsFilter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema nonFilterSchema = new ObjectSchema();
      nonFilterSchema.setType("object");
      nonFilterSchema.addProperty("custom_field", new StringSchema());
      nonFilterSchema.addProperty("metadata", new StringSchema());

      Parameter param = new Parameter();
      param.setName("options");
      param.setIn("query");
      param.setSchema(nonFilterSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      // Should generate submodel, not filter
      assertThat(writeOp.fileContent)
          .contains("public CustomerListBuilder options(OptionsParams value)");
      assertThat(writeOp.fileContent).contains("public static final class OptionsParams");
      assertThat(writeOp.fileContent).doesNotContain("OptionsFilter");
    }

    @Test
    @DisplayName("Should treat object with mixed filter and non-filter properties as filter")
    void shouldHandleMixedFilterAndNonFilterProperties() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema mixedSchema = new ObjectSchema();
      mixedSchema.setType("object");
      mixedSchema.addExtension(Extension.IS_FILTER_PARAMETER, true);
      mixedSchema.addProperty("is", new StringSchema()); // filter operation
      mixedSchema.addProperty("custom_data", new StringSchema()); // non-filter property

      Parameter param = new Parameter();
      param.setName("status");
      param.setIn("query");
      param.setSchema(mixedSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      // Should be treated as filter because it contains at least one filter operation
      assertThat(writeOp.fileContent).contains("public StatusFilter status()");
      assertThat(writeOp.fileContent).contains("public static final class StatusFilter");
    }

    @Test
    @DisplayName("Should not create filter for object without filter operations")
    void shouldNotCreateFilterForObjectWithoutFilterOperations() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema objectSchema = new ObjectSchema();
      objectSchema.setType("object");
      objectSchema.addProperty("street", new StringSchema());
      objectSchema.addProperty("city", new StringSchema());
      objectSchema.addProperty("zip", new StringSchema());

      Parameter param = new Parameter();
      param.setName("address");
      param.setIn("query");
      param.setSchema(objectSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).doesNotContain("AddressFilter");
      assertThat(writeOp.fileContent)
          .contains("public CustomerListBuilder address(AddressParams value)");
    }

    @Test
    @DisplayName("Should return false for empty properties map")
    void shouldReturnFalseForEmptyProperties() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema filterSchema = new ObjectSchema();
      filterSchema.setType("object");
      filterSchema.setProperties(Map.of()); // Empty properties

      Parameter param = new Parameter();
      param.setName("name");
      param.setIn("query");
      param.setSchema(filterSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).doesNotContain("NameFilter");
    }

    @Test
    @DisplayName("Should handle asc/desc properties in non-sort_by parameter as filter")
    void shouldHandleAscDescInNonSortParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema schema = new ObjectSchema();
      schema.setType("object");
      schema.addExtension(Extension.IS_FILTER_PARAMETER, true);
      schema.addProperty("asc", new StringSchema());
      schema.addProperty("desc", new StringSchema());

      Parameter param = new Parameter();
      param.setName("order"); // Not "sort_by"
      param.setIn("query");
      param.setSchema(schema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      // Should be treated as filter since name != "sort_by"
      assertThat(writeOp.fileContent).contains("public OrderFilter order()");
      assertThat(writeOp.fileContent).contains("public static final class OrderFilter");
    }

    @Test
    @DisplayName("Should recognize nested filter inside submodel")
    @SuppressWarnings("rawtypes")
    void shouldRecognizeNestedFilterInsideSubmodel() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      // Create a nested filter object
      java.util.Map<String, Schema> filterProps = new java.util.LinkedHashMap<>();
      filterProps.put("is", new IntegerSchema());
      filterProps.put("is_not", new IntegerSchema());

      ObjectSchema amountFilterSchema = new ObjectSchema();
      amountFilterSchema.setType("object");
      amountFilterSchema.addExtension(Extension.IS_FILTER_PARAMETER, true);
      amountFilterSchema.setProperties(filterProps);

      // Create parent submodel
      ObjectSchema submodelSchema = new ObjectSchema();
      submodelSchema.setType("object");
      submodelSchema.addProperty("currency", new StringSchema());
      submodelSchema.addProperty("amount", amountFilterSchema);

      Parameter param = new Parameter();
      param.setName("billing");
      param.setIn("query");
      param.setSchema(submodelSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public static final class BillingParams");
      assertThat(writeOp.fileContent).contains("public AmountFilter amount()");
      assertThat(writeOp.fileContent)
          .contains("public static final class AmountFilter extends StringFilter<BillingBuilder>");
    }

    @Test
    @DisplayName("Should handle nested object without filter operations in submodel")
    void shouldHandleNestedObjectWithoutFilterOperationsInSubmodel() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema addressSchema = new ObjectSchema();
      addressSchema.setType("object");
      addressSchema.addProperty("street", new StringSchema());
      addressSchema.addProperty("city", new StringSchema());

      ObjectSchema parentSchema = new ObjectSchema();
      parentSchema.setType("object");
      parentSchema.addProperty("name", new StringSchema());
      parentSchema.addProperty("address", addressSchema);

      Parameter param = new Parameter();
      param.setName("billing");
      param.setIn("query");
      param.setSchema(parentSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public static final class BillingParams");
      assertThat(writeOp.fileContent).contains("public BillingBuilder name(String value)");
      assertThat(writeOp.fileContent).contains("public BillingBuilder address(Address value)");
      assertThat(writeOp.fileContent).doesNotContain("AddressFilter");
    }
  }

  // SORT PARAMETERS

  @Nested
  @DisplayName("Sort Parameters")
  class SortParametersTests {

    @Test
    @DisplayName("Should generate sort_by parameter with multiple sortable fields")
    void shouldGenerateSortByParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addSortByParam(getOperation, List.of("created_at", "updated_at", "id"));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public SortBySortBuilder sortBy()");
      assertThat(writeOp.fileContent).contains("public static final class SortBySortBuilder");
      assertThat(writeOp.fileContent).contains("public SortDirection created_at()");
      assertThat(writeOp.fileContent).contains("public SortDirection updated_at()");
      assertThat(writeOp.fileContent).contains("public SortDirection id()");
    }

    @Test
    @DisplayName("Should generate sort direction methods (asc/desc)")
    void shouldGenerateSortDirectionMethods() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addSortByParam(getOperation, List.of("created_at"));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public static final class SortDirection");
      assertThat(writeOp.fileContent).contains("public CustomerListBuilder asc()");
      assertThat(writeOp.fileContent).contains("public CustomerListBuilder desc()");
    }

    @Test
    @DisplayName("Should handle single sortable field")
    void shouldHandleSingleSortableField() throws IOException {
      Operation getOperation = createGetOperation("invoice", "list");
      addSortByParam(getOperation, List.of("date"));

      addPathWithOperation("/invoices", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "InvoiceListParams.java");
      assertThat(writeOp.fileContent).contains("public SortDirection date()");
    }

    @Test
    @DisplayName("Should handle sort parameter with only asc property")
    void shouldHandleSortParameterWithOnlyAsc() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema sortSchema = new ObjectSchema();
      sortSchema.setType("object");
      StringSchema ascSchema = new StringSchema();
      ascSchema.setEnum(List.of("created_at", "updated_at"));
      sortSchema.addProperty("asc", ascSchema);

      Parameter param = new Parameter();
      param.setName("sort_by");
      param.setIn("query");
      param.setSchema(sortSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }

    @Test
    @DisplayName("Should handle sort parameter with only desc property")
    void shouldHandleSortParameterWithOnlyDesc() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema sortSchema = new ObjectSchema();
      sortSchema.setType("object");
      StringSchema descSchema = new StringSchema();
      descSchema.setEnum(List.of("created_at", "updated_at"));
      sortSchema.addProperty("desc", descSchema);

      Parameter param = new Parameter();
      param.setName("sort_by");
      param.setIn("query");
      param.setSchema(sortSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public SortDirection created_at()");
      assertThat(writeOp.fileContent).contains("public SortDirection updated_at()");
      assertThat(writeOp.fileContent).contains("public enum SortByDesc");
    }

    @Test
    @DisplayName("Should handle sort parameter with both asc and desc properties")
    void shouldHandleSortParameterWithBothAscAndDesc() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema sortSchema = new ObjectSchema();
      sortSchema.setType("object");

      StringSchema ascSchema = new StringSchema();
      ascSchema.setEnum(List.of("name", "id"));
      sortSchema.addProperty("asc", ascSchema);

      StringSchema descSchema = new StringSchema();
      descSchema.setEnum(List.of("created_at", "updated_at"));
      sortSchema.addProperty("desc", descSchema);

      Parameter param = new Parameter();
      param.setName("sort_by");
      param.setIn("query");
      param.setSchema(sortSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public SortDirection name()");
      assertThat(writeOp.fileContent).contains("public SortDirection id()");
      assertThat(writeOp.fileContent).contains("public SortDirection created_at()");
      assertThat(writeOp.fileContent).contains("public SortDirection updated_at()");
    }

    @Test
    @DisplayName("Should ignore non-asc/desc properties in sort parameter")
    void shouldIgnoreNonAscDescPropertiesInSortParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema sortSchema = new ObjectSchema();
      sortSchema.setType("object");

      StringSchema ascSchema = new StringSchema();
      ascSchema.setEnum(List.of("name"));
      sortSchema.addProperty("asc", ascSchema);
      sortSchema.addProperty("other_field", new StringSchema());

      Parameter param = new Parameter();
      param.setName("sort_by");
      param.setIn("query");
      param.setSchema(sortSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public SortDirection name()");
      assertThat(writeOp.fileContent).doesNotContain("other_field");
    }

    @Test
    @DisplayName("Should not treat sort_by without asc/desc as sort parameter")
    void shouldNotTreatSortByWithoutAscDescAsSort() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema sortSchema = new ObjectSchema();
      sortSchema.setType("object");
      sortSchema.addProperty("field1", new StringSchema());
      sortSchema.addProperty("field2", new StringSchema());

      Parameter param = new Parameter();
      param.setName("sort_by");
      param.setIn("query");
      param.setSchema(sortSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).doesNotContain("SortDirection");
      assertThat(writeOp.fileContent).contains("SortByParams");
    }

    @Test
    @DisplayName("Should handle duplicate sortable fields in asc and desc")
    void shouldHandleDuplicateSortableFields() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema sortSchema = new ObjectSchema();
      sortSchema.setType("object");

      StringSchema ascSchema = new StringSchema();
      ascSchema.setEnum(List.of("created_at", "id"));
      sortSchema.addProperty("asc", ascSchema);

      StringSchema descSchema = new StringSchema();
      descSchema.setEnum(List.of("created_at", "id")); // Duplicates
      sortSchema.addProperty("desc", descSchema);

      Parameter param = new Parameter();
      param.setName("sort_by");
      param.setIn("query");
      param.setSchema(sortSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }

    @Test
    @DisplayName("Should handle sort field with null enum list")
    void shouldHandleSortFieldWithNullEnum() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema sortSchema = new ObjectSchema();
      sortSchema.setType("object");
      StringSchema ascSchema = new StringSchema();
      // Enum is null
      sortSchema.addProperty("asc", ascSchema);

      Parameter param = new Parameter();
      param.setName("sort_by");
      param.setIn("query");
      param.setSchema(sortSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }
  }

  // ENUM PARAMETERS

  @Nested
  @DisplayName("Enum Parameters")
  class EnumFieldsTests {

    @Test
    @DisplayName("Should generate enum parameter with values")
    void shouldGenerateDirectEnumParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "hierarchy");
      addEnumQueryParam(
          getOperation, "hierarchy_operation_type", List.of("complete_hierarchy", "subordinates"));

      // Path with {id} and action - module "customer" is prefixed to operation name "hierarchy"
      addPathWithOperation("/customers/{customer-id}/hierarchy", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      // Module "customer" + operation "hierarchy" = CustomerHierarchyParams
      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerHierarchyParams.java");
      assertThat(writeOp.fileContent).contains("public enum HierarchyOperationType");
      assertThat(writeOp.fileContent).contains("COMPLETE_HIERARCHY(\"complete_hierarchy\")");
      assertThat(writeOp.fileContent).contains("SUBORDINATES(\"subordinates\")");
      assertThat(writeOp.fileContent).contains("_UNKNOWN");
    }

    @Test
    @DisplayName("Should generate enum inside filter parameter")
    void shouldGenerateEnumInsideFilterParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addEnumFilterParam(getOperation, "auto_collection", List.of("on", "off"));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public enum AutoCollectionIs");
      assertThat(writeOp.fileContent).contains("ON(\"on\")");
      assertThat(writeOp.fileContent).contains("OFF(\"off\")");
    }

    @Test
    @DisplayName("Should handle multiple enum parameters in operation")
    void shouldHandleMultipleEnumParameters() throws IOException {
      Operation getOperation = createGetOperation("subscription", "list");
      addEnumQueryParam(getOperation, "status", List.of("active", "cancelled", "non_renewing"));
      addEnumQueryParam(getOperation, "billing_period", List.of("monthly", "yearly"));

      addPathWithOperation("/subscriptions", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "SubscriptionListParams.java");
      assertThat(writeOp.fileContent).contains("public enum Status");
      assertThat(writeOp.fileContent).contains("public enum BillingPeriod");
    }

    @Test
    @DisplayName("Should handle empty enum list with _UNKNOWN")
    void shouldHandleEmptyEnumList() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addEnumQueryParam(getOperation, "status", List.of());

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public enum Status");
      assertThat(writeOp.fileContent).contains("_UNKNOWN");
    }
  }

  // SUBMODEL PARAMETERS

  @Nested
  @DisplayName("Submodel Parameters")
  class SubmodelParametersTests {

    @Test
    @DisplayName("Should generate submodel parameter with multiple fields")
    void shouldGenerateSubmodelParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addSubmodelParam(
          getOperation,
          "relationship",
          Map.of(
              "parent_id", new StringSchema(),
              "payment_owner_id", new StringSchema()));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public static final class RelationshipParams");
      assertThat(writeOp.fileContent).contains("public RelationshipBuilder parentId(String value)");
      assertThat(writeOp.fileContent)
          .contains("public RelationshipBuilder paymentOwnerId(String value)");
    }

    @Test
    @DisplayName("Should generate submodel with nested filter parameter")
    void shouldGenerateSubmodelWithFilterParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema parentIdFilter = new ObjectSchema();
      parentIdFilter.setType("object");
      parentIdFilter.addExtension(Extension.IS_FILTER_PARAMETER, true);
      parentIdFilter.addProperty("is", new StringSchema());
      parentIdFilter.addProperty("is_not", new StringSchema());

      addSubmodelParam(getOperation, "relationship", Map.of("parent_id", parentIdFilter));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public static final class RelationshipParams");
      assertThat(writeOp.fileContent).contains("public ParentIdFilter parentId()");
      assertThat(writeOp.fileContent).contains("public static final class ParentIdFilter");
    }

    @Test
    @DisplayName("Should generate enum inside submodel")
    void shouldGenerateEnumInsideSubmodel() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      StringSchema statusEnum = new StringSchema();
      statusEnum.setEnum(List.of("active", "inactive", "pending"));

      addSubmodelParam(
          getOperation,
          "relationship",
          Map.of(
              "parent_id", new StringSchema(),
              "invoice_owner_id", new StringSchema(),
              "payment_owner_id", new StringSchema(),
              "status", statusEnum));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public static final class RelationshipParams");
      assertThat(writeOp.fileContent).contains("public enum Status");
      assertThat(writeOp.fileContent).contains("ACTIVE(\"active\")");
      assertThat(writeOp.fileContent).contains("INACTIVE(\"inactive\")");
      assertThat(writeOp.fileContent).contains("PENDING(\"pending\")");
    }

    @Test
    @DisplayName("Should handle nested submodels")
    void shouldHandleNestedSubmodels() throws IOException {
      Operation getOperation = createGetOperation("subscription", "list");
      addSubmodelParam(
          getOperation,
          "customer",
          Map.of(
              "id", new StringSchema(),
              "first_name", new StringSchema()));

      addPathWithOperation("/subscriptions", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "SubscriptionListParams.java");
    }

    @Test
    @DisplayName("Should skip submodel with null properties")
    void shouldHandleSubmodelWithNullProperties() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      ObjectSchema submodelSchema = new ObjectSchema();
      submodelSchema.setType("object");
      submodelSchema.setProperties(null);

      Parameter param = new Parameter();
      param.setName("relationship");
      param.setIn("query");
      param.setSchema(submodelSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).doesNotContain("RelationshipParams");
    }
  }

  // MODULE AND PACKAGE NAME CONVERSION

  @Nested
  @DisplayName("Module and Package Name Conversion")
  class PackageNameConversionTests {

    @Test
    @DisplayName("Should convert snake_case module to lowerCamel package")
    void shouldConvertSnakeCaseModuleToLowerCamel() throws IOException {
      Operation getOperation = createGetOperation("payment_method", "list");
      addStringQueryParam(getOperation, "name");

      addPathWithOperation("/payment_methods", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertModuleDirectoryExists(fileOps, "paymentMethod");
      FileOp.WriteString writeOp = findWriteOp(fileOps, "PaymentMethodListParams.java");
      assertThat(writeOp.fileContent)
          .contains("package com.chargebee.v4.models.paymentMethod.params");
    }

    @Test
    @DisplayName("Should convert multi-word module to lowerCamel package")
    void shouldConvertUpperCamelModuleToLowerCamel() throws IOException {
      Operation getOperation = createGetOperation("customer_account", "list");
      addStringQueryParam(getOperation, "name");

      addPathWithOperation("/customer_accounts", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertModuleDirectoryExists(fileOps, "customerAccount");
    }

    @Test
    @DisplayName("Should generate correct UpperCamel class name")
    void shouldGenerateCorrectClassName() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addStringQueryParam(getOperation, "name");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public final class CustomerListParams");
      assertThat(writeOp.fileContent).contains("public static final class CustomerListBuilder");
    }

    @Test
    @DisplayName("Should handle complex operation names (camelCase to snake_case to UpperCamel)")
    void shouldHandleComplexOperationNames() throws IOException {
      Operation getOperation = createGetOperation("customer", "changeEstimate");
      addStringQueryParam(getOperation, "subscription_id");

      // Path with {id} and action - module "customer" is prefixed to operation name
      // "changeEstimate"
      addPathWithOperation("/customers/{customer-id}/change_estimate", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      // Module "customer" + operation "changeEstimate" = CustomerChangeEstimateParams
      assertFileExists(fileOps, "CustomerChangeEstimateParams.java");
    }

    @Test
    @DisplayName("Should handle module name with underscore")
    void shouldHandleModuleWithUnderscore() throws IOException {
      Operation getOperation = createGetOperation("customer_account", "list");
      addStringQueryParam(getOperation, "name");

      addPathWithOperation("/customer_accounts", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerAccountListParams.java");
    }

    @Test
    @DisplayName("Should handle module name without underscore")
    void shouldHandleModuleWithoutUnderscore() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      addStringQueryParam(getOperation, "name");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }
  }

  // NULL AND EMPTY INPUT HANDLING

  @Nested
  @DisplayName("Null and Empty Input Handling")
  class NullAndEmptyInputTests {

    @Test
    @DisplayName("Should handle null OpenAPI spec")
    void shouldHandleNullOpenAPI() throws IOException {
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(null);

      assertThat(fileOps).hasSize(1); // Only base directory
    }

    @Test
    @DisplayName("Should handle empty OpenAPI spec")
    void shouldHandleEmptyOpenAPISpec() throws IOException {
      OpenAPI emptySpec = new OpenAPI();
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(emptySpec);

      assertThat(fileOps).hasSize(1); // Only directory creation
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }

    @Test
    @DisplayName("Should handle null paths in OpenAPI")
    void shouldHandleNullPaths() throws IOException {
      OpenAPI apiWithNullPaths = new OpenAPI();
      apiWithNullPaths.setPaths(null);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(apiWithNullPaths);

      assertThat(fileOps).hasSize(1);
    }

    @Test
    @DisplayName("Should handle empty paths map")
    void shouldHandleEmptyPathsMap() throws IOException {
      openAPI.setPaths(new Paths());

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1); // Only base directory
    }

    @Test
    @DisplayName("Should handle null PathItem")
    void shouldHandleNullPathItem() throws IOException {
      if (openAPI.getPaths() == null) {
        openAPI.setPaths(new Paths());
      }
      openAPI.getPaths().addPathItem("/test", null);

      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1); // Only base directory
    }

    @Test
    @DisplayName("Should skip operation with null parameters list")
    void shouldHandleOperationWithNullParameters() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      getOperation.setParameters(null);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1);
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }

    @Test
    @DisplayName("Should skip operation with empty parameters list")
    void shouldHandleEmptyParametersList() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      getOperation.setParameters(new ArrayList<>());

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1); // Only base directory
    }

    @Test
    @DisplayName("Should skip null parameter in parameters list")
    void shouldHandleNullParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      getOperation.addParametersItem(null);
      addStringQueryParam(getOperation, "name");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }

    @Test
    @DisplayName("Should skip operation without required extensions")
    void shouldSkipOperationWithoutExtensions() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      getOperation.setExtensions(null);
      addStringQueryParam(getOperation, "test_param");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1);
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }

    @Test
    @DisplayName("Should skip operation when SDK_METHOD_NAME extension is missing")
    void shouldSkipOperationWhenMethodNameExtensionMissing() throws IOException {
      Operation getOperation = new Operation();
      Map<String, Object> extensions = new java.util.HashMap<>();
      extensions.put(Extension.RESOURCE_ID, "customer");
      extensions.put(Extension.IS_OPERATION_LIST, true);
      // SDK_METHOD_NAME is intentionally not set
      getOperation.setExtensions(extensions);
      addStringQueryParam(getOperation, "test_param");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      // Without SDK_METHOD_NAME, the operation cannot generate a valid params class
      // Only directory creation should occur
      assertThat(fileOps).hasSize(1);
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }

    @Test
    @DisplayName("Should skip operation missing RESOURCE_ID extension")
    void shouldSkipOperationWithMissingModule() throws IOException {
      Operation getOperation = new Operation();
      // No extensions at all - should be skipped
      addStringQueryParam(getOperation, "test_param");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertThat(fileOps).hasSize(1);
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }
  }

  // SCHEMA AND PARAMETER VALIDATION

  @Nested
  @DisplayName("Schema and Parameter Validation")
  class SchemaValidationTests {

    @Test
    @DisplayName("Should skip parameter with null schema")
    void shouldHandleParameterWithNullSchema() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      Parameter param = new Parameter();
      param.setName("test_param");
      param.setIn("query");
      param.setSchema(null);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).doesNotContain("testParam");
    }

    @Test
    @DisplayName("Should skip parameter without schema property")
    void shouldHandleParameterWithoutSchema() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      Parameter param = new Parameter();
      param.setName("test_param");
      param.setIn("query");
      getOperation.addParametersItem(param);
      addStringQueryParam(getOperation, "name");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).doesNotContain("testParam");
    }

    @Test
    @DisplayName("Should skip non-query parameters")
    void shouldHandleNonQueryParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      Parameter param = new Parameter();
      param.setName("path_param");
      param.setIn("path"); // Not "query"
      param.setSchema(new StringSchema());
      getOperation.addParametersItem(param);

      addStringQueryParam(getOperation, "name");
      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).doesNotContain("pathParam");
    }

    @Test
    @DisplayName("Should handle object schema with null properties")
    void shouldHandleObjectSchemaWithoutProperties() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      ObjectSchema objectSchema = new ObjectSchema();
      objectSchema.setType("object");

      Parameter param = new Parameter();
      param.setName("metadata");
      param.setIn("query");
      param.setSchema(objectSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }

    @Test
    @DisplayName("Should handle filter parameter with null properties")
    void shouldHandleFilterParameterWithNullProperties() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      ObjectSchema filterSchema = new ObjectSchema();
      filterSchema.setType("object");
      filterSchema.setProperties(null);

      Parameter param = new Parameter();
      param.setName("name");
      param.setIn("query");
      param.setSchema(filterSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).doesNotContain("NameFilter");
      assertThat(writeOp.fileContent).doesNotContain("NameParams");
    }

    @Test
    @DisplayName("Should handle filter parameter with empty properties")
    void shouldHandleFilterParameterWithEmptyProperties() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      ObjectSchema filterSchema = new ObjectSchema();
      filterSchema.setType("object");
      filterSchema.setProperties(Map.of());

      Parameter param = new Parameter();
      param.setName("name");
      param.setIn("query");
      param.setSchema(filterSchema);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).doesNotContain("NameFilter");
    }

    @Test
    @DisplayName("Should handle non-object schema types")
    void shouldHandleNonObjectSchemaType() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      Parameter param = new Parameter();
      param.setName("count");
      param.setIn("query");
      param.setSchema(new IntegerSchema());
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }
  }

  // DEPRECATION HANDLING

  @Nested
  @DisplayName("Deprecation Handling")
  class DeprecationTests {

    @Test
    @DisplayName("Should handle parameter with deprecated=true")
    void shouldHandleDeprecatedParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      Parameter param = new Parameter();
      param.setName("old_param");
      param.setIn("query");
      param.setSchema(new StringSchema());
      param.setDeprecated(true);
      getOperation.addParametersItem(param);

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }

    @Test
    @DisplayName("Should handle parameter with deprecated=false")
    void shouldHandleNonDeprecatedParameter() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      Parameter param = new Parameter();
      param.setName("current_param");
      param.setIn("query");
      param.setSchema(new StringSchema());
      param.setDeprecated(false);

      addStringQueryParam(getOperation, "name");
      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }

    @Test
    @DisplayName("Should handle parameter with deprecated=null")
    void shouldHandleParameterWithNullDeprecated() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      Parameter param = new Parameter();
      param.setName("normal_param");
      param.setIn("query");
      param.setSchema(new StringSchema());

      addStringQueryParam(getOperation, "name");
      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }

    @Test
    @DisplayName("Should handle submodel field with deprecated=false")
    void shouldHandleFieldWithDeprecatedFalseInSubmodel() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      StringSchema fieldSchema = new StringSchema();
      fieldSchema.setDeprecated(false);

      addSubmodelParam(getOperation, "relationship", Map.of("parent_id", fieldSchema));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }

    @Test
    @DisplayName("Should handle submodel field with deprecated=true")
    void shouldHandleFieldWithDeprecatedTrueInSubmodel() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      StringSchema fieldSchema = new StringSchema();
      fieldSchema.setDeprecated(true);

      addSubmodelParam(getOperation, "relationship", Map.of("old_field", fieldSchema));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }

    @Test
    @DisplayName("Should handle submodel field with deprecated=null")
    void shouldHandleFieldWithNullDeprecatedInSubmodel() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      StringSchema fieldSchema = new StringSchema();

      addSubmodelParam(getOperation, "relationship", Map.of("normal_field", fieldSchema));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      assertFileExists(fileOps, "CustomerListParams.java");
    }
  }

  // CUSTOM FIELD FILTER SUPPORT

  @Nested
  @DisplayName("Custom Field Filter Support")
  class CustomFieldFilterSupportTests {

    @Test
    @DisplayName("Should generate customField method when x-cb-is-custom-fields-supported is true")
    void shouldGenerateCustomFieldMethodWhenSupported() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      getOperation.getExtensions().put(Extension.IS_CUSTOM_FIELDS_SUPPORTED, true);
      addStringQueryParam(getOperation, "first_name");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent)
          .contains(
              "public CustomFieldSelector<CustomerListBuilder> customField(String fieldName)");
      assertThat(writeOp.fileContent)
          .contains("return new CustomFieldSelector<>(fieldName, this, queryParams)");
    }

    @Test
    @DisplayName(
        "Should not generate customField method when x-cb-is-custom-fields-supported is false")
    void shouldNotGenerateCustomFieldMethodWhenNotSupported() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      getOperation.getExtensions().put(Extension.IS_CUSTOM_FIELDS_SUPPORTED, false);
      addStringQueryParam(getOperation, "first_name");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).doesNotContain("customField");
      assertThat(writeOp.fileContent).doesNotContain("CustomFieldSelector");
    }

    @Test
    @DisplayName("Should not generate customField method when extension is missing")
    void shouldNotGenerateCustomFieldMethodWhenExtensionMissing() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      // Not adding IS_CUSTOM_FIELDS_SUPPORTED extension
      addStringQueryParam(getOperation, "first_name");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).doesNotContain("customField");
      assertThat(writeOp.fileContent).doesNotContain("CustomFieldSelector");
    }

    @Test
    @DisplayName("Should import CustomFieldSelector when custom fields are supported")
    void shouldImportCustomFieldSelectorWhenSupported() throws IOException {
      Operation getOperation = createGetOperation("subscription", "list");
      getOperation.getExtensions().put(Extension.IS_CUSTOM_FIELDS_SUPPORTED, true);
      addStringQueryParam(getOperation, "plan_id");

      addPathWithOperation("/subscriptions", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "SubscriptionListParams.java");
      assertThat(writeOp.fileContent)
          .contains("import com.chargebee.v4.filters.CustomFieldSelector;");
      assertThat(writeOp.fileContent).contains("import com.chargebee.v4.filters.BooleanFilter;");
    }

    @Test
    @DisplayName("Should generate customField with correct builder type for different operations")
    void shouldGenerateCorrectBuilderTypeForDifferentOperations() throws IOException {
      Operation customerListOp = createGetOperation("customer", "list");
      customerListOp.getExtensions().put(Extension.IS_CUSTOM_FIELDS_SUPPORTED, true);
      addStringQueryParam(customerListOp, "name");

      Operation subscriptionListOp = createGetOperation("subscription", "list");
      subscriptionListOp.getExtensions().put(Extension.IS_CUSTOM_FIELDS_SUPPORTED, true);
      addStringQueryParam(subscriptionListOp, "status");

      addPathWithOperation("/customers", customerListOp);
      addPathWithOperation("/subscriptions", subscriptionListOp);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString customerWriteOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(customerWriteOp.fileContent)
          .contains(
              "public CustomFieldSelector<CustomerListBuilder> customField(String fieldName)");

      FileOp.WriteString subscriptionWriteOp = findWriteOp(fileOps, "SubscriptionListParams.java");
      assertThat(subscriptionWriteOp.fileContent)
          .contains(
              "public CustomFieldSelector<SubscriptionListBuilder> customField(String fieldName)");
    }

    @Test
    @DisplayName("Should generate customField method with Javadoc")
    void shouldGenerateCustomFieldMethodWithJavadoc() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      getOperation.getExtensions().put(Extension.IS_CUSTOM_FIELDS_SUPPORTED, true);
      addStringQueryParam(getOperation, "first_name");

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("Create a filter for a custom field");
      assertThat(writeOp.fileContent).contains("@param fieldName the custom field name");
      assertThat(writeOp.fileContent)
          .contains("@return CustomFieldSelector for choosing filter type");
    }

    @Test
    @DisplayName("Should handle custom field support alongside other features")
    void shouldHandleCustomFieldSupportWithOtherFeatures() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");
      getOperation.getExtensions().put(Extension.IS_CUSTOM_FIELDS_SUPPORTED, true);

      // Add various other features
      addStringFilterParam(getOperation, "first_name", List.of("is", "starts_with"));
      addTimestampFilterParam(getOperation, "created_at");
      addSortByParam(getOperation, List.of("created_at", "updated_at"));
      addEnumQueryParam(getOperation, "status", List.of("active", "inactive"));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      // Verify customField method is generated
      assertThat(writeOp.fileContent)
          .contains(
              "public CustomFieldSelector<CustomerListBuilder> customField(String fieldName)");
      // Verify other features are still generated
      assertThat(writeOp.fileContent).contains("public FirstNameFilter firstName()");
      assertThat(writeOp.fileContent).contains("public CreatedAtFilter createdAt()");
      assertThat(writeOp.fileContent).contains("public SortBySortBuilder sortBy()");
      assertThat(writeOp.fileContent).contains("public enum Status");
    }
  }

  // COMPLEX INTEGRATION TESTS

  @Nested
  @DisplayName("Complex Integration Tests")
  class ComplexIntegrationTests {

    @Test
    @DisplayName("Should handle operation with all feature combinations")
    void shouldHandleOperationWithAllFeatureCombinations() throws IOException {
      Operation getOperation = createGetOperation("customer", "list");

      // String filter
      addStringFilterParam(getOperation, "first_name", List.of("is", "starts_with", "is_present"));

      // Timestamp filter
      addTimestampFilterParam(getOperation, "created_at");

      // Boolean filter
      addBooleanFilterParam(getOperation, "auto_collection");

      // Sort
      addSortByParam(getOperation, List.of("created_at", "updated_at"));

      // Enum
      addEnumQueryParam(getOperation, "status", List.of("active", "inactive"));

      // Submodel
      addSubmodelParam(getOperation, "relationship", Map.of("parent_id", new StringSchema()));

      addPathWithOperation("/customers", getOperation);
      paramsBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = paramsBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerListParams.java");
      assertThat(writeOp.fileContent).contains("public FirstNameFilter firstName()");
      assertThat(writeOp.fileContent).contains("public CreatedAtFilter createdAt()");
      assertThat(writeOp.fileContent).contains("public AutoCollectionFilter autoCollection()");
      assertThat(writeOp.fileContent).contains("public SortBySortBuilder sortBy()");
      assertThat(writeOp.fileContent).contains("public enum Status");
      assertThat(writeOp.fileContent).contains("public static final class RelationshipParams");
    }
  }

  // HELPER METHODS

  /**
   * Creates a GET operation with required extensions for testing.
   *
   * @param resourceId The resource identifier (e.g., "customer")
   * @param methodName The operation method name (e.g., "list")
   * @return Configured Operation instance
   */
  private Operation createGetOperation(String resourceId, String methodName) {
    Operation operation = new Operation();
    Map<String, Object> extensions = new HashMap<>();
    extensions.put(Extension.RESOURCE_ID, resourceId);
    extensions.put(Extension.SDK_METHOD_NAME, methodName);
    if ("list".equals(methodName)) {
      extensions.put(Extension.IS_OPERATION_LIST, true);
    }
    operation.setExtensions(extensions);
    return operation;
  }

  /**
   * Creates a POST operation with required extensions for testing.
   */
  private Operation createPostOperation(String resourceId, String methodName) {
    Operation operation = new Operation();
    Map<String, Object> extensions = new HashMap<>();
    extensions.put(Extension.RESOURCE_ID, resourceId);
    extensions.put(Extension.SDK_METHOD_NAME, methodName);
    operation.setExtensions(extensions);
    return operation;
  }

  /**
   * Adds a simple string query parameter to an operation.
   */
  private void addStringQueryParam(Operation operation, String name) {
    Parameter param = new Parameter();
    param.setName(name);
    param.setIn("query");
    param.setSchema(new StringSchema());
    operation.addParametersItem(param);
  }

  /**
   * Adds a string filter parameter with specified operations (is, is_not, etc.).
   */
  private void addStringFilterParam(Operation operation, String name, List<String> operations) {
    ObjectSchema filterSchema = new ObjectSchema();
    filterSchema.setType("object");
    filterSchema.addExtension(Extension.IS_FILTER_PARAMETER, true);
    for (String op : operations) {
      filterSchema.addProperty(op, new StringSchema());
    }

    Parameter param = new Parameter();
    param.setName(name);
    param.setIn("query");
    param.setSchema(filterSchema);
    operation.addParametersItem(param);
  }

  /**
   * Adds a timestamp filter parameter with date operations.
   */
  private void addTimestampFilterParam(Operation operation, String name) {
    ObjectSchema filterSchema = new ObjectSchema();
    filterSchema.setType("object");
    filterSchema.addExtension(Extension.IS_FILTER_PARAMETER, true);
    filterSchema.addProperty("after", new StringSchema());
    filterSchema.addProperty("before", new StringSchema());
    filterSchema.addProperty("on", new StringSchema());
    filterSchema.addProperty("between", new StringSchema());

    Parameter param = new Parameter();
    param.setName(name);
    param.setIn("query");
    param.setSchema(filterSchema);
    operation.addParametersItem(param);
  }

  /**
   * Adds a boolean filter parameter.
   */
  private void addBooleanFilterParam(Operation operation, String name) {
    ObjectSchema filterSchema = new ObjectSchema();
    filterSchema.setType("object");
    filterSchema.addExtension(Extension.IS_FILTER_PARAMETER, true);
    filterSchema.addProperty("is", new StringSchema());

    Parameter param = new Parameter();
    param.setName(name);
    param.setIn("query");
    param.setSchema(filterSchema);
    operation.addParametersItem(param);
  }

  /**
   * Adds a sort_by parameter with specified sortable fields.
   */
  private void addSortByParam(Operation operation, List<String> sortableFields) {
    ObjectSchema sortSchema = new ObjectSchema();
    sortSchema.setType("object");

    StringSchema ascSchema = new StringSchema();
    ascSchema.setEnum(new ArrayList<>(sortableFields));
    sortSchema.addProperty("asc", ascSchema);

    StringSchema descSchema = new StringSchema();
    descSchema.setEnum(new ArrayList<>(sortableFields));
    sortSchema.addProperty("desc", descSchema);

    Parameter param = new Parameter();
    param.setName("sort_by");
    param.setIn("query");
    param.setSchema(sortSchema);
    operation.addParametersItem(param);
  }

  /**
   * Adds an enum query parameter with specified values.
   */
  private void addEnumQueryParam(Operation operation, String name, List<String> enumValues) {
    StringSchema enumSchema = new StringSchema();
    enumSchema.setEnum(new ArrayList<>(enumValues));

    Parameter param = new Parameter();
    param.setName(name);
    param.setIn("query");
    param.setSchema(enumSchema);
    operation.addParametersItem(param);
  }

  /**
   * Adds an enum filter parameter (enum inside a filter object).
   */
  private void addEnumFilterParam(Operation operation, String name, List<String> enumValues) {
    StringSchema enumSchema = new StringSchema();
    enumSchema.setEnum(new ArrayList<>(enumValues));

    ObjectSchema filterSchema = new ObjectSchema();
    filterSchema.setType("object");
    filterSchema.addExtension(Extension.IS_FILTER_PARAMETER, true);
    filterSchema.addProperty("is", enumSchema);

    Parameter param = new Parameter();
    param.setName(name);
    param.setIn("query");
    param.setSchema(filterSchema);
    operation.addParametersItem(param);
  }

  /**
   * Adds a submodel parameter with specified properties.
   */
  private void addSubmodelParam(
      Operation operation, String name, Map<String, Schema<?>> properties) {
    ObjectSchema submodelSchema = new ObjectSchema();
    submodelSchema.setType("object");
    for (Map.Entry<String, Schema<?>> entry : properties.entrySet()) {
      submodelSchema.addProperty(entry.getKey(), entry.getValue());
    }

    Parameter param = new Parameter();
    param.setName(name);
    param.setIn("query");
    param.setSchema(submodelSchema);
    operation.addParametersItem(param);
  }

  /**
   * Adds a path with a GET operation to the OpenAPI spec.
   */
  private void addPathWithOperation(String path, Operation getOperation) {
    PathItem pathItem = new PathItem();
    pathItem.setGet(getOperation);
    addPath(path, pathItem);
  }

  /**
   * Adds a path item to the OpenAPI spec.
   */
  private void addPath(String path, PathItem pathItem) {
    if (openAPI.getPaths() == null) {
      openAPI.setPaths(new Paths());
    }
    openAPI.getPaths().addPathItem(path, pathItem);
  }

  /**
   * Finds a WriteString operation for a specific file name in the file operations list.
   */
  private FileOp.WriteString findWriteOp(List<FileOp> fileOps, String fileName) {
    return fileOps.stream()
        .filter(op -> op instanceof FileOp.WriteString)
        .map(op -> (FileOp.WriteString) op)
        .filter(op -> op.fileName.equals(fileName))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("WriteString operation not found for file: " + fileName));
  }

  /**
   * Asserts that a file with the given name exists in the file operations list.
   */
  private void assertFileExists(List<FileOp> fileOps, String fileName) {
    assertThat(fileOps)
        .anyMatch(
            op ->
                op instanceof FileOp.WriteString
                    && ((FileOp.WriteString) op).fileName.equals(fileName));
  }

  /**
   * Asserts that a module directory exists in the file operations list.
   */
  private void assertModuleDirectoryExists(List<FileOp> fileOps, String moduleName) {
    assertThat(fileOps)
        .anyMatch(
            op ->
                op instanceof FileOp.CreateDirectory
                    && ((FileOp.CreateDirectory) op).basePath.contains("/" + moduleName)
                    && ((FileOp.CreateDirectory) op).directoryName.equals("params"));
  }
}
