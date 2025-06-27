package com.chargebee.sdk.dotnet.models;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class SingluarSubResource {
  private boolean hidden;
  private boolean deprecated;
  private boolean isListParam;
  private boolean isMulti;
  private boolean supportsPresenceFilter;
  private String listType;
  private String dotNetMethName;
  private String dotNetType;
  private String varName;
  private String dotNetPutMethName;
  private String resName;
  private String name;
  private String returnGeneric;
  private String presName;
  private int sortOrder;

  public int sortOrder() {
    return sortOrder != 0 ? (int) sortOrder : -1;
  }
}
