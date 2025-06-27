package com.chargebee.sdk.python.v3.model;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class Response {
  private String className;
  private String responseParams;
  private String subResponseClassName;
  private String subResponseParams;
}
