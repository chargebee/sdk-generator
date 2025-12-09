package com.chargebee.sdk.java.javanext.core;

import com.chargebee.sdk.java.javanext.util.CaseFormatUtil;
import java.util.List;

/**
 * Represents an exception class to be generated (e.g., PaymentException, InvalidRequestException).
 */
@lombok.Data
public class ExceptionClass {
  private String name;
  private String errorType;
  private String description;
  private String httpStatusCode;
  private String apiErrorCodeEnumName;
  private List<String> apiErrorCodes;

  /**
   * Get the class name in UpperCamelCase with "Exception" suffix.
   */
  public String getClassName() {
    return CaseFormatUtil.toUpperCamelSafe(errorType) + "Exception";
  }

  /**
   * Get the error type value for matching (e.g., "payment", "invalid_request").
   */
  public String getErrorTypeValue() {
    return errorType;
  }
}
