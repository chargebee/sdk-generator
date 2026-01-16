package com.chargebee.sdk.java.v4.util;

import com.chargebee.openapi.Extension;
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
    Object customFieldsExt = schema.getExtensions().get(Extension.IS_CUSTOM_FIELDS_SUPPORTED);
    if (customFieldsExt instanceof Boolean) {
      return (Boolean) customFieldsExt;
    }
    return false;
  }

  /**
   * Checks if a schema supports consent fields by looking for the x-cb-is-consent-fields-supported
   * extension.
   *
   * @param schema the schema to check
   * @return true if the schema supports consent fields, false otherwise
   */
  public static boolean isConsentFieldsSupported(Schema<?> schema) {
    if (schema == null || schema.getExtensions() == null) {
      return false;
    }
    Object consentFieldsExt = schema.getExtensions().get(Extension.IS_CONSENT_FIELDS_SUPPORTED);
    if (consentFieldsExt instanceof Boolean) {
      return (Boolean) consentFieldsExt;
    }
    return false;
  }

  private SchemaUtil() {
    // Utility class, prevent instantiation
  }
}
