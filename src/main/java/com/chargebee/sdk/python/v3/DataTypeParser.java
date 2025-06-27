package com.chargebee.sdk.python.v3;

import static com.chargebee.openapi.Extension.SDK_FILTER_NAME;

import com.chargebee.handlebar.Inflector;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Optional;

public class DataTypeParser {
  protected static String filterType(Schema schema) {
    if (schema.getExtensions() != null && schema.getExtensions().get(SDK_FILTER_NAME) != null) {
      return (String) schema.getExtensions().get(SDK_FILTER_NAME);
    }
    Optional<Schema> filterSch = schema.getProperties().values().stream().findFirst();
    if (filterSch.isEmpty()) return null;
    Schema filterSchema = filterSch.get();
    if (filterSchema.getEnum() != null) {
      if (filterSchema.getFormat() != null && filterSchema.getFormat().equals("boolean")) {
        return "BooleanFilter";
      }
      return "EnumFilter";
    }
    if (filterSchema instanceof StringSchema) {
      if (filterSchema.getFormat() != null) {
        if (filterSchema.getFormat().equals(Constants.UNIX_TIME)) {
          return "TimestampFilter";
        }
        return "NumberFilter";
      }
      return "StringFilter";
    }
    return Constants.ANY_TYPE;
  }

  protected static String formatResponseDataType(String dataType) {
    if (dataType.contains("]")) {
      dataType = dataType.replace("]", "");
      dataType = Inflector.singularize(dataType);
      dataType += "Response]";
    } else {
      dataType = Inflector.singularize(dataType);
      dataType += "Response";
    }
    return dataType;
  }

  protected static String formatDependantResponseDataType(String dataType) {
    if (dataType.contains(".")) {
      if (dataType.contains("List")) {
        dataType = dataType.replace("[", "[\"").replace("]", "\"]");
      } else {
        dataType = "\"" + dataType + "\"";
      }
    }
    return dataType;
  }
}
