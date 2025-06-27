package com.chargebee.sdk.php.v4;

import static com.chargebee.openapi.Resource.isSubResourceSchema;
import static com.chargebee.sdk.php.v4.ActionParser.isCompositeArrayBody;
import static com.chargebee.sdk.php.v4.Common.dataType;

import com.chargebee.openapi.*;
import com.chargebee.openapi.parameter.Parameter;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.List;

public class PHPDocSerializer {
  private static final String INDENT = "    ";
  private static final String ASTERISK = "    * ";

  public static String serializeActionParameter(Action action) {
    List<Parameter> allParams = new ArrayList<>();
    allParams.addAll(action.queryParameters());
    allParams.addAll(action.requestBodyParameters());

    StringBuilder docBuilder = new StringBuilder("@param array{" + System.lineSeparator());
    docBuilder.append(ASTERISK).append(INDENT);

    allParams.stream()
        .filter(param -> !param.isHiddenFromSDK())
        .forEach(param -> parseParameter(param.schema, docBuilder, 1, param.getName()));

    docBuilder.append("} $params Description of the parameters");
    return docBuilder.toString();
  }

  private static void parseParameter(
      Schema<?> schema, StringBuilder builder, int indentLevel, String name) {
    String paramName = name;

    if (isCompositeArrayBody(schema)) {
      parseCompositeArray(schema, builder, paramName);
      return;
    }

    if (schema instanceof ObjectSchema && isSubResourceSchema(schema)) {
      parseObjectSchema((ObjectSchema) schema, builder, indentLevel, paramName);
      return;
    }

    if (schema instanceof ObjectSchema) {
      String filter = new Attribute(schema.getName(), schema, false).getFilterType();
      if (filter != null) {
        parseFilterSchema((ObjectSchema) schema, builder, indentLevel, paramName);
        return;
      }
      parseObjectSchema((ObjectSchema) schema, builder, indentLevel, paramName);
      return;
    }

    if (schema instanceof ArraySchema) {
      parseArraySchema((ArraySchema) schema, builder, indentLevel, paramName);
      return;
    }

    appendSimpleParameter(builder, paramName, schema);
  }

  private static void parseObjectSchema(
      ObjectSchema schema, StringBuilder builder, int indentLevel, String paramName) {
    String filter = new Attribute(schema.getName(), schema, false).getFilterType();
    if (filter == null) {
      builder
          .append(paramName)
          .append("?: array{")
          .append(System.lineSeparator())
          .append(ASTERISK)
          .append(INDENT.repeat(indentLevel));
    }
    schema
        .getProperties()
        .forEach(
            (key, value) -> {
              String propertyName = key;
              if (value instanceof ArraySchema) {
                builder
                    .append(propertyName)
                    .append("?: array<")
                    .append(dataType(value.getItems()))
                    .append(">,")
                    .append(System.lineSeparator())
                    .append(ASTERISK)
                    .append(INDENT.repeat(indentLevel));
              } else {
                parseSchemaProperty(value, builder, indentLevel + 1, propertyName);
              }
            });
    if (filter == null) {
      builder
          .append("},")
          .append(System.lineSeparator())
          .append(ASTERISK)
          .append(INDENT.repeat(indentLevel - 1));
    }
  }

  private static void parseFilterSchema(
      ObjectSchema schema, StringBuilder builder, int indentLevel, String paramName) {
    builder
        .append(paramName)
        .append("?: array{")
        .append(System.lineSeparator())
        .append(ASTERISK)
        .append(INDENT.repeat(indentLevel));

    schema
        .getProperties()
        .forEach(
            (key, value) -> {
              String propertyName = key;
              builder.append(propertyName).append("?: ");
              builder
                  .append("mixed")
                  .append(",")
                  .append(System.lineSeparator())
                  .append(ASTERISK)
                  .append(INDENT.repeat(indentLevel));
            });

    builder
        .append("},")
        .append(System.lineSeparator())
        .append(ASTERISK)
        .append(INDENT.repeat(indentLevel - 1));
  }

  private static void parseSchemaProperty(
      Schema<?> schema, StringBuilder builder, int indentLevel, String propertyName) {
    builder.append(propertyName).append("?: ");

    if (schema instanceof ObjectSchema && isSubResourceSchema(schema)) {
      builder
          .append("array{")
          .append(System.lineSeparator())
          .append(ASTERISK)
          .append(INDENT.repeat(indentLevel));
      parseParameter(schema, builder, indentLevel + 1, propertyName);
      builder
          .append("},")
          .append(System.lineSeparator())
          .append(ASTERISK)
          .append(INDENT.repeat(indentLevel - 1));
    } else if (schema instanceof ArraySchema) {
      Schema<?> itemSchema = schema.getItems();
      builder
          .append("array<")
          .append(dataType(itemSchema))
          .append(">,")
          .append(System.lineSeparator())
          .append(ASTERISK)
          .append(INDENT.repeat(indentLevel - 1));
    } else {
      builder
          .append(dataType(schema))
          .append(",")
          .append(System.lineSeparator())
          .append(ASTERISK)
          .append(INDENT.repeat(indentLevel - 1));
    }
  }

  private static void parseArraySchema(
      ArraySchema schema, StringBuilder builder, int indentLevel, String paramName) {
    Schema<?> itemSchema = schema.getItems();
    if (itemSchema == null) {
      appendSimpleParameter(builder, paramName, schema);
      return;
    }

    builder.append(paramName).append("?: ");

    if (itemSchema instanceof ObjectSchema && isSubResourceSchema(itemSchema)) {
      builder
          .append("array<array{")
          .append(System.lineSeparator())
          .append(ASTERISK)
          .append(INDENT.repeat(indentLevel));
      parseParameter(itemSchema, builder, indentLevel + 1, "");
      builder
          .append("}>,")
          .append(System.lineSeparator())
          .append(ASTERISK)
          .append(INDENT.repeat(indentLevel - 1));
    } else {
      builder
          .append("array<")
          .append(dataType(itemSchema))
          .append(">,")
          .append(System.lineSeparator())
          .append(ASTERISK)
          .append(INDENT.repeat(indentLevel - 1));
    }
  }

  private static void parseCompositeArray(
      Schema<?> schema, StringBuilder builder, String paramName) {
    if (schema.getProperties().isEmpty()) {
      return;
    }

    builder
        .append(paramName)
        .append("?: array<array{")
        .append(System.lineSeparator())
        .append(ASTERISK)
        .append(INDENT);

    schema
        .getProperties()
        .forEach(
            (key, value) -> {
              builder
                  .append(key)
                  .append("?: ")
                  .append(dataType(value.getItems()))
                  .append(",")
                  .append(System.lineSeparator())
                  .append(ASTERISK)
                  .append(INDENT);
            });

    builder.append("}>,").append(System.lineSeparator()).append(ASTERISK).append(INDENT);
  }

  private static void appendSimpleParameter(StringBuilder builder, String name, Schema<?> schema) {
    builder
        .append(name)
        .append("?: ")
        .append(dataType(schema))
        .append(",")
        .append(System.lineSeparator())
        .append(ASTERISK)
        .append(INDENT);
  }
}
