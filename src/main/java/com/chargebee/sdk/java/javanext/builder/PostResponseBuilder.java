package com.chargebee.sdk.java.javanext.builder;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.javanext.JavaFormatter;
import com.chargebee.sdk.java.javanext.core.Field;
import com.chargebee.sdk.java.javanext.core.TypeMapper;
import com.chargebee.sdk.java.javanext.datatype.FieldType;
import com.chargebee.sdk.java.javanext.datatype.ObjectType;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Builds Java response classes for POST operations using the provided OpenAPI
 * specification and Handlebars template. This builder is responsible for:
 * - Discovering POST operations
 * - Resolving the success response (200/202/204)
 * - Extracting fields and imports from the response schema
 * - Rendering content and registering file operations for writing
 */
public class PostResponseBuilder {

  // ---------------------------------------------------------------------------
  // Constants & Logger
  // ---------------------------------------------------------------------------

  private static final Logger LOG = Logger.getLogger(PostResponseBuilder.class.getName());

  private static final String JSON_MIME = "application/json";
  private static final String CODE_200 = "200";
  private static final String CODE_202 = "202";
  private static final String CODE_204 = "204";
  private static final String MODELS_DIR_SUFFIX = "/com/chargebee/v4/models";

  private Template template;
  private Template baseResponseTemplate;
  private String outputDirectoryPath;
  private OpenAPI openApi;

  private final List<FileOp> fileOps = new ArrayList<>();

