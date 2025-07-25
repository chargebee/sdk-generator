package com.chargebee.sdk.java.models;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class Operation {
  private String retType;
  private String methName;
  private String handleArg;
  private String urlArgs;
  private String reqCreationCode;
  private boolean isDeprecated;
}
