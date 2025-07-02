package com.chargebee.sdk.ts.models;

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public @Data class SubResource {

  private String clazName;
  private List<Column> cols;
}
