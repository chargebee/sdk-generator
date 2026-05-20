package com.chargebee.sdk.validator;

import static com.chargebee.sdk.test_data.OperationBuilder.*;
import static com.chargebee.sdk.test_data.ResourceBuilder.buildResource;
import static com.chargebee.sdk.test_data.ResourceResponseParam.resourceResponseParam;
import static com.chargebee.sdk.test_data.SpecBuilder.buildSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.LanguageTests;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Tests for {@link ValidatorZod} (Zod validation emitters for chargebee-node). */
public class ValidatorZodTests extends LanguageTests {

  // ---- Shared test fixtures ------------------------------------------------
  // Centralised here so schema changes only need updating in one place.
  // Each test creates its own Spec/Operation, but all re-use these resource
  // definitions rather than rebuilding them inline.

  @SuppressWarnings("unchecked")
  private static final MapEntry<String, Schema<?>> CUSTOMER =
      (MapEntry<String, Schema<?>>)
          (MapEntry<?, ?>) buildResource("customer").withAttribute("id", true).done();

  @SuppressWarnings("unchecked")
  private static final MapEntry<String, Schema<?>> SUBSCRIPTION =
      (MapEntry<String, Schema<?>>)
          (MapEntry<?, ?>) buildResource("subscription").withAttribute("id", true).done();

  @SuppressWarnings("unchecked")
  private static final MapEntry<String, Schema<?>> TOKEN =
      (MapEntry<String, Schema<?>>)
          (MapEntry<?, ?>) buildResource("token").withAttribute("id", true).done();

  // ---- subject under test --------------------------------------------------

  private static ValidatorZod validatorZod;

  @BeforeAll
  static void beforeAll() {
    validatorZod = new ValidatorZod();
  }

  // =========================================================================
  // Layout / structure tests
  // =========================================================================

  @Test
  void shouldEmitOnlyRootLayoutWhenThereAreNoResources() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = validatorZod.generate("/validators", spec);

