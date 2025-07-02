package com.chargebee.sdk.go.model;

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class OperationRequest {
  private boolean hasInputParams;
  private String clazName;
  private String inputParams;
  private List<InputSubResParam> inputSubResParams;
}
