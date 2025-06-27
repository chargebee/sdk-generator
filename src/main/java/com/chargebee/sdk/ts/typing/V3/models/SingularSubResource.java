package com.chargebee.sdk.ts.typing.V3.models;

import lombok.Data;
import lombok.Getter;

@Getter
public @Data class SingularSubResource {

  private String resName;
  private String clazName;
  private boolean hidden;
  private boolean deprecated;
  private String name;
  private String typescriptPutMethName;
  private String returnGeneric;
}
