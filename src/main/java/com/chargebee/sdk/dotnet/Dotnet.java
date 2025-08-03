package com.chargebee.sdk.dotnet;

import static com.chargebee.GenUtil.*;
import static com.chargebee.openapi.Extension.IS_MONEY_COLUMN;
import static com.chargebee.sdk.common.Constant.DEBUG_RESOURCE;
import static com.chargebee.sdk.common.Constant.SDK_DEBUG;
import static com.chargebee.sdk.dotnet.Constants.*;

import com.chargebee.GenUtil;
import com.chargebee.handlebar.Inflector;
import com.chargebee.openapi.*;
import com.chargebee.openapi.Enum;
import com.chargebee.openapi.Resource;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.common.ActionAssist;
import com.chargebee.sdk.common.AttributeAssistant;
import com.chargebee.sdk.common.GlobalEnum;
import com.chargebee.sdk.common.ResourceAssist;
import com.chargebee.sdk.dotnet.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class Dotnet extends Language {

  Resource activeResource;
  List<Resource> resourceList = new ArrayList<>();
  List<Enum> globalEnums;

  protected final String[] hiddenOverride = {"usage_file", "media", "non_subscription"};

  @Override
  public List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    globalEnums = spec.globalEnums();
    var resources =
        spec.resources().stream()
            .filter(resource -> !Arrays.stream(this.hiddenOverride).toList().contains(resource.id))
            .toList();
    resourceList = resources;
    var createModelsDirectory =
        new FileOp.CreateDirectory(outputDirectoryPath, "/" + MODELS_DIRECTORY_PATH);
    var createEnumsDirectory =
        new FileOp.CreateDirectory(
            outputDirectoryPath + "/" + MODELS_DIRECTORY_PATH, "/" + ENUMS_DIRECTORY_PATH);
    List<FileOp> fileOps = new ArrayList<>();

    fileOps.addAll(List.of(createModelsDirectory, createEnumsDirectory));
    fileOps.addAll(
        generateGlobalEnumFiles(
            outputDirectoryPath + "/" + MODELS_DIRECTORY_PATH + "/" + ENUMS_DIRECTORY_PATH,
            globalEnums));
    fileOps.addAll(
        generateResourceFiles(outputDirectoryPath + "/" + MODELS_DIRECTORY_PATH, resources));
    fileOps.add(generateResultFile(outputDirectoryPath + "/" + INTERNAL_DIRECTORY_PATH, resources));

    return fileOps;
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of(
        "enums",
        "/templates/dotnet/enums.cs.hbs",
        "models.resource",
        "/templates/dotnet/models.resources.cs.hbs",
        "resultBase",
        "/templates/dotnet/resultBase.cs.hbs");
  }

  private List<FileOp> generateGlobalEnumFiles(String outDirectoryPath, List<Enum> globalEnums)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();

    Template enumTemplate = getTemplateContent("enums");
    globalEnums = globalEnums.stream().sorted(Comparator.comparing(e -> e.name)).toList();
    for (var _enum : globalEnums) {
      var content = enumTemplate.apply(globalEnumTemplate(_enum));
      String fileName = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, _enum.name);
      fileOps.add(new FileOp.WriteString(outDirectoryPath, fileName + "Enum.cs", content));
    }
    return fileOps;
  }

  private List<FileOp> generateResourceFiles(String outDirectoryPath, List<Resource> resources)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();
    Template resourceTemplate = getTemplateContent("models.resource");
    for (var res : resources) {
      if (SDK_DEBUG && !DEBUG_RESOURCE.contains(res.name)) continue;

      activeResource = res;
      com.chargebee.sdk.dotnet.models.Resource resource;
      resource = new com.chargebee.sdk.dotnet.models.Resource();
      resource.setClazName(res.name);
      resource.setOperations(getOperationList(res));
      resource.setCols(getResourceCols(res));
      resource.setOperRequestClasses(getOperRequestClasses(res));
      resource.setHasOperReqClasses(!resource.getOperRequestClasses().isEmpty());
      resource.setSnippet(getSnippet(res.name));
      resource.setHasContent(
          new AttributeAssistant().setResource(activeResource).hasAttributeByGivenName("content"));
      resource.setCustomImport(getCustomImport(res.name));
      resource.setEnumCols(getEnumCols());
      resource.setSubResources(getSubResources());
      resource.setSchemaLessEnums(
          SchemaLessEnumParser.getSchemalessEnumForResource(resourceList, activeResource));
      ObjectMapper oMapper = new ObjectMapper();
      var content = resourceTemplate.apply(oMapper.convertValue(resource, Map.class));
      String fileName = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, res.name);
      fileOps.add(new FileOp.WriteString(outDirectoryPath, fileName + ".cs", content));
    }
    return fileOps;
  }

  private FileOp generateResultFile(String outputDirectory, List<Resource> resources)
      throws IOException {
    Map<String, Object> templateParams = resourceResponses(resources);
    Template resultTemplate = getTemplateContent("resultBase");
    return new FileOp.WriteString(
        outputDirectory, "ResultBase.cs", resultTemplate.apply(templateParams));
  }

  private String getCustomImport(String name) {
    if (name.equals("TimeMachine") || name.equals(EXPORT)) {
      return "using System.Threading.Tasks;\n" + "using System.Net;";
    }
    return null;
  }

  private String getSnippet(String name) throws IOException {
    if (name.equals("TimeMachine")) {
      return FileOp.fetchFileContent("src/main/resources/templates/dotnet/timeMachine.cs.hbs");
    } else if (name.equals(EXPORT)) {
      return FileOp.fetchFileContent("src/main/resources/templates/dotnet/export.cs.hbs");
    } else if (name.equals("HostedPage")) {
      return FileOp.fetchFileContent("src/main/resources/templates/dotnet/hostedPage.cs.hbs");
    } else if (name.equals("Session")) {
      return FileOp.fetchFileContent("src/main/resources/templates/dotnet/session.cs.hbs");
    } else if (name.equals("Event")) {
      return FileOp.fetchFileContent("src/main/resources/templates/dotnet/event.cs.hbs");
    }
    return null;
  }

  private List<SubResource> getSubResources() {
    ResourceAssist resourceAssist = new ResourceAssist().setResource(activeResource);
    List<SubResource> subResources = new ArrayList<>();
    for (Attribute attribute : resourceAssist.subResource()) {
      SubResource subResource = new SubResource();
      subResource.setClazName(
          attribute.schema instanceof ArraySchema
              ? singularize(toClazName(attribute.name))
              : attribute.subResourceName());
      subResource.setEnumCols(getSubResourceEnum(attribute));
      subResource.setCols(getSubResourceCols(attribute));
      subResource.setSchemaLessEnum(
          SchemaLessEnumParser.getSchemaLessEnumForSubResource(
              attribute.subResourceName(), activeResource));
      subResources.add(subResource);
    }
    return subResources;
  }

  private List<EnumColumn> getSubResourceEnum(Attribute attribute) {
    AttributeAssistant attributeAssistant = new AttributeAssistant().setAttribute(attribute);
    List<EnumColumn> enumColumns = new ArrayList<>();
    for (Attribute enumAttribute : attributeAssistant.subResourceEnum()) {
      EnumColumn enumColumn = new EnumColumn();
      enumColumn.setApiClassName(getName(enumAttribute.name));
      enumColumn.setVisibleEntries(getEnumEntries(enumAttribute));
      enumColumn.setDeprecated(enumAttribute.isDeprecated());
      enumColumns.add(enumColumn);
    }
    return enumColumns;
  }

  private List<EnumColumn> getEnumCols() {
    List<EnumColumn> enumColumns = new ArrayList<>();
    AttributeAssistant attributeAssistant = new AttributeAssistant().setResource(activeResource);
    for (Attribute attribute : attributeAssistant.resourceEnum()) {
      EnumColumn enumColumn = new EnumColumn();
      enumColumn.setDeprecated(attribute.isDeprecated());
      enumColumn.setVisibleEntries(getEnumEntries(attribute));
      enumColumn.setApiClassName(getName(attribute.name));
      enumColumns.add(enumColumn);
    }
    return enumColumns;
  }

  private List<VisibleEnumEntries> getEnumEntries(Attribute attribute) {
    List<VisibleEnumEntries> visibleEnumEntries = new ArrayList<>();
    Enum anEnum = new Enum(attribute.schema);
    List<String> deprecatedValues = anEnum.deprecatedValues();
    for (String validValue : anEnum.values()) {
      VisibleEnumEntries visibleEnumEntry = new VisibleEnumEntries();
      visibleEnumEntry.setDeprecated(deprecatedValues.contains(validValue));
      visibleEnumEntry.setDotNetName(getName(validValue));
      visibleEnumEntry.setApiName(validValue);
      visibleEnumEntries.add(visibleEnumEntry);
    }
    return visibleEnumEntries;
  }

  public List<SingularSubResource> getMultiSubs(Action action) {
    List<SingularSubResource> subResources = new ArrayList<>();
    ActionAssist actionAssist = ActionAssist.of(action).withFlatMultiAttribute(true);
    for (Attribute attribute : actionAssist.multiSubAttributes()) {
      attribute
          .attributes()
          .forEach(
              subAttribute -> {
                SingularSubResource subResource = new SingularSubResource();
                subResource.setDeprecated(subAttribute.isDeprecated());
                subResource.setListParam(false);
                subResource.setReturnGeneric(getReturnGeneric(subAttribute));
                subResource.setMulti(subAttribute.isMultiAttribute());
                subResource.setDotNetMethName(
                    getName(singularize(attribute.name) + "_" + subAttribute.name));
                subResource.setDotNetType(
                    dataTypeForMultiAttribute(subAttribute, attribute.name, action.modelName()));
                subResource.setVarName(
                    singularize(StringUtils.uncapitalize(singularize(getName(attribute.name))))
                        + getName(subAttribute.name));
                subResource.setDotNetPutMethName(
                    getRequired(subAttribute).equals("true") ? "Add" : "AddOpt");
                subResource.setResName(attribute.name);
                subResource.setName(subAttribute.name);
                subResource.setSortOrder(subAttribute.sortOrder());
                subResources.add(subResource);
              });
    }
    return subResources.stream()
        .sorted(Comparator.comparing(SingularSubResource::sortOrder))
        .toList();
  }

  public Map<String, List<SingularSubResource>> getMultiSubsForBatch(Action action) {
    List<SingularSubResource> subResources = new ArrayList<>();
    ActionAssist actionAssist = ActionAssist.of(action).withFlatMultiAttribute(true);
    for (Attribute attribute : actionAssist.multiSubAttributes()) {
      attribute
          .attributes()
          .forEach(
              subAttribute -> {
                SingularSubResource subResource = new SingularSubResource();
                subResource.setDeprecated(subAttribute.isDeprecated());
                subResource.setListParam(false);
                subResource.setReturnGeneric(getReturnGeneric(subAttribute));
                subResource.setMulti(subAttribute.isMultiAttribute());
                subResource.setDotNetMethName(
                    getName(singularize(attribute.name) + "_" + subAttribute.name));
                subResource.setDotNetType(
                    dataTypeForMultiAttribute(subAttribute, attribute.name, action.modelName()));
                subResource.setVarName(
                    singularize(StringUtils.uncapitalize(singularize(getName(attribute.name))))
                        + getName(subAttribute.name));
                subResource.setDotNetPutMethName(
                    getRequired(subAttribute).equals("true") ? "Add" : "AddOpt");
                subResource.setResName(attribute.name);
                subResource.setName(subAttribute.name);
                subResource.setSortOrder(subAttribute.sortOrder());
                subResources.add(subResource);
              });
    }
    return subResources.stream().collect(Collectors.groupingBy(SingularSubResource::getResName));
  }

  public List<SingularSubResource> getSingularSubs(Action action) {
    List<SingularSubResource> subResources = new ArrayList<>();
    ActionAssist actionAssist = ActionAssist.of(action).withFlatSingleAttribute(true);
    for (Attribute attribute : actionAssist.singularSubAttributes()) {
      if (attribute.isFilterAttribute()) continue;
      attribute
          .attributes()
          .forEach(
              value -> {
                SingularSubResource subResource = new SingularSubResource();
                subResource.setDeprecated(value.isDeprecated());
                subResource.setListParam(false);
                subResource.setReturnGeneric(getReturnGenericForSingularSubParams(attribute));
                subResource.setMulti(attribute.isMultiAttribute());
                subResource.setDotNetMethName(
                    getName(Inflector.capitalize(attribute.name)) + getName(value.name));
                subResource.setDotNetType(dataTypeForSingleSubAttribute(value, attribute.name));
                subResource.setVarName(
                    GenUtil.getVarName(attribute.name + Inflector.capitalize(value.name)));
                subResource.setDotNetPutMethName(getDotNetPutMethName(value.isRequired));
                subResource.setResName(attribute.name);
                subResource.setName(value.name);
                subResource.setSortOrder(value.sortOrder());
                subResources.add(subResource);
              });
    }
    return subResources.stream()
        .sorted(Comparator.comparing(SingularSubResource::sortOrder))
        .toList();
  }

  private List<OperationRequest> getOperRequestClasses(Resource res) {
    List<OperationRequest> operationRequests = new ArrayList<>();
    for (Action action : res.getSortedAction()) {
      OperationRequest operationRequest = new OperationRequest();
      operationRequest.setClazName(getClazName(action));
      operationRequest.setSuperClazName(getSuperClazName(action));
      operationRequest.setList(action.isListResourceAction());
      operationRequest.setParams(getOperationParams(action));
      operationRequest.setSingularSubs(getSingularSubs(action));
      operationRequest.setMultiSubs(getMultiSubs(action));
      operationRequest.setMultiSubsForBatch(getMultiSubsForBatch(action));
      operationRequest.setRawOperationName(GenUtil.toClazName(action.name));
      operationRequest.setJsonRequest(action.isOperationNeedsJsonInput());
      operationRequest.setHasBatch(action.isBatch());
      operationRequest.setPostOperationWithFilter(
          action.hasPostActionContainingFilterAsBodyParams());
      if (operationRequest.canHide()) continue;
      operationRequests.add(operationRequest);
    }
    return operationRequests;
  }

  private List<OperationRequestParameter> getOperationParams(Action action) {
    ActionAssist actionAssist = ActionAssist.of(action).withSortBy(true);
    if (activeResource.name.equals(EXPORT)) {
      actionAssist = actionAssist.withFlatSingleAttribute(true);
    }
    List<OperationRequestParameter> operationRequestParameters = new ArrayList<>();
    for (Attribute attribute : actionAssist.getAllAttribute()) {
      if (attribute.isFilterAttribute() || attribute.name.equals(SORT_BY)) {
        handleOperationListAttribute(action, attribute, operationRequestParameters, null);
      } else {
        if (attribute.isCompositeArrayRequestBody()) continue;
        OperationRequestParameter operationRequestParameter = new OperationRequestParameter();
        operationRequestParameter.setHidden(!attribute.isNotHiddenAttribute());
        operationRequestParameter.setDeprecated(attribute.isDeprecated());
        operationRequestParameter.setReturnGeneric(getReturnGeneric(attribute));
        operationRequestParameter.setDotNetMethName(
            Inflector.capitalize(getVarName(attribute.name)));
        operationRequestParameter.setDotNetType(parameterPrimitiveDataType(attribute));
        operationRequestParameter.setVarName(GenUtil.getVarName(attribute.name));
        operationRequestParameter.setName(attribute.name);
        operationRequestParameter.setDotNetPutMethName(getDotNetPutMethName(attribute.isRequired));
        operationRequestParameter.setSupportsPresenceFilter(
            attribute.isPresenceOperatorSupported());
        operationRequestParameter.setMulti(attribute.isMultiAttribute());
        operationRequestParameters.add(operationRequestParameter);
      }
    }
    return operationRequestParameters;
  }

  private void handleOperationListAttribute(
      Action action,
      Attribute attribute,
      List<OperationRequestParameter> operationRequestParameters,
      String parentName) {
    OperationRequestParameter operationRequestParameter = new OperationRequestParameter();
    operationRequestParameter.setHidden(!attribute.isNotHiddenAttribute());
    operationRequestParameter.setDeprecated(attribute.isDeprecated());
    operationRequestParameter.setListParam(
        attribute.isFilterAttribute() && !attribute.isEnumAttribute());
    operationRequestParameter.setListType(dataTypeForForFilterAttribute(attribute));
    operationRequestParameter.setDotNetType(dataTypeForForFilterAttribute(attribute));
    operationRequestParameter.setVarName(GenUtil.getVarName(attribute.name));
    if (attribute.attributes().isEmpty()
        || attribute.attributes().stream().noneMatch(a -> a.schema instanceof ObjectSchema)) {
      if (parentName == null) {
        operationRequestParameter.setDotNetMethName(Inflector.capitalize(getName(attribute.name)));
        operationRequestParameter.setName(attribute.name);
        operationRequestParameter.setReturnGeneric(
            new ReturnTypeBuilder()
                .setAttribute(attribute)
                .setAction(action)
                .setDataTypeMethod(this::dataType)
                .setGetFullNameDotnetMethod(this::getFullNameDotnet)
                .setResourceList(resourceList)
                .setActiveResource(activeResource)
                .build());
      } else {
        operationRequestParameter.setDotNetMethName(
            Inflector.capitalize(getName(parentName)) + toClazName(attribute.name));
        operationRequestParameter.setName(parentName + "[" + attribute.name + "]");
        operationRequestParameter.setReturnGeneric(
            new ReturnTypeBuilder()
                .setAttribute(attribute)
                .setAction(action)
                .setParentName(parentName)
                .setDataTypeMethod(this::dataType)
                .setGetFullNameDotnetMethod(this::getFullNameDotnet)
                .setResourceList(resourceList)
                .setActiveResource(activeResource)
                .build());
      }
    }
    operationRequestParameter.setMulti(attribute.isMultiAttribute());
    operationRequestParameter.setSupportsPresenceFilter(attribute.isPresenceOperatorSupported());
    operationRequestParameter.setDotNetPutMethName(getDotNetPutMethName(attribute.isRequired));
    if (attribute.name.equals(SORT_BY) && !getSortParams(attribute).isEmpty()) {
      operationRequestParameter.setListParam(true);
      operationRequestParameter.setSortParam(true);
      operationRequestParameter.setSortParams(getSortParams(attribute));
    }
    if (!(attribute.attributes().isEmpty()
            || attribute.attributes().stream().noneMatch(a -> a.schema instanceof ObjectSchema))
        && !operationRequestParameter.isSortParam()) {
      for (Attribute subAttribute : attribute.attributes()) {
        handleOperationListAttribute(
            action, subAttribute, operationRequestParameters, attribute.name);
      }
    } else {
      operationRequestParameters.add(operationRequestParameter);
    }
  }

  private List<OperationRequestParameterSortParameter> getSortParams(Attribute attribute) {
    List<OperationRequestParameterSortParameter> sortParameters = new ArrayList<>();
    Attribute sortAttribute =
        attribute.attributes().isEmpty() ? null : attribute.attributes().get(0);
    if (sortAttribute != null
        && sortAttribute.getEnum() != null
        && !sortAttribute.getEnum().values().isEmpty()) {
      for (String enumValue : sortAttribute.getEnum().values()) {
        OperationRequestParameterSortParameter operationRequestParameterSortParameter =
            new OperationRequestParameterSortParameter();
        operationRequestParameterSortParameter.setName(enumValue);
        operationRequestParameterSortParameter.setDotNetMethName(
            getName(singularize(attribute.name) + "_" + enumValue));
        sortParameters.add(operationRequestParameterSortParameter);
      }
    }
    return sortParameters;
  }

  public String getDotNetPutMethName(boolean isRequired) {
    return isRequired ? "Add" : "AddOpt";
  }

  public String getReturnGenericForSingularSubParams(Attribute attribute) {
    return dataType(attribute.schema) + ", " + getClazName(attribute);
  }

  public String getReturnGeneric(Attribute parameter) {
    String lowerCase = dataType(parameter.schema).toLowerCase();
    if (lowerCase.equals("timestamp")
        || lowerCase.equals(STING_TYPE)
        || lowerCase.equals("boolean")) {
      return getClazName(parameter);
    }
    return dataType(parameter.schema) + ", " + getClazName(parameter);
  }

  private List<Column> getResourceCols(Resource res) {
    List<Column> cols = new ArrayList<>();
    for (Attribute attribute : res.getSortedResourceAttributes()) {
      if (attribute.isContentObjectAttribute()) continue;
      Column column = new Column();
      column.setDeprecated(attribute.isDeprecated());
      column.setReturnType(getColsRetType(attribute));
      column.setGetterCode(getGetterCode(attribute));
      column.setSubResource(attribute.isSubResource());
      if (attribute.name.equals("type")) {
        column.setMethName(activeResource.name + "Type");
      } else {
        column.setMethName(getName(attribute.name));
      }
      cols.add(column);
    }
    return cols;
  }

  public List<Column> getSubResourceCols(Attribute subResourceAttribute) {
    List<Column> cols = new ArrayList<>();
    for (Attribute attribute : subResourceAttribute.attributes()) {
      if (!attribute.isNotHiddenAttribute()) continue;
      Column column = new Column();
      column.setDeprecated(attribute.isDeprecated());
      column.setReturnType(
          updateForSubAttributes(
              attribute.subResourceName() != null
                  ? attribute.subResourceName()
                  : getColsRetType(attribute)));
      column.setGetterCode(updateForSubAttributes(getGetterCode(attribute)));
      if (attribute.name.equals("type")) {
        column.setMethName(toClazName(singularize(subResourceAttribute.name)) + "Type");
      } else {
        column.setMethName(getName(attribute.name));
      }
      cols.add(column);
    }
    return cols;
  }

  private String updateForSubAttributes(String colsRetType) {
    colsRetType = colsRetType.replace("TxnStatusEnum", "Transaction.StatusEnum");
    colsRetType = colsRetType.replace("CnStatusEnum", "CreditNote.StatusEnum");
    colsRetType = colsRetType.replace("CnReasonCodeEnum", "CreditNote.ReasonCodeEnum");
    return colsRetType;
  }

  public String getGetterCode(Attribute attribute) {
    StringBuilder buf = new StringBuilder();
    String name = attribute.name;
    if (attribute.isEnumAttribute()) {
      String type = attribute.getEnumApiName() + "Enum";
      buf.append("GetEnum<")
          .append(type)
          .append(">")
          .append("(\"")
          .append(name)
          .append("\", ")
          .append(getRequired(attribute))
          .append(")");
    } else if (attribute.isListSubResourceAttribute()) {
      buf.append("GetResourceList<")
          .append(getFullClazName(attribute))
          .append(">")
          .append("(\"")
          .append(name)
          .append("\")");
    } else if (attribute.isSubResource()) {
      buf.append("GetSubResource<")
          .append(getFullClazName(attribute))
          .append(">")
          .append("(\"")
          .append(name)
          .append("\")");
    } else if (attribute.isListAttribute()
        && !Resource.isGlobalResourceReference(attribute.schema)) {
      Attribute itemAttribute =
          new Attribute(
              attribute.schema.getItems().getName(),
              attribute.schema.getItems(),
              attribute.isRequired);
      buf.append("GetList<")
          .append(getColsRetType(itemAttribute))
          .append(">(\"")
          .append(name)
          .append("\")");
    } else if (isDateTimeAttribute(attribute)) {
      buf.append(!attribute.isRequired ? "" : "(DateTime)")
          .append("GetDateTime(\"")
          .append(name)
          .append("\", ")
          .append(attribute.isRequired)
          .append(")");
    } else if (isJsonObject(attribute)) {
      if (activeResource.hasContentTypeJsonAction()) {
        buf.append("GetMap(\"")
            .append(name)
            .append("\", ")
            .append(getRequired(attribute))
            .append(")");
      } else {
        buf.append("GetJToken(\"")
            .append(name)
            .append("\", ")
            .append(getRequired(attribute))
            .append(")");
      }
    } else if (isJsonArray(attribute)) {
      buf.append("GetJArray(\"")
          .append(name)
          .append("\", ")
          .append(getRequired(attribute))
          .append(")");
    } else {
      buf.append("GetValue<")
          .append(getColsRetType(attribute))
          .append(">")
          .append("(\"")
          .append(name)
          .append("\", ")
          .append(getRequired(attribute))
          .append(")");
    }
    return buf.toString();
  }

  public boolean isDateTimeAttribute(Attribute attribute) {
    if (attribute.schema != null && attribute.schema.getFormat() != null) {
      return attribute.schema.getFormat().equals(UNIX_TIME);
    }
    return false;
  }

  public boolean isJsonArray(Attribute attribute) {
    var dataType = this.dataType(attribute.schema);
    return dataType != null && dataType.equals(J_ARRAY);
  }

  public boolean isJsonObject(Attribute attribute) {
    var dataType = this.dataType(attribute.schema);
    return dataType != null
        && (dataType.equals(J_TOKEN) || dataType.equals(DICTIONARY_K_STR_V_OBJ));
  }

  public String getReturnTypeNullable(Attribute attribute) {
    if (!attribute.isEnumAttribute()
        && (STING_TYPE.equals(dataType(attribute.schema)) || attribute.isListAttribute())) {
      return "";
    }
    return !attribute.isRequired && !isJsonObject(attribute) && !isJsonArray(attribute) ? "?" : "";
  }

  public String getRequired(Attribute attribute) {
    if (attribute.isPcv1Attribute()) {
      return "false";
    }
    return !attribute.isRequired ? "false" : "true";
  }

  private String getColsRetType(Attribute attribute) {

    if (attribute.isListSubResourceAttribute()) {
      return LIST_OF + getFullClazName(attribute) + ">";
    }
    if (attribute.isListAttribute() && !Resource.isGlobalResourceReference(attribute.schema)) {
      Attribute itemAttribute =
          new Attribute(attribute.name, attribute.schema.getItems(), attribute.isRequired);
      return LIST_OF + singularize(getColsRetType(itemAttribute)) + ">";
    }
    if (isJsonArray(attribute)) {
      return J_ARRAY;
    }
    if (attribute.isEnumAttribute() && attribute.attributes().isEmpty())
      return attribute.getEnumApiName() + "Enum" + getReturnTypeNullable(attribute);

    if (attribute.isForeignColumn()) {
      return dataType(attribute.schema);
    }
    if (attribute.isDependentAttribute()) {
      return getFullClazName(attribute);
    }
    if (attribute.isSubResource()) {
      if (attribute.subResourceName() != null) {
        return getFullClazName(attribute);
      }
    } else {
      return dataType(attribute.schema) + getReturnTypeNullable(attribute);
    }
    return "unknown";
  }

  private String getName(String name) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
  }

  public String getFullClazName(Attribute attribute) {
    if (activeResource.subResources().stream()
        .anyMatch(s -> s.id != null && s.id.equals(attribute.name))) {
      if (attribute.schema instanceof ArraySchema) {
        return activeResource.name + toClazName(singularize(attribute.name));
      }
      return activeResource.name + toClazName(attribute.subResourceName());
    }
    if (attribute.isSubResource() && attribute.subResourceName() != null) {
      return toClazName(attribute.subResourceName());
    }
    return null;
  }

  private String getClazName(Attribute attribute) {
    return toCamelCase(singularize(attribute.name));
  }

  private List<Operation> getOperationList(Resource res) {
    List<Operation> operations = new ArrayList<>();
    for (Action action : res.getSortedAction()) {
      Operation operation = new Operation();
      operation.setHandleArg(getHandleArg(action));
      operation.setUrlArgs(getUrlArgs(action));
      operation.setDeprecated(action.isOperationDeprecated());
      operation.setSubDomain(action.subDomain());
      operation.setIdempotent(action.isIdempotent());
      operation.setRetType(getRetType(action));
      operation.setMethName(Inflector.capitalize(action.name));
      operation.setReqCreationCode(requestCreationCodeWithSubDomainAndContentTypeJson(action));
      operations.add(operation);
    }
    return operations;
  }

  public String requestCreationCodeWithSubDomainAndContentTypeJson(Action action) {
    String requestCode = getReqCreationCode(action);
    if (action.subDomain() != null) {
      requestCode = requestCode + ".SetSubDomain(\"" + action.subDomain() + "\")";
    }
    if (action.isOperationNeedsJsonInput()) {
      requestCode = requestCode + ".IsJsonRequest(true)";
    }
    if (!action.isIdempotent() && action.httpRequestType.equals(HttpRequestType.POST)) {
      requestCode = requestCode + ".SetIdempotent(false)";
    }
    return requestCode;
  }

  public String getReqCreationCode(Action action) {
    boolean isCodeGen = isCodeGen(action);
    if (action.isInputObjNeeded() && isCodeGen) {
      StringBuilder buf = new StringBuilder();
      buf.append("return new ").append(getClazName(action)).append("(url");
      if (!action.isListResourceAction()) {
        buf.append(", HttpMethod." + action.httpRequestType);
      }
      if (action.hasPostActionContainingFilterAsBodyParams()) {
        buf.append(", true");
      }
      buf.append(")");
      return buf.toString();
    } else {
      if (action.isListResourceAction()) {
        return "return new ListRequest(url)";
      } else {
        return "return new EntityRequest<Type>(url, HttpMethod." + action.httpRequestType + ")";
      }
    }
  }

  public String getHandleArg(Action action) {
    return !action.pathParameters().isEmpty() ? "string id" : "";
  }

  public String getUrlArgs(Action action) {
    StringBuilder buf = new StringBuilder();
    String actionUrl = action.getUrl();
    actionUrl =
        actionUrl.replace(
            "subscription_entitlements/set_availability",
            "subscription_entitlements###set_availability");
    String[] urlParts = actionUrl.split("/");

    appendUrlArg(buf, urlParts[1]);

    for (int i = 2; i < urlParts.length; i++) {
      String path = urlParts[i];

      if (isPathParameter(path, action)) {
        appendPathParameter(buf, path);
      } else {
        appendUrlArg(buf, path);
      }
    }

    return buf.toString();
  }

  private void appendUrlArg(StringBuilder buf, String arg) {
    if (buf.isEmpty()) {
      buf.append("\"").append(patchFix(arg)).append("\"");
    } else {
      buf.append(", \"").append(patchFix(arg)).append("\"");
    }
  }

  private String patchFix(String path) {
    return path.replace("###", "/");
  }

  private boolean isPathParameter(String path, Action action) {
    return action.pathParameters().stream()
        .map(p -> "{" + p.getName() + "}")
        .toList()
        .contains(path);
  }

  private void appendPathParameter(StringBuilder buf, String path) {
    buf.append(", CheckNull(id)");
  }

  private boolean isCodeGen(Action action) {
    return action.isOperationDeprecated()
        ? (action.hasInputParamsForEAPClientLibs() || action.hasInputParamsForClientLibs())
        : action.hasInputParamsForClientLibs();
  }

  private String getRetType(Action action) {
    boolean isCodeGen = isCodeGen(action);
    if (action.isInputObjNeeded() && isCodeGen) {
      return getClazName(action);
    } else {
      return action.isListResourceAction() ? "ListRequest" : "EntityRequest<Type>";
    }
  }

  public String getClazName(Action action) {
    String methodName = action.name;
    if (action.isListResourceAction()) {
      methodName = action.resourceId() + "_" + action.name;
    }
    return GenUtil.toClazName(methodName, "Request");
  }

  public String getSuperClazName(Action action) {
    return action.isListResourceAction() ? "ListRequestBase" : "EntityRequest";
  }

  private Map<String, Object> globalEnumTemplate(Enum e) {
    return new GlobalEnum(e).template();
  }

  public String dataTypeForForFilterAttribute(Attribute attribute) {
    if (attribute.schema instanceof ArraySchema) {
      return LIST_OF + dataType(attribute.schema) + ">";
    }
    if (!attribute.attributes().isEmpty()
        && attribute.attributes().stream().anyMatch(a -> a.schema instanceof ObjectSchema)) {
      attribute = attribute.attributes().get(0);
    }
    if (attribute.isFilterAttribute() && attribute.schema instanceof ObjectSchema) {
      return dataType(attribute.schema);
    }
    if (attribute.isEnumAttribute() && attribute.isGlobalEnumAttribute()) {
      return ENUM_PACKAGE_NAME + getName(attribute.name + ENUM_SUFFIX);
    }

    if (isDateTimeAttribute(attribute)) {
      return "long";
    }
    return dataType(attribute.schema);
  }

  public String getFullNameDotnet(Attribute attribute) {
    String name = attribute.name;
    if (!attribute.attributes().isEmpty()
        && ((attribute.attributes().get(0).isExternalEnum()
                && attribute.attributes().get(0).isGenSeparate())
            || attribute.attributes().get(0).isGenSeparate())) {
      return ENUM_PACKAGE_NAME + Inflector.capitalize(toClazName(name)) + "Enum";
    }
    if (attribute.isGlobalEnumAttribute()) {
      return ENUM_PACKAGE_NAME + Inflector.capitalize(toClazName(name)) + "Enum";
    }
    if (attribute.metaModelName() != null) {
      if (attribute.schema.getExtensions() != null && attribute.isSubResource()) {
        if (activeResource.name.equalsIgnoreCase(EXPORT)) {
          return Inflector.capitalize(
                  toClazName(
                      Inflector.singularize(attribute.metaModelName())
                          + "."
                          + Inflector.capitalize(toClazName((attribute.name)))))
              + "Enum";
        }
        return Inflector.capitalize(toClazName(activeResource.name))
            + Inflector.capitalize(
                toClazName(
                    Inflector.singularize(attribute.metaModelName())
                        + "."
                        + Inflector.capitalize(toClazName((attribute.name)))))
            + "Enum";
      } else {
        return Inflector.capitalize(toClazName(Inflector.singularize(attribute.metaModelName())))
            + "."
            + Inflector.capitalize(toClazName(attribute.name))
            + "Enum";
      }
    }
    return Inflector.capitalize(toClazName(attribute.name)) + "Enum";
  }

  public String parameterPrimitiveDataType(Attribute attribute) {
    if (attribute.isEnumAttribute()) {
      return getFullNameDotnet(attribute);
    }
    if (isDateTimeAttribute(attribute)) {
      return "long";
    }
    String dataType = dataType(attribute.schema);
    if (dataType.equals(STING_TYPE) && attribute.schema instanceof ArraySchema) {
      return "List<string>";
    }
    return dataType;
  }

  public String dataTypeForSingleSubAttribute(Attribute attribute, String parentAttribute) {
    if (attribute.isGlobalEnumAttribute()) {
      return "ChargeBee.Models.Enums." + getName(attribute.name + ENUM_SUFFIX);
    }
    boolean isResourceLevelEnum =
        activeResource.attributes().stream().anyMatch(a -> a.name.equals(parentAttribute));
    if (attribute.isEnumAttribute() && isResourceLevelEnum) {
      return getName(
          activeResource.id + "_" + parentAttribute + "_._" + attribute.name + ENUM_SUFFIX);
    }
    if (attribute.isEnumAttribute()) {
      return getName(parentAttribute + "_._" + attribute.name + ENUM_SUFFIX);
    }
    if (isDateTimeAttribute(attribute)) {
      return "long";
    }
    return dataType(attribute.schema);
  }

  private String enumDataTypeForMultiAttribute(
      Attribute attribute, String parentAttribute, String modelName) {
    if (attribute.isEnumAttribute()) {
      if (toClazName(singularize(parentAttribute)).equals(toClazName(activeResource.name))) {
        return toClazName(singularize(parentAttribute)) + "." + toClazName(attribute.name) + "Enum";
      }
      List<Resource> reference =
          resourceList.stream()
              .filter(r -> r.id != null && r.id.equalsIgnoreCase(singularize(parentAttribute)))
              .toList();
      if (!reference.isEmpty()) {
        Resource refrenceResource = reference.get(0);
        if (refrenceResource.attributes().stream().anyMatch(a -> a.name.equals(attribute.name))) {
          return toClazName(singularize(parentAttribute))
              + "."
              + toClazName(attribute.name)
              + "Enum";
        }
      }
      reference =
          resourceList.stream()
              .filter(r -> r.id != null && r.id.equalsIgnoreCase(modelName))
              .toList();
      if (!reference.isEmpty()) {
        Resource refrenceResource = reference.get(0);
        if (refrenceResource.attributes().stream().anyMatch(a -> a.name.equals(parentAttribute))) {
          return toClazName(modelName)
              + "."
              + toClazName(modelName)
              + toClazName(singularize(parentAttribute))
              + "."
              + toClazName(attribute.name)
              + "Enum";
        }
      }
      return toClazName(activeResource.id)
          + toClazName(singularize(parentAttribute))
          + "."
          + toClazName(attribute.name)
          + "Enum";
    }
    return null;
  }

  public String dataTypeForMultiAttribute(
      Attribute attribute, String parentAttribute, String modelName) {
    String name = attribute.name;
    if (name.equals("juris_type")) {
      name = "tax_juris_type";
    }
    if (attribute.isGlobalEnumAttribute()) {
      return "ChargeBee.Models.Enums." + getName(name + ENUM_SUFFIX);
    }
    String enumDataType = enumDataTypeForMultiAttribute(attribute, parentAttribute, modelName);
    // Patch to match the naming convention of cb-app
    if (activeResource.name.equalsIgnoreCase("CreditNote")
        && enumDataType != null
        && enumDataType.equals("Invoice.InvoiceLineItem.EntityTypeEnum")) {
      return "CreditNoteLineItem.EntityTypeEnum";
    }
    if (activeResource.name.equalsIgnoreCase("Invoice")
        && enumDataType != null
        && enumDataType.equals("Invoice.InvoiceLineItem.EntityTypeEnum")) {
      return "InvoiceLineItem.EntityTypeEnum";
    }
    if (activeResource.name.equalsIgnoreCase("Invoice")
        && enumDataType != null
        && enumDataType.equals("Invoice.InvoiceNote.EntityTypeEnum")) {
      return "InvoiceNote.EntityTypeEnum";
    }
    if (activeResource.name.equalsIgnoreCase("CreditNote")
        && enumDataType != null
        && enumDataType.equals("Invoice.InvoiceDiscount.EntityTypeEnum")) {
      return "CreditNoteDiscount.EntityTypeEnum";
    }
    if (activeResource.name.equalsIgnoreCase("Invoice")
        && enumDataType != null
        && enumDataType.equals("Invoice.InvoiceDiscount.EntityTypeEnum")) {
      return "InvoiceDiscount.EntityTypeEnum";
    }
    if (enumDataType != null) return enumDataType;

    String dataType = dataType(attribute.schema);
    if (dataType.equals(DATE_TIME)) {
      return "long";
    }

    return dataType;
  }

  private String integerDataType(Schema schema) {
    if (schema.getType().equals("integer")) {
      if (schema.getExtensions() != null
          && schema.getExtensions().get(IS_MONEY_COLUMN) != null
          && (boolean) schema.getExtensions().get(IS_MONEY_COLUMN)) {
        return "long";
      }
      if (schema.getFormat().equals("int32")) {
        return "int";
      }
      if (schema.getFormat().equals("int64")) {
        return "long";
      }
      if (schema.getFormat().equals(UNIX_TIME)) {
        return DATE_TIME;
      }
    }
    return null;
  }

  private String numberDataType(Schema schema) {
    if (schema.getType().equals(NUMBER_TYPE)) {
      if (schema.getFormat().equals("decimal")) {
        return "decimal";
      } else if (schema.getFormat().equals("double")) {
        return "double";
      }
    }
    return null;
  }

  private String intDataType(Schema schema) {
    if (schema instanceof NumberSchema || schema instanceof IntegerSchema) {
      String integerType = integerDataType(schema);
      if (integerType != null) return integerType;
      return numberDataType(schema);
    }
    return null;
  }

  private String stringDataType(Schema schema) {
    if (schema instanceof StringSchema
        || schema instanceof EmailSchema
        || schema instanceof PasswordSchema) {
      if (schema.getFormat() != null && schema.getFormat().equals(UNIX_TIME)) {
        return DATE_TIME;
      }
      if (schema.getFormat() != null && !schema.getFormat().equals("email")) {
        return "long";
      }
      return STING_TYPE;
    }
    return null;
  }

  @Override
  public String dataType(Schema schema) {
    String stringType = stringDataType(schema);
    if (stringType != null) return stringType;

    String numberType = intDataType(schema);
    if (numberType != null) return numberType;

    if (schema instanceof BooleanSchema) {
      return "bool";
    }
    if (schema instanceof ObjectSchema && GenUtil.hasAdditionalProperties(schema)) {
      if (activeResource.hasContentTypeJsonAction()) {
        return DICTIONARY_K_STR_V_OBJ;
      }
      return J_TOKEN;
    }
    if (schema instanceof ArraySchema && schema.getItems() != null) {
      if (schema.getItems().getType() == null) {
        return J_ARRAY;
      }
      return String.format("%s", dataType(schema.getItems()));
    }
    if (schema instanceof ObjectSchema) {
      String filter = new Attribute(schema.getName(), schema, false).getFilterType();
      if (filter == null) return "StringFilter";
      return filter;
    }
    return "unknown";
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
