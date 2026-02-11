package com.chargebee.sdk.php.v4.generators;

import static com.chargebee.GenUtil.*;
import static com.chargebee.sdk.php.v4.Common.toPascalCase;
import static com.chargebee.sdk.php.v4.Constants.*;

import com.chargebee.openapi.Resource;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.common.ResourceAssist;
import com.chargebee.sdk.php.v4.Constants;
import com.chargebee.sdk.php.v4.PHP_V4;
import com.chargebee.sdk.php.v4.ResourceParser;
import com.chargebee.sdk.php.v4.SubResourceParser;
import com.chargebee.sdk.php.v4.models.Column;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResourceFileGenerator implements FileGenerator {
  private final PHP_V4 phpGenerator;
  private final Template resourceTemplate;
  private final Template enumTemplate;

  public ResourceFileGenerator(PHP_V4 phpGenerator) {
    this.phpGenerator = phpGenerator;
    this.resourceTemplate = phpGenerator.getTemplateContent(Constants.RESOURCES);
    this.enumTemplate = phpGenerator.getTemplateContent(ENUMS);
  }

  @Override
  public List<FileOp> generate(String outputPath, List<?> items) throws IOException {
    List<Resource> resources = (List<Resource>) items;
    List<FileOp> fileOps = new ArrayList<>();

    for (Resource resource : resources) {
      fileOps.addAll(generateResourceFiles(outputPath, resource, resources));
    }

    fileOps.addAll(generateContentFile(outputPath, resources));
    return fileOps;
  }

  private List<FileOp> generateResourceFiles(
      String outputPath, Resource resource, List<Resource> resourceList) throws IOException {
    List<FileOp> fileOps = new ArrayList<>();
    fileOps.add(new FileOp.CreateDirectory(outputPath, resource.name));
    var resourceModel = createResourceModel(resource);
    fileOps.add(writeResourceFile(outputPath, resource.name, resourceModel));
    for (var subResource : SubResourceParser.listSubResources(resource)) {
      subResource.setNamespace(resourceModel.getNamespace());
      fileOps.add(writeResourceFile(outputPath, resource.name, subResource));
    }
    List<com.chargebee.openapi.Enum> resourceEnums =
        new ResourceAssist().setResource(resource).enums();
    if (!resourceEnums.isEmpty()) {
      fileOps.addAll(generateEnumFiles(outputPath, resource.name, resourceEnums));
    }

    return fileOps;
  }

  private com.chargebee.sdk.php.v4.models.Resource createResourceModel(Resource resource) {
    var model = new com.chargebee.sdk.php.v4.models.Resource();
    model.setCols(ResourceParser.getCols(resource));
    model.setGlobalEnumCols(ResourceParser.generateGlobalEnumColumn(resource));
    model.setLocalEnumCols(ResourceParser.generateLocalEnumCloumn(resource));
    model.setListOfEnumCols(ResourceParser.generateListOfEnumColumns(resource));
    model.setClazName(resource.name);
    model.setNamespace(RESOURCE_NAMESPACE + BACK_SLASH + resource.name);
    model.setCustomFieldSupported(resource.isCustomFieldSupported());
    List<String> knownFields =
        Stream.of(
                model.getCols(),
                model.getGlobalEnumCols(),
                model.getLocalEnumCols(),
                model.getListOfEnumCols())
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .map(Column::getApiName)
            .collect(Collectors.toList());

    model.setKnownFields(knownFields);
    return model;
  }

  private FileOp writeResourceFile(
      String basePath, String resourceName, com.chargebee.sdk.php.v4.models.Resource resource)
      throws IOException {
    String content =
        resourceTemplate.apply(phpGenerator.getObjectMapper().convertValue(resource, Map.class));
    return new FileOp.WriteString(
        basePath + FORWARD_SLASH + resourceName,
        resource.getClazName() + PHP_FILE_NAME_EXTENSION,
        content);
  }

  private List<FileOp> generateContentFile(String outputPath, List<Resource> resources)
      throws IOException {
    var contentResource = new com.chargebee.sdk.php.v4.models.Resource();
    contentResource.setClazName(CONTENT);
    contentResource.setNamespace(RESOURCE_NAMESPACE + BACK_SLASH + CONTENT);

    List<Column> columns =
        resources.stream().map(this::createContentColumn).collect(Collectors.toList());

    contentResource.setCols(columns);
    contentResource.setImports(generateImports(columns));

    return List.of(
        new FileOp.CreateDirectory(outputPath, CONTENT),
        writeResourceFile(outputPath, CONTENT, contentResource));
  }

  private Column createContentColumn(Resource resource) {
    Column column = new Column();
    column.setName(toPascalCase(resource.name));
    column.setFieldTypePHP(toCamelCase(resource.name));
    column.setPhpDocField(toCamelCase(resource.name));
    column.setSubResourceName(toCamelCase(resource.name));
    column.setIsOptional(true);
    column.setApiName(resource.id);
    column.setSubResources(true);
    return column;
  }

  private List<String> generateImports(List<Column> columns) {
    return columns.stream()
        .map(
            col ->
                String.format(
                    USE
                        + " "
                        + CHARGEBEE
                        + BACK_SLASH
                        + "Resources"
                        + BACK_SLASH
                        + "%s"
                        + BACK_SLASH
                        + "%s",
                    col.getFieldTypePHP(),
                    col.getFieldTypePHP()))
        .collect(Collectors.toList());
  }

  private List<FileOp> generateEnumFiles(
      String outputPath, String resourceName, List<com.chargebee.openapi.Enum> enums)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();
    String enumDirPath = outputPath + FORWARD_SLASH + resourceName + FORWARD_SLASH + ENUMS;
    fileOps.add(
        new FileOp.CreateDirectory(outputPath + FORWARD_SLASH + resourceName, PASCAL_CASE_ENUMS));
    String namespace =
        CHARGEBEE
            + BACK_SLASH
            + "Resources"
            + BACK_SLASH
            + resourceName
            + BACK_SLASH
            + PASCAL_CASE_ENUMS;
    List<Map<String, Object>> enumMaps =
        enums.stream()
            .map(e -> createEnumMap(e, namespace))
            .filter(map -> !map.isEmpty())
            .collect(Collectors.toList());
    for (Map<String, Object> enumMap : enumMaps) {
      String content = enumTemplate.apply(enumMap);
      String fileName = firstCharUpper(toCamelCase(enumMap.get(NAME).toString()));
      fileOps.add(new FileOp.WriteString(enumDirPath, fileName + DOT_PHP, content));
    }
    return fileOps;
  }

  private Map<String, Object> createEnumMap(com.chargebee.openapi.Enum enumObj, String namespace) {
    return Map.of(
        "name", firstCharUpper(toCamelCase(enumObj.name)),
        "possibleValues",
            enumObj.validValues().stream()
                .map(value -> Map.of("name", value))
                .collect(Collectors.toList()),
        "deprecatedEnums",
            !enumObj.deprecatedValues().isEmpty()
                ? enumObj.deprecatedValues().stream()
                    .map(value -> Map.of("name", value))
                    .collect(Collectors.toList())
                : new ArrayList<>(),
        "IsParamBlankOption", enumObj.isParamBlankOption(),
        "namespace", namespace);
  }
}
