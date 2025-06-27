package com.chargebee.sdk.ts.typing.V3;

import static com.chargebee.GenUtil.pluralize;
import static com.chargebee.GenUtil.toCamelCase;
import static com.chargebee.sdk.ts.typing.V3.Common.primitiveDataType;

import com.chargebee.openapi.Attribute;
import com.chargebee.sdk.ts.typing.Constants;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;

public class DataTypesParser {
  public static String dataTypeForSubResources(Schema schema) {
    if (schema instanceof ArraySchema && schema.getItems() != null) {
      return String.format("%s", dataTypeForSubResources(schema.getItems()));
    }
    if (schema instanceof ArraySchema
        && schema.getItems() != null
        && schema.getItems().getType() != null) {
      return String.format(Constants.ARRAY_OF_STRING, primitiveDataType(schema.getItems()));
    }
    return primitiveDataType(schema);
  }

  public static String dataTypeForSingularSubResources(Attribute attribute) {
    if (attribute.isFilterAttribute() && attribute.getFilterType() != null) {
      return dataTypeForFilterAttribute(attribute);
    }
    return dataTypeForSubResources(attribute.schema);
  }

  public static String dataTypeForFilterAttribute(Attribute attribute) {
    if (attribute.getFilterType().equals("DateFilter")) {
      return "filter.Date";
    }
    if (attribute.getFilterType().equals("TimestampFilter")) {
      return "filter.Timestamp";
    }
    if (attribute.getFilterType().equals("BooleanFilter")) {
      return "filter.Boolean";
    }
    if (attribute.getFilterType().equals("NumberFilter")) {
      return "filter.Number";
    }
    if (attribute.getFilterType().equals("EnumFilter")) {
      return "filter.Enum";
    }
    return "filter.String";
  }

  public static String dataTypePrimitiveParameters(Attribute attribute) {
    String dataType = dataTypeForSubResources(attribute.schema);
    if (dataType.equalsIgnoreCase("string") && attribute.schema instanceof ArraySchema) {
      return "string[]";
    }
    if (attribute.isMultiAttribute()) {
      return toCamelCase(attribute.name)
          + toCamelCase(pluralize(attribute.name))
          + Constants.INPUT_PARAM;
    }
    return dataType;
  }
}
