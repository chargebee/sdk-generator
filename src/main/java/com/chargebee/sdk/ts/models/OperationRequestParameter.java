package com.chargebee.sdk.ts.models;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class OperationRequestParameter {
  private boolean isHidden;
  private boolean isSubFilterParam;
  private boolean isSortParam;
  private boolean deprecated;
  private String name;
  private String typescriptPutMethName;
  private String returnGeneric;
}
