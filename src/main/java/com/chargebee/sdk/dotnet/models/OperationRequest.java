package com.chargebee.sdk.dotnet.models;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class OperationRequest {
  private String clazName;
  private String superClazName;
  private List<OperationRequestParameter> params;
  private String getReturnGeneric;
  private boolean isList;
  private List<SingularSubResource> singularSubs;
  private List<SingularSubResource> multiSubs;
  private boolean isPostOperationWithFilter;
  private boolean hasBatch;
  private boolean isJsonRequest;
  private Map<String, List<SingularSubResource>> multiSubsForBatch;
  private String rawOperationName;

  public boolean canHide() {
    return getParams().isEmpty() && getSingularSubs().isEmpty() && getMultiSubs().isEmpty();
  }
}
