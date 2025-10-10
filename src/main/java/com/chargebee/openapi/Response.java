package com.chargebee.openapi;

import static com.chargebee.GenUtil.toCamelCase;

import com.chargebee.sdk.DataType;
import com.chargebee.sdk.common.model.OperationResponse;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.*;

public class Response {
  protected final String actionName;
  protected final Schema<?> onSuccessSchema;

  public Response(String actionName, Schema<?> onSuccessSchema) {
    this.actionName = actionName;
    this.onSuccessSchema = onSuccessSchema;
  }

  public Map<String, Object> templateParams(DataType lang) {
    return Map.of(
        "description",
        onSuccessSchema != null && onSuccessSchema.getDescription() != null
            ? onSuccessSchema.getDescription()
            : "",
        "name",
        toCamelCase(actionName) + "Response",
        "parameters",
        responseParameters().stream().map(r -> r.templateParams(lang)).toList());
  }

  protected Schema<?> getOnSuccessSchema() {
    return onSuccessSchema;
  }

  public List<OperationResponse> responseParameters(DataType lang) {

    if (getOnSuccessSchema() != null
        && getOnSuccessSchema().getProperties() != null
        && getOnSuccessSchema().getProperties().containsKey("list")
        && getOnSuccessSchema().getProperties().get("list") instanceof ArraySchema) {
      var schema = ((ArraySchema) onSuccessSchema.getProperties().get("list")).getItems();
      return List.of(
          new OperationResponse(
              "list", null, true, true, new Response(actionName, schema).responseParameters(lang)));
    }
    return responseParameters().stream()
        .map(
            response ->
                new OperationResponse(
                    response.name,
                    response.referredResourceName(lang),
                    response.schema instanceof ArraySchema,
                    response.isRequired,
                    Collections.emptyList()))
        .toList();
  }

  protected List<com.chargebee.openapi.parameter.Response> responseParameters() {
    if (getOnSuccessSchema() == null) {
      return List.of();
    }
    var properties = getOnSuccessSchema().getProperties();
    if (properties == null) {
      return List.of();
    }
    Set<String> requiredProperties =
        getOnSuccessSchema().getRequired() == null
            ? Set.of()
            : new HashSet<>(getOnSuccessSchema().getRequired());
    return properties.entrySet().stream()
        .map(
            entry ->
                new com.chargebee.openapi.parameter.Response(
                    entry.getKey(), entry.getValue(), requiredProperties.contains(entry.getKey())))
        .toList();
  }
}
