package com.chargebee.sdk.php.v4;

import static com.chargebee.GenUtil.singularize;
import static com.chargebee.GenUtil.toCamelCase;
import static com.chargebee.openapi.Extension.SDK_ENUM_API_NAME;
import static com.chargebee.openapi.Extension.SUB_RESOURCE_NAME;
import static com.chargebee.openapi.Resource.*;
import static com.chargebee.sdk.php.v4.Constants.BACK_SLASH;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.sdk.php.v4.models.Column;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.*;

public class Common {

  public static final String CHARGEBEE_ENUMS_BASE_PATH = "\\Chargebee\\Enums\\";
  public static final String CHARGEBEE_RESOURCES_BASE_PATH = "\\Chargebee\\Resources\\";
  public static final String ENUMS_DIRECTORY = "Enums";

  public static String dataType(Schema schema) {
    if (schema instanceof StringSchema
        || schema instanceof EmailSchema
        || schema instanceof PasswordSchema) {
      return "string";
    }
    if (schema instanceof NumberSchema || schema instanceof IntegerSchema) {
      if (schema.getFormat().equals("decimal")) {
        return "float";
      }
      if (schema.getType().equals("number")) {
        if (schema.getFormat().equals("double")) {
          return "float";
        }
        if (schema.getFormat().equals("decimal")) {
          return "float";
        }
      }
      return "int";
    }
    if (schema instanceof BooleanSchema) {
      return "bool";
    }
    return "mixed";
  }

  public static String dataTypeForMultiLineAttributes(Attribute attribute) {
    if (attribute.schema instanceof ObjectSchema && isSubResourceSchema(attribute.schema)) {
      if (isGlobalResourceReference(attribute.schema)) {
        return getGlobalResourceReferenceName(attribute.schema); // resource.%s
      }
      return subResourceName(attribute.schema);
    }
    if (attribute.schema instanceof ArraySchema
        && attribute.schema.getItems() != null
        && attribute.schema.getItems().getType() != null) {
      if (attribute.schema.getItems() instanceof ObjectSchema
          && isSubResourceSchema(attribute.schema.getItems())) {
        if (isGlobalResourceReference(attribute.schema.getItems())) {
          return "array"; // Array<resources.%s>
        }
        return "array";
      }
      return "array";
    }
    return dataType(attribute.schema);
  }

  public static boolean isArrayOfSubResources(Attribute attribute) {
    return attribute.schema instanceof ArraySchema
        && attribute.schema.getItems() != null
        && attribute.schema.getItems().getType() != null
        && attribute.schema.getItems() instanceof ObjectSchema
        && isSubResourceSchema(attribute.schema.getItems());
  }

  public static String getSubResourceName(Attribute attribute) {
    if (attribute.schema instanceof ObjectSchema && isSubResourceSchema(attribute.schema)) {
      if (isGlobalResourceReference(attribute.schema)) {
        return getGlobalResourceReferenceName(attribute.schema);
      }
      return subResourceName(attribute.schema);
    }
    if (attribute.schema instanceof ArraySchema
        && attribute.schema.getItems() != null
        && attribute.schema.getItems().getType() != null) {
      if (attribute.schema.getItems() instanceof ObjectSchema
          && isSubResourceSchema(attribute.schema.getItems())) {
        if (isGlobalResourceReference(attribute.schema.getItems())) {
          return getGlobalResourceReferenceName(attribute.schema.getItems());
        }
        return getClazName(attribute);
      }
    }
    return null;
  }

  public static String PHPDocForAttribute(Attribute attribute) {
    if (attribute.schema instanceof ObjectSchema && isSubResourceSchema(attribute.schema)) {
      if (isGlobalResourceReference(attribute.schema)) {
        return getGlobalResourceReferenceName(attribute.schema); // resource.%s
      }
      return subResourceName(attribute.schema);
    }
    if (attribute.schema instanceof ArraySchema
        && attribute.schema.getItems() != null
        && attribute.schema.getItems().getType() != null) {
      if (attribute.schema.getItems() instanceof ObjectSchema
          && isSubResourceSchema(attribute.schema.getItems())) {
        if (isGlobalResourceReference(attribute.schema.getItems())) {
          return String.format(
              "array<%s>",
              getGlobalResourceReferenceName(attribute.schema.getItems())); // Array<resources.%s>
        }
        return String.format("array<%s>", getClazName(attribute));
      }
      return String.format("array<%s>", dataType(attribute.schema.getItems()));
    }
    return dataType(attribute.schema);
  }

  public static String getClazName(Attribute attribute) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, singularize(attribute.name));
  }

  public static String toPascalCase(String name) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name);
  }

  public static String subResourceName(Schema<?> schema) {
    if (schema instanceof ArraySchema) {
      return subResourceName(schema.getItems());
    }
    return (String) schema.getExtensions().get(SUB_RESOURCE_NAME);
  }

  public static Column columnParser(Attribute attribute) {
    Column column = new Column();
    column.setName(attribute.name);
    column.setFieldTypePHP(dataTypeForMultiLineAttributes(attribute));
    column.setPhpDocField(PHPDocForAttribute(attribute));
    column.setIsOptional(true);
    column.setSubResources(attribute.isSubResource());
    column.setApiName(attribute.name);
    column.setArrayOfSubResources(isArrayOfSubResources(attribute));
    column.setSubResourceName(getSubResourceName(attribute));
    return column;
  }

  public static Column globalEnumParser(Attribute attribute) {
    return createEnumColumn(attribute, CHARGEBEE_ENUMS_BASE_PATH);
  }

  public static Column listOfEnumParser(Attribute attribute) {
    String type = (String) attribute.schema.getItems().getExtensions().get(SDK_ENUM_API_NAME);
    return createEnumColumn(
        attribute, CHARGEBEE_ENUMS_BASE_PATH, String.format("%sType", singularize(type)));
  }

  public static Column localEnumParser(Attribute attribute, Resource res) {
    return createEnumColumn(
        attribute,
        CHARGEBEE_RESOURCES_BASE_PATH
            + toCamelCase(res.name)
            + BACK_SLASH
            + ENUMS_DIRECTORY
            + BACK_SLASH);
  }

  private static Column createEnumColumn(Attribute attribute, String basePath) {
    Column column = new Column();
    column.setName(attribute.name);
    column.setFieldTypePHP(basePath + toCamelCase(attribute.name));
    column.setPhpDocField(basePath + toCamelCase(attribute.name));
    column.setIsOptional(true);
    column.setApiName(attribute.name);
    return column;
  }

  private static Column createEnumColumn(
      Attribute attribute, String basePath, String fieldTypePHP) {
    Column column = new Column();
    column.setName(attribute.name);
    column.setFieldTypePHP(basePath + fieldTypePHP);
    column.setPhpDocField(basePath + fieldTypePHP);
    column.setIsOptional(true);
    column.setApiName(attribute.name);
    return column;
  }

  public static String getGlobalResourceReferenceName(Schema schema) {
    return CHARGEBEE_RESOURCES_BASE_PATH
        + subResourceName(schema)
        + BACK_SLASH
        + subResourceName(schema);
  }
}
