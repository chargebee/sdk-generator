package com.chargebee.sdk.java.javanext.datatype;

import org.jetbrains.annotations.NotNull;

public record TimestampType() implements FieldType {
  @Override
  public String display() {
    return "Timestamp";
  }

  @Override
  public @NotNull String toString() {
    return display();
  }
}
