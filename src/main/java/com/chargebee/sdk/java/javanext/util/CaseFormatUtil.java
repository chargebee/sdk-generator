package com.chargebee.sdk.java.javanext.util;

import com.google.common.base.CaseFormat;

/**
 * Utility for safely converting strings between cases without assuming a single source format.
 * All methods return an empty string for null or blank inputs.
 */
public final class CaseFormatUtil {
  private CaseFormatUtil() {}

  public static String toUpperCamelSafe(String input) {
    if (input == null) {
      return "";
    }
    String s = input.trim();
    if (s.isEmpty()) {
      return s;
    }

    boolean hasUnderscore = s.indexOf('_') >= 0;
    boolean hasLower = containsLowerCase(s);
    boolean hasUpper = containsUpperCase(s);

    if (hasUnderscore) {
      if (hasUpper && !hasLower) {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, s);
      }
      String normalized = s.replaceAll("^_+|_+$", "");
      normalized = normalized.replaceAll("__+", "_");
      normalized = normalized.toLowerCase(java.util.Locale.ROOT);
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, normalized);
    }

    if (hasLower && hasUpper) {
      CaseFormat src =
          Character.isUpperCase(s.charAt(0)) ? CaseFormat.UPPER_CAMEL : CaseFormat.LOWER_CAMEL;
      return src.to(CaseFormat.UPPER_CAMEL, s);
    }

    if (hasUpper) {
      return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, s);
    }

    return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, s);
  }

  /**
   * Convert any of: camelCase, PascalCase, snake_case, UPPER_SNAKE, kebab-case, dotted.case,
   * spaced words, or mixed forms into lower_snake_case safely.
   */
  public static String toSnakeCaseSafe(String input) {
    if (input == null) return "";
    String trimmed = input.trim();
    if (trimmed.isEmpty()) return "";

    String normalized =
        trimmed.replace('-', '_').replace(' ', '_').replace('.', '_').replace('/', '_');

    StringBuilder result = new StringBuilder();
    for (int i = 0; i < normalized.length(); i++) {
      char ch = normalized.charAt(i);

      if (ch == '_') {
        if (result.length() > 0 && result.charAt(result.length() - 1) != '_') {
          result.append('_');
        }
        continue;
      }

      char last = result.length() > 0 ? result.charAt(result.length() - 1) : '\0';

      if (Character.isUpperCase(ch)) {
        if (result.length() > 0
            && last != '_'
            && (Character.isLowerCase(last) || Character.isDigit(last))) {
          result.append('_');
        }
        result.append(Character.toLowerCase(ch));
      } else if (Character.isDigit(ch)) {
        if (result.length() > 0 && last != '_' && Character.isLetter(last)) {
          result.append('_');
        }
        result.append(ch);
      } else {
        if (result.length() > 0 && last != '_' && Character.isDigit(last)) {
          result.append('_');
        }
        result.append(ch);
      }
    }

    String snake = result.toString().replaceAll("__+", "_");
    snake = snake.replaceAll("^_+|_+$", "");
    return snake;
  }

  /** Convert any supported format into lowerCamelCase safely. */
  public static String toLowerCamelSafe(String input) {
    if (input == null) return "";
    String upperCamel = toUpperCamelSafe(input);
    if (upperCamel.isEmpty()) return upperCamel;
    return Character.toLowerCase(upperCamel.charAt(0)) + upperCamel.substring(1);
  }

  public static String toUpperUnderscoreSafe(String input) {
    if (input == null) return "";
    String snake = toSnakeCaseSafe(input);
    return snake.toUpperCase(java.util.Locale.ROOT);
  }

  private static boolean containsUpperCase(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (Character.isUpperCase(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsLowerCase(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (Character.isLowerCase(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }
}
