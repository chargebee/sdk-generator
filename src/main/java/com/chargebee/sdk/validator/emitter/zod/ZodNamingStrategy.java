package com.chargebee.sdk.validator.emitter.zod;

import com.google.common.base.CaseFormat;

/** Naming conventions for generated Zod validation artefacts. */
public class ZodNamingStrategy {

  private ZodNamingStrategy() {}

  /** e.g. create → create.validation.ts */
  public static String fileName(String actionName) {
    return toSnake(actionName) + ".validation.ts";
  }

  /** e.g. resource=Customer, action=create → createCustomerBodySchema */
  public static String bodySchemaName(String actionName, String resourceName) {
    return toCamel(actionName) + toPascal(resourceName) + "BodySchema";
  }

  /** e.g. action=create, resource=Customer, subObject=card → createCustomerCardSchema */
  public static String nestedSchemaName(String actionName, String resourceName, String subObject) {
    return toCamel(actionName) + toPascal(resourceName) + toPascal(subObject) + "Schema";
  }

  /** e.g. postalAddress → postalAddressBlockSchema */
  public static String sharedSchemaName(String refName) {
    return toCamel(refName) + "BlockSchema";
  }

  /** Resource subdirectory: Customer → customer */
  public static String resourceDir(String resourceName) {
    return toSnake(resourceName);
  }

  private static String toCamel(String name) {
    if (name == null || name.isBlank()) return "";
    if (name.contains("_")) return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name);
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  private static String toPascal(String name) {
    if (name == null || name.isBlank()) return "";
    if (name.contains("_")) return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  private static String toSnake(String name) {
    if (name == null || name.isBlank()) return "";
    if (name.contains("_")) return name.toLowerCase();
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
  }
}
