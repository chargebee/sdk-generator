package com.chargebee.openapi;

public enum ApiVersion {
  V1(1),
  V2(2);

  public final int number;

  ApiVersion(int number) {

    this.number = number;
  }
}
