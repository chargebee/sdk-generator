package com.chargebee.sdk.test_data;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import org.assertj.core.data.MapEntry;

public class EnumBuilder {
  public final String typeName;
  private final Schema<?> schema;

  private EnumBuilder(String typeName, List<String> values) {
    this.typeName = typeName;
    this.schema = new StringSchema()._enum(values);
  }

  public static EnumBuilder buildEnum(String typeName, List<String> values) {
    return new EnumBuilder(typeName, values);
  }

  public EnumBuilder withDeprecatedValues(List<String> deprecatedValues) {
    schema.addExtension("x-cb-deprecated-enum-values", String.join(",", deprecatedValues));
    return this;
  }

  public EnumBuilder withParameterBlankOption() {
    schema.addExtension("x-cb-parameter-blank-option", "not_allowed");
    return this;
  }

  public EnumBuilder asApi() {
    schema.addExtension("x-cb-is-api-column", true);
    return this;
  }

  public EnumBuilder setEnumApiName(String name) {
    schema.addExtension("x-cb-sdk-enum-api-name", name);
    return this;
  }

  public EnumBuilder asGenSeparate() {
    schema.addExtension("x-cb-is-gen-separate", true);
    return this;
  }

  public EnumBuilder asGlobalEnum(boolean asGobalEnum) {
    schema.addExtension("x-cb-is-global-enum", asGobalEnum);
    return this;
  }

  public EnumBuilder withMetaModelName(String metaModelName) {
    schema.addExtension("x-cb-meta-model-name", metaModelName);
    return this;
  }

  public MapEntry<String, Schema<?>> done() {
    return MapEntry.entry(typeName, schema);
  }
}
