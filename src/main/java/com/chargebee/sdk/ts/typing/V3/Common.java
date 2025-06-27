package com.chargebee.sdk.ts.typing.V3;

import com.chargebee.openapi.Enum;
import io.swagger.v3.oas.models.media.*;

public class Common {
  public static String primitiveDataType(Schema schema) {
    if (schema instanceof StringSchema
        || schema instanceof EmailSchema
        || schema instanceof PasswordSchema) {
      if (schema.getEnum() != null) {
        Enum anEnum = new Enum(schema);
        var enumValues =
            String.join(
                " | ", anEnum.validValues().stream().map(s -> String.format("'%s'", s)).toList());
        return anEnum.globalEnumReference().map(value -> value + "Enum").orElse(enumValues);
      }
      return "string";
    }
    if (schema instanceof NumberSchema || schema instanceof IntegerSchema) {
      return "number";
    }
    if (schema instanceof BooleanSchema) {
      return "boolean";
    }
    return "any";
  }
}
