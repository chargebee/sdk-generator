package com.chargebee.sdk.java.javanext.datatype;

import com.chargebee.sdk.java.javanext.util.CaseFormatUtil;
import org.jetbrains.annotations.NotNull;

// ===== Enum =====
public record EnumType(String fieldName) implements FieldType {
  @Override
  public String display() {
    return CaseFormatUtil.toUpperCamelSafe(fieldName);
  }

  @Override
  public String javaTypeName() {
    return fieldName;
  }

  @Override
  public @NotNull String toString() {
    return display();
  }
}
