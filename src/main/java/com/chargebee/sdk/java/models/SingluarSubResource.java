package com.chargebee.sdk.java.models;

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
  private String methName;
  private String javaType;
  private String varName;
  private String putMethodName;
  private String resName;
  private String name;
  private String returnGeneric;
  private String presName;
  private int sortOrder;
  private boolean hasBatch;

  public int sortOrder() {
    return sortOrder != 0 ? sortOrder : -1;
  }

  public void setPutMethodName(String putMethodName) {
    this.putMethodName = putMethodName;
  }
}
