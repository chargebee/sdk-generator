package com.chargebee;

import com.chargebee.openapi.ApiVersion;

public class ApiVersionHandler {
  private static ApiVersionHandler instance;
  private ApiVersion version = ApiVersion.V2;

  private ApiVersionHandler() {}

  public static ApiVersionHandler getInstance() {
    if (instance == null) {
      instance = new ApiVersionHandler();
    }
    return instance;
  }

  public ApiVersion getValue() {
    return this.version;
  }

  public void setValue(ApiVersion version) {
    this.version = version;
  }
}
