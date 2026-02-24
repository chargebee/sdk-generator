package com.chargebee.sdk.java.v4.core;

import static com.chargebee.openapi.Extension.IS_MONEY_COLUMN;

import com.chargebee.sdk.java.v4.datatype.EnumType;
import com.chargebee.sdk.java.v4.datatype.FieldType;
import com.chargebee.sdk.java.v4.datatype.ListType;
import com.chargebee.sdk.java.v4.datatype.ObjectType;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.List;

@lombok.Data
public class Field {
  private String name;
  private FieldType type;
  private boolean deprecated;
  private Model subModel;
  private String curlName;
  private boolean isFilter;
  private String filterType;
  private String filterSdkName; // e.g., "NumberFilter", "TimestampFilter", "StringFilter"
  private List<String> supportedOperations = new ArrayList<>();
  private List<String> filterEnumValues; // Enum values for filter fields (e.g., Status values)
  private boolean isSort;
  private List<String> sortableFields = new ArrayList<>();
  private boolean subModelField;
  private boolean compositeArrayField;
  private boolean required;

  public String getName() {
    if (name == null) return null;
    // Replace dots with underscores to handle field names like "card.copy_billing_info"
    String normalizedName = name.replace('.', '_');
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, normalizedName);
  }

  public String getCapitalizedName() {
    if (name == null) return null;
    // Replace dots with underscores to handle field names like "card.copy_billing_info"
    String normalizedName = name.replace('.', '_');
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, normalizedName);
  }

  public String getCurlName() {
    return name;
  }

  public boolean isObjectType() {
    return type instanceof ObjectType;
  }

  public boolean isComplexObjectType() {
    if (type instanceof ObjectType objectType) {
      var schema = objectType.schema();
      return schema != null && schema.getProperties() != null && !schema.getProperties().isEmpty();
    }
    return false;
  }

  /**
   * True when this field is an object type that references another schema via $ref.
   * In such cases, we should parse using <Type>.fromJson rather than treating it as a plain Object.
   */
  public boolean isRefObjectType() {
    if (type instanceof ObjectType objectType) {
      return objectType.schema() == null;
    }
    return false;
  }

  /**
   * True when this field is an object type without defined properties (a free-form JSON object).
   * In such cases, we should use JsonUtil.getObject and keep it as Object/String JSON.
   */
  public boolean isPlainObjectType() {
    if (type instanceof ObjectType objectType) {
      var schema = objectType.schema();
      if (schema != null && (schema.getProperties() == null || schema.getProperties().isEmpty())) {
        // If it has additionalProperties, it's a Map type, not a plain object
        if (schema.getAdditionalProperties() != null) {
          return false;
        }
        return true;
      }
    }
    return false;
  }

  /**
   * Getter for Handlebars template (maps to isMapType).
   */
  public boolean getMapType() {
    return isMapType();
  }

  /**
   * True when this field is a Map type (object with additionalProperties but no defined properties).
   * In such cases, we should return Map<String, Object>.
   */
  public boolean isMapType() {
    // First check if the type string indicates it's a Map
    if (type != null && type.display() != null && type.display().startsWith("java.util.Map")) {
      return true;
    }

    // Then check the schema for additionalProperties
    if (type instanceof ObjectType objectType) {
      var schema = objectType.schema();
      if (schema != null && (schema.getProperties() == null || schema.getProperties().isEmpty())) {
        Object additionalProps = schema.getAdditionalProperties();
        if (additionalProps instanceof Boolean && (Boolean) additionalProps) {
          return true;
        }
        if (additionalProps instanceof Schema) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isEnumType() {
    return type instanceof EnumType;
  }

  public boolean isListType() {
    return type instanceof ListType;
  }

  public String getListElementType() {
    if (type instanceof ListType listType) {
      var schema = listType.schema();
      if (schema == null) return null;
      if (!(schema instanceof ArraySchema arraySchema)) {
        return null;
      }
      Schema<?> items = arraySchema.getItems();

      if (items != null && items.get$ref() != null) {
        return items.get$ref().substring(items.get$ref().lastIndexOf("/") + 1);
      }
      if (items != null && items.getProperties() != null && !items.getProperties().isEmpty()) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, listType.fieldName());
      }
      if (items != null && "object".equals(items.getType())) {
        // If it's a free-form object (no properties), treat as Map
        if (items.getProperties() == null || items.getProperties().isEmpty()) {
          return "java.util.Map<String, Object>";
        }
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, listType.fieldName());
      }

      // Items with no type specified → generic array, matching List<Object> from display()
      if (items == null || items.getType() == null || items.getType().isEmpty()) {
        return "Object";
      }

      // For primitive types like string, integer, etc.
      if ("string".equals(items.getType())) {
        return "String";
      }
      if ("integer".equals(items.getType())) {
        String fmt = items.getFormat();
        if ("int64".equals(fmt)) {
          return "Long";
        }
        // Check for money column extension - money columns should be Long
        if (isMoneyColumn(items)) {
          return "Long";
        }
        return "Integer";
      }
      if ("boolean".equals(items.getType())) {
        return "Boolean";
      }
      if ("number".equals(items.getType())) {
        return "Double";
      }

      // Default for unspecified or other types
      return "Object";
    }
    return null;
  }

  public boolean isListOfObjects() {
    if (type instanceof ListType listType) {
      var schema = listType.schema();
      if (schema == null) return false;
      if (!(schema instanceof ArraySchema arraySchema)) {
        return false;
      }
      Schema<?> items = arraySchema.getItems();

      return items != null
          && (items.get$ref() != null
              || (items.getProperties() != null && !items.getProperties().isEmpty()));
    }
    return false;
  }

  public String getGetterName() {
    if (name == null) return "get";
    // Replace dots with underscores to handle field names like "card.copy_billing_info"
    String normalizedName = name.replace('.', '_');
    return "get" + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, normalizedName);
  }

  public String getSubModelParamsType() {
    if (subModel != null && subModel.getName() != null) {
      return subModel.getName() + "Params";
    }
    if (name == null) return null;
    // Replace dots with underscores to handle field names like "card.copy_billing_info"
    String normalizedName = name.replace('.', '_');
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, normalizedName) + "Params";
  }

  public boolean isSubModelHasFilterFields() {
    return subModel != null && subModel.hasFilterFields();
  }

  public String getSortBuilderType() {
    if (name == null) return null;
    // Replace dots with underscores to handle field names like "card.copy_billing_info"
    String normalizedName = name.replace('.', '_');
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, normalizedName) + "SortBuilder";
  }

  /**
   * Returns true if this filter field has typed enum values.
   */
  public boolean hasFilterEnum() {
    return filterEnumValues != null && !filterEnumValues.isEmpty();
  }

  /**
   * Returns true if this is a NumberFilter (uses Long for values).
   */
  public boolean isNumberFilter() {
    return "NumberFilter".equals(filterSdkName);
  }

  /**
   * Returns true if this is a TimestampFilter (uses Timestamp for values).
   */
  public boolean isTimestampFilter() {
    return "TimestampFilter".equals(filterSdkName);
  }

  /**
   * Returns true if this is a BooleanFilter (uses Boolean for values).
   */
  public boolean isBooleanFilter() {
    return "BooleanFilter".equals(filterSdkName);
  }

  /**
   * Returns the enum type name for this filter field (e.g., "Status" for status filter).
   */
  public String getFilterEnumType() {
    if (name == null) return null;
    String normalizedName = name.replace('.', '_');
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, normalizedName);
  }

  /**
   * Returns the filter enum values formatted for template (key-value pairs).
   */
  public List<java.util.Map<String, String>> getFilterEnumFormatted() {
    if (filterEnumValues == null) return null;
    var formatted = new ArrayList<java.util.Map<String, String>>();
    for (String value : filterEnumValues) {
      var entry = new java.util.LinkedHashMap<String, String>();
      entry.put("key", CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_UNDERSCORE, value));
      entry.put("value", value);
      formatted.add(entry);
    }
    return formatted;
  }

  /**
   * Check if the schema has the x-cb-is-money-column extension set to true.
   * Money columns should be represented as Long in Java.
   */
  private static boolean isMoneyColumn(Schema<?> schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_MONEY_COLUMN) != null
        && (boolean) schema.getExtensions().get(IS_MONEY_COLUMN);
  }
}
