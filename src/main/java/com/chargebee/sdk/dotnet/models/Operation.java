package com.chargebee.sdk.dotnet.models;

import lombok.Data;

@Data
public class Operation {
  private String retType;
  private String methName;
  private String handleArg;
  private String urlArgs;
  private String reqCreationCode;
  private boolean isDeprecated;
  private String subDomain;
  private boolean isIdempotent;
}
