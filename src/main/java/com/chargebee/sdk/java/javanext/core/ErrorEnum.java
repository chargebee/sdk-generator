package com.chargebee.sdk.java.javanext.core;

import com.chargebee.sdk.java.javanext.util.CaseFormatUtil;
import java.util.AbstractMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents an error enum (e.g., BadRequestApiErrorCode, ErrorType).
 */
@lombok.Data
public class ErrorEnum {
  private String name;
  private String description;
  private List<String> values;
  private String httpStatusCode;
  private boolean isApiErrorCode;

  /**
   * Get enum values as key-value pairs where key is the UPPER_SNAKE_CASE enum constant
   * and value is the original API string value.
   */
  public List<AbstractMap.SimpleEntry<String, String>> getEnumValues() {
    return values.stream()
        .map(
            value ->
                new AbstractMap.SimpleEntry<>(CaseFormatUtil.toUpperUnderscoreSafe(value), value))
        .collect(Collectors.toList());
  }

  /**
   * Check if this enum is an API error code enum (should implement ApiErrorCode interface).
   * ErrorType enum should NOT implement this interface.
   */
  public boolean isApiErrorCode() {
    return isApiErrorCode;
  }
}
