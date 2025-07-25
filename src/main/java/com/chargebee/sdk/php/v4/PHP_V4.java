package com.chargebee.sdk.php.v4;

import static com.chargebee.sdk.php.v4.Constants.*;

import com.chargebee.openapi.*;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.php.v4.generators.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    return Map.of(
        RESOURCES,
        "/templates/php/v4/resource.php.hbs",
        ENUMS,
        "/templates/php/v4/enums.php.hbs",
        ACTION,
        "/templates/php/v4/action.php.hbs",
        RESPONSE,
        "/templates/php/v4/response.php.hbs",
        LIST_RESPONSE,
        "/templates/php/v4/listResponse.php.hbs",
        CHARGEBEE_CLIENT,
        "/templates/php/v4/chargebeeClient.php.hbs",
        ACTION_CONTRACT,
        "/templates/php/v4/actionContract.php.hbs");
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

    fileOps.addAll(
        generators.get("resource").generate(outputPath + "/Resources", filteredResources));
    fileOps.addAll(generators.get("action").generate(outputPath + "/Actions", filteredResources));
    fileOps.addAll(
        generators.get("response").generate(outputPath + "/Responses", filteredResources));
    fileOps.addAll(generators.get("enum").generate(outputPath + "/Enums", spec.globalEnums()));
    fileOps.add(generators.get("client").generateSingle(outputPath, filteredResources));
    fileOps.addAll(
        generators
            .get("actionContract")
            .generate(outputPath + "/Actions/Contracts", filteredResources));
    return fileOps;
  }

  private List<FileOp> createBaseDirectories(String basePath) {
    return Arrays.asList(
        new FileOp.CreateDirectory(basePath, "/Resources"),
        new FileOp.CreateDirectory(basePath, "/Enums"),
        new FileOp.CreateDirectory(basePath, "/Actions"),
        new FileOp.CreateDirectory(basePath, "/Responses"),
        new FileOp.CreateDirectory(basePath, "/Actions/Contracts"));
  }

  private List<com.chargebee.openapi.Resource> filterResources(
      List<com.chargebee.openapi.Resource> resources) {
    return resources.stream()
        .filter(resource -> !List.of(this.hiddenOverride).contains(resource.id))
        .collect(Collectors.toList());
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
