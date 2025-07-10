package com.chargebee.sdk.ts.models;

import java.util.List;
import lombok.Data;

@Data
public class SubResource {

  private String clazName;
  private List<Column> cols;
}
