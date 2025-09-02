package com.chargebee;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * JSON Schema Upcaster that converts generic Schema objects
 * to their specific subclasses (ObjectSchema, ArraySchema, etc.) in OpenAPI specifications.
 *
 * <p>This class provides robust schema transformation with comprehensive error handling,
 * logging, and validation to ensure reliable processing of OpenAPI specifications.</p>
 *
 * @author Chargebee DX Team
 * @version 1.0
 * @since 1.0
 */
public class JsonSchemaUpcaster {

  private static final Logger LOGGER = Logger.getLogger(JsonSchemaUpcaster.class.getName());

  // Schema type constants
  private static final class SchemaTypes {
    static final String OBJECT = "object";
    static final String ARRAY = "array";
    static final String STRING = "string";
    static final String INTEGER = "integer";
    static final String NUMBER = "number";
    static final String BOOLEAN = "boolean";
    static final String NULL = "null";

    private SchemaTypes() {
      // Utility class - prevent instantiation
    }
  }

  // Reference path constants
  private static final class ReferencePaths {
    static final String COMPONENT_SEPARATOR = "/";
    static final int MIN_REF_PARTS = 1;

    private ReferencePaths() {
      // Utility class - prevent instantiation
    }
  }

  // Configuration for upcasting behavior
  private static final class UpcastingConfig {
    static final boolean PRESERVE_ORIGINAL_EXTENSIONS = true;
    static final int MAX_RECURSION_DEPTH = 100;

    private UpcastingConfig() {
      // Utility class - prevent instantiation
    }
  }

  private final OpenAPI openAPI;
  private final Set<Schema<?>> processedSchemas;
  private int recursionDepth;

  /**
   * Constructs a new JsonSchemaUpcaster for the given OpenAPI specification.
   *
   * @param openAPI the OpenAPI specification to process
   * @throws IllegalArgumentException if openAPI is null
   */
  public JsonSchemaUpcaster(OpenAPI openAPI) {
    this.openAPI = validateNotNull(openAPI, "OpenAPI specification cannot be null");
    this.processedSchemas = new HashSet<>();
    this.recursionDepth = 0;

    LOGGER.info("Initialized JsonSchemaUpcaster for OpenAPI specification");
  }

  /**
   * Entry point: Upcasts all schemas in OpenAPI spec to ObjectSchema, ArraySchema, etc.
   *
   * @throws JsonSchemaUpcastingException if upcasting fails
   */
  public void upcastAllSchemas() {
    try {
      LOGGER.info("Starting schema upcasting process");

      validateOpenAPIStructure();

      // Reset state for new upcasting operation
      processedSchemas.clear();
      recursionDepth = 0;

      // 1. Process path operations
      upcastPathSchemas();

      // 2. Process component schemas
      upcastComponentSchemas();

      LOGGER.info("Schema upcasting completed successfully");

    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Failed to upcast schemas", e);
      throw new JsonSchemaUpcastingException("Schema upcasting failed", e);
    }
  }

  /**
   * Validates the basic structure of the OpenAPI specification.
   *
   * @throws JsonSchemaUpcastingException if validation fails
   */
  private void validateOpenAPIStructure() {
    if (openAPI.getInfo() == null) {
      LOGGER.warning("OpenAPI specification missing info section");
    }

    if (openAPI.getPaths() == null
        && (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null)) {
      throw new JsonSchemaUpcastingException(
          "OpenAPI specification must contain either paths or component schemas");
    }
  }

  /**
   * Upcasts schemas in all path operations.
   */
  private void upcastPathSchemas() {
    if (openAPI.getPaths() == null) {
      LOGGER.fine("No paths found in OpenAPI specification");
      return;
    }

    LOGGER.fine("Processing path schemas");

    for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
      try {
        String pathName = pathEntry.getKey();
        PathItem pathItem = pathEntry.getValue();

        LOGGER.finest("Processing path: " + pathName);

        if (pathItem != null) {
          pathItem
              .readOperations()
              .forEach(operation -> processOperationSchemas(operation, pathName));
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Failed to process path: " + pathEntry.getKey(), e);
        // Continue processing other paths
      }
    }
  }

  /**
   * Upcasts component schemas.
   */
  private void upcastComponentSchemas() {
    if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
      LOGGER.fine("No component schemas found in OpenAPI specification");
      return;
    }

    LOGGER.fine("Processing component schemas");

