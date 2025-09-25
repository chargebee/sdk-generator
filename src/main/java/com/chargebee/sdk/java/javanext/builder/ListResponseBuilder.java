package com.chargebee.sdk.java.javanext.builder;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.javanext.JavaFormatter;
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

/**
 * Builder for generating paginated list response classes.
 * Handles list operations with pagination support including next_offset, auto-pagination, and iteration.
 */
public class ListResponseBuilder {

  private Template template;
  private String outputDirectoryPath;
  private OpenAPI openApi;
  private List<Imports> modelImports = new ArrayList<>();

  private final List<FileOp> fileOps = new ArrayList<>();

  public ListResponseBuilder withOutputDirectoryPath(String outputDirectoryPath) {
    this.outputDirectoryPath = outputDirectoryPath;
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  public ListResponseBuilder withTemplate(Template template) {
    this.template = template;
    return this;
  }

  public List<FileOp> build(OpenAPI openApi) {
    this.openApi = openApi;
    generateListResponses();
    return fileOps;
  }

  private void generateListResponses() {
    var operations = getOperations();
    for (var pathEntry : operations.entrySet()) {
      var pathItem = pathEntry.getValue();

      if (pathItem.getGet() != null) {
        var operation = pathItem.getGet();

        var originalMethodName =
            operation.getExtensions().get(Extension.OPERATION_METHOD_NAME).toString();
        var methodName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, originalMethodName);
        var module = operation.getExtensions().get(Extension.RESOURCE_ID).toString();

        var response = operation.getResponses().get("200");
        if (response == null) continue;

        var jsonContent = response.getContent().get("application/json");
        if (jsonContent == null) continue;

        var schema = jsonContent.getSchema();

        // Only process if this is a paginated list operation
        if (!isPaginatedListOperation(schema)) {
          continue;
        }

        var listResponse =
            createListResponse(methodName, module, schema, pathEntry.getKey(), originalMethodName);
        generateListResponseFile(listResponse);
      }
    }
  }

  private ListResponse createListResponse(
      String methodName, String module, Schema<?> schema, String path, String originalMethodName) {
    var listResponse = new ListResponse();
    listResponse.setName(methodName);
    listResponse.setOriginalMethodName(originalMethodName);
    listResponse.setModule(module);
    listResponse.setFields(getResponseFields(schema, module, methodName));
    listResponse.setSubModels(getSubModels(schema, module, methodName));

    // Set pagination-specific fields
    setPaginationFields(listResponse, schema);
    listResponse.setImports(getImports(schema));

    // Path parameter handling for nextPage()/service calls
    boolean hasPathParams = path != null && path.contains("{");
    listResponse.setHasPathParams(hasPathParams);
    if (hasPathParams) {
      int start = path.indexOf('{');
      int end = path.indexOf('}', start);
      if (start != -1 && end != -1) {
        String rawParam = path.substring(start + 1, end);
        String paramName =
            com.google.common.base.CaseFormat.LOWER_HYPHEN.to(
                com.google.common.base.CaseFormat.LOWER_CAMEL, rawParam);
        listResponse.setPathParamName(paramName);
      }
    }

    return listResponse;
  }

  private void setPaginationFields(ListResponse listResponse, Schema<?> schema) {
    var properties = schema.getProperties();

    // Find the list field
    for (var entry : properties.entrySet()) {
      var fieldName = entry.getKey().toString();
      var fieldSchema = (Schema<?>) entry.getValue();

      if ("list".equals(fieldName) && fieldSchema.getType().equals("array")) {
        listResponse.setListFieldName(fieldName);

        // Get the item type from the array items (defensive checks)
        var itemSchema = fieldSchema.getItems();
        if (itemSchema != null) {
          if (itemSchema.get$ref() != null) {
            // When items refer to an existing model, we could set an import if needed
            // Item type used in templates defaults to "<Name>Item" via getter, so this is optional
          } else if (itemSchema.getProperties() != null && !itemSchema.getProperties().isEmpty()) {
            String firstProp = itemSchema.getProperties().keySet().iterator().next().toString();
            // Not strictly required since templates derive item type name, but keep for
            // completeness
            listResponse.setItemType(TypeMapper.getJavaType(firstProp, itemSchema).toString());
          }
        }
        break;
      }
    }

    // Set next offset field name
    if (properties.containsKey("next_offset")) {
      listResponse.setNextOffsetField("nextOffset");
    }
  }

  private void generateListResponseFile(ListResponse listResponse) {
    try {
      var fileName = listResponse.getName() + "Response.java";
      var content = template.apply(listResponse);
      var formattedContent = JavaFormatter.formatSafely(content);
      fileOps.add(
          new FileOp.CreateDirectory(outputDirectoryPath + "/" + listResponse.getModule(), ""));
      fileOps.add(
          new FileOp.WriteString(
              outputDirectoryPath + "/" + listResponse.getModule(), fileName, formattedContent));
    } catch (IOException e) {
      throw new RuntimeException("Failed to generate list response file", e);
    }
  }

