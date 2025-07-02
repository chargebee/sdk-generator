package com.chargebee.sdk.ts.typing.V3;

import static com.chargebee.GenUtil.singularize;
import static com.chargebee.GenUtil.toCamelCase;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.parameter.Parameter;
import com.chargebee.sdk.ts.typing.Constants;
import com.google.common.base.CaseFormat;

public class Utils {
  public static String getClazName(Action action, Resource res) {
    if (action.isListResourceAction()) {
      return toCamelCase(action.name) + Constants.INPUT_PARAM;
    }
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, toCamelCase(action.name))
        + Constants.INPUT_PARAM;
  }

  public static String getClazName(Action action, Resource res, boolean forFilterSubParams) {
    if (action.isListResourceAction() && forFilterSubParams) {
      return res.name + toCamelCase(action.name) + Constants.INPUT_PARAM;
    }
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, toCamelCase(action.name))
        + Constants.INPUT_PARAM;
  }

  public static String getClazName(Attribute attribute) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, singularize(attribute.name));
  }

  public static String getClazName(Action action) {
    return toCamelCase(action.name) + Constants.INPUT_PARAM;
  }

  public static boolean getHasSortParam(Action action) {
    for (Parameter queryParameter : action.queryParameters()) {
      Attribute attribute =
          new Attribute(queryParameter.getName(), queryParameter.schema, queryParameter.isRequired);
      if (attribute.getSchema() != null
          && attribute.schema.getProperties() != null
          && attribute.name.equals("sort_by")) return true;
    }
    for (Parameter requestBodyParameter : action.requestBodyParameters()) {
      Attribute attribute =
          new Attribute(
              requestBodyParameter.getName(),
              requestBodyParameter.schema,
              requestBodyParameter.isRequired);
      if (attribute.getSchema() != null
          && attribute.schema.getProperties() != null
          && attribute.name.equals("sort_by")) return true;
    }
    return false;
  }

  public static boolean getSubFilterParam(Attribute attribute) {
    return attribute.isFilterAttribute() && attribute.isSubResource();
  }

  public static String getTypescriptPutMethName(Attribute attribute) {
    if (attribute.isRequired) {
      return "";
    } else return "?";
  }
}
