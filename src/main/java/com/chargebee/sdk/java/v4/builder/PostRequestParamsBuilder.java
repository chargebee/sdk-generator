package com.chargebee.sdk.java.v4.builder;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.v4.JavaFormatter;
import com.chargebee.sdk.java.v4.core.EnumFields;
import com.chargebee.sdk.java.v4.core.Field;
import com.chargebee.sdk.java.v4.core.Model;
import com.chargebee.sdk.java.v4.core.TypeMapper;
import com.chargebee.sdk.java.v4.datatype.FieldType;
import com.chargebee.sdk.java.v4.datatype.ListType;
import com.chargebee.sdk.java.v4.datatype.ObjectType;
import com.chargebee.sdk.java.v4.util.CaseFormatUtil;
import com.chargebee.sdk.java.v4.util.SchemaUtil;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds Java params models for POST operations from an OpenAPI spec using a Handlebars template.
 *
 * <p>Responsibilities:
 * - Orchestrate discovery of POST operations
 * - Extract operation/module identifiers reliably
 * - Traverse request schema to collect fields, enums, and sub-models
 * - Resolve naming collisions across sub-models
 * - Render generated models to files
 */
public class PostRequestParamsBuilder {

  private Template template;
  private String outputDirectoryPath;
  private OpenAPI openApi;

  private final List<FileOp> fileOps = new ArrayList<>();

