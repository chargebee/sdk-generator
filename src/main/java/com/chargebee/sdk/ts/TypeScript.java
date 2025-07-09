package com.chargebee.sdk.ts;

import static com.chargebee.GenUtil.*;
import static com.chargebee.openapi.Resource.*;
import static com.chargebee.sdk.common.Constant.DEBUG_RESOURCE;
import static com.chargebee.sdk.common.Constant.SDK_DEBUG;

import com.chargebee.openapi.*;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.openapi.parameter.Parameter;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.common.ActionAssist;
import com.chargebee.sdk.common.ResourceAssist;
import com.chargebee.sdk.ts.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.*;

public class TypeScript extends Language {
  protected final String[] hiddenOverride = {"media"};
  Resource activeResource;
  boolean forQa = false;
  boolean forEap = false;

  @Override
  protected List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    String resourcesDirectoryPath = "/resources";
    var createResourcesDirectory =
        new FileOp.CreateDirectory(outputDirectoryPath, resourcesDirectoryPath);
    List<FileOp> fileOps = new ArrayList<>(List.of(createResourcesDirectory));

    var resources =
        spec.resources().stream()
            .filter(resource -> !Arrays.stream(this.hiddenOverride).toList().contains(resource.id))
            .toList();
    List<FileOp> generateResourceFiles =
        generateResourceFiles(outputDirectoryPath + resourcesDirectoryPath, resources);
    fileOps.addAll(generateResourceFiles);
    fileOps.add(generateIndexFile(outputDirectoryPath + resourcesDirectoryPath, resources));
    fileOps.add(generateChargebeeFile(outputDirectoryPath, resources));
    fileOps.add(generateResultFile(outputDirectoryPath, resources));
    return fileOps;
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of(
        Constants.RESOURCES,
        "/templates/ts/resources.ts.hbs",
        "index",
        "/templates/ts/resources.index.ts.hbs",
        "chargebee",
        "/templates/ts/chargebee.ts.hbs",
        "result",
        "/templates/ts/result.ts.hbs");
  }

  private List<FileOp> generateResourceFiles(
      String resourcesDirectoryPath, List<Resource> resources) throws IOException {
    List<FileOp> fileOps = new ArrayList<>();
    Template resourceTemplate = getTemplateContent(Constants.RESOURCES);
    for (var res : resources) {
      if (SDK_DEBUG && !DEBUG_RESOURCE.contains(res.name)) continue;

      activeResource = res;
      com.chargebee.sdk.ts.models.Resource resource;
      resource = new com.chargebee.sdk.ts.models.Resource();
      resource.setClazName(res.name);
      resource.setAttributesInMultiLine(getAttributesInMultiLine(res));
      resource.setOperations(getOperationList(res));
      resource.setSnippet(getSnippet(res.name));
      resource.setSubResources(getSubResources());
      resource.setSingularName(singularName(res));
      resource.setOperRequestInterfaces(getOperRequestInterfaces(res));
      resource.setListAPI(res.hasListOperations() && !resource.getOperations().isEmpty());
      ObjectMapper oMapper = new ObjectMapper();
      var content = resourceTemplate.apply(oMapper.convertValue(resource, Map.class));
      String fileName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, res.name);
      fileOps.add(new FileOp.WriteString(resourcesDirectoryPath, fileName + ".ts", content));
    }
    return fileOps;
  }

  private FileOp generateIndexFile(String outputDirectoryPath, List<Resource> resources)
      throws IOException {
    List<Map<String, Object>> resourceMap =
        resources.stream()
            .sorted(Comparator.comparing(Resource::sortOrder))
            .map(r -> r.templateParams(this))
            .toList();
    Map<String, List<Map<String, Object>>> templateParams =
        Map.of(Constants.RESOURCES, resourceMap);
    Template indexTemplate = getTemplateContent("index");
    return new FileOp.WriteString(
        outputDirectoryPath, "index.ts", indexTemplate.apply(templateParams));
  }

  private FileOp generateChargebeeFile(String outputDirectoryPath, List<Resource> resources)
      throws IOException {
    List<Map<String, Object>> resourceMap =
        resources.stream()
            .filter(Resource::isNotDependentResource)
            .sorted(Comparator.comparing(Resource::sortOrder))
            .map(r -> r.templateParams(this))
            .toList();
    Map<String, List<Map<String, Object>>> templateParams =
        Map.of(Constants.RESOURCES, resourceMap);
    Template chargebeeTemplate = getTemplateContent("chargebee");
    return new FileOp.WriteString(
        outputDirectoryPath, "chargebee.ts", chargebeeTemplate.apply(templateParams));
  }

  private FileOp generateResultFile(String outputDirectoryPath, List<Resource> resources)
      throws IOException {
    Map<String, Object> templateParams = resourceResponses(resources);
    Template resultTemplate = getTemplateContent("result");
    return new FileOp.WriteString(
        outputDirectoryPath, "result.ts", resultTemplate.apply(templateParams));
  }

  public List<String> getAttributesInMultiLine(Resource res) {
    List<String> attributesInMultiLine = new ArrayList<>();
    for (Attribute attribute : res.getSortedResourceAttributes()) {
      if (attribute.isContentObjectAttribute()) continue;
      attributesInMultiLine.add(
          "public "
              + attribute.name
              + (attribute.isRequired ? "" : "?")
              + ": "
              + dataTypeForMultiLineAttributes(attribute)
              + ";");
    }
    return attributesInMultiLine;
  }

  private String getSnippet(String name) throws IOException {
    if (name.equals("TimeMachine")) {
      return FileOp.fetchFileContent("src/main/resources/templates/ts/timeMachine.ts.hbs");
    } else if (name.equals("Event")) {
      return FileOp.fetchFileContent("src/main/resources/templates/ts/event.ts.hbs");
    } else if (name.equals("Export")) {
      return FileOp.fetchFileContent("src/main/resources/templates/ts/export.ts.hbs");
    } else if (name.equals("Session")) {
      return FileOp.fetchFileContent("src/main/resources/templates/ts/session.ts.hbs");
    }
    return null;
  }

  private String getClazName(Attribute attribute) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, singularize(attribute.name));
  }

  private String getClazName(Action action) {
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, action.name)
        + Constants.UNDERSCORE_PARAMS;
  }

  private String getClazName(Action action, Resource res) {
    if (action.isListResourceAction()) {
      return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, res.name + "_" + action.name)
          + Constants.UNDERSCORE_PARAMS;
    }
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, action.name)
        + Constants.UNDERSCORE_PARAMS;
  }

  private List<Operation> getOperationList(Resource res) {
    List<Operation> operations = new ArrayList<>();
    for (Action action : res.getSortedAction()) {
      Operation operation = new Operation();
      operation.setHasHandle(getHasHandle(action));
      operation.setHasInterface(getHasInterface(action));
      operation.setList(action.isListResourceAction());
      operation.setMethName(getMethName(action));
      operation.setEntity(singularize(action.getUrlPrefix()));
      operation.setSingularName(singularName(res));
      operation.setGetClazName(getClazName(action, res));
      operation.setHttpMethName(String.valueOf(action.httpRequestType));
      operation.setResourceIdentifier1(getResourceIdentifier1(action));
      operation.setResourceIdentifier2(getResourceIdentifier2(action));
      operation.setSubDomain(action.subDomain());
      operation.setJsonKeys(action.getJsonKeysInRequestBody());
      operation.setOperationNeedsJsonInput(action.isOperationNeedsJsonInput());
      operations.add(operation);
    }
    return operations;
  }

  public String getResourceIdentifier1(Action action) {
    return "'/" + action.getUrlPrefix() + "'";
  }

  public String getResourceIdentifier2(Action action) {
    return !action.getUrlSuffix().isEmpty() ? "'/" + action.getUrlSuffix() + "'" : "null";
  }

  private String getMethName(Action action) {
    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, action.name);
  }

  private boolean getHasHandle(Action action) {
    return !action.pathParameters().isEmpty();
  }

  private boolean getHasInterface(Action action) {
    return action.isInputObjNeeded()
        && (!action.hasInputParamsForClientLibs() || action.hasInputParamsForEAPClientLibs());
  }

  private String singularName(Resource res) {
    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, singularize(res.name));
  }

  private List<SubResource> getSubResources() {
    ResourceAssist resourceAssist = new ResourceAssist().setResource(activeResource);
    List<SubResource> subResources = new ArrayList<>();
    for (Attribute attribute : resourceAssist.subResource()) {
      if (attribute.isSubResource()
          && !attribute.isDependentAttribute()
          && !Resource.isGlobalResourceReference(attribute.schema)) {
        SubResource subResource = new SubResource();
        subResource.setClazName(
            attribute.schema instanceof ArraySchema
                ? getClazName(attribute)
                : attribute.subResourceName());
        subResource.setCols(getSubResourceCols(attribute));
        subResources.add(subResource);
      }
    }
    return subResources;
  }

  public List<Column> getSubResourceCols(Attribute subResourceAttribute) {
    List<Column> cols = new ArrayList<>();
    for (Attribute attribute : subResourceAttribute.attributes()) {
      if (!attribute.isNotHiddenAttribute()) continue;
      Column column = new Column();
      if (attribute.isRequired) {
        column.setName(attribute.name + ": ");
      } else column.setName(attribute.name + "?: ");
      column.setFieldTypeTypescript(dataTypeForMultiLineAttributes(attribute));
      cols.add(column);
    }
    return cols;
  }

  private List<OperationRequestInterface> getOperRequestInterfaces(Resource res) {
    List<OperationRequestInterface> operationRequestInterfaces = new ArrayList<>();
    for (Action action : res.getSortedAction()) {
      OperationRequestInterface operationRequestInterface = new OperationRequestInterface();
      operationRequestInterface.setClazName(getClazName(action, res));
      operationRequestInterface.setParams(getOperationRequestParameter(action));
      operationRequestInterface.setHasSortParam(getHasSortParam(action));
      operationRequestInterface.setSubParamsForOperation(getSubParamsForOperation(action));
      operationRequestInterface.setSingularSubs(getSingularSubAttribute(action));
      operationRequestInterface.setHasSingularSubs(
          !operationRequestInterface.getSingularSubs().isEmpty());
      operationRequestInterface.setHasMultiSubs(!getMultiSubs(action).isEmpty());
      operationRequestInterface.setMultiSubs(getMultiSubsParameter(action));
      if (operationRequestInterface.getParams().isEmpty()
          && operationRequestInterface.getSingularSubs().isEmpty()
          && operationRequestInterface.getMultiSubs().isEmpty()) {
        continue;
      }
      operationRequestInterfaces.add(operationRequestInterface);
    }
    return operationRequestInterfaces;
  }

  public boolean getHasSortParam(Action action) {
    for (Parameter queryParameter : action.queryParameters()) {
      Attribute attribute =
          new Attribute(queryParameter.getName(), queryParameter.schema, queryParameter.isRequired);
      if (attribute.getSchema() != null
          && attribute.schema.getProperties() != null
          && attribute.name.equals("sort_by")) return true;
    }
    for (Parameter requestBodyParameter : action.requestBodyParameters()) {
      Attribute attribute =
          new Attribute(
              requestBodyParameter.getName(),
              requestBodyParameter.schema,
              requestBodyParameter.isRequired);
      if (attribute.getSchema() != null
          && attribute.schema.getProperties() != null
          && attribute.name.equals("sort_by")) return true;
    }
    return false;
  }

  public List<String> getSubParamsForOperation(Action action) {
    List<String> toRet = new ArrayList<>();
    for (Attribute param : getSingularSubs(action)) {
      String singularSub = param.name + "?: " + param.name + "_" + getClazName(action) + ";";

      if (!toRet.contains(singularSub)) {
        toRet.add(singularSub);
      }
    }

    for (Attribute param : getMultiSubs(action)) {
      String multiSub = param.name + "?: Array<" + param.name + "_" + getClazName(action) + ">;";

      if (!toRet.contains(multiSub)) {
        toRet.add(multiSub);
      }
    }
    return toRet;
  }

  public List<Attribute> getSingularSubs(Action action) {
    List<Attribute> attributes = new ArrayList<>();
    List<Parameter> actionParameters = new ArrayList<>();
    actionParameters.addAll(action.queryParameters());
    actionParameters.addAll(action.requestBodyParameters());
    for (Parameter iparam : actionParameters) {
      Attribute attribute = new Attribute(iparam.getName(), iparam.schema, iparam.isRequired);
      boolean isCodeGen =
          forQa
              ? (attribute.isSubResource() && !attribute.isMultiAttribute())
              : (attribute.isSubResource()
                  && !attribute.isMultiAttribute()
                  && (attribute.isNotHiddenAttribute()));
      if (isCodeGen) attributes.add(attribute);
    }
    return attributes;
  }

  public List<Attribute> getMultiSubs(Action action) {
    List<Attribute> attributes = new ArrayList<>();
    List<Parameter> actionParameters = new ArrayList<>();
    actionParameters.addAll(action.queryParameters());
    actionParameters.addAll(action.requestBodyParameters());
    for (Parameter iparam : actionParameters) {
      Attribute attribute = new Attribute(iparam.getName(), iparam.schema, iparam.isRequired);
      boolean isCodeGen =
          forQa
              ? (attribute.isSubResource() && attribute.isMultiAttribute())
              : (forEap
                  ? (attribute.isSubResource()
                      && attribute.isMultiAttribute()
                      && (attribute.isNotHiddenAttribute()))
                  : (iparam.isCompositeArrayBody() && attribute.isNotHiddenAttribute()));
      if (isCodeGen) attributes.add(attribute);
    }
    return attributes;
  }

  public boolean getSubFilterParam(Attribute attribute) {
    return attribute.isFilterAttribute() && attribute.isSubResource();
  }

  private List<OperationRequestParameter> getOperationRequestParameter(Action action) {
    ActionAssist actionAssist =
        new ActionAssist().setAction(action).includeSortBy().includePagination();
    List<OperationRequestParameter> operationRequestParameters = new ArrayList<>();
    for (Attribute attribute : actionAssist.getAllAttribute()) {
      if (attribute.isFilterAttribute() && !attribute.isSubResource()) {
        handleOperationListAttribute(action, attribute, operationRequestParameters);
      } else {
        if (!attribute.attributes().isEmpty() && !attribute.isMultiAttribute()) continue;
        OperationRequestParameter operationRequestParameter = new OperationRequestParameter();
        operationRequestParameter.setHidden(!attribute.isNotHiddenAttribute());
        operationRequestParameter.setDeprecated(attribute.isDeprecated());
        operationRequestParameter.setReturnGeneric(dataTypePrimitiveParameters(attribute));
        operationRequestParameter.setName(attribute.name);
        operationRequestParameter.setTypescriptPutMethName(getTypescriptPutMethName(attribute));
        operationRequestParameter.setSubFilterParam(getSubFilterParam(attribute));
        operationRequestParameter.setSortParam(getHasSortParam(action));
        operationRequestParameters.add(operationRequestParameter);
      }
    }
    return operationRequestParameters;
  }

  public List<SingularSubResource> getSingularSubAttribute(Action action) {
    List<SingularSubResource> subResources = new ArrayList<>();
    ActionAssist actionAssist = new ActionAssist().setAction(action);
    for (Attribute attribute : actionAssist.singularSubAttributes()) {
      attribute
          .attributes()
          .forEach(
              value -> {
                SingularSubResource subResource = new SingularSubResource();
                if (action.isListResourceAction()) {
                  subResource.setResName(attribute.name + "_" + toUnderScores(activeResource.name));
                } else {
                  subResource.setResName(attribute.name);
                }
                subResource.setClazName(getClazName(action));
                subResource.setHidden(value.isHiddenParameter());
                subResource.setDeprecated(value.isDeprecated());
                subResource.setName(value.name);
                subResource.setTypescriptPutMethName(getTypescriptPutMethName(value));
                subResource.setReturnGeneric(dataTypeForSingularSubResources(value));
                subResources.add(subResource);
              });
    }
    return subResources.stream().toList();
  }

  public List<SingularSubResource> getMultiSubsParameter(Action action) {
    List<SingularSubResource> subResources = new ArrayList<>();
    ActionAssist actionAssist = new ActionAssist().setAction(action);
    for (Attribute attribute : actionAssist.multiSubAttributes()) {
      attribute
          .attributes()
          .forEach(
              subAttribute -> {
                SingularSubResource subResource = new SingularSubResource();
                subResource.setDeprecated(subAttribute.isDeprecated());
                subResource.setResName(attribute.name);
                subResource.setClazName(getClazName(action));
                subResource.setHidden(subAttribute.isHiddenParameter());
                subResource.setDeprecated(subAttribute.isDeprecated());
                subResource.setName(subAttribute.name);
                subResource.setTypescriptPutMethName(getTypescriptPutMethName(subAttribute));
                subResource.setReturnGeneric(dataTypeForSubResources(subAttribute.schema));
                subResources.add(subResource);
              });
    }
    return subResources.stream().toList();
  }

  private void handleOperationListAttribute(
      Action action,
      Attribute attribute,
      List<OperationRequestParameter> operationRequestParameters) {
    OperationRequestParameter operationRequestParameter = new OperationRequestParameter();
    if (!action.isInputObjNeeded() && attribute.isPaginationProperty()) return;
    operationRequestParameter.setHidden(!attribute.isNotHiddenAttribute());
    operationRequestParameter.setDeprecated(attribute.isDeprecated());
    operationRequestParameter.setReturnGeneric(getFilterReturnGeneric(attribute, action));
    operationRequestParameter.setTypescriptPutMethName(getTypescriptPutMethName(attribute));
    operationRequestParameter.setName(attribute.name);
    operationRequestParameters.add(operationRequestParameter);
  }

  private String getTypescriptPutMethName(Attribute attribute) {
    if (attribute.isRequired) {
      return "";
    } else return "?";
  }

  public String dataTypePrimitiveParameters(Attribute attribute) {
    String dataType = dataTypeForSubResources(attribute.schema);
    if (dataType.equalsIgnoreCase("string") && attribute.schema instanceof ArraySchema) {
      return "Array<string>";
    }
    if (attribute.isMultiAttribute()) {
      return attribute.name + "_" + pluralize(attribute.name) + Constants.UNDERSCORE_PARAMS;
    }
    return dataType;
  }

  public String getFilterReturnGeneric(Attribute attribute, Action action) {
    final String attributeName = attribute.name;
    if (activeResource.subResources().stream()
        .anyMatch(a -> a.id != null && a.id.equals(attributeName))) {
      return (attributeName + "_" + getClazName(action, activeResource));
    }
    if (attribute.schema instanceof ArraySchema) {
      return String.format(Constants.ARRAY_OF_STRING, dataType(attribute.schema.getItems()));
    }
    if (!attribute.attributes().isEmpty()
        && attribute.attributes().stream().anyMatch(a -> a.schema instanceof ObjectSchema)) {
      attribute = attribute.attributes().get(0);
    }
    if (attribute.isFilterAttribute() && attribute.schema instanceof ObjectSchema) {
      if (attribute.isSubResource()) {
        return (attributeName + "_" + getClazName(action, activeResource));
      } else {
        return dataTypeForFilterAttribute(attribute);
      }
    }
    return dataType(attribute.schema);
  }

  public String dataTypeForFilterAttribute(Attribute attribute) {
    if (attribute.getFilterType().equals("DateFilter")) {
      return "filter._date";
    }
    if (attribute.getFilterType().equals("TimestampFilter")) {
      return "filter._timestamp";
    }
    if (attribute.getFilterType().equals("BooleanFilter")) {
      return "filter._boolean";
    }
    if (attribute.getFilterType().equals("NumberFilter")) {
      return "filter._number";
    }
    if (attribute.getFilterType().equals("EnumFilter")) {
      return "filter._enum";
    }
    return "filter._string";
  }

  public String dataTypeForSubResources(Schema schema) {
    if (schema instanceof ArraySchema && schema.getItems() != null) {
      return String.format("%s", dataTypeForSubResources(schema.getItems()));
    }
    if (schema instanceof ArraySchema
        && schema.getItems() != null
        && schema.getItems().getType() != null) {
      return String.format(Constants.ARRAY_OF_STRING, dataType(schema.getItems()));
    }
    return dataType(schema);
  }

  public String dataTypeForSingularSubResources(Attribute attribute) {
    if (attribute.isFilterAttribute() && attribute.getFilterType() != null) {
      return dataTypeForFilterAttribute(attribute);
    }
    return dataTypeForSubResources(attribute.schema);
  }

  public String dataTypeForMultiLineAttributes(Attribute attribute) {
    if (attribute.schema instanceof ObjectSchema && isSubResourceSchema(attribute.schema)) {
      if (isGlobalResourceReference(attribute.schema)) {
        return String.format("resources.%s", subResourceName(attribute.schema));
      }
      return String.format("%s", subResourceName(attribute.schema));
    }
    if (attribute.schema instanceof ArraySchema
        && attribute.schema.getItems() != null
        && attribute.schema.getItems().getType() != null) {
      if (attribute.schema.getItems() instanceof ObjectSchema
          && isSubResourceSchema(attribute.schema.getItems())) {
        if (isGlobalResourceReference(attribute.schema.getItems())) {
          return String.format("Array<resources.%s>", subResourceName(attribute.schema.getItems()));
        }
        return String.format(
            Constants.ARRAY_OF_STRING,
            attribute.schema instanceof ArraySchema
                ? getClazName(attribute)
                : attribute.subResourceName());
      }
      return String.format(Constants.ARRAY_OF_STRING, dataType(attribute.schema.getItems()));
    }
    return dataType(attribute.schema);
  }

  @Override
  public String dataType(Schema schema) {
    if (schema instanceof StringSchema
        || schema instanceof EmailSchema
        || schema instanceof PasswordSchema) {
      return "string";
    }
    if (schema instanceof NumberSchema || schema instanceof IntegerSchema) {
      return "number";
    }
    if (schema instanceof BooleanSchema) {
      return "boolean";
    }
    return "any";
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
