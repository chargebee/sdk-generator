package com.chargebee.sdk.java.javanext.datatype;

import com.google.common.base.CaseFormat;
import org.jetbrains.annotations.NotNull;

// ===== Enum =====
public record EnumType(String fieldName) implements FieldType {
  @Override
  public String display() {
    return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, fieldName);
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
