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
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

  public List<FileOp> build(OpenAPI openApi) {
    this.openApi = openApi;
    generateParams();
    return fileOps;
  }

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
          String module = null;
          if (extensions != null) {
            Object opNameExt = extensions.get(Extension.OPERATION_METHOD_NAME);
            if (opNameExt != null) opId = opNameExt.toString();
            Object moduleExt = extensions.get(Extension.RESOURCE_ID);
            if (moduleExt != null) module = moduleExt.toString();
          }
          // Fallbacks
          if (opId == null && operation.getOperationId() != null) {
            opId = operation.getOperationId();
          }
          if (module == null) {
            // Derive module from path as last non-empty segment
            String p = entry.getKey();
            if (p != null) {
              String[] parts = p.split("/");
              for (int i = parts.length - 1; i >= 0; i--) {
                if (parts[i] != null && !parts[i].isBlank() && !parts[i].startsWith("{")) {
                  module = parts[i];
                  break;
                }
              }
            }
            if (module == null) module = "unknown";
          }
          postAction.setOperationId(opId != null ? opId : "post");
          postAction.setModule(module);
          postAction.setPath(entry.getKey());

          var requestBody = operation.getRequestBody();
          if (requestBody != null && requestBody.getContent() != null) {
            var content = requestBody.getContent();
            var media = content.get("application/x-www-form-urlencoded");
            if (media == null) {
              media = content.get("application/json");
            }
            if (media == null && !content.isEmpty()) {
              media = content.values().iterator().next();
            }
            if (media != null && media.getSchema() != null) {
              var schema = media.getSchema();
              postAction.setFields(getFields(schema, null));
              postAction.setEnumFields(getEnumFields(schema));
              var subModels = getSubModels(schema, null);
              // Conditionally prefix only duplicate sub-model names
              dedupeAndPrefixSubModels(postAction.getModule(), subModels, postAction.getFields());
              postAction.setSubModels(subModels);
            } else {
              postAction.setFields(new ArrayList<>());
              postAction.setEnumFields(new ArrayList<>());
              postAction.setSubModels(new ArrayList<>());
            }
          } else {
            // No requestBody - generate empty params class
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

  private List<Model> getSubModels(Schema schema, String parentPath) {
    var subModels = new ArrayList<Model>();
    if (schema.getProperties() == null) {
      return subModels;
    }
    for (var fieldName : schema.getProperties().keySet()) {
      Schema schemaDefn = (Schema) schema.getProperties().get(fieldName);
      FieldType fieldType = TypeMapper.getJavaType(fieldName.toString(), schemaDefn);
      if (fieldType instanceof ObjectType) {
        if (schemaDefn.getProperties() == null || schemaDefn.getProperties().isEmpty()) {
          continue;
        }
        String fullPath =
            (parentPath == null || parentPath.isBlank())
                ? fieldName.toString()
                : parentPath + "_" + fieldName;
        // Name = leaf only; packageName = fullPath for disambiguation
        subModels.add(createSubModel(fieldName.toString(), fullPath, schemaDefn));
        subModels.addAll(getSubModels(schemaDefn, fullPath));
      } else if (fieldType instanceof ListType) {
        var listSchema = schemaDefn.getItems();
        if (listSchema == null
            || ((Schema<?>) listSchema).getProperties() == null
            || ((Schema) listSchema).getProperties().isEmpty()) {
          continue;
        }
        String fullPath =
            (parentPath == null || parentPath.isBlank())
                ? fieldName.toString()
                : parentPath + "_" + fieldName;
        subModels.add(createSubModel(fieldName.toString(), fullPath, (Schema) listSchema));
        subModels.addAll(getSubModels((Schema) listSchema, fullPath));
      }
    }
    return subModels;
  }

  private Model createSubModel(String simpleNameSnake, String fullPathSnake, Schema schema) {
    var subModel = new Model();
    // packageName holds full path for disambiguation; name holds the simple leaf name
    subModel.setPackageName(fullPathSnake);
    subModel.setName(simpleNameSnake);
    subModel.setFields(getFields(schema, fullPathSnake));
    subModel.setEnumFields(getEnumFields(schema));
    subModel.setSubModels(getSubModels(schema, fullPathSnake));
    return subModel;
  }

  private List<EnumFields> getEnumFields(Schema schema) {
    var enumFields = new ArrayList<EnumFields>();
    if (schema.getProperties() == null) {
      return enumFields;
    }
    for (var fieldName : schema.getProperties().keySet()) {
      Schema schemaDefn = (Schema) schema.getProperties().get(fieldName);
      if (schemaDefn.getEnum() != null) {
        var enumField = new EnumFields();
        enumField.setName(fieldName.toString());
        enumField.setEnums(schemaDefn.getEnum());
        enumFields.add(enumField);
      }
    }
    return enumFields;
  }

  private List<Field> getFields(Schema schema, String parentPath) {
    var fields = new ArrayList<Field>();
    if (schema.getProperties() == null) return fields;
    Map<String, Schema> parameters = schema.getProperties();
    for (var entry : parameters.entrySet()) {
      var field = new Field();
      field.setName(entry.getKey());
      field.setType(TypeMapper.getJavaType(entry.getKey(), entry.getValue()));
      field.setDeprecated(
          entry.getValue().getDeprecated() != null && entry.getValue().getDeprecated());
      // If this is a complex object with properties, mark as subModelField and attach a model
      Schema schemaDefn = entry.getValue();
      FieldType fieldType = TypeMapper.getJavaType(entry.getKey(), schemaDefn);
      if (fieldType instanceof ObjectType
          && schemaDefn.getProperties() != null
          && !schemaDefn.getProperties().isEmpty()) {
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

  private void dedupeAndPrefixSubModels(
      String moduleLowerCamel, List<Model> subModels, List<Field> topLevelFields) {
    if (subModels == null || subModels.isEmpty()) return;

    var usedUpperCamelNames = new java.util.HashSet<String>();
    var byBaseName = new java.util.HashMap<String, java.util.List<Model>>();
    for (var m : subModels) {
      byBaseName.computeIfAbsent(m.getName(), k -> new java.util.ArrayList<>()).add(m);
    }

    // Build mapping from fullPath -> new snake name (possibly unchanged)
    var renameByFullPath = new java.util.HashMap<String, String>();
    String moduleUpperCamel =
        com.google.common.base.CaseFormat.LOWER_CAMEL.to(
            com.google.common.base.CaseFormat.UPPER_CAMEL, moduleLowerCamel);

    for (var entry : byBaseName.entrySet()) {
      var models = entry.getValue();
      if (models.size() == 1) {
        var only = models.get(0);
        // Keep actual simple name
        usedUpperCamelNames.add(only.getName());
        renameByFullPath.put(only.getPackageName(), only.getName());
        continue;
      }
      // Duplicate group: keep first as-is, prefix the rest with module (and fallback to parent path
      // if needed)
      for (int i = 0; i < models.size(); i++) {
        var m = models.get(i);
        String baseUpper = m.getName();
        String targetUpper;
        if (i == 0 && !usedUpperCamelNames.contains(baseUpper)) {
          targetUpper = baseUpper;
        } else {
          // Prefer Module + Base
          targetUpper = moduleUpperCamel + baseUpper;
          if (usedUpperCamelNames.contains(targetUpper)) {
            // Fallback: Module + Parent + Base (derive parent from full path)
            String parentUpper = deriveParentUpperCamel(m.getPackageName());
            targetUpper =
                (parentUpper == null || parentUpper.isEmpty())
                    ? moduleUpperCamel + baseUpper
                    : moduleUpperCamel + parentUpper + baseUpper;
            int suffix = 2;
            while (usedUpperCamelNames.contains(targetUpper)) {
              targetUpper = moduleUpperCamel + parentUpper + baseUpper + suffix;
              suffix++;
            }
          }
        }
        usedUpperCamelNames.add(targetUpper);
        // Store snake name in rename map
        String targetSnake =
            com.google.common.base.CaseFormat.UPPER_CAMEL.to(
                com.google.common.base.CaseFormat.LOWER_UNDERSCORE, targetUpper);
        renameByFullPath.put(m.getPackageName(), targetSnake);
        // Apply to model immediately
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

  private String deriveParentUpperCamel(String fullPathSnake) {
    if (fullPathSnake == null) return null;
    int idx = fullPathSnake.lastIndexOf('_');
    if (idx <= 0) return null;
    String parent = fullPathSnake.substring(0, idx);
    String lastSegment =
        parent.contains("_") ? parent.substring(parent.lastIndexOf('_') + 1) : parent;
    return com.google.common.base.CaseFormat.LOWER_UNDERSCORE.to(
        com.google.common.base.CaseFormat.UPPER_CAMEL, lastSegment);
  }

  private Map<String, PathItem> getOperations() {
    var paths = openApi.getPaths();
    return paths.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @lombok.Data
  private static class PostAction {
    private String operationId;
    private String module;
    private String path;
    private List<Field> fields;
    private List<EnumFields> enumFields;
    private List<Model> subModels;
    private String name;

    public String getName() {
      var operationId = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, getOperationId());
      // Normalize module to snake_case to preserve token boundaries (handles lowerCamel inputs)
      var moduleSnake =
          module != null && module.contains("_")
              ? module
              : CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, module);
      var actionName = moduleSnake + "_" + operationId;
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, actionName);
    }

    public String getModule() {
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, module);
    }
  }
}
