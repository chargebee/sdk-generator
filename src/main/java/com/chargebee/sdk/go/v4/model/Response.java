package com.chargebee.sdk.go.v4.model;

import lombok.Data;

@Data
public class Response {
  private String className;
  private String responseParams;
  private String subResponseClassName;
  private String subResponseParams;
}

