package com.chargebee.sdk.go.model;

import java.util.List;
import lombok.Data;

@Data
public class Resource {
  private String pkgName;
  private String importFiles;
  private String clazName;
  private String cols;
  private List<SubResource> subResources;
  private List<OperationRequest> operRequestClasses;
}