  public PostRequestParamsBuilder withOutputDirectoryPath(String outputDirectoryPath) {
    this.outputDirectoryPath = outputDirectoryPath + "/com/chargebee/v4/models";
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  public PostRequestParamsBuilder withTemplate(Template template) {
    this.template = template;
    return this;
  }

  /**
   * Generate all POST request param models for the provided OpenAPI document.
   */
  public List<FileOp> build(OpenAPI openApi) {
    this.openApi = openApi;
    generateParams();
    return fileOps;
  }

  // =========================================================
  // Orchestration
  // =========================================================

  /**
   * Iterates over API paths, extracts POST operations, prepares a {@code PostAction} and writes
   * corresponding params model files.
   */
  private void generateParams() {
    try {
      var operations = getOperations();
      for (var entry : operations.entrySet()) {
        PathItem pathItem = entry.getValue();
        if (pathItem.getPost() != null) {
          var operation = pathItem.getPost();

          // Skip true batch operations (those with batch-operation-path-id) -
          // they use BatchRequest infrastructure, not individual params classes
          if (isTrueBatchOperation(operation)) {
            continue;
          }

          var postAction = new PostAction();
          String module = resolveModuleName(entry.getKey(), operation);

          // Read method name from SDK extension (populated by cb-openapi-generator)
          String opId = readExtensionAsString(operation, Extension.SDK_METHOD_NAME);
          postAction.setOperationId(opId);
          postAction.setModule(module);
          postAction.setPath(entry.getKey());

          // Set JSON input flag from x-cb-is-operation-needs-json-input extension
          Object needsJsonInput = operation.getExtensions() != null
              ? operation.getExtensions().get(Extension.IS_OPERATION_NEEDS_JSON_INPUT)
              : null;
          postAction.setOperationNeedsJsonInput(
              needsJsonInput != null && (boolean) needsJsonInput);

          Schema<?> requestSchema = resolveRequestSchema(operation);
          if (requestSchema != null) {
            postAction.setFields(getFields(requestSchema, null));
            postAction.setEnumFields(getEnumFields(requestSchema));
            var subModels = getSubModels(requestSchema, null);
            // Conditionally prefix only duplicate sub-model names
            dedupeAndPrefixSubModels(postAction.getModule(), subModels, postAction.getFields());
            postAction.setSubModels(subModels);
            postAction.setCustomFieldsSupported(SchemaUtil.isCustomFieldsSupported(requestSchema));
            postAction.setConsentFieldsSupported(
                SchemaUtil.isConsentFieldsSupported(requestSchema));
          } else {
            // No usable request schema - generate empty params class
            postAction.setFields(new ArrayList<>());
            postAction.setEnumFields(new ArrayList<>());
            postAction.setSubModels(new ArrayList<>());
            postAction.setCustomFieldsSupported(false);
            postAction.setConsentFieldsSupported(false);
          }

          var content = template.apply(postAction);
          var formattedContent = JavaFormatter.formatSafely(content);
          fileOps.add(
              new FileOp.CreateDirectory(
                  this.outputDirectoryPath + "/" + postAction.getModule(), "params"));

          fileOps.add(
              new FileOp.WriteString(
                  this.outputDirectoryPath + "/" + postAction.getModule() + "/params",
                  postAction.getName() + "Params.java",
                  formattedContent));
        }
      }
    } catch (IOException e) {
      System.err.println("Error generating params: " + e.getMessage());
    }
  }

  // =========================================================
  // Extension helpers
  // =========================================================

  /** Returns true if the operation is a true batch operation (has batch-operation-path-id). */
  private static boolean isTrueBatchOperation(Operation operation) {
    if (operation == null || operation.getExtensions() == null) return false;
    return operation.getExtensions().containsKey(Extension.BATCH_OPERATION_PATH_ID)
        && operation.getExtensions().get(Extension.BATCH_OPERATION_PATH_ID) != null;
  }

  /** Returns the string value of a custom OpenAPI extension or null. */
  private static String readExtensionAsString(Operation operation, String key) {
    if (operation == null || operation.getExtensions() == null) return null;
    var value = operation.getExtensions().get(key);
    return value != null ? value.toString() : null;
  }

  // =========================================================
  // Schema traversal helpers
  // =========================================================

  /**
   * Recursively collect sub-models for nested object and array-of-object fields.
   */
  private List<Model> getSubModels(Schema<?> schema, String parentPath) {
    var subModels = new ArrayList<Model>();
    Map<String, Schema<?>> properties = safeProperties(schema);
    if (properties == null) {
      return subModels;
    }
    for (var fieldName : properties.keySet()) {
      Schema<?> schemaDefn = properties.get(fieldName);
      FieldType fieldType = TypeMapper.getJavaType(fieldName.toString(), schemaDefn);
      if (fieldType instanceof ObjectType) {
        Map<String, Schema<?>> nestedProps = safeProperties(schemaDefn);
        if (nestedProps == null || nestedProps.isEmpty()) {
          continue;
        }
        if (isDirectFilterParameter(schemaDefn)) {
          continue;
        }
        String fullPath =
            (parentPath == null || parentPath.isBlank())
                ? fieldName.toString()
                : parentPath + "_" + fieldName;
        subModels.add(createSubModel(fieldName.toString(), fullPath, schemaDefn));
        subModels.addAll(getSubModels(schemaDefn, fullPath));
      } else if (fieldType instanceof ListType) {
        Schema<?> listSchema = (Schema<?>) schemaDefn.getItems();
        Map<String, Schema<?>> listProps = safeProperties(listSchema);
        if (listSchema == null || listProps == null || listProps.isEmpty()) {
          continue;
        }
        String fullPath =
            (parentPath == null || parentPath.isBlank())
                ? fieldName.toString()
                : parentPath + "_" + fieldName;
        subModels.add(createSubModel(fieldName.toString(), fullPath, listSchema));
        subModels.addAll(getSubModels(listSchema, fullPath));
      }
    }
    return subModels;
  }

  /**
   * Create a sub-model with the given name/path from the provided schema.
   */
  private Model createSubModel(String simpleNameSnake, String fullPathSnake, Schema<?> schema) {
    var subModel = new Model();
    // packageName holds full path for disambiguation; name holds the simple leaf name
    subModel.setPackageName(fullPathSnake);
    subModel.setName(simpleNameSnake);

    // For composite array patterns, generate fields with single values instead of lists
    boolean isCompositeArray = isCompositeArrayPattern(schema);
    subModel.setFields(getFields(schema, fullPathSnake, isCompositeArray));
    subModel.setEnumFields(getEnumFields(schema, isCompositeArray));
    subModel.setSubModels(getSubModels(schema, fullPathSnake));

    // Check for custom fields and consent fields support at sub-params level
    subModel.setCustomFieldsSupported(SchemaUtil.isCustomFieldsSupported(schema));
    subModel.setConsentFieldsSupported(SchemaUtil.isConsentFieldsSupported(schema));

    return subModel;
  }

  /** Collect enum fields defined directly under the given schema. */
  private List<EnumFields> getEnumFields(Schema<?> schema) {
    return getEnumFields(schema, false);
  }

  /** Collect enum fields with option to handle composite array patterns. */
  private List<EnumFields> getEnumFields(Schema<?> schema, boolean isCompositeArraySubModel) {
    var enumFields = new ArrayList<EnumFields>();
    Map<String, Schema<?>> properties = safeProperties(schema);
    if (properties == null) {
      return enumFields;
    }
    for (var fieldName : properties.keySet()) {
      Schema<?> schemaDefn = properties.get(fieldName);

      if (isDirectFilterParameter(schemaDefn)) {
        continue;
      }

      Schema<?> enumSchema = schemaDefn;

      // For composite array sub-models, check if this is an array with enum items
      if (isCompositeArraySubModel
          && "array".equals(schemaDefn.getType())
          && schemaDefn.getItems() != null) {
        enumSchema = schemaDefn.getItems();
      }

      if (enumSchema.getEnum() != null) {
        var enumField = new EnumFields();
        enumField.setName(fieldName.toString());
        java.util.List<String> enumStrings =
            ((java.util.List<?>) enumSchema.getEnum())
                .stream().map(String::valueOf).collect(java.util.stream.Collectors.toList());
        enumField.setEnums(enumStrings);
        enumFields.add(enumField);
      }
    }
    return enumFields;
  }

  /** Collect immediate field definitions for the given schema. */
  private List<Field> getFields(Schema<?> schema, String parentPath) {
    return getFields(schema, parentPath, false);
  }

  /** Collect immediate field definitions for the given schema, with option to handle composite arrays. */
  private List<Field> getFields(
      Schema<?> schema, String parentPath, boolean isCompositeArraySubModel) {
    var fields = new ArrayList<Field>();
    Map<String, Schema<?>> parameters = safeProperties(schema);
    if (parameters == null) return fields;
    for (var entry : parameters.entrySet()) {
      var field = new Field();
      field.setName(entry.getKey());

      // For composite array sub-models, convert array types to their element types
      Schema<?> fieldSchema = entry.getValue();
      if (isCompositeArraySubModel && "array".equals(fieldSchema.getType())) {
        // Get the element type instead of the array type
        Schema<?> itemSchema = fieldSchema.getItems();
        field.setType(TypeMapper.getJavaType(entry.getKey(), itemSchema));
      } else {
        field.setType(TypeMapper.getJavaType(entry.getKey(), fieldSchema));
      }

      field.setDeprecated(
          entry.getValue().getDeprecated() != null && entry.getValue().getDeprecated());

      Schema<?> schemaDefn = entry.getValue();
      FieldType fieldType = TypeMapper.getJavaType(entry.getKey(), schemaDefn);

      // Check if this is a composite array pattern
      if (isCompositeArrayPattern(schemaDefn)) {
        field.setCompositeArrayField(true);
        field.setSubModelField(true); // Still create the sub-model for the array structure
        String fullPath =
            (parentPath == null || parentPath.isBlank())
                ? entry.getKey()
                : parentPath + "_" + entry.getKey();
        var subModelRef = new Model();
        subModelRef.setPackageName(fullPath);
        subModelRef.setName(entry.getKey());
        field.setSubModel(subModelRef);
      } else if (isDirectFilterParameter(schemaDefn)) {
        field.setFilter(true);
        String filterTypeName = CaseFormatUtil.toUpperCamelSafe(entry.getKey()) + "Filter";
        field.setFilterType(filterTypeName);
        field.setSupportedOperations(getSupportedOperations(schemaDefn));
        field.setFilterSdkName(getFilterSdkName(schemaDefn));
        List<String> enumValues = getFilterEnumValues(schemaDefn);
        if (enumValues != null && !enumValues.isEmpty()) {
          field.setFilterEnumValues(enumValues);
        }
      } else if (fieldType instanceof ObjectType
          && safeProperties(schemaDefn) != null
          && !safeProperties(schemaDefn).isEmpty()) {
        // Regular complex object with properties, mark as subModelField and attach a model
        field.setSubModelField(true);
        String fullPath =
            (parentPath == null || parentPath.isBlank())
                ? entry.getKey()
                : parentPath + "_" + entry.getKey();
        var subModelRef = new Model();
        // name is simple leaf; packageName is full path
        subModelRef.setPackageName(fullPath);
        subModelRef.setName(entry.getKey());
        field.setSubModel(subModelRef);
      }
      fields.add(field);
    }
    return fields;
  }

  // =========================================================
  // Submodel de-duplication helpers
  // =========================================================

  private void dedupeAndPrefixSubModels(
      String moduleLowerCamel, List<Model> subModels, List<Field> topLevelFields) {
    if (subModels == null || subModels.isEmpty()) return;

    var usedUpperCamelNames = new java.util.HashSet<String>();
    var byBaseName = new java.util.HashMap<String, java.util.List<Model>>();
    for (var model : subModels) {
      byBaseName.computeIfAbsent(model.getName(), k -> new java.util.ArrayList<>()).add(model);
    }

    // Build mapping from fullPath -> new snake name (possibly unchanged)
    var renameByFullPath = new java.util.HashMap<String, String>();
    String moduleUpperCamel = CaseFormatUtil.toUpperCamelSafe(moduleLowerCamel);

    for (var entry : byBaseName.entrySet()) {
      var models = entry.getValue();
      if (models.size() == 1) {
        var only = models.get(0);
        String baseUpper = CaseFormatUtil.toUpperCamelSafe(only.getName());
        if (!usedUpperCamelNames.contains(baseUpper)) {
          usedUpperCamelNames.add(baseUpper);
          renameByFullPath.put(only.getPackageName(), only.getName());
        } else {
          // Rare collision with an existing name: prefix with module and dedupe
          String candidate = moduleUpperCamel + baseUpper;
          String parentUpper = deriveParentUpperCamel(only.getPackageName());
          if (usedUpperCamelNames.contains(candidate)) {
            candidate =
                (parentUpper == null || parentUpper.isEmpty())
                    ? moduleUpperCamel + baseUpper
                    : moduleUpperCamel + parentUpper + baseUpper;
            int suffix = 2;
            String core = candidate;
            while (usedUpperCamelNames.contains(candidate)) {
              candidate = core + suffix;
              suffix++;
            }
          }
          usedUpperCamelNames.add(candidate);
          String targetSnake = CaseFormatUtil.toSnakeCaseSafe(candidate);
          renameByFullPath.put(only.getPackageName(), targetSnake);
          only.setName(targetSnake);
        }
        continue;
      }

      // Duplicate group: keep first as-is (if unique), prefix the rest with module (and fallback
      // to parent path if needed)
      for (int i = 0; i < models.size(); i++) {
        var m = models.get(i);
        String baseUpper = CaseFormatUtil.toUpperCamelSafe(m.getName());
        String targetUpper = baseUpper;

        if (i != 0 || usedUpperCamelNames.contains(targetUpper)) {
          targetUpper = moduleUpperCamel + baseUpper;
          if (usedUpperCamelNames.contains(targetUpper)) {
            String parentUpper = deriveParentUpperCamel(m.getPackageName());
            targetUpper =
                (parentUpper == null || parentUpper.isEmpty())
                    ? moduleUpperCamel + baseUpper
                    : moduleUpperCamel + parentUpper + baseUpper;
            int suffix = 2;
            String core = targetUpper;
            while (usedUpperCamelNames.contains(targetUpper)) {
              targetUpper = core + suffix;
              suffix++;
            }
          }
        }

        usedUpperCamelNames.add(targetUpper);
        String targetSnake = CaseFormatUtil.toSnakeCaseSafe(targetUpper);
        renameByFullPath.put(m.getPackageName(), targetSnake);
        m.setName(targetSnake);
      }
    }

    // Update top-level field references
    updateFieldSubModelRefs(topLevelFields, renameByFullPath, subModels);
    // Update field references inside each submodel (flattened, but they may reference other
    // submodels)
    for (var m : subModels) {
      updateFieldSubModelRefs(m.getFields(), renameByFullPath, subModels);
    }
  }

  private void updateFieldSubModelRefs(
      List<Field> fields, java.util.Map<String, String> renameByFullPath, List<Model> subModels) {
    if (fields == null) return;
    for (var f : fields) {
      if (Boolean.TRUE.equals(f.isSubModelField()) && f.getSubModel() != null) {
        var ref = f.getSubModel();
        String fullPath = ref.getPackageName();
        String newSnakeName = renameByFullPath.get(fullPath);
        if (newSnakeName != null) {
          ref.setName(newSnakeName);
        }
        // Find the actual subModel and copy its fields so hasFilterFields() works
        if (subModels != null) {
          for (var subModel : subModels) {
            if (fullPath.equals(subModel.getPackageName())) {
              ref.setFields(subModel.getFields());
              break;
            }
          }
        }
      }
    }
  }

  /** Derive parent segment from a snake_case path and return it as UpperCamel. */
  private String deriveParentUpperCamel(String fullPathSnake) {
    if (fullPathSnake == null) return null;
    int idx = fullPathSnake.lastIndexOf('_');
    if (idx <= 0) return null;
    String parent = fullPathSnake.substring(0, idx);
    String lastSegment =
        parent.contains("_") ? parent.substring(parent.lastIndexOf('_') + 1) : parent;
    return CaseFormatUtil.toUpperCamelSafe(lastSegment);
  }

  // =========================================================
  // Resolution helpers
  // =========================================================

  /** Resolve module name using extension first, then path segment, defaulting to "unknown". */
  private String resolveModuleName(String path, Operation operation) {
    String module = null;
    if (operation != null && operation.getExtensions() != null) {
      Object moduleExt = operation.getExtensions().get(Extension.RESOURCE_ID);
      if (moduleExt != null) module = moduleExt.toString();
    }
    if (module == null) {
      String p = path;
      if (p != null) {
        String[] parts = p.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
          String part = parts[i];
          if (part != null && !part.isBlank() && !part.startsWith("{")) {
            module = part;
            break;
          }
        }
      }
    }
    if (module == null || module.isBlank()) module = "unknown";
    return module;
  }

