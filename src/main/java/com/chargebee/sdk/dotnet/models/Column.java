package com.chargebee.sdk.dotnet.models;

import lombok.Data;

@Data
public class Column {
  private boolean deprecated;
  private String returnType;
  private boolean isSubResource;
  private String methName;
  private String getterCode;
}