  /**
   * Sets the output directory and creates the responses directory structure.
   *
   * @param outputDirectoryPath root path where responses will be generated
   * @return this builder instance
   * @throws IllegalArgumentException if the provided path is null or blank
   */
  public PostResponseBuilder withOutputDirectoryPath(String outputDirectoryPath) {
    if (outputDirectoryPath == null || outputDirectoryPath.trim().isEmpty()) {
      throw new IllegalArgumentException("outputDirectoryPath must not be null or blank");
    }
    this.outputDirectoryPath = outputDirectoryPath + MODELS_DIR_SUFFIX;
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  /**
   * Sets the Handlebars template used to render response classes.
   *
   * @param template non-null template instance
   * @return this builder instance
   * @throws NullPointerException if template is null
   */
  public PostResponseBuilder withTemplate(Template template) {
    this.template = Objects.requireNonNull(template, "template");
    return this;
  }

  /**
   * Sets the Handlebars template used to render the BaseResponse class.
   *
   * @param baseResponseTemplate non-null template instance
   * @return this builder instance
   */
  public PostResponseBuilder withBaseResponseTemplate(Template baseResponseTemplate) {
    this.baseResponseTemplate = baseResponseTemplate;
    return this;
  }

  /**
   * Generates file operations for all POST responses found in the given OpenAPI
   * specification.
   *
   * @param openApi non-null OpenAPI specification
   * @return ordered list of file operations to be executed by the caller
   * @throws IllegalStateException if required configuration is missing
   */
  public List<FileOp> build(OpenAPI openApi) {
    this.openApi = Objects.requireNonNull(openApi, "openApi");
    MethodNameDeriver.initialize(openApi);
    if (this.template == null) {
      throw new IllegalStateException("Template not set. Call withTemplate(...) before build().");
    }
    if (this.outputDirectoryPath == null || this.outputDirectoryPath.isEmpty()) {
      throw new IllegalStateException(
          "Output directory not set. Call withOutputDirectoryPath(...) before build().");
    }
    // Generate BaseResponse class if template is provided
    if (this.baseResponseTemplate != null) {
      generateBaseResponse();
    }
    generateResponses();
    return fileOps;
  }

  /**
   * Generates the BaseResponse abstract class that all response classes extend.
   */
  private void generateBaseResponse() {
    try {
      String content = baseResponseTemplate.apply(null);
      String formattedContent = JavaFormatter.formatSafely(content);
      fileOps.add(
          new FileOp.WriteString(this.outputDirectoryPath, "BaseResponse.java", formattedContent));
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Failed to generate BaseResponse class", e);
    }
  }

  /**
   * Iterates all operations, renders response classes for POST endpoints, and
   * registers file write operations.
   */
  private void generateResponses() {
    try {
      var operations = getOperations();
      for (var entry : operations.entrySet()) {
        String path = entry.getKey();
        PathItem pathItem = entry.getValue();
        if (pathItem == null || pathItem.getPost() == null) continue;

        var operation = pathItem.getPost();
        var successResponse = findSuccessResponse(operation);
        if (successResponse == null) continue;

        var responseAction = createResponseAction(path, operation, successResponse);
        if (responseAction == null) continue;

        var formattedContent = applyTemplate(responseAction);
        writeResponse(responseAction, formattedContent);
      }
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Error generating POST responses", e);
    }
  }

  /**
   * Creates the rendering model for a POST response based on the operation and
   * selected success response.
   *
   * @return model for templating, or null when schema is not an object
   */
  private PostResponse createResponseAction(
      String path, Operation operation, ApiResponse response) {
    var postResponse = new PostResponse();

    String moduleName = getExtensionOrNull(operation, Extension.RESOURCE_ID);
    if (moduleName == null) {
      LOG.log(
          Level.WARNING,
          "Skipping operation due to missing RESOURCE_ID extension: {0}",
          operation.getOperationId());
      return null;
    }

    // Derive method name from path instead of using x-cb-operation-method-name extension
    String methodName = MethodNameDeriver.deriveMethodName(path, "POST", operation);
    methodName = MethodNameDeriver.applyBatchPrefix(path, methodName);

    postResponse.setOperationId(methodName);
    postResponse.setModule(moduleName);

    var content = response.getContent();
    if (content == null || content.get(JSON_MIME) == null) {
      // 204 No Content or non-JSON: produce empty class to keep API surface consistent
      postResponse.setFields(new ArrayList<>());
      postResponse.setImports(new ArrayList<>());
      return postResponse;
    }

    var jsonContent = content.get(JSON_MIME);
    if (jsonContent.getSchema() == null) {
      // No schema defined - create empty response class
      postResponse.setFields(new ArrayList<>());
      postResponse.setImports(new ArrayList<>());
      return postResponse;
    }

    var schema = jsonContent.getSchema();
    if (!isObjectSchema(schema)) return null;

    if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
      postResponse.setFields(getResponseFields(schema));
      postResponse.setImports(getImports(schema));
    } else {
      // Create empty response class for schemas without properties
      postResponse.setFields(new ArrayList<>());
      postResponse.setImports(new ArrayList<>());
    }

    return postResponse;
  }

  /**
   * Collects import types required by the response model based on its schema.
   */
  private List<Imports> getImports(Schema<?> schema) {
    Set<String> importNames = new HashSet<>();
    if (schema != null && schema.getProperties() != null) {
      for (var key : schema.getProperties().keySet()) {
        var propertySchema = (Schema<?>) schema.getProperties().get(key);
        collectImportsFromSchema(propertySchema, importNames);
      }
    }
    var imports = new ArrayList<Imports>();
    for (String name : importNames) {
      Imports importObj = new Imports();
      importObj.setName(name);
      imports.add(importObj);
    }
    return imports;
  }

