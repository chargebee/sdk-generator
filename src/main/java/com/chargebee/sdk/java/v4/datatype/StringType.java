package com.chargebee.sdk.java.v4.datatype;

import org.jetbrains.annotations.NotNull;

public record StringType() implements FieldType {
  @Override
  public String display() {
    return "String";
  }

  @Override
  public @NotNull String toString() {
    return display();
  }
}
