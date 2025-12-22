package com.chargebee.sdk.ts.typing.V3.models;

import lombok.Data;
import lombok.Getter;

@Getter
public @Data class OperationRequestParameter {
  private boolean isHidden;
  private boolean isSubFilterParam;
  private boolean isSortParam;
  private boolean deprecated;
  private String deprecationMessage;
  private String name;
  private String typescriptPutMethName;
  private String returnGeneric;
}