  /**
   * Recursively walks the provided schema to collect referenced types for imports
   * (handles $ref, arrays, composed schemas, object properties, and maps).
   */
  private void collectImportsFromSchema(Schema<?> schema, Set<String> importNames) {
    if (schema == null) return;
    if (schema.get$ref() != null) {
      importNames.add(extractTypeFromRef(schema.get$ref()));
      return;
    }
    // Arrays (explicit type or ArraySchema instance)
    if ("array".equals(schema.getType()) || schema instanceof ArraySchema) {
      Schema<?> items =
          schema instanceof ArraySchema ? ((ArraySchema) schema).getItems() : schema.getItems();
      collectImportsFromSchema(items, importNames);
      return;
    }
    // Composed schemas (oneOf/anyOf/allOf)
    if (schema instanceof ComposedSchema) {
      ComposedSchema composed = (ComposedSchema) schema;
      if (composed.getAllOf() != null) {
        for (Schema<?> s : composed.getAllOf()) collectImportsFromSchema(s, importNames);
      }
      if (composed.getAnyOf() != null) {
        for (Schema<?> s : composed.getAnyOf()) collectImportsFromSchema(s, importNames);
      }
      if (composed.getOneOf() != null) {
        for (Schema<?> s : composed.getOneOf()) collectImportsFromSchema(s, importNames);
      }
    }
    // Object properties
    if ("object".equals(schema.getType()) && schema.getProperties() != null) {
      for (var entry : schema.getProperties().entrySet()) {
        collectImportsFromSchema((Schema<?>) entry.getValue(), importNames);
      }
    }
    // additionalProperties (map types)
    Object additional = schema.getAdditionalProperties();
    if (additional instanceof Schema) {
      collectImportsFromSchema((Schema<?>) additional, importNames);
    }
  }

  /**
   * Translates top-level schema properties into response fields for templating.
   */
  @SuppressWarnings("unchecked")
  private List<Field> getResponseFields(Schema<?> schema) {
    var fields = new ArrayList<Field>();
    if (schema == null || schema.getProperties() == null) return fields;
    for (var key : schema.getProperties().keySet()) {
      var fieldName = key.toString();
      var propertySchemaRaw = schema.getProperties().get(key);
      var propertySchema = (Schema<Object>) propertySchemaRaw;
      var field = new Field();
      field.setName(fieldName);
      if (propertySchema.get$ref() != null) {
        field.setType(createRefFieldType(propertySchema.get$ref()));
      } else {
        FieldType fieldType = unwrapArrayIfNeeded(fieldName, propertySchema);
        field.setType(fieldType);
      }
      fields.add(field);
    }
    return fields;
  }

  /**
   * Unwraps array fields that contain single-property wrapper objects with a $ref.
   * Example: array of {entitlement_override: $ref} becomes List<EntitlementOverride>
   */
  private FieldType unwrapArrayIfNeeded(String fieldName, Schema<?> propertySchema) {
    // Check if this is an array
    if (!"array".equals(propertySchema.getType()) && !(propertySchema instanceof ArraySchema)) {
      return TypeMapper.getJavaType(fieldName, propertySchema);
    }

    Schema<?> items =
        propertySchema instanceof ArraySchema
            ? ((ArraySchema) propertySchema).getItems()
            : propertySchema.getItems();

    // Check if items is an object with a single property that's a $ref
    if (items != null
        && "object".equals(items.getType())
        && items.getProperties() != null
        && items.getProperties().size() == 1) {

      Object singleProp = items.getProperties().values().iterator().next();
      if (singleProp instanceof Schema) {
        Schema<?> propSchema = (Schema<?>) singleProp;
        if (propSchema.get$ref() != null && !propSchema.get$ref().isEmpty()) {
          // Extract the type name from the $ref and return List<Type>
          String refName = extractTypeFromRef(propSchema.get$ref());
          return new com.chargebee.sdk.java.javanext.datatype.ListType(
              CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, refName),
              createArraySchemaWithRef(propSchema.get$ref()));
        }
      }
    }

