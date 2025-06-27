package com.chargebee.openapi.parameter;

import io.swagger.v3.oas.models.media.Schema;

public class Path extends Parameter {
  public Path(String name, Schema schema) {
    super(name, schema);
  }
}
