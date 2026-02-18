package com.chargebee.sdk.java.v4.builder;

import com.chargebee.handlebar.Inflector;
import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.v4.JavaFormatter;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * Builds client methods source files from an OpenAPI specification using Handlebars templates.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Derive the list of resources that expose operations</li>
 *   <li>Transform resource names into service metadata used by templates</li>
 *   <li>Render and format the ClientMethods interface and its implementation</li>
 * </ul>
 */
public class ClientMethodsBuilder {

  // --------------------------------------------------------------------------------------
  // Constants
  // --------------------------------------------------------------------------------------

  private static final String CLIENT_SUBDIR = "/com/chargebee/v4/client";
  private static final String KEY_SERVICES = "services";
  private static final String KEY_IMPORTS = "imports";
  private static final String CLIENT_METHODS_FILENAME = "ClientMethods.java";
  private static final String CLIENT_METHODS_IMPL_FILENAME = "ClientMethodsImpl.java";

  // --------------------------------------------------------------------------------------
  // State
  // --------------------------------------------------------------------------------------

  private String outputDirectoryPath;
  private Template clientMethodsTemplate;
  private Template clientMethodsImplTemplate;

  // --------------------------------------------------------------------------------------
  // Fluent configuration
  // --------------------------------------------------------------------------------------

  /**
   * Configure the output directory. Files will be written under a "client" subdirectory.
   *
   * @param outputDirectoryPath base output directory path (non-null, non-blank)
   * @return this builder
   */
  public ClientMethodsBuilder withOutputDirectoryPath(String outputDirectoryPath) {
    validateNonBlank(outputDirectoryPath, "outputDirectoryPath");
    this.outputDirectoryPath = outputDirectoryPath + CLIENT_SUBDIR;
    return this;
  }

  /**
   * Set the Handlebars template used to generate the ClientMethods interface.
   *
   * @param template template instance (non-null)
   * @return this builder
   */
  public ClientMethodsBuilder withClientMethodsTemplate(Template template) {
    this.clientMethodsTemplate = Objects.requireNonNull(template, "clientMethodsTemplate");
    return this;
  }

  /**
   * Set the Handlebars template used to generate the ClientMethodsImpl class.
   *
   * @param template template instance (non-null)
   * @return this builder
   */
  public ClientMethodsBuilder withClientMethodsImplTemplate(Template template) {
    this.clientMethodsImplTemplate = Objects.requireNonNull(template, "clientMethodsImplTemplate");
    return this;
  }

  // --------------------------------------------------------------------------------------
  // Build
  // --------------------------------------------------------------------------------------

  /**
   * Generate file operations for ClientMethods sources using the provided OpenAPI model.
   *
   * @param openAPI the OpenAPI specification (non-null)
   * @return list of file operations to persist the generated sources
   * @throws IllegalStateException if required configuration is missing
   */
  public List<FileOp> build(OpenAPI openAPI) {
    Objects.requireNonNull(openAPI, "openAPI");
    ensureConfigured();

    List<FileOp> fileOps = new ArrayList<>();

    // Derive services and imports from OpenAPI
    Set<String> resourcesWithOperations = getResourcesWithOperations(openAPI);
    List<ServiceInfo> services =
        resourcesWithOperations.stream().map(this::createServiceInfo).collect(Collectors.toList());
    Set<String> imports = deriveImports(services);

    Map<String, Object> templateData = new HashMap<>();
    templateData.put(KEY_SERVICES, services);
    templateData.put(KEY_IMPORTS, imports);

    // Render interface and implementation
    fileOps.add(new FileOp.CreateDirectory(outputDirectoryPath, ""));
    fileOps.add(renderToWriteOp(clientMethodsTemplate, templateData, CLIENT_METHODS_FILENAME));
    fileOps.add(
        renderToWriteOp(clientMethodsImplTemplate, templateData, CLIENT_METHODS_IMPL_FILENAME));

    return fileOps;
  }

  // --------------------------------------------------------------------------------------
  // Derivation helpers
  // --------------------------------------------------------------------------------------

  private Set<String> getResourcesWithOperations(OpenAPI openAPI) {
    Set<String> resourcesWithOperations = new HashSet<>();

    if (openAPI.getPaths() == null) {
      return resourcesWithOperations;
    }

    for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
      PathItem pathItem = pathEntry.getValue();
      if (pathItem == null) {
        continue;
      }

      List<Operation> operations =
          Arrays.asList(
              pathItem.getGet(),
              pathItem.getPost(),
              pathItem.getPut(),
              pathItem.getDelete(),
              pathItem.getPatch());

      for (Operation operation : operations) {
        if (operation == null || operation.getExtensions() == null) {
          continue;
        }

        Object resourceIdObj = operation.getExtensions().get(Extension.RESOURCE_ID);
        if (resourceIdObj != null) {
          resourcesWithOperations.add(resourceIdObj.toString());
        }
      }
    }

    return resourcesWithOperations;
  }

  private Set<String> deriveImports(List<ServiceInfo> services) {
    return services.stream()
        .map(service -> "com.chargebee.v4.services." + service.getClassName())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private ServiceInfo createServiceInfo(String resourceName) {
    ServiceInfo serviceInfo = new ServiceInfo();
    serviceInfo.setResourceName(resourceName);
    String camelCase = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, resourceName);
    serviceInfo.setMethodName(Inflector.pluralize(camelCase));
    serviceInfo.setClassName(
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, resourceName) + "Service");
    return serviceInfo;
  }

  private FileOp.WriteString renderToWriteOp(
      Template template, Map<String, Object> templateData, String fileName) {
    try {
      String content = template.apply(templateData);
      String formattedContent = JavaFormatter.formatSafely(content);
      return new FileOp.WriteString(outputDirectoryPath, fileName, formattedContent);
    } catch (Exception e) {
      throw new RuntimeException("Failed to render template for " + fileName, e);
    }
  }

  // --------------------------------------------------------------------------------------
  // Validation helpers
  // --------------------------------------------------------------------------------------

  private void ensureConfigured() {
    validateNonBlank(outputDirectoryPath, "outputDirectoryPath (via withOutputDirectoryPath)");
    Objects.requireNonNull(clientMethodsTemplate, "clientMethodsTemplate is not configured");
    Objects.requireNonNull(
        clientMethodsImplTemplate, "clientMethodsImplTemplate is not configured");
  }

  private void validateNonBlank(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must be non-null and non-blank");
    }
  }

  // --------------------------------------------------------------------------------------
  // Model
  // --------------------------------------------------------------------------------------

  @Data
  public static class ServiceInfo {
    private String resourceName;
    private String methodName;
    private String className;
  }
}
