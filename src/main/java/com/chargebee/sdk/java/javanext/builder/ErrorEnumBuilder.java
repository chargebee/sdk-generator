package com.chargebee.sdk.java.javanext.builder;

import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.javanext.JavaFormatter;
import com.chargebee.sdk.java.javanext.core.ErrorEnum;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builder that generates error enum classes from OpenAPI error schemas.
 * Generates:
 * - Per-HTTP-status-code ApiErrorCode enums (e.g., BadRequestApiErrorCode, UnauthorizedApiErrorCode)
 * - ErrorType enum containing all possible error types
 */
public class ErrorEnumBuilder {

  private static final String EXCEPTIONS_PACKAGE = "exceptions";
  private static final String CODES_PACKAGE = "exceptions/codes";
  private static final Map<String, String> HTTP_STATUS_NAMES =
      Map.ofEntries(
          Map.entry("400", "BadRequest"),
          Map.entry("401", "Unauthorized"),
          Map.entry("403", "Forbidden"),
          Map.entry("404", "NotFound"),
          Map.entry("405", "MethodNotAllowed"),
          Map.entry("409", "Conflict"),
          Map.entry("422", "UnprocessableEntity"),
          Map.entry("429", "TooManyRequests"),
          Map.entry("500", "InternalServerError"),
          Map.entry("502", "BadGateway"),
          Map.entry("503", "ServiceUnavailable"),
          Map.entry("504", "GatewayTimeout"));

  private Template template;
  private Template interfaceTemplate;
  private String outputDirectoryPath;
  private String codesOutputDirectoryPath;
  private final List<FileOp> fileOps = new ArrayList<>();

  public ErrorEnumBuilder withOutputDirectoryPath(String outputDirectoryPath) {
    this.outputDirectoryPath = outputDirectoryPath + "/com/chargebee/v4/" + EXCEPTIONS_PACKAGE;
    this.codesOutputDirectoryPath = outputDirectoryPath + "/com/chargebee/v4/" + CODES_PACKAGE;
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    fileOps.add(new FileOp.CreateDirectory(this.codesOutputDirectoryPath, ""));
    return this;
  }

  public ErrorEnumBuilder withTemplate(Template template) {
    this.template = template;
    return this;
  }

  public ErrorEnumBuilder withInterfaceTemplate(Template interfaceTemplate) {
    this.interfaceTemplate = interfaceTemplate;
    return this;
  }

  public List<FileOp> build(OpenAPI openApi) throws IOException {
    // Generate the ApiErrorCode interface first
    generateApiErrorCodeInterface();
    generateErrorEnums(openApi);
    return fileOps;
  }

  private void generateApiErrorCodeInterface() throws IOException {
    if (interfaceTemplate != null) {
      String content = interfaceTemplate.apply(Map.of());
      String formattedContent = JavaFormatter.formatSafely(content);
      // Write ApiErrorCode interface to the codes subpackage
      fileOps.add(
          new FileOp.WriteString(
              this.codesOutputDirectoryPath, "ApiErrorCode.java", formattedContent));
    }
  }

  private void generateErrorEnums(OpenAPI openApi) throws IOException {
    if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
      return;
    }

    Map<String, Schema> schemas = openApi.getComponents().getSchemas();
    Set<String> allErrorTypes = new LinkedHashSet<>();

    // Generate per-status-code ApiErrorCode enums
    for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
      String schemaName = entry.getKey();
      Schema<?> schema = entry.getValue();

      // Check if this is an error schema (numeric name like "400", "401", etc.)
      if (isErrorSchema(schemaName, schema)) {
        generateApiErrorCodeEnum(schemaName, schema);

        // Collect error types from this schema
        collectErrorTypes(schema, allErrorTypes);
      }
    }

    // Generate unified ErrorType enum
    if (!allErrorTypes.isEmpty()) {
      generateErrorTypeEnum(allErrorTypes);
    }
  }

  private boolean isErrorSchema(String schemaName, Schema<?> schema) {
    // Error schemas are named with HTTP status codes (400, 401, etc.)
    if (!schemaName.matches("\\d{3}")) {
      return false;
    }
    // Must have api_error_code and message properties
    if (schema.getProperties() == null) {
      return false;
    }
    return schema.getProperties().containsKey("api_error_code")
        && schema.getProperties().containsKey("message");
  }

  private void generateApiErrorCodeEnum(String statusCode, Schema<?> schema) throws IOException {
    Schema<?> apiErrorCodeSchema = (Schema<?>) schema.getProperties().get("api_error_code");
    if (apiErrorCodeSchema == null || apiErrorCodeSchema.getEnum() == null) {
      return;
    }

    String statusName = HTTP_STATUS_NAMES.getOrDefault(statusCode, "Http" + statusCode);

    ErrorEnum errorEnum = new ErrorEnum();
    errorEnum.setName(statusName + "ApiErrorCode");
    errorEnum.setHttpStatusCode(statusCode);
    errorEnum.setDescription("API error codes for HTTP " + statusCode + " responses");
    errorEnum.setApiErrorCode(true); // This enum implements ApiErrorCode interface
    errorEnum.setValues(
        apiErrorCodeSchema.getEnum().stream().map(Object::toString).collect(Collectors.toList()));

    String content = template.apply(errorEnum);
    String formattedContent = JavaFormatter.formatSafely(content);
    // Write API error code enums to the codes subpackage
    fileOps.add(
        new FileOp.WriteString(
            this.codesOutputDirectoryPath, errorEnum.getName() + ".java", formattedContent));
  }

  private void collectErrorTypes(Schema<?> schema, Set<String> allErrorTypes) {
    Schema<?> typeSchema = (Schema<?>) schema.getProperties().get("type");
    if (typeSchema != null && typeSchema.getEnum() != null) {
      typeSchema.getEnum().forEach(value -> allErrorTypes.add(value.toString()));
    }
  }

  private void generateErrorTypeEnum(Set<String> errorTypes) throws IOException {
    ErrorEnum errorEnum = new ErrorEnum();
    errorEnum.setName("ErrorType");
    errorEnum.setDescription("Types of errors returned by the Chargebee API");
    errorEnum.setValues(new ArrayList<>(errorTypes));

    String content = template.apply(errorEnum);
    String formattedContent = JavaFormatter.formatSafely(content);
    fileOps.add(
        new FileOp.WriteString(this.outputDirectoryPath, "ErrorType.java", formattedContent));
  }

  /**
   * Get the mapping of HTTP status codes to their human-readable names.
   */
  public static Map<String, String> getHttpStatusNames() {
    return HTTP_STATUS_NAMES;
  }
}
