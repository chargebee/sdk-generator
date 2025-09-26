package com.chargebee.sdk.java.javanext.builder;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.javanext.JavaFormatter;
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
    this.outputDirectoryPath = outputDirectoryPath + "/core/services";
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
    return operation != null
        && operation.getExtensions() != null
        && operation.getExtensions().containsKey(Extension.RESOURCE_ID)
        && operation.getExtensions().containsKey(Extension.OPERATION_METHOD_NAME);
  }

  private ServiceOperation createServiceOperation(
      String path, String httpMethod, String resourceName, Operation operation) {
    var serviceOp = new ServiceOperation();
    serviceOp.setOperationId(
        operation.getExtensions().get(Extension.OPERATION_METHOD_NAME).toString());
    serviceOp.setModule(resourceName);
    serviceOp.setPath(path);
    serviceOp.setHttpMethod(httpMethod);
    serviceOp.setOperation(operation);
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

  private String createServiceFileName(Service service) {
    return service.getName() + "Service.java";
  }

  @lombok.Data
  private static class Service {
    private String packageName;
    private String name;
    private List<ServiceOperation> operations;
  }

  @lombok.Data
  private static class ServiceOperation {
    private String operationId;
    private String module;
    private String path;
    private String httpMethod;
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
      var actionName = moduleSnake + "_" + operationId;
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, actionName) + "Params";
    }

    @SuppressWarnings("unused")
    public String getReturnType() {
      // Generate response class name based on operation
      var operationId = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, getOperationId());
      var moduleSnake =
          module != null && module.contains("_")
              ? module
              : CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, module);
      var actionName = moduleSnake + "_" + operationId;
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, actionName) + "Response";
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
  }
}