  private List<Model> getSubModels(Schema<?> schema, String module, String methodName) {
    var subModels = new ArrayList<Model>();
    if (schema.getProperties() == null) {
      return subModels;
    }
    for (var fieldName : schema.getProperties().keySet()) {
      Schema<?> schemaDefn = (Schema<?>) schema.getProperties().get(fieldName);
      FieldType fieldType = TypeMapper.getJavaType(fieldName, schemaDefn);
      if (fieldType instanceof ObjectType) {
        if (schemaDefn.getProperties() == null || schemaDefn.getProperties().isEmpty()) {
          continue;
        }
        subModels.add(createSubModel(fieldName, schemaDefn, module, methodName));
      } else if (fieldType instanceof ListType) {
        var listSchema = schemaDefn.getItems();
        if (listSchema == null
            || ((Schema<?>) listSchema).getProperties() == null
            || ((Schema<?>) listSchema).getProperties().isEmpty()) {
          continue;
        }
        subModels.add(
            createSubModel(module + "_" + methodName + "_Item", listSchema, module, methodName));
      }
    }
    return subModels;
  }

  private Model createSubModel(
      String fieldName, Schema<?> schema, String module, String methodName) {
    var subModel = new Model();
    subModel.setName(fieldName);
    subModel.setFields(getResponseFields(schema, module, methodName));
    subModel.setSubModels(getSubModels(schema, module, methodName));
    return subModel;
  }

  private List<Imports> getImports(Schema<?> schema) {
    var imports = new ArrayList<Imports>();
    for (var importObj : modelImports) {
      imports.add(importObj);
    }

    return imports;
  }

  private List<Field> getResponseFields(Schema<?> schema, String module, String methodName) {
    var fields = new ArrayList<Field>();
    for (var key : schema.getProperties().keySet()) {
      var fieldName = key.toString();
      var fieldSchema = (Schema<?>) schema.getProperties().get(fieldName);
      var fieldType = TypeMapper.getJavaType(fieldName, fieldSchema);
      var field = new Field();
      field.setName(fieldName);
      if (fieldType instanceof ListType) {
        field.setName(fieldName);
        // Check if the list items have a $ref
        var itemsSchema = fieldSchema.getItems();
        if (itemsSchema != null && itemsSchema.get$ref() != null) {
          var refModelName =
              itemsSchema.get$ref().substring(itemsSchema.get$ref().lastIndexOf("/") + 1);
          var importObj = new Imports();
          importObj.setName(refModelName);
          importObj.setPackageName(
              "com.chargebee.core.models."
                  + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, refModelName));
          modelImports.add(importObj);
          // fieldType is already correctly set by TypeMapper
        } else {
          // For inline object definitions, use the old logic
          fieldType = TypeMapper.getJavaType(module + "_" + methodName + "_Item", fieldSchema);
        }
      } else if (fieldSchema.get$ref() != null) {
        field.setName(fieldName);
        var refModelName =
            fieldSchema.get$ref().substring(fieldSchema.get$ref().lastIndexOf("/") + 1);
        var importObj = new Imports();
        importObj.setName(refModelName);
        importObj.setPackageName(
            "com.chargebee.core.models."
                + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, refModelName));
        modelImports.add(importObj);
        fieldType = TypeMapper.getJavaType(refModelName, schema);
      }
      field.setType(fieldType);
      fields.add(field);
    }
    return fields;
  }

  private boolean isPaginatedListOperation(Schema<?> schema) {
    var properties = schema.getProperties();
    return properties.containsKey("list") && properties.containsKey("next_offset");
  }

  private Map<String, PathItem> getOperations() {
    var paths = openApi.getPaths();
    return paths.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @lombok.Data
  private static class Imports {
    private String name;
    private String packageName;
    private String moduleName;

    public String getModuleName() {
      return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, name);
    }
  }

  @lombok.Data
  private static class ListResponse {
    private String name;
    private String originalMethodName;
    private String module;
    private List<Field> fields;
    private List<Imports> imports;
    private List<Model> subModels;
    private String serviceName;
    private String listFieldName;
    private String itemType;
    private String nextOffsetField;
    private boolean hasPathParams;
    private String pathParamName;

    public String getName() {
      var operationId = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
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

    public String getServiceName() {
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, module) + "Service";
    }

    public String getItemType() {
      // Determine item type based on the list field schema
      String effectiveListField = getListFieldName();
      if (effectiveListField == null || effectiveListField.isEmpty()) {
        effectiveListField = "list";
      }
      if (fields != null) {
        for (Field f : fields) {
          if (effectiveListField.equals(f.getName())) {
            String elementType = f.getListElementType();
            if ("Object".equals(elementType)) {
              return "Object";
            }
            break;
          }
        }
      }
      return getName() + "Item";
    }

    public String getListFieldName() {
      return listFieldName != null ? listFieldName : "list";
    }

    public String getNextOffsetField() {
      return nextOffsetField != null ? nextOffsetField : "nextOffset";
    }

    public String getOperationMethodName() {
      return originalMethodName;
    }
  }
}
