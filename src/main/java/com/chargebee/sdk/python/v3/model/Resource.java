package com.chargebee.sdk.python.v3.model;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class Resource {
  private String responseImports;
  private String operationImports;
  private String clazName;
  private String responseCols;
  private boolean event;
  private boolean timeMachine;
  private boolean hostedPage;
  private boolean session;
  private boolean export;
  private List<SubResource> subResources;
  private List<Operation> operations;
  private List<Map<String, Object>> enums;
  private List<Map<String, Object>> actions;
}
