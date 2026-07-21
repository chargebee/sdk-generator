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

  /**
   * Java built-in / language type names that must not be used as generated (sub-)model class names,
   * because doing so shadows the real type (e.g. an inner class named {@code List} shadows
   * {@code java.util.List}). Add more names here manually as new collisions are discovered.
   */
  private static final java.util.Set<String> RESERVED_TYPE_NAMES = java.util.Set.of("List");

  /**
   * Returns true if the given class name collides with a Java built-in type and therefore cannot be
   * used as a generated class name.
   */
  public static boolean isReservedTypeName(String name) {
    return name != null && RESERVED_TYPE_NAMES.contains(name);
  }

  /**
   * Reads the {@code x-cb-sub-resource-name} extension from a schema.
   *
   * @param schema the schema to inspect
   * @return the sub-resource name (e.g. "AsyncResponse"), or null if absent
   */
  public static String subResourceName(Schema<?> schema) {
    if (schema == null || schema.getExtensions() == null) {
      return null;
    }
    Object subResourceName = schema.getExtensions().get(Extension.SUB_RESOURCE_NAME);
    if (subResourceName instanceof String && !((String) subResourceName).isEmpty()) {
      return (String) subResourceName;
    }
    return null;
  }

  /**
   * Resolves the class name for an inline object (used for list items / sub-models). Normally this
   * is the UpperCamel form of the field name, but when that collides with a Java built-in type
   * (e.g. "list" -&gt; "List"), the {@code x-cb-sub-resource-name} is used instead so the generated
   * class does not shadow the built-in type.
   *
   * @param fieldName the property name driving the class name
   * @param itemSchema the inline object schema (source of x-cb-sub-resource-name)
   * @return the class name to use for the generated inline class
   */
  public static String inlineItemClassName(String fieldName, Schema<?> itemSchema) {
    String candidate =
        com.google.common.base.CaseFormat.LOWER_UNDERSCORE.to(
            com.google.common.base.CaseFormat.UPPER_CAMEL, fieldName);
    if (isReservedTypeName(candidate)) {
      String subResourceName = subResourceName(itemSchema);
      if (subResourceName != null) {
        return subResourceName;
      }
    }
    return candidate;
  }

  private SchemaUtil() {
    // Utility class, prevent instantiation
  }
}
