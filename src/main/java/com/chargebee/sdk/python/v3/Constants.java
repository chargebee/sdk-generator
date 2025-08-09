package com.chargebee.sdk.python.v3;

import java.util.regex.Pattern;

public class Constants {
  public static final String STRING_TYPE = "str";

  public static final String LIST_OF_STRING_TYPE = "List[str]";
  public static final String INT_TYPE = "int";
  public static final String FLOAT_TYPE = "float";
  public static final String BOOLEAN_TYPE = "bool";
  public static final String ANY_TYPE = "Any";
  public static final String JSON_OBJECT_TYPE = "Dict[Any, Any]";
  public static final String JSON_ARRAY_TYPE = "List[Dict[Any, Any]]";

  public static final String INT_SIXTY_FOUR = "int64";
  public static final String INT_THIRTY_TWO = "int32";
  public static final String UNIX_TIME = "unix-time";
  public static final String SORT_BY = "sort_by";
  public static final String ENUM_DOT = "Enum.";
  public static final String FILTER = "Filters.";
  public static final String ENUM_WITH_DELIMITER = "enums.";
  public static final String REFERENCE_ENUM_DELIMITER = ".";

  public static final String INDENT_DELIMITER = String.format("%4s", "");
  public static final String DOUBLE_INDENT_DELIMITER = String.format("%8s", "");
  public static final String JOIN_DELIMITER = String.format("");
  public static final String MODEL_IMPORT = "\nfrom chargebee.models import ";
  public static final String RESPONSE_HEADERS = "headers: Dict[str, str] = None";
  public static final String RESPONSE_STATUS_CODE = "http_status_code: int = None";

  public static Pattern REFEREMCE_ENUM_PATTERN =
      Pattern.compile("^[\"a-z_]+\\.[A-Za-z]+\\.[A-Za-z]+\"$");
  public static Pattern GLOBAL_ENUM_PATTERN = Pattern.compile("\\b(enums.\\w*)\\b");

  public static final String STRING_JOIN_DELIMITER = "";
}
