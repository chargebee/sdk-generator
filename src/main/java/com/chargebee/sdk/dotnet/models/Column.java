package com.chargebee.sdk.dotnet.models;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class Column {
  private boolean deprecated;
  private String returnType;
  private boolean isSubResource;
  private String methName;
  private String getterCode;
}
