package com.chargebee.sdk.java;

public class Constants {

  public static final String ADD_OPT = "addOpt";
  public static final String ADD = "add";
  public static final String TIMESTAMP = "timestamp";
  public static final String LIST_OF = "List<";
  public static final String ENUMS_EXPORT = "com.chargebee.models.enums.";
  public static final String ENUMS_EXPORT_INTERNAL = "com.chargebee.v2.models.enums.";
  public static final String TYPE = "Type";

  public static final String MODELS_DOT_ENUMS = ".models.enums.";
  public static final String SORT_BY = "sort_by";
  public static final String DATE_TIME = "DateTime";

  public static final String MODELS = "models";
  public static final String ENUMS = "enums";
  public static final String INTERNAL = "internal";
  public static final String EXCEPTIONS = "exceptions";
  public static final String STRING_TYPE = "string";
  public static final String INT_TYPE = "Integer";
  public static final String REQUEST = "Request";
  public static final String DOT_CLASS = ".class)";
  public static final String BOOLEAN_TYPE = "boolean";
  public static final String BATCH = "_batch";
  public static final String NUMBER_TYPE = "number";
  public static final String CREDIT_NOTE = "credit_note";

  /**
   * Java reserved words (keywords and literals) that cannot be used as method identifiers.
   * When an API attribute name (e.g. "enum") collides with one of these after case conversion,
   * the generated getter method name must be escaped (see Java#getName(String)).
   */
  public static final java.util.Set<String> JAVA_RESERVED_WORDS =
      java.util.Set.of(
          "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
          "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
          "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
          "interface", "long", "native", "new", "package", "private", "protected", "public",
          "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
          "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
          "null");

  private Constants() {}
}
