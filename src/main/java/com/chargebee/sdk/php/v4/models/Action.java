package com.chargebee.sdk.php.v4.models;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(chain = true)
public @Data class Action {
  private String phpDocField;
  private String name;
  private String returnType;
  private boolean hasHandle;
  private String httpMethodName;
  private String languageMethodName;
  private boolean hasRequestBodyParameters;
  private boolean hasQueryParameters;
  private boolean isAllRequestBodyParamsOptional;
  private boolean isAllQueryParamsOptional;
  private boolean isListAction;
  private String urlPrefix;
  private String urlSuffix;
  private boolean isOperationNeedsJsonInput;
  private String subDomain;
  private String actionDocLink;
  List<Map<String, Integer>> jsonKeys;
  private boolean isIdempotent;
}
