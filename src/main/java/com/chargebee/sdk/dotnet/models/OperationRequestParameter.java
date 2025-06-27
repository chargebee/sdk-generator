package com.chargebee.sdk.dotnet.models;

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class OperationRequestParameter {
  private boolean isHidden;
  private boolean deprecated;
  private boolean isListParam;
  private boolean isSortParam;
  private String dotNetMethName;
  private String listType;
  private String dotNetType;
  private String varName;
  private String name;
  private String dotNetPutMethName;
  private String returnGeneric;
  private boolean isMulti;
  private boolean isExceptionFilterParam;
  private boolean supportsPresenceFilter;
  private List<OperationRequestParameterSortParameter> sortParams;
}
