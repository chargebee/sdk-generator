package com.chargebee.sdk.java.javanext.builder;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.javanext.JavaFormatter;
import com.chargebee.sdk.java.javanext.core.EnumFields;
import com.chargebee.sdk.java.javanext.core.Field;
import com.chargebee.sdk.java.javanext.core.Model;
import com.chargebee.sdk.java.javanext.core.TypeMapper;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Builds type-safe Java parameter models for GET operations from an OpenAPI spec.
 *
 * <p>Responsibilities:
 * - Traverse OpenAPI paths and GET operations
 * - Extract query parameters and classify them as filters, sorters, enums or nested sub-models
 * - Render Handlebars template into concrete Java classes under core/models/<module>/params
 */
public class GetRequestParamsBuilder {

  // ---------------------------------------------------------------------------------------------
  // Constants & Logger
  // ---------------------------------------------------------------------------------------------
  private static final Logger LOGGER = Logger.getLogger(GetRequestParamsBuilder.class.getName());
  private static final String QUERY = "query";
  private static final String OBJECT = "object";
  private static final String SORT_BY = "sort_by";

  private Template template;
  private String outputDirectoryPath;
  private OpenAPI openApi;

  private final List<FileOp> fileOps = new ArrayList<>();

  // ---------------------------------------------------------------------------------------------
  // Fluent configuration API
  // ---------------------------------------------------------------------------------------------
  /**
   * Sets the output base directory and ensures the base folder exists.
   * Output structure: <base>/core/models/<module>/params
   */
  public GetRequestParamsBuilder withOutputDirectoryPath(String outputDirectoryPath) {
    this.outputDirectoryPath = outputDirectoryPath + "/v4/core/models";
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  /** Sets the Handlebars template used to render parameter classes. */
  public GetRequestParamsBuilder withTemplate(Template template) {
    this.template = template;
    return this;
  }

  /** Builds all GET request param classes and returns pending file operations. */
  public List<FileOp> build(OpenAPI openApi) {
    this.openApi = openApi;
    generateParams();
    return fileOps;
  }

  // ---------------------------------------------------------------------------------------------
  // Core generation flow
  // ---------------------------------------------------------------------------------------------
  private void generateParams() {
    try {
      var operations = getOperations();
      for (var entry : operations.entrySet()) {
        PathItem pathItem = entry.getValue();
        if (pathItem == null || pathItem.getGet() == null) continue;

        var operation = pathItem.getGet();
        if (operation.getParameters() == null || operation.getParameters().isEmpty()) continue;

        var getAction = new GetAction();
        getAction.setOperationId(readExtension(operation, Extension.OPERATION_METHOD_NAME));
        getAction.setModule(readExtension(operation, Extension.RESOURCE_ID));
        getAction.setPath(entry.getKey());
        getAction.setFields(getFilterFields(operation));
        getAction.setEnumFields(getEnumFields(operation));
        getAction.setSubModels(getSubModels(operation));

        var content = template.apply(getAction);
        var formattedContent = JavaFormatter.formatSafely(content);
        fileOps.add(
            new FileOp.CreateDirectory(
                this.outputDirectoryPath + "/" + getAction.getModule(), "params"));

        fileOps.add(
            new FileOp.WriteString(
                this.outputDirectoryPath + "/" + getAction.getModule() + "/params",
                getAction.getName() + "Params.java",
                formattedContent));
      }
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Error generating GET params", e);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Extraction: fields, enums, sort, submodels
  // ---------------------------------------------------------------------------------------------
  private List<Field> getFilterFields(Operation operation) {
    var fields = new ArrayList<Field>();
    if (operation.getParameters() == null) return fields;

    for (Parameter param : operation.getParameters()) {
      if (isQueryParamWithSchema(param)) {
        var field = new Field();
        field.setName(param.getName());
        field.setType(TypeMapper.getJavaType(param.getName(), param.getSchema()));
        field.setDeprecated(param.getDeprecated() != null && param.getDeprecated());

        // Check if this is a filter parameter, sort parameter, or submodel
        @SuppressWarnings("unchecked")
        Schema<Object> schema = (Schema<Object>) param.getSchema();
        if (OBJECT.equals(schema.getType()) && schema.getProperties() != null) {
          if (isSortParameter(param.getName(), schema)) {
            // Sort parameter (like sort_by)
            field.setSort(true);
            field.setSortableFields(getSortableFields(schema));
          } else if (isDirectFilterParameter(schema)) {
            // Direct filter parameter (like id, email)
            field.setFilter(true);
            String filterTypeName =
                CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, param.getName()) + "Filter";
            field.setFilterType(filterTypeName);
            field.setSupportedOperations(getSupportedOperations(schema));
          } else {
            // Submodel parameter (like relationship) - will be handled separately by getSubModels()
            field.setSubModelField(true);
          }
        }

        fields.add(field);
      }
    }
    return fields;
  }

  private List<String> getSupportedOperations(Schema<Object> schema) {
    var props = schema.getProperties();
    if (props == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(props.keySet());
  }

  private boolean isDirectFilterParameter(Schema<Object> schema) {
    var props = schema.getProperties();
    if (props == null || props.isEmpty()) {
      return false;
    }

    // Check if the schema has filter operation properties directly
    // Filter operations include: is, is_not, starts_with, in, not_in, is_present, after, before,
    // on, between, asc, desc
    var filterOperations =
        Set.of(
            "is",
            "is_not",
            "starts_with",
            "in",
            "not_in",
            "is_present",
            "after",
            "before",
            "on",
            "between",
            "asc",
            "desc");

    // If any property is a filter operation, this is a direct filter
    for (String propName : props.keySet()) {
      if (filterOperations.contains(propName)) {
        return true;
      }
    }

    // Otherwise, it's a submodel (properties are business fields, not filter operations)
    return false;
  }

  private boolean isSortParameter(String paramName, Schema<Object> schema) {
    // Check if this is a sort_by parameter
    return SORT_BY.equals(paramName)
        && schema.getProperties() != null
        && (schema.getProperties().containsKey("asc")
            || schema.getProperties().containsKey("desc"));
  }

  private List<String> getSortableFields(Schema<Object> schema) {
    var sortableFields = new ArrayList<String>();
    var props = schema.getProperties();
    if (props == null) {
      return sortableFields;
    }

    // For sort_by parameters, look for asc/desc properties that contain enum values
    // The enum values are the sortable field names
    for (var entry : props.entrySet()) {
      if ("asc".equals(entry.getKey()) || "desc".equals(entry.getKey())) {
        @SuppressWarnings("unchecked")
        Schema<Object> directionSchema = (Schema<Object>) entry.getValue();
        if (directionSchema.getEnum() != null) {
          for (Object enumValue : directionSchema.getEnum()) {
            String fieldName = enumValue.toString();
            if (!sortableFields.contains(fieldName)) {
              sortableFields.add(fieldName);
            }
          }
        }
      }
    }

    return sortableFields;
  }

  private List<EnumFields> getEnumFields(Operation operation) {
    var enumFields = new ArrayList<EnumFields>();
    if (operation.getParameters() == null) return enumFields;

    for (Parameter param : operation.getParameters()) {
      if (isQueryParamWithSchema(param)) {
        @SuppressWarnings("unchecked")
        Schema<Object> schema = (Schema<Object>) param.getSchema();

        // Check for direct enum parameters (like hierarchy_operation_type)
        if (schema.getEnum() != null) {
          var enumField = new EnumFields();
          enumField.setName(param.getName());
          // Convert enum values to strings
          List<String> enumValues =
              schema.getEnum().stream()
                  .map(Object::toString)
                  .collect(java.util.stream.Collectors.toList());
          enumField.setEnums(enumValues);
          enumFields.add(enumField);
        }
        // Also check for enums inside object properties (existing logic)
        else if (OBJECT.equals(schema.getType()) && schema.getProperties() != null) {
          // Check if any property in the filter object has enums
          for (var prop : schema.getProperties().entrySet()) {
            @SuppressWarnings("unchecked")
            Schema<Object> propSchema = (Schema<Object>) prop.getValue();
            if (propSchema.getEnum() != null) {
              var enumField = new EnumFields();
              enumField.setName(param.getName() + "_" + prop.getKey());
              // Convert enum values to strings
              List<String> enumValues =
                  propSchema.getEnum().stream()
                      .map(Object::toString)
                      .collect(java.util.stream.Collectors.toList());
              enumField.setEnums(enumValues);
              enumFields.add(enumField);
            }
          }
        }
      }
    }
    return enumFields;
  }

  private List<Model> getSubModels(Operation operation) {
    var subModels = new ArrayList<Model>();
    if (operation.getParameters() == null) return subModels;

    for (Parameter param : operation.getParameters()) {
      if (isQueryParamWithSchema(param)) {
        @SuppressWarnings("unchecked")
        Schema<Object> schema = (Schema<Object>) param.getSchema();
        if (OBJECT.equals(schema.getType()) && schema.getProperties() != null) {
          // Only create submodels for non-direct filter parameters (like relationship)
          if (!isDirectFilterParameter(schema)) {
            subModels.add(createFilterModel(param.getName(), schema));
          }
        }
      }
    }
    return subModels;
  }

  private Model createFilterModel(String fieldName, Schema<Object> schema) {
    var subModel = new Model();
    subModel.setPackageName(fieldName);
    subModel.setName(fieldName);
    subModel.setFields(getFilterFieldsFromSchema(schema));
    subModel.setEnumFields(getEnumFieldsFromSchema(schema));
    return subModel;
  }

  private List<Field> getFilterFieldsFromSchema(Schema<Object> schema) {
    var fields = new ArrayList<Field>();
    if (schema.getProperties() == null) return fields;

    for (var entry : schema.getProperties().entrySet()) {
      var field = new Field();
      field.setName(entry.getKey());
      @SuppressWarnings("unchecked")
      Schema<Object> propSchema = (Schema<Object>) entry.getValue();
      field.setType(TypeMapper.getJavaType(entry.getKey(), propSchema));
      field.setDeprecated(propSchema.getDeprecated() != null && propSchema.getDeprecated());

      // Check if this nested property is a filter (has filter operations)
      if (OBJECT.equals(propSchema.getType()) && propSchema.getProperties() != null) {
        if (isDirectFilterParameter(propSchema)) {
          field.setFilter(true);
          String filterTypeName =
              CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, entry.getKey()) + "Filter";
          field.setFilterType(filterTypeName);
          field.setSupportedOperations(getSupportedOperations(propSchema));
        }
      }

      fields.add(field);
    }
    return fields;
  }

  private List<EnumFields> getEnumFieldsFromSchema(Schema<Object> schema) {
    var enumFields = new ArrayList<EnumFields>();
    if (schema.getProperties() == null) return enumFields;

    for (var entry : schema.getProperties().entrySet()) {
      @SuppressWarnings("unchecked")
      Schema<Object> propSchema = (Schema<Object>) entry.getValue();
      if (propSchema.getEnum() != null) {
        var enumField = new EnumFields();
        enumField.setName(entry.getKey());
        // Convert enum values to strings
        List<String> enumValues =
            propSchema.getEnum().stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toList());
        enumField.setEnums(enumValues);
        enumFields.add(enumField);
      }
    }
    return enumFields;
  }

  // ---------------------------------------------------------------------------------------------
  // OpenAPI helpers
  // ---------------------------------------------------------------------------------------------
  private Map<String, PathItem> getOperations() {
    var paths = openApi != null ? openApi.getPaths() : null;
    if (paths == null || paths.isEmpty()) return Collections.emptyMap();
    return paths.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private String readExtension(Operation operation, String extensionKey) {
    if (operation == null || operation.getExtensions() == null) return null;
    Object value = operation.getExtensions().get(extensionKey);
    return value != null ? Objects.toString(value) : null;
  }

  private boolean isQueryParamWithSchema(Parameter parameter) {
    return parameter != null && QUERY.equals(parameter.getIn()) && parameter.getSchema() != null;
  }

  @lombok.Data
  private static class GetAction {
    private String operationId;
    private String module;
    private String path;
    private List<Field> fields;
    private List<EnumFields> enumFields;
    private List<Model> subModels;

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
