package com.chargebee.sdk.dotnet;

import static com.chargebee.GenUtil.singularize;
import static com.chargebee.GenUtil.toClazName;
import static com.chargebee.sdk.dotnet.Constants.*;
import static com.chargebee.sdk.dotnet.Constants.DATE_TIME;

import com.chargebee.GenUtil;
import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class ReturnTypeBuilder {

  private Attribute attribute;
  private Action action;
  private String parentName;
  private Function<Schema, String> dataTypeMethod;
  private Function<Attribute, String> getFullNameDotnetMethod;

  private Resource activeResource;
  private List<Resource> resourceList;

  public ReturnTypeBuilder setAttribute(Attribute attribute) {
    this.attribute = attribute;
    return this;
  }

  public ReturnTypeBuilder setGetFullNameDotnetMethod(
      Function<Attribute, String> getFullNameDotnetMethod) {
    this.getFullNameDotnetMethod = getFullNameDotnetMethod;
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

  public String getClazName(Action action) {
    String methodName = action.name;
    if (action.isListResourceAction()) {
      methodName = action.resourceId() + "_" + action.name;
    }
    return GenUtil.toClazName(methodName, "Request");
  }

  private String getName(String name) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
  }

  private boolean isBooleanAttribute() {
    return !attribute.attributes().isEmpty()
        && attribute.attributes().get(0).schema.getFormat() != null
        && attribute.attributes().get(0).schema.getFormat().equals("boolean");
  }

  public String returnTypeWithoutAttribute() {
    if (isBooleanAttribute()) {
      return getClazName(action);
    }
    if (attribute.name.equals(SORT_BY)) {
      return getClazName(action);
    }
    if (!attribute.isEnumAttribute() && dataTypeMethod.apply(attribute.schema).equals(STING_TYPE)) {
      return getClazName(action);
    }
    if (dataTypeMethod.apply(attribute.schema).equals(DATE_TIME)) {
      return getClazName(action);
    }
    if (attribute.isEnumAttribute()) {
      if (attribute.schema.getEnum() != null && !attribute.schema.getEnum().isEmpty()) {
        return (activeResource.name)
            + "."
            + getName(attribute.name + ENUM_SUFFIX)
            + ", "
            + getClazName(action);
      }
      return dataTypeMethod.apply(attribute.schema) + ", " + getClazName(action);
    }
    return null;
  }

  private String subAttributesWithEnum() {
    if (attribute.attributes().stream()
            .noneMatch(
                a ->
                    a.schema.getFormat() != null
                        && (a.schema.getFormat().equals(NUMBER_TYPE)
                            || a.schema.getFormat().equals(UNIX_TIME)))
        && attribute.attributes().stream().anyMatch(Attribute::isEnumAttribute)) {
      return toClazName(activeResource.name)
          + "."
          + getName(attribute.name + ENUM_SUFFIX)
          + ", "
          + getClazName(action);
    }
    return null;
  }

  public String build() {
    String returnTypeWithoutSubAttribute = returnTypeWithoutAttribute();
    if (returnTypeWithoutSubAttribute != null) {
      return returnTypeWithoutSubAttribute;
    }
    if (attribute.attributes().isEmpty()) {
      return getClazName(action);
    }
    if (attribute.attributes().stream().anyMatch(a -> a.schema.getMinLength() != null)) {
      return getClazName(action);
    }
    if (attribute.attributes().get(0).isEnumAttribute()) {
      return getFullNameDotnetMethod.apply(attribute) + ", " + getClazName(action);
    }
    String subAttributesWithEnum = subAttributesWithEnum();
    if (subAttributesWithEnum != null) {
      return subAttributesWithEnum;
    }
    if (attribute.isSubResource()) {
      String returnType = handleSubResourceReturnType();
      if (returnType != null) {
        return returnType;
      }
    }
    String resourceLevelReturnType = resourceLevelAttributeReturnType();
    if (resourceLevelReturnType != null) {
      return resourceLevelReturnType;
    }
    Optional<Resource> resource =
        resourceList.stream()
            .filter(r -> r.name.equalsIgnoreCase(singularize(action.name)))
            .findFirst();
    if (resource.isPresent()
        && resource.get().attributes().stream().anyMatch(a -> a.name.equals(attribute.name))) {
      Optional<Attribute> selectedAttribute =
          resource.get().attributes().stream()
              .filter(a -> a.name.equals(attribute.name))
              .findFirst();
      if (selectedAttribute.isPresent()) {
        Attribute resourceLevelAttribute = selectedAttribute.get();
        return dataTypeMethod.apply(resourceLevelAttribute.schema) + ", " + getClazName(action);
      }
    }
    return dataTypeMethod.apply(
            attribute.schema.getProperties().get(attribute.attributes().get(0).name))
        + ", "
        + getClazName(action);
  }

  private String resourceLevelAttributeReturnType() {
    if (activeResource.attributes().stream().anyMatch(a -> a.name.equals(attribute.name))) {
      Optional<Attribute> selectedAttribute =
          activeResource.attributes().stream()
              .filter(a -> a.name.equals(attribute.name))
              .findFirst();
      if (selectedAttribute.isPresent()) {
        Attribute resourceLevelAttribute = selectedAttribute.get();
        if (dataTypeMethod.apply(resourceLevelAttribute.schema).equals(DATE_TIME)) {
          return getClazName(action);
        }
        return dataTypeMethod.apply(resourceLevelAttribute.schema) + ", " + getClazName(action);
      }
    }
    return null;
  }

  private String handleSubResourceReturnType() {
    Attribute childAttribute = attribute.attributes().get(0);
    if (childAttribute.schema.getFormat() != null
        && childAttribute.schema.getFormat().equals(NUMBER_TYPE)
        && parentName != null
        && resourceList.stream().anyMatch(r -> r.name.equals(toClazName(parentName)))) {
      Optional<Resource> resList =
          resourceList.stream().filter(r -> r.name.equals(toClazName(parentName))).findFirst();
      if (resList.isPresent()) {
        Optional<Attribute> resourceLevelAttribute =
            resList.get().attributes().stream()
                .filter(a -> a.name.equals(attribute.name))
                .findFirst();
        if (resourceLevelAttribute.isPresent()) {
          if (resourceLevelAttribute.get().schema.getEnum() != null
              && !resourceLevelAttribute.get().schema.getEnum().isEmpty()) {
            return (activeResource.name)
                + "."
                + getName(attribute.name + ENUM_SUFFIX)
                + ", "
                + getClazName(action);
          }
          return dataTypeMethod.apply(resourceLevelAttribute.get().schema)
              + ", "
              + getClazName(action);
        }
      }
    }

    if (dataTypeMethod.apply(childAttribute.schema).equals(DATE_TIME)) {
      return getClazName(action);
    }
    return null;
  }
}
