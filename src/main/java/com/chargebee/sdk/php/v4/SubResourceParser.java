package com.chargebee.sdk.php.v4;

import static com.chargebee.GenUtil.*;

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
            .map(SubResourceParser::createSubResource)
            .collect(Collectors.toList());
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
