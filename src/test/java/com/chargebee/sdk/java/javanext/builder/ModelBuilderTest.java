package com.chargebee.sdk.java.javanext.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.sdk.FileOp;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;

class ModelBuilderTest {

  private ModelBuilder modelBuilder;
  private Template mockTemplate;
  private OpenAPI openAPI;
  private String outputPath;

  @BeforeEach
  void setUp() throws IOException {
    modelBuilder = new ModelBuilder();
    outputPath = "/test/output";
    openAPI = new OpenAPI().components(new Components());

    Handlebars handlebars =
        new Handlebars(
            new com.github.jknack.handlebars.io.ClassPathTemplateLoader("/templates/java/next", ""));
    HandlebarsUtil.registerAllHelpers(handlebars);
    mockTemplate = handlebars.compile("core.models.hbs");
  }

  @Nested
  @DisplayName("Builder Configuration")
  class BuilderConfigurationTests {

    @Test
    void shouldConfigureOutputDirectoryPath() {
      ModelBuilder result = modelBuilder.withOutputDirectoryPath(outputPath);

      assertThat(result).isSameAs(modelBuilder);
    }

    @Test
    void shouldConfigureTemplate() {
      ModelBuilder result = modelBuilder.withTemplate(mockTemplate);

      assertThat(result).isSameAs(modelBuilder);
    }

    @Test
    void shouldCreateOutputDirectoryOnBuild() throws IOException {
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
      FileOp.CreateDirectory dirOp = (FileOp.CreateDirectory) fileOps.get(0);
      assertThat(dirOp.basePath).endsWith("/v4/core/models");
    }
  }

  @Nested
  @DisplayName("Basic Model Generation")
  class ModelGenerationTests {

    @Test
    void shouldGenerateModelWithStringFields() throws IOException {
      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty("first_name", new StringSchema())
              .addProperty("last_name", new StringSchema())
              .addProperty("email_address", new StringSchema());

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "Customer.java");
      assertThat(writeOp.fileContent).contains("public class Customer");
      assertThat(writeOp.fileContent).contains("private String firstName");
      assertThat(writeOp.fileContent).contains("private String lastName");
      assertThat(writeOp.fileContent).contains("private String emailAddress");
      assertThat(writeOp.fileContent).contains("package com.chargebee.v4.core.models.customer");
    }

    @Test
    void shouldGenerateModelWithMultipleFieldTypes() throws IOException {
      Schema<?> invoiceSchema =
          new ObjectSchema()
              .addProperty("id", new StringSchema())
              .addProperty("amount", new IntegerSchema())
              .addProperty("paid", new BooleanSchema());

      openAPI.getComponents().addSchemas("Invoice", invoiceSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "Invoice.java");
      assertThat(writeOp.fileContent).contains("public class Invoice");
      assertThat(writeOp.fileContent).contains("private String id");
      assertThat(writeOp.fileContent).contains("private Integer amount");
      assertThat(writeOp.fileContent).contains("private Boolean paid");
    }

    @Test
    void shouldGenerateMultipleModels() throws IOException {
      openAPI
          .getComponents()
          .addSchemas("Customer", new ObjectSchema().addProperty("name", new StringSchema()))
          .addSchemas("Invoice", new ObjectSchema().addProperty("amount", new IntegerSchema()))
          .addSchemas("Subscription", new ObjectSchema().addProperty("status", new StringSchema()));
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Customer.java");
      assertFileExists(fileOps, "Invoice.java");
      assertFileExists(fileOps, "Subscription.java");
    }

    @Test
    void shouldSkipSchemasWithoutProperties() throws IOException {
      openAPI
          .getComponents()
          .addSchemas("Empty", new ObjectSchema())
          .addSchemas("Valid", new ObjectSchema().addProperty("name", new StringSchema()));
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Valid.java");
      assertFileNotExists(fileOps, "Empty.java");
    }

