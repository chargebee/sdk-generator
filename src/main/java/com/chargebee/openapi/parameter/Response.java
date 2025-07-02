package com.chargebee.openapi.parameter;

import com.chargebee.sdk.DataType;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;

public class Response {
  public final String name;
  public final Schema<?> schema;
  public final boolean isRequired;

  public Response(String name, Schema<?> schema, boolean isRequired) {
    this.name = name;
    this.schema = schema;
    this.isRequired = isRequired;
  }

  private String getResourceNameFromRef(Schema<?> schema, DataType lang) {
    var ref = schema.get$ref();
    if (ref == null) {
      return lang.dataType(schema);
    }
    var x = ref.split("/");
    return x[x.length - 1];
  }

  public String referredResourceName(DataType lang) {
    if (schema instanceof ArraySchema) {
      return getResourceNameFromRef(schema.getItems(), lang);
    }
    return getResourceNameFromRef(schema, lang);
  }

  public Map<String, Object> templateParams(DataType lang) {
    return Map.of(
        "name",
        name,
        "isRequired",
        isRequired,
        "type",
        schema instanceof ArraySchema
            ? referredResourceName(lang) + "[]"
            : referredResourceName(lang),
        "isListResponse",
        schema instanceof ArraySchema,
        "referredResourceName",
        referredResourceName(lang));
  }
}
