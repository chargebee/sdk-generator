package com.chargebee.sdk.java.javanext.datatype;

import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.Schema;
import org.jetbrains.annotations.NotNull;

public record ObjectType(String fieldName, Schema schema) implements FieldType {
  @Override
  public String display() {
    if (schema != null && (schema.getProperties() == null || schema.getProperties().isEmpty())) {
      // Check if this is a free-form object with additionalProperties
      if (schema.getAdditionalProperties() != null) {
        // additionalProperties can be Boolean or Schema
        Object additionalProps = schema.getAdditionalProperties();
        if (additionalProps instanceof Boolean && (Boolean) additionalProps) {
          return "java.util.Map<String, Object>";
        }
        if (additionalProps instanceof Schema) {
          return "java.util.Map<String, Object>";
        }
      }
      return "Object";
    }
    String name = fieldName == null ? "" : fieldName;
    if (name.indexOf('_') >= 0) {
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
    }
    if (name.indexOf('-') >= 0) {
      return CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, name);
    }
    // Default to converting from lowerCamel to UpperCamel
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, name);
  }

  @Override
  public @NotNull String toString() {
    return display();
  }
}
