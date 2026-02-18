package com.chargebee.sdk.java.v4.builder;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.v4.JavaFormatter;
import com.chargebee.sdk.java.v4.util.CaseFormatUtil;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.NonNull;

/**
 * Builds Java service classes from an OpenAPI specification using a Handlebars template.
 *
 * <p>Usage:
 * <pre>
 *   List<FileOp> fileOps = new ServiceBuilder()
 *       .withOutputDirectoryPath("/path/to/output")
 *       .withTemplate(template)
 *       .build(openApi);
 * </pre>
 *
 * <p>This builder: creates the services output directory, renders the provided template for each
 * discovered service, formats the generated Java, and returns file operations to be executed by
 * the caller.
 */
public class ServiceBuilder {

  // ---------------------------------------------------------------------------
  // Fields
  // ---------------------------------------------------------------------------

  private static final Logger LOGGER = Logger.getLogger(ServiceBuilder.class.getName());

  private Template template;
  private String outputDirectoryPath;
  private OpenAPI openApi;

  private final List<FileOp> fileOps = new ArrayList<>();

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /**
   * Sets the base output directory. Services are written under "core/services" inside this path.
   */
  public ServiceBuilder withOutputDirectoryPath(@NonNull String outputDirectoryPath) {
    Objects.requireNonNull(outputDirectoryPath, "outputDirectoryPath must not be null");
    this.outputDirectoryPath = outputDirectoryPath + "/com/chargebee/v4/services";
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  /** Sets the Handlebars template used to render each generated service. */
  public ServiceBuilder withTemplate(@NonNull Template template) {
    this.template = Objects.requireNonNull(template, "template must not be null");
    return this;
  }

  /**
   * Generates service classes from the provided OpenAPI specification.
   *
   * <p>Returns the list of file operations that the caller should execute to persist the files.
   */
  public List<FileOp> build(@NonNull OpenAPI openApi) {
    this.openApi = Objects.requireNonNull(openApi, "openApi must not be null");
    validateRequiredState();
    try {
      generateServices();
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Error generating services", e);
    }
    return fileOps;
  }

  // ---------------------------------------------------------------------------
  // Generation Orchestration
  // ---------------------------------------------------------------------------

  /** Coordinates service discovery and file operation creation. */
  private void generateServices() throws IOException {
    var services = getServices();
    if (services.isEmpty()) {
      LOGGER.log(Level.FINE, "No services discovered from OpenAPI spec");
      return;
    }
    for (var service : services) {
      var content = template.apply(service);
      var formattedContent = JavaFormatter.formatSafely(content);
      var fileName = createServiceFileName(service);
      fileOps.add(new FileOp.WriteString(this.outputDirectoryPath, fileName, formattedContent));
    }
    LOGGER.log(Level.FINE, () -> "Prepared file operations for " + services.size() + " services");
  }

  // ---------------------------------------------------------------------------
  // Service Discovery
  // ---------------------------------------------------------------------------

  /** Discovers services from the OpenAPI spec. */
  private List<Service> getServices() {
    Map<String, List<ServiceOperation>> operationsByResource = collectOperationsByResource(openApi);
    return toServices(operationsByResource);
  }

  /**
   * Builds a map of resource identifier to list of operations from the OpenAPI spec.
   */
  private Map<String, List<ServiceOperation>> collectOperationsByResource(OpenAPI openApi) {
    Map<String, List<ServiceOperation>> operationsByResource = new HashMap<>();

    if (openApi.getPaths() == null) {
      return operationsByResource;
    }

    for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
      String path = pathEntry.getKey();
      PathItem pathItem = pathEntry.getValue();
      processPathItem(path, pathItem, operationsByResource);
    }

    return operationsByResource;
  }

