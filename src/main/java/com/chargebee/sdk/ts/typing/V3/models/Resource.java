package com.chargebee.sdk.ts.typing.V3.models;

import java.util.List;
import lombok.Data;
import lombok.Getter;

@Getter
public @Data class Resource {
  private List<OperationRequestInterface> operRequestInterfaces;
  private List<String> attributesInMultiLine;
}
