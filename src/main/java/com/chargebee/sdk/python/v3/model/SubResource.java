package com.chargebee.sdk.python.v3.model;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class SubResource {
  private String clazName;
  private String cols;
  private String responseCols;
}
