package com.chargebee.sdk.java.javanext.builder;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.javanext.JavaFormatter;
import com.chargebee.sdk.java.javanext.core.EnumFields;
import com.chargebee.sdk.java.javanext.core.Field;
import com.chargebee.sdk.java.javanext.core.Model;
import com.chargebee.sdk.java.javanext.core.TypeMapper;
import com.chargebee.sdk.java.javanext.datatype.FieldType;
import com.chargebee.sdk.java.javanext.datatype.ListType;
import com.chargebee.sdk.java.javanext.datatype.ObjectType;
import com.chargebee.sdk.java.javanext.util.CaseFormatUtil;
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
    this.outputDirectoryPath = outputDirectoryPath + "/core/models";
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

          var postAction = new PostAction();
          // Safely read extensions
          var extensions = operation.getExtensions();
          String opId = null;
          if (extensions != null) {
            Object opNameExt = extensions.get(Extension.OPERATION_METHOD_NAME);
            if (opNameExt != null) opId = opNameExt.toString();
          }
          // Fallbacks
          if (opId == null && operation.getOperationId() != null) {
            opId = operation.getOperationId();
          }
          String module = resolveModuleName(entry.getKey(), operation);
          postAction.setOperationId(opId != null ? opId : "post");
          postAction.setModule(module);
          postAction.setPath(entry.getKey());

          Schema<?> requestSchema = resolveRequestSchema(operation);
          if (requestSchema != null) {
            postAction.setFields(getFields(requestSchema, null));
            postAction.setEnumFields(getEnumFields(requestSchema));
            var subModels = getSubModels(requestSchema, null);
            // Conditionally prefix only duplicate sub-model names
            dedupeAndPrefixSubModels(postAction.getModule(), subModels, postAction.getFields());
            postAction.setSubModels(subModels);
          } else {
            // No usable request schema - generate empty params class
            postAction.setFields(new ArrayList<>());
            postAction.setEnumFields(new ArrayList<>());
            postAction.setSubModels(new ArrayList<>());
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
    subModel.setFields(getFields(schema, fullPathSnake));
    subModel.setEnumFields(getEnumFields(schema));
    subModel.setSubModels(getSubModels(schema, fullPathSnake));
    return subModel;
  }

  /** Collect enum fields defined directly under the given schema. */
  private List<EnumFields> getEnumFields(Schema<?> schema) {
    var enumFields = new ArrayList<EnumFields>();
    Map<String, Schema<?>> properties = safeProperties(schema);
    if (properties == null) {
      return enumFields;
    }
    for (var fieldName : properties.keySet()) {
      Schema<?> schemaDefn = properties.get(fieldName);
      if (schemaDefn.getEnum() != null) {
        var enumField = new EnumFields();
        enumField.setName(fieldName.toString());
        java.util.List<String> enumStrings =
            ((java.util.List<?>) schemaDefn.getEnum())
                .stream().map(String::valueOf).collect(java.util.stream.Collectors.toList());
        enumField.setEnums(enumStrings);
        enumFields.add(enumField);
      }
    }
    return enumFields;
  }

  /** Collect immediate field definitions for the given schema. */
  private List<Field> getFields(Schema<?> schema, String parentPath) {
    var fields = new ArrayList<Field>();
    Map<String, Schema<?>> parameters = safeProperties(schema);
    if (parameters == null) return fields;
    for (var entry : parameters.entrySet()) {
      var field = new Field();
      field.setName(entry.getKey());
      field.setType(TypeMapper.getJavaType(entry.getKey(), entry.getValue()));
      field.setDeprecated(
          entry.getValue().getDeprecated() != null && entry.getValue().getDeprecated());
      // If this is a complex object with properties, mark as subModelField and attach a model
      Schema<?> schemaDefn = entry.getValue();
      FieldType fieldType = TypeMapper.getJavaType(entry.getKey(), schemaDefn);
      if (fieldType instanceof ObjectType
          && safeProperties(schemaDefn) != null
          && !safeProperties(schemaDefn).isEmpty()) {
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
    updateFieldSubModelRefs(topLevelFields, renameByFullPath);
    // Update field references inside each submodel (flattened, but they may reference other
    // submodels)
    for (var m : subModels) {
      updateFieldSubModelRefs(m.getFields(), renameByFullPath);
    }
  }

  private void updateFieldSubModelRefs(
      List<Field> fields, java.util.Map<String, String> renameByFullPath) {
    if (fields == null) return;
    for (var f : fields) {
      if (Boolean.TRUE.equals(f.isSubModelField()) && f.getSubModel() != null) {
        var ref = f.getSubModel();
        String fullPath = ref.getPackageName();
        String newSnakeName = renameByFullPath.get(fullPath);
        if (newSnakeName != null) {
          ref.setName(newSnakeName);
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

  @lombok.Data
  private static class PostAction {
    private String operationId;
    private String module;
    private String path;
    private List<Field> fields;
    private List<EnumFields> enumFields;
    private List<Model> subModels;

    public String getName() {
      String opSnake = CaseFormatUtil.toSnakeCaseSafe(getOperationId());
      String moduleSnake = CaseFormatUtil.toSnakeCaseSafe(module);
      String actionSnake = moduleSnake.isEmpty() ? opSnake : moduleSnake + "_" + opSnake;
      return CaseFormatUtil.toUpperCamelSafe(actionSnake);
    }

    public String getModule() {
      return CaseFormatUtil.toLowerCamelSafe(module);
    }
  }
}
