package com.chargebee;

import com.chargebee.handlebar.Inflector;
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
}
