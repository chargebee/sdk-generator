package com.chargebee.sdk.ts.models;

import java.util.List;
import lombok.Data;

@Data
public class OperationRequestInterface {
  private String clazName;
  private List<OperationRequestParameter> params;
  private boolean hasSortParam;
  private List<String> subParamsForOperation;
  private boolean hasSingularSubs;
  private List<SingularSubResource> singularSubs;
  private boolean hasMultiSubs;
  private List<SingularSubResource> multiSubs;
}