    assertThat(fileOps).hasSize(2);
    assertCreateDirectoryFileOp(fileOps.get(0), "/validators", "");
    assertWriteStringFileOp(
        fileOps.get(1),
        "/validators",
        "index.ts",
        """
        // Auto-generated barrel export for Zod validators
        """);
  }

  @Test
  void shouldEmitPostBodyValidatorWithRequiredAndOptionalFields() throws IOException {
    var createOp =
        buildPostOperation("create")
            .forResource("customer")
            .withResponse(resourceResponseParam("customer", CUSTOMER))
            // Required fields must be added before optional ones: Schema#setRequired only keeps
            // names already present in properties when using OperationBuilder's call order.
            .withRequestBody("email", new StringSchema(), true)
            .withRequestBody("first_name", new StringSchema())
            .withSortOrder(0)
            .done();

    var spec = buildSpec().withResource(CUSTOMER).withPostOperation("/customers", createOp).done();

    List<FileOp> fileOps = validatorZod.generate("/validators", spec);

    assertThat(fileOps).hasSize(3);
    assertCreateDirectoryFileOp(fileOps.get(0), "/validators", "");
    assertWriteStringFileOp(fileOps.get(1), "/validators", "customer.schema.ts");
    String src = ((FileOp.WriteString) fileOps.get(1)).fileContent;
    assertThat(src)
        .contains("// Generated Zod schemas: Customer")
        .contains("// Actions: create")
        .contains("import { z } from 'zod';")
        .contains("const CreateCustomerBodySchema = ")
        .contains("export { CreateCustomerBodySchema };")
        .contains("export type CreateCustomerBody = z.infer<typeof CreateCustomerBodySchema>;")
        .contains("z.looseObject({")
        .contains("first_name: z.string().optional()")
        .contains("email: z.string()")
        .doesNotContain("email: z.string().optional()");

    assertWriteStringFileOp(
        fileOps.get(2),
        "/validators",
        "index.ts",
        """
        // Auto-generated barrel export for Zod validators
        export * from './customer.schema.js';
        """);
  }

  @Test
  void shouldEmitGetQueryParamValidatorWhenOperationHasQueryParameters() throws IOException {
    var listOp =
        buildListOperation("list")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", SUBSCRIPTION))
            .withQueryParam("limit", new IntegerSchema(), false)
            .withQueryParam("status", new StringSchema(), true)
            .withSortOrder(1)
            .done();

    var spec =
        buildSpec().withResource(SUBSCRIPTION).withOperation("/subscriptions", listOp).done();

    List<FileOp> fileOps = validatorZod.generate("/validators", spec);

    assertThat(fileOps).hasSize(3);
    assertWriteStringFileOp(fileOps.get(1), "/validators", "subscription.schema.ts");
    String src = ((FileOp.WriteString) fileOps.get(1)).fileContent;
    assertThat(src)
        .contains("// Generated Zod schemas: Subscription")
        .contains("// Actions: list")
        .contains("limit: z.number().int().optional()")
        .contains("status: z.string()")
        .doesNotContain("status: z.string().optional()");
  }

  // =========================================================================
  // Filtering tests (hidden / bulk / internal actions)
  // =========================================================================

  @Test
  void shouldSkipHiddenBulkAndInternalActions() throws IOException {
    var visible =
        buildPostOperation("create")
            .forResource("token")
            .withResponse(resourceResponseParam("token", TOKEN))
            .withRequestBody("value", new StringSchema())
            .withSortOrder(0)
            .done();
    var hidden =
        buildPostOperation("create_for_card")
            .forResource("token")
            .withResponse(resourceResponseParam("token", TOKEN))
            .withRequestBody("number", new StringSchema())
            .asHiddenFromSDKGeneration()
            .withSortOrder(1)
            .done();
    var bulk =
        buildPostOperation("bulk_create")
            .forResource("token")
            .withResponse(resourceResponseParam("token", TOKEN))
            .withRequestBody("ids", new StringSchema())
            .asBulkOperationFromSDKGeneration()
            .withSortOrder(2)
            .done();
    var internal =
        buildPostOperation("internal_ping")
            .forResource("token")
            .withResponse(resourceResponseParam("token", TOKEN))
            .withRequestBody("ping", new StringSchema())
            .withSortOrder(3)
            .done();
    internal.getExtensions().put(Extension.IS_INTERNAL, true);

    var spec =
        buildSpec()
            .withResource(TOKEN)
            .withPostOperation("/tokens", visible)
            .withPostOperation("/tokens/create_for_card", hidden)
            .withPostOperation("/tokens/bulk", bulk)
            .withPostOperation("/tokens/internal", internal)
            .done();

    List<FileOp> fileOps = validatorZod.generate("/validators", spec);

    assertThat(fileOps).hasSize(3);
    assertWriteStringFileOp(fileOps.get(1), "/validators", "token.schema.ts");
    assertWriteStringFileOp(
        fileOps.get(2),
        "/validators",
        "index.ts",
        """
        // Auto-generated barrel export for Zod validators
        export * from './token.schema.js';
        """);
  }

  @Test
  void shouldSkipHiddenFieldsInRequestBody() throws IOException {
    var createOp =
        buildPostOperation("create")
            .forResource("customer")
            .withResponse(resourceResponseParam("customer", CUSTOMER))
            .withRequestBody("name", new StringSchema())
            .withRequestBody("internal_ref", hiddenFromSDKPropSchema())
            .withSortOrder(0)
            .done();

    var spec = buildSpec().withResource(CUSTOMER).withPostOperation("/customers", createOp).done();

    String src = findFileContent(validatorZod.generate("/validators", spec), "customer.schema.ts");

    assertThat(src).contains("name: z.string().optional()").doesNotContain("internal_ref");
  }

  // =========================================================================
  // Shared $ref / component schemas
  // =========================================================================

  @Test
  void shouldEmitSharedValidationFileForComponentRefsInRequestBody() throws IOException {
    var addressSchema =
        new ObjectSchema()
            .addProperty("line1", new StringSchema())
            .addProperty("zip", new StringSchema());
    var createOp =
        buildPostOperation("create")
            .forResource("customer")
            .withResponse(resourceResponseParam("customer", CUSTOMER))
            .withRequestBody(
                "shipping_address", new Schema<>().$ref("#/components/schemas/Address"))
            .withSortOrder(0)
            .done();

    var spec =
        buildSpec()
            .withResource(CUSTOMER)
            .withResource(entry("Address", addressSchema))
            .withPostOperation("/customers", createOp)
            .done();

    List<FileOp> fileOps = validatorZod.generate("/validators", spec);

    assertThat(fileOps).hasSize(4);
    String shared = findFileContent(fileOps, "shared.schema.ts");
    String action = findFileContent(fileOps, "customer.schema.ts");

    assertThat(shared)
        .contains("// Shared Zod schemas")
        .contains("const addressBlockSchema = ")
        .contains("export { addressBlockSchema };")
        .contains("z.object({");

    assertThat(action)
        .contains("import { addressBlockSchema } from './shared.schema.js';")
        .contains("shipping_address: addressBlockSchema.optional()");

    String index = ((FileOp.WriteString) fileOps.get(fileOps.size() - 1)).fileContent;
    assertThat(index)
        .contains("export * from './shared.schema.js';")
        .contains("export * from './customer.schema.js';");
  }

  // =========================================================================
  // String type constraints
  // =========================================================================

  @Test
  void shouldEmitStringValidatorsWithFormatConstraints() throws IOException {
    var createOp =
        buildPostOperation("create")
            .forResource("customer")
            .withResponse(resourceResponseParam("customer", CUSTOMER))
            .withRequestBody("email", new StringSchema().format("email"))
            .withRequestBody("website", new StringSchema().format("uri"))
            .withRequestBody("created_at", new StringSchema().format("date-time"))
            .withSortOrder(0)
            .done();

    var spec = buildSpec().withResource(CUSTOMER).withPostOperation("/customers", createOp).done();

    String src = findFileContent(validatorZod.generate("/validators", spec), "customer.schema.ts");

    assertThat(src)
        .contains("email: z.string().email().optional()")
        .contains("website: z.string().url().optional()")
        .contains("created_at: z.string().datetime().optional()");
  }

  @Test
  void shouldEmitStringValidatorsWithLengthAndPatternConstraints() throws IOException {
    var createOp =
        buildPostOperation("create")
            .forResource("customer")
            .withResponse(resourceResponseParam("customer", CUSTOMER))
            .withRequestBody("name", new StringSchema().maxLength(100).minLength(1))
            .withRequestBody("code", new StringSchema().pattern("^[A-Z]+$"))
            .withSortOrder(0)
            .done();

    var spec = buildSpec().withResource(CUSTOMER).withPostOperation("/customers", createOp).done();

    String src = findFileContent(validatorZod.generate("/validators", spec), "customer.schema.ts");

    assertThat(src)
        .contains("name: z.string().max(100).min(1).optional()")
        .contains("code: z.string().regex(RegExp('^[A-Z]+$')).optional()");
  }

  @Test
  void shouldEmitEnumStringValidator() throws IOException {
    var createOp =
        buildPostOperation("create")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", SUBSCRIPTION))
            .withRequestBody(
                "status", new StringSchema()._enum(List.of("active", "cancelled", "paused")), true)
            .withSortOrder(0)
            .done();

    var spec =
        buildSpec().withResource(SUBSCRIPTION).withPostOperation("/subscriptions", createOp).done();

    String src =
        findFileContent(validatorZod.generate("/validators", spec), "subscription.schema.ts");

    assertThat(src)
        .contains("z.enum([")
        .contains("'active'")
        .contains("'cancelled'")
        .contains("'paused'")
        // required enum field → no .optional()
        .doesNotContain(".optional()");
  }

  // =========================================================================
  // Other scalar types (boolean, number, integer)
  // =========================================================================

  @Test
  void shouldEmitBooleanAndNumberValidators() throws IOException {
    var createOp =
        buildPostOperation("create")
            .forResource("customer")
            .withResponse(resourceResponseParam("customer", CUSTOMER))
            .withRequestBody("auto_collection", new BooleanSchema())
            .withRequestBody(
                "net_term_days",
                new IntegerSchema().minimum(BigDecimal.ZERO).maximum(new BigDecimal(180)))
            .withRequestBody("amount", new NumberSchema())
            .withSortOrder(0)
            .done();

    var spec = buildSpec().withResource(CUSTOMER).withPostOperation("/customers", createOp).done();

    String src = findFileContent(validatorZod.generate("/validators", spec), "customer.schema.ts");

    assertThat(src)
        .contains("auto_collection: z.boolean().optional()")
        .contains("net_term_days: z.number().int().min(0).max(180).optional()")
        .contains("amount: z.number().optional()");
  }

  // =========================================================================
  // Array and nested-object types
  // =========================================================================

  @Test
  void shouldEmitArrayValidator() throws IOException {
    var createOp =
        buildPostOperation("create")
            .forResource("customer")
            .withResponse(resourceResponseParam("customer", CUSTOMER))
            .withRequestBody("tags", new ArraySchema().items(new StringSchema()))
            .withSortOrder(0)
            .done();

    var spec = buildSpec().withResource(CUSTOMER).withPostOperation("/customers", createOp).done();

    String src = findFileContent(validatorZod.generate("/validators", spec), "customer.schema.ts");

    assertThat(src).contains("tags: z.array(z.string().optional()).optional()");
  }

  @Test
  void shouldEmitNestedObjectAsInlineConst() throws IOException {
    var cardSchema =
        new ObjectSchema()
            .addProperty("number", new StringSchema())
            .addProperty("cvv", new StringSchema());
    var createOp =
        buildPostOperation("create")
            .forResource("customer")
            .withResponse(resourceResponseParam("customer", CUSTOMER))
            .withRequestBody("card", cardSchema)
            .withSortOrder(0)
            .done();

    var spec = buildSpec().withResource(CUSTOMER).withPostOperation("/customers", createOp).done();

    String src = findFileContent(validatorZod.generate("/validators", spec), "customer.schema.ts");

    // Nested objects are lifted into a named const that precedes the root schema
    assertThat(src)
        .contains("const CreateCustomerCardSchema = z.object({")
        .contains("card: CreateCustomerCardSchema.optional()")
        // The const must appear before it is referenced
        .satisfies(
            s ->
                assertThat(s.indexOf("CreateCustomerCardSchema ="))
                    .isLessThan(s.indexOf("card: CreateCustomerCardSchema")));
  }

  // =========================================================================
  // Index / sort-order
  // =========================================================================

  @Test
  void shouldSortActionsAndProduceCorrectIndexExports() throws IOException {
    var updateOp =
        buildPostOperation("update")
            .forResource("customer")
            .withResponse(resourceResponseParam("customer", CUSTOMER))
            .withRequestBody("name", new StringSchema())
            .withSortOrder(1)
            .done();
    var createOp =
        buildPostOperation("create")
            .forResource("customer")
            .withResponse(resourceResponseParam("customer", CUSTOMER))
            .withRequestBody("email", new StringSchema())
            .withSortOrder(0)
            .done();

    var spec =
        buildSpec()
            .withResource(CUSTOMER)
            .withPostOperation("/customers", createOp)
            .withPostOperation("/customers/update", updateOp)
            .done();

    String customerSchema =
        findFileContent(validatorZod.generate("/validators", spec), "customer.schema.ts");

    // create (sortOrder=0) must appear before update (sortOrder=1) in the resource module
    assertThat(customerSchema.indexOf("CreateCustomerBodySchema"))
        .isLessThan(customerSchema.indexOf("UpdateCustomerBodySchema"));
    assertThat(customerSchema.indexOf("export type CreateCustomerBody"))
        .isLessThan(customerSchema.indexOf("export type UpdateCustomerBody"));
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /**
   * Returns the content of the first {@link FileOp.WriteString} whose {@code fileName} matches.
   * Throws an {@link AssertionError} if no match is found, giving a clear failure message.
   */
  private static String findFileContent(List<FileOp> fileOps, String fileName) {
    return fileOps.stream()
        .filter(op -> op instanceof FileOp.WriteString ws && ws.fileName.equals(fileName))
        .map(op -> ((FileOp.WriteString) op).fileContent)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No WriteString FileOp with fileName: " + fileName));
  }
}
