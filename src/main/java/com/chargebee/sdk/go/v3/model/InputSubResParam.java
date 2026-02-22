package com.chargebee.sdk.go.v3.model;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class InputSubResParam {
  private boolean isMulti;
  private String methodName;
  private String camelSingularResName;
  private String camelResName;
  private String subParams;
}
