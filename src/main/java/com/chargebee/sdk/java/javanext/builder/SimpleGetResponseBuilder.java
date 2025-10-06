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
import java.util.stream.Collectors;
import lombok.NonNull;

/**
 * Builder for generating simple get response classes.
 * Handles single resource get operations without pagination support.
 */
public class SimpleGetResponseBuilder {

  private Template template;
  private String outputDirectoryPath;
  private OpenAPI openApi;

  private final List<FileOp> fileOps = new ArrayList<>();

  // -----------------------------------------------------------------------------
  // Section: Constants
  // -----------------------------------------------------------------------------
  private static final String RESPONSE_CODE_OK = "200";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String PROP_LIST = "list";
  private static final String PROP_NEXT_OFFSET = "next_offset";
  private static final String CORE_MODELS_PACKAGE_PREFIX = "com.chargebee.v4.core.models.";

  /**
   * Configure the output directory where generated response classes will be written.
   *
   * @param outputDirectoryPath absolute path to the output directory
   * @return this builder for chaining
   */
  public SimpleGetResponseBuilder withOutputDirectoryPath(@NonNull String outputDirectoryPath) {
    this.outputDirectoryPath = outputDirectoryPath;
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  /**
   * Configure the template used to render the response classes.
   *
   * @param template handlebars template instance
   * @return this builder for chaining
   */
  public SimpleGetResponseBuilder withTemplate(@NonNull Template template) {
    this.template = template;
    return this;
  }

  /**
   * Generate simple GET response classes and return filesystem operations to be applied.
   *
   * @param openApi parsed OpenAPI spec
   * @return list of file operations representing the generated outputs
   */
  public List<FileOp> build(@NonNull OpenAPI openApi) {
    this.openApi = openApi;
    generateSimpleGetResponses();
    return fileOps;
  }

  // -----------------------------------------------------------------------------
  // Section: Generation Orchestration
  // -----------------------------------------------------------------------------
  private void generateSimpleGetResponses() {
    var operations = getOperations();
    for (var pathEntry : operations.entrySet()) {
      var pathItem = pathEntry.getValue();

      if (pathItem.getGet() != null) {
        var operation = pathItem.getGet();
        var extensions = operation.getExtensions();
        var methodExt = extensions != null ? extensions.get(Extension.OPERATION_METHOD_NAME) : null;
        var moduleExt = extensions != null ? extensions.get(Extension.RESOURCE_ID) : null;
        var methodName = methodExt != null ? methodExt.toString() : null;
        var module = moduleExt != null ? moduleExt.toString() : null;
        if (methodName == null || module == null) {
          continue; // Missing required extensions; skip gracefully
        }

        var responses = operation.getResponses();
        if (responses == null) continue;
        var response = responses.get(RESPONSE_CODE_OK);
        if (response == null || response.getContent() == null) continue;

        var jsonContent = response.getContent().get(CONTENT_TYPE_JSON);
        if (jsonContent == null) continue;

        var schema = jsonContent.getSchema();
        if (schema == null) continue;

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
    var importsAccumulator = new ArrayList<Imports>();

    var simpleGetResponse = new SimpleGetResponse();
    simpleGetResponse.setName(methodName);
    simpleGetResponse.setModule(module);
    simpleGetResponse.setFields(getResponseFields(schema, module, importsAccumulator));
    simpleGetResponse.setSubModels(getSubModels(schema, module, importsAccumulator));
    simpleGetResponse.setImports(getImports(importsAccumulator));

    return simpleGetResponse;
  }

  /**
   * Render and stage a file operation for the generated response.
   */
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

  // -----------------------------------------------------------------------------
  // Section: Schema Parsing
  // -----------------------------------------------------------------------------
  private List<Model> getSubModels(
      Schema<?> schema, String module, List<Imports> importsAccumulator) {
    var subModels = new ArrayList<Model>();
    if (schema == null || schema.getProperties() == null) {
      return subModels;
    }
    for (var fieldName : schema.getProperties().keySet()) {
      Schema<?> schemaDefn = (Schema<?>) schema.getProperties().get(fieldName);
      FieldType fieldType = TypeMapper.getJavaType(fieldName, schemaDefn);
      if (fieldType instanceof ObjectType) {
        if (schemaDefn.getProperties() == null || schemaDefn.getProperties().isEmpty()) {
          continue;
        }
        subModels.add(createSubModel(fieldName, schemaDefn, module, importsAccumulator));
      } else if (fieldType instanceof ListType) {
        var listSchema = schemaDefn.getItems();
        if (listSchema == null
            || ((Schema<?>) listSchema).getProperties() == null
            || ((Schema<?>) listSchema).getProperties().isEmpty()) {
          continue;
        }
        subModels.add(
            createSubModel(
                module + "_" + fieldName + "_Item", listSchema, module, importsAccumulator));
      }
    }
    return subModels;
  }

  private Model createSubModel(
      String fieldName, Schema<?> schema, String module, List<Imports> importsAccumulator) {
    var subModel = new Model();
    subModel.setName(fieldName);
    subModel.setFields(getResponseFields(schema, module, importsAccumulator));
    subModel.setSubModels(getSubModels(schema, module, importsAccumulator));
    return subModel;
  }

  private List<Imports> getImports(List<Imports> importsAccumulator) {
    // Dedupe by import name while preserving order
    var dedupedByName = new LinkedHashMap<String, Imports>();
    for (var imp : importsAccumulator) {
      if (!dedupedByName.containsKey(imp.getName())) {
        dedupedByName.put(imp.getName(), imp);
      }
    }
    return new ArrayList<>(dedupedByName.values());
  }

  private List<Field> getResponseFields(
      Schema<?> schema, String module, List<Imports> importsAccumulator) {
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
          var refModelName = lastSegmentOfRef(itemsSchema.get$ref());
          accumulateImport(importsAccumulator, refModelName);
          // fieldType is already correctly set by TypeMapper
        } else {
          // For inline object definitions, use the old logic
          if (itemsSchema != null) {
            fieldType =
                TypeMapper.getJavaType(module + "_" + fieldName + "_Item", (Schema<?>) itemsSchema);
          }
        }
      } else if (fieldSchema.get$ref() != null) {
        field.setName(fieldName);
        var refModelName = lastSegmentOfRef(fieldSchema.get$ref());
        accumulateImport(importsAccumulator, refModelName);
        // Fix: use the field schema (the referenced schema), not the parent schema
        fieldType = TypeMapper.getJavaType(refModelName, fieldSchema);
      }
      field.setType(fieldType);
      fields.add(field);
    }
    return fields;
  }

