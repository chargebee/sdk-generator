package com.chargebee;

import com.chargebee.handlebar.Inflector;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.ArrayUtils;

public class GenUtil {

  private GenUtil() {}

  public static String singularize(String word) {
    return Inflector.singularize(word);
  }

  public static String pluralize(String word) {
    return Inflector.pluralize(word);
  }

  public static String firstCharUpper(String word) {
    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
  }

  public static String firstCharLower(String word) {
    return Character.toLowerCase(word.charAt(0)) + word.substring(1);
  }

  /**
   * To generate java variable style name
   */
  public static String getVarName(String... parts) {
    return firstCharLower(toCamelCase(parts));
  }

  /**
   * To generate java class style name
   */
  public static String toClazName(String... parts) {
    return firstCharUpper(toCamelCase(parts));
  }

  /**
   * @param parts If part has '_', it will be split further
   * @return
   */
  public static String toCamelCase(String... parts) {
    return toCamelCaseWithFiller(null, parts);
  }

  public static String toCamelCaseWithFiller(String filler, String... parts) {
    String[] all = new String[0];
    for (String part : parts) {
      if (part.isEmpty()) {
        continue;
      }
      String[] subParts = part.split("_");
      all = ArrayUtils.addAll(all, subParts);
    }
    boolean first = true;
    StringBuilder buff = new StringBuilder();
    for (String str : all) {
      if (filler != null && !first) {
        buff.append(filler);
      }
      first = false;
      buff.append(Character.toUpperCase(str.charAt(0)));
      buff.append(str.substring(1));
    }
    return buff.toString();
  }

  public static String toUnderScores(String camelCaseName) {
    StringBuilder buf = new StringBuilder(camelCaseName.length() + 5);
    buf.append(Character.toLowerCase(camelCaseName.charAt(0)));
    for (int i = 1; i < camelCaseName.length(); i++) {
      char c = camelCaseName.charAt(i);
      if (Character.isUpperCase(c)) {
        buf.append('_');
        c = Character.toLowerCase(c);
      }
      buf.append(c);
    }
    return buf.toString();
  }

  /**
   * Normalizes a string to proper lowerCamelCase by removing underscores
   * and capitalizing the character after each underscore.
   * This handles hybrid formats like "payment_vouchersForCustomer".
   */
  public static String normalizeToLowerCamelCase(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }

    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;

    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }

    // Ensure first character is lowercase
    if (result.length() > 0 && Character.isUpperCase(result.charAt(0))) {
      result.setCharAt(0, Character.toLowerCase(result.charAt(0)));
    }

    return result.toString();
  }

  public static boolean hasAdditionalProperties(Schema schema) {
    var additionalProperties = schema.getAdditionalProperties();
    if (additionalProperties == null) {
      return false;
    }
    if (additionalProperties instanceof Boolean) {
      return (Boolean) additionalProperties;
    }
    if (additionalProperties instanceof ObjectSchema) {
      return ((ObjectSchema) additionalProperties).getBooleanSchemaValue();
    }
    if (additionalProperties instanceof Schema<?>) {
      return ((Schema<?>) additionalProperties).getBooleanSchemaValue();
    }
    return false;
  }
}
