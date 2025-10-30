package com.chargebee.sdk.java.javanext.datatype;

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
