package com.chargebee.sdk.java.v4.builder;

import com.chargebee.handlebar.Inflector;
import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.v4.JavaFormatter;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ServiceRegistryBuilder {

  // ---------------------------------------------------------------------------------------------
  // Constants & Logger
  // ---------------------------------------------------------------------------------------------
  private static final Logger LOGGER = Logger.getLogger(ServiceRegistryBuilder.class.getName());
  private static final String CLIENT_DIR_SUFFIX = "/com/chargebee/v4/client";

  // ---------------------------------------------------------------------------------------------
  // State
  // ---------------------------------------------------------------------------------------------
  private Template template;
  private String outputDirectoryPath;
  private OpenAPI openApi;

  private final List<FileOp> fileOps = new ArrayList<>();

  // ---------------------------------------------------------------------------------------------
  // Fluent configuration API
  // ---------------------------------------------------------------------------------------------
  /**
   * Sets the output base directory and ensures the client folder exists.
   * Output structure: <base>/client
   *
   * @param outputDirectoryPath base generation directory (non-null, non-blank)
   * @return this builder instance
   * @throws IllegalArgumentException when the provided path is null or blank
   */
  public ServiceRegistryBuilder withOutputDirectoryPath(String outputDirectoryPath) {
    if (outputDirectoryPath == null || outputDirectoryPath.trim().isEmpty()) {
      throw new IllegalArgumentException("outputDirectoryPath must not be null or blank");
    }
    this.outputDirectoryPath = outputDirectoryPath + CLIENT_DIR_SUFFIX;
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  /**
   * Sets the Handlebars template used to render the service registry file.
   *
   * @param template non-null template instance
   * @return this builder instance
   * @throws NullPointerException if template is null
   */
  public ServiceRegistryBuilder withTemplate(Template template) {
    this.template = Objects.requireNonNull(template, "template");
    return this;
  }

  /**
   * Generates the service registry using the provided OpenAPI specification and
   * returns the list of file operations to be performed by the caller.
   *
   * @param openApi non-null OpenAPI specification
   * @return ordered list of file operations
   * @throws IllegalStateException when required configuration is missing
   */
  public List<FileOp> build(OpenAPI openApi) {
    this.openApi = Objects.requireNonNull(openApi, "openApi");
    if (this.template == null) {
      throw new IllegalStateException("Template not set. Call withTemplate(...) before build().");
    }
    if (this.outputDirectoryPath == null || this.outputDirectoryPath.isEmpty()) {
      throw new IllegalStateException(
          "Output directory not set. Call withOutputDirectoryPath(...) before build().");
    }
    generateServiceRegistry();
    return fileOps;
  }

  // ---------------------------------------------------------------------------------------------
  // Generation Orchestration
  // ---------------------------------------------------------------------------------------------
  private void generateServiceRegistry() {
    try {
      var services = getServices();
      var serviceRegistry = new ServiceRegistry();
      serviceRegistry.setServices(
          services.keySet().stream().map(this::buildServiceInfo).collect(Collectors.toList()));

      var content = template.apply(serviceRegistry);
      var formattedContent = JavaFormatter.formatSafely(content);
      fileOps.add(
          new FileOp.WriteString(
              this.outputDirectoryPath, "ServiceRegistry.java", formattedContent));
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Error generating service registry", e);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Discovery
  // ---------------------------------------------------------------------------------------------
  private Map<String, Schema<?>> getServices() {
    // Get resources that actually have operations, not just schemas
    Set<String> resourcesWithOperations = getResourcesWithOperations();

    // Create a map with dummy schemas for resources that have operations but no schema
    Map<String, Schema<?>> services = new HashMap<>();

    // Add services from existing schemas
    var components = openApi.getComponents();
    if (components != null && components.getSchemas() != null) {
      var schemas = components.getSchemas();
      schemas.entrySet().stream()
          .filter(entry -> entry.getValue().getProperties() != null)
          .filter(entry -> !entry.getKey().startsWith("4") && !entry.getKey().startsWith("5"))
          // Only include resources that actually have operations
          .filter(entry -> resourcesWithOperations.contains(entry.getKey()))
          .forEach(entry -> services.put(entry.getKey(), entry.getValue()));
    }

    // Add services for resources that have operations but no schema definition
    for (String resourceName : resourcesWithOperations) {
      if (!services.containsKey(resourceName)) {
        // Create a dummy schema for resources that have operations but no schema
        Schema<?> dummySchema = new Schema<>();
        dummySchema.setProperties(new HashMap<>()); // Empty properties to pass the filter
        services.put(resourceName, dummySchema);
      }
    }

    return services;
  }

  /**
   * Get the set of resource names that have actual operations in the OpenAPI spec.
   */
  private Set<String> getResourcesWithOperations() {
    Set<String> resourcesWithOperations = new HashSet<>();

    if (openApi.getPaths() == null) {
      return resourcesWithOperations;
    }

    // Iterate through all paths and operations to find resources with operations
    for (var pathEntry : openApi.getPaths().entrySet()) {
      var pathItem = pathEntry.getValue();
      var operations = pathItem.readOperationsMap();

      for (var operationEntry : operations.entrySet()) {
        var operation = operationEntry.getValue();

        // Check if operation has the required extensions
        if (operation.getExtensions() != null
            && operation.getExtensions().containsKey(Extension.RESOURCE_ID)
            && operation.getExtensions().containsKey(Extension.OPERATION_METHOD_NAME)) {

          String resourceName = operation.getExtensions().get(Extension.RESOURCE_ID).toString();
          resourcesWithOperations.add(resourceName);
        }
      }
    }

    return resourcesWithOperations;
  }

  // ---------------------------------------------------------------------------------------------
  // Utilities
  // ---------------------------------------------------------------------------------------------
  /** Builds a ServiceInfo DTO from a resource name. */
  private ServiceInfo buildServiceInfo(String resourceName) {
    var service = new ServiceInfo();
    service.setName(resourceName);
    service.setClassName(toServiceClassName(resourceName));
    service.setMethodName(toServiceMethodName(resourceName));
    service.setFieldName(toServiceFieldName(resourceName));
    return service;
  }

  private static String toServiceClassName(String resourceName) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, resourceName) + "Service";
  }

  private static String toServiceMethodName(String resourceName) {
    String camelCase = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, resourceName);
    return Inflector.pluralize(camelCase);
  }

  private static String toServiceFieldName(String resourceName) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, resourceName) + "Service";
  }

  // ---------------------------------------------------------------------------------------------
  // Model
  // ---------------------------------------------------------------------------------------------
  @lombok.Data
  private static class ServiceRegistry {
    private List<ServiceInfo> services;
  }

  @lombok.Data
  private static class ServiceInfo {
    private String name; // customer
    private String className; // CustomerService
    private String methodName; // customers
    private String fieldName; // customerService
  }
}
