package com.chargebee.sdk.ts.typing.V3;

import static com.chargebee.openapi.Resource.*;
import static com.chargebee.sdk.common.AttributeAssistant.isHiddenFromSDK;
import static com.chargebee.sdk.ts.typing.V3.AttributeParser.getAttributesInMultiLine;
import static com.chargebee.sdk.ts.typing.V3.RequestInterfaceParser.getOperRequestInterfaces;

import com.chargebee.GenUtil;
import com.chargebee.QAModeHandler;
import com.chargebee.openapi.*;
import com.chargebee.openapi.Enum;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.parameter.Response;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TypeScriptTypings extends Language {
  protected final String[] hiddenOverride = {"media"};
  Resource activeResource;

  boolean forQa = false;
  boolean forEap = false;

  @Override
  protected List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    QAModeHandler.getInstance().setValue(false);
    String resourcesDirectoryPath = "/resources";
    var createResourcesDirectory =
        new FileOp.CreateDirectory(outputDirectoryPath, resourcesDirectoryPath);
    List<FileOp> fileOps = new ArrayList<>(List.of(createResourcesDirectory));

    var resources =
        spec.resources().stream()
            .filter(resource -> !Arrays.stream(this.hiddenOverride).toList().contains(resource.id))
            .toList();
    List<FileOp> generateResourceTypings =
        generateResourceTypings(outputDirectoryPath + resourcesDirectoryPath, resources);
    fileOps.addAll(generateResourceTypings);

    fileOps.add(generateCoreFile(outputDirectoryPath, spec));
    fileOps.add(generateIndexFile(outputDirectoryPath, resources));
    fileOps.add(generateFilterFile(outputDirectoryPath + resourcesDirectoryPath));
    return fileOps;
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of(
        "resource", "/templates/ts/typings/v3/resource.d.ts.hbs",
        "index", "/templates/ts/typings/v3/index.d.ts.hbs",
        "core", "/templates/ts/typings/v3/core.d.ts.hbs",
        "filter", "/templates/ts/typings/v3/filter.d.ts.hbs");
  }

  private FileOp generateIndexFile(String outputDirectoryPath, List<Resource> resources)
      throws IOException {
    List<Map<String, Object>> resourcesMap =
        resources.stream().map(Resource::templateParams).toList();
    Map templateParams = Map.of("resources", resourcesMap);
    Template indexTemplate = getTemplateContent("index");
    return new FileOp.WriteString(
        outputDirectoryPath, "index.d.ts", indexTemplate.apply(templateParams));
  }

  private FileOp generateFilterFile(String resourcesDirectoryPath) throws IOException {
    Template indexTemplate = getTemplateContent("filter");
    return new FileOp.WriteString(resourcesDirectoryPath, "filter.d.ts", indexTemplate.apply(""));
  }

  private FileOp generateCoreFile(String outputDirectoryPath, Spec spec) throws IOException {
    List<Enum> enums = spec.globalEnums();
    Template coreTemplate = getTemplateContent("core");
    return new FileOp.WriteString(
        outputDirectoryPath,
        "core.d.ts",
        coreTemplate.apply(
            Map.of("enums", enums.stream().map(e -> e.templateParams(this)).toList())));
  }

  private List<FileOp> generateResourceTypings(
      String resourcesDirectoryPath, List<Resource> resources) throws IOException {
    List<FileOp> fileOps = new ArrayList<>();
    Template resourceTemplate = getTemplateContent("resource");
    for (var resource : resources) {
      activeResource = resource;

      var content = resourceTemplate.apply(resource.templateParams(this));
      fileOps.add(new FileOp.WriteString(resourcesDirectoryPath, resource.name + ".d.ts", content));
    }
    return fileOps;
  }

  @Override
  public String dataType(Schema schema) {
    if (schema instanceof StringSchema
        || schema instanceof EmailSchema
        || schema instanceof PasswordSchema) {
      if (schema.getEnum() != null) {
        Enum anEnum = new Enum(schema);
        var enumValues =
            String.join(
                " | ", anEnum.validValues().stream().map(s -> String.format("'%s'", s)).toList());
        return anEnum.globalEnumReference().map(value -> value + "Enum").orElse(enumValues);
      }
      return "string";
    }
    if (schema instanceof NumberSchema || schema instanceof IntegerSchema) {
      return "number";
    }
    if (schema instanceof BooleanSchema) {
      return "boolean";
    }
    if (schema instanceof ObjectSchema && GenUtil.hasAdditionalProperties(schema)) {
      return "object";
    }
    if (schema instanceof ObjectSchema
        && isSubResourceSchema(schema)
        && !(parentResourceName(schema) == null && subResourceName(schema) == null)) {
      if (isGlobalResourceReference(schema)) {
        return subResourceName(schema);
      }
      return String.format("%s.%s", parentResourceName(schema), subResourceName(schema));
    }
    if (isReferenceSchema(schema)) {
      return referredResourceName(schema);
    }
    if (schema instanceof ArraySchema && schema.getItems() != null) {
      if (schema.getItems().getType() == null) {
        return "any[]";
      }
      if (schema.getItems() instanceof ObjectSchema && isSubResourceSchema(schema.getItems())) {
        if (isGlobalResourceReference(schema.getItems())) {
          return String.format("%s[]", subResourceName(schema.getItems()));
        }
        return String.format(
            "%s.%s[]", parentResourceName(schema.getItems()), subResourceName(schema.getItems()));
      }
      return String.format("%s[]", dataType(schema.getItems()));
    }

    if (schema instanceof ObjectSchema) {
      Map<String, Schema<?>> properties = schema.getProperties();
      boolean isCompositeArrayRequestBody = isCompositeArrayRequestBody(schema);
      if (isCompositeArrayRequestBody) {
        properties
            .keySet()
            .forEach(
                key -> {
                  if (properties.get(key) instanceof ArraySchema) {
                    properties.put(key, properties.get(key).getItems());
                  }
                });
      }
      var requiredProps =
          new HashSet<>(schema.getRequired() == null ? List.of() : schema.getRequired());
      String objectDefinition =
          properties.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .filter(es -> !isHiddenFromSDK(es.getValue(), false))
              .map(
                  es ->
                      String.format(
                          "%s%s:%s",
                          es.getKey(),
                          requiredProps.contains(es.getKey()) ? "" : "?",
                          dataType(es.getValue())))
              .collect(Collectors.joining(","));
      return String.format("{%s}%s", objectDefinition, isCompositeArrayRequestBody ? "[]" : "");
    }
    return "any";
  }

  @Override
  public String listDataType(List<Response> responseParameters) {
    String type =
        responseParameters.stream()
            .map(
                rp ->
                    String.format(
                        "%s%s:%s", rp.name, rp.isRequired ? "" : "?", dataType(rp.schema)))
            .collect(Collectors.joining(","));
    return String.format("{%s}[]", type);
  }

  @Override
  public Map<String, Object> additionalTemplateParams(Resource res) {
    com.chargebee.sdk.ts.typing.V3.models.Resource resource;
    resource = new com.chargebee.sdk.ts.typing.V3.models.Resource();
    resource.setOperRequestInterfaces(getOperRequestInterfaces(res, activeResource));
    resource.setAttributesInMultiLine(getAttributesInMultiLine(res, activeResource));
    ObjectMapper oMapper = new ObjectMapper();
    return oMapper.convertValue(resource, Map.class);
  }
}