  /** Return the path map for the current OpenAPI document. */
  private Map<String, PathItem> getOperations() {
    var paths = openApi.getPaths();
    return paths.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Select and return the request schema for an operation, considering common media types.
   */
  private Schema<?> resolveRequestSchema(Operation operation) {
    if (operation == null || operation.getRequestBody() == null) return null;
    RequestBody requestBody = operation.getRequestBody();
    if (requestBody.getContent() == null || requestBody.getContent().isEmpty()) return null;
    var content = requestBody.getContent();
    MediaType media = content.get("application/x-www-form-urlencoded");
    if (media == null) media = content.get("application/json");
    if (media == null) media = content.values().iterator().next();
    return media != null ? media.getSchema() : null;
  }

  /** Safely access schema properties with generics to avoid raw type warnings. */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private Map<String, Schema<?>> safeProperties(Schema<?> schema) {
    if (schema == null) return null;
    Map raw = schema.getProperties();
    return (Map<String, Schema<?>>) raw;
  }

  /**
   * Determines if a schema represents a composite array request body pattern.
   * This pattern is characterized by:
   * - Being an object type
   * - All properties are arrays
   * - Used for representing multiple parallel arrays (e.g., subscription_items)
   */
  private boolean isCompositeArrayPattern(Schema<?> schema) {
    if (schema == null || !"object".equals(schema.getType())) {
      return false;
    }

    Map<String, Schema<?>> properties = safeProperties(schema);
    if (properties == null || properties.isEmpty()) {
      return false;
    }

    // Check if ALL properties are arrays
    for (Schema<?> propertySchema : properties.values()) {
      if (!"array".equals(propertySchema.getType())) {
        return false;
      }
    }

    return true;
  }

  // =========================================================
  // Filter detection helpers (for POST params with filters)
  // =========================================================

  /**
   * Determines if a schema represents a filter parameter by checking the x-cb-is-filter-parameter
   * extension. This is the canonical way to detect filter parameters in the OpenAPI spec.
   */
  private boolean isDirectFilterParameter(Schema<?> schema) {
    if (schema == null) {
      return false;
    }
    return schema.getExtensions() != null
        && schema.getExtensions().get(Extension.IS_FILTER_PARAMETER) != null
        && (boolean) schema.getExtensions().get(Extension.IS_FILTER_PARAMETER);
  }

  /**
   * Get the list of supported filter operations from a filter schema.
   */
  private List<String> getSupportedOperations(Schema<?> schema) {
    var props = safeProperties(schema);
    if (props == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(props.keySet());
  }

  /**
   * Get the SDK filter name from the x-cb-sdk-filter-name extension.
   * Returns values like "NumberFilter", "TimestampFilter", "StringFilter", etc.
   */
  private String getFilterSdkName(Schema<?> schema) {
    if (schema == null || schema.getExtensions() == null) {
      return null;
    }
    Object filterName = schema.getExtensions().get(Extension.SDK_FILTER_NAME);
    return filterName != null ? filterName.toString() : null;
  }

  private List<String> getFilterEnumValues(Schema<?> filterSchema) {
    String sdkFilterName = getFilterSdkName(filterSchema);
    if (!"EnumFilter".equals(sdkFilterName)) {
      return null;
    }

    var props = safeProperties(filterSchema);
    if (props == null) {
      return null;
    }

    for (var entry : props.entrySet()) {
      String propName = entry.getKey();
      Schema<?> propSchema = entry.getValue();

      if ("is_present".equals(propName)) {
        continue;
      }

      if (propSchema.getEnum() != null && !propSchema.getEnum().isEmpty()) {
        return propSchema.getEnum().stream().map(String::valueOf).collect(Collectors.toList());
      }
      if (propSchema.getItems() != null && propSchema.getItems().getEnum() != null) {
        return propSchema.getItems().getEnum().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
      }
    }
    return null;
  }

  @lombok.Data
  private static class PostAction {
    private String operationId;
    private String module;
    private String path;
    private List<Field> fields;
    private List<EnumFields> enumFields;
    private List<Model> subModels;
    private boolean customFieldsSupported;
    private boolean consentFieldsSupported;
    private boolean operationNeedsJsonInput;

    public String getName() {
      String opSnake = CaseFormatUtil.toSnakeCaseSafe(getOperationId());
      String moduleSnake = CaseFormatUtil.toSnakeCaseSafe(module);

      // Check if operationId already contains the module name (e.g., "voidInvoice" contains
      // "invoice")
      // This happens when reserved keywords are suffixed with the resource name
      String moduleBase = moduleSnake.replaceAll("_", "");
      String operationBase = opSnake.replaceAll("_", "");
      if (opSnake.contains(moduleSnake)
          || operationBase.contains(moduleBase)
          || moduleBase.contains(operationBase)) {
        return CaseFormatUtil.toUpperCamelSafe(opSnake);
      }

      // Prefix with module name for other cases
      String actionSnake = moduleSnake.isEmpty() ? opSnake : moduleSnake + "_" + opSnake;
      return CaseFormatUtil.toUpperCamelSafe(actionSnake);
    }

    public String getModule() {
      return CaseFormatUtil.toLowerCamelSafe(module);
    }

    /**
     * Returns true if this action has any filter fields.
     * Required by the template to conditionally declare filterParams.
     */
    public boolean hasFilterFields() {
      if (fields == null || fields.isEmpty()) {
        return false;
      }
      return fields.stream().anyMatch(Field::isFilter);
    }
  }
}
