package com.chargebee.sdk.test_data;

import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.data.MapEntry;

public class ResourceResponseParam {
  public final String paramName;
  public final boolean isRequired;
  private final Schema<?> schema;

  public ResourceResponseParam(
      String paramName, MapEntry<String, Schema<?>> resource, boolean isRequired) {
    this(paramName, resource.key, isRequired);
  }

  public ResourceResponseParam(String paramName, String resourceName, boolean isRequired) {
    this(paramName, new Schema().$ref(resourceName), isRequired);
  }

  public ResourceResponseParam(String paramName, Schema<?> schema, boolean isRequired) {
    this.paramName = paramName;
    this.schema = schema;
    this.isRequired = isRequired;
  }

  public static ResourceResponseParam resourceResponseParam(
      String paramName, MapEntry<String, Schema<?>> resource) {
    return resourceResponseParam(paramName, resource, true);
  }

  public static ResourceResponseParam resourceResponseParam(
      String paramName, MapEntry<String, Schema<?>> resource, boolean isRequired) {
    return new ResourceResponseParam(paramName, resource, isRequired);
  }

  public static ResourceResponseParam resourceResponseParam(
      String paramName, Schema<?> schema, boolean isRequired) {
    return new ResourceResponseParam(paramName, schema, isRequired);
  }

  public static ResourceResponseParam resourceResponseParam(String paramName, Schema<?> schema) {
    return new ResourceResponseParam(paramName, schema, true);
  }

  public Schema schema() {
    return schema;
  }
}
