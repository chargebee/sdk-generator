package com.chargebee.sdk.validator.emitter.joi;

import com.google.common.base.CaseFormat;

/**
 * Naming conventions for generated Joi validation artefacts.
 */
public class JoiNamingStrategy {

  private JoiNamingStrategy() {}

  /** e.g. create → create.validation.js */
  public static String fileName(String actionName) {
    return toSnake(actionName) + ".validation.js";
  }

  /** e.g. resource=Customer, action=create → createCustomerBodySchema */
  public static String bodySchemaName(String actionName, String resourceName) {
    return toCamel(actionName) + toPascal(resourceName) + "BodySchema";
  }

  /**
   * e.g. resource=Customer, action=create, subObject=ShippingAddress
   *   → createCustomerShippingAddressSchema
   */
  public static String nestedSchemaName(String actionName, String resourceName, String subObject) {
    return toCamel(actionName) + toPascal(resourceName) + toPascal(subObject) + "Schema";
  }

  /** e.g. postalAddress → postalAddressBlockSchema */
  public static String sharedSchemaName(String refName) {
    return toCamel(refName) + "BlockSchema";
  }

  /** Subdirectory name for a resource: e.g. Customer → customer */
  public static String resourceDir(String resourceName) {
    return toSnake(resourceName);
  }

  // ---- helpers ----

  private static String toCamel(String name) {
    if (name == null || name.isBlank()) return "";
    if (name.contains("_")) {
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name);
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  private static String toPascal(String name) {
    if (name == null || name.isBlank()) return "";
    if (name.contains("_")) {
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
    }
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  private static String toSnake(String name) {
    if (name == null || name.isBlank()) return "";
    // Already snake_case
    if (name.contains("_")) return name.toLowerCase();
    // camelCase → snake_case
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
  }
}