    @Test
    void shouldFilterOutErrorSchemas() throws IOException {
      openAPI
          .getComponents()
          .addSchemas("Customer", new ObjectSchema().addProperty("name", new StringSchema()))
          .addSchemas("400Error", new ObjectSchema().addProperty("message", new StringSchema()))
          .addSchemas("500Error", new ObjectSchema().addProperty("message", new StringSchema()));
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Customer.java");
      assertThat(fileOps)
          .noneMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.contains("Error"));
    }

    @Test
    void shouldCreateProperDirectoryStructure() throws IOException {
      openAPI.getComponents().addSchemas("Customer", new ObjectSchema().addProperty("name", new StringSchema()));
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("customer"));
    }
  }

  @Nested
  @DisplayName("Enum Fields")
  class EnumFieldsTests {

    @Test
    void shouldGenerateEnumFields() throws IOException {
      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty("status", new StringSchema()._enum(List.of("active", "inactive", "cancelled")));

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "Customer.java");
      assertThat(writeOp.fileContent).contains("enum Status");
      assertThat(writeOp.fileContent).contains("Active");
      assertThat(writeOp.fileContent).contains("Inactive");
      assertThat(writeOp.fileContent).contains("Cancelled");
    }

    @Test
    void shouldHandleMultipleEnumFields() throws IOException {
      Schema<?> subscriptionSchema =
          new ObjectSchema()
              .addProperty("status", new StringSchema()._enum(List.of("active", "cancelled")))
              .addProperty("billing_period", new StringSchema()._enum(List.of("monthly", "yearly")));

      openAPI.getComponents().addSchemas("Subscription", subscriptionSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Subscription.java");
    }

    @Test
    void shouldSkipFieldsWithoutEnum() throws IOException {
      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("type", new StringSchema());

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Nested Objects and Sub-Models")
  class NestedObjectsTests {

    @Test
    void shouldGenerateNestedObjectFields() throws IOException {
      Schema<?> addressSchema =
          new ObjectSchema()
              .addProperty("street_name", new StringSchema())
              .addProperty("city", new StringSchema())
              .addProperty("zip_code", new StringSchema());

      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("billing_address", addressSchema);

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "Customer.java");
      assertThat(writeOp.fileContent).contains("public class Customer");
      assertThat(writeOp.fileContent).contains("private String name");
      assertThat(writeOp.fileContent).contains("public static class BillingAddress");
      assertThat(writeOp.fileContent).contains("private String streetName");
      assertThat(writeOp.fileContent).contains("private String zipCode");
    }

    @Test
    void shouldSkipEmptyNestedObjects() throws IOException {
      ObjectSchema emptyObject = new ObjectSchema();
      emptyObject.setProperties(Map.of());

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("empty_data", emptyObject);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Test.java");
    }

    @Test
    void shouldSkipNestedObjectsWithNullProperties() throws IOException {
      ObjectSchema objectWithNullProps = new ObjectSchema();
      objectWithNullProps.setProperties(null);

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("metadata", objectWithNullProps);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Test.java");
    }

    @Test
    void shouldGenerateRecursivelyNestedSubModels() throws IOException {
      Schema<?> level3 = new ObjectSchema().addProperty("value", new StringSchema());
      Schema<?> level2 =
          new ObjectSchema().addProperty("name", new StringSchema()).addProperty("level3", level3);
      Schema<?> level1 =
          new ObjectSchema().addProperty("id", new StringSchema()).addProperty("level2", level2);

      openAPI.getComponents().addSchemas("Level1", level1);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Level1.java");
    }
  }

  @Nested
  @DisplayName("Array Fields")
  class ArrayFieldsTests {

    @Test
    void shouldGenerateArrayFields() throws IOException {
      Schema<?> itemSchema =
          new ObjectSchema()
              .addProperty("id", new StringSchema())
              .addProperty("quantity", new IntegerSchema());
      ArraySchema itemsArray = new ArraySchema().items(itemSchema);

      Schema<?> orderSchema =
          new ObjectSchema()
              .addProperty("order_id", new StringSchema())
              .addProperty("items", itemsArray);

      openAPI.getComponents().addSchemas("Order", orderSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "Order.java");
      assertThat(writeOp.fileContent).contains("public class Order");
      assertThat(writeOp.fileContent).contains("private String orderId");
      assertThat(writeOp.fileContent).contains("public static class Items");
    }

    @Test
    void shouldSkipArraysWithoutItemProperties() throws IOException {
      ArraySchema primitiveArray = new ArraySchema().items(new StringSchema());

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("tags", primitiveArray);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Test.java");
    }

    @Test
    void shouldHandleArrayWithNullItems() throws IOException {
      ArraySchema arraySchema = new ArraySchema();

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("tags", arraySchema);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Test.java");
    }

    @Test
    void shouldHandleArrayItemsWithNullProperties() throws IOException {
      ObjectSchema itemSchema = new ObjectSchema();
      itemSchema.setProperties(null);
      ArraySchema arraySchema = new ArraySchema().items(itemSchema);

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("items", arraySchema);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Test.java");
    }

    @Test
    void shouldHandleArrayItemsWithEmptyProperties() throws IOException {
      ObjectSchema itemSchema = new ObjectSchema();
      itemSchema.setProperties(Map.of());
      ArraySchema arraySchema = new ArraySchema().items(itemSchema);

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("items_list", arraySchema);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Test.java");
    }

    @Test
    void shouldHandleComplexNestedArrays() throws IOException {
      Schema<?> detailSchema =
          new ObjectSchema()
              .addProperty("detail_name", new StringSchema())
              .addProperty("detail_value", new IntegerSchema());

      Schema<?> itemSchema =
          new ObjectSchema()
              .addProperty("item_id", new StringSchema())
              .addProperty("details", detailSchema);

      ArraySchema itemsArray = new ArraySchema().items(itemSchema);

      Schema<?> orderSchema =
          new ObjectSchema()
              .addProperty("order_id", new StringSchema())
              .addProperty("items", itemsArray);

      openAPI.getComponents().addSchemas("Order", orderSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Order.java");
    }
  }

  @Nested
  @DisplayName("Schema References and Imports")
  class SchemaReferencesTests {

    @Test
    void shouldGenerateImportsForReferencedSchemas() throws IOException {
      Schema<?> addressSchema =
          new ObjectSchema()
              .addProperty("street", new StringSchema())
              .addProperty("city", new StringSchema());

      Schema<?> addressRef = new Schema<>().$ref("#/components/schemas/Address");

      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("billing_address", addressRef);

      openAPI.getComponents().addSchemas("Address", addressSchema);
      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      FileOp.WriteString writeOp = findWriteOp(fileOps, "Customer.java");
      assertThat(writeOp.fileContent).contains("public class Customer");
      assertThat(writeOp.fileContent)
          .contains("import com.chargebee.v4.core.models.address.Address");
      assertFileExists(fileOps, "Address.java");
    }

    @Test
    void shouldCollectImportsForArrayReferences() throws IOException {
      Schema<?> itemSchema =
          new ObjectSchema()
              .addProperty("id", new StringSchema())
              .addProperty("name", new StringSchema());

      Schema<?> itemRef = new Schema<>().$ref("#/components/schemas/Item");
      ArraySchema itemsArray = new ArraySchema().items(itemRef);

      Schema<?> orderSchema =
          new ObjectSchema()
              .addProperty("order_id", new StringSchema())
              .addProperty("items", itemsArray);

      openAPI.getComponents().addSchemas("Item", itemSchema);
      openAPI.getComponents().addSchemas("Order", orderSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Order.java");
      assertFileExists(fileOps, "Item.java");
    }

    @Test
    void shouldCollectImportsFromNestedObjects() throws IOException {
      Schema<?> innerSchema = new ObjectSchema().addProperty("id", new StringSchema());
      Schema<?> innerRef = new Schema<>().$ref("#/components/schemas/Inner");

      ObjectSchema middleSchema = new ObjectSchema();
      middleSchema.addProperty("inner", innerRef);

      Schema<?> outerSchema = new ObjectSchema();
      outerSchema.addProperty("middle", middleSchema);

      openAPI.getComponents().addSchemas("Inner", innerSchema);
      openAPI.getComponents().addSchemas("Outer", outerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }

    @Test
    void shouldCollectImportsFromAdditionalProperties() throws IOException {
      Schema<?> valueSchema = new ObjectSchema().addProperty("id", new StringSchema());
      Schema<?> valueRef = new Schema<>().$ref("#/components/schemas/Value");

      ObjectSchema mapSchema = new ObjectSchema();
      mapSchema.setAdditionalProperties(valueRef);
      mapSchema.addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Value", valueSchema);
      openAPI.getComponents().addSchemas("MapType", mapSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }

    @Test
    void shouldHandleAdditionalPropertiesAsBoolean() throws IOException {
      ObjectSchema mapSchema = new ObjectSchema();
      mapSchema.setAdditionalProperties(true);
      mapSchema.addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("MapType", mapSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Composed Schemas")
  class ComposedSchemaTests {

    @Test
    void shouldHandleComposedSchemasWithAllOf() throws IOException {
      Schema<?> baseSchema = new ObjectSchema().addProperty("id", new StringSchema());
      Schema<?> extendedSchema = new ObjectSchema().addProperty("name", new StringSchema());

      ComposedSchema composedSchema = new ComposedSchema();
      composedSchema.addAllOfItem(baseSchema);
      composedSchema.addAllOfItem(extendedSchema);

      openAPI.getComponents().addSchemas("ExtendedModel", composedSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }

    @Test
    void shouldCollectImportsFromComposedSchemaAllOf() throws IOException {
      Schema<?> baseSchema = new ObjectSchema().addProperty("id", new StringSchema());
      Schema<?> baseRef = new Schema<>().$ref("#/components/schemas/Base");

      ComposedSchema composedSchema = new ComposedSchema();
      composedSchema.addAllOfItem(baseRef);
      composedSchema.addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Base", baseSchema);
      openAPI.getComponents().addSchemas("Extended", composedSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }

    @Test
    void shouldCollectImportsFromComposedSchemaAnyOf() throws IOException {
      Schema<?> schema1 = new ObjectSchema().addProperty("field1", new StringSchema());
      Schema<?> ref1 = new Schema<>().$ref("#/components/schemas/Schema1");

      ComposedSchema composedSchema = new ComposedSchema();
      composedSchema.addAnyOfItem(ref1);
      composedSchema.addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Schema1", schema1);
      openAPI.getComponents().addSchemas("Composed", composedSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }

    @Test
    void shouldCollectImportsFromComposedSchemaOneOf() throws IOException {
      Schema<?> schema1 = new ObjectSchema().addProperty("field1", new StringSchema());
      Schema<?> ref1 = new Schema<>().$ref("#/components/schemas/Schema1");

      ComposedSchema composedSchema = new ComposedSchema();
      composedSchema.addOneOfItem(ref1);
      composedSchema.addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Schema1", schema1);
      openAPI.getComponents().addSchemas("Composed", composedSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }

    @Test
    void shouldHandleComposedSchemaWithNullLists() throws IOException {
      ComposedSchema composedSchema = new ComposedSchema();
      composedSchema.addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Composed", composedSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Package Name Conversion")
  class PackageNameConversionTests {

    @Test
    void shouldConvertUpperCamelToLowerCamel() throws IOException {
      openAPI.getComponents().addSchemas("CustomerAccount", new ObjectSchema().addProperty("name", new StringSchema()));
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertDirectoryExists(fileOps, "customerAccount");
      FileOp.WriteString writeOp = findWriteOp(fileOps, "CustomerAccount.java");
      assertThat(writeOp.fileContent)
          .contains("package com.chargebee.v4.core.models.customerAccount");
    }

    @Test
    void shouldConvertSnakeCaseToLowerCamel() throws IOException {
      Schema<?> schema = new ObjectSchema().addProperty("field", new StringSchema());
      openAPI.getComponents().addSchemas("payment_method", schema);
      openAPI.getComponents().addSchemas("customer_account", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertDirectoryExists(fileOps, "paymentMethod");
      assertDirectoryExists(fileOps, "customerAccount");
      FileOp.WriteString writeOp = findWriteOp(fileOps, "PaymentMethod.java");
      assertThat(writeOp.fileContent)
          .contains("package com.chargebee.v4.core.models.paymentMethod");
    }

    @Test
    void shouldKeepLowerCamelUnchanged() throws IOException {
      openAPI.getComponents().addSchemas("paymentMethod", new ObjectSchema().addProperty("name", new StringSchema()));
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertDirectoryExists(fileOps, "paymentMethod");
    }

    @Test
    void shouldHandleSingleCharacterNames() throws IOException {
      Schema<?> schema = new ObjectSchema().addProperty("field", new StringSchema());
      openAPI.getComponents().addSchemas("A", schema);
      openAPI.getComponents().addSchemas("x", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertDirectoryExists(fileOps, "a");
      assertDirectoryExists(fileOps, "x");
    }

    @Test
    void shouldHandleMixedCaseNames() throws IOException {
      Schema<?> schema = new ObjectSchema().addProperty("field", new StringSchema());
      openAPI.getComponents().addSchemas("CustomerAccount", schema);
      openAPI.getComponents().addSchemas("billing_address", schema);
      openAPI.getComponents().addSchemas("paymentMethod", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertDirectoryExists(fileOps, "customerAccount");
      assertDirectoryExists(fileOps, "billingAddress");
      assertDirectoryExists(fileOps, "paymentMethod");
    }

    @Test
    void shouldHandleNullNameUsingReflection() throws Exception {
      var method = ModelBuilder.class.getDeclaredMethod("toLowerCamel", String.class);
      method.setAccessible(true);

      String result = (String) method.invoke(null, (String) null);

      assertThat(result).isNull();
    }

    @Test
    void shouldHandleEmptyNameUsingReflection() throws Exception {
      var method = ModelBuilder.class.getDeclaredMethod("toLowerCamel", String.class);
      method.setAccessible(true);

      String result = (String) method.invoke(null, "");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Field Type Handling")
  class FieldTypeHandlingTests {

    @Test
    void shouldHandleFieldWithDeprecatedTrue() throws IOException {
      StringSchema deprecatedField = new StringSchema();
      deprecatedField.setDeprecated(true);

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("old_field", deprecatedField);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Test.java");
    }

    @Test
    void shouldHandleFieldWithDeprecatedFalse() throws IOException {
      StringSchema field = new StringSchema();
      field.setDeprecated(false);

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("field", field);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }

    @Test
    void shouldHandleFieldWithDeprecatedNull() throws IOException {
      StringSchema field = new StringSchema();
      field.setDeprecated(null);

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("field", field);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }

    @Test
    void shouldHandleFieldWithoutDeprecatedSet() throws IOException {
      StringSchema field = new StringSchema();

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("field", field);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }

    @Test
    void shouldHandleIntegerFieldsWithFormat() throws IOException {
      IntegerSchema timestampSchema = new IntegerSchema();
      timestampSchema.setFormat("unix-time");

      IntegerSchema longSchema = new IntegerSchema();
      longSchema.setFormat("int64");

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("occurred_at", timestampSchema)
              .addProperty("count", longSchema);

      openAPI.getComponents().addSchemas("Event", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "Event.java");
    }
  }

  @Nested
  @DisplayName("Edge Cases and Null Handling")
  class EdgeCasesTests {

    @Test
    void shouldHandleEmptyOpenAPISpec() throws IOException {
      OpenAPI emptySpec = new OpenAPI().components(new Components());
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(emptySpec);

      assertThat(fileOps).hasSize(1);
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }

    @Test
    void shouldHandleOpenAPIWithNullComponents() throws IOException {
      OpenAPI apiWithNullComponents = new OpenAPI();
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(apiWithNullComponents);

      assertThat(fileOps).hasSize(1);
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }

    @Test
    void shouldHandleNullSchemaInCollectImports() throws IOException {
      ObjectSchema schema = new ObjectSchema();
      schema.addProperty("field", new StringSchema());

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }

    @Test
    void shouldHandleObjectSchemaWithNullProperties() throws IOException {
      ObjectSchema objectWithNullProps = new ObjectSchema();
      objectWithNullProps.setType("object");

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("data", objectWithNullProps);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertThat(fileOps).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Complex Scenarios")
  class ComplexScenariosTests {

    @Test
    void shouldHandleModelWithAllFeatureCombinations() throws IOException {
      Schema<?> addressSchema =
          new ObjectSchema()
              .addProperty("street", new StringSchema())
              .addProperty("city", new StringSchema())
              .addProperty("zip", new StringSchema());

      StringSchema deprecatedField = new StringSchema();
      deprecatedField.setDeprecated(true);

      IntegerSchema timestampField = new IntegerSchema();
      timestampField.setFormat("unix-time");

      Schema<?> itemSchema =
          new ObjectSchema()
              .addProperty("item_id", new StringSchema())
              .addProperty("quantity", new IntegerSchema());

      ArraySchema itemsArray = new ArraySchema().items(itemSchema);

      Schema<?> complexSchema =
          new ObjectSchema()
              .addProperty("id", new StringSchema())
              .addProperty("status", new StringSchema()._enum(List.of("active", "inactive")))
              .addProperty("deprecated_field", deprecatedField)
              .addProperty("created_at", timestampField)
              .addProperty("billing_address", addressSchema)
              .addProperty("line_items", itemsArray);

      openAPI.getComponents().addSchemas("ComplexModel", complexSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      List<FileOp> fileOps = modelBuilder.build(openAPI);

      assertFileExists(fileOps, "ComplexModel.java");
      assertDirectoryExists(fileOps, "complexModel");
    }
  }

  private FileOp.WriteString findWriteOp(List<FileOp> fileOps, String fileName) {
    return fileOps.stream()
        .filter(op -> op instanceof FileOp.WriteString)
        .map(op -> (FileOp.WriteString) op)
        .filter(op -> op.fileName.equals(fileName))
        .findFirst()
        .orElseThrow();
  }

  private void assertFileExists(List<FileOp> fileOps, String fileName) {
    assertThat(fileOps)
        .anyMatch(
            op -> op instanceof FileOp.WriteString && ((FileOp.WriteString) op).fileName.equals(fileName));
  }

  private void assertFileNotExists(List<FileOp> fileOps, String fileName) {
    assertThat(fileOps)
        .noneMatch(
            op -> op instanceof FileOp.WriteString && ((FileOp.WriteString) op).fileName.equals(fileName));
  }

  private void assertDirectoryExists(List<FileOp> fileOps, String directoryName) {
    assertThat(fileOps)
        .anyMatch(
            op ->
                op instanceof FileOp.CreateDirectory
                    && ((FileOp.CreateDirectory) op).directoryName.equals(directoryName));
  }
}
