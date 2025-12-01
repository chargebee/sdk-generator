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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builder for generating paginated list response classes.
 * Handles list operations with pagination support including next_offset, auto-pagination, and iteration.
 */
public class ListResponseBuilder {

  // ------------------------------------------------------------
  // Constants
  // ------------------------------------------------------------
  private static final String HTTP_OK = "200";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String PROP_LIST = "list";
  private static final String PROP_NEXT_OFFSET = "next_offset";

  // ------------------------------------------------------------
  // State
  // ------------------------------------------------------------
  private Template template;
  private String outputDirectoryPath;
  private OpenAPI openApi;

  private final List<FileOp> fileOps = new ArrayList<>();

  // ------------------------------------------------------------
  // Fluent configuration API
  // ------------------------------------------------------------
  public ListResponseBuilder withOutputDirectoryPath(String outputDirectoryPath) {
    this.outputDirectoryPath = outputDirectoryPath;
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  public ListResponseBuilder withTemplate(Template template) {
    this.template = template;
    return this;
  }

  /**
   * Generates file operations for all paginated list responses discovered in the provided OpenAPI.
   *
   * @param openApi the OpenAPI model to read paths, operations and schemas from
   * @return ordered list of file operations to create the generated sources
   * @throws IllegalStateException if the builder is not fully configured
   */
  public List<FileOp> build(OpenAPI openApi) {
    validateConfiguration(openApi);
    this.openApi = openApi;
    MethodNameDeriver.initialize(openApi);
    generateListResponses();
    return fileOps;
  }

  // ------------------------------------------------------------
  // Orchestration
  // ------------------------------------------------------------
  private void generateListResponses() {
    var operations = getOperations();
    for (var pathEntry : operations.entrySet()) {
      var pathItem = pathEntry.getValue();

      if (pathItem != null && pathItem.getGet() != null) {
        var operation = pathItem.getGet();

        if (operation.getExtensions() == null
            || !operation.getExtensions().containsKey(Extension.RESOURCE_ID)
            || operation.getResponses() == null) {
          // Skip operations that are not fully annotated for generation
          continue;
        }

        // Derive method name from path using common utility
        var normalizedMethodName = MethodNameDeriver.deriveMethodName(pathEntry.getKey(), "GET", operation);
        normalizedMethodName = MethodNameDeriver.applyBatchPrefix(pathEntry.getKey(), normalizedMethodName);
        var methodName =
            CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, normalizedMethodName);
        var module = String.valueOf(operation.getExtensions().get(Extension.RESOURCE_ID));

        var response = operation.getResponses().get(HTTP_OK);
        if (response == null || response.getContent() == null) continue;

        var jsonContent = response.getContent().get(CONTENT_TYPE_JSON);
        if (jsonContent == null || jsonContent.getSchema() == null) continue;

        var schema = jsonContent.getSchema();

        // Only process if this is a paginated list operation
        if (!isPaginatedListOperation(schema)) {
          continue;
        }

        var listResponse =
            createListResponse(
                methodName, module, schema, pathEntry.getKey(), normalizedMethodName);
        generateListResponseFile(listResponse);
      }
    }
  }

  /**
   * Creates the in-memory model for a single list response based on the schema and path context.
   */
  private ListResponse createListResponse(
      String methodName, String module, Schema<?> schema, String path, String originalMethodName) {
    var listResponse = new ListResponse();
    listResponse.setName(methodName);
    listResponse.setOriginalMethodName(originalMethodName);
    listResponse.setModule(module);

    // Collect imports per response to avoid cross-operation leakage
    var importsCollector = new ArrayList<Imports>();
    listResponse.setFields(getResponseFields(schema, module, methodName, importsCollector));
    listResponse.setSubModels(getSubModels(schema, module, methodName, importsCollector));

    // Set pagination-specific fields
    setPaginationFields(listResponse, schema);
    listResponse.setImports(deduplicateImports(importsCollector));

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
    if (schema == null || schema.getProperties() == null) {
      return;
    }
    var properties = schema.getProperties();

    // Find the list field
    for (var entry : properties.entrySet()) {
      var fieldName = entry.getKey().toString();
      var fieldSchema = (Schema<?>) entry.getValue();

      if (PROP_LIST.equals(fieldName) && "array".equals(fieldSchema.getType())) {
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
    if (properties.containsKey(PROP_NEXT_OFFSET)) {
      listResponse.setNextOffsetField("nextOffset");
    }
  }

  private void generateListResponseFile(ListResponse listResponse) {
    try {
      var fileName = listResponse.getName() + "Response.java";
      var content = template.apply(listResponse);
      var formattedContent = JavaFormatter.formatSafely(content);
      var moduleDir = outputDirectoryPath + "/" + listResponse.getModule();
      var responsesDir = moduleDir + "/responses";
      fileOps.add(new FileOp.CreateDirectory(moduleDir, ""));
      fileOps.add(new FileOp.CreateDirectory(responsesDir, ""));
      fileOps.add(new FileOp.WriteString(responsesDir, fileName, formattedContent));
    } catch (IOException e) {
      throw new RuntimeException("Failed to generate list response file", e);
    }
  }

  private List<Model> getSubModels(
      Schema<?> schema, String module, String methodName, List<Imports> importsCollector) {
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
        subModels.add(createSubModel(fieldName, schemaDefn, module, methodName, importsCollector));
      } else if (fieldType instanceof ListType) {
        var listSchema = schemaDefn.getItems();
        if (listSchema == null
            || ((Schema<?>) listSchema).getProperties() == null
            || ((Schema<?>) listSchema).getProperties().isEmpty()) {
          continue;
        }
        subModels.add(
            createSubModel(
                module + "_" + methodName + "_Item",
                listSchema,
                module,
                methodName,
                importsCollector));
      }
    }
    return subModels;
  }

