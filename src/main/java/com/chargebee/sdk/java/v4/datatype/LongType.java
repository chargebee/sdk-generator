package com.chargebee.sdk.java.v4.datatype;

import org.jetbrains.annotations.NotNull;

public record LongType() implements FieldType {
  @Override
  public String display() {
    return "Long";
  }

  @Override
  public @NotNull String toString() {
    return display();
  }
}