  private boolean isPaginatedListOperation(Schema<?> schema) {
    var properties = schema != null ? schema.getProperties() : null;
    return properties != null
        && properties.containsKey(PROP_LIST)
        && properties.containsKey(PROP_NEXT_OFFSET);
  }

  private Map<String, PathItem> getOperations() {
    if (openApi == null || openApi.getPaths() == null) {
      return Collections.emptyMap();
    }
    var paths = openApi.getPaths();
    return paths.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  // -----------------------------------------------------------------------------
  // Section: Utilities
  // -----------------------------------------------------------------------------
  private static String lastSegmentOfRef(String ref) {
    var idx = ref.lastIndexOf('/') + 1;
    return idx >= 0 && idx < ref.length() ? ref.substring(idx) : ref;
  }

  private static String toModelPackage(String refModelName) {
    return CORE_MODELS_PACKAGE_PREFIX
        + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, refModelName);
  }

  private static void accumulateImport(List<Imports> importsAccumulator, String refModelName) {
    var importObj = new Imports();
    importObj.setName(refModelName);
    importObj.setPackageName(toModelPackage(refModelName));
    importsAccumulator.add(importObj);
  }

  @lombok.Data
  private static class Imports {
    private String name;
    private String packageName;

    @SuppressWarnings("unused")
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
