package com.chargebee.sdk.java.v4.builder;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.v4.JavaFormatter;
import com.chargebee.sdk.java.v4.util.CaseFormatUtil;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.NonNull;

/**
 * Builder that generates a {@code SubDomain} enum from unique subdomain values found in the OpenAPI
 * spec. Each operation may declare {@code x-cb-operation-sub-domain-name}; this builder collects
 * all distinct values, converts them to UPPER_UNDERSCORE enum constants, and renders the template.
 */
public class SubDomainEnumBuilder {

  private static final Logger LOGGER = Logger.getLogger(SubDomainEnumBuilder.class.getName());

  private Template template;
  private String outputDirectoryPath;
  private final List<FileOp> fileOps = new ArrayList<>();

  public SubDomainEnumBuilder withOutputDirectoryPath(@NonNull String outputDirectoryPath) {
    Objects.requireNonNull(outputDirectoryPath, "outputDirectoryPath must not be null");
    this.outputDirectoryPath = outputDirectoryPath + "/com/chargebee/v4/internal";
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  public SubDomainEnumBuilder withTemplate(@NonNull Template template) {
    this.template = Objects.requireNonNull(template, "template must not be null");
    return this;
  }

  public List<FileOp> build(@NonNull OpenAPI openApi) throws IOException {
    Objects.requireNonNull(openApi, "openApi must not be null");
    if (template == null) {
      throw new IllegalStateException("Template must be set before build()");
    }
    if (outputDirectoryPath == null || outputDirectoryPath.isEmpty()) {
      throw new IllegalStateException("Output directory path must be set before build()");
    }

    Set<String> subDomains = collectSubDomains(openApi);
    if (subDomains.isEmpty()) {
      LOGGER.log(Level.FINE, "No subdomain values found in OpenAPI spec");
      return fileOps;
    }

    List<Map<String, String>> enumValues = new ArrayList<>();
    for (String value : subDomains) {
      enumValues.add(Map.of("key", CaseFormatUtil.toUpperUnderscoreSafe(value), "value", value));
    }

    Map<String, Object> context = Map.of("enumValues", enumValues);
    String content = template.apply(context);
    String formattedContent = JavaFormatter.formatSafely(content);
    fileOps.add(new FileOp.WriteString(outputDirectoryPath, "SubDomain.java", formattedContent));

    LOGGER.log(Level.FINE, () -> "Generated SubDomain enum with " + enumValues.size() + " values");
    return fileOps;
  }

  private Set<String> collectSubDomains(OpenAPI openApi) {
    Set<String> subDomains = new LinkedHashSet<>();
    if (openApi.getPaths() == null) {
      return subDomains;
    }

    for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
      PathItem pathItem = pathEntry.getValue();
      if (pathItem == null) {
        continue;
      }
      Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();
      if (operations == null) {
        continue;
      }
      for (Operation operation : operations.values()) {
        if (operation == null || operation.getExtensions() == null) {
          continue;
        }
        Object value = operation.getExtensions().get(Extension.OPERATION_SUB_DOMAIN);
        if (value != null) {
          String sd = value.toString().trim();
          if (!sd.isEmpty()) {
            subDomains.add(sd);
          }
        }
      }
    }
    return subDomains;
  }
}
