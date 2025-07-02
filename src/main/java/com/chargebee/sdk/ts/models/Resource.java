package com.chargebee.sdk.ts.models;

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class Resource {

  private boolean isListAPI;
  private String clazName;
  private List<String> attributesInMultiLine;
  private String snippet;
  private List<Operation> operations;
  private List<SubResource> subResources;
  private String singularName;
  private List<OperationRequestInterface> operRequestInterfaces;
}
