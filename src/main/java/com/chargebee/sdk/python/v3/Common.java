package com.chargebee.sdk.python.v3;

import static com.chargebee.openapi.Extension.IS_MONEY_COLUMN;
import static com.chargebee.openapi.Extension.SUB_RESOURCE_NAME;
import static com.chargebee.sdk.python.v3.Constants.GLOBAL_ENUM_PATTERN;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Enum;
import com.chargebee.openapi.Resource;
import com.chargebee.sdk.common.GlobalEnum;
import com.chargebee.sdk.common.ResourceAssist;
import com.chargebee.sdk.go.SchemaLessEnumParser;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Common {

  protected static List<Map<String, Object>> globalEnumTemplate(List<Enum> enums) {
    return enums.stream().map(e -> new GlobalEnum(e).template()).toList();
  }

  protected static Map<String, Object> globalEnumTemplate(Enum e) {
    return new GlobalEnum(e).template();
  }

  protected static boolean isMoneyColumn(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_MONEY_COLUMN) != null
        && (boolean) schema.getExtensions().get(IS_MONEY_COLUMN);
  }

  protected static boolean hasGlobalEnumImports(List<String> imports) {
    return imports.stream().anyMatch(imp -> GLOBAL_ENUM_PATTERN.matcher(imp).matches());
  }

  protected static boolean hasFilterImports(List<String> imports) {
    return imports.stream().anyMatch(imp -> imp.equals(Constants.FILTER));
  }

  protected static String getSnakeClazName(String name) {
    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
  }

  protected static List<Attribute> getDependentResource(Resource resource) {
    return resource.attributes().stream()
        .filter(com.chargebee.openapi.Attribute::isDependentAttribute)
        .toList();
  }

  protected static String subResourceName(Schema schema) {
    if (schema.getExtensions() != null && schema.getExtensions().get(SUB_RESOURCE_NAME) != null) {
      return (String) schema.getExtensions().get(SUB_RESOURCE_NAME);
    }
    return null;
  }

  protected static List<Map<String, Object>> genModelEnums(
      Resource resource, List<Resource> resourceList) {
    ResourceAssist resourceAssist = new ResourceAssist().setResource(resource);
    List<Enum> schemaLessEnums = SchemaLessEnumParser.getSchemalessEnum(resource, resourceList);
    List<Enum> resourceEnums =
        Stream.concat(schemaLessEnums.stream(), resourceAssist.pyEnums().stream())
            .collect(Collectors.toList());
    var enums =
        resourceEnums.stream().map(Common::globalEnumTemplate).filter(m -> !m.isEmpty()).toList();
    return enums;
  }

  protected static boolean hasInputParams(Action a) {
    return !(a.requestBodyParameters().isEmpty() && a.queryParameters().isEmpty());
  }
}
