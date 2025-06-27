package com.chargebee.sdk.java.models;

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class SubResource {
  private String clazName;
  private List<EnumColumn> enumCols;
  private List<EnumColumn> schemaLessEnum;
  private List<Column> cols;
}
