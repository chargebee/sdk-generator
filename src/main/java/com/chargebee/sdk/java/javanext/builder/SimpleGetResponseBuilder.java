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
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
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
  private static final String CORE_MODELS_PACKAGE_PREFIX = "com.chargebee.v4.models.";

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
    MethodNameDeriver.initialize(openApi);
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

        var moduleExt = extensions != null ? extensions.get(Extension.RESOURCE_ID) : null;
        var module = moduleExt != null ? moduleExt.toString() : null;
        if (module == null) {
          continue; // Missing required extensions; skip gracefully
        }
        // Derive method name from path using common utility
        var methodName = MethodNameDeriver.deriveMethodName(pathEntry.getKey(), "GET", operation);
        methodName = MethodNameDeriver.applyBatchPrefix(pathEntry.getKey(), methodName);

        var responses = operation.getResponses();
        if (responses == null) continue;
        var response = responses.get(RESPONSE_CODE_OK);
        if (response == null || response.getContent() == null) continue;

        var jsonContent = response.getContent().get(CONTENT_TYPE_JSON);
        if (jsonContent == null) continue;

        var schema = jsonContent.getSchema();
        // Resolve top-level $ref and unwrap composed schemas (allOf/anyOf/oneOf) to access
        // properties
        schema = resolveEffectiveSchema(schema);
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
      var moduleDir = outputDirectoryPath + "/" + simpleGetResponse.getModule();
      var responsesDir = moduleDir + "/responses";
      fileOps.add(new FileOp.CreateDirectory(moduleDir, ""));
      fileOps.add(new FileOp.CreateDirectory(responsesDir, ""));
      fileOps.add(new FileOp.WriteString(responsesDir, fileName, formattedContent));
    } catch (IOException e) {
      throw new RuntimeException("Failed to generate simple get response file", e);
    }
  }

  // -----------------------------------------------------------------------------
  // Section: Schema Parsing
  // -----------------------------------------------------------------------------
  private List<Model> getSubModels(
      Schema<?> schema, String module, List<Imports> importsAccumulator) {
    schema = resolveEffectiveSchema(schema);
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
    schema = resolveEffectiveSchema(schema);
    var fields = new ArrayList<Field>();
    var requiredFields =
        schema != null && schema.getRequired() != null
            ? new java.util.HashSet<>(schema.getRequired())
            : new java.util.HashSet<String>();
    if (schema == null || schema.getProperties() == null || schema.getProperties().isEmpty()) {
      // Fallback: some external specs omit properties but mark required ["<resource>"]
      // Infer a single field named after the resource id and reference the corresponding model.
      if (openApi != null
          && openApi.getComponents() != null
          && openApi.getComponents().getSchemas() != null
          && module != null
          && !module.isEmpty()) {
        String refModelName =
            com.google.common.base.CaseFormat.LOWER_UNDERSCORE.to(
                com.google.common.base.CaseFormat.UPPER_CAMEL, module);
        if (openApi.getComponents().getSchemas().containsKey(refModelName)) {
          // Create a $ref schema to drive typing/imports
          io.swagger.v3.oas.models.media.Schema<?> refSchema =
              new io.swagger.v3.oas.models.media.Schema<>()
                  .$ref("#/components/schemas/" + refModelName);

          var field = new Field();
          field.setName(module); // e.g., "full_export" -> getter getFullExport()
          field.setType(TypeMapper.getJavaType(module, refSchema));
          accumulateImport(importsAccumulator, refModelName);
          fields.add(field);
          return fields;
        }
      }
      // Second fallback: use required property names as Object fields
      if (schema != null && schema.getRequired() != null && !schema.getRequired().isEmpty()) {
        for (String prop : schema.getRequired()) {
          var valueSchema = new io.swagger.v3.oas.models.media.Schema<>().type("object");
          var field = new Field();
          field.setName(prop);
          field.setType(TypeMapper.getJavaType(prop, valueSchema));
          fields.add(field);
        }
        return fields;
      }
      // Final fallback: synthesize a single object field using the resource id
      if (module != null && !module.isEmpty()) {
        var valueSchema = new io.swagger.v3.oas.models.media.Schema<>().type("object");
        var field = new Field();
        field.setName(module);
        field.setType(TypeMapper.getJavaType(module, valueSchema));
        fields.add(field);
        return fields;
      }
      return fields;
    }
    for (var key : schema.getProperties().keySet()) {
      var fieldName = key.toString();
      var fieldSchema = (Schema<?>) schema.getProperties().get(fieldName);
      var fieldType = TypeMapper.getJavaType(fieldName, fieldSchema);
      var field = new Field();
      field.setName(fieldName);
      field.setRequired(requiredFields.contains(fieldName));
      if (fieldType instanceof ListType) {
        field.setName(fieldName);
        // Check if the list items have a $ref
        var itemsSchema = fieldSchema.getItems();
        if (itemsSchema != null && itemsSchema.get$ref() != null) {
          var refModelName = lastSegmentOfRef(itemsSchema.get$ref());
          accumulateImport(importsAccumulator, refModelName);
          // fieldType is already correctly set by TypeMapper
        } else if (itemsSchema != null
            && itemsSchema.getType() != null
            && "object".equals(itemsSchema.getType())
            && itemsSchema.getProperties() != null
            && !itemsSchema.getProperties().isEmpty()) {
          // For inline object definitions with properties, use the old logic
          fieldType =
              TypeMapper.getJavaType(module + "_" + fieldName + "_Item", (Schema<?>) itemsSchema);
        }
        // Otherwise, keep the ListType as-is (it will be List<Object> for arrays with no item
        // schema)
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
    schema = resolveEffectiveSchema(schema);
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

  // Resolve schema to an object schema with accessible properties:
  // - Follow top-level $ref into components
  // - Flatten composed schemas (allOf) by merging properties
  private Schema<?> resolveEffectiveSchema(Schema<?> schema) {
    if (schema == null) return null;

    // Follow $ref chain
    Schema<?> current = schema;
    int guard = 0;
    while (current.get$ref() != null && openApi != null && openApi.getComponents() != null) {
      String refName = lastSegmentOfRef(current.get$ref());
      Schema<?> target =
          openApi.getComponents().getSchemas() != null
              ? openApi.getComponents().getSchemas().get(refName)
              : null;
      if (target == null || target == current) break;
      current = target;
      if (++guard > 50) break; // guard against cycles
    }

    // Unwrap composed schemas (prefer allOf aggregation)
    if (current instanceof ComposedSchema composed) {
      Map<String, Schema<?>> mergedProps = new java.util.LinkedHashMap<>();
      if (composed.getAllOf() != null) {
        for (Schema<?> part : composed.getAllOf()) {
          Schema<?> resolvedPart = resolveEffectiveSchema(part);
          if (resolvedPart != null && resolvedPart.getProperties() != null) {
            for (var e : resolvedPart.getProperties().entrySet()) {
              mergedProps.putIfAbsent(e.getKey(), (Schema<?>) e.getValue());
            }
          }
        }
      }
      if (mergedProps.isEmpty() && current.getProperties() != null) {
        for (var e : current.getProperties().entrySet()) {
          mergedProps.putIfAbsent(e.getKey(), (Schema<?>) e.getValue());
        }
      }
      ObjectSchema aggregated = new ObjectSchema();
      // Cast needed due to OpenAPI generics; safe because keys and values are Schema<?>
      @SuppressWarnings("unchecked")
      Map<String, Schema<?>> castProps = (Map<String, Schema<?>>) (Map) mergedProps;
      // setProperties expects Map<String, Schema> (raw), cast to that type for compatibility
      @SuppressWarnings("unchecked")
      Map<String, Schema> rawProps = (Map<String, Schema>) (Map) castProps;
      aggregated.setProperties(rawProps);
      return aggregated;
    }

    return current;
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
      var operationIdSnake = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
      // Normalize module to snake_case to preserve token boundaries (handles lowerCamel inputs)
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