    // No unwrapping needed, use normal type mapping
    return TypeMapper.getJavaType(fieldName, propertySchema);
  }

  /**
   * Creates an ArraySchema with a direct $ref to the given type (for unwrapped arrays).
   */
  private ArraySchema createArraySchemaWithRef(String ref) {
    ArraySchema arraySchema = new ArraySchema();
    Schema<?> refSchema = new Schema<>();
    refSchema.set$ref(ref);
    arraySchema.setItems(refSchema);
    return arraySchema;
  }

  /**
   * Creates a field type for a referenced schema (ObjectType using snake_case
   * name derived from the ref value).
   */
  private FieldType createRefFieldType(String ref) {
    String typeName = extractTypeFromRef(ref);
    return new ObjectType(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, typeName), null);
  }

  /**
   * Extracts the simple type name from an OpenAPI $ref value.
   * Example: "#/components/schemas/Customer" -> "Customer"
   */
  private String extractTypeFromRef(String ref) {
    return ref.substring(ref.lastIndexOf('/') + 1);
  }

  /**
   * Returns all path items from the OpenAPI spec or an empty map if none
   * present.
   */
  private Map<String, PathItem> getOperations() {
    var paths = openApi != null ? openApi.getPaths() : null;
    if (paths == null || paths.isEmpty()) return Collections.emptyMap();
    // Copy into a regular map to avoid mutating the underlying OpenAPI structure
    return paths.entrySet().stream()
        .collect(
            Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, HashMap::new));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Selects the first available success response among 200, 202, or 204. */
  private ApiResponse findSuccessResponse(Operation operation) {
    if (operation == null || operation.getResponses() == null) return null;
    var responses = operation.getResponses();
    ApiResponse r200 = responses.get(CODE_200);
    if (r200 != null) return r200;
    ApiResponse r202 = responses.get(CODE_202);
    if (r202 != null) return r202;
    return responses.get(CODE_204);
  }

  /** Applies the template and formats the generated content safely. */
  private String applyTemplate(PostResponse responseAction) throws IOException {
    var content = template.apply(responseAction);
    return JavaFormatter.formatSafely(content);
  }

  /** Registers file operations to write the rendered response class. */
  private void writeResponse(PostResponse responseAction, String formattedContent) {
    var moduleDir = this.outputDirectoryPath + "/" + responseAction.getModule();
    var responsesDir = moduleDir + "/responses";
    fileOps.add(new FileOp.CreateDirectory(moduleDir, ""));
    fileOps.add(new FileOp.CreateDirectory(responsesDir, ""));
    fileOps.add(
        new FileOp.WriteString(
            responsesDir, responseAction.getName() + "Response.java", formattedContent));
  }

  /** Returns true if the schema is an object schema. */
  private static boolean isObjectSchema(Schema<?> schema) {
    return schema != null && "object".equals(schema.getType());
  }

  /** Returns the string value of a custom OpenAPI extension or null. */
  private static String getExtensionOrNull(Operation operation, String key) {
    if (operation == null || operation.getExtensions() == null) return null;
    var value = operation.getExtensions().get(key);
    return value != null ? value.toString() : null;
  }

  @lombok.Data
  private static class Imports {
    private String name;

    @SuppressWarnings("unused")
    public String getModuleName() {
      return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, name);
    }
  }

  @lombok.Data
  private static class PostResponse {
    private String operationId;
    private List<Imports> imports;
    private String module;
    private List<Field> fields;

    public String getName() {
      var operationIdSnake =
          CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, getOperationId());
      // Normalize module to snake_case to preserve word boundaries (handles lowerCamel inputs)
      var moduleSnake =
          module != null && module.contains("_")
              ? module
              : CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, module);

      // If operationId contains the module name (or its singular/plural variations), don't prefix
      // it
      var moduleBase = moduleSnake.replaceAll("_", "");
      var operationBase = operationIdSnake.replaceAll("_", "");
      if (operationIdSnake.contains(moduleSnake)
          || operationBase.contains(moduleBase)
          || moduleBase.contains(operationBase)) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, operationIdSnake);
      }

      var actionName = moduleSnake + "_" + operationIdSnake;
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, actionName);
    }

    public String getModule() {
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, module);
    }
  }
}
