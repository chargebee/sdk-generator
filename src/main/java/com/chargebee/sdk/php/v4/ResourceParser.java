package com.chargebee.sdk.php.v4;

import static com.chargebee.GenUtil.toCamelCase;
import static com.chargebee.sdk.php.v4.Common.*;

import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.sdk.php.v4.models.Column;
import java.util.List;
import java.util.stream.Collectors;

public class ResourceParser {
  public static List<Column> getCols(Resource res) {
    return res.getSortedResourceAttributes().stream()
        .filter(
            attribute ->
                attribute.isNotHiddenAttribute()
                    && !attribute.isEnumAttribute()) // Exclude hidden & enum attributes
        .map(
            attribute ->
                attribute.isContentObjectAttribute()
                    ? generateContentColum(attribute)
                    : columnParser(attribute))
        .collect(Collectors.toList());
  }

  private static Column generateContentColum(Attribute attribute) {
    Column column = new Column();
    column.setIsOptional(true);
    column.setApiName(attribute.name);
    column.setName(attribute.name);
    column.setSubResources(true);
    column.setSubResourceName(toCamelCase(attribute.name));
    column.setFieldTypePHP(toCamelCase(attribute.name));
    column.setPhpDocField(toCamelCase(attribute.name));
    return column;
  }

  public static List<Column> generateGlobalEnumColumn(Resource res) {
    return res.getSortedResourceAttributes().stream()
        .filter(
            attribute ->
                attribute.isNotHiddenAttribute()
                    && attribute.isEnumAttribute()
                    && (attribute.isGlobalEnumAttribute() || attribute.isGenSeparate()))
        .map(Common::globalEnumParser)
        .collect(Collectors.toList());
  }

  public static List<Column> generateLocalEnumCloumn(Resource res) {
    return res.getSortedResourceAttributes().stream()
        .filter(
            attribute ->
                attribute.isNotHiddenAttribute()
                    && attribute.isEnumAttribute()
                    && !(attribute.isGlobalEnumAttribute() || attribute.isGenSeparate()))
        .map(attribute -> Common.localEnumParser(attribute, res))
        .collect(Collectors.toList());
  }
}
