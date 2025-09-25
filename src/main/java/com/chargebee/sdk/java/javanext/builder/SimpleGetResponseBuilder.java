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
 * Builder for generating simple get response classes.
 * Handles single resource get operations without pagination support.
 */
public class SimpleGetResponseBuilder {

  private Template template;
  private String outputDirectoryPath;
  private OpenAPI openApi;
  private List<Imports> modelImports = new ArrayList<>();

  private final List<FileOp> fileOps = new ArrayList<>();

  public SimpleGetResponseBuilder withOutputDirectoryPath(String outputDirectoryPath) {
    this.outputDirectoryPath = outputDirectoryPath;
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  public SimpleGetResponseBuilder withTemplate(Template template) {
    this.template = template;
    return this;
  }

  public List<FileOp> build(OpenAPI openApi) {
    this.openApi = openApi;
    generateSimpleGetResponses();
    return fileOps;
  }

  private void generateSimpleGetResponses() {
    var operations = getOperations();
    for (var pathEntry : operations.entrySet()) {
      var pathItem = pathEntry.getValue();

      if (pathItem.getGet() != null) {
        var operation = pathItem.getGet();
        var methodName = operation.getExtensions().get(Extension.OPERATION_METHOD_NAME).toString();
        var module = operation.getExtensions().get(Extension.RESOURCE_ID).toString();

        var response = operation.getResponses().get("200");
        if (response == null) continue;

        var jsonContent = response.getContent().get("application/json");
        if (jsonContent == null) continue;

        var schema = jsonContent.getSchema();

        // Only process if this is NOT a paginated list operation
        if (isPaginatedListOperation(schema)) {
          continue;
        }

        var simpleGetResponse = createSimpleGetResponse(methodName, module, schema);
        generateSimpleGetResponseFile(simpleGetResponse);
      }
    }
  }

  private SimpleGetResponse createSimpleGetResponse(
      String methodName, String module, Schema<?> schema) {
    var simpleGetResponse = new SimpleGetResponse();
    simpleGetResponse.setName(methodName);
    simpleGetResponse.setModule(module);
    simpleGetResponse.setFields(getResponseFields(schema, module));
    simpleGetResponse.setSubModels(getSubModels(schema, module));
    simpleGetResponse.setImports(getImports(schema));

    return simpleGetResponse;
  }

  private void generateSimpleGetResponseFile(SimpleGetResponse simpleGetResponse) {
    try {
      var fileName = simpleGetResponse.getName() + "Response.java";
      var content = template.apply(simpleGetResponse);
      var formattedContent = JavaFormatter.formatSafely(content);
      fileOps.add(
          new FileOp.CreateDirectory(
              outputDirectoryPath + "/" + simpleGetResponse.getModule(), ""));
      fileOps.add(
          new FileOp.WriteString(
              outputDirectoryPath + "/" + simpleGetResponse.getModule(),
              fileName,
              formattedContent));
    } catch (IOException e) {
      throw new RuntimeException("Failed to generate simple get response file", e);
    }
  }

  private List<Model> getSubModels(Schema<?> schema, String module) {
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
        subModels.add(createSubModel(fieldName, schemaDefn, module));
      } else if (fieldType instanceof ListType) {
        var listSchema = schemaDefn.getItems();
        if (listSchema == null
            || ((Schema<?>) listSchema).getProperties() == null
            || ((Schema<?>) listSchema).getProperties().isEmpty()) {
          continue;
        }
        subModels.add(createSubModel(module + "_" + fieldName + "_Item", listSchema, module));
      }
    }
    return subModels;
  }

  private Model createSubModel(String fieldName, Schema<?> schema, String module) {
    var subModel = new Model();
    subModel.setName(fieldName);
    subModel.setFields(getResponseFields(schema, module));
    subModel.setSubModels(getSubModels(schema, module));
    return subModel;
  }

  private List<Imports> getImports(Schema<?> schema) {
    var imports = new ArrayList<Imports>();
    for (var importObj : modelImports) {
      imports.add(importObj);
    }

    return imports;
  }

  private List<Field> getResponseFields(Schema<?> schema, String module) {
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
          fieldType = TypeMapper.getJavaType(module + "_" + fieldName + "_Item", fieldSchema);
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
  private static class SimpleGetResponse {
    private String name;
    private String module;
    private List<Field> fields;
    private List<Imports> imports;
    private List<Model> subModels;

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
  }
}
