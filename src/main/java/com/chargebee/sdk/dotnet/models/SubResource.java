package com.chargebee.sdk.dotnet.models;

import java.util.List;
import lombok.Data;

@Data
public class SubResource {
  private String clazName;
  private List<EnumColumn> enumCols;
  private List<EnumColumn> schemaLessEnum;
  private List<Column> cols;
}
