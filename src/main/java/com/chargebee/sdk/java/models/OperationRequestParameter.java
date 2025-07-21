package com.chargebee.sdk.java.models;

import java.util.List;
import lombok.Data;

public @Data class OperationRequestParameter {
  private boolean isHidden;
  private boolean deprecated;
  private boolean isListParam;
  private boolean isSortParam;
  private String methName;
  private String listType;
  private String javaType;
  private String javaSimpleType;
  private String varName;
  private String name;
  private String putMethodName;
  private String returnGeneric;
  private boolean isMulti;
  private boolean isExceptionFilterParam;
  private boolean supportsPresenceFilter;
  private List<OperationRequestParameterSortParameter> sortParams;
  private boolean simpleList;
  private boolean hasBatch;
  private boolean isIdempotent;
}
