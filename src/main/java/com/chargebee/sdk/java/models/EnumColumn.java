package com.chargebee.sdk.java.models;

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class EnumColumn {
  private String apiClassName;
  private boolean deprecated;
  private List<VisibleEnumEntries> visibleEntries;
}
