package com.chargebee.sdk.java;

import com.chargebee.openapi.Enum;
import io.swagger.v3.oas.models.media.Schema;

public class JavaHelper {

  private final String enumDelimiter = ",\n" + " ".repeat(8);

  public String typeOfEnum(Schema schema) {
    Enum anEnum = new Enum(schema);
    var enumValues = "";
    if (!anEnum.deprecatedValues().isEmpty()) {
      enumValues =
          String.join(
              enumDelimiter,
              anEnum.deprecatedValues().stream()
                  .map(e -> "@Deprecated\n        " + e.toUpperCase())
                  .toList());
      enumValues += enumDelimiter;
    }
    enumValues =
        enumValues
            + String.join(
                enumDelimiter, anEnum.validValues().stream().map(String::toUpperCase).toList());
    return anEnum.globalEnumReference().orElse(enumValues);
  }
}
