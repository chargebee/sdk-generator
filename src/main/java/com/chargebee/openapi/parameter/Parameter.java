package com.chargebee.openapi.parameter;

import static com.chargebee.openapi.Extension.*;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Enum;
import com.chargebee.openapi.Extension;
import com.chargebee.sdk.DataType;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Getter;

public class Parameter {
  public final Schema<?> schema;
  public final boolean isRequired;
  @Getter private final String name;

  public Parameter(String name, Schema<?> schema) {
    this(name, schema, true);
  }

  public Parameter(String name, Schema<?> schema, boolean isRequired) {
    this.name = name;
    this.schema = schema;
    this.isRequired = isRequired;
  }

  public static Parameter fromParameter(io.swagger.v3.oas.models.parameters.Parameter parameter) {
    return new Parameter(parameter.getName(), parameter.getSchema(), parameter.getRequired());
  }

  public static int sortOrder(Schema schema) {
    return schema.getExtensions() != null
            && schema.getExtensions().get(Extension.SORT_ORDER) != null
        ? (int) schema.getExtensions().get(Extension.SORT_ORDER)
        : -1;
  }

  public boolean isDeprecated() {
    return schema.getDeprecated() != null && schema.getDeprecated();
  }

  public boolean isHiddenFromSDK() {
    return this.schema.getExtensions() != null
        && this.schema.getExtensions().get(HIDDEN_FROM_CLIENT_SDK) != null
        && (boolean) this.schema.getExtensions().get(HIDDEN_FROM_CLIENT_SDK);
  }

  public boolean isPaginationProperty() {
    return this.schema.getExtensions() != null
        && this.schema.getExtensions().get(IS_PAGINATION_PARAMETER) != null
        && (boolean) this.schema.getExtensions().get(IS_PAGINATION_PARAMETER);
  }

  public boolean isSubResource() {
    return this.schema.getExtensions() != null
        && this.schema.getExtensions().get(IS_SUB_RESOURCE) != null
        && (boolean) this.schema.getExtensions().get(IS_SUB_RESOURCE);
  }

  public boolean isFilterParameters() {
    return this.schema.getExtensions() != null
        && this.schema.getExtensions().get(IS_FILTER_PARAMETER) != null
        && (boolean) this.schema.getExtensions().get(IS_FILTER_PARAMETER);
  }

  public boolean isCompositeArrayBody() {
    return this.schema.getExtensions() != null
        && this.schema.getExtensions().get(Extension.IS_COMPOSITE_ARRAY_REQUEST_BODY) != null
        && (boolean) this.schema.getExtensions().get(Extension.IS_COMPOSITE_ARRAY_REQUEST_BODY);
  }

  public int sortOrder() {
    return schema.getExtensions() != null
            && schema.getExtensions().get(Extension.SORT_ORDER) != null
        ? (int) schema.getExtensions().get(Extension.SORT_ORDER)
        : -1;
  }

  public boolean hasRequiredSubParameters() {
    if (this.schema.getProperties() == null) return false;
    if (this.schema.getRequired() != null && !this.schema.getRequired().isEmpty()) return true;
    return !this.schema.getProperties().values().stream()
        .filter(x -> x.getRequired() != null && !x.getRequired().isEmpty())
        .toList()
        .isEmpty();
  }

  public Map<String, Object> templateParams(DataType lang) {
    return new Attribute(
            CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_UNDERSCORE, name), schema, isRequired)
        .templateParams(lang);
  }

  public int sortOrder(Parameter parameter) {
    return parameter.schema.getExtensions() != null
            && parameter.schema.getExtensions().get(SORT_ORDER) != null
        ? (int) parameter.schema.getExtensions().get(SORT_ORDER)
        : -1;
  }

  public boolean isEnum() {
    return schema instanceof ArraySchema
        ? schema.getItems().getEnum() != null && !schema.getItems().getEnum().isEmpty()
        : schema.getEnum() != null && !schema.getEnum().isEmpty();
  }

  public boolean isGlobalEnum() {
    return schema instanceof ArraySchema
        ? isGlobalEnumAttribute(schema.getItems())
        : isGlobalEnumAttribute(schema);
  }

  private boolean isGlobalEnumAttribute(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_GLOBAL_ENUM) != null
        && (boolean) schema.getExtensions().get(IS_GLOBAL_ENUM);
  }

  public List<String> getEnumValues() {
    if (!isEnum()) {
      return Collections.emptyList();
    }
    return new Enum(schema).values();
  }
}
