package com.chargebee.sdk.go;

import static com.chargebee.GenUtil.singularize;

import com.chargebee.handlebar.Inflector;
import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Enum;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.parameter.Parameter;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SchemaLessEnumParser {
  public static List<Enum> getSchemalessEnum(Resource activeResource, List<Resource> resourceList) {
    List<Enum> enumList = new ArrayList<>();
    for (Resource res : resourceList) {
      for (Action action : res.getSortedAction()) {
        for (Parameter iParam : action.requestBodyParameters()) {
          if ((iParam.schema instanceof ObjectSchema || iParam.schema instanceof MapSchema)
              && iParam.schema.getProperties() != null
              && iParam.schema.getItems() == null
              && !iParam.isCompositeArrayBody()) {
            Attribute attribute = new Attribute(iParam.getName(), iParam.schema, iParam.isRequired);
            if (attribute.isFilterAttribute()) continue;
            attribute
                .attributes()
                .forEach(
                    value -> {
                      if (!(value.isEnumAttribute() || value.isGlobalEnumAttribute())) {
                        return;
                      }
                      if (!value.isNotHiddenAttribute()) return;
                      if (value.isGlobalEnumAttribute()
                          || (value.isDeprecated() && value.isEnumAttribute())) {
                        return;
                      }
                      boolean isResourceLevelEnum =
                          res.attributes().stream().anyMatch(a -> a.name.equals(iParam.getName()));
                      if (value.isEnumAttribute() && isResourceLevelEnum) return;
                      if (value.metaModelName() != null) {
                        String parentResourceName =
                            Inflector.capitalize(getName(singularize(value.metaModelName())));
                        List<Resource> filteredResources =
                            resourceList.stream()
                                .filter(
                                    resource -> Objects.equals(resource.name, parentResourceName))
                                .toList();
                        if (filteredResources.isEmpty()) {
                          return;
                        }
                        Resource parentResource = filteredResources.get(0);
                        boolean isSchemaLessEnum =
                            parentResource.attributes().stream()
                                .noneMatch(
                                    parentAttribute ->
                                        Objects.equals(parentAttribute.name, value.name));
                        if (isSchemaLessEnum
                            && Objects.equals(activeResource.name, parentResourceName)) {
                          Enum tempEnum = new Enum(value.name, value.schema);
                          if (enumList.stream()
                              .noneMatch(
                                  _enum ->
                                      _enum.name.equals(tempEnum.name)
                                          && _enum.validValues().equals(tempEnum.validValues()))) {
                            enumList.add(tempEnum);
                          }
                        }
                      }
                    });
          }
        }
      }
    }
    return enumList;
  }

  public static String getName(String name) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
  }
}
