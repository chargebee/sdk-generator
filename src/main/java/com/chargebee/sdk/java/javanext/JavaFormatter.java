package com.chargebee.sdk.java.javanext;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.google.googlejavaformat.java.RemoveUnusedImports;

/**
 * Utility class for formatting Java code using Google Java Format.
 */
public class JavaFormatter {

  private static final Formatter formatter = new Formatter();

  /**
   * Formats the given Java source code, returning the original code if formatting fails.
   *
   * @param sourceCode the Java source code to format
   * @return the formatted Java source code, or original code if formatting fails
   */
  public static String formatSafely(String sourceCode) {
    try {
      // First remove unused imports, then format
      String withoutUnusedImports = RemoveUnusedImports.removeUnusedImports(sourceCode);
      return formatter.formatSource(withoutUnusedImports);
    } catch (FormatterException e) {
      System.err.println("Warning: Failed to format Java code: " + e.getMessage());
      return sourceCode;
    } catch (Exception e) {
      System.err.println("Warning: Failed to remove unused imports: " + e.getMessage());
      // Fallback to just formatting without removing imports
      try {
        return formatter.formatSource(sourceCode);
      } catch (FormatterException fe) {
        System.err.println("Warning: Failed to format Java code: " + fe.getMessage());
        return sourceCode;
      }
    }
  }
}
