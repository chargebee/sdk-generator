package com.chargebee.sdk.java.javanext.core;

import static com.chargebee.openapi.Extension.IS_MONEY_COLUMN;

import com.chargebee.sdk.java.javanext.datatype.*;
import io.swagger.v3.oas.models.media.*;

public class TypeMapper {

  public static FieldType getJavaType(String fieldName, Schema<?> schema) {
    // Handle null schema
    if (schema == null) {
      return new ObjectType(fieldName, null);
    }

    // Handle direct $ref to another schema as typed object
    if (schema.get$ref() != null) {
      String refName = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
      String lowerUnderscore =
          com.google.common.base.CaseFormat.UPPER_CAMEL.to(
              com.google.common.base.CaseFormat.LOWER_UNDERSCORE, refName);
      return new ObjectType(lowerUnderscore, null);
    }

    FieldType fieldType = primitiveType(fieldName, schema);
    if (fieldType == null) {
      fieldType = complexType(fieldName, schema);
    }

    // If both primitive and complex type return null, use Object as fallback
    if (fieldType == null) {
      System.err.println(
          "Warning: Unable to determine type for field '"
              + fieldName
              + "', schema type: "
              + (schema.getType() != null ? schema.getType() : "null")
              + ". Using Object as fallback.");
      return new ObjectType(fieldName, schema);
    }

    return fieldType;
  }

  private static FieldType primitiveType(String fieldName, Schema<?> schema) {
    if (schema == null || schema.getType() == null) return null;

    // Handle enums
    if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
      return new EnumType(fieldName);
    }

    if (schema instanceof BooleanSchema) {
      return new BooleanType();
    }

    switch (schema.getType()) {
      case "string":
        return new StringType();
      case "boolean":
        return new BooleanType();
      case "integer":
        String fmt = schema.getFormat();
        if ("unix-time".equals(fmt)) return new TimestampType();
        if ("int64".equals(fmt)) return new LongType();
        if ("decimal".equals(fmt)) return new BigDecimalType();
        // Check for money column extension - money columns should be Long
        if (isMoneyColumn(schema)) return new LongType();
        return new IntegerType();
      case "number":
        String numFmt = schema.getFormat();
        if ("decimal".equals(numFmt)) return new BigDecimalType();
        if ("double".equals(numFmt)) return new DoubleType();
        return new NumberType();
      default:
        return null;
    }
  }

  private static FieldType complexType(String fieldName, Schema<?> schema) {
    if (schema == null || schema.getType() == null) return null;

    switch (schema.getType()) {
      case "object":
        return new ObjectType(fieldName, schema);

      case "array":
        return new ListType(fieldName, schema);

      default:
        return null;
    }
  }

  /**
   * Check if the schema has the x-cb-is-money-column extension set to true.
   * Money columns should be represented as Long in Java.
   */
  private static boolean isMoneyColumn(Schema<?> schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_MONEY_COLUMN) != null
        && (boolean) schema.getExtensions().get(IS_MONEY_COLUMN);
  }
}
