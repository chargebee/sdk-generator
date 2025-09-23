package com.chargebee.sdk.java.javanext.datatype;

import org.jetbrains.annotations.NotNull;

public record BigDecimalType() implements FieldType {
  @Override
  public String display() {
    return "BigDecimal";
  }

  @Override
  public @NotNull String toString() {
    return display();
  }
}
