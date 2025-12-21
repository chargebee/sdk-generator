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
  // Constants
  // ---------------------------------------------------------------------------------------------
  private static final String QUERY = "query";
  private static final String OBJECT = "object";
  private static final String SORT_BY = "sort_by";
  private static final String ASC = "asc";
  private static final String DESC = "desc";

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
    this.outputDirectoryPath = outputDirectoryPath + "/com/chargebee/v4/models";
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  /** Sets the Handlebars template used to render parameter classes. */
  public GetRequestParamsBuilder withTemplate(Template template) {
    this.template = template;
    return this;
  }

  /** Builds all GET request param classes and returns pending file operations. */
  public List<FileOp> build(OpenAPI openApi) throws IOException {
    this.openApi = openApi;
    generateParams();
    return fileOps;
  }

  // ---------------------------------------------------------------------------------------------
  // Core generation flow
  // ---------------------------------------------------------------------------------------------
  private void generateParams() throws IOException {
    var operations = getOperations();
    for (var entry : operations.entrySet()) {
      PathItem pathItem = entry.getValue();
      if (pathItem.getGet() == null) continue;

      var operation = pathItem.getGet();
      if (operation.getParameters() == null || operation.getParameters().isEmpty()) continue;

      var getAction = new GetAction();
      var module = readExtension(operation, Extension.RESOURCE_ID);

      // Skip operations without required extensions
      if (module == null) continue;

      var operationId = readExtension(operation, Extension.SDK_METHOD_NAME);

      getAction.setOperationId(operationId);
      getAction.setModule(module);
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
  }

  // ---------------------------------------------------------------------------------------------
  // Extraction: fields, enums, sort, submodels
  // ---------------------------------------------------------------------------------------------
  private List<Field> getFilterFields(Operation operation) {
    var fields = new ArrayList<Field>();
    for (Parameter param : operation.getParameters()) {
      if (isQueryParamWithSchema(param)) {
        var field = new Field();
        field.setName(param.getName());
        field.setType(TypeMapper.getJavaType(param.getName(), param.getSchema()));
        field.setDeprecated(param.getDeprecated() != null && param.getDeprecated());

        @SuppressWarnings("unchecked")
        Schema<Object> schema = (Schema<Object>) param.getSchema();
        if (OBJECT.equals(schema.getType()) && schema.getProperties() != null) {
          if (isSortParameter(param.getName(), schema)) {
            field.setSort(true);
            field.setSortableFields(getSortableFields(schema));
          } else if (isDirectFilterParameter(schema)) {
            field.setFilter(true);
            String filterTypeName =
                CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, param.getName()) + "Filter";
            field.setFilterType(filterTypeName);
            field.setSupportedOperations(getSupportedOperations(schema));
          } else {
            field.setSubModelField(true);
          }
        }

        fields.add(field);
      }
    }
    return fields;
  }

  private List<String> getSupportedOperations(Schema<Object> schema) {
    return new ArrayList<>(schema.getProperties().keySet());
  }

  private boolean isDirectFilterParameter(Schema<Object> schema) {
    var props = schema.getProperties();
    if (props == null || props.isEmpty()) {
      return false;
    }
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
            ASC,
            DESC);

    for (String propName : props.keySet()) {
      if (filterOperations.contains(propName)) {
        return true;
      }
    }

    return false;
  }

  private boolean isSortParameter(String paramName, Schema<Object> schema) {
    return SORT_BY.equals(paramName)
        && (schema.getProperties().containsKey(ASC) || schema.getProperties().containsKey(DESC));
  }

  private List<String> getSortableFields(Schema<Object> schema) {
    var sortableFields = new ArrayList<String>();
    var props = schema.getProperties();

    for (var entry : props.entrySet()) {
      if (ASC.equals(entry.getKey()) || DESC.equals(entry.getKey())) {
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
    for (Parameter param : operation.getParameters()) {
      if (isQueryParamWithSchema(param)) {
        @SuppressWarnings("unchecked")
        Schema<Object> schema = (Schema<Object>) param.getSchema();

        if (schema.getEnum() != null) {
          var enumField = new EnumFields();
          enumField.setName(param.getName());
          List<String> enumValues =
              schema.getEnum().stream()
                  .map(Object::toString)
                  .collect(java.util.stream.Collectors.toList());
          enumField.setEnums(enumValues);
          enumFields.add(enumField);
        } else if (OBJECT.equals(schema.getType()) && schema.getProperties() != null) {
          for (var prop : schema.getProperties().entrySet()) {
            @SuppressWarnings("unchecked")
            Schema<Object> propSchema = (Schema<Object>) prop.getValue();
            if (propSchema.getEnum() != null) {
              var enumField = new EnumFields();
              enumField.setName(param.getName() + "_" + prop.getKey());
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
    for (Parameter param : operation.getParameters()) {
      if (isQueryParamWithSchema(param)) {
        @SuppressWarnings("unchecked")
        Schema<Object> schema = (Schema<Object>) param.getSchema();
        if (OBJECT.equals(schema.getType()) && schema.getProperties() != null) {
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

    for (var entry : schema.getProperties().entrySet()) {
      var field = new Field();
      field.setName(entry.getKey());
      @SuppressWarnings("unchecked")
      Schema<Object> propSchema = (Schema<Object>) entry.getValue();
      field.setType(TypeMapper.getJavaType(entry.getKey(), propSchema));
      field.setDeprecated(propSchema.getDeprecated() != null && propSchema.getDeprecated());

      if (OBJECT.equals(propSchema.getType())) {
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

    for (var entry : schema.getProperties().entrySet()) {
      @SuppressWarnings("unchecked")
      Schema<Object> propSchema = (Schema<Object>) entry.getValue();
      if (propSchema.getEnum() != null) {
        var enumField = new EnumFields();
        enumField.setName(entry.getKey());
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
        .filter(entry -> entry.getValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private String readExtension(Operation operation, String extensionKey) {
    var extensions = operation.getExtensions();
    return (extensions != null && extensions.get(extensionKey) != null)
        ? Objects.toString(extensions.get(extensionKey))
        : null;
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
      var operationIdSnake =
          CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, getOperationId());
      var moduleSnake =
          module.contains("_")
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
