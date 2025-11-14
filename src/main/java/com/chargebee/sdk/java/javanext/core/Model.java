package com.chargebee.sdk.java.javanext.core;

import com.google.common.base.CaseFormat;
import java.util.List;

@lombok.Data
public class Model {
  private String packageName;
  private String name;
  private String getterName;
  private List<Field> fields;
  private List<EnumFields> enumFields;
  private List<Model> subModels;
  private List<String> imports;
  private boolean customFieldsSupported;

  public String getPackageName() {
    if (packageName == null || packageName.isEmpty()) return packageName;
    if (packageName.contains("_")) {
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, packageName);
    }
    // If starts with upper, treat as UpperCamel; else assume already lowerCamel
    return Character.isUpperCase(packageName.charAt(0))
        ? CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, packageName)
        : packageName;
  }

  public String getName() {
    if (name == null || name.isEmpty()) return name;
    if (name.contains("_")) {
      // snake_case -> UpperCamel
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
    }
    // If lowerCamel, promote to UpperCamel; if already UpperCamel, return as-is
    return Character.isLowerCase(name.charAt(0))
        ? Character.toUpperCase(name.charAt(0)) + name.substring(1)
        : name;
  }
}
