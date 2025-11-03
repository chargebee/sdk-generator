package com.chargebee.sdk.java.javanext.util;

import io.swagger.v3.oas.models.media.Schema;

/**
 * Utility class for common schema operations.
 */
public class SchemaUtil {

  /**
   * Checks if a schema supports custom fields by looking for the x-cb-is-custom-fields-supported
   * extension.
   *
   * @param schema the schema to check
   * @return true if the schema supports custom fields, false otherwise
   */
  public static boolean isCustomFieldsSupported(Schema<?> schema) {
    if (schema == null || schema.getExtensions() == null) {
      return false;
    }
    Object customFieldsExt = schema.getExtensions().get("x-cb-is-custom-fields-supported");
    if (customFieldsExt instanceof Boolean) {
      return (Boolean) customFieldsExt;
    }
    return false;
  }

  private SchemaUtil() {
    // Utility class, prevent instantiation
  }
}
