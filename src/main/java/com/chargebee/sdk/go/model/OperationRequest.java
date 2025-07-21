package com.chargebee.sdk.go.model;

import java.util.List;
import lombok.Data;

@Data
public class OperationRequest {
  private boolean hasInputParams;
  private String clazName;
  private String inputParams;
  private List<InputSubResParam> inputSubResParams;
}
