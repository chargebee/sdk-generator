package com.chargebee.sdk.go;

import static com.chargebee.GenUtil.singularize;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Enum;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.parameter.Parameter;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.ObjectSchema;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SchemaLessEnumParser {
  public static List<Enum> getSchemalessEnum(Resource activeResource, List<Resource> resourceList) {
    List<Enum> enumList = new ArrayList<>();
    Set<String> seenEnumNames = new HashSet<>();
    
    for (Action action : activeResource.getSortedAction()) {
      for (Parameter iParam : action.requestBodyParameters()) {
        Attribute attribute = new Attribute(iParam.getName(), iParam.schema, iParam.isRequired);
        String parentParamName = iParam.getName();
        
        // Check if this is a sub-resource parameter (has nested attributes)
        if ((iParam.schema instanceof ObjectSchema)
            && iParam.schema.getProperties() != null
            && iParam.schema.getItems() == null
            && !iParam.isCompositeArrayBody()) {
          if (attribute.isFilterAttribute()) continue;
          
          // Process nested enum attributes in sub-resources
          processSubResourceEnums(attribute, parentParamName, seenEnumNames, enumList, resourceList);
        }
        
        // Handle composite array bodies (arrays of objects like discounts[], unbilled_charges[], etc.)
        if (iParam.isCompositeArrayBody() && attribute.attributes() != null) {
          processSubResourceEnums(attribute, parentParamName, seenEnumNames, enumList, resourceList);
        }
        
        // Also process top-level enum parameters
        if (attribute.isEnumAttribute() 
            && attribute.isNotHiddenAttribute() 
            && !attribute.isDeprecated()
            && !attribute.isGlobalEnumAttribute()) {
          // Check if this is already a resource-level enum
          boolean isResourceLevelEnum = activeResource.attributes().stream()
              .anyMatch(a -> a.name.equals(parentParamName));
          if (!isResourceLevelEnum) {
            String enumName = attribute.name;
            if (!seenEnumNames.contains(enumName)) {
              seenEnumNames.add(enumName);
              enumList.add(new Enum(enumName, attribute.schema));
            }
          }
        }
      }
    }
    return enumList;
  }
  
  /**
   * Process enum attributes in a sub-resource, checking if there's a matching resource
   * that has this enum defined. If the enum is from another resource (like UnbilledCharge.entity_type),
   * we skip it because the enum is already defined there.
   */
  private static void processSubResourceEnums(Attribute subResource, String parentParamName, 
      Set<String> seenEnumNames, List<Enum> enumList, List<Resource> resourceList) {
    // Get the singularized name to check if it matches a resource
    String singularName = singularize(parentParamName);
    
    for (Attribute subAttr : subResource.attributes()) {
      if (!subAttr.isEnumAttribute()) continue;
      if (!subAttr.isNotHiddenAttribute()) continue;
      if (subAttr.isDeprecated()) continue;
      if (subAttr.isGlobalEnumAttribute()) continue;
      if (subAttr.isExternalEnum()) continue; // External enums are defined elsewhere
      
      // Check if a matching resource has this enum defined
      boolean resourceHasEnum = resourceList.stream()
          .filter(r -> r.name.equalsIgnoreCase(singularName))
          .flatMap(r -> r.attributes().stream())
          .anyMatch(a -> a.name.equalsIgnoreCase(subAttr.name) && a.isEnumAttribute());
      
      if (resourceHasEnum) {
        // The enum is defined in the matching resource, skip generating it here
        continue;
      }
      
      // For sub-resource enums, name is: singularized(parentParamName) + "_" + attributeName
      // This matches the reference pattern in subParams(): 
      // activeResource.name + toCamelCase(singularize(subParam.name)) + toCamelCase(attribute.name)
      String enumName = singularName + "_" + subAttr.name;
      if (!seenEnumNames.contains(enumName)) {
        seenEnumNames.add(enumName);
        enumList.add(new Enum(enumName, subAttr.schema));
      }
    }
  }

  public static String getName(String name) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
  }
}
