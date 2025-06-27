package com.chargebee.sdk.common;

import static com.chargebee.openapi.Action.HIDDEN_FROM_SDK;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.List;

public class AttributeAssistant {
  private Resource resource;
  private Attribute attribute;

  public static boolean isHiddenFromSDK(Schema<?> schema, boolean forQa) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(HIDDEN_FROM_SDK) != null
        && (boolean) schema.getExtensions().get(HIDDEN_FROM_SDK)
        && !forQa;
  }

  public AttributeAssistant setResource(Resource resource) {
    this.resource = resource;
    return this;
  }

  public boolean hasAttributeByGivenName(String snakeCaseName) {
    List<Attribute> attributeList = new ArrayList<>();
    collectAttributes(this.resource, attributeList);
    return attributeList.stream().anyMatch(a -> a.name.equals(snakeCaseName));
  }

  private void collectAttributes(Resource resource, List<Attribute> attributeList) {
    attributeList.addAll(resource.attributes());
    for (Attribute subAttribute : resource.attributes()) {
      if (subAttribute.attributes() != null) {
        attributeList.addAll(subAttribute.attributes());
      }
    }
  }

  public List<Attribute> subResourceEnum() {
    return attribute.attributes().stream()
        .filter(Attribute::isNotHiddenAttribute)
        .filter(att -> att.isApi() && !att.isExternalEnum())
        .toList();
  }

  public List<Attribute> resourceEnum() {
    return resource.attributes().stream()
        .filter(Attribute::isNotHiddenAttribute)
        .filter(att -> att.isApi() && !att.isGenSeparate())
        .toList();
  }

  public AttributeAssistant setAttribute(Attribute attribute) {
    this.attribute = attribute;
    return this;
  }
}
