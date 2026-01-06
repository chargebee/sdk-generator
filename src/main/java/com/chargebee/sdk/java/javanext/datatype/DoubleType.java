package com.chargebee.sdk.java.javanext.datatype;

import org.jetbrains.annotations.NotNull;

public record DoubleType() implements FieldType {
  @Override
  public String display() {
    return "Double";
  }

  @Override
  public @NotNull String toString() {
    return display();
  }
}

