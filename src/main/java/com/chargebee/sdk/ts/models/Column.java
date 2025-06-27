package com.chargebee.sdk.ts.models;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class Column {

  private String name;
  private String fieldTypeTypescript;
}
