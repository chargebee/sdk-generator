package com.chargebee.sdk.java.javanext.util;

import com.google.common.base.CaseFormat;

/** Utility for safely converting strings to UpperCamel without assuming a single source format. */
public final class CaseFormatUtil {
  private CaseFormatUtil() {}

  public static String toUpperCamelSafe(String input) {
    if (input == null) {
      return null;
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
      CaseFormat src = Character.isUpperCase(s.charAt(0)) ? CaseFormat.UPPER_CAMEL : CaseFormat.LOWER_CAMEL;
      return src.to(CaseFormat.UPPER_CAMEL, s);
    }

    if (hasUpper) {
      return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, s);
    }

    return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, s);
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


