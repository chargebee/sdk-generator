package com.chargebee.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Test suite for Action class.
 *
 * <p>Validates the behavior of Action methods, particularly focusing on request body parameter
 * extraction and the correct handling of required vs optional fields.
 */
@DisplayName("Action")
class ActionTest {

  private static final String RESOURCE_ID = "x-chargebee-resource-id";
  private static final String SDK_METHOD_NAME = "x-chargebee-sdk-method-name";

  @Nested
  @DisplayName("requestBodyParameters()")
  class RequestBodyParametersTests {

    @Test
    @DisplayName("Should only mark fields as required when they are in schema's required list")
    void shouldOnlyMarkFieldsAsRequiredWhenInSchemaRequiredList() {
      // Arrange: Create a POST operation with mixed required/optional fields
      ObjectSchema requestSchema = new ObjectSchema();
      
      // Add properties
      requestSchema.addProperty("id", new StringSchema());
      requestSchema.addProperty("email", new StringSchema());
      requestSchema.addProperty("first_name", new StringSchema());
      requestSchema.addProperty("last_name", new StringSchema());
      
      // Only "id" and "email" are required according to schema
      requestSchema.setRequired(List.of("id", "email"));

      Operation operation = createPostOperation("customer", "create", requestSchema);
      Action action = new Action(HttpRequestType.POST, operation, "/customers");

      // Act
      List<com.chargebee.openapi.parameter.Parameter> params = action.requestBodyParameters();

      // Assert
      assertThat(params).hasSize(4);
      
      // Find each parameter and check required status
      com.chargebee.openapi.parameter.Parameter idParam = 
          findParameter(params, "id");
      com.chargebee.openapi.parameter.Parameter emailParam = 
          findParameter(params, "email");
      com.chargebee.openapi.parameter.Parameter firstNameParam = 
          findParameter(params, "first_name");
      com.chargebee.openapi.parameter.Parameter lastNameParam = 
          findParameter(params, "last_name");

      assertThat(idParam.isRequired).isTrue();
      assertThat(emailParam.isRequired).isTrue();
      assertThat(firstNameParam.isRequired).isFalse();
      assertThat(lastNameParam.isRequired).isFalse();
    }

    @Test
    @DisplayName(
        "Should not mark fields as required based on individual property's required attribute")
    void shouldNotMarkFieldsAsRequiredBasedOnPropertyRequiredAttribute() {
      // Arrange: Create a POST operation where individual properties have required attribute
      // but are not in schema's required list
      ObjectSchema requestSchema = new ObjectSchema();

      // Create a property with required attribute set
      StringSchema nameSchema = new StringSchema();
      nameSchema.setRequired(Arrays.asList("name")); // This should be ignored
      
      requestSchema.addProperty("name", nameSchema);
      requestSchema.addProperty("description", new StringSchema());

      // Schema's required list is empty - no fields should be required
      requestSchema.setRequired(List.of());

      Operation operation = createPostOperation("item", "create", requestSchema);
      Action action = new Action(HttpRequestType.POST, operation, "/items");

      // Act
      List<com.chargebee.openapi.parameter.Parameter> params = action.requestBodyParameters();

      // Assert
      assertThat(params).hasSize(2);
      
      // Both should be optional since they're not in schema's required list
      com.chargebee.openapi.parameter.Parameter nameParam = 
          findParameter(params, "name");
      com.chargebee.openapi.parameter.Parameter descParam = 
          findParameter(params, "description");

      assertThat(nameParam.isRequired)
          .as("name should not be required even though property has required attribute")
          .isFalse();
      assertThat(descParam.isRequired).isFalse();
    }

    @Test
    @DisplayName("Should return empty list when schema has no required list")
    void shouldHandleSchemaWithNoRequiredList() {
      // Arrange
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("optional_field_1", new StringSchema());
      requestSchema.addProperty("optional_field_2", new IntegerSchema());
      requestSchema.addProperty("optional_field_3", new BooleanSchema());
      // No required list set (null)

      Operation operation = createPostOperation("resource", "update", requestSchema);
      Action action = new Action(HttpRequestType.POST, operation, "/resources");

      // Act
      List<com.chargebee.openapi.parameter.Parameter> params = action.requestBodyParameters();

      // Assert
      assertThat(params).hasSize(3);
      assertThat(params).allMatch(p -> !p.isRequired, "All parameters should be optional");
    }

    @Test
    @DisplayName("Should return all fields as required when all are in schema's required list")
    void shouldMarkAllFieldsAsRequiredWhenAllInRequiredList() {
      // Arrange
      ObjectSchema requestSchema = new ObjectSchema();
      requestSchema.addProperty("field1", new StringSchema());
      requestSchema.addProperty("field2", new StringSchema());
      requestSchema.addProperty("field3", new StringSchema());
      
      // All fields are required
      requestSchema.setRequired(List.of("field1", "field2", "field3"));

      Operation operation = createPostOperation("resource", "create", requestSchema);
      Action action = new Action(HttpRequestType.POST, operation, "/resources");

      // Act
      List<com.chargebee.openapi.parameter.Parameter> params = action.requestBodyParameters();

      // Assert
      assertThat(params).hasSize(3);
      assertThat(params).allMatch(p -> p.isRequired, "All parameters should be required");
    }

