package com.chargebee.openapi.parameter;

import java.util.Map;

public class ListResponse {
  public final String name;
  private final String type;
  private final boolean isRequired;

  public ListResponse(String name, String type, boolean isRequired) {
    this.name = name;
    this.type = type;
    this.isRequired = isRequired;
  }

  public Map<String, Object> templateParams() {
    return Map.of(
        "name", name,
        "isRequired", isRequired,
        "type", type);
  }
}