    Map<String, Schema> originalSchemas = openAPI.getComponents().getSchemas();
    Map<String, Schema> upcastedSchemas = new LinkedHashMap<>();

    for (Map.Entry<String, Schema> schemaEntry : originalSchemas.entrySet()) {
      try {
        String schemaName = schemaEntry.getKey();
        Schema<?> originalSchema = schemaEntry.getValue();

        LOGGER.finest("Processing component schema: " + schemaName);

        Schema<?> upcastedSchema = upcastSchema(originalSchema);
        upcastedSchemas.put(schemaName, upcastedSchema);

      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Failed to process component schema: " + schemaEntry.getKey(), e);
        // Preserve original schema if upcasting fails
        upcastedSchemas.put(schemaEntry.getKey(), schemaEntry.getValue());
      }
    }

    openAPI.getComponents().setSchemas(upcastedSchemas);
  }

  /**
   * Processes schemas in a single operation.
   *
   * @param operation the operation to process
   * @param pathName the path name for logging context
   */
  private void processOperationSchemas(Operation operation, String pathName) {
    if (operation == null) {
      return;
    }

    try {
      // Process request body schemas
      processRequestBodySchemas(operation.getRequestBody());

      // Process response schemas
      processResponseSchemas(operation.getResponses());

      // Process parameter schemas
      processParameterSchemas(operation.getParameters());

    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Failed to process operation schemas for path: " + pathName, e);
    }
  }

  /**
   * Processes request body schemas.
   *
   * @param requestBody the request body to process
   */
  private void processRequestBodySchemas(RequestBody requestBody) {
    if (requestBody == null || requestBody.getContent() == null) {
      return;
    }

    for (Map.Entry<String, MediaType> contentEntry : requestBody.getContent().entrySet()) {
      try {
        MediaType mediaType = contentEntry.getValue();
        if (mediaType != null && mediaType.getSchema() != null) {
          mediaType.setSchema(upcastSchema(mediaType.getSchema()));
        }
      } catch (Exception e) {
        LOGGER.log(
            Level.WARNING,
            "Failed to process request body schema for media type: " + contentEntry.getKey(),
            e);
      }
    }
  }

  /**
   * Processes response schemas.
   *
   * @param responses the responses to process
   */
  private void processResponseSchemas(Map<String, ApiResponse> responses) {
    if (responses == null) {
      return;
    }

    for (Map.Entry<String, ApiResponse> responseEntry : responses.entrySet()) {
      try {
        ApiResponse response = responseEntry.getValue();
        if (response != null && response.getContent() != null) {
          processResponseContentSchemas(response.getContent());
        }
      } catch (Exception e) {
        LOGGER.log(
            Level.WARNING,
            "Failed to process response schemas for status: " + responseEntry.getKey(),
            e);
      }
    }
  }

  /**
   * Processes content schemas in a response.
   *
   * @param content the content map to process
   */
  private void processResponseContentSchemas(Map<String, MediaType> content) {
    for (Map.Entry<String, MediaType> contentEntry : content.entrySet()) {
      try {
        MediaType mediaType = contentEntry.getValue();
        if (mediaType != null && mediaType.getSchema() != null) {
          mediaType.setSchema(upcastSchema(mediaType.getSchema()));
        }
      } catch (Exception e) {
        LOGGER.log(
            Level.WARNING,
            "Failed to process response content schema for media type: " + contentEntry.getKey(),
            e);
      }
    }
  }

  /**
   * Processes parameter schemas.
   *
   * @param parameters the parameters to process
   */
  private void processParameterSchemas(List<Parameter> parameters) {
    if (parameters == null) {
      return;
    }

    for (int i = 0; i < parameters.size(); i++) {
      try {
        Parameter parameter = parameters.get(i);
        if (parameter != null) {
          Parameter upcastedParameter = upcastParameter(parameter);
          parameters.set(i, upcastedParameter);
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Failed to process parameter schema at index: " + i, e);
      }
    }
  }

  /**
   * Upcasts a Parameter to ensure its schema is properly subclassed.
   *
   * @param parameter the parameter to upcast
   * @return the upcasted parameter
   * @throws JsonSchemaUpcastingException if upcasting fails
   */
  public Parameter upcastParameter(Parameter parameter) {
    if (parameter == null) {
      return null;
    }

    try {
      if (parameter.getSchema() != null) {
        Schema<?> upcastedSchema = upcastSchema(parameter.getSchema());
        parameter.setSchema(upcastedSchema);
      } else {
        resolveParameterReference(parameter);
      }

      return parameter;

    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Failed to upcast parameter: " + parameter.getName(), e);
      return parameter; // Return original parameter if upcasting fails
    }
  }

  /**
   * Resolves parameter reference if schema is not directly available.
   *
   * @param parameter the parameter with potential reference
   */
  private void resolveParameterReference(Parameter parameter) {
    String ref = parameter.get$ref();
    if (ref == null) {
      return;
    }

    try {
      String schemaName = extractSchemaNameFromReference(ref);
      Parameter referencedParameter = resolveParameterReference(schemaName);

      if (referencedParameter != null) {
        copyParameterProperties(referencedParameter, parameter);
      }

    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Failed to resolve parameter reference: " + ref, e);
    }
  }

  /**
   * Extracts schema name from a reference string.
   *
   * @param ref the reference string
   * @return the schema name
   * @throws IllegalArgumentException if reference format is invalid
   */
  private String extractSchemaNameFromReference(String ref) {
    validateNotNull(ref, "Reference cannot be null");

    String[] refParts = ref.split(ReferencePaths.COMPONENT_SEPARATOR);
    if (refParts.length < ReferencePaths.MIN_REF_PARTS) {
      throw new IllegalArgumentException("Invalid reference format: " + ref);
    }

    return refParts[refParts.length - 1];
  }

  /**
   * Resolves a parameter by name from components.
   *
   * @param parameterName the parameter name to resolve
   * @return the resolved parameter, or null if not found
   */
  private Parameter resolveParameterReference(String parameterName) {
    if (openAPI.getComponents() == null || openAPI.getComponents().getParameters() == null) {
      return null;
    }

    return openAPI.getComponents().getParameters().get(parameterName);
  }

  /**
   * Copies properties from source parameter to target parameter.
   *
   * @param source the source parameter
   * @param target the target parameter
   */
  private void copyParameterProperties(Parameter source, Parameter target) {
    if (source.getSchema() != null) {
      target.setSchema(upcastSchema(source.getSchema()));
    }

    if (source.getIn() != null) target.setIn(source.getIn());
    if (source.getName() != null) target.setName(source.getName());

    target.setRequired(source.getRequired() != null && source.getRequired());
    target.setDeprecated(source.getDeprecated() != null && source.getDeprecated());
    target.setExplode(source.getExplode() != null && source.getExplode());

    if (source.getDescription() != null) target.setDescription(source.getDescription());
    if (source.getStyle() != null) target.setStyle(source.getStyle());
  }

  /**
   * Converts a Schema<?> into an ObjectSchema, ArraySchema, etc., recursively.
   *
   * @param schema the schema to upcast
   * @return the upcasted schema
   * @throws JsonSchemaUpcastingException if upcasting fails or recursion limit exceeded
   */
  public Schema<?> upcastSchema(Schema<?> schema) {
    if (schema == null) {
      return null;
    }

    // Prevent infinite recursion
    if (recursionDepth >= UpcastingConfig.MAX_RECURSION_DEPTH) {
      throw new JsonSchemaUpcastingException(
          "Maximum recursion depth exceeded: " + UpcastingConfig.MAX_RECURSION_DEPTH);
    }

    // Prevent circular references
    if (processedSchemas.contains(schema)) {
      LOGGER.finest("Circular reference detected, returning original schema");
      return schema;
    }

    try {
      recursionDepth++;
      processedSchemas.add(schema);

      return performSchemaUpcasting(schema);

    } finally {
      recursionDepth--;
      processedSchemas.remove(schema);
    }
  }

  /**
   * Performs the actual schema upcasting logic.
   *
   * @param schema the schema to upcast
   * @return the upcasted schema
   */
  private Schema<?> performSchemaUpcasting(Schema<?> schema) {
    // Handle already properly typed schemas
    if (isAlreadyUpcast(schema)) {
      return handleAlreadyUpcastSchema(schema);
    }

    // Handle reference schemas
    if (schema.get$ref() != null) {
      return schema; // References are handled separately
    }

    // Determine the resolved type
    String resolvedType = resolveType(schema);
    if (resolvedType == null) {
      LOGGER.fine("Schema has no type, treating as generic schema");
      return createGenericSchema(schema);
    }

    // Upcast based on resolved type
    return upcastByType(schema, resolvedType);
  }

  /**
   * Checks if a schema is already properly upcast.
   *
   * @param schema the schema to check
   * @return true if already upcast, false otherwise
   */
  private boolean isAlreadyUpcast(Schema<?> schema) {
    return schema instanceof ObjectSchema
        || schema instanceof ArraySchema
        || schema instanceof StringSchema
        || schema instanceof IntegerSchema
        || schema instanceof NumberSchema
        || schema instanceof BooleanSchema;
  }

  /**
   * Handles schemas that are already properly upcast by processing their children.
   *
   * @param schema the already upcast schema
   * @return the processed schema
   */
  private Schema<?> handleAlreadyUpcastSchema(Schema<?> schema) {
    if (schema instanceof ObjectSchema) {
      return processObjectSchemaProperties((ObjectSchema) schema);
    } else if (schema instanceof ArraySchema) {
      return processArraySchemaItems((ArraySchema) schema);
    }

    return schema; // Other types don't need child processing
  }

  /**
   * Processes properties of an ObjectSchema.
   *
   * @param objectSchema the object schema to process
   * @return the processed object schema
   */
  private ObjectSchema processObjectSchemaProperties(ObjectSchema objectSchema) {
    if (objectSchema.getProperties() != null) {
      Map<String, Schema> upcastedProperties = new LinkedHashMap<>();

      for (Map.Entry<String, Schema> propertyEntry : objectSchema.getProperties().entrySet()) {
        try {
          upcastedProperties.put(propertyEntry.getKey(), upcastSchema(propertyEntry.getValue()));
        } catch (Exception e) {
          LOGGER.log(Level.WARNING, "Failed to upcast property: " + propertyEntry.getKey(), e);
          upcastedProperties.put(propertyEntry.getKey(), propertyEntry.getValue());
        }
      }

      objectSchema.setProperties(upcastedProperties);
    }

    return objectSchema;
  }

  /**
   * Processes items of an ArraySchema.
   *
   * @param arraySchema the array schema to process
   * @return the processed array schema
   */
  private ArraySchema processArraySchemaItems(ArraySchema arraySchema) {
    if (arraySchema.getItems() != null) {
      arraySchema.setItems(upcastSchema(arraySchema.getItems()));
    }

    return arraySchema;
  }

  /**
   * Upcasts a schema based on its resolved type.
   *
   * @param schema the original schema
   * @param resolvedType the resolved type
   * @return the upcasted schema
   */
  private Schema<?> upcastByType(Schema<?> schema, String resolvedType) {
    switch (resolvedType) {
      case SchemaTypes.OBJECT:
        return createObjectSchema(schema);
      case SchemaTypes.ARRAY:
        return createArraySchema(schema);
      case SchemaTypes.STRING:
        return createStringSchema(schema);
      case SchemaTypes.INTEGER:
        return createIntegerOrNumberSchema(schema);
      case SchemaTypes.NUMBER:
        return createNumberSchema(schema);
      case SchemaTypes.BOOLEAN:
        return createBooleanSchema(schema);
      default:
        LOGGER.fine("Unknown schema type: " + resolvedType + ", creating generic schema");
        return createGenericSchema(schema);
    }
  }

  /**
   * Creates an ObjectSchema from a generic schema.
   *
   * @param originalSchema the original schema
   * @return the new ObjectSchema
   */
  private ObjectSchema createObjectSchema(Schema<?> originalSchema) {
    ObjectSchema objectSchema = new ObjectSchema();
    copyCommonFields(originalSchema, objectSchema);

    // Copy object-specific properties
    if (originalSchema.getProperties() != null) {
      Map<String, Schema> upcastedProperties = new LinkedHashMap<>();

      for (Map.Entry<String, Schema> propertyEntry : originalSchema.getProperties().entrySet()) {
        upcastedProperties.put(propertyEntry.getKey(), upcastSchema(propertyEntry.getValue()));
      }

      objectSchema.setProperties(upcastedProperties);
    }

    objectSchema.setRequired(originalSchema.getRequired());
    objectSchema.setAdditionalProperties(originalSchema.getAdditionalProperties());

    return objectSchema;
  }

  /**
   * Creates an ArraySchema from a generic schema.
   *
   * @param originalSchema the original schema
   * @return the new ArraySchema
   */
  private ArraySchema createArraySchema(Schema<?> originalSchema) {
    ArraySchema arraySchema = new ArraySchema();
    copyCommonFields(originalSchema, arraySchema);

    // Process array items
    if (originalSchema.getItems() != null) {
      arraySchema.setItems(upcastSchema(originalSchema.getItems()));
    }

    arraySchema.set$ref(originalSchema.get$ref());
    arraySchema.setName(originalSchema.getName());

    return arraySchema;
  }

  /**
   * Creates a StringSchema from a generic schema.
   *
   * @param originalSchema the original schema
   * @return the new StringSchema
   */
  private StringSchema createStringSchema(Schema<?> originalSchema) {
    StringSchema stringSchema = new StringSchema();
    copyCommonFields(originalSchema, stringSchema);

    // Copy string-specific properties
    stringSchema.setMinLength(originalSchema.getMinLength());
    stringSchema.setMaxLength(originalSchema.getMaxLength());
    stringSchema.setPattern(originalSchema.getPattern());
    stringSchema.setFormat(originalSchema.getFormat());

    // Safely cast enum values to List<String>
    if (originalSchema.getEnum() != null) {
      try {
        @SuppressWarnings("unchecked")
        List<String> enumValues = (List<String>) originalSchema.getEnum();
        stringSchema.setEnum(enumValues);
      } catch (ClassCastException e) {
        LOGGER.log(Level.WARNING, "Failed to cast enum values to String list", e);
      }
    }

    return stringSchema;
  }

  /**
   * Creates an IntegerSchema or NumberSchema based on constraints.
   *
   * @param originalSchema the original schema
   * @return the appropriate numeric schema
   */
  private Schema<?> createIntegerOrNumberSchema(Schema<?> originalSchema) {
    // Check if fractional minimum/maximum suggests this should be a number
    if (hasFractionalConstraints(originalSchema)) {
      LOGGER.fine("Integer schema has fractional constraints, creating NumberSchema instead");
      return createNumberSchema(originalSchema);
    }

    IntegerSchema integerSchema = new IntegerSchema();
    copyCommonFields(originalSchema, integerSchema);
    copyNumericFields(originalSchema, integerSchema);

    return integerSchema;
  }

  /**
   * Creates a NumberSchema from a generic schema.
   *
   * @param originalSchema the original schema
   * @return the new NumberSchema
   */
  private NumberSchema createNumberSchema(Schema<?> originalSchema) {
    NumberSchema numberSchema = new NumberSchema();
    copyCommonFields(originalSchema, numberSchema);
    copyNumericFields(originalSchema, numberSchema);

    return numberSchema;
  }

  /**
   * Creates a BooleanSchema from a generic schema.
   *
   * @param originalSchema the original schema
   * @return the new BooleanSchema
   */
  private BooleanSchema createBooleanSchema(Schema<?> originalSchema) {
    BooleanSchema booleanSchema = new BooleanSchema();
    copyCommonFields(originalSchema, booleanSchema);

    return booleanSchema;
  }

  /**
   * Creates a generic Schema as fallback.
   *
   * @param originalSchema the original schema
   * @return the new generic Schema
   */
  private Schema<?> createGenericSchema(Schema<?> originalSchema) {
    Schema<?> genericSchema = new Schema<>();
    copyCommonFields(originalSchema, genericSchema);

    genericSchema.setType(originalSchema.getType());
    genericSchema.setFormat(originalSchema.getFormat());

    return genericSchema;
  }

  /**
   * Checks if a schema has fractional minimum or maximum constraints.
   *
   * @param schema the schema to check
   * @return true if fractional constraints exist
   */
  private boolean hasFractionalConstraints(Schema<?> schema) {
    return (schema.getMinimum() != null && schema.getMinimum().scale() > 0)
        || (schema.getMaximum() != null && schema.getMaximum().scale() > 0);
  }

  /**
   * Copies numeric-specific fields between schemas.
   *
   * @param from the source schema
   * @param to the target schema
   */
  private void copyNumericFields(Schema<?> from, Schema<?> to) {
    if (to instanceof IntegerSchema) {
      IntegerSchema intSchema = (IntegerSchema) to;
      intSchema.setMinimum(from.getMinimum());
      intSchema.setMaximum(from.getMaximum());
      intSchema.setExclusiveMinimum(from.getExclusiveMinimum());
      intSchema.setExclusiveMaximum(from.getExclusiveMaximum());
      intSchema.setMultipleOf(from.getMultipleOf());
      intSchema.setFormat(from.getFormat());
    } else if (to instanceof NumberSchema) {
      NumberSchema numSchema = (NumberSchema) to;
      numSchema.setMinimum(from.getMinimum());
      numSchema.setMaximum(from.getMaximum());
      numSchema.setExclusiveMinimum(from.getExclusiveMinimum());
      numSchema.setExclusiveMaximum(from.getExclusiveMaximum());
      numSchema.setMultipleOf(from.getMultipleOf());
      numSchema.setFormat(from.getFormat());
    }
  }

  /**
   * Handles both OpenAPI 3.0 (getType()) and OpenAPI 3.1 (getTypes()) type resolution.
   *
   * @param schema the schema to resolve type for
   * @return the resolved type, or null if no type found
   */
  public String resolveType(Schema<?> schema) {
    if (schema == null) {
      return null;
    }

    // OpenAPI 3.1 multi-type support - find first non-null type
    if (schema.getTypes() != null && !schema.getTypes().isEmpty()) {
      for (String type : schema.getTypes()) {
        if (type != null && !SchemaTypes.NULL.equals(type)) {
          return type;
        }
      }
    }

    // Fallback to OpenAPI 3.0 single type
    return schema.getType();
  }

  /**
   * Copies common fields between schemas and recursively converts composed types.
   *
   * @param from the source schema
   * @param to the target schema
   */
  private void copyCommonFields(Schema<?> from, Schema<?> to) {
    // Basic metadata
    to.setTitle(from.getTitle());
    to.setDescription(from.getDescription());
    to.setNullable(resolveNullable(from));
    to.setDeprecated(from.getDeprecated());
    to.setReadOnly(from.getReadOnly());
    to.setWriteOnly(from.getWriteOnly());
    to.setExample(from.getExample());
    to.setDefault(from.getDefault());

    // Extensions (if configured to preserve)
    if (UpcastingConfig.PRESERVE_ORIGINAL_EXTENSIONS) {
      to.setExtensions(from.getExtensions());
    }

    to.setExternalDocs(from.getExternalDocs());

    // Handle composed schemas with error handling
    copyComposedSchemas(from, to);

    // Discriminator
    if (from.getDiscriminator() != null) {
      to.setDiscriminator(from.getDiscriminator());
    }
  }

  /**
   * Copies composed schema fields (allOf, anyOf, oneOf, not).
   *
   * @param from the source schema
   * @param to the target schema
   */
  private void copyComposedSchemas(Schema<?> from, Schema<?> to) {
    try {
      if (from.getAllOf() != null) {
        to.setAllOf(from.getAllOf().stream().map(this::upcastSchema).collect(Collectors.toList()));
      }

      if (from.getAnyOf() != null) {
        to.setAnyOf(from.getAnyOf().stream().map(this::upcastSchema).collect(Collectors.toList()));
      }

      if (from.getOneOf() != null) {
        to.setOneOf(from.getOneOf().stream().map(this::upcastSchema).collect(Collectors.toList()));
      }

      if (from.getNot() != null) {
        to.setNot(upcastSchema(from.getNot()));
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Failed to copy composed schemas", e);
      // Continue without composed schemas rather than failing entirely
    }
  }

  /**
   * Resolves nullable property from either getNullable() or type array containing "null".
   *
   * @param schema the schema to check
   * @return the nullable value, or null if not specified
   */
  private Boolean resolveNullable(Schema<?> schema) {
    // Explicit nullable property takes precedence
    if (schema.getNullable() != null) {
      return schema.getNullable();
    }

    // Check for "null" in types array (OpenAPI 3.1)
    if (schema.getTypes() != null && schema.getTypes().contains(SchemaTypes.NULL)) {
      return true;
    }

    return null; // Not specified
  }

  /**
   * Validates that an object is not null.
   *
   * @param object the object to validate
   * @param message the error message if validation fails
   * @param <T> the type of the object
   * @return the validated object
   * @throws IllegalArgumentException if object is null
   */
  private static <T> T validateNotNull(T object, String message) {
    if (object == null) {
      throw new IllegalArgumentException(message);
    }
    return object;
  }

  /**
   * Custom exception for schema upcasting failures.
   */
  public static class JsonSchemaUpcastingException extends RuntimeException {

    public JsonSchemaUpcastingException(String message) {
      super(message);
    }

    public JsonSchemaUpcastingException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