    @Test
    @DisplayName("Should return empty list for GET requests")
    void shouldReturnEmptyListForGetRequests() {
      // Arrange
      Operation operation = new Operation();
      Map<String, Object> extensions = new HashMap<>();
      extensions.put("x-cb-operation-method-name", "list");
      operation.setExtensions(extensions);
      Action action = new Action(HttpRequestType.GET, operation, "/customers");

      // Act
      List<com.chargebee.openapi.parameter.Parameter> params = action.requestBodyParameters();

      // Assert
      assertThat(params).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when operation has no request body")
    void shouldReturnEmptyListWhenNoRequestBody() {
      // Arrange
      Operation operation = new Operation();
      Map<String, Object> extensions = new HashMap<>();
      extensions.put("x-cb-operation-method-name", "create");
      operation.setExtensions(extensions);
      Action action = new Action(HttpRequestType.POST, operation, "/customers");

      // Act
      List<com.chargebee.openapi.parameter.Parameter> params = action.requestBodyParameters();

      // Assert
      assertThat(params).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when request body schema is empty")
    void shouldReturnEmptyListWhenRequestBodySchemaIsEmpty() {
      // Arrange
      ObjectSchema requestSchema = new ObjectSchema();
      // No properties added

      Operation operation = createPostOperation("resource", "create", requestSchema);
      Action action = new Action(HttpRequestType.POST, operation, "/resources");

      // Act
      List<com.chargebee.openapi.parameter.Parameter> params = action.requestBodyParameters();

      // Assert
      assertThat(params).isEmpty();
    }

    @Test
    @DisplayName("Should handle complex types (nested objects, arrays)")
    void shouldHandleComplexTypes() {
      // Arrange
      ObjectSchema requestSchema = new ObjectSchema();
      
      // Simple field
      requestSchema.addProperty("id", new StringSchema());
      
      // Nested object
      ObjectSchema addressSchema = new ObjectSchema();
      addressSchema.addProperty("street", new StringSchema());
      addressSchema.addProperty("city", new StringSchema());
      requestSchema.addProperty("address", addressSchema);
      
      // Array field
      ArraySchema tagsSchema = new ArraySchema();
      tagsSchema.setItems(new StringSchema());
      requestSchema.addProperty("tags", tagsSchema);
      
      // Only "id" is required
      requestSchema.setRequired(List.of("id"));

      Operation operation = createPostOperation("customer", "create", requestSchema);
      Action action = new Action(HttpRequestType.POST, operation, "/customers");

      // Act
      List<com.chargebee.openapi.parameter.Parameter> params = action.requestBodyParameters();

      // Assert
      assertThat(params).hasSize(3);
      
      com.chargebee.openapi.parameter.Parameter idParam = findParameter(params, "id");
      com.chargebee.openapi.parameter.Parameter addressParam = findParameter(params, "address");
      com.chargebee.openapi.parameter.Parameter tagsParam = findParameter(params, "tags");

      assertThat(idParam.isRequired).isTrue();
      assertThat(addressParam.isRequired).isFalse();
      assertThat(tagsParam.isRequired).isFalse();
    }
  }

  // HELPER METHODS

  /**
   * Creates a POST operation with request body and required extensions.
   *
   * @param resourceId The resource identifier (e.g., "customer")
   * @param methodName The operation method name (e.g., "create")
   * @param requestSchema The request body schema
   * @return Configured Operation instance
   */
  private static Operation createPostOperation(
      String resourceId, String methodName, ObjectSchema requestSchema) {
    MediaType mediaType = new MediaType();
    mediaType.setSchema(requestSchema);

    Content content = new Content();
    content.addMediaType("application/x-www-form-urlencoded", mediaType);

    RequestBody requestBody = new RequestBody();
    requestBody.setContent(content);

    Operation operation = new Operation();
    Map<String, Object> extensions = new HashMap<>();
    extensions.put(RESOURCE_ID, resourceId);
    extensions.put(SDK_METHOD_NAME, methodName);
    extensions.put("x-cb-operation-method-name", methodName); // Required by Action constructor
    operation.setExtensions(extensions);
    operation.setRequestBody(requestBody);
    
    return operation;
  }

  /**
   * Finds a parameter by name in the list.
   *
   * @param params List of parameters
   * @param name Parameter name to find
   * @return The parameter
   * @throws AssertionError if parameter not found
   */
  private static com.chargebee.openapi.parameter.Parameter findParameter(
      List<com.chargebee.openapi.parameter.Parameter> params, String name) {
    return params.stream()
        .filter(p -> p.getName().equals(name))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("Parameter not found: " + name));
  }
}
