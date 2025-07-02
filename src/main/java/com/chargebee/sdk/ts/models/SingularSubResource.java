package com.chargebee.sdk.ts.models;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class SingularSubResource {

  private String resName;
  private String clazName;
  private boolean hidden;
  private boolean deprecated;
  private String name;
  private String typescriptPutMethName;
  private String returnGeneric;
}
