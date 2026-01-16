package com.chargebee.sdk.java.v4.datatype;

import org.jetbrains.annotations.NotNull;

public record IntegerType() implements FieldType {
  @Override
  public String display() {
    return "Integer";
  }

  @Override
  public @NotNull String toString() {
    return display();
  }
}
