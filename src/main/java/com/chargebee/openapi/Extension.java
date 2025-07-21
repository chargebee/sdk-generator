package com.chargebee.openapi;

public class Extension {
  // Operation Extensions
  public static final String OPERATION_METHOD_NAME = "x-cb-operation-method-name";
  public static final String IS_OPERATION_LIST = "x-cb-operation-is-list";
  public static final String IS_BULK_OPERATION = "x-cb-operation-is-bulk";
  public static final String OPERATION_IS_BATCH = "x-cb-operation-is-batch";
  public static final String IS_OPERATION_IDEMPOTENT = "x-cb-operation-is-idempotent";
  public static final String OPERATION_SUB_DOMAIN = "x-cb-operation-sub-domain-name";
  public static final String BATCH_OPERATION_PATH_ID = "x-cb-batch-operation-path-id";
  public static final String IS_OPERATION_NEEDS_JSON_INPUT = "x-cb-is-operation-needs-json-input";
  public static final String IS_OPERATION_NEEDS_INPUT_OBJECT =
      "x-cb-is-operation-needs-input-object";
  public static final String IS_CUSTOM_FIELDS_SUPPORTED = "x-cb-is-custom-fields-supported";
   public static final String IS_GLOBAL_RESOURCE_REFRENCE = "x-cb-is-global-resource-reference";
  public static final String SORT_ORDER = "x-cb-sort-order";

  // Parameter Extensions
  public static final String IS_FILTER_PARAMETER = "x-cb-is-filter-parameter";
  public static final String IS_SUB_RESOURCE = "x-cb-is-sub-resource";
  public static final String IS_PAGINATION_PARAMETER = "x-cb-is-pagination-parameter";
  public static final String IS_PARAMETER_BLANK_OPTION = "x-cb-parameter-blank-option";
  public static final String SDK_FILTER_NAME = "x-cb-sdk-filter-name";

  // Schema Extensions
  public static final String IS_MONEY_COLUMN = "x-cb-is-money-column";
  public static final String IS_LONG_MONEY_COLUMN = "x-cb-is-long-money-column";
  public static final String IS_GLOBAL_ENUM = "x-cb-is-global-enum";
  public static final String GLOBAL_ENUM_REFERENCE = "x-cb-global-enum-reference";
  public static final String IS_EXTERNAL_ENUM = "x-cb-is-external-enum";
  public static final String SDK_ENUM_API_NAME = "x-cb-sdk-enum-api-name";
  public static final String DEPRECATED_ENUM_VALUES = "x-cb-deprecated-enum-values";
  public static final String IS_META_MODEL_AVAILABLE = "x-cb-meta-model-name";
  public static final String ATTRIBUTE_META_COMMENT = "x-cb-attribute-meta-comment";
  public static final String IS_PCV1_ATTRIBUTE = "x-cb-attribute-pcv";
  public static final String IS_API_COLUMN = "x-cb-is-api-column";
  public static final String IS_FOREIGN_KEY_COLUMN = "x-cb-is-foreign-column";
  public static final String IS_MULTI_ATTRIBUTE = "x-cb-is-multi-value-attribute";
  public static final String IS_DEPENDENT_ATTRIBUTE = "x-cb-is-dependent-attribute";
  public static final String IS_COMPOSITE_ARRAY_REQUEST_BODY =
      "x-cb-is-composite-array-request-body";

  // Resource Extensions
  public static final String RESOURCE_ID = "x-cb-resource-id";
  public static final String RESOURCE_PATH_NAME = "x-cb-resource-path-name";
  public static final String IS_GLOBAL_RESOURCE_REFERENCE = "x-cb-is-global-resource-reference";
  public static final String IS_THIRD_PARTY_RESOURCE = "x-cb-is-third-party-resource";
  public static final String IS_DEPENDENT_RESOURCE = "x-cb-is-dependent-resource";
  public static final String SUB_RESOURCE_NAME = "x-cb-sub-resource-name";
  public static final String SUB_RESOURCE_PARENT_NAME = "x-cb-sub-resource-parent-name";

  // API Version and Control Extensions
  public static final String API_VERSION = "x-cb-api-version";
  public static final String PRODUCT_CATALOG_VERSION = "x-cb-product-catalog-version";
  public static final String HIDDEN_FROM_CLIENT_SDK = "x-cb-hidden-from-client-sdk";
  public static final String IS_INTERNAL = "x-cb-internal";
  public static final String MODEL_NAME = "x-cb-module";
  public static final String IS_EAP = "x-cb-is-eap";
  public static final String IS_GEN_SEPARATE = "x-cb-is-gen-separate";
  public static final String IS_PRESENCE_OPERATOR_SUPPORTED = "x-cb-is-presence-operator-supported";

  private Extension() {}
}
