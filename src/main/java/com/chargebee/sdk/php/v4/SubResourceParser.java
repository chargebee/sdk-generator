package com.chargebee.sdk.php.v4;

import static com.chargebee.GenUtil.*;
import static com.chargebee.sdk.php.v4.Common.localEnumsForSubResources;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.sdk.common.ResourceAssist;
import com.chargebee.sdk.php.v4.models.Column;
import io.swagger.v3.oas.models.media.ArraySchema;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    subResource.setGlobalEnumCols(generateGlobalEnumsForSubResources(attribute));
    subResource.setLocalEnumCols(generateLocalEnumsForSubResources(attribute, activeResource));
    List<String> knownFields =
        Stream.of(
                subResource.getCols(),
                subResource.getGlobalEnumCols(),
                subResource.getLocalEnumCols(),
                subResource.getListOfEnumCols())
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .map(Column::getApiName)
            .collect(Collectors.toList());
    subResource.setKnownFields(knownFields);
    return subResource;
  }

  private static String determineClassName(Attribute attribute) {
    return attribute.schema instanceof ArraySchema
        ? singularize(toClazName(attribute.name))
        : attribute.subResourceName();
  }

  public static List<Column> generateGlobalEnumsForSubResources(Attribute subResourceAttribute) {
    return subResourceAttribute.attributes().stream()
        .filter(
            attribute ->
                attribute.isNotHiddenAttribute()
                    && attribute.isEnumAttribute()
                    && (attribute.isGlobalEnumAttribute() || attribute.isGenSeparate()))
        .map(Common::globalEnumsForSubResources)
        .collect(Collectors.toList());
  }

  public static List<Column> generateLocalEnumsForSubResources(
      Attribute subResourceAttribute, Resource res) {
    return subResourceAttribute.attributes().stream()
        .filter(
            attribute ->
                attribute.isNotHiddenAttribute()
                    && attribute.isEnumAttribute()
                    && !(attribute.isGlobalEnumAttribute() || attribute.isGenSeparate()))
        .map(attribute -> localEnumsForSubResources(attribute, res, subResourceAttribute))
        .collect(Collectors.toList());
  }
}
