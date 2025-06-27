package com.chargebee.sdk.php.v4.models;

import java.util.List;
import lombok.Data;
import lombok.Getter;

@Getter
public @Data class Resource {
  private List<Column> cols;
  private List<Column> localEnumCols;
  private List<Column> globalEnumCols;
  private String clazName;
  private String namespace;
  private List<String> imports;
  private boolean isCustomFieldSupported;
}
