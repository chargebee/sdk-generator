package com.chargebee.openapi;

import com.chargebee.sdk.DataType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Enum {

  public final String name;
  private final Schema<?> schema;

  public Enum(Schema<?> schema) {
    this("", schema);
  }

  public Enum(String name, Schema<?> schema) {
    this.name = name;
    this.schema = schema;
  }

  public static List<Enum> globalEnums(OpenAPI openAPI) {
    if (openAPI.getComponents().getSchemas() == null) {
      return List.of();
    }
    return openAPI.getComponents().getSchemas().entrySet().stream()
        .filter(ee -> ee.getValue() instanceof StringSchema && ee.getValue().getEnum() != null)
        .map(ee -> new Enum(ee.getKey(), ee.getValue()))
        .toList();
  }

  public Map<String, Object> templateParams(DataType lang) {
    return Map.of("name", name, "enumDefinition", lang.dataType(schema));
  }

  public Optional<String> globalEnumReference() {
    if (schema.getExtensions() == null
        || schema.getExtensions().get("x-cb-global-enum-reference") == null) {
      return Optional.empty();
    }
    var reference = (String) schema.getExtensions().get("x-cb-global-enum-reference");
    String[] referenceTokens = reference.split("/");
    var referenceTypeName = referenceTokens[referenceTokens.length - 1].split("\\.")[0];
    return Optional.of(referenceTypeName);
  }

  public List<String> values() {
    if (schema instanceof StringSchema strSchema) {
      return strSchema.getEnum();
    }
    if (schema instanceof ArraySchema arraySchema
        && schema.getItems() != null
        && schema.getItems().getEnum() != null) {
      return arraySchema.getItems().getEnum().stream()
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .toList();
    }

    return List.of();
  }

  public List<String> validValues() {
    List<String> deprecatedValues = deprecatedValues();
    return values().stream().filter(value -> !deprecatedValues.contains(value)).toList();
  }

  public List<String> deprecatedValues() {
    if (schema.getExtensions() != null
        && schema.getExtensions().get("x-cb-deprecated-enum-values") != null) {
      return List.of(
          ((String) schema.getExtensions().get("x-cb-deprecated-enum-values")).split(","));
    }
    return List.of();
  }

  public Boolean isParamBlankOption() {
    return schema.getExtensions() != null
        && schema.getExtensions().get("x-cb-parameter-blank-option") != null
        && schema.getExtensions().get("x-cb-parameter-blank-option").equals("not_allowed");
  }
}
