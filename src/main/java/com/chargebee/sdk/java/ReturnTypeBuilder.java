package com.chargebee.sdk.java;

import static com.chargebee.GenUtil.*;
import static com.chargebee.openapi.Extension.GLOBAL_ENUM_REFERENCE;

import com.chargebee.GenUtil;
import com.chargebee.openapi.*;
import com.chargebee.openapi.Enum;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ReturnTypeBuilder {

  private Attribute attribute;
  private Action action;
  private String parentName;
  private Function<Schema, String> dataTypeMethod;

  private ApiVersion apiVersion;
  private GenerationMode generationMode;
  private BiFunction<Attribute, String, String> getFullNameMethod;
  private Resource activeResource;
  private List<Resource> resourceList;
  private List<Enum> globalEnum;

  public ReturnTypeBuilder setAttribute(Attribute attribute) {
    this.attribute = attribute;
    return this;
  }

  public ReturnTypeBuilder setGetFullNameMethod(
      BiFunction<Attribute, String, String> getFullNameMethod) {
    this.getFullNameMethod = getFullNameMethod;
    return this;
  }

  public ReturnTypeBuilder setAction(Action action) {
    this.action = action;
    return this;
  }

  public ReturnTypeBuilder setParentName(String parentName) {
    this.parentName = parentName;
    return this;
  }

  public ReturnTypeBuilder setDataTypeMethod(Function<Schema, String> dataTypeMethod) {
    this.dataTypeMethod = dataTypeMethod;
    return this;
  }

  public ReturnTypeBuilder setActiveResource(Resource activeResource) {
    this.activeResource = activeResource;
    return this;
  }

  public ReturnTypeBuilder setResourceList(List<Resource> resourceList) {
    this.resourceList = resourceList;
    return this;
  }

  public ReturnTypeBuilder setApiVersion(ApiVersion apiVersion) {
    this.apiVersion = apiVersion;
    return this;
  }

  public ReturnTypeBuilder setGenerationMode(GenerationMode generationMode) {
    this.generationMode = generationMode;
    return this;
  }

  public String getClazName(Action action) {
    String methodName = action.name;
    if (action.id.contains(Constants.BATCH)) {
      return "BatchRequest";
    }
    if (action.isListResourceAction()) {
      methodName = action.resourceId() + "_" + action.name;
    }
    return GenUtil.toClazName(methodName, Constants.REQUEST);
  }

  private String getPackagePrefix() {
    return "com.chargebee"
        + (generationMode.equals(GenerationMode.INTERNAL) && apiVersion.equals(ApiVersion.V2)
            ? ".v2"
            : "");
  }

  private String multiBoolean(Attribute attribute) {
    if (!attribute.attributes().isEmpty()
        && attribute.attributes().get(0).schema.getFormat() != null
        && attribute.attributes().get(0).schema.getFormat().equals(Constants.BOOLEAN_TYPE)) {
      return getClazName(action);
    }
    return null;
  }

  private String singleString(Attribute attribute) {
    if (!attribute.isEnumAttribute()
        && dataTypeMethod.apply(attribute.schema).equals(Constants.STRING_TYPE)) {
      return getClazName(action);
    }
    return null;
  }

  private String eventTypeEnum(Attribute attribute) {
    if (attribute.isGlobalEnumAttribute() && attribute.name.equalsIgnoreCase("type")) {
      return getPackagePrefix()
          + Constants.MODELS_DOT_ENUMS
          + toClazName(attribute.name)
          + ", "
          + getClazName(action);
    }
    return null;
  }

  private String singleEnum(Attribute attribute) {
    if (attribute.isEnumAttribute()
        && attribute.schema.getEnum() != null
        && !attribute.schema.getEnum().isEmpty()) {
      if (attribute.schema.getExtensions() != null
          && !attribute.schema.getExtensions().isEmpty()
          && attribute.schema.getExtensions().get(GLOBAL_ENUM_REFERENCE) != null) {
        return getPackagePrefix()
            + Constants.MODELS_DOT_ENUMS
            + toCamelCase(attribute.name)
            + ", "
            + getClazName(action);
      }
      if (Boolean.TRUE.equals(attribute.schema.getDeprecated())) {
        return toClazName(attribute.name) + ", " + getClazName(action);
      }
      return (activeResource.name) + "." + toClazName(attribute.name) + ", " + getClazName(action);
    }

    return null;
  }

  private String multiInteger(Attribute attribute) {
    if (!attribute.attributes().isEmpty()
        && attribute.attributes().stream().anyMatch(a -> a.schema.getMinLength() != null)) {
      return getClazName(action);
    }
    return null;
  }

  private String multiEnum(Attribute attribute) {
    if (attribute.attributes().get(0).isEnumAttribute()) {
      return getFullNameMethod.apply(attribute, parentName) + ", " + getClazName(action);
    }
    if (attribute.attributes().stream().anyMatch(Attribute::isGlobalEnumAttribute)) {
      return getPackagePrefix()
          + Constants.MODELS_DOT_ENUMS
          + toClazName(attribute.name)
          + ", "
          + getClazName(action);
    }
    return null;
  }

  private String multiDateTime(Attribute attribute) {
    if (attribute.attributes().stream()
            .noneMatch(
                a ->
                    a.schema.getFormat() != null
                        && (a.schema.getFormat().equals(Constants.NUMBER_TYPE)
                            || a.schema.getFormat().equals("unix-time")))
        && attribute.attributes().stream().anyMatch(Attribute::isEnumAttribute)) {
      if (parentName != null) {
        String name = attribute.name;
        if (globalEnum.stream().anyMatch(e -> e.name.equals(toClazName(name)))) {
          return getPackagePrefix()
              + Constants.MODELS_DOT_ENUMS
              + toCamelCase(attribute.name)
              + ", "
              + getClazName(action);
        }
        return toClazName(parentName)
            + "."
            + toCamelCase(attribute.name)
            + ", "
            + getClazName(action);
      }
      return toClazName(activeResource.name)
          + "."
          + toCamelCase(attribute.name)
          + ", "
          + getClazName(action);
    }
    return null;
  }

  private boolean isSubResource() {
    return parentName != null
        && resourceList.stream().anyMatch(r -> r.name.equals(toClazName(parentName)));
  }

  private String subResourceNumberType(Attribute attribute) {
    if (isSubResource()) {
      String attributeName = attribute.name;
      Optional<Resource> refResourceOption =
          resourceList.stream().filter(r -> r.name.equals(toClazName(parentName))).findFirst();
      if (refResourceOption.isEmpty()) {
        return null;
      }
      Optional<Attribute> resourceLevelAttributeOption =
          refResourceOption.get().attributes().stream()
              .filter(a -> a.name.equals(attributeName))
              .findFirst();

      if (resourceLevelAttributeOption.isPresent()) {
        Attribute resourceLevelAttribute = resourceLevelAttributeOption.get();
        if (resourceLevelAttribute.isEnumAttribute()) {
          if (resourceLevelAttribute.isGlobalEnumReference()) {
            return getPackagePrefix() + Constants.MODELS_DOT_ENUMS + toCamelCase(attributeName);
          }
          if (Boolean.TRUE.equals(resourceLevelAttribute.schema.getDeprecated())) {
            return toClazName(attributeName);
          }
          return (activeResource.name)
              + "."
              + toClazName(attributeName)
              + ", "
              + getClazName(action);
        }
        return dataTypeMethod.apply(resourceLevelAttribute.schema) + ", " + getClazName(action);
      }
    }
    return null;
  }

  private String subResourceEnumReturnType(Attribute attribute) {
    if (attribute.attributes().get(0).schema.getEnum() != null
        && !attribute.attributes().get(0).schema.getEnum().isEmpty()) {
      if (attribute.attributes().get(0).schema.getExtensions() != null
          && !attribute.attributes().get(0).schema.getExtensions().isEmpty()
          && attribute.attributes().get(0).schema.getExtensions().get(GLOBAL_ENUM_REFERENCE)
              != null) {
        return getPackagePrefix()
            + Constants.MODELS_DOT_ENUMS
            + toCamelCase(attribute.name)
            + ", "
            + getClazName(action);
      }
      if (Boolean.TRUE.equals(attribute.attributes().get(0).schema.getDeprecated())) {
        return toClazName(attribute.name) + ", " + getClazName(action);
      }
      return (activeResource.name) + "." + toClazName(attribute.name);
    }
    return null;
  }

  private String subResourceReturnType(Attribute attribute) {
    if (!attribute.attributes().isEmpty() && attribute.isSubResource()) {
      Attribute childAttribute = attribute.attributes().get(0);
      if (childAttribute.schema.getFormat() != null
          && childAttribute.schema.getFormat().equals(Constants.NUMBER_TYPE)) {

        String subResourceNumberType = subResourceNumberType(attribute);
        if (subResourceNumberType != null) {
          return subResourceNumberType;
        }
        String subResourceEnumReturnType = subResourceEnumReturnType(attribute);
        if (subResourceEnumReturnType != null) {
          return subResourceEnumReturnType;
        }
        return dataTypeMethod.apply(attribute.attributes().get(0).schema)
            + ", "
            + getClazName(action);
      }
      if (dataTypeMethod.apply(childAttribute.schema).equals(Constants.DATE_TIME)) {
        return getClazName(action);
      }
    }
    return null;
  }

  private String resourceLevelAttribute(Attribute attribute) {
    if (!attribute.attributes().isEmpty()
        && activeResource.attributes().stream().anyMatch(a -> a.name.equals(attribute.name))) {
      Optional<Attribute> resourceLevelAttributeOption =
          activeResource.attributes().stream()
              .filter(a -> a.name.equals(attribute.name))
              .findFirst();
      if (resourceLevelAttributeOption.isPresent()) {
        Attribute resourceLevelAttribute = resourceLevelAttributeOption.get();
        if (dataTypeMethod.apply(resourceLevelAttribute.schema).equals("Timestamp")) {
          return getClazName(action);
        }
        return dataTypeMethod.apply(resourceLevelAttribute.schema) + ", " + getClazName(action);
      }
    }
    return null;
  }

  private String filterTimestamp(Attribute attribute) {
    if (attribute.isFilterAttribute()
        && dataTypeMethod.apply(attribute.schema).equalsIgnoreCase("TimestampFilter")) {
      return getClazName(action);
    }
    return null;
  }

  private String resourceLevelMultiAttribute(Attribute attribute) {
    if (!attribute.attributes().isEmpty()) {
      Optional<Resource> resource =
          resourceList.stream()
              .filter(r -> r.name.equalsIgnoreCase(singularize(action.name)))
              .findFirst();
      if (resource.isPresent()
          && resource.get().attributes().stream().anyMatch(a -> a.name.equals(attribute.name))) {
        Optional<Attribute> resourceLevelAttributeOption =
            resource.get().attributes().stream()
                .filter(a -> a.name.equals(attribute.name))
                .findFirst();
        if (resourceLevelAttributeOption.isPresent()) {
          Attribute resourceLevelAttribute = resourceLevelAttributeOption.get();
          return dataTypeMethod.apply(resourceLevelAttribute.schema) + ", " + getClazName(action);
        }
      }

      return dataTypeMethod.apply(
              attribute.schema.getProperties().get(attribute.attributes().get(0).name))
          + ", "
          + getClazName(action);
    }
    return null;
  }

  private String multiAttributeReturnType(Attribute attribute) {

    if (!attribute.attributes().isEmpty()) {
      String multiEnum = multiEnum(attribute);
      if (multiEnum != null) {
        return multiEnum;
      }
      return multiDateTime(attribute);
    }
    return null;
  }

  public String build() {
    if (!attribute.attributes().isEmpty()
        && attribute.attributes().stream().anyMatch(a -> a.schema instanceof ObjectSchema)) {
      attribute = attribute.attributes().get(0);
    }
    String multiBool = multiBoolean(attribute);
    if (multiBool != null) {
      return multiBool;
    }
    if (attribute.name.equals(Constants.SORT_BY)) {
      return getClazName(action);
    }
    String singleString = singleString(attribute);
    if (singleString != null) {
      return singleString;
    }
    if (dataTypeMethod.apply(attribute.schema).equals(Constants.DATE_TIME)) {
      return getClazName(action);
    }
    String eventTypeEnum = eventTypeEnum(attribute);
    if (eventTypeEnum != null) {
      return eventTypeEnum;
    }
    String singleEnum = singleEnum(attribute);
    if (singleEnum != null) {
      return singleEnum;
    }
    String multiInteger = multiInteger(attribute);
    if (multiInteger != null) {
      return multiInteger;
    }
    String multiAttribute = multiAttributeReturnType(attribute);
    if (multiAttribute != null) {
      return multiAttribute;
    }
    String subResourceReturnType = subResourceReturnType(attribute);
    if (subResourceReturnType != null) {
      return subResourceReturnType;
    }

    String resourceLevelAttribute = resourceLevelAttribute(attribute);
    if (resourceLevelAttribute != null) {
      return resourceLevelAttribute;
    }

    String filterTimestamp = filterTimestamp(attribute);
    if (filterTimestamp != null) {
      return filterTimestamp;
    }

    String resourceLevelMultiAttribute = resourceLevelMultiAttribute(attribute);
    if (resourceLevelMultiAttribute != null) {
      return resourceLevelMultiAttribute;
    }

    return getClazName(action);
  }

  public ReturnTypeBuilder setGlobalEnums(List<Enum> globalEnums) {
    this.globalEnum = globalEnums;
    return this;
  }
}
