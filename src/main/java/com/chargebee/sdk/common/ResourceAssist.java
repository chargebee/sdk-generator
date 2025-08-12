package com.chargebee.sdk.common;

import static com.chargebee.GenUtil.toCamelCase;
import static com.chargebee.handlebar.Inflector.singularize;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Enum;
import com.chargebee.openapi.Resource;
import com.google.common.base.CaseFormat;
import java.util.List;

public class ResourceAssist {
  private Resource resource;

  public ResourceAssist setResource(Resource resource) {
    this.resource = resource;
    return this;
  }

  public List<Attribute> subResource() {
    return resource.getSortedResourceAttributes().stream()
        .filter(
            attribute ->
                (attribute.isSubResource()
                    && !attribute.isDependentAttribute()
                    && !Resource.isGlobalResourceReference(attribute.schema)))
        .toList();
  }

  public List<Enum> enumsHelper() {
    List<Enum> enums =
        new java.util.ArrayList<>(
            new java.util.ArrayList<>(
                this.resource.attributes().stream()
                    .filter(Attribute::isNotHiddenAttribute)
                    .filter(attr -> !attr.isGlobalEnumAttribute() && attr.isEnumAttribute())
                    .map(attr -> new Enum(attr.name, attr.getSchema()))
                    .toList()));

    return enums;
  }

  public List<Enum> enums() {
    List<Enum> enums = enumsHelper();

    for (Attribute attribute : this.resource.attributes()) {
      for (Attribute subAttribute :
          attribute.attributes().stream()
              .filter(Attribute::isNotHiddenAttribute)
              .filter(attr -> !attr.isGlobalEnumAttribute() && attr.isEnumAttribute())
              .toList()) {
        if (subAttribute.isExternalEnum()) continue;
        String enumName = singularize(attribute.name) + "_" + subAttribute.name;
        String attributeName = attribute.name;
        String attributeType = attribute.subResourceName();

        if (!attributeType.equals(toCamelCase(singularize(attributeName)))) {
          enumName =
              CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, (attributeType))
                  + "_"
                  + subAttribute.name;
        }
        enums.add(new Enum(enumName, subAttribute.getSchema()));
      }
    }
    return enums;
  }

  public List<Enum> pyEnums() {
    List<Enum> enums = enumsHelper();

    for (Attribute attribute : this.resource.attributes()) {
      if (!attribute.isNotHiddenAttribute()) continue;
      for (Attribute subAttribute :
          attribute.attributes().stream()
              .filter(Attribute::isNotHiddenAttribute)
              .filter(attr -> !attr.isGlobalEnumAttribute() && attr.isEnumAttribute())
              .toList()) {
        if (subAttribute.isExternalEnum()) continue;
        String subResSnakeCaseName =
            CaseFormat.UPPER_CAMEL.to(
                CaseFormat.LOWER_UNDERSCORE, attribute.subResourceName().toString());
        enums.add(
            new Enum(
                singularize(subResSnakeCaseName) + "_" + subAttribute.name,
                subAttribute.getSchema()));
      }
    }
    return enums;
  }
}
