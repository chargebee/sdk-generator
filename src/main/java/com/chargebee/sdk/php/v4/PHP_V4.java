package com.chargebee.sdk.php.v4;

import static com.chargebee.sdk.php.v4.Constants.*;

import com.chargebee.openapi.*;
import com.chargebee.openapi.Error;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.php.v4.generators.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class PHP_V4 extends Language {
  protected final String[] hiddenOverride = {"media"};
  private final ObjectMapper objectMapper;
  private Map<String, FileGenerator> generators;

  public PHP_V4() {
    this.objectMapper = new ObjectMapper();
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.ofEntries(
        Map.entry(RESOURCES, "/templates/php/v4/resource.php.hbs"),
        Map.entry(ENUMS, "/templates/php/v4/enums.php.hbs"),
        Map.entry(ACTION, "/templates/php/v4/action.php.hbs"),
        Map.entry(RESPONSE, "/templates/php/v4/response.php.hbs"),
        Map.entry(LIST_RESPONSE, "/templates/php/v4/listResponse.php.hbs"),
        Map.entry(CHARGEBEE_CLIENT, "/templates/php/v4/chargebeeClient.php.hbs"),
        Map.entry(LIST_RESPONSE_OBJECT, "/templates/php/v4/listResponseObject.php.hbs"),
        Map.entry(ACTION_CONTRACT, "/templates/php/v4/actionContract.php.hbs"),
        Map.entry(EXCEPTION, "/templates/php/v4/exception.php.hbs"),
        Map.entry(
            "telemetryAttributeKeys",
            "/templates/php/telemetry/TelemetryAttributeKeys.php.hbs"),
        Map.entry(
            "telemetryRequestContext",
            "/templates/php/telemetry/RequestTelemetryContext.php.hbs"),
        Map.entry(
            "telemetryRequestError", "/templates/php/telemetry/RequestTelemetryError.php.hbs"),
        Map.entry(
            "telemetryRequestResult",
            "/templates/php/telemetry/RequestTelemetryResult.php.hbs"),
        Map.entry("telemetryAdapter", "/templates/php/telemetry/TelemetryAdapter.php.hbs"),
        Map.entry("telemetrySupport", "/templates/php/telemetry/TelemetrySupport.php.hbs"));
  }

  private Map<String, FileGenerator> initializeGenerators() {
    return Map.of(
        "resource",
        new ResourceFileGenerator(this),
        "action",
        new ActionFileGenerator(this),
        "response",
        new ResponseFileGenerator(this),
        "enum",
        new EnumFileGenerator(this),
        "client",
        new ClientFileGenerator(this),
        "actionContract",
        new ActionContractFileGenerator(this));
  }

  @Override
  protected List<FileOp> generateSDK(String outputPath, Spec spec) throws IOException {
    this.generators = initializeGenerators();
    List<FileOp> fileOps = new ArrayList<>();
    fileOps.addAll(createBaseDirectories(outputPath));
    List<com.chargebee.openapi.Resource> filteredResources = filterResources(spec.resources());
    List<Error> errors = spec.errorResources();
    fileOps.addAll(
        generators.get("resource").generate(outputPath + "/Resources", filteredResources));
    fileOps.addAll(generators.get("action").generate(outputPath + "/Actions", filteredResources));
    fileOps.addAll(
        generators.get("response").generate(outputPath + "/Responses", filteredResources));
    List<com.chargebee.openapi.Enum> globalEnums = new ArrayList<>(spec.globalEnums());
    for (var res : filteredResources) {
      for (var attribute : res.getSortedResourceAttributes()) {
        addGlobalEnumIfMissing(attribute, globalEnums);
        if (attribute.isSubResource()) {
          for (var subAttribute : attribute.attributes()) {
            addGlobalEnumIfMissing(subAttribute, globalEnums);
          }
        }
      }
    }
    fileOps.addAll(generators.get("enum").generate(outputPath + "/Enums", globalEnums));
    fileOps.add(generators.get("client").generateSingle(outputPath, filteredResources));
    fileOps.addAll(
        generators
            .get("actionContract")
            .generate(outputPath + "/Actions/Contracts", filteredResources));
    fileOps.addAll(generateExceptionFiles(outputPath + "/Exceptions", errors));
    fileOps.addAll(generateTelemetryFiles(outputPath));
    return fileOps;
  }

  private List<FileOp> generateTelemetryFiles(String outputPath) throws IOException {
    final String telemetryDir = outputPath + "/Telemetry";
    final String[] telemetryFiles = {
      "TelemetryAttributeKeys.php",
      "RequestTelemetryContext.php",
      "RequestTelemetryError.php",
      "RequestTelemetryResult.php",
      "TelemetryAdapter.php",
      "TelemetrySupport.php"
    };
    final String[] templateKeys = {
      "telemetryAttributeKeys",
      "telemetryRequestContext",
      "telemetryRequestError",
      "telemetryRequestResult",
      "telemetryAdapter",
      "telemetrySupport"
    };

    List<FileOp> fileOps = new ArrayList<>();
    fileOps.add(new FileOp.CreateDirectory(telemetryDir, ""));

    for (int i = 0; i < telemetryFiles.length; i++) {
      Template template = getTemplateContent(templateKeys[i]);
      fileOps.add(new FileOp.WriteString(telemetryDir, telemetryFiles[i], template.apply("")));
    }

    return fileOps;
  }

  private List<FileOp> generateExceptionFiles(String resourcesDirectoryPath, List<Error> errors)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();
    Template resourceTemplate = getTemplateContent("exception");
    for (var error : errors) {
      var content = error.templateParams(this);
      fileOps.add(
          new FileOp.WriteString(
              resourcesDirectoryPath,
              error.name + "Exception.php",
              resourceTemplate.apply(content)));
    }
    return fileOps;
  }

  private List<FileOp> createBaseDirectories(String basePath) {
    return Arrays.asList(
        new FileOp.CreateDirectory(basePath, "/Resources"),
        new FileOp.CreateDirectory(basePath, "/Enums"),
        new FileOp.CreateDirectory(basePath, "/Actions"),
        new FileOp.CreateDirectory(basePath, "/Responses"),
        new FileOp.CreateDirectory(basePath, "/Actions/Contracts"),
        new FileOp.CreateDirectory(basePath, "/Telemetry"));
  }

  private List<com.chargebee.openapi.Resource> filterResources(
      List<com.chargebee.openapi.Resource> resources) {
    return resources.stream()
        .filter(resource -> !List.of(this.hiddenOverride).contains(resource.id))
        .collect(Collectors.toList());
  }

  private void addGlobalEnumIfMissing(
      Attribute attribute, List<com.chargebee.openapi.Enum> globalEnums) {
    if (attribute.isGlobalEnumAttribute()
        && attribute.isGenSeparate()
        && attribute.getEnum() != null) {
      String enumName =
          attribute.getEnumApiName() != null
              ? attribute.getEnumApiName()
              : com.google.common.base.CaseFormat.LOWER_UNDERSCORE.to(
                  com.google.common.base.CaseFormat.UPPER_CAMEL, attribute.name);
      if (globalEnums.stream().noneMatch(e -> e.name != null && e.name.equals(enumName))) {
        globalEnums.add(new com.chargebee.openapi.Enum(enumName, attribute.schema));
      }
    }
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
