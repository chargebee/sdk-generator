package com.chargebee.sdk.ts.typing.V3;

import static com.chargebee.openapi.Resource.*;
import static com.chargebee.sdk.ts.typing.V3.Utils.getClazName;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import java.util.ArrayList;
import java.util.List;

public class AttributeParser {
  public static List<String> getAttributesInMultiLine(Resource res, Resource activeResource) {
    List<String> attributesInMultiLine = new ArrayList<>();
    for (Attribute attribute : res.getSortedResourceAttributes()) {
      attributesInMultiLine.add(
          attribute.name
              + (attribute.isRequired ? "" : "?")
              + ":"
              + dataTypeForMultiLineAttributes(attribute, activeResource)
              + ";");
    }
    return attributesInMultiLine;
  }

  public static String dataTypeForMultiLineAttributes(
      Attribute attribute, Resource activeResource) {
    if (attribute.schema instanceof ObjectSchema && isSubResourceSchema(attribute.schema)) {
      if (isGlobalResourceReference(attribute.schema)) {
        return String.format("%s", subResourceName(attribute.schema));
      }
      return String.format("%s.%s", activeResource.name, subResourceName(attribute.schema));
    }
    if (attribute.schema instanceof ArraySchema
        && attribute.schema.getItems() != null
        && attribute.schema.getItems().getType() != null) {
      if (attribute.schema.getItems() instanceof ObjectSchema
          && isSubResourceSchema(attribute.schema.getItems())) {
        if (isGlobalResourceReference(attribute.schema.getItems())) {
          return String.format("%s[]", subResourceName(attribute.schema.getItems()));
        }
        return String.format(
            "%s.%s[]",
            activeResource.name,
            attribute.schema instanceof ArraySchema
                ? getClazName(attribute)
                : attribute.subResourceName());
      }
      return String.format("%s[]", Common.primitiveDataType(attribute.schema.getItems()));
    }
    return Common.primitiveDataType(attribute.schema);
  }
}
