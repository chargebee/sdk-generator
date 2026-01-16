package com.chargebee.sdk.java.v4.datatype;

import org.jetbrains.annotations.NotNull;

public record NumberType() implements FieldType {
  @Override
  public String display() {
    return "Number";
  }

  @Override
  public @NotNull String toString() {
    return display();
  }
}
