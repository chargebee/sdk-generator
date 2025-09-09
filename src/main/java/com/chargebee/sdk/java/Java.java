package com.chargebee.sdk.java;

import static com.chargebee.GenUtil.*;
import static com.chargebee.openapi.Extension.*;
import static com.chargebee.sdk.common.Constant.DEBUG_RESOURCE;
import static com.chargebee.sdk.common.Constant.SDK_DEBUG;
import static com.chargebee.sdk.java.Constants.*;

import com.chargebee.GenUtil;
import com.chargebee.handlebar.Inflector;
import com.chargebee.openapi.*;
import com.chargebee.openapi.Enum;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.parameter.Parameter;
import com.chargebee.openapi.parameter.Path;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.common.ActionAssist;
import com.chargebee.sdk.java.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Java extends Language {

  public final ApiVersion apiVersion;
  GenerationMode generationMode;
  JarType jarType;
  Resource activeResource;
  List<Enum> globalEnums;
  List<Resource> resourceList = new ArrayList<>();

  public Java(GenerationMode generationMode, ApiVersion apiVersion, JarType jarType) {
    this.generationMode = generationMode;
    this.apiVersion = apiVersion;
    this.jarType = jarType;
  }

  @Override
  public List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    List<Enum> globalEnumList = new ArrayList<>(spec.globalEnums());
    globalEnumList.stream()
        .filter(
            globalEnum ->
                apiVersion.equals(ApiVersion.V1)
                    && generationMode.equals(GenerationMode.INTERNAL)
                    && jarType.equals(JarType.INT)
                    && globalEnum.name.equals("EntityType"))
        .forEach(
            globalEnum -> {
              globalEnum.values().add("invoice");
              globalEnum.values().add("quote");
              globalEnum.values().add("credit_note");
            });
    if (generationMode.equals(GenerationMode.INTERNAL)) {
      spec.enableForQa();
    }
    var resources =
        generationMode.equals(GenerationMode.INTERNAL)
            ? spec.resources()
            : spec.resources().stream()
                .filter(
                    resource -> !Arrays.stream(this.hiddenOverride).toList().contains(resource.id))
                .toList();
    resourceList = resources;
    var createModelsDirectory =
        new FileOp.CreateDirectory(outputDirectoryPath, "/" + Constants.MODELS);
    var createEnumsDirectory =
        new FileOp.CreateDirectory(
            outputDirectoryPath + "/" + Constants.MODELS, "/" + Constants.ENUMS);
    List<FileOp> fileOps = new ArrayList<>();

    fileOps.addAll(List.of(createModelsDirectory, createEnumsDirectory));
    fileOps.addAll(
        generateGlobalEnumFiles(
            outputDirectoryPath + "/" + Constants.MODELS + "/" + Constants.ENUMS,
            globalEnumList,
            resources));
    fileOps.addAll(generateResourceFiles(outputDirectoryPath + "/" + Constants.MODELS, resources));
    fileOps.add(generateResultBaseFile(outputDirectoryPath + "/" + Constants.INTERNAL, resources));

    return fileOps;
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of(
        "enums",
        "/templates/java/enums.java.hbs",
        "models.resources",
        "/templates/java/models.resources.java.hbs",
        "resultBase",
        "/templates/java/internal.resultBase.java.hbs");
  }

  private List<FileOp> generateGlobalEnumFiles(
      String outDirectoryPath, List<Enum> globalEnums, List<Resource> resources)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();

    if (generationMode.equals(GenerationMode.INTERNAL)) {
      for (var res : resources) {
        for (var attribute : res.getSortedResourceAttributes()) {
          processEnumAttribute(attribute, globalEnums);
          if (attribute.isSubResource()) {
            for (var enumAttribute : attribute.attributes()) {
              processEnumAttribute(enumAttribute, globalEnums);
            }
          }
        }
        for (var action : res.actions) {
          ActionAssist actionEnum = ActionAssist.of(action).withSortBy(true);
          for (Attribute attribute : actionEnum.getAllAttribute().stream().toList()) {
            processEnumAttribute(attribute, globalEnums);
            for (var subAttribute : attribute.attributes()) {
              processEnumAttribute(subAttribute, globalEnums);
            }
          }
        }
      }
    }

    Template enumTemplate = getTemplateContent("enums");
    globalEnums = globalEnums.stream().sorted(Comparator.comparing(e -> e.name)).toList();
    for (var _enum : globalEnums) {
      var content = enumTemplate.apply(globalEnumTemplate(_enum));
      String fileName = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, _enum.name);
      fileOps.add(new FileOp.WriteString(outDirectoryPath, fileName + ".java", content));
    }
    return fileOps;
  }

  private void processEnumAttribute(Attribute attribute, List<Enum> globalEnums) {
    if (attribute.isGlobalEnumAttribute()
        && attribute.isGenSeparate()
        && attribute.getEnum() != null) {
      String attributeName = getPascalName(attribute.name);
      if (globalEnums.stream().noneMatch(e -> e.name != null && e.name.equals(attributeName))) {
        globalEnums.add(new Enum(attributeName, attribute.schema));
      }
    }
  }

  private String getEnumsPkg() {
    return getPackagePrefix() + ".models.enums";
  }

  private String getPackageName() {
    return getPackagePrefix() + ".models";
  }

  private String getPackagePrefix() {
    return "com.chargebee"
        + (generationMode.equals(GenerationMode.INTERNAL) && apiVersion.equals(ApiVersion.V2)
            ? ".v2"
            : "");
  }

  private List<FileOp> generateResourceFiles(String outDirectoryPath, List<Resource> resources)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();
    Template resourceTemplate = getTemplateContent("models.resources");
    for (var res : resources) {
      if (SDK_DEBUG && !DEBUG_RESOURCE.contains(res.name)) continue;
      if (generationMode.equals(GenerationMode.INTERNAL)) {
        res.enableForQa();
        res.setApiVersion(apiVersion);
      }
      activeResource = res;
      com.chargebee.sdk.java.models.Resource resource;
      resource = new com.chargebee.sdk.java.models.Resource();
      resource.setPkgName(getPackageName());
      resource.setPkgNamePrefix(getPackagePrefix());
      resource.setEnumsPkg(getEnumsPkg());
      resource.setClazName(res.name);
      resource.setOperations(getOperationList(res));
      resource.setCols(getResourceCols(res));
      resource.setOperRequestClasses(getOperRequestClasses(res));
      resource.setHasOperReqClasses(!resource.getOperRequestClasses().isEmpty());
      resource.setSnippet(getSnippet(res.name));
      resource.setEventResource(res.name.equalsIgnoreCase("event"));
      resource.setCustomImport(getCustomImport());
      resource.setHasFilterImports(getHasFilterImports());
      resource.setEnumCols(getEnumCols(res));
      resource.setSubResources(getSubResrouces(res));
      resource.setSchemaLessEnums(
          SchemaLessEnumParser.getSchemalessEnumForResource(resourceList, activeResource));
      ObjectMapper oMapper = new ObjectMapper();
      var content = resourceTemplate.apply(oMapper.convertValue(resource, Map.class));
      String fileName = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, res.name);
      fileOps.add(new FileOp.WriteString(outDirectoryPath, fileName + ".java", content));
    }
    return fileOps;
  }

  private boolean getHasFilterImports() {
    return !generationMode.equals(GenerationMode.INTERNAL) || !apiVersion.equals(ApiVersion.V1);
  }

  private List<EnumColumn> getSubResourceEnum(Attribute attribute) {
    List<EnumColumn> enumColumns = new ArrayList<>();
    for (Attribute enumAttribute : attribute.attributes()) {
      if (!enumAttribute.isNotHiddenAttribute()) continue;
      if (enumAttribute.isApi() && !enumAttribute.isExternalEnum()) {
        EnumColumn enumColumn = new EnumColumn();
        enumColumn.setApiClassName(toClazName(enumAttribute.name));
        enumColumn.setVisibleEntries(getEnumEntries(enumAttribute));
        enumColumn.setDeprecated(enumAttribute.isDeprecated());
        enumColumns.add(enumColumn);
      }
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
      visibleEnumEntry.setName(enumName(validValue));
      visibleEnumEntry.setApiName(validValue);
      visibleEnumEntries.add(visibleEnumEntry);
    }
    return visibleEnumEntries;
  }

  private String enumName(String name) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_UNDERSCORE, name);
  }

  private String getName(String name) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name);
  }

  private String getPascalName(String name) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
  }

  private List<SubResource> getSubResrouces(Resource res) {
    List<SubResource> subResources = new ArrayList<>();
    for (Attribute attribute : res.getSortedResourceAttributes()) {
      if (attribute.isSubResource()
          && !attribute.isDependentAttribute()
          && !Resource.isGlobalResourceReference(attribute.schema)) {
        SubResource subResource = new SubResource();
        subResource.setClazName(
            attribute.schema instanceof ArraySchema
                ? singularize(toClazName(attribute.name))
                : attribute.subResourceName());
        subResource.setEnumCols(getSubResourceEnum(attribute));
        subResource.setSchemaLessEnum(
            SchemaLessEnumParser.getSchemaLessEnumForSubResource(
                subResource.getClazName(), activeResource));
        subResource.setCols(getSubResourceCols(attribute));
        subResources.add(subResource);
      }
    }
    return subResources;
  }

  private String updateForSubAttributes(String colsRetType) {
    if (colsRetType != null) {
      colsRetType = colsRetType.replace("TxnStatus", "Transaction.Status");
      colsRetType = colsRetType.replace("CnStatus", "CreditNote.Status");
      colsRetType = colsRetType.replace("CnReasonCode", "CreditNote.ReasonCode");
    }
    return colsRetType;
  }

  public String getFullClazName(Attribute attribute) {
    if (activeResource.subResources().stream()
        .anyMatch(s -> s.id != null && s.id.equals(attribute.name))) {
      if (attribute.schema instanceof ArraySchema) {
        return activeResource.name + "." + toClazName(singularize(attribute.name));
      }
      return activeResource.name + "." + toClazName(attribute.subResourceName());
    }
    if (attribute.isSubResource()
        && attribute.subResourceName() != null
        && attribute.subResourceParentName() != null) {
      return attribute.subResourceParentName() + "." + toClazName(attribute.subResourceName());
    }
    if (attribute.isSubResource() && attribute.subResourceName() != null) {
      if (attribute.schema instanceof ArraySchema) {
        return singularize(toClazName(attribute.name));
      } else {
        return toClazName(attribute.subResourceName());
      }
    }
    return attribute.isDependentAttribute()
        ? attribute.isListAttribute() ? toCamelCase(attribute.name) : getClazName(attribute)
        : (activeResource.name + "." + getClazName(attribute));
  }

  private String getClazName(Parameter attribute) {
    return toCamelCase(singularize(attribute.getName()));
  }

  private String getClazName(Attribute attribute) {
    return toCamelCase(singularize(attribute.name));
  }

  public boolean isJsonArray(Attribute attribute) {
    var dataType = this.dataType(attribute.schema);
    return dataType != null && dataType.equals("JArray");
  }

  public boolean isJsonObject(Attribute attribute) {
    var dataType = this.dataType(attribute.schema);
    return dataType != null && dataType.equals("JToken");
  }

  private String getColsRetType(Attribute attribute) {
    if (attribute.isListOfEnum()) {
      return Constants.LIST_OF + listEnumAttributeType(attribute) + ">";
    }
    if (attribute.isListOfSimpleType()) {
      return Constants.LIST_OF + dataType(attribute.schema) + ">";
    }
    if (attribute.isListSubResourceAttribute()) {
      if (Resource.isGlobalResourceReference(attribute.schema)) {
        return Constants.LIST_OF + attribute.subResourceName() + ">";
      }
      return Constants.LIST_OF + getFullClazName(attribute) + ">";
    }
    if (attribute.isListAttribute() && !Resource.isGlobalResourceReference(attribute.schema)) {
      Attribute itemAttribute =
          new Attribute(attribute.name, attribute.schema.getItems(), attribute.isRequired);
      return Constants.LIST_OF + singularize(getColsRetType(itemAttribute)) + ">";
    }
    if (!attribute.isListSubResourceAttribute() && attribute.isListAttribute()) {
      return Constants.LIST_OF
          + getName(activeResource.name + "_" + singularize(attribute.name))
          + ">";
    }
    if (isJsonArray(attribute)) {
      return "JArray";
    }
    if (attribute.isEnumAttribute()) {
      if (attribute.attributes().isEmpty()) return attribute.getEnumApiName();
      return attribute.attributes().get(0).getEnumApiName();
    }
    if (attribute.schema instanceof ArraySchema) {
      return "JSONArray";
    }
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
      return (activeResource.name) + getName(attribute.name);
    } else {
      return dataType(attribute.schema);
    }
  }

  public String getRequiredEnum(Attribute attribute) {
    return getRequired(attribute) + "Enum";
  }

  public String getRequired(Attribute attribute) {
    return !attribute.isRequired ? "opt" : "req";
  }

  public boolean isDateTimeAttribute(Attribute attribute) {
    var dataType = this.dataType(attribute.schema);
    return dataType != null && dataType.equals(Constants.DATE_TIME);
  }

  public final String listEnumAttributeType(Attribute attribute) {
    String importPiece =
        this.generationMode == GenerationMode.INTERNAL ? ENUMS_EXPORT_INTERNAL : ENUMS_EXPORT;
    String type =
        singularize(
                (String) attribute.getSchema().getItems().getExtensions().get(SDK_ENUM_API_NAME))
            + TYPE;
    return importPiece + type;
  }

  public String getGetterCode(Attribute attribute) {
    StringBuilder buf = new StringBuilder();
    String name = attribute.name;
    if (attribute.isEnumAttribute()) {
      if (attribute.isListOfEnum()) {
        buf.append(attribute.isRequired ? "" : "opt")
            .append("List")
            .append("(\"")
            .append(name)
            .append("\", ")
            .append(listEnumAttributeType(attribute))
            .append(Constants.DOT_CLASS);
      } else {
        buf.append(getRequiredEnum(attribute))
            .append("(\"")
            .append(name)
            .append("\", ")
            .append(attribute.getEnumApiName())
            .append(".class")
            .append(")");
      }
    } else if (attribute.isListOfSimpleType()) {
      buf.append(attribute.isRequired ? "" : "opt")
          .append("List")
          .append("(\"")
          .append(name)
          .append("\", ")
          .append(dataType(attribute.schema))
          .append(Constants.DOT_CLASS);
    } else if (attribute.isListSubResourceAttribute()) {
      if (Resource.isGlobalResourceReference(attribute.schema)) {
        buf.append(attribute.isRequired ? "req" : "opt")
            .append("List")
            .append("(\"")
            .append(name)
            .append("\", ")
            .append(singularize(attribute.subResourceName()))
            .append(Constants.DOT_CLASS);
      } else
        buf.append(attribute.isRequired ? "req" : "opt")
            .append("List")
            .append("(\"")
            .append(name)
            .append("\", ")
            .append(getFullClazName(attribute))
            .append(Constants.DOT_CLASS);
    } else if (attribute.isSubResource()) {
      if (attribute.isListAttribute()) {
        buf.append(attribute.isRequired ? "req" : "opt")
            .append("List")
            .append("(\"")
            .append(name)
            .append("\", ")
            .append(getFullClazName(attribute))
            .append(Constants.DOT_CLASS);
      } else
        buf.append(attribute.isRequired ? "req" : "opt")
            .append("SubResource")
            .append("(\"")
            .append(name)
            .append("\", ")
            .append(getFullClazName(attribute))
            .append(Constants.DOT_CLASS);
    } else if (attribute.isListAttribute()
        && !Resource.isGlobalResourceReference(attribute.schema)) {
      Attribute itemAttribute =
          new Attribute(
              attribute.schema.getItems().getName(),
              attribute.schema.getItems(),
              attribute.isRequired);
      buf.append(getRequired(attribute))
          .append(getColsRetType(itemAttribute))
          .append("(\"")
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
      buf.append("GetJToken(\"")
          .append(name)
          .append("\", ")
          .append(getRequired(attribute))
          .append(")");
    } else if (isJsonArray(attribute)) {
      buf.append("GetJArray(\"")
          .append(name)
          .append("\", ")
          .append(getRequired(attribute))
          .append(")");
    } else if (Objects.equals(getColsRetType(attribute), "Map<String, Object>")) {
      buf.append(attribute.isRequired ? "reqMap( \"" : "optMap(\"").append(name).append("\")");
    } else {
      buf.append(getRequired(attribute))
          .append(getColsRetType(attribute))
          .append("(\"")
          .append(name)
          .append("\"")
          .append(")");
    }
    return buf.toString();
  }

  public List<Column> getSubResourceCols(Attribute subResourceAttribute) {
    List<Column> cols = new ArrayList<>();
    for (Attribute attribute : subResourceAttribute.attributes()) {
      // to be discussed with the respective teams.
      if (activeResource.id.equals("subscription_entitlement")
          && subResourceAttribute.name.equals("components")
          && attribute.name.equals("inherited_entitlements")) continue;
      if (!attribute.isNotHiddenAttribute()) continue;
      Column column = new Column();
      column.setDeprecated(attribute.isDeprecated());
      column.setJavaType(
          updateForSubAttributes(
              attribute.subResourceName() != null
                  ? (attribute.isListAttribute()
                      ? "List<" + attribute.subResourceName() + ">"
                      : attribute.subResourceName())
                  : getColsRetType(attribute)));
      column.setGetterCode(updateForSubAttributes(getGetterCode(attribute)));
      if (attribute.name.equals("type")) {
        column.setMethName("type");
      } else {
        column.setMethName(getName(attribute.name));
      }
      cols.add(column);
    }
    return cols;
  }

  private List<EnumColumn> getEnumCols(Resource res) {
    List<EnumColumn> enumColumns = new ArrayList<>();
    for (Attribute attribute : res.attributes()) {
      if (!attribute.isNotHiddenAttribute()) continue;
      if (attribute.isApi() && !attribute.isGenSeparate()) {
        EnumColumn enumColumn = new EnumColumn();
        enumColumn.setDeprecated(attribute.isDeprecated());
        enumColumn.setVisibleEntries(getEnumEntries(attribute));
        enumColumn.setApiClassName(toClazName(attribute.name));
        enumColumns.add(enumColumn);
      }
    }
    return enumColumns;
  }

  private String getDataTypeForArrayOfEnum(Attribute attribute) {
    return (!attribute.isGenSeparate()
        ? getClazName(attribute)
        : "com.chargebee.v2.models.enums." + pluralize(getClazName(attribute)) + ">");
  }

  private String getCustomImport() {
    List<String> importStatements = new ArrayList();
    if (activeResource.hasBigDecimalAttributes(this)) {
      importStatements.add("import java.math.BigDecimal;");
    }
    return importStatements.isEmpty() ? null : String.join("\n", importStatements);
  }

  private String getSnippet(String name) throws IOException {
    if (name.equals("TimeMachine")) {
      return FileOp.fetchFileContent("src/main/resources/templates/java/timeMachine.java.hbs");
    } else if (name.equals("Export")) {
      return FileOp.fetchFileContent("src/main/resources/templates/java/export.java.hbs");
    } else if (name.equals("HostedPage")) {
      return FileOp.fetchFileContent("src/main/resources/templates/java/hostedPage.java.hbs");
    } else if (name.equals("Session") && !generationMode.equals(GenerationMode.INTERNAL)) {
      return FileOp.fetchFileContent("src/main/resources/templates/java/session.java.hbs");
    } else if (name.equals("Event")) {
      return FileOp.fetchFileContent("src/main/resources/templates/java/event.java.hbs");
    }
    return null;
  }

  public String getSuperClazName(Action action) {
    return action.isListResourceAction() ? "ListRequest" : Constants.REQUEST;
  }

  private List<com.chargebee.sdk.java.models.OperationRequestParameter> getOperationParams(
      Action action) {
    ActionAssist actionAssist = ActionAssist.of(action).withSortBy(true);
    if (activeResource.name.equals("Export")) {
      actionAssist = actionAssist.withFlatSingleAttribute(true);
    }
    List<com.chargebee.sdk.java.models.OperationRequestParameter> operationRequestParameters =
        new ArrayList<>();
    for (Attribute attribute :
        actionAssist.getAllAttribute().stream()
            .filter(
                attribute ->
                    !Arrays.stream(attribute.excludedParams).toList().contains(attribute.name))
            .toList()) {
      if (attribute.isFilterAttribute() || attribute.name.equals(Constants.SORT_BY)) {
        handleOperationListAttribute(action, attribute, operationRequestParameters, null);
      } else {
        if ((!generationMode.equals(GenerationMode.INTERNAL)
                || !(attribute.paramBlankOption() != null
                    && attribute.paramBlankOption().equals("as_empty")))
            && attribute.isCompositeArrayRequestBody()) continue;
        OperationRequestParameter operationRequestParameter = new OperationRequestParameter();
        operationRequestParameter.setHidden(!attribute.isNotHiddenAttribute());
        operationRequestParameter.setDeprecated(attribute.isDeprecated());
        operationRequestParameter.setReturnGeneric(getReturnGeneric(attribute));
        operationRequestParameter.setMethName(getVarName(attribute.name));
        operationRequestParameter.setJavaType(dataTypePrimitiveParamters(attribute));
        operationRequestParameter.setHasBatch(action.isBatch());
        operationRequestParameter.setIdempotent(action.isIdempotent());
        operationRequestParameter.setJavaSimpleType(dataType(attribute.schema));
        operationRequestParameter.setVarName(GenUtil.getVarName(attribute.name));
        operationRequestParameter.setName(attribute.name);
        operationRequestParameter.setPutMethodName(
            getPutMethodName(attribute.isRequired || attribute.isAttributeMetaCommentRequired()));
        operationRequestParameter.setSupportsPresenceFilter(
            attribute.isPresenceOperatorSupported());
        operationRequestParameter.setMulti(isMultiFilterAttribute(attribute));
        operationRequestParameter.setSimpleList(attribute.isListOfSimpleType());
        operationRequestParameter.setExceptionFilterParam(isExceptionFilterParam(attribute));
        operationRequestParameters.add(operationRequestParameter);
      }
    }
    return operationRequestParameters;
  }

  public String getReturnGeneric(Parameter parameter) {
    return switch (dataType(parameter.schema).toLowerCase()) {
      case Constants.TIMESTAMP, STRING_TYPE, Constants.BOOLEAN_TYPE -> getClazName(parameter);
      default -> dataType(parameter.schema) + ", " + getClazName(parameter);
    };
  }

  public String getFullNameJava(Attribute attribute, String parentName) {
    String name = attribute.name;
    if (name.equals("TaxJuris")) {
      name = "Juris";
    }
    if (!attribute.attributes().isEmpty()
        && ((attribute.attributes().get(0).isExternalEnum()
                && attribute.attributes().get(0).isGenSeparate())
            || attribute.attributes().get(0).isGenSeparate())) {
      return getEnumsPkg() + "." + Inflector.capitalize(toClazName(name));
    }
    if (attribute.isGlobalEnumAttribute()) {
      return getEnumsPkg() + "." + Inflector.capitalize(toClazName(name));
    }
    if (parentName != null
        && activeResource.subResources().stream()
            .anyMatch(r -> r.id != null && r.id.equals(parentName))) {
      return Inflector.capitalize(toClazName(parentName))
          + "."
          + Inflector.capitalize(toClazName((attribute.name)));
    }
    if (attribute.metaModelName() != null) {
      if (attribute.schema.getExtensions() != null && attribute.isSubResource()) {
        return Inflector.capitalize(
            toClazName(
                Inflector.singularize(attribute.metaModelName())
                    + "."
                    + Inflector.capitalize(toClazName((attribute.name)))));
      } else {
        return Inflector.capitalize(toClazName(Inflector.singularize(attribute.metaModelName())))
            + "."
            + Inflector.capitalize(toClazName(attribute.name));
      }
    }
    return Inflector.capitalize(toClazName(attribute.name));
  }

  public String getReturnGeneric(Attribute parameter) {
    return switch (dataType(parameter.schema).toLowerCase()) {
      case Constants.TIMESTAMP, STRING_TYPE, Constants.BOOLEAN_TYPE -> getClazName(parameter);
      default -> dataType(parameter.schema) + ", " + getClazName(parameter);
    };
  }

  public String dataTypePrimitiveParamters(Attribute attribute) {
    if (attribute.isEnumAttribute()) {
      if (attribute.isListOfEnum()) {
        return Constants.LIST_OF
            + ((!attribute.isGenSeparate()
                    ? getClazName(attribute)
                    : listEnumAttributeType(attribute))
                + ">");
      }
      return getFullNameJava(attribute, null);
    }

    String dataType = dataType(attribute.schema);
    if (dataType.equals(Constants.DATE_TIME)) {
      return "long";
    }
    if (dataType.equalsIgnoreCase(Constants.STRING_TYPE)
        && attribute.schema instanceof ArraySchema) {
      return "List<String>";
    }
    return dataType;
  }

  public String getPutMethodName(boolean isRequired) {
    return isRequired ? Constants.ADD : Constants.ADD_OPT;
  }

  private boolean isMultiFilterAttribute(Attribute attribute) {
    return attribute.isMultiAttribute();
  }

  private void handleOperationListAttribute(
      Action action,
      Attribute attribute,
      List<OperationRequestParameter> operationRequestParameters,
      String parentName) {
    if (!attribute.attributes().isEmpty()
        && attribute.attributes().stream().noneMatch(Attribute::isNotHiddenAttribute)) return;
    if (attribute.isCompositeArrayRequestBody()) return;
    OperationRequestParameter operationRequestParameter = new OperationRequestParameter();
    operationRequestParameter.setHidden(!attribute.isNotHiddenAttribute());
    operationRequestParameter.setDeprecated(attribute.isDeprecated());
    operationRequestParameter.setListParam(
        attribute.isFilterAttribute() && !attribute.isEnumAttribute());
    operationRequestParameter.setListType(dataTypeForForFilterAttribute(attribute));
    operationRequestParameter.setJavaType(dataTypeForForFilterAttribute(attribute));
    operationRequestParameter.setHasBatch(action.isBatch());
    operationRequestParameter.setIdempotent(action.isIdempotent());
    operationRequestParameter.setJavaSimpleType(dataType(attribute.schema));
    operationRequestParameter.setVarName(GenUtil.getVarName(attribute.name));
    if (attribute.attributes().isEmpty()
        || attribute.attributes().stream().noneMatch(a -> a.schema instanceof ObjectSchema)) {
      if (parentName == null) {
        operationRequestParameter.setMethName((getName(attribute.name)));
        operationRequestParameter.setName(attribute.name);
        operationRequestParameter.setReturnGeneric(
            getFilterReturnGeneric(attribute, action, parentName));
        /*to be discussed with the respective teams.
        event_type and webhook_status are not deprecated*/
        if (generationMode.equals(GenerationMode.INTERNAL)
            && activeResource.name.equals("Event")
            && (attribute.name.equals("event_type") || attribute.name.equals("webhook_status"))) {
          operationRequestParameter.setJavaType(
              dataTypeForExceptionFilterParam(attribute, parentName));
          operationRequestParameter.setExceptionFilterParam(true);
        }
      } else {
        operationRequestParameter.setMethName((getName(parentName)) + toClazName(attribute.name));
        operationRequestParameter.setName(parentName + "[" + attribute.name + "]");
        operationRequestParameter.setReturnGeneric(
            getFilterReturnGeneric(attribute, action, parentName));
      }
    }
    operationRequestParameter.setMulti(isMultiFilterAttribute(attribute));
    operationRequestParameter.setSimpleList(attribute.isListOfSimpleType());
    operationRequestParameter.setSupportsPresenceFilter(attribute.isPresenceOperatorSupported());
    operationRequestParameter.setPutMethodName(
        getPutMethodName(attribute.isRequired || attribute.isAttributeMetaCommentRequired()));
    if (attribute.name.equals(Constants.SORT_BY) && !getSortParams(attribute).isEmpty()) {
      operationRequestParameter.setListParam(true);
      operationRequestParameter.setSortParam(true);
      operationRequestParameter.setSortParams(getSortParams(attribute));
    }
    if (!(attribute.attributes().isEmpty()
        || attribute.attributes().stream().noneMatch(a -> a.schema instanceof ObjectSchema))) {
      for (Attribute subAttribute : attribute.attributes()) {
        handleOperationListAttribute(
            action, subAttribute, operationRequestParameters, attribute.name);
      }
    } else {
      operationRequestParameters.add(operationRequestParameter);
    }
  }

  public boolean isExceptionFilterParam(Attribute attribute) {
    return attribute.isFilterAttribute()
        && !attribute.isSortAttribute()
        && attribute.isDeprecated();
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
        operationRequestParameterSortParameter.setMethodName(
            getName(singularize(attribute.name) + "_" + enumValue));
        sortParameters.add(operationRequestParameterSortParameter);
      }
    }
    return sortParameters;
  }

  private String getFilterReturnGeneric(Attribute attribute, Action action, String parentName) {
    return new ReturnTypeBuilder()
        .setDataTypeMethod(this::dataType)
        .setGetFullNameMethod(this::getFullNameJava)
        .setParentName(parentName)
        .setAction(action)
        .setAttribute(attribute)
        .setActiveResource(activeResource)
        .setResourceList(resourceList)
        .setGenerationMode(generationMode)
        .setGlobalEnums(globalEnums)
        .setApiVersion(apiVersion)
        .build();
  }

  public String dataType(Schema schema, String attributeName) {
    if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
      if (schema.getExtensions() != null
          && !schema.getExtensions().isEmpty()
          && schema.getExtensions().get(GLOBAL_ENUM_REFERENCE) != null) {
        return getPackagePrefix() + Constants.MODELS_DOT_ENUMS + toCamelCase(attributeName);
      }
      if (schema.getDeprecated()) {
        return toClazName(attributeName);
      }
      return (activeResource.name) + "." + toClazName(attributeName);
    }
    return dataType(schema);
  }

  public String dataTypeForExceptionFilterParam(Attribute attribute, String parentName) {
    if (!attribute.attributes().isEmpty() && attribute.attributes().get(0).isEnumAttribute()) {
      return getFullNameJava(attribute, parentName);
    }
    return dataTypeForForFilterAttribute(attribute);
  }

  public String dataTypeForForFilterAttribute(Attribute attribute) {
    if (attribute.schema instanceof ArraySchema) {
      return Constants.LIST_OF + dataType(attribute.schema) + ">";
    }
    if (!attribute.attributes().isEmpty()
        && attribute.attributes().stream().anyMatch(a -> a.schema instanceof ObjectSchema)) {
      attribute = attribute.attributes().get(0);
    }
    if (attribute.isFilterAttribute() && attribute.schema instanceof ObjectSchema) {
      return dataType(attribute.schema);
    }
    if (attribute.isFilterAttribute()) {
      return attribute.getFilterType();
    }
    if (attribute.isEnumAttribute()) {
      if (attribute.isDeprecated()) {
        return toClazName(attribute.name);
      }
      if (attribute.isGlobalEnumAttribute()) {
        return getPackagePrefix() + Constants.MODELS_DOT_ENUMS + toClazName(attribute.name);
      }

      return (activeResource.name) + "." + toClazName(attribute.name);
    }

    return dataType(attribute.schema);
  }

  public List<SingularSubResource> getMultiSubs(Action action) {
    List<SingularSubResource> subResources = new ArrayList<>();
    for (Parameter iparam : action.queryParameters()) {
      if (iparam.isCompositeArrayBody()) {
        Attribute multiAttribute =
            new Attribute(iparam.getName(), iparam.schema, iparam.isRequired);
        if (!multiAttribute.isNotHiddenAttribute()) continue;
        if (!multiAttribute.attributes().isEmpty()
            && multiAttribute.attributes().stream().noneMatch(Attribute::isNotHiddenAttribute))
          continue;
        multiAttribute
            .attributes()
            .forEach(
                attribute -> {
                  if (attribute.isHiddenParameter()) return;
                  SingularSubResource subResource = new SingularSubResource();
                  subResource.setDeprecated(attribute.isDeprecated());
                  subResource.setListParam(false);
                  subResource.setReturnGeneric(getReturnGeneric(attribute));
                  subResource.setMulti(isMultiFilterAttribute(attribute));
                  subResource.setMethName(
                      getName(singularize(iparam.getName()) + "_" + attribute.name));
                  subResource.setJavaType(
                      dataTypeForMultiAttribute(attribute, iparam.getName(), action.modelName()));
                  subResource.setVarName(
                      singularize(getName(iparam.getName())) + toClazName(attribute.name));
                  subResource.setPutMethodName(attribute.isRequired ? "add" : "addOpt");
                  subResource.setResName(iparam.getName());
                  subResource.setName(attribute.name);
                  subResource.setSortOrder(
                      sortOrder(iparam.schema.getProperties().get(attribute.name).getItems()));
                  subResource.setHasBatch(action.isBatch());
                  subResources.add(subResource);
                });
      }
    }
    for (Parameter iparam : action.requestBodyParameters()) {
      if (iparam.isCompositeArrayBody()) {
        Attribute multiAttribute =
            new Attribute(iparam.getName(), iparam.schema, iparam.isRequired);
        if (!multiAttribute.isNotHiddenAttribute()) continue;
        if (!multiAttribute.attributes().isEmpty()
            && multiAttribute.attributes().stream().noneMatch(Attribute::isNotHiddenAttribute))
          continue;
        multiAttribute
            .attributes()
            .forEach(
                attribute -> {
                  String name = attribute.name;
                  if (name.equals("juris_type")) {
                    name = "Juris";
                  }
                  if (attribute.isHiddenParameter()) return;
                  if (!attribute.isNotHiddenAttribute()) return;
                  SingularSubResource subResource = new SingularSubResource();
                  subResource.setDeprecated(attribute.isDeprecated());
                  subResource.setListParam(false);
                  subResource.setReturnGeneric(getReturnGeneric(attribute));
                  subResource.setMulti(isMultiFilterAttribute(attribute));
                  subResource.setMethName(
                      getName(singularize(iparam.getName()) + "_" + attribute.name));
                  subResource.setJavaType(
                      dataTypeForMultiAttribute(attribute, iparam.getName(), action.modelName()));
                  subResource.setVarName(
                      singularize(getName(iparam.getName())) + toClazName(attribute.name));
                  subResource.setPutMethodName(attribute.isRequired ? "add" : "addOpt");
                  subResource.setResName(iparam.getName());
                  subResource.setName(attribute.name);
                  subResource.setHasBatch(action.isBatch());
                  subResource.setSortOrder(
                      sortOrder(iparam.schema.getProperties().get(attribute.name).getItems()));
                  subResources.add(subResource);
                });
      }
    }
    return subResources.stream()
        .sorted(Comparator.comparing(SingularSubResource::sortOrder))
        .toList();
  }

  public Map<String, List<SingularSubResource>> getMultiSubsForBatch(Action action) {
    List<SingularSubResource> subResources = getMultiSubs(action);
    return subResources.stream().collect(Collectors.groupingBy(SingularSubResource::getResName));
  }

  public Map<String, List<SingularSubResource>> getSingularForJsonTypeRequest(Action action) {
    List<SingularSubResource> subResources = getSingularSubs(action);
    return subResources.stream().collect(Collectors.groupingBy(SingularSubResource::getResName));
  }

  public int sortOrder(Schema schema) {
    int sortOrder =
        schema != null
                && schema.getExtensions() != null
                && schema.getExtensions().get(SORT_ORDER) != null
            ? (int) schema.getExtensions().get(SORT_ORDER)
            : -1;
    if (sortOrder == -1 && schema != null && schema.getItems() != null) {
      sortOrder =
          schema.getItems().getExtensions() != null
                  && schema.getItems().getExtensions().get(SORT_ORDER) != null
              ? (int) schema.getItems().getExtensions().get(SORT_ORDER)
              : 0;
    }
    return sortOrder;
  }

  public String dataTypeForMultiAttribute(
      Attribute attribute, String parentAttribute, String modelName) {
    String name = attribute.name;
    if (name.equals("juris_type")) {
      name = "tax_juris_type";
    }
    if (attribute.isGlobalEnumAttribute()) {
      return getPackagePrefix() + Constants.MODELS_DOT_ENUMS + toClazName(name);
    }
    if (attribute.isEnumAttribute()) {
      if (toClazName(singularize(parentAttribute)).equals(toClazName(activeResource.name))) {
        return toClazName(singularize(parentAttribute)) + "." + toClazName(name);
      }
      List<Resource> reference =
          resourceList.stream()
              .filter(r -> r.id.equalsIgnoreCase(singularize(parentAttribute)))
              .toList();
      if (!reference.isEmpty()) {
        Resource refrenceResource = reference.get(0);
        if (refrenceResource.attributes().stream().anyMatch(a -> a.name.equals(attribute.name))) {
          return toClazName(singularize(parentAttribute)) + "." + toClazName(name);
        }
      }
      if (activeResource.id.equals(Constants.CREDIT_NOTE)
          && modelName != null
          && modelName.equals("invoice")) {
        modelName = Constants.CREDIT_NOTE;
      }
      String modelResourceName = modelName;
      reference =
          resourceList.stream().filter(r -> r.id.equalsIgnoreCase(modelResourceName)).toList();
      if (!reference.isEmpty()) {
        Resource refrenceResource = reference.get(0);
        if (refrenceResource.attributes().stream().anyMatch(a -> a.name.equals(parentAttribute))) {
          return toClazName(modelResourceName)
              + "."
              + toClazName(singularize(parentAttribute))
              + "."
              + toClazName(name);
        }
      }
      return toClazName(singularize(parentAttribute)) + "." + toClazName(name);
    }

    String dataType = dataType(attribute.schema);
    if (dataType.equals(Constants.DATE_TIME)) {
      return "long";
    }
    return dataType;
  }

  private List<OperationRequest> getOperRequestClasses(Resource res) {
    List<OperationRequest> operationRequests = new ArrayList<>();
    for (Action action : res.getSortedAction()) {
      if (action.id.contains(Constants.BATCH) && !action.isContentTypeJsonAction()) continue;
      OperationRequest operationRequest = new OperationRequest();
      operationRequest.setClazName(getClazName(action));
      operationRequest.setSuperClazName(getSuperClazName(action));
      operationRequest.setHasBatch(action.isBatch());
      operationRequest.setSubDomain(action.subDomain());
      operationRequest.setJsonRequest(action.isContentTypeJsonAction());
      operationRequest.setList(action.isListResourceAction());
      operationRequest.setParams(getOperationParams(action));
      operationRequest.setSingularSubs(getSingularSubs(action));
      operationRequest.setMultiSubs(getMultiSubs(action));
      if (!action.isIdempotent() && action.httpRequestType.equals(HttpRequestType.POST)) {
        operationRequest.setIdempotent(action.isIdempotent());
      }
      operationRequest.setSingularSubsForJsonRequest(getSingularForJsonTypeRequest(action));
      operationRequest.setMultiSubsForBatch(
          getMultiSubsForBatch(action)); // for batch there will be only one top level multiSubs
      operationRequest.setRawOperationName(GenUtil.toClazName(action.name));
      if (operationRequest.getParams().isEmpty()
          && operationRequest.getSingularSubs().isEmpty()
          && operationRequest.getMultiSubs().isEmpty()) {
        continue;
      }
      operationRequests.add(operationRequest);
    }
    return operationRequests;
  }

  public String getReturnGenericForSingularSubParams(Parameter parameter) {
    return switch (dataType(parameter.schema).toLowerCase()) {
      case Constants.TIMESTAMP, Constants.STRING_TYPE, Constants.BOOLEAN_TYPE ->
          getClazName(parameter);
      default -> dataType(parameter.schema) + ", " + getClazName(parameter);
    };
  }

  public String dataTypeForSingleSubAttribute(Attribute attribute, String parentAttribute) {
    if (attribute.isGlobalEnumAttribute()) {
      return getPackagePrefix() + Constants.MODELS_DOT_ENUMS + toClazName(attribute.name);
    }
    if (attribute.isDeprecated() && attribute.isEnumAttribute()) {
      return toClazName(attribute.name);
    }
    boolean isResourceLevelEnum =
        activeResource.attributes().stream().anyMatch(a -> a.name.equals(parentAttribute));
    if (attribute.isEnumAttribute() && isResourceLevelEnum) {
      if (attribute.metaModelName() != null) {
        return Inflector.capitalize(
            getName(singularize(attribute.metaModelName()) + "_._" + attribute.name));
      }
      return Inflector.capitalize(
          getName(activeResource.name + "_" + parentAttribute + "_._" + attribute.name));
    }
    if (attribute.isEnumAttribute()) {
      for (Resource res : resourceList) {
        if (res.name.equals(toClazName(parentAttribute))) {
          for (Attribute attr : res.attributes()) {
            if (!attr.isNotHiddenAttribute()) continue;
            if (attr.isApi() && !attr.isGenSeparate() && attribute.name.equals(attr.name)) {
              return Inflector.capitalize(getName(parentAttribute + "_._" + attribute.name));
            }
            if (attr.isSubResource()
                && !attr.isDependentAttribute()
                && !Resource.isGlobalResourceReference(attr.schema)) {
              String SubResName =
                  attr.schema instanceof ArraySchema
                      ? singularize((attr.name))
                      : attr.subResourceName();
              for (Attribute enumAttribute : attr.attributes()) {
                if (!enumAttribute.isNotHiddenAttribute()) continue;
                if (enumAttribute.isApi()
                    && !enumAttribute.isExternalEnum()
                    && attribute.name.equals(enumAttribute.name)) {
                  return Inflector.capitalize(
                      getName(
                          parentAttribute
                              + "_._"
                              + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, SubResName)
                              + "_._"
                              + attribute.name));
                }
              }
            }
          }
        }
      }
      return Inflector.capitalize(getName(parentAttribute + "_._" + attribute.name));
    }
    String dataType = dataType(attribute.schema);
    if (dataType.equals(Constants.DATE_TIME)) {
      return "long";
    }
    return dataType;
  }

  public List<SingularSubResource> getSingularSubs(Action action) {
    List<SingularSubResource> subResources = new ArrayList<>();
    for (Parameter iParam : action.queryParameters()) {
      if ((iParam.schema instanceof ObjectSchema)
          && iParam.schema.getProperties() != null
          && iParam.schema.getItems() == null
          && !iParam.isCompositeArrayBody()) {
        Attribute attribute = new Attribute(iParam.getName(), iParam.schema, iParam.isRequired);
        if (attribute.isFilterAttribute()) continue;
        if (attribute.name.equals(Constants.SORT_BY)) continue;
        attribute
            .attributes()
            .forEach(
                value -> {
                  if (!value.isNotHiddenAttribute()) return;
                  SingularSubResource subResource = new SingularSubResource();
                  subResource.setDeprecated(value.isDeprecated());
                  subResource.setListParam(false);
                  subResource.setReturnGeneric(getReturnGenericForSingularSubParams(iParam));
                  subResource.setMulti(isMultiFilterAttribute(attribute));
                  subResource.setMethName(
                      getName(Inflector.capitalize(iParam.getName()))
                          + Inflector.capitalize(toCamelCase(value.name)));
                  subResource.setJavaType(dataTypeForSingleSubAttribute(value, iParam.getName()));
                  subResource.setHasBatch(action.isBatch());
                  subResource.setVarName(
                      GenUtil.getVarName(iParam.getName() + Inflector.capitalize(value.name)));
                  subResource.setPutMethodName(
                      getPutMethodName(value.isRequired || value.isAttributeMetaCommentRequired()));
                  subResource.setResName(iParam.getName());
                  subResource.setName(value.name);
                  subResource.setSortOrder(value.sortOrder());
                  subResources.add(subResource);
                });
      }
    }
    for (Parameter iParam : action.requestBodyParameters()) {
      if ((iParam.schema instanceof ObjectSchema)
          && iParam.schema.getProperties() != null
          && iParam.schema.getItems() == null
          && !iParam.isCompositeArrayBody()) {
        Attribute attribute = new Attribute(iParam.getName(), iParam.schema, iParam.isRequired);
        if (attribute.isFilterAttribute()) continue;
        attribute
            .attributes()
            .forEach(
                value -> {
                  if (!value.isNotHiddenAttribute()) return;
                  SingularSubResource subResource = new SingularSubResource();
                  subResource.setDeprecated(value.isDeprecated());
                  subResource.setListParam(false);
                  subResource.setReturnGeneric(getReturnGenericForSingularSubParams(iParam));
                  subResource.setMulti(isMultiFilterAttribute(attribute));
                  subResource.setMethName(
                      getName(Inflector.capitalize(iParam.getName()))
                          + Inflector.capitalize(toCamelCase(value.name)));
                  subResource.setJavaType(dataTypeForSingleSubAttribute(value, iParam.getName()));
                  subResource.setHasBatch(action.isBatch());
                  subResource.setVarName(
                      GenUtil.getVarName(iParam.getName() + Inflector.capitalize(value.name)));
                  subResource.setPutMethodName(
                      getPutMethodName(value.isRequired || value.isAttributeMetaCommentRequired()));
                  subResource.setResName(iParam.getName());
                  subResource.setName(value.name);
                  subResource.setSortOrder(value.sortOrder());
                  subResources.add(subResource);
                });
      }
    }
    return subResources.stream()
        .sorted(Comparator.comparing(SingularSubResource::sortOrder))
        .toList();
  }

  private List<Column> getResourceCols(Resource res) {
    List<Column> cols = new ArrayList<>();
    for (Attribute attribute : res.getSortedResourceAttributes()) {
      if (attribute.isContentObjectAttribute()) continue;
      Column column = new Column();
      column.setDeprecated(attribute.isDeprecated());
      column.setJavaType(getColsRetType(attribute));
      column.setGetterCode(getGetterCode(attribute));
      column.setSubResource(attribute.isSubResource());
      column.setMethName(getName(attribute.name));
      cols.add(column);
    }
    return cols;
  }

  private List<Operation> getOperationList(Resource res) {
    List<Operation> operations = new ArrayList<>();
    for (Action action : res.getSortedAction()) {
      Operation operation = new Operation();
      operation.setHandleArg(getHandleArg(action));
      operation.setUrlArgs(getUrlArgs(action));
      operation.setDeprecated(action.isOperationDeprecated());
      operation.setRetType(getRetType(action));
      operation.setMethName(getMethNameForOperation(action));
      operation.setReqCreationCode(getReqCreationCode(action));
      operations.add(operation);
    }
    return operations;
  }

  private String getMethNameForOperation(Action action) {
    if (action.id.contains(Constants.BATCH) && !action.isContentTypeJsonAction()) {
      return GenUtil.getVarName("batch_" + action.name);
    }
    // to be discussed with the respective teams.
    if (activeResource.id.equals("full_export") && action.name.equals("status")) {
      return "statusRequest";
    }
    return firstCharLower(toCamelCase(action.name));
  }

  private String getRetType(Action action) {
    boolean isCodeGen = isCodeGen(action);
    if (action.id.contains(Constants.BATCH) && !action.isContentTypeJsonAction()) {
      return "BatchRequest";
    }
    if (action.isInputObjNeeded() && isCodeGen) {
      return getClazName(action);
    } else {
      return action.isListResourceAction() ? "ListRequest" : Constants.REQUEST;
    }
  }

  public String getClazName(Action action) {
    String methodName = action.name;
    if (action.isListResourceAction()) {
      methodName = action.resourceId() + "_" + action.name;
    }
    return GenUtil.toClazName(methodName, Constants.REQUEST);
  }

  public String getReqCreationCode(Action action) {
    boolean isCodeGen = isCodeGen(action);
    StringBuilder reqCodeBuffer = new StringBuilder();
    reqCodeBuffer.append("return new ");
    if (action.isBatch() && action.isContentTypeJsonAction()) {
      reqCodeBuffer
          .append(getClazName(action))
          .append("(Method.")
          .append(action.httpRequestType)
          .append(", uri");
      if (action.batchId() != null && !Objects.equals(action.batchId(), "")) {
        reqCodeBuffer.append(", \"").append(action.batchId()).append("\"");
      }
      reqCodeBuffer.append(")");
      if (!action.isIdempotent() && action.httpRequestType.equals(HttpRequestType.POST)) {
        reqCodeBuffer.append(".setIdempotency(false)");
      }
      return reqCodeBuffer.toString();
    }
    if (action.id.contains(Constants.BATCH)) {
      reqCodeBuffer.append("BatchRequest(Method.").append((action.httpRequestType)).append(", uri");
      if (action.batchId() != null) {
        reqCodeBuffer.append(", \"").append(action.batchId()).append("\"");
      }
      reqCodeBuffer.append(")");
      if (!action.isIdempotent() && action.httpRequestType.equals(HttpRequestType.POST)) {
        reqCodeBuffer.append(".setIdempotency(false)");
      }
      return reqCodeBuffer.toString();
    }
    if (action.isInputObjNeeded() && isCodeGen) {
      reqCodeBuffer.append(getClazName(action)).append("(");
      if (!action.isListResourceAction()) {
        reqCodeBuffer.append("Method.").append(action.httpRequestType).append(", ");
      }
      reqCodeBuffer.append("uri");
      if (action.isBatch() && !action.pathParameters().isEmpty()) {
        reqCodeBuffer.append(", nullCheckWithoutEncoding(id)");
      }
      reqCodeBuffer.append(")");
      if (!action.isIdempotent() && action.httpRequestType.equals(HttpRequestType.POST)) {
        reqCodeBuffer.append(".setIdempotency(false)");
      }
      return reqCodeBuffer.toString();
    } else {
      if (action.isListResourceAction()) {
        return reqCodeBuffer.append("ListRequest(uri)").toString();
      } else {
        reqCodeBuffer.append("Request(Method.").append(action.httpRequestType).append(", uri");
        if (action.isBatch() && !action.pathParameters().isEmpty()) {
          reqCodeBuffer.append(", nullCheckWithoutEncoding(id)");
        }
        reqCodeBuffer.append(")");
        if (!action.isIdempotent() && action.httpRequestType.equals(HttpRequestType.POST)) {
          reqCodeBuffer.append(".setIdempotency(false)");
        }
        return reqCodeBuffer.toString();
      }
    }
  }

  private boolean isCodeGen(Action action) {
    return action.isOperationDeprecated()
        ? (action.hasInputParamsForEAPClientLibs() || action.hasInputParamsForClientLibs())
        : action.hasInputParamsForClientLibs();
  }

  public String getUrlArgs(Action action) {
    if (action.getUrl().startsWith("/batch") && generationMode.equals(GenerationMode.INTERNAL)) {
      action.setUrl(action.getUrl().replaceAll("/batch", ""));
    }
    StringBuilder buf = new StringBuilder();
    if (action.getUrl().startsWith("/async/")) {
      buf.append('"')
          .append(action.getUrl().split("/")[1])
          .append('/')
          .append(action.getUrl().split("/")[2])
          .append('"');
    } else {
      buf.append('"').append(action.getUrl().split("/")[1]).append('"');
    }
    for (Path comp : action.pathParameters()) {
      buf.append(", ");
      if (comp.isRequired) {
        buf.append("nullCheck(id)");
      } else {
        buf.append('"').append(comp.getName()).append('"');
      }
    }
    for (int i = 0; i < action.getUrl().split("/").length; i++) {
      var path = action.getUrl().split("/")[i];
      if (path.isEmpty()) continue;
      if (path.equals(action.getUrl().split("/")[1])) continue;
      if (action.getUrl().startsWith("/async/") && path.equals(action.getUrl().split("/")[2]))
        continue;
      if (action.pathParameters().stream()
          .map(p -> "{" + p.getName() + "}")
          .toList()
          .contains(path)) continue;
      buf.append(", ");
      if (action.getUrl().split("/").length > 4 && i == 3) {
        buf.append('"').append(path).append("/").append(action.getUrl().split("/")[4]).append('"');
        break;
      } else buf.append('"').append(path).append('"');
    }
    return buf.toString();
  }

  public String getHandleArg(Action action) {
    return !action.pathParameters().isEmpty() ? "String id" : "";
  }

  private FileOp generateResultBaseFile(String outputDirectory, List<Resource> resources)
      throws IOException {
    Map<String, Object> templateParams = resourceResponses(resources);
    Template resultBaseTemplate = getTemplateContent("resultBase");
    return new FileOp.WriteString(
        outputDirectory, "ResultBase.java", resultBaseTemplate.apply(templateParams));
  }

  private Map<String, Object> globalEnumTemplate(Enum e) {
    return Map.of(
        "name",
        e.name,
        "possibleValues",
        e.validValues().stream().map(value -> Map.of("name", value.toUpperCase())).toList(),
        "deprecatedEnums",
        e.deprecatedValues().size() > 0
            ? e.deprecatedValues().stream()
                .map(value -> Map.of("name", value.toUpperCase()))
                .toList()
            : false,
        "IsParamBlankOption",
        (e.isParamBlankOption() && !generationMode.equals(GenerationMode.INTERNAL)),
        "isApiV2",
        generationMode.equals(GenerationMode.INTERNAL) && apiVersion.equals(ApiVersion.V2));
  }

  private boolean isMoneyColumn(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_MONEY_COLUMN) != null
        && (boolean) schema.getExtensions().get(IS_MONEY_COLUMN);
  }

  private boolean isLongMoneyColumn(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_LONG_MONEY_COLUMN) != null
        && (boolean) schema.getExtensions().get(IS_LONG_MONEY_COLUMN);
  }

  @Override
  public String dataType(Schema schema) {
    if (schema instanceof StringSchema
        || schema instanceof EmailSchema
        || schema instanceof PasswordSchema) {
      if (schema.getEnum() != null) {
        return new JavaHelper().typeOfEnum(schema);
      }
      return "String";
    }
    if (schema.getType() == null) {
      return "JSONObject";
    }
    if (schema.getType().equals(Constants.STRING_TYPE) && schema.getFormat().equals("date")) {
      return "Date";
    }
    if (schema instanceof NumberSchema || schema instanceof IntegerSchema) {
      if (schema.getType().equals("integer")) {
        if (schema.getFormat().equals("decimal")) {
          return "BigDecimal";
        }
        if (isMoneyColumn(schema) && !isLongMoneyColumn(schema)) {
          if (generationMode.equals(GenerationMode.INTERNAL) && jarType.equals(JarType.INT)) {
            return Constants.INT_TYPE;
          }
          return "Long";
        }
        if (schema.getFormat().equals("int32")) {
          return Constants.INT_TYPE;
        } else if (schema.getFormat().equals("int64")) {
          return "Long";
        } else if (schema.getFormat().equals("unix-time")) {
          return "Timestamp";
        }
      }
      if (schema.getType().equals(Constants.NUMBER_TYPE)) {
        if (schema.getFormat().equals("double")) {
          return "Double";
        }
        if (schema.getFormat().equals("decimal")) {
          return "BigDecimal";
        }
      }
      return Constants.INT_TYPE;
    }
    if (schema instanceof BooleanSchema) {
      return "Boolean";
    }
    if (schema instanceof ObjectSchema && GenUtil.hasAdditionalProperties(schema)) {
      if (activeResource.hasContentTypeJsonAction()) {
        return "Map<String, Object>";
      } else {
        return "JSONObject";
      }
    }
    if (schema instanceof ArraySchema && schema.getItems() != null) {
      if (schema.getItems().getType() == null) {
        return "JSONArray";
      }
      return String.format("%s", dataType(schema.getItems()));
    }
    if (schema instanceof ObjectSchema) {
      if (schema.getExtensions() != null
          && schema.getExtensions().get(IS_PARAMETER_BLANK_OPTION) != null
          && schema.getExtensions().get(IS_PARAMETER_BLANK_OPTION).equals("as_empty")) {
        return "String";
      }
      String filter = new Attribute(schema.getName(), schema, false).getFilterType();
      if (filter == null) return "StringFilter";
      return filter;
    }
    return "any";
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
