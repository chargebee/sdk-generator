package com.chargebee.sdk.python.v3.model;

import static com.chargebee.handlebar.Inflector.capitalize;

import com.chargebee.openapi.Action;
import com.chargebee.sdk.common.model.OperationResponse;
import com.chargebee.sdk.python.v3.Constants;
import com.google.common.base.CaseFormat;
import java.util.*;

public class ResponseParser {
  private Action action;

  private String activeResourceName;
  private List<OperationResponse> responseParams;

  public ResponseParser(
      Action action, String activeResourceName, List<OperationResponse> responseParams) {
    this.action = action;
    this.activeResourceName = activeResourceName;
    this.responseParams = responseParams;
    this.sortResponseParams();
  }

  public String className() {
    return actionNameInPascalCase() + "Response";
  }

  public String actionResponseParams() {
    StringJoiner buf = new StringJoiner("\n");
    for (OperationResponse operationResponse : this.responseParams) {
      buf.add(responseHelper(operationResponse));
    }
    return buf.toString();
  }

  public String subResponseClassName() {
    if (responseParams.stream()
        .anyMatch(r -> r.isListResponse() && !r.getListResponse().isEmpty())) {
      return actionNameInPascalCase() + activeResourceName + "Response";
    }
    return null;
  }

  public String subResponseParams() {
    StringJoiner buf = new StringJoiner("\n");
    for (OperationResponse response : this.responseParams) {
      if (response.isListResponse() && !response.getListResponse().isEmpty()) {
        for (OperationResponse operationResponse : response.getListResponse()) {
          buf.add(responseHelper(operationResponse));
        }
      }
    }
    return buf.toString();
  }

  private String responseHelper(OperationResponse operationResponse) {
    StringJoiner buf = new StringJoiner("\n");
    String typePrefix = ": ";
    String type =
        operationResponse.getType() != null
            ? this.operationResponseType(operationResponse.getType())
            : "";
    String typeSuffix = operationResponse.isRequired() ? "" : " = None";
    if (!operationResponse.isListResponse()) {
      type = type.equals("unknown") ? Constants.STRING_TYPE : type;
      buf.add(
          Constants.INDENT_DELIMITER
              + String.join(
                  Constants.JOIN_DELIMITER,
                  operationResponse.getName(),
                  typePrefix,
                  type,
                  typeSuffix));
    } else if (operationResponse.isListResponse()
        && operationResponse.getListResponse().isEmpty()) {
      typePrefix += type.equals("Any") ? "" : "List[";
      typeSuffix = type.equals("Any") ? "" : "]" + typeSuffix;
      buf.add(
          Constants.INDENT_DELIMITER
              + String.join(
                  Constants.JOIN_DELIMITER,
                  operationResponse.getName(),
                  typePrefix,
                  type,
                  typeSuffix));
    } else {
      typePrefix += "List[";
      typeSuffix = "]" + typeSuffix;
      type = actionNameInPascalCase() + activeResourceName;
      type += isPythonDataType(type) ? "" : "Response";
      buf.add(
          Constants.INDENT_DELIMITER
              + String.join(
                  Constants.JOIN_DELIMITER,
                  operationResponse.getName(),
                  typePrefix,
                  type,
                  typeSuffix));
    }
    return buf.toString();
  }

  private String actionNameInPascalCase() {
    String[] parts = action.name.toString().split("_");
    StringBuilder result = new StringBuilder();
    for (String part : parts) {
      result.append(capitalize(part));
    }
    return result.toString();
  }

  public static boolean isPythonDataType(String type) {
    List<String> pythonDatatypes =
        new ArrayList<>(
            Arrays.asList(
                Constants.STRING_TYPE,
                Constants.INT_TYPE,
                Constants.FLOAT_TYPE,
                Constants.BOOLEAN_TYPE,
                Constants.JSON_ARRAY_TYPE,
                Constants.JSON_OBJECT_TYPE,
                Constants.UNIX_TIME));
    return pythonDatatypes.contains(type);
  }

  private void sortResponseParams() {
    this.responseParams =
        this.responseParams.stream().sorted(Comparator.comparing(r -> !r.isRequired())).toList();
  }

  private String operationResponseType(String type) {
    if (type.equals("Any")) return "Any";
    if (type.equals("unknown")) return Constants.STRING_TYPE;
    if (isPythonDataType(type)) return type;
    if (type.equalsIgnoreCase(activeResourceName)) return type + "Response";
    return "\""
        + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, type.toString())
        + "."
        + type
        + "Response\"";
  }
}
