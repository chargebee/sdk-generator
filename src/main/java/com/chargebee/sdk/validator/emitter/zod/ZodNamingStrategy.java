package com.chargebee.sdk.validator.emitter.zod;

import com.google.common.base.CaseFormat;

/** Naming conventions for generated Zod validation artefacts. */
public class ZodNamingStrategy {

  private ZodNamingStrategy() {}

  /** e.g. Customer → customer.schema.ts */
  public static String resourceSchemaFileName(String resourceName) {
    return toSnake(resourceName) + ".schema.ts";
  }

  public static final String SHARED_SCHEMA_FILE = "shared.schema.ts";

  /** e.g. resource=Customer, action=create → CreateCustomerBodySchema */
  public static String bodySchemaName(String actionName, String resourceName) {
    return toPascal(actionName) + toPascal(resourceName) + "BodySchema";
  }

  /**
   * TypeScript type for the request body / query schema: same name as the Zod const without the
   * trailing {@code Schema} (e.g. CreateCustomerBodySchema → CreateCustomerBody).
   */
  public static String bodyInferredTypeName(String actionName, String resourceName) {
    String schemaConst = bodySchemaName(actionName, resourceName);
    if (schemaConst.endsWith("Schema")) {
      return schemaConst.substring(0, schemaConst.length() - "Schema".length());
    }
    return schemaConst;
  }

  /** e.g. action=create, resource=Customer, subObject=card → CreateCustomerCardSchema */
  public static String nestedSchemaName(String actionName, String resourceName, String subObject) {
    return toPascal(actionName) + toPascal(resourceName) + toPascal(subObject) + "Schema";
  }

  /** e.g. postalAddress → postalAddressBlockSchema */
  public static String sharedSchemaName(String refName) {
    return toCamel(refName) + "BlockSchema";
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
