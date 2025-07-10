package com.chargebee.sdk.ts.models;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class Operation {

  private boolean hasHandle;
  private boolean hasInterface;
  private boolean isList;
  private String methName;
  private String entity;
  private String singularName;
  private String getClazName;
  private String httpMethName;
  private String resourceIdentifier1;
  private String resourceIdentifier2;
  private String subDomain;
  private boolean isOperationNeedsJsonInput;
  private List<Map<String, Integer>> jsonKeys;
}