  /**
   * Processes a single path item and adds matching operations to the accumulator.
   */
  private void processPathItem(
      String path, PathItem pathItem, Map<String, List<ServiceOperation>> operationsByResource) {
    if (pathItem == null) {
      return;
    }
    Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();
    if (operations == null || operations.isEmpty()) {
      return;
    }

    for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : operations.entrySet()) {
      Operation operation = opEntry.getValue();
      if (!hasRequiredExtensions(operation)) {
        continue;
      }

      String httpMethod = opEntry.getKey().toString().toLowerCase();
      String resourceName = operation.getExtensions().get(Extension.RESOURCE_ID).toString();
      ServiceOperation serviceOp =
          createServiceOperation(path, httpMethod, resourceName, operation);
      operationsByResource.computeIfAbsent(resourceName, k -> new ArrayList<>()).add(serviceOp);
    }
  }

  private boolean hasRequiredExtensions(Operation operation) {
    if (operation == null
        || operation.getExtensions() == null
        || !operation.getExtensions().containsKey(Extension.RESOURCE_ID)
        || !operation.getExtensions().containsKey(Extension.SDK_METHOD_NAME)) {
      return false;
    }

    return true;
  }

  private ServiceOperation createServiceOperation(
      String path, String httpMethod, String resourceName, Operation operation) {
    var serviceOp = new ServiceOperation();

    String operationId = getExtensionAsString(operation, Extension.SDK_METHOD_NAME);
    String subDomain = getExtensionAsString(operation, Extension.OPERATION_SUB_DOMAIN);
    serviceOp.setOperationId(operationId);
    serviceOp.setModule(resourceName);
    serviceOp.setPath(path);
    serviceOp.setHttpMethod(httpMethod);
    serviceOp.setSubDomain(subDomain);
    serviceOp.setOperation(operation);

    // Set JSON input flag from x-cb-is-operation-needs-json-input extension
    Object needsJsonInput =
        operation.getExtensions() != null
            ? operation.getExtensions().get(Extension.IS_OPERATION_NEEDS_JSON_INPUT)
            : null;
    serviceOp.setOperationNeedsJsonInput(needsJsonInput != null && (boolean) needsJsonInput);

    // Detect true batch operations by x-cb-batch-operation-path-id extension
    String batchPathId = getExtensionAsString(operation, Extension.BATCH_OPERATION_PATH_ID);
    if (batchPathId != null) {
      serviceOp.setBatchOperation(true);
      serviceOp.setBatchPathId(batchPathId);
      // Store the non-batch URI for BatchRequest construction (strip /batch prefix)
      if (path != null && path.startsWith("/batch")) {
        serviceOp.setBatchUri(path.substring("/batch".length()));
      } else {
        serviceOp.setBatchUri(path);
      }
    }

    return serviceOp;
  }

  /** Converts the collected operations map into a list of services. */
  private List<Service> toServices(Map<String, List<ServiceOperation>> operationsByResource) {
    List<Service> services = new ArrayList<>();
    for (Map.Entry<String, List<ServiceOperation>> entry : operationsByResource.entrySet()) {
      String resourceName = entry.getKey();
      List<ServiceOperation> operations = entry.getValue();

      Service service = new Service();
      service.setPackageName(CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, resourceName));
      service.setName(CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, resourceName));
      service.setOperations(operations);

      services.add(service);
    }
    return services;
  }

  // ---------------------------------------------------------------------------
  // Validation & Utilities
  // ---------------------------------------------------------------------------

  private void validateRequiredState() {
    if (this.outputDirectoryPath == null || this.outputDirectoryPath.isEmpty()) {
      throw new IllegalStateException("Output directory path must be set before build()");
    }
    if (this.template == null) {
      throw new IllegalStateException("Template must be set before build()");
    }
  }

  /** Returns the string value of a custom OpenAPI extension or null. */
  private static String getExtensionAsString(Operation operation, String key) {
    if (operation == null || operation.getExtensions() == null) return null;
    var value = operation.getExtensions().get(key);
    return value != null ? value.toString() : null;
  }

  private String createServiceFileName(Service service) {
    return service.getName() + "Service.java";
  }

  @lombok.Data
  private static class Service {
    private String packageName;
    private String name;
    private List<ServiceOperation> operations;

    @SuppressWarnings("unused")
    public boolean hasBatchOperations() {
      return operations != null && operations.stream().anyMatch(ServiceOperation::isBatchOperation);
    }

    @SuppressWarnings("unused")
    public boolean hasSubDomainOperations() {
      return operations != null && operations.stream().anyMatch(ServiceOperation::hasSubDomain);
    }
  }

  @lombok.Data
  private static class ServiceOperation {
    private String operationId;
    private String module;
    private String path;
    private String httpMethod;
    private String subDomain;
    private boolean batchOperation;
    private String batchPathId;
    private String batchUri;
    private boolean operationNeedsJsonInput;
    private io.swagger.v3.oas.models.Operation
        operation; // Store full operation for response analysis

    @SuppressWarnings("unused")
    public String getMethodName() {
      return operationId;
    }

    @SuppressWarnings("unused")
    public String getParamsClassName() {
      var operationId = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, getOperationId());
      var moduleSnake =
          module != null && module.contains("_")
              ? module
              : CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, module);

      // If operationId contains the module name (or its singular/plural variations), don't prefix
      // it
      var operationSnake = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, getOperationId());
      var moduleBase = moduleSnake.replaceAll("_", "");
      var operationBase = operationSnake.replaceAll("_", "");
      boolean shouldSkipPrefix =
          operationSnake.contains(moduleSnake)
              || operationBase.contains(moduleBase)
              || moduleBase.contains(operationBase);
      if (shouldSkipPrefix) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, operationId) + "Params";
      }
      var prefixedActionName = moduleSnake + "_" + operationId;
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, prefixedActionName) + "Params";
    }

    @SuppressWarnings("unused")
    public String getReturnType() {
      // Generate response class name based on operation
      var operationId = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, getOperationId());

      // Check if operationId already contains the module name to avoid duplication
      var moduleSnake =
          module != null && module.contains("_")
              ? module
              : CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, module);

      // If operationId contains the module name (or its singular/plural variations), don't prefix
      // it
      var operationSnake = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, getOperationId());
      var moduleBase = moduleSnake.replaceAll("_", "");
      var operationBase = operationSnake.replaceAll("_", "");
      boolean shouldSkipPrefix =
          operationSnake.contains(moduleSnake)
              || operationBase.contains(moduleBase)
              || moduleBase.contains(operationBase);
      if (shouldSkipPrefix) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, operationId) + "Response";
      }
      var prefixedActionName = moduleSnake + "_" + operationId;
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, prefixedActionName)
          + "Response";
    }

    @SuppressWarnings("unused")
    public boolean isListResponse() {
      if (operation != null && operation.getResponses() != null) {
        var response200 = operation.getResponses().get("200");
        if (response200 != null && response200.getContent() != null) {
          var jsonContent = response200.getContent().get("application/json");
          if (jsonContent != null && jsonContent.getSchema() != null) {
            return isPaginatedListResponse(jsonContent.getSchema());
          }
        }
      }
      return false;
    }

    /** Returns true if the operation defines a request body. */
    public boolean hasRequestBody() {
      return operation != null && operation.getRequestBody() != null;
    }

    private boolean isPaginatedListResponse(io.swagger.v3.oas.models.media.Schema<?> schema) {
      if (schema == null || schema.getProperties() == null) {
        return false;
      }
      var properties = schema.getProperties();
      return properties.containsKey("list") && properties.containsKey("next_offset");
    }

    public String getModule() {
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, module);
    }

    public boolean hasPathParams() {
      return path != null && path.contains("{");
    }

    @SuppressWarnings("unused")
    public String getPathParamName() {
      if (!hasPathParams()) return null;
      // Extract parameter name from path like "/customers/{customer-id}" -> "customerId"
      int start = path.indexOf('{');
      int end = path.indexOf('}', start);
      if (start != -1 && end != -1) {
        String paramName = path.substring(start + 1, end);
        // Convert "customer-id" to "customerId"
        return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, paramName);
      }
      return null;
    }

    @SuppressWarnings("unused")
    public String getPathParam() {
      if (!hasPathParams()) return null;
      // Extract raw parameter name from path like "/customers/{customer-id}" -> "customer-id"
      int start = path.indexOf('{');
      int end = path.indexOf('}', start);
      if (start != -1 && end != -1) {
        return path.substring(start + 1, end);
      }
      return null;
    }

    /**
     * Returns true if the operation has query parameters (for GET operations).
     * This is used to determine if we should generate methods that accept params.
     */
    @SuppressWarnings("unused")
    public boolean hasQueryParams() {
      if (operation == null || operation.getParameters() == null) {
        return false;
      }
      return operation.getParameters().stream()
          .anyMatch(
              param -> param != null && "query".equals(param.getIn()) && param.getSchema() != null);
    }

    /**
     * Returns true if the operation has a request body but all parameters are optional.
     * Used to generate no-params overloads for better DX when all params are optional.
     */
    @SuppressWarnings("unused")
    public boolean isAllRequestBodyParamsOptional() {
      if (operation == null || operation.getRequestBody() == null) {
        return false;
      }
      var content = operation.getRequestBody().getContent();
      if (content == null) {
        return true; // No content means no required params
      }
      var mediaType = content.get("application/x-www-form-urlencoded");
      if (mediaType == null) {
        mediaType = content.get("application/json");
      }
      if (mediaType == null || mediaType.getSchema() == null) {
        return true; // No schema means no required params
      }
      var schema = mediaType.getSchema();
      var required = schema.getRequired();
      return required == null || required.isEmpty();
    }

    @SuppressWarnings("unused")
    public boolean isAllQueryParamsOptional() {
      if (operation == null || !"get".equalsIgnoreCase(httpMethod)) {
        return false;
      }
      var parameters = operation.getParameters();
      if (parameters == null || parameters.isEmpty()) {
        return true;
      }
      for (var param : parameters) {
        if (param != null && "query".equals(param.getIn())) {
          if (Boolean.TRUE.equals(param.getRequired())) {
            return false;
          }
        }
      }
      return true;
    }

    @SuppressWarnings("unused")
    public boolean hasSubDomain() {
      return subDomain != null && !subDomain.trim().isEmpty();
    }

    @SuppressWarnings("unused")
    public String getSubDomainEnumRef() {
      if (!hasSubDomain()) return null;
      return "SubDomain." + CaseFormatUtil.toUpperUnderscoreSafe(subDomain);
    }

    @SuppressWarnings("unused")
    public String getBatchMethodName() {
      if (!batchOperation) return null;
      // Prefix with "batch" and capitalize the first letter of operationId
      return "batch" + Character.toUpperCase(operationId.charAt(0)) + operationId.substring(1);
    }
  }
}
