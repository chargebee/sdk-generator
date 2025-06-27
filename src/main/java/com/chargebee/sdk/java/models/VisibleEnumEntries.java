package com.chargebee.sdk.java.models;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class VisibleEnumEntries {
  private boolean hidden;
  private boolean isDeprecated;
  private String name;
  private String apiName;
}
