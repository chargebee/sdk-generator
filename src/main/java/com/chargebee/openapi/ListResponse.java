package com.chargebee.openapi;

import static com.chargebee.GenUtil.toCamelCase;

import com.chargebee.sdk.DataType;
import com.chargebee.sdk.common.model.OperationResponse;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ListResponse extends Response {
  public ListResponse(String actionName, Schema<?> onSuccessSchema) {
    super(actionName, onSuccessSchema);
  }

  @Override
  public Map<String, Object> templateParams(DataType lang) {
    if (!(onSuccessSchema.getProperties().get("list") instanceof ArraySchema)) {
      return Map.of();
    }
    var schema = ((ArraySchema) onSuccessSchema.getProperties().get("list")).getItems();
    var responseParams =
        List.of(
            new com.chargebee.openapi.parameter.ListResponse(
                "list",
                lang.listDataType(new Response(actionName, schema).responseParameters()),
                true),
            new com.chargebee.openapi.parameter.ListResponse(
                "next_offset", lang.dataType(new StringSchema()), false));
    return Map.of(
        "name",
        toCamelCase(actionName) + "Response",
        "parameters",
        responseParams.stream()
            .map(com.chargebee.openapi.parameter.ListResponse::templateParams)
            .toList());
  }

  public List<OperationResponse> responseParameters(DataType lang) {
    if (!(onSuccessSchema.getProperties().get("list") instanceof ArraySchema)) {
      return Collections.emptyList();
    }
    var schema = ((ArraySchema) onSuccessSchema.getProperties().get("list")).getItems();
    return List.of(
        new OperationResponse(
            "list", null, true, true, new Response(actionName, schema).responseParameters(lang)),
        new OperationResponse(
            "next_offset",
            lang.dataType(new StringSchema()),
            false,
            false,
            Collections.emptyList()));
  }

  @Override
  protected Schema<?> getOnSuccessSchema() {
    if (!(onSuccessSchema.getProperties().get("list") instanceof ArraySchema)) {
      return onSuccessSchema;
    }
    return ((ArraySchema) onSuccessSchema.getProperties().get("list")).getItems();
  }
}
