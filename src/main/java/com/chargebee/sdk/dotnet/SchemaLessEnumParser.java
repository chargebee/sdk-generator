package com.chargebee.sdk.dotnet;

import static com.chargebee.GenUtil.singularize;
import static com.chargebee.GenUtil.toClazName;

import com.chargebee.handlebar.Inflector;
import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Enum;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.parameter.Parameter;
import com.chargebee.sdk.dotnet.models.EnumColumn;
import com.chargebee.sdk.dotnet.models.VisibleEnumEntries;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import java.util.*;

public class SchemaLessEnumParser {

  public static List<EnumColumn> getSchemalessEnumForResource(
      List<Resource> resourceList, Resource activeResource) {
    Map<String, List<EnumColumn>> enumColumns = new HashMap<>();
    for (Resource res : resourceList) {
      for (Action action : res.getSortedAction()) {
        for (Parameter iParam : action.requestBodyParameters()) {
          List<Attribute> atter = new ArrayList<>();
          Attribute attribute = new Attribute(iParam.getName(), iParam.schema, iParam.isRequired);
          if ((iParam.schema instanceof ObjectSchema || iParam.schema instanceof MapSchema)
              && iParam.schema.getProperties() != null
              && iParam.schema.getItems() == null
              && !iParam.isCompositeArrayBody()) {
            if (attribute.isFilterAttribute()) continue;
            atter.addAll(attribute.attributes());
          }
          atter.add(attribute);
          atter.forEach(
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
                          .filter(resource -> Objects.equals(resource.name, parentResourceName))
                          .toList();
                  if (filteredResources.isEmpty()) {
                    return;
                  }
                  Resource parentResource = filteredResources.get(0);
                  boolean isSchemaLessEnum =
                      parentResource.attributes().stream()
                          .noneMatch(
                              parentAttribute -> Objects.equals(parentAttribute.name, value.name));
                  if (isSchemaLessEnum && Objects.equals(activeResource.name, parentResourceName)) {
                    EnumColumn enumColumn = new EnumColumn();
                    enumColumn.setDeprecated(value.isDeprecated());
                    enumColumn.setVisibleEntries(getEnumEntries(value));
                    enumColumn.setApiClassName(toClazName(value.name));
                    String apiClassName = enumColumn.getApiClassName();
                    enumColumns
                        .computeIfAbsent(apiClassName, k -> new ArrayList<>())
                        .add(enumColumn);
                  }
                } else if (value.metaModelName() == null
                    && Objects.equals(res.name, activeResource.name)) {
                  EnumColumn enumColumn = new EnumColumn();
                  enumColumn.setDeprecated(value.isDeprecated());
                  enumColumn.setVisibleEntries(getEnumEntries(value));
                  enumColumn.setApiClassName(toClazName(value.name));
                  String apiClassName = enumColumn.getApiClassName();
                  enumColumns.computeIfAbsent(apiClassName, k -> new ArrayList<>()).add(enumColumn);
                }
              });
        }
      }
    }
    return enumColumns.values().stream().flatMap(List::stream).distinct().toList();
  }

  public static List<EnumColumn> getSchemaLessEnumForSubResource(
      String activeSubResourceName, Resource activeResource) {
    Map<String, List<EnumColumn>> enumColumns = new HashMap<>();
    for (Action action : activeResource.getSortedAction()) {
      if (action.id.contains(Constants.BATCH)) continue;
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
                        activeResource.attributes().stream()
                            .anyMatch(a -> a.name.equals(iParam.getName()));
                    if (value.isEnumAttribute() && isResourceLevelEnum) {
                      Attribute parentSubResource =
                          activeResource.attributes().stream()
                              .filter(a -> a.name.equals(iParam.getName()))
                              .findFirst()
                              .orElse(null);
                      if (parentSubResource == null) {
                        return;
                      }
                      boolean isSchemaLessEnum =
                          parentSubResource.attributes().stream()
                              .noneMatch(
                                  parentAttribute ->
                                      Objects.equals(parentAttribute.name, value.name));
                      String parentSubResourceName =
                          singularize(toClazName(parentSubResource.name));
                      if (isSchemaLessEnum
                          && Objects.equals(parentSubResourceName, activeSubResourceName)) {
                        EnumColumn enumColumn = new EnumColumn();
                        enumColumn.setDeprecated(value.isDeprecated());
                        enumColumn.setVisibleEntries(getEnumEntries(value));
                        enumColumn.setApiClassName(toClazName(value.name));
                        String apiClassName = enumColumn.getApiClassName();
                        enumColumns
                            .computeIfAbsent(apiClassName, k -> new ArrayList<>())
                            .add(enumColumn);
                      }
                    }
                  });
        }
      }
    }
    return enumColumns.values().stream().flatMap(List::stream).distinct().toList();
  }

  public static String getName(String name) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
  }

  public static List<VisibleEnumEntries> getEnumEntries(Attribute attribute) {
    List<VisibleEnumEntries> visibleEnumEntries = new ArrayList<>();
    Enum anEnum = new Enum(attribute.schema);
    List<String> deprecatedValues = anEnum.deprecatedValues();
    for (String validValue : anEnum.values()) {
      VisibleEnumEntries visibleEnumEntry = new VisibleEnumEntries();
      visibleEnumEntry.setDeprecated(deprecatedValues.contains(validValue));
      visibleEnumEntry.setDotNetName(getName(validValue));
      visibleEnumEntry.setApiName(validValue);
      visibleEnumEntries.add(visibleEnumEntry);
    }
    return visibleEnumEntries;
  }
}