  private Model createSubModel(
      String fieldName,
      Schema<?> schema,
      String module,
      String methodName,
      List<Imports> importsCollector) {
    var subModel = new Model();
    subModel.setName(fieldName);
    subModel.setFields(getResponseFields(schema, module, methodName, importsCollector));
    subModel.setSubModels(getSubModels(schema, module, methodName, importsCollector));
    return subModel;
  }

  // ------------------------------------------------------------
  // Schema parsing helpers
  // ------------------------------------------------------------

  /**
   * Extracts fields for the response model and collects any referenced imports.
   */
  private List<Field> getResponseFields(
      Schema<?> schema, String module, String methodName, List<Imports> importsCollector) {
    var fields = new ArrayList<Field>();
    if (schema == null || schema.getProperties() == null) {
      return fields;
    }
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
              "com.chargebee.v4.models."
                  + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, refModelName));
          importsCollector.add(importObj);
          // fieldType is already correctly set by TypeMapper
        } else {
          // For inline object definitions, use the old logic (list schema context)
          fieldType = TypeMapper.getJavaType(module + "_" + methodName + "_Item", fieldSchema);
        }
      } else if (fieldSchema.get$ref() != null) {
        field.setName(fieldName);
        var refModelName =
            fieldSchema.get$ref().substring(fieldSchema.get$ref().lastIndexOf("/") + 1);
        var importObj = new Imports();
        importObj.setName(refModelName);
        importObj.setPackageName(
            "com.chargebee.v4.models."
                + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, refModelName));
        importsCollector.add(importObj);
        fieldType = TypeMapper.getJavaType(refModelName, schema);
      }
      field.setType(fieldType);
      fields.add(field);
    }
    return fields;
  }

  private boolean isPaginatedListOperation(Schema<?> schema) {
    if (schema == null || schema.getProperties() == null) {
      return false;
    }
    var properties = schema.getProperties();
    return properties.containsKey(PROP_LIST) && properties.containsKey(PROP_NEXT_OFFSET);
  }

  private Map<String, PathItem> getOperations() {
    if (openApi == null || openApi.getPaths() == null) {
      return Collections.emptyMap();
    }
    var paths = openApi.getPaths();
    return paths.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  // ------------------------------------------------------------
  // Utility methods
  // ------------------------------------------------------------

  private void validateConfiguration(OpenAPI openApi) {
    Objects.requireNonNull(template, "Template must be provided via withTemplate()");
    Objects.requireNonNull(outputDirectoryPath, "Output directory path must be provided");
    Objects.requireNonNull(openApi, "OpenAPI must not be null");
  }

  private List<Imports> deduplicateImports(List<Imports> imports) {
    if (imports == null || imports.isEmpty()) {
      return Collections.emptyList();
    }
    Map<String, Imports> unique = new LinkedHashMap<>();
    for (Imports imp : imports) {
      if (imp == null) continue;
      String key =
          (imp.getPackageName() == null ? "" : imp.getPackageName())
              + "#"
              + (imp.getName() == null ? "" : imp.getName());
      unique.putIfAbsent(key, imp);
    }
    return new ArrayList<>(unique.values());
  }

  @lombok.Data
  private static class Imports {
    @SuppressWarnings("unused")
    private String name;

    @SuppressWarnings("unused")
    private String packageName;

    @SuppressWarnings("unused")
    private String moduleName;

    public String getModuleName() {
      return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, name);
    }
  }

  @lombok.Data
  private static class ListResponse {
    @SuppressWarnings("unused")
    private String name;

    @SuppressWarnings("unused")
    private String originalMethodName;

    @SuppressWarnings("unused")
    private String module;

    private List<Field> fields;
    private List<Imports> imports;
    private List<Model> subModels;

    @SuppressWarnings("unused")
    private String serviceName;

    private String listFieldName;

    @SuppressWarnings("unused")
    private String itemType;

    private String nextOffsetField;
    private boolean hasPathParams;
    private String pathParamName;

    public String getName() {
      var operationIdSnake = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
      // Normalize module to snake_case to preserve token boundaries (handles lowerCamel inputs)
      var moduleSnake =
          module != null && module.contains("_")
              ? module
              : CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, module);

      // If operationId contains the module name (or its singular/plural variations), don't prefix it
      var moduleBase = moduleSnake.replaceAll("_", "");
      var operationBase = operationIdSnake.replaceAll("_", "");
      if (operationIdSnake.contains(moduleSnake) ||
          operationBase.contains(moduleBase) ||
          moduleBase.contains(operationBase)) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, operationIdSnake);
      }

      var actionName = moduleSnake + "_" + operationIdSnake;
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
            if (elementType == null || elementType.isEmpty()) {
              break;
            }
            if ("Object".equals(elementType)
                || "java.util.Map<String, Object>".equals(elementType)) {
              return "java.util.Map<String, Object>";
            }
            return elementType;
          }
        }
      }
      // Default to map for safety when unknown
      return "java.util.Map<String, Object>";
    }

    public String getListFieldName() {
      return listFieldName != null ? listFieldName : "list";
    }

    public String getNextOffsetField() {
      return nextOffsetField != null ? nextOffsetField : "nextOffset";
    }

    @SuppressWarnings("unused")
    public String getOperationMethodName() {
      return originalMethodName;
    }
  }
}
