package com.chargebee.sdk.java.models;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class OperationRequestParameterSortParameter {
  private String methodName;
  private String name;
  private String returnGeneric;

  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }
}
