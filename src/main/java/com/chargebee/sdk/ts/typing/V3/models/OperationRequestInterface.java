package com.chargebee.sdk.ts.typing.V3.models;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class OperationRequestInterface {
  private String clazName;
  private List<OperationRequestParameter> params;
  private boolean hasSortParam;
  private List<String> subParamsForOperation;
  private boolean hasSingularSubs;
  private Map<String, List<SingularSubResource>> singularSubs;
  private boolean hasMultiSubs;
  private Map<String, List<SingularSubResource>> multiSubs;
  private boolean customFieldSupported;
}
