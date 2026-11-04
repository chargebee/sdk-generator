package com.chargebee.sdk.php.v4;

import static com.chargebee.GenUtil.*;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.sdk.common.ResourceAssist;
import com.chargebee.sdk.php.v4.models.Column;
import io.swagger.v3.oas.models.media.ArraySchema;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SubResourceParser {
  public static List<com.chargebee.sdk.php.v4.models.Resource> listSubResources(
      Resource activeResource) {
    List<com.chargebee.sdk.php.v4.models.Resource> subResources = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Attribute attribute : new ResourceAssist().setResource(activeResource).subResource()) {
      addLocalSubResource(attribute, subResources, seen);
    }
    return subResources;
  }

  private static void addLocalSubResource(
      Attribute attribute,
      List<com.chargebee.sdk.php.v4.models.Resource> subResources,
      Set<String> seen) {
    if (isValidSubResource(attribute)) {
      String clazName = determineClassName(attribute);
      if (clazName != null && seen.add(clazName)) {
        subResources.add(createSubResource(attribute));
      }
      for (Attribute child : attribute.attributes()) {
        addLocalSubResource(child, subResources, seen);
      }
    }
  }

  public static List<Column> getSubResourceCols(Attribute subResourceAttribute) {
    return subResourceAttribute.attributes().stream()
        .filter(Attribute::isNotHiddenAttribute)
        .map(Common::columnParser)
        .collect(Collectors.toList());
  }

  private static boolean isValidSubResource(Attribute attribute) {
    return attribute.isSubResource()
        && !attribute.isDependentAttribute()
        && !Resource.isGlobalResourceReference(attribute.schema);
  }

  private static com.chargebee.sdk.php.v4.models.Resource createSubResource(Attribute attribute) {
    com.chargebee.sdk.php.v4.models.Resource subResource =
        new com.chargebee.sdk.php.v4.models.Resource();
    subResource.setClazName(determineClassName(attribute));
    subResource.setCols(getSubResourceCols(attribute));
    return subResource;
  }

  private static String determineClassName(Attribute attribute) {
    return attribute.schema instanceof ArraySchema
        ? singularize(toClazName(attribute.name))
        : attribute.subResourceName();
  }
}
