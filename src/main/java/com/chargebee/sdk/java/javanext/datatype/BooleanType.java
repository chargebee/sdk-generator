package com.chargebee.sdk.java.javanext.datatype;

import org.jetbrains.annotations.NotNull;

public record BooleanType() implements FieldType {
  @Override
  public String display() {
    return "Boolean";
  }

  @Override
  public @NotNull String toString() {
    return display();
  }
}
