package com.chargebee.sdk.common.model;

import java.util.List;

public class OperationResponse {

  private String name;
  private String type;
  private boolean isListResponse;
  private boolean isRequired;

  private List<OperationResponse> listResponse;

  public OperationResponse(
      String name,
      String type,
      boolean isListResponse,
      boolean isRequired,
      List<OperationResponse> listResponse) {
    this.name = name;
    this.type = type;
    this.isListResponse = isListResponse;
    this.isRequired = isRequired;
    this.listResponse = listResponse;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public boolean isListResponse() {
    return isListResponse;
  }

  public boolean isRequired() {
    return isRequired;
  }

  public List<OperationResponse> getListResponse() {
    return listResponse;
  }
}
