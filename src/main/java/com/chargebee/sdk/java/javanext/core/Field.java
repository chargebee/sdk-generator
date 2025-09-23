package com.chargebee.sdk.java.javanext.core;

import com.chargebee.sdk.java.javanext.datatype.EnumType;
import com.chargebee.sdk.java.javanext.datatype.FieldType;
import com.chargebee.sdk.java.javanext.datatype.ListType;
import com.chargebee.sdk.java.javanext.datatype.ObjectType;
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
  private List<String> supportedOperations = new ArrayList<>();
  private boolean isSort;
  private List<String> sortableFields = new ArrayList<>();
  private boolean subModelField;

  public String getName() {
    if (name == null) return null;
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name);
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
      return schema != null && (schema.getProperties() == null || schema.getProperties().isEmpty());
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
        return "Object";
      }
      // Default for primitive arrays
      return "String";
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
    return "get" + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
  }

  public String getSubModelParamsType() {
    if (subModel != null && subModel.getName() != null) {
      return subModel.getName() + "Params";
    }
    if (name == null) return null;
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name) + "Params";
  }

  public String getSortBuilderType() {
    if (name == null) return null;
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name) + "SortBuilder";
  }
}
