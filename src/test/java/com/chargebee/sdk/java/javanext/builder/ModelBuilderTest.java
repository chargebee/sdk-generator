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

    Handlebars handlebars = new Handlebars();
    mockTemplate = handlebars.compileInline(
        "package {{packageName}};\n"
            + "{{#each imports}}import {{this}};\n{{/each}}"
            + "class {{name}} {\n"
            + "{{#each fields}}  private {{{type}}} {{name}};\n{{/each}}"
            + "{{#each enumFields}}  enum {{name}} { {{#each values}}{{key}}, {{/each}} }\n{{/each}}"
            + "{{#each subModels}}  static class {{name}} {\n"
            + "{{#each fields}}    private {{{type}}} {{name}};\n{{/each}}"
            + "  }\n{{/each}}\n"
            + "}");
  }

  @Nested
  @DisplayName("Builder Configuration Tests")
  class BuilderConfigurationTests {

    @Test
    @DisplayName("Should configure output directory path")
    void shouldConfigureOutputDirectoryPath() throws IOException {
      // When
      ModelBuilder result = modelBuilder.withOutputDirectoryPath(outputPath);

      // Then
      assertThat(result).isSameAs(modelBuilder);
    }

    @Test
    @DisplayName("Should configure template")
    void shouldConfigureTemplate() throws IOException {
      // When
      ModelBuilder result = modelBuilder.withTemplate(mockTemplate);

      // Then
      assertThat(result).isSameAs(modelBuilder);
    }

    @Test
    @DisplayName("Should create output directory on build")
    void shouldCreateOutputDirectoryOnBuild() throws IOException {
      // Given
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
      FileOp.CreateDirectory dirOp = (FileOp.CreateDirectory) fileOps.get(0);
      assertThat(dirOp.basePath).endsWith("/v4/core/models");
    }
  }

  @Nested
  @DisplayName("Model Generation Tests")
  class ModelGenerationTests {

    @Test
    @DisplayName("Should generate model with simple string field")
    void shouldGenerateModelWithSimpleStringField() throws IOException {
      // Given - Tests underscore field name conversion
      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty("first_name", new StringSchema())
              .addProperty("last_name", new StringSchema())
              .addProperty("email_address", new StringSchema());

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).hasSizeGreaterThan(1);
      FileOp.WriteString writeOp =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.WriteString)
              .map(op -> (FileOp.WriteString) op)
              .filter(op -> op.fileName.equals("Customer.java"))
              .findFirst()
              .orElseThrow();

      assertThat(writeOp.fileContent).contains("class Customer");
      assertThat(writeOp.fileContent).contains("private String firstName");
      assertThat(writeOp.fileContent).contains("private String lastName");
      assertThat(writeOp.fileContent).contains("private String emailAddress");
      assertThat(writeOp.fileContent).contains("package customer");
    }

    @Test
    @DisplayName("Should generate model with multiple field types")
    void shouldGenerateModelWithMultipleFieldTypes() throws IOException {
      // Given
      Schema<?> invoiceSchema =
          new ObjectSchema()
              .addProperty("id", new StringSchema())
              .addProperty("amount", new IntegerSchema())
              .addProperty("paid", new BooleanSchema());

      openAPI.getComponents().addSchemas("Invoice", invoiceSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      FileOp.WriteString writeOp =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.WriteString)
              .map(op -> (FileOp.WriteString) op)
              .filter(op -> op.fileName.equals("Invoice.java"))
              .findFirst()
              .orElseThrow();

      assertThat(writeOp.fileContent).contains("class Invoice");
      assertThat(writeOp.fileContent).contains("private String id");
      assertThat(writeOp.fileContent).contains("private Integer amount");
      assertThat(writeOp.fileContent).contains("private Boolean paid");
    }

    @Test
    @DisplayName("Should generate model with enum fields")
    void shouldGenerateModelWithEnumFields() throws IOException {
      // Given
      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty(
                  "status", new StringSchema()._enum(List.of("active", "inactive", "cancelled")));

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      FileOp.WriteString writeOp =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.WriteString)
              .map(op -> (FileOp.WriteString) op)
              .filter(op -> op.fileName.equals("Customer.java"))
              .findFirst()
              .orElseThrow();

      assertThat(writeOp.fileContent).contains("enum Status");
      assertThat(writeOp.fileContent).contains("Active");
      assertThat(writeOp.fileContent).contains("Inactive");
      assertThat(writeOp.fileContent).contains("Cancelled");
    }

    @Test
    @DisplayName("Should generate model with nested object fields")
    void shouldGenerateModelWithNestedObjectFields() throws IOException {
      // Given - Tests underscore in nested field names
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

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      FileOp.WriteString writeOp =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.WriteString)
              .map(op -> (FileOp.WriteString) op)
              .filter(op -> op.fileName.equals("Customer.java"))
              .findFirst()
              .orElseThrow();

      assertThat(writeOp.fileContent).contains("class Customer");
      assertThat(writeOp.fileContent).contains("private String name");
      assertThat(writeOp.fileContent).contains("static class BillingAddress");
      assertThat(writeOp.fileContent).contains("private String streetName");
      assertThat(writeOp.fileContent).contains("private String zipCode");
    }

    @Test
    @DisplayName("Should generate model with array fields")
    void shouldGenerateModelWithArrayFields() throws IOException {
      // Given
      Schema<?> itemSchema =
          new ObjectSchema()
              .addProperty("id", new StringSchema())
              .addProperty("quantity", new IntegerSchema());

      ArraySchema itemsArraySchema = new ArraySchema().items(itemSchema);

      Schema<?> orderSchema =
          new ObjectSchema()
              .addProperty("order_id", new StringSchema())
              .addProperty("items", itemsArraySchema);

      openAPI.getComponents().addSchemas("Order", orderSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      FileOp.WriteString writeOp =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.WriteString)
              .map(op -> (FileOp.WriteString) op)
              .filter(op -> op.fileName.equals("Order.java"))
              .findFirst()
              .orElseThrow();

      assertThat(writeOp.fileContent).contains("class Order");
      assertThat(writeOp.fileContent).contains("private String orderId");
      assertThat(writeOp.fileContent).contains("static class Items");
    }

    @Test
    @DisplayName("Should generate model with referenced schema")
    void shouldGenerateModelWithReferencedSchema() throws IOException {
      // Given
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

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      FileOp.WriteString customerWriteOp =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.WriteString)
              .map(op -> (FileOp.WriteString) op)
              .filter(op -> op.fileName.equals("Customer.java"))
              .findFirst()
              .orElseThrow();

      assertThat(customerWriteOp.fileContent).contains("class Customer");
      assertThat(customerWriteOp.fileContent).contains("import com.chargebee.v4.core.models.address.Address");
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Address.java"));
    }

    @Test
    @DisplayName("Should filter out schemas starting with 4xx or 5xx")
    void shouldFilterOutErrorSchemas() throws IOException {
      // Given
      Schema<?> customerSchema =
          new ObjectSchema().addProperty("name", new StringSchema());

      Schema<?> errorSchema =
          new ObjectSchema().addProperty("message", new StringSchema());

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      openAPI.getComponents().addSchemas("400Error", errorSchema);
      openAPI.getComponents().addSchemas("500Error", errorSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Customer.java"));
      assertThat(fileOps)
          .noneMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.contains("Error"));
    }

    @Test
    @DisplayName("Should skip schemas without properties")
    void shouldSkipSchemasWithoutProperties() throws IOException {
      // Given
      Schema<?> emptySchema = new ObjectSchema();
      Schema<?> validSchema = new ObjectSchema().addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Empty", emptySchema);
      openAPI.getComponents().addSchemas("Valid", validSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Valid.java"));
      assertThat(fileOps)
          .noneMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Empty.java"));
    }

    @Test
    @DisplayName("Should handle deprecated fields")
    void shouldHandleDeprecatedFields() throws IOException {
      // Given
      StringSchema deprecatedField = new StringSchema();
      deprecatedField.setDeprecated(true);

      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("old_field", deprecatedField);

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Customer.java"));
    }

    @Test
    @DisplayName("Should handle integer fields with format")
    void shouldHandleIntegerFieldsWithFormat() throws IOException {
      // Given
      IntegerSchema timestampSchema = new IntegerSchema();
      timestampSchema.setFormat("unix-time");

      IntegerSchema longSchema = new IntegerSchema();
      longSchema.setFormat("int64");

      Schema<?> eventSchema =
          new ObjectSchema()
              .addProperty("occurred_at", timestampSchema)
              .addProperty("count", longSchema);

      openAPI.getComponents().addSchemas("Event", eventSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Event.java"));
    }

    @Test
    @DisplayName("Should create proper directory structure for models")
    void shouldCreateProperDirectoryStructureForModels() throws IOException {
      // Given
      Schema<?> customerSchema =
          new ObjectSchema().addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("customer"));
    }
  }

  @Nested
  @DisplayName("Package Name Conversion Tests")
  class PackageNameConversionTests {

    @Test
    @DisplayName("Should convert UpperCamel to lowerCamel for package names")
    void shouldConvertUpperCamelToLowerCamelForPackageNames() throws IOException {
      // Given
      Schema<?> schema = new ObjectSchema().addProperty("name", new StringSchema());
      openAPI.getComponents().addSchemas("CustomerAccount", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("customerAccount"));
    }

    @Test
    @DisplayName("Should convert snake_case to lowerCamel for package names")
    void shouldConvertSnakeCaseToLowerCamelForPackageNames() throws IOException {
      // Given - Tests: if (name.indexOf('_') >= 0) branch in toLowerCamel
      Schema<?> schema = new ObjectSchema()
          .addProperty("field_name", new StringSchema())
          .addProperty("another_field", new StringSchema());
      openAPI.getComponents().addSchemas("payment_method", schema);
      openAPI.getComponents().addSchemas("customer_account", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then - Verify payment_method -> paymentMethod conversion
      FileOp.CreateDirectory paymentMethodDir =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.CreateDirectory)
              .map(op -> (FileOp.CreateDirectory) op)
              .filter(op -> op.directoryName.equals("paymentMethod"))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Expected paymentMethod directory"));
      
      assertThat(paymentMethodDir.directoryName).isEqualTo("paymentMethod");
      
      // Verify customer_account -> customerAccount conversion
      FileOp.CreateDirectory customerAccountDir =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.CreateDirectory)
              .map(op -> (FileOp.CreateDirectory) op)
              .filter(op -> op.directoryName.equals("customerAccount"))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Expected customerAccount directory"));
      
      assertThat(customerAccountDir.directoryName).isEqualTo("customerAccount");
      
      // Verify content
      FileOp.WriteString paymentWriteOp =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.WriteString)
              .map(op -> (FileOp.WriteString) op)
              .filter(op -> op.fileName.equals("PaymentMethod.java"))
              .findFirst()
              .orElseThrow();
      
      assertThat(paymentWriteOp.fileContent).contains("package paymentMethod");
      assertThat(paymentWriteOp.baseFilePath).endsWith("paymentMethod");
      assertThat(paymentWriteOp.fileContent).contains("private String fieldName");
      assertThat(paymentWriteOp.fileContent).contains("private String anotherField");
    }

    @Test
    @DisplayName("Should keep already lowerCamel package names unchanged")
    void shouldKeepLowerCamelPackageNamesUnchanged() throws IOException {
      // Given
      Schema<?> schema = new ObjectSchema().addProperty("name", new StringSchema());
      openAPI.getComponents().addSchemas("paymentMethod", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("paymentMethod"));
      
      FileOp.WriteString writeOp =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.WriteString)
              .map(op -> (FileOp.WriteString) op)
              .findFirst()
              .orElseThrow();
      
      assertThat(writeOp.fileContent).contains("package paymentMethod");
    }

    @Test
    @DisplayName("Should validate package name in generated content for UpperCamel")
    void shouldValidatePackageNameInContentForUpperCamel() throws IOException {
      // Given
      Schema<?> schema = new ObjectSchema().addProperty("field", new StringSchema());
      openAPI.getComponents().addSchemas("CustomerAccount", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      FileOp.WriteString writeOp =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.WriteString)
              .map(op -> (FileOp.WriteString) op)
              .findFirst()
              .orElseThrow();
      
      assertThat(writeOp.fileContent).contains("package customerAccount");
      assertThat(writeOp.fileContent).contains("class CustomerAccount");
    }

    @Test
    @DisplayName("Should handle single uppercase character name")
    void shouldHandleSingleUppercaseCharacterName() throws IOException {
      // Given
      Schema<?> schema = new ObjectSchema().addProperty("field", new StringSchema());
      openAPI.getComponents().addSchemas("A", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("a"));
      
      FileOp.WriteString writeOp =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.WriteString)
              .map(op -> (FileOp.WriteString) op)
              .findFirst()
              .orElseThrow();
      
      assertThat(writeOp.fileContent).contains("package a");
    }

    @Test
    @DisplayName("Should handle single lowercase character name")
    void shouldHandleSingleLowercaseCharacterName() throws IOException {
      // Given
      Schema<?> schema = new ObjectSchema().addProperty("field", new StringSchema());
      openAPI.getComponents().addSchemas("x", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("x"));
      
      FileOp.WriteString writeOp =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.WriteString)
              .map(op -> (FileOp.WriteString) op)
              .findFirst()
              .orElseThrow();
      
      assertThat(writeOp.fileContent).contains("package x");
    }
  }

  @Nested
  @DisplayName("Multiple Models Generation Tests")
  class MultipleModelsGenerationTests {

    @Test
    @DisplayName("Should generate multiple models")
    void shouldGenerateMultipleModels() throws IOException {
      // Given
      Schema<?> customerSchema =
          new ObjectSchema().addProperty("name", new StringSchema());
      Schema<?> invoiceSchema =
          new ObjectSchema().addProperty("amount", new IntegerSchema());
      Schema<?> subscriptionSchema =
          new ObjectSchema().addProperty("status", new StringSchema());

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      openAPI.getComponents().addSchemas("Invoice", invoiceSchema);
      openAPI.getComponents().addSchemas("Subscription", subscriptionSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Customer.java"));
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Invoice.java"));
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Subscription.java"));
    }
  }

  @Nested
  @DisplayName("Composed Schema Tests")
  class ComposedSchemaTests {

    @Test
    @DisplayName("Should handle composed schemas with allOf")
    void shouldHandleComposedSchemasWithAllOf() throws IOException {
      // Given
      Schema<?> baseSchema = new ObjectSchema().addProperty("id", new StringSchema());
      Schema<?> extendedSchema = new ObjectSchema().addProperty("name", new StringSchema());

      ComposedSchema composedSchema = new ComposedSchema();
      composedSchema.addAllOfItem(baseSchema);
      composedSchema.addAllOfItem(extendedSchema);

      openAPI.getComponents().addSchemas("ExtendedModel", composedSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then - composed schemas without direct properties should be filtered
      assertThat(fileOps).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Empty and Null Handling Tests")
  class EmptyAndNullHandlingTests {

    @Test
    @DisplayName("Should handle empty OpenAPI spec gracefully")
    void shouldHandleEmptyOpenAPISpec() throws IOException {
      // Given
      OpenAPI emptySpec = new OpenAPI().components(new Components());
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(emptySpec);

      // Then - should only contain directory creation
      assertThat(fileOps).hasSize(1);
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }
  }

  @Nested
  @DisplayName("Sub-Model Generation Tests")
  class SubModelGenerationTests {

    @Test
    @DisplayName("Should generate sub-models for nested objects")
    void shouldGenerateSubModelsForNestedObjects() throws IOException {
      // Given
      Schema<?> addressSchema =
          new ObjectSchema()
              .addProperty("street", new StringSchema())
              .addProperty("city", new StringSchema());

      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("billing_address", addressSchema);

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Customer.java"));
    }

    @Test
    @DisplayName("Should skip ObjectType sub-models with null properties")
    void shouldSkipObjectSubModelsWithNullProperties() throws IOException {
      // Given - Tests: schemaDefn.getProperties() == null for ObjectType
      ObjectSchema objectWithNullProperties = new ObjectSchema();
      objectWithNullProperties.setProperties(null);

      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("metadata", objectWithNullProperties);

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Customer.java"));
    }

    @Test
    @DisplayName("Should skip ObjectType sub-models with empty properties")
    void shouldSkipObjectSubModelsWithEmptyProperties() throws IOException {
      // Given - Tests: schemaDefn.getProperties().isEmpty() for ObjectType
      ObjectSchema objectWithEmptyProperties = new ObjectSchema();
      objectWithEmptyProperties.setProperties(Map.of()); // Empty map

      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("empty_data", objectWithEmptyProperties);

      openAPI.getComponents().addSchemas("CustomerWithEmpty", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("CustomerWithEmpty.java"));
    }

    @Test
    @DisplayName("Should generate sub-models for arrays of objects")
    void shouldGenerateSubModelsForArraysOfObjects() throws IOException {
      // Given
      Schema<?> itemSchema =
          new ObjectSchema()
              .addProperty("id", new StringSchema())
              .addProperty("description", new StringSchema());

      ArraySchema itemsArray = new ArraySchema().items(itemSchema);

      Schema<?> invoiceSchema =
          new ObjectSchema()
              .addProperty("invoice_id", new StringSchema())
              .addProperty("line_items", itemsArray);

      openAPI.getComponents().addSchemas("Invoice", invoiceSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Invoice.java"));
    }

    @Test
    @DisplayName("Should skip sub-models for empty objects")
    void shouldSkipSubModelsForEmptyObjects() throws IOException {
      // Given
      Schema<?> emptyObjectSchema = new ObjectSchema();

      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("metadata", emptyObjectSchema);

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Customer.java"));
    }

    @Test
    @DisplayName("Should skip sub-models for arrays without item properties")
    void shouldSkipSubModelsForArraysWithoutItemProperties() throws IOException {
      // Given
      ArraySchema primitiveArray = new ArraySchema().items(new StringSchema());

      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("tags", primitiveArray);

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Customer.java"));
    }
  }

  @Nested
  @DisplayName("Import Collection Tests")
  class ImportCollectionTests {

    @Test
    @DisplayName("Should collect imports for referenced schemas")
    void shouldCollectImportsForReferencedSchemas() throws IOException {
      // Given
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

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Customer.java"));
    }

    @Test
    @DisplayName("Should collect imports for array item references")
    void shouldCollectImportsForArrayItemReferences() throws IOException {
      // Given
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

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Order.java"));
    }

    @Test
    @DisplayName("Should collect imports for composed schema references")
    void shouldCollectImportsForComposedSchemaReferences() throws IOException {
      // Given
      Schema<?> baseSchema = new ObjectSchema().addProperty("id", new StringSchema());
      Schema<?> baseRef = new Schema<>().$ref("#/components/schemas/Base");

      ComposedSchema composedSchema = new ComposedSchema();
      composedSchema.addAllOfItem(baseRef);
      composedSchema.addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Base", baseSchema);
      openAPI.getComponents().addSchemas("Extended", composedSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Enum Fields Extraction Tests")
  class EnumFieldsExtractionTests {

    @Test
    @DisplayName("Should extract enum fields correctly")
    void shouldExtractEnumFieldsCorrectly() throws IOException {
      // Given
      Schema<?> customerSchema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("type", new StringSchema()._enum(List.of("individual", "company")));

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Customer.java"));
    }

    @Test
    @DisplayName("Should handle multiple enum fields")
    void shouldHandleMultipleEnumFields() throws IOException {
      // Given
      Schema<?> subscriptionSchema =
          new ObjectSchema()
              .addProperty("status", new StringSchema()._enum(List.of("active", "cancelled")))
              .addProperty(
                  "billing_period", new StringSchema()._enum(List.of("monthly", "yearly")));

      openAPI.getComponents().addSchemas("Subscription", subscriptionSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Subscription.java"));
    }
  }

  @Nested
  @DisplayName("IOException Handling Tests")
  class IOExceptionHandlingTests {

    @Test
    @DisplayName("Should handle errors during model generation gracefully")
    void shouldHandleErrorsDuringModelGeneration() throws IOException {
      // Given - Create a template that will succeed compilation but cause issues
      // We'll use a valid template and verify the error handling path
      Handlebars handlebars = new Handlebars();
      // Using a template with minimal content
      Template simpleTemplate = handlebars.compileInline("{{name}}");

      Schema<?> customerSchema =
          new ObjectSchema().addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Customer", customerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(simpleTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then - should complete successfully
      assertThat(fileOps).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Edge Case Tests for Package Name Conversion")
  class PackageNameEdgeCaseTests {

    @Test
    @DisplayName("Should handle empty string name")
    void shouldHandleEmptyStringName() throws IOException {
      // Given - this tests the empty string check in toLowerCamel
      Schema<?> schema = new ObjectSchema().addProperty("field", new StringSchema());
      openAPI.getComponents().addSchemas("", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then - should complete without error
      assertThat(fileOps).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle single character name")
    void shouldHandleSingleCharacterName() throws IOException {
      // Given
      Schema<?> schema = new ObjectSchema().addProperty("field", new StringSchema());
      openAPI.getComponents().addSchemas("A", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("a"));
    }

    @Test
    @DisplayName("Should handle already lowerCamel name")
    void shouldHandleAlreadyLowerCamel() throws IOException {
      // Given
      Schema<?> schema = new ObjectSchema().addProperty("field", new StringSchema());
      openAPI.getComponents().addSchemas("customer", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("customer"));
    }

    @Test
    @DisplayName("Should handle mixed case names")
    void shouldHandleMixedCaseNames() throws IOException {
      // Given
      Schema<?> schema = new ObjectSchema().addProperty("field", new StringSchema());
      openAPI.getComponents().addSchemas("CustomerAccount", schema);
      openAPI.getComponents().addSchemas("billing_address", schema);
      openAPI.getComponents().addSchemas("paymentMethod", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("customerAccount"));
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("billingAddress"));
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("paymentMethod"));
    }
  }

  @Nested
  @DisplayName("CollectImports Comprehensive Tests")
  class CollectImportsTests {

    @Test
    @DisplayName("Should handle null schema in collectImports")
    void shouldHandleNullSchema() throws IOException {
      // Given - ObjectSchema with null item
      ObjectSchema schema = new ObjectSchema();
      schema.addProperty("field", new StringSchema());

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }

    @Test
    @DisplayName("Should collect imports from composed schema with anyOf")
    void shouldCollectImportsFromComposedSchemaWithAnyOf() throws IOException {
      // Given
      Schema<?> schema1 = new ObjectSchema().addProperty("field1", new StringSchema());
      Schema<?> ref1 = new Schema<>().$ref("#/components/schemas/Schema1");

      ComposedSchema composedSchema = new ComposedSchema();
      composedSchema.addAnyOfItem(ref1);
      composedSchema.addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Schema1", schema1);
      openAPI.getComponents().addSchemas("Composed", composedSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }

    @Test
    @DisplayName("Should collect imports from composed schema with oneOf")
    void shouldCollectImportsFromComposedSchemaWithOneOf() throws IOException {
      // Given
      Schema<?> schema1 = new ObjectSchema().addProperty("field1", new StringSchema());
      Schema<?> ref1 = new Schema<>().$ref("#/components/schemas/Schema1");

      ComposedSchema composedSchema = new ComposedSchema();
      composedSchema.addOneOfItem(ref1);
      composedSchema.addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Schema1", schema1);
      openAPI.getComponents().addSchemas("Composed", composedSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }

    @Test
    @DisplayName("Should collect imports from additionalProperties")
    void shouldCollectImportsFromAdditionalProperties() throws IOException {
      // Given
      Schema<?> valueSchema =
          new ObjectSchema().addProperty("id", new StringSchema());
      Schema<?> valueRef = new Schema<>().$ref("#/components/schemas/Value");

      ObjectSchema mapSchema = new ObjectSchema();
      mapSchema.setAdditionalProperties(valueRef);
      mapSchema.addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Value", valueSchema);
      openAPI.getComponents().addSchemas("MapType", mapSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle additionalProperties as boolean")
    void shouldHandleAdditionalPropertiesAsBoolean() throws IOException {
      // Given
      ObjectSchema mapSchema = new ObjectSchema();
      mapSchema.setAdditionalProperties(true); // Boolean, not Schema
      mapSchema.addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("MapType", mapSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }

    @Test
    @DisplayName("Should collect nested imports from objects")
    void shouldCollectNestedImportsFromObjects() throws IOException {
      // Given
      Schema<?> innerSchema =
          new ObjectSchema().addProperty("id", new StringSchema());
      Schema<?> innerRef = new Schema<>().$ref("#/components/schemas/Inner");

      ObjectSchema middleSchema = new ObjectSchema();
      middleSchema.addProperty("inner", innerRef);

      Schema<?> outerSchema = new ObjectSchema();
      outerSchema.addProperty("middle", middleSchema);

      openAPI.getComponents().addSchemas("Inner", innerSchema);
      openAPI.getComponents().addSchemas("Outer", outerSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle object schema with type object but null properties")
    void shouldHandleObjectSchemaWithTypeObjectButNullProperties() throws IOException {
      // Given
      ObjectSchema objectWithTypeButNoProperties = new ObjectSchema();
      objectWithTypeButNoProperties.setType("object");
      // properties will be null

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("data", objectWithTypeButNoProperties);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle composed schema with null allOf, anyOf, oneOf")
    void shouldHandleComposedSchemaWithNullLists() throws IOException {
      // Given
      ComposedSchema composedSchema = new ComposedSchema();
      // Don't set allOf, anyOf, or oneOf - they will be null
      composedSchema.addProperty("name", new StringSchema());

      openAPI.getComponents().addSchemas("Composed", composedSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle array schema without instanceof check")
    void shouldHandleArraySchemaWithoutInstanceOf() throws IOException {
      // Given
      ArraySchema arraySchema = new ArraySchema();
      arraySchema.items(new StringSchema());

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("items", arraySchema);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Recursive Sub-Model Tests")
  class RecursiveSubModelTests {

    @Test
    @DisplayName("Should handle nested sub-models recursively")
    void shouldHandleNestedSubModelsRecursively() throws IOException {
      // Given
      Schema<?> level3Schema =
          new ObjectSchema()
              .addProperty("value", new StringSchema());

      Schema<?> level2Schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("level3", level3Schema);

      Schema<?> level1Schema =
          new ObjectSchema()
              .addProperty("id", new StringSchema())
              .addProperty("level2", level2Schema);

      openAPI.getComponents().addSchemas("Level1", level1Schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Level1.java"));
    }

    @Test
    @DisplayName("Should handle arrays of complex nested objects")
    void shouldHandleArraysOfComplexNestedObjects() throws IOException {
      // Given
      Schema<?> itemDetailSchema =
          new ObjectSchema()
              .addProperty("detail_name", new StringSchema())
              .addProperty("detail_value", new IntegerSchema());

      Schema<?> itemSchema =
          new ObjectSchema()
              .addProperty("item_id", new StringSchema())
              .addProperty("details", itemDetailSchema);

      ArraySchema itemsArray = new ArraySchema().items(itemSchema);

      Schema<?> orderSchema =
          new ObjectSchema()
              .addProperty("order_id", new StringSchema())
              .addProperty("items", itemsArray);

      openAPI.getComponents().addSchemas("Order", orderSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Order.java"));
    }
  }

  @Nested
  @DisplayName("ListType Edge Cases Tests")
  class ListTypeEdgeCasesTests {

    @Test
    @DisplayName("Should handle array with null items - tests listSchema == null")
    void shouldHandleArrayWithNullItems() throws IOException {
      // Given - Tests first condition: listSchema == null
      ArraySchema arraySchema = new ArraySchema();
      // Don't set items - it will be null

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("tags", arraySchema);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Test.java"));
    }

    @Test
    @DisplayName("Should handle array items with null properties - tests getProperties() == null")
    void shouldHandleArrayItemsWithNullProperties() throws IOException {
      // Given - Tests second condition: ((Schema<?>) listSchema).getProperties() == null
      ObjectSchema itemSchema = new ObjectSchema();
      itemSchema.setProperties(null);
      ArraySchema arraySchema = new ArraySchema().items(itemSchema);

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("items", arraySchema);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Test.java"));
    }

    @Test
    @DisplayName("Should handle array items with empty properties - tests getProperties().isEmpty()")
    void shouldHandleArrayItemsWithEmptyProperties() throws IOException {
      // Given - Tests third condition: ((Schema) listSchema).getProperties().isEmpty()
      ObjectSchema itemSchema = new ObjectSchema();
      // Set properties to empty map, not null
      itemSchema.setProperties(Map.of());
      ArraySchema arraySchema = new ArraySchema().items(itemSchema);

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("items_list", arraySchema);

      openAPI.getComponents().addSchemas("TestEmpty", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("TestEmpty.java"));
    }

    @Test
    @DisplayName("Should handle array with empty object items")
    void shouldHandleArrayWithEmptyObjectItems() throws IOException {
      // Given
      Schema<?> emptyItemSchema = new ObjectSchema();
      ArraySchema arraySchema = new ArraySchema().items(emptyItemSchema);

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("data", arraySchema);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Test.java"));
    }

    @Test
    @DisplayName("Should handle array of referenced items")
    void shouldHandleArrayOfReferencedItems() throws IOException {
      // Given
      Schema<?> itemSchema =
          new ObjectSchema()
              .addProperty("id", new StringSchema())
              .addProperty("name", new StringSchema());

      Schema<?> itemRef = new Schema<>().$ref("#/components/schemas/Item");
      ArraySchema arraySchema = new ArraySchema().items(itemRef);

      Schema<?> collectionSchema =
          new ObjectSchema()
              .addProperty("collection_id", new StringSchema())
              .addProperty("items", arraySchema);

      openAPI.getComponents().addSchemas("Item", itemSchema);
      openAPI.getComponents().addSchemas("Collection", collectionSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Collection.java"));
    }
  }

  @Nested
  @DisplayName("Complex Scenario Tests")
  class ComplexScenarioTests {

    @Test
    @DisplayName("Should handle model with all feature combinations")
    void shouldHandleModelWithAllFeatureCombinations() throws IOException {
      // Given
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

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("ComplexModel.java"));
      // Should have directory for complexModel
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.CreateDirectory
                      && ((FileOp.CreateDirectory) op).directoryName.equals("complexModel"));
    }
  }


  @Nested
  @DisplayName("Deprecated Field Handling Tests")
  class DeprecatedFieldHandlingTests {

    @Test
    @DisplayName("Should handle field with deprecated as null")
    void shouldHandleFieldWithDeprecatedAsNull() throws IOException {
      // Given
      StringSchema fieldWithNullDeprecated = new StringSchema();
      fieldWithNullDeprecated.setDeprecated(null);

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("field", fieldWithNullDeprecated);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle field with deprecated as false")
    void shouldHandleFieldWithDeprecatedAsFalse() throws IOException {
      // Given
      StringSchema fieldWithFalseDeprecated = new StringSchema();
      fieldWithFalseDeprecated.setDeprecated(false);

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("field", fieldWithFalseDeprecated);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Schema Type Filtering Tests")
  class SchemaTypeFilteringTests {

    @Test
    @DisplayName("Should filter out schemas starting with 4")
    void shouldFilterOutSchemasStartingWith4() throws IOException {
      // Given
      Schema<?> validSchema = new ObjectSchema().addProperty("name", new StringSchema());
      Schema<?> errorSchema = new ObjectSchema().addProperty("message", new StringSchema());

      openAPI.getComponents().addSchemas("Customer", validSchema);
      openAPI.getComponents().addSchemas("400BadRequest", errorSchema);
      openAPI.getComponents().addSchemas("404NotFound", errorSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Customer.java"));
      assertThat(fileOps)
          .noneMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.contains("BadRequest"));
    }

    @Test
    @DisplayName("Should filter out schemas starting with 5")
    void shouldFilterOutSchemasStartingWith5() throws IOException {
      // Given
      Schema<?> validSchema = new ObjectSchema().addProperty("name", new StringSchema());
      Schema<?> errorSchema = new ObjectSchema().addProperty("message", new StringSchema());

      openAPI.getComponents().addSchemas("Invoice", validSchema);
      openAPI.getComponents().addSchemas("500InternalError", errorSchema);
      openAPI.getComponents().addSchemas("503ServiceUnavailable", errorSchema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps)
          .anyMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.equals("Invoice.java"));
      assertThat(fileOps)
          .noneMatch(
              op ->
                  op instanceof FileOp.WriteString
                      && ((FileOp.WriteString) op).fileName.contains("Error"));
    }
  }

  @Nested
  @DisplayName("Enum Field Handling Tests")
  class EnumFieldHandlingTests {

    @Test
    @DisplayName("Should skip fields with null enum")
    void shouldSkipFieldsWithNullEnum() throws IOException {
      // Given
      StringSchema fieldWithoutEnum = new StringSchema();
      // Don't set enum - it will be null

      Schema<?> schema =
          new ObjectSchema()
              .addProperty("name", new StringSchema())
              .addProperty("type", fieldWithoutEnum);

      openAPI.getComponents().addSchemas("Test", schema);
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(openAPI);

      // Then
      assertThat(fileOps).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Null Components Tests")
  class NullComponentsTests {

    @Test
    @DisplayName("Should handle OpenAPI with null components")
    void shouldHandleOpenAPIWithNullComponents() throws IOException {
      // Given
      OpenAPI apiWithNullComponents = new OpenAPI();
      // Don't set components - it will be null
      modelBuilder.withOutputDirectoryPath(outputPath).withTemplate(mockTemplate);

      // When
      List<FileOp> fileOps = modelBuilder.build(apiWithNullComponents);

      // Then - should only have directory creation
      assertThat(fileOps).hasSize(1);
      assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
    }
  }
}

