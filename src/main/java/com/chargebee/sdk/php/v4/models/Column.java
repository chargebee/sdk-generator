package com.chargebee.sdk.php.v4.models;

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class Column {
  private String phpDocField;
  private String name;
  private String fieldTypePHP;
  private Boolean isOptional;
  private boolean isPrimitiveDataType;
  private boolean isSubResources;
  private String apiName;
  private boolean isArrayOfSubResources;
  private String subResourceName;
  private List<Column> childCols;
  private boolean hasChildCols;
  private boolean isDeprecated;
}
