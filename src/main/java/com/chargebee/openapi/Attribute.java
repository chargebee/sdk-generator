package com.chargebee.openapi;

import static com.chargebee.openapi.Extension.*;
import static com.chargebee.openapi.MarkdownHelper.convertHtmlToMarkdown;
import static com.chargebee.openapi.Resource.isSubResourceSchema;

import com.chargebee.QAModeHandler;
import com.chargebee.sdk.DataType;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Attribute {
  public final String name;
  public final Schema<?> schema;
  public final boolean isRequired;
  public final String[] excludedParams = {
    "limit", "offset", "card.copy_billing_info", "card.copy_shipping_info"
  };
  private String description;

  public Attribute(String name, Schema<?> schema, boolean isRequired) {
    this.name = name;
    this.schema = schema;
    this.isRequired = isRequired;
    if (this.schema != null) {
      this.description = this.schema.getDescription();
    }
  }

  public boolean isDependentAttribute() {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_DEPENDENT_ATTRIBUTE) != null
        && (boolean) schema.getExtensions().get(IS_DEPENDENT_ATTRIBUTE);
  }

  public boolean isGlobalResourceReference() {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_GLOBAL_RESOURCE_REFERENCE) != null
        && (boolean) schema.getExtensions().get(IS_GLOBAL_RESOURCE_REFERENCE);
  }

  public boolean isForeignColumn() {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_FOREIGN_KEY_COLUMN) != null
        && (boolean) schema.getExtensions().get(IS_FOREIGN_KEY_COLUMN);
  }

  public boolean isMultiAttribute() {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_MULTI_ATTRIBUTE) != null
        && (boolean) schema.getExtensions().get(IS_MULTI_ATTRIBUTE);
  }

  public boolean isPresenceOperatorSupported() {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_PRESENCE_OPERATOR_SUPPORTED) != null
        && (boolean) schema.getExtensions().get(IS_PRESENCE_OPERATOR_SUPPORTED);
  }

  public boolean isListSubResourceAttribute() {
    if (!(schema instanceof ArraySchema)) return false;
    if (schema.getItems().getProperties() == null) return false;
    var itemSchema = schema.getItems();
    if (itemSchema == null) return false;
    return itemSchema.getExtensions() != null
        && itemSchema.getExtensions().get(IS_SUB_RESOURCE) != null
        && (boolean) itemSchema.getExtensions().get(IS_SUB_RESOURCE);
  }

  public boolean isSubResource() {
    boolean isSubResource =
        schema.getExtensions() != null
            && schema.getExtensions().get(IS_SUB_RESOURCE) != null
            && (boolean) schema.getExtensions().get(IS_SUB_RESOURCE);
    if (!isSubResource && schema.getItems() != null) {
      isSubResource =
          schema.getItems().getExtensions() != null
              && schema.getItems().getExtensions().get(IS_SUB_RESOURCE) != null
              && (boolean) schema.getItems().getExtensions().get(IS_SUB_RESOURCE);
    }
    return isSubResource;
  }

  public boolean isCompositeArrayRequestBody() {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_COMPOSITE_ARRAY_REQUEST_BODY) != null
        && (boolean) schema.getExtensions().get(IS_COMPOSITE_ARRAY_REQUEST_BODY);
  }

  public String subResourceName() {
    if (schema.getExtensions() != null) {
      String subResourceName = (String) schema.getExtensions().get(SUB_RESOURCE_NAME);
      if (subResourceName != null) return subResourceName;
    }
    if (schema.getItems() == null) return null;
    if (schema.getItems().getExtensions() == null) return null;
    return (String) schema.getItems().getExtensions().get(SUB_RESOURCE_NAME);
  }

  public String subResourceParentName() {
    if (schema.getExtensions() == null) return null;
    return (String) schema.getExtensions().get(SUB_RESOURCE_PARENT_NAME);
  }

  public boolean isEnumAttribute() {
    return schema instanceof ArraySchema
        ? schema.getItems().getEnum() != null && !schema.getItems().getEnum().isEmpty()
        : schema.getEnum() != null && !schema.getEnum().isEmpty();
  }

  public Enum getEnum() {
    return new Enum(name, schema);
  }

  public boolean isGlobalEnumAttribute(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_GLOBAL_ENUM) != null
        && (boolean) schema.getExtensions().get(IS_GLOBAL_ENUM);
  }

  public boolean isGlobalEnumAttribute() {
    return schema instanceof ArraySchema
        ? isGlobalEnumAttribute(schema.getItems())
        : isGlobalEnumAttribute(schema);
  }

  public int sortOrder() {
    int sortOrder =
        schema.getExtensions() != null && schema.getExtensions().get(SORT_ORDER) != null
            ? (int) schema.getExtensions().get(SORT_ORDER)
            : -1;
    if (sortOrder == -1) {
      sortOrder =
          schema.getItems() != null
                  && schema.getItems().getExtensions() != null
                  && schema.getItems().getExtensions().get(SORT_ORDER) != null
              ? (int) schema.getItems().getExtensions().get(SORT_ORDER)
              : -1;
    }
    return sortOrder;
  }

  public Map<String, Object> templateParams(DataType lang) {
    var dataType = lang.dataType(schema);
    if (dataType == null) {
      return Map.of();
    }

    Map<String, Object> params =
        new HashMap<>(
            Map.of(
                "name", name,
                "isRequired", isRequired,
                "isDependentAttribute", isDependentAttribute(),
                "isDeprecated", schema.getDeprecated() != null && schema.getDeprecated(),
                "isEnumAttribute", isEnumAttribute(),
                "isGlobalEnum", isGlobalEnumAttribute(),
                "isExternalEnum", isExternalEnum(),
                "isListAttribute", isListAttribute(),
                "isSubResource", isSubResourceSchema(schema),
                "hasSubAttributes", schema.getProperties() != null));

    if (description != null) {
      params.put("description", convertHtmlToMarkdown(description));
    }
    if (schema.getProperties() != null) {
      params.put("attributes", attributes().stream().map(a -> a.templateParams(lang)).toList());
    }
    params.put("deprecationMessage", getDeprecatedMessage());
    params.put("type", dataType);
    return params;
  }

  public String getDeprecatedMessage() {
    if (schema.getExtensions() != null
        && schema.getExtensions().get("x-cb-deprecation-message") != null) {
      return (String) schema.getExtensions().get("x-cb-deprecation-message");
    }
    return "Please refer API docs to use other attributes";
  }

  public List<Attribute> attributes() {
    if (schema.getProperties() != null) {
      return schema.getProperties().entrySet().stream()
          .map(
              e ->
                  new Attribute(
                      CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_UNDERSCORE, e.getKey()),
                      e.getValue(),
                      schema.getRequired() != null && schema.getRequired().contains(e.getKey())))
          .filter(a -> a.schema != null)
          .filter(Attribute::isNotHiddenAttribute)
          .toList();
    } else if (schema.getItems() != null && schema.getItems().getProperties() != null) {
      return schema.getItems().getProperties().entrySet().stream()
          .map(
              e ->
                  new Attribute(
                      CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_UNDERSCORE, e.getKey()),
                      e.getValue(),
                      schema.getItems().getRequired() != null
                          && schema.getItems().getRequired().contains(e.getKey())))
          .filter(Attribute::isNotHiddenAttribute)
          .toList();
    }
    return new ArrayList<>();
  }

  public boolean isListAttribute() {
    return schema.getType() != null
        && schema.getType().equals("array")
        && schema.getItems().getType() != null;
  }

  public boolean isFilterAttribute() {
    boolean isFilterAttribute =
        schema.getExtensions() != null
            && schema.getExtensions().get(IS_FILTER_PARAMETER) != null
            && (boolean) schema.getExtensions().get(IS_FILTER_PARAMETER);
    if (isFilterAttribute) return true;
    if (attributes() != null && !attributes().isEmpty()) {
      return attributes().stream().anyMatch(Attribute::isFilterAttribute);
    }
    return false;
  }

  public boolean isPcv1Attribute() {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_PCV1_ATTRIBUTE) != null
        && (int) schema.getExtensions().get(IS_PCV1_ATTRIBUTE) == 1;
  }

  public boolean isNotHiddenAttribute() {
    boolean isVisible =
        schema.getExtensions() == null
            || schema.getExtensions().get(HIDDEN_FROM_CLIENT_SDK) == null
            || !((boolean) schema.getExtensions().get(HIDDEN_FROM_CLIENT_SDK)
                && !QAModeHandler.getInstance().getValue());
    if (isVisible) {
      isVisible =
          schema.getProperties() == null
              || schema.getProperties().values().isEmpty()
              || schema.getProperties().values().stream()
                  .anyMatch(
                      v ->
                          v.getExtensions() == null
                              || v.getExtensions().get(HIDDEN_FROM_CLIENT_SDK) == null
                              || !((boolean) v.getExtensions().get(HIDDEN_FROM_CLIENT_SDK)
                                  && !QAModeHandler.getInstance().getValue()));
    }
    if (isVisible) {
      isVisible =
          schema.getItems() == null
              || schema.getItems().getExtensions() == null
              || schema.getItems().getExtensions().get(HIDDEN_FROM_CLIENT_SDK) == null
              || !((boolean) schema.getItems().getExtensions().get(HIDDEN_FROM_CLIENT_SDK)
                  && !QAModeHandler.getInstance().getValue());
    }
    return isVisible;
  }

  public boolean isDataTypeBigDecimal(DataType lang) {
    var dataType = lang.dataType(schema);
    return dataType != null && dataType.equals("BigDecimal");
  }

  public boolean isDeprecated() {
    boolean isDeprecated = schema.getDeprecated() != null && schema.getDeprecated();
    if (!isDeprecated && schema.getItems() != null) {
      isDeprecated = schema.getItems().getDeprecated() != null && schema.getItems().getDeprecated();
    }
    return isDeprecated;
  }

  public boolean isHiddenParameter() {
    if (schema instanceof ArraySchema) {
      if (schema.getItems() == null) return false;
      if (schema.getItems().getExtensions() == null) return false;
      if (schema.getItems().getExtensions().get(HIDDEN_FROM_CLIENT_SDK) == null) return false;
      return (boolean) schema.getItems().getExtensions().get(HIDDEN_FROM_CLIENT_SDK)
          && !QAModeHandler.getInstance().getValue();
    }
    return false;
  }

  public String metaModelName() {
    String metaModel =
        schema.getExtensions() == null
            ? null
            : (String) schema.getExtensions().get(IS_META_MODEL_AVAILABLE);
    if (metaModel != null) return metaModel;
    if (schema.getItems() == null) return null;
    if (schema.getItems().getExtensions() == null) return null;
    return (String) schema.getItems().getExtensions().get(IS_META_MODEL_AVAILABLE);
  }

  public String getEnumApiName() {
    if (schema.getExtensions() == null) return null;
    return (String) schema.getExtensions().get(SDK_ENUM_API_NAME);
  }

  public boolean isExternalEnum() {
    boolean isExternalEnum =
        schema.getExtensions() != null
            && schema.getExtensions().get(IS_EXTERNAL_ENUM) != null
            && ((boolean) schema.getExtensions().get(IS_EXTERNAL_ENUM));
    if (isExternalEnum) return true;
    if (schema.getItems() == null) return false;
    if (schema.getItems().getExtensions() == null) return false;
    if (schema.getItems().getExtensions().get(IS_EXTERNAL_ENUM) == null) return false;
    return (boolean) schema.getItems().getExtensions().get(IS_EXTERNAL_ENUM)
        && !QAModeHandler.getInstance().getValue();
  }

  public boolean isApi() {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_API_COLUMN) != null
        && ((boolean) schema.getExtensions().get(IS_API_COLUMN));
  }

  public boolean isGenSeparate(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_GEN_SEPARATE) != null
        && ((boolean) schema.getExtensions().get(IS_GEN_SEPARATE));
  }

  public boolean isGenSeparate() {
    return schema instanceof ArraySchema ? isGenSeparate(schema.getItems()) : isGenSeparate(schema);
  }

  public String getFilterType() {
    if (schema.getExtensions() == null) return null;
    return (String) schema.getExtensions().get(SDK_FILTER_NAME);
  }

  public boolean isListOfSimpleType() {
    return isListAttribute()
        && !isSubResource()
        && !(paramBlankOption() != null && paramBlankOption().equals("not_allowed"))
        && !isListOfEnum();
  }

  public boolean isListOfEnum() {
    return isListAttribute()
        && !isSubResource()
        && schema.getItems() != null
        && schema.getItems().getEnum() != null;
  }

  public Schema getSchema() {
    return schema;
  }

  public boolean isContentObjectAttribute() {
    return schema instanceof ObjectSchema
        && schema.getProperties() == null
        && name.equals("content");
  }

  public boolean isPaginationProperty() {
    return this.schema.getExtensions() != null
        && this.schema.getExtensions().get(IS_PAGINATION_PARAMETER) != null
        && (boolean) this.schema.getExtensions().get(IS_PAGINATION_PARAMETER);
  }

  public String paramBlankOption() {
    if (schema.getExtensions() == null) return null;
    if (schema.getExtensions().get(IS_PARAMETER_BLANK_OPTION) == null) return null;
    return (String) schema.getExtensions().get(IS_PARAMETER_BLANK_OPTION);
  }

  public boolean isAttributeMetaCommentRequired() {
    return schema.getExtensions() != null
        && schema.getExtensions().get(ATTRIBUTE_META_COMMENT) != null
        && schema.getExtensions().get(ATTRIBUTE_META_COMMENT).equals("required");
  }

  public boolean isGlobalEnumReference() {
    return schema.getExtensions() != null
        && !schema.getExtensions().isEmpty()
        && schema.getExtensions().get(GLOBAL_ENUM_REFERENCE) != null;
  }

  public boolean isSortAttribute() {
    return name.equals("sort_by");
  }
}
