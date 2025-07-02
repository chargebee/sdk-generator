package com.chargebee.sdk.php.v4;

import static com.chargebee.GenUtil.toCamelCase;
import static com.chargebee.sdk.php.v4.Constants.RESPONSE_NAMESPACE;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Resource;
import com.chargebee.sdk.php.v4.models.Column;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.*;

public class ListResponseParser {
  public static com.chargebee.sdk.php.v4.models.Resource actionResponses(
      Action action, Resource res) {
    Schema<?> successSchema = action.getOnSuccessSchema();
    if (successSchema.getProperties() == null) {
      return null;
    }

    com.chargebee.sdk.php.v4.models.Resource response =
        new com.chargebee.sdk.php.v4.models.Resource();
    response.setClazName(toCamelCase(action.name) + res.name + "Response");
    response.setNamespace(RESPONSE_NAMESPACE + toCamelCase(res.name) + "Response");
    response.setCols(parseColumns(successSchema, action, res));
    response.setImports(collectImports(response.getCols()));
    return response;
  }

  private static List<Column> parseColumns(Schema<?> schema, Action action, Resource res) {
    Set<String> required =
        new HashSet<>(
            schema.getRequired() != null ? schema.getRequired() : Collections.emptyList());
    List<Column> columns = new ArrayList<>();

    schema
        .getProperties()
        .forEach(
            (key, value) -> {
              Column column = createColumn(key, value, required.contains(key));

              if (value instanceof ArraySchema) {
                handleArraySchema((ArraySchema) value, column, action, res, key);
              }

              columns.add(column);
            });

    return columns;
  }

  private static Column createColumn(String key, Schema<?> schema, boolean isRequired) {
    Column column = new Column();
    column.setName(key);
    column.setApiName(key);
    column.setFieldTypePHP(referredResourceName(schema));
    column.setPhpDocField(referredResourceName(schema));
    column.setIsOptional(!isRequired);
    column.setPrimitiveDataType(isPrimitiveDataType(schema));
    column.setArrayOfSubResources(schema instanceof ArraySchema);
    return column;
  }

  private static void handleArraySchema(
      ArraySchema schema, Column column, Action action, Resource res, String key) {
    Schema<?> items = schema.getItems();
    if (items.getProperties() == null) return;

    Set<String> requiredProps =
        new HashSet<>(items.getRequired() != null ? items.getRequired() : Collections.emptyList());
    List<Column> childColumns = new ArrayList<>();

    items
        .getProperties()
        .forEach(
            (childKey, childSchema) -> {
              Column childColumn =
                  createColumn(childKey, childSchema, requiredProps.contains(childKey));
              childColumns.add(childColumn);
            });

    column.setChildCols(childColumns);
    column.setHasChildCols(true);
    column.setPhpDocField(
        String.format(
            "%s%sResponse%sObject", toCamelCase(action.name), res.name, toCamelCase(key)));
  }

  private static List<String> collectImports(List<Column> columns) {
    Set<String> imports = new HashSet<>();
    for (Column column : columns) {
      if (!column.isPrimitiveDataType()) {
        imports.add(String.format("use Chargebee\\Resources\\%s;", column.getFieldTypePHP()));
      }
      if (column.isHasChildCols() && column.getChildCols() != null) {
        for (Column childCol : column.getChildCols()) {
          if (!childCol.isPrimitiveDataType()) {
            String type = childCol.getFieldTypePHP();
            imports.add(String.format("use Chargebee\\Resources\\%s\\%s", type, type));
          }
        }
      }
    }
    return new ArrayList<>(imports);
  }

  private static String referredResourceName(Schema<?> schema) {
    return schema instanceof ArraySchema
        ? getResourceNameFromRef(schema.getItems())
        : getResourceNameFromRef(schema);
  }

  private static String getResourceNameFromRef(Schema<?> schema) {
    String ref = schema.get$ref();
    return ref == null ? Common.dataType(schema) : ref.substring(ref.lastIndexOf('/') + 1);
  }

  private static boolean isPrimitiveDataType(Schema<?> schema) {
    if (schema instanceof ArraySchema) {
      return schema.getItems().get$ref() == null;
    }
    return schema.get$ref() == null;
  }
}
