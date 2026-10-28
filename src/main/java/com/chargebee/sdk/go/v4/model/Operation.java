package com.chargebee.sdk.go.v4.model;

import java.util.List;
import lombok.Data;

@Data
public class Operation {
  private boolean hasInputParams;
  private boolean hasOnlyPathParam;
  private String clazName;
  private String inputParams;
  private List<InputSubResParam> inputSubResParams;
  private Response operationResponses;
  private String httpRequestType;
}

