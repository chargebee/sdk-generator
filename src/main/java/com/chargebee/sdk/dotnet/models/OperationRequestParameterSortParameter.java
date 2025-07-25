package com.chargebee.sdk.dotnet.models;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class OperationRequestParameterSortParameter {
  private String dotNetMethName;
  private String name;
  private String returnGeneric;
}
