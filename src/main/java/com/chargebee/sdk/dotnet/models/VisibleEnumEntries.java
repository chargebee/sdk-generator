package com.chargebee.sdk.dotnet.models;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class VisibleEnumEntries {
  private boolean hidden;
  private boolean isDeprecated;
  private String dotNetName;
  private String apiName;
}
