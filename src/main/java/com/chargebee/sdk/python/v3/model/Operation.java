package com.chargebee.sdk.python.v3.model;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class Operation {
  private boolean hasInputParams;
  private String httpRequestType;
  private String clazName;
  private String inputParams;
  private List<InputSubResParam> inputSubResParams;
  private Response operationResponses;
  private String subDomain;
  private boolean isContentTypeJson;
  private List<Map<String, Integer>> jsonKeys;
}
