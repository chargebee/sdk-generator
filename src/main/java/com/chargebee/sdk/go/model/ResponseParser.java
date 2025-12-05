package com.chargebee.sdk.go.model;

import static com.chargebee.handlebar.Inflector.capitalize;

import com.chargebee.openapi.Action;
import com.chargebee.sdk.common.model.OperationResponse;
import com.chargebee.sdk.go.Constants;
import com.chargebee.sdk.go.Formatter;
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
    return Formatter.formatUsingDelimiter(buf.toString());
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
    return Formatter.formatUsingDelimiter(buf.toString());
  }

  private String responseHelper(OperationResponse operationResponse) {
    String name =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, operationResponse.getName());
    String type =
        operationResponse.getType() != null
            ? this.operationResponseType(operationResponse.getType())
            : "";

    if (!operationResponse.isListResponse()) {
      type = type.equals("unknown") ? Constants.STRING_TYPE : type;
    } else if (operationResponse.isListResponse()
        && operationResponse.getListResponse().isEmpty()) {
      type = "[]" + type;
    } else {
      type = "[]*" + actionNameInPascalCase() + activeResourceName + "Response";
    }
    return "\t"
        + String.join(
            Formatter.delimiter, name, type, getJsonVal(operationResponse.getName()));
  }

  private String getJsonVal(String name) {
    return "`json:\"" + name + ",omitempty\"`";
  }

  private String actionNameInPascalCase() {
    String[] parts = action.name.toString().split("_");
    StringBuilder result = new StringBuilder();
    for (String part : parts) {
      result.append(capitalize(part));
    }
    return result.toString();
  }

  public static boolean isGoDataType(String type) {
    List<String> goDatatypes =
        new ArrayList<>(
            Arrays.asList(
                Constants.STRING_TYPE,
                Constants.INT_SIXTY_FOUR,
                Constants.INT_THIRTY_TWO,
                "float64",
                "bool",
                Constants.JSON_RAW_MESSAGE,
                Constants.MAP_STRING_INTERFACE));
    return goDatatypes.contains(type);
  }

  private void sortResponseParams() {
    this.responseParams =
        this.responseParams.stream().sorted(Comparator.comparing(r -> !r.isRequired())).toList();
  }

  private String operationResponseType(String type) {
    if (type.equals("Any")) return "interface{}";
    if (type.equals("unknown")) return Constants.STRING_TYPE;
    if (isGoDataType(type)) return type;
    if (type.equalsIgnoreCase(activeResourceName)) return "*" + type; // Local resource
    // External resource
    String pkg = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, type).toLowerCase();
    return "*" + pkg + "." + type;
  }
}
