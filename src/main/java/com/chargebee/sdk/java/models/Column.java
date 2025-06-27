package com.chargebee.sdk.java.models;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class Column {
  private boolean deprecated;
  private String javaType;
  private boolean isSubResource;
  private String methName;
  private String getterCode;
}
