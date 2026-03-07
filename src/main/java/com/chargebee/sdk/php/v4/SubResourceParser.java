package com.chargebee.sdk.php.v4;

import static com.chargebee.GenUtil.*;
import static com.chargebee.sdk.php.v4.Common.localClassBasedEnumParser;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.sdk.common.ResourceAssist;
import com.chargebee.sdk.php.v4.models.Column;
import io.swagger.v3.oas.models.media.ArraySchema;
import java.util.List;
import java.util.stream.Collectors;

public class SubResourceParser {
  public static List<com.chargebee.sdk.php.v4.models.Resource> listSubResources(
      Resource activeResource) {
    return new ResourceAssist()
        .setResource(activeResource).subResource().stream()
            .filter(SubResourceParser::isValidSubResource)
            .map(attribute -> createSubResource(attribute, activeResource))
            .collect(Collectors.toList());
  }

  public static List<Column> getSubResourceCols(Attribute subResourceAttribute) {
    return subResourceAttribute.attributes().stream()
        .filter(attribute -> attribute.isNotHiddenAttribute() && !attribute.isEnumAttribute())
        .map(Common::columnParser)
        .collect(Collectors.toList());
  }

  private static boolean isValidSubResource(Attribute attribute) {
    return attribute.isSubResource()
        && !attribute.isDependentAttribute()
        && !Resource.isGlobalResourceReference(attribute.schema);
  }

  private static com.chargebee.sdk.php.v4.models.Resource createSubResource(
      Attribute attribute, Resource activeResource) {
    com.chargebee.sdk.php.v4.models.Resource subResource =
        new com.chargebee.sdk.php.v4.models.Resource();
    subResource.setClazName(determineClassName(attribute));
    subResource.setCols(getSubResourceCols(attribute));
    subResource.setGlobalEnumCols(generateGlobalClassBasedEnumColumn(attribute));
    subResource.setLocalEnumCols(generateLocalClassBasedEnumColumn(attribute, activeResource));
    return subResource;
  }

  private static String determineClassName(Attribute attribute) {
    return attribute.schema instanceof ArraySchema
        ? singularize(toClazName(attribute.name))
        : attribute.subResourceName();
  }

  public static List<Column> generateGlobalClassBasedEnumColumn(Attribute subResourceAttribute) {
    return subResourceAttribute.attributes().stream()
        .filter(
            attribute ->
                attribute.isNotHiddenAttribute()
                    && attribute.isEnumAttribute()
                    && (attribute.isGlobalEnumAttribute() || attribute.isGenSeparate()))
        .map(Common::globalClassBasedEnumParser)
        .collect(Collectors.toList());
  }

  public static List<Column> generateLocalClassBasedEnumColumn(
      Attribute subResourceAttribute, Resource res) {
    return subResourceAttribute.attributes().stream()
        .filter(
            attribute ->
                attribute.isNotHiddenAttribute()
                    && attribute.isEnumAttribute()
                    && !(attribute.isGlobalEnumAttribute() || attribute.isGenSeparate()))
        .map(attribute -> localClassBasedEnumParser(attribute, res, subResourceAttribute))
        .collect(Collectors.toList());
  }
}
