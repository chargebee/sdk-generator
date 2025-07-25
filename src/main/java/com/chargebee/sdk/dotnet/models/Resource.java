package com.chargebee.sdk.dotnet.models;

import java.util.List;
import lombok.Data;

@Data
public class Resource {
  private String clazName;
  private List<Operation> operations;
  private List<Column> cols;
  private String customImport;
  private String snippet;
  private boolean hasOperReqClasses;
  private List<OperationRequest> operRequestClasses;
  private boolean hasContent;
  private List<EnumColumn> enumCols;
  private List<EnumColumn> schemaLessEnums;
  private List<SubResource> subResources;
}
