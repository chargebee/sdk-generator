package com.chargebee.sdk.java.v4.builder;

import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.v4.JavaFormatter;
import com.chargebee.sdk.java.v4.core.ExceptionClass;
import com.chargebee.sdk.java.v4.util.CaseFormatUtil;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builder that generates exception classes from OpenAPI error schemas.
 * Generates:
 * - Base APIException class
 * - Specific exception classes per error type (PaymentException, InvalidRequestException, etc.)
 * - HttpStatusHandler for routing errors to appropriate exception classes
 */
public class ExceptionBuilder {

  private static final String EXCEPTIONS_PACKAGE = "exceptions";

  private Template exceptionTemplate;
  private Template baseExceptionTemplate;
  private Template httpStatusHandlerTemplate;
  private String outputDirectoryPath;
  private final List<FileOp> fileOps = new ArrayList<>();

  public ExceptionBuilder withOutputDirectoryPath(String outputDirectoryPath) {
    this.outputDirectoryPath = outputDirectoryPath + "/com/chargebee/v4/" + EXCEPTIONS_PACKAGE;
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    // Also create the transport directory for HttpStatusHandler
    fileOps.add(
        new FileOp.CreateDirectory(outputDirectoryPath + "/com/chargebee/v4/transport", ""));
    return this;
  }

  public ExceptionBuilder withExceptionTemplate(Template template) {
    this.exceptionTemplate = template;
    return this;
  }

  public ExceptionBuilder withBaseExceptionTemplate(Template template) {
    this.baseExceptionTemplate = template;
    return this;
  }

  public ExceptionBuilder withHttpStatusHandlerTemplate(Template template) {
    this.httpStatusHandlerTemplate = template;
    return this;
  }

  public List<FileOp> build(OpenAPI openApi) throws IOException {
    generateExceptions(openApi);
    return fileOps;
  }

  private void generateExceptions(OpenAPI openApi) throws IOException {
    if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
      return;
    }

    Map<String, Schema> schemas = openApi.getComponents().getSchemas();

    // Collect all unique error types and their associated HTTP status codes
    Map<String, Set<String>> errorTypeToStatusCodes = new LinkedHashMap<>();
    Map<String, Set<String>> errorTypeToApiErrorCodes = new LinkedHashMap<>();

    for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
      String schemaName = entry.getKey();
      Schema<?> schema = entry.getValue();

      if (isErrorSchema(schemaName, schema)) {
        collectErrorTypeInfo(schemaName, schema, errorTypeToStatusCodes, errorTypeToApiErrorCodes);
      }
    }

    // Generate base APIException
    generateBaseException(errorTypeToStatusCodes.keySet());

    // Generate specific exception classes for each error type
    for (String errorType : errorTypeToStatusCodes.keySet()) {
      generateExceptionClass(
          errorType,
          errorTypeToStatusCodes.get(errorType),
          errorTypeToApiErrorCodes.getOrDefault(errorType, Set.of()));
    }

    // Generate HttpStatusHandler
    generateHttpStatusHandler(errorTypeToStatusCodes.keySet());
  }

  private boolean isErrorSchema(String schemaName, Schema<?> schema) {
    if (!schemaName.matches("\\d{3}")) {
      return false;
    }
    if (schema.getProperties() == null) {
      return false;
    }
    return schema.getProperties().containsKey("api_error_code")
        && schema.getProperties().containsKey("message");
  }

  private void collectErrorTypeInfo(
      String statusCode,
      Schema<?> schema,
      Map<String, Set<String>> errorTypeToStatusCodes,
      Map<String, Set<String>> errorTypeToApiErrorCodes) {
    Schema<?> typeSchema = (Schema<?>) schema.getProperties().get("type");
    Schema<?> apiErrorCodeSchema = (Schema<?>) schema.getProperties().get("api_error_code");

    if (typeSchema != null && typeSchema.getEnum() != null) {
      for (Object typeValue : typeSchema.getEnum()) {
        String errorType = typeValue.toString();
        errorTypeToStatusCodes
            .computeIfAbsent(errorType, k -> new LinkedHashSet<>())
            .add(statusCode);

        if (apiErrorCodeSchema != null && apiErrorCodeSchema.getEnum() != null) {
          Set<String> apiErrorCodes =
              errorTypeToApiErrorCodes.computeIfAbsent(errorType, k -> new LinkedHashSet<>());
          apiErrorCodeSchema.getEnum().forEach(code -> apiErrorCodes.add(code.toString()));
        }
      }
    }
  }

  private void generateBaseException(Set<String> errorTypes) throws IOException {
    Map<String, Object> context = new HashMap<>();
    context.put(
        "errorTypes",
        errorTypes.stream()
            .map(
                type ->
                    Map.of(
                        "name", CaseFormatUtil.toUpperCamelSafe(type),
                        "value", type,
                        "enumConstant", CaseFormatUtil.toUpperUnderscoreSafe(type)))
            .collect(Collectors.toList()));

    String content = baseExceptionTemplate.apply(context);
    String formattedContent = JavaFormatter.formatSafely(content);
    fileOps.add(
        new FileOp.WriteString(this.outputDirectoryPath, "APIException.java", formattedContent));
  }

  private void generateExceptionClass(
      String errorType, Set<String> statusCodes, Set<String> apiErrorCodes) throws IOException {
    // Skip "untyped" as it will use base APIException
    if ("untyped".equals(errorType)) {
      return;
    }

    ExceptionClass exceptionClass = new ExceptionClass();
    exceptionClass.setErrorType(errorType);
    exceptionClass.setDescription(getExceptionDescription(errorType));
    exceptionClass.setHttpStatusCode(String.join(", ", statusCodes));
    exceptionClass.setApiErrorCodes(new ArrayList<>(apiErrorCodes));

    String content = exceptionTemplate.apply(exceptionClass);
    String formattedContent = JavaFormatter.formatSafely(content);
    fileOps.add(
        new FileOp.WriteString(
            this.outputDirectoryPath, exceptionClass.getClassName() + ".java", formattedContent));
  }

  private void generateHttpStatusHandler(Set<String> errorTypes) throws IOException {
    Map<String, Object> context = new HashMap<>();
    context.put(
        "errorTypes",
        errorTypes.stream()
            .filter(type -> !"untyped".equals(type))
            .map(
                type ->
                    Map.of(
                        "name",
                        CaseFormatUtil.toUpperCamelSafe(type),
                        "value",
                        type,
                        "enumConstant",
                        CaseFormatUtil.toUpperUnderscoreSafe(type),
                        "className",
                        CaseFormatUtil.toUpperCamelSafe(type) + "Exception"))
            .collect(Collectors.toList()));

    String content = httpStatusHandlerTemplate.apply(context);
    String formattedContent = JavaFormatter.formatSafely(content);
    fileOps.add(
        new FileOp.WriteString(
            this.outputDirectoryPath.replace("/exceptions", "/transport"),
            "HttpStatusHandler.java",
            formattedContent));
  }

  private String getExceptionDescription(String errorType) {
    return switch (errorType) {
      case "payment" -> "Exception thrown for payment-related errors.";
      case "invalid_request" ->
          "Exception thrown for invalid request errors including validation failures.";
      case "operation_failed" ->
          "Exception thrown when an operation fails due to business logic constraints.";
      default -> "Exception thrown for " + errorType.replace("_", " ") + " errors.";
    };
  }
}
