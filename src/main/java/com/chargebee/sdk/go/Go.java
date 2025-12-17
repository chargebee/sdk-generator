package com.chargebee.sdk.go;

import static com.chargebee.GenUtil.*;
import static com.chargebee.openapi.Extension.*;
import static com.chargebee.openapi.Resource.isListOfSubResourceSchema;
import static com.chargebee.openapi.Resource.isSubResourceSchema;
import static com.chargebee.sdk.common.Constant.*;
import static com.chargebee.sdk.go.Formatter.delimiter;
import static com.chargebee.sdk.go.Formatter.formatUsingDelimiter;

import com.chargebee.GenUtil;
import com.chargebee.handlebar.Inflector;
import com.chargebee.openapi.*;
import com.chargebee.openapi.Enum;
import com.chargebee.openapi.parameter.Parameter;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.common.ActionAssist;
import com.chargebee.sdk.common.GlobalEnum;
import com.chargebee.sdk.common.ResourceAssist;
import com.chargebee.sdk.dotnet.models.OperationRequest;
import com.chargebee.sdk.dotnet.models.OperationRequestParameter;
import com.chargebee.sdk.go.model.InputSubResParam;
import com.chargebee.sdk.go.model.ResponseParser;
import com.chargebee.sdk.go.model.SubResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Go extends Language {

  Resource activeResource;

  List<Resource> resourceList = new ArrayList<>();
  List<String> enumImport = new ArrayList<>();

  public static String getJsonVal(Attribute a, boolean req) {
    String json = "`json:\"";
    if (!req) {
      json = json + a.name + ",omitempty\"`";
    } else {
      json = json + a.name + "\"`";
    }
    return json;
  }

  @Override
  public List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    var resources =
        spec.resources().stream()
            .filter(resource -> !Arrays.stream(this.hiddenOverride).toList().contains(resource.id))
            .toList();
    this.resourceList = resources;
    List<FileOp> fileOps = new ArrayList<>();

    fileOps.add(new FileOp.CreateDirectory(outputDirectoryPath, ""));
    fileOps.addAll(generateServices(outputDirectoryPath, resources));
    fileOps.addAll(genModels(outputDirectoryPath, resources, spec));
    return fileOps;
  }

  private boolean hasJSONObjectCols(Resource r) {
    boolean hasJsonAttr =
        r.attributes().stream()
            .filter(com.chargebee.openapi.Attribute::isNotHiddenAttribute)
            .anyMatch(a -> dataType(a.schema, a.name).equals(Constants.JSON_RAW_MESSAGE));

    boolean hasJsonSubResAttr =
        !r.subResources().stream()
            .filter(
                sr ->
                    sr.attributes().stream()
                        .filter(com.chargebee.openapi.Attribute::isNotHiddenAttribute)
                        .anyMatch(
                            a -> dataType(a.schema, a.name).equals(Constants.JSON_RAW_MESSAGE)))
            .toList()
            .isEmpty();

    return hasJsonAttr || hasJsonSubResAttr;
  }

  public boolean hasExternalEnumCols(Resource res) {
    List<Attribute> extCols = res.getSortedResourceAttributes();
    for (Attribute attribute : extCols) {
      if (attribute.isEnumAttribute() && attribute.isGenSeparate()) {
        return true;
      }
    }
    for (Action action : res.getSortedAction()) {
      for (Parameter requestParams : action.requestBodyParameters()) {
        com.chargebee.openapi.Attribute attribute =
            new Attribute(requestParams.getName(), requestParams.schema, requestParams.isRequired);
        if (attribute.isEnumAttribute() && attribute.isExternalEnum()) {
          return true;
        }
        for (Attribute subAttribute : attribute.attributes()) {
          if (subAttribute.isEnumAttribute() && subAttribute.isGenSeparate()) {
            return true;
          }
        }
      }
      for (Parameter requestParams : action.queryParameters()) {
        com.chargebee.openapi.Attribute attribute =
            new Attribute(requestParams.getName(), requestParams.schema, requestParams.isRequired);
        if (attribute.isEnumAttribute() && attribute.isExternalEnum()) {
          return true;
        }
      }
    }
    for (Attribute subResourceAttribute : res.getSortedResourceAttributes()) {
      if (subResourceAttribute.isSubResource() && !subResourceAttribute.isDependentAttribute()) {
        for (Attribute attribute : subResourceAttribute.attributes()) {
          if (attribute.isEnumAttribute() && attribute.isGenSeparate()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void addEnumImport(String enumName) {
    enumImport.add(enumName);
  }

  private List<String> organizeImportEnums() {
    List<String> consolidatedEnums = new ArrayList<>();
    Pattern pattern = Pattern.compile("(\\w+)Enum");
    enumImport.forEach(
        a -> {
          Matcher matcher = pattern.matcher(a);
          if (matcher.find() && !consolidatedEnums.contains(matcher.group(1)))
            consolidatedEnums.add(matcher.group(1));
        });
    return consolidatedEnums.stream().toList();
  }

  private String getImportFiles() {
    List<String> consolidatedEnum = organizeImportEnums();
    StringJoiner buf = new StringJoiner("\n");
    if (hasJSONObjectCols(activeResource)) {
      buf.add("\t\"encoding/json\"");
    }
    // if (hasExternalEnumCols(activeResource)) {
    //   buf.add("\t\"github.com/chargebee/chargebee-go/v3/enum\"");
    // }
    // if (!getOperRequestClasses(activeResource).isEmpty()
    //     && (hasFilterParams() || isExportResource())) {
    //   buf.add("\t\"github.com/chargebee/chargebee-go/v3/filter\"");
    // }
    // if (!getDependentResource(activeResource).isEmpty()) {
    //   for (Attribute dr : getDependentResource(activeResource)) {
    //     var refModelName = singularize(dr.name.replace("_", ""));
    //     if (dr.isDependentAttribute()
    //         && resourceList.stream().noneMatch(r -> r.id.contains(singularize(dr.name)))) {
    //       if (dr.subResourceName() != null) {
    //         refModelName = dr.subResourceName().toLowerCase();
    //       } else {
    //         refModelName = refModelName.replace(activeResource.id, "");
    //       }
    //     }
    //     buf.add("\t\"github.com/chargebee/chargebee-go/v3/" + refModelName + "\"");
    //   }
    // }
    // if (!getSubResource(activeResource).isEmpty()) {
    //   for (Attribute sr : getSubResource(activeResource)) {
    //     for (Attribute nestedSubRes :
    //         sr.attributes().stream()
    //             .filter(
    //                 (attr ->
    //                     attr.isDependentAttribute()
    //                         || attr.isGlobalResourceReference()
    //                             && attr.name != "error_detail")) // error detail is handled below
    //             .toList()) {
    //       buf.add(
    //           "\t\"github.com/chargebee/chargebee-go/v3/"
    //               + singularize(nestedSubRes.name.replace("_", "").toLowerCase())
    //               + "\"");
    //     }
    //   }
    // }
    // if (activeResource.name.equalsIgnoreCase("PaymentIntent")) {
    //   buf.add("\t\"github.com/chargebee/chargebee-go/v3/gatewayerrordetail\"");
    // }
    // String currentPkg =
    //     CaseFormat.UPPER_CAMEL
    //         .to(CaseFormat.LOWER_UNDERSCORE, activeResource.name)
    //         .replace("_", "");
    // consolidatedEnum.forEach(
    //     a -> {
    //       if (!getCamelClazName(a).toLowerCase().equals(currentPkg)) {
    //         buf.add(
    //             "\t"
    //                 + getCamelClazName(a)
    //                 + Constants.ENUM_MODEL_IMPORT
    //                 + getCamelClazName(a).toLowerCase()
    //                 + Constants.ENUM);
    //       }
    //     });
    if (buf.toString().isEmpty()) {
      return "";
    } else {
      return "\nimport (\n" + buf + "\n)" + "\n";
    }
  }

  public String getCamelClazName(String name) {
    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, name);
  }

  private List<Attribute> getDependentResource(Resource resource) {
    return resource.attributes().stream()
        .filter(com.chargebee.openapi.Attribute::isDependentAttribute)
        .toList();
  }

  private List<Attribute> getSubResource(Resource resource) {
    return resource.attributes().stream()
        .filter(com.chargebee.openapi.Attribute::isSubResource)
        .toList();
  }

  public Boolean isExportResource() {
    return "Export".equals(activeResource.name);
  }

  public boolean hasFilterParams() {
    List<OperationRequest> op = getOperRequestClasses(activeResource);
    for (OperationRequest oper : op) {
      for (OperationRequestParameter p : oper.getParams()) {
        if (p.isListParam()) {
          return true;
        }
      }
    }
    return false;
  }

  private List<OperationRequest> getOperRequestClasses(Resource res) {
    List<OperationRequest> operationRequests = new ArrayList<>();
    for (Action action : res.getSortedAction()) {
      OperationRequest operationRequest = new OperationRequest();
      operationRequest.setParams(getOperationParams(action));
      operationRequests.add(operationRequest);
    }
    return operationRequests;
  }

  private String getName(String name) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
  }

  public String dataTypeForForFilterAttribute(Attribute attribute) {
    if (!attribute.attributes().isEmpty()
        && attribute.attributes().stream().anyMatch(a -> a.schema instanceof ObjectSchema)) {
      attribute = attribute.attributes().get(0);
    }
    if (attribute.isFilterAttribute() && attribute.schema instanceof ObjectSchema) {
      return dataType(attribute.schema, attribute.name);
    }
    if (attribute.isFilterAttribute()) {
      return attribute.getFilterType();
    }
    if (attribute.isEnumAttribute()) {
      if (attribute.isDeprecated()) {
        return getName(attribute.name + Constants.UNDERSCORE_ENUM);
      }
      if (attribute.isGlobalEnumAttribute()) {
        return "ChargeBee.Models.Enums." + getName(attribute.name + Constants.UNDERSCORE_ENUM);
      }

      return (activeResource.name) + "." + getName(attribute.name + Constants.UNDERSCORE_ENUM);
    }
    return dataType(attribute.schema, attribute.name);
  }

  private boolean isMoneyColumn(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_MONEY_COLUMN) != null
        && (boolean) schema.getExtensions().get(IS_MONEY_COLUMN);
  }

  private boolean isMultiFilterAttribute(Parameter queryParameter) {
    Attribute attribute =
        new Attribute(queryParameter.getName(), queryParameter.schema, queryParameter.isRequired);
    return attribute.isMultiAttribute();
  }

  private void handleOperationListAttribute(
      Action action,
      Parameter queryParameter,
      List<OperationRequestParameter> operationRequestParameters,
      String parentName) {
    Attribute attribute =
        new Attribute(queryParameter.getName(), queryParameter.schema, queryParameter.isRequired);
    if (queryParameter.isHiddenFromSDK()) return;
    if (queryParameter.isPaginationProperty()) return;
    if (!attribute.attributes().isEmpty()
        && attribute.attributes().stream()
            .noneMatch(com.chargebee.openapi.Attribute::isNotHiddenAttribute)) return;
    OperationRequestParameter operationRequestParameter = new OperationRequestParameter();
    operationRequestParameter.setHidden(queryParameter.isHiddenFromSDK());
    operationRequestParameter.setDeprecated(queryParameter.isDeprecated());
    operationRequestParameter.setListParam(
        attribute.isFilterAttribute() && !attribute.isEnumAttribute());
    operationRequestParameter.setListType(dataTypeForForFilterAttribute(attribute));
    operationRequestParameter.setDotNetType(dataTypeForForFilterAttribute(attribute));
    operationRequestParameter.setVarName(GenUtil.getVarName(queryParameter.getName()));
    if (attribute.attributes().isEmpty()
        || attribute.attributes().stream().noneMatch(a -> a.schema instanceof ObjectSchema)) {
      if (parentName == null) {
        operationRequestParameter.setDotNetMethName(
            Inflector.capitalize(getName(queryParameter.getName())));
        operationRequestParameter.setName(queryParameter.getName());
      } else {
        operationRequestParameter.setDotNetMethName(
            Inflector.capitalize(getName(parentName)) + toClazName(queryParameter.getName()));
        operationRequestParameter.setName(parentName + "[" + queryParameter.getName() + "]");
      }
    }
    operationRequestParameter.setMulti(isMultiFilterAttribute(queryParameter));
    operationRequestParameter.setSupportsPresenceFilter(attribute.isPresenceOperatorSupported());
    if (attribute.name.equals(Constants.SORT_BY)) {
      operationRequestParameter.setListParam(true);
      operationRequestParameter.setSortParam(true);
    }
    if (!(attribute.attributes().isEmpty()
        || attribute.attributes().stream().noneMatch(a -> a.schema instanceof ObjectSchema))) {
      for (Attribute subAttribute : attribute.attributes()) {
        handleOperationListAttribute(
            action,
            new Parameter(subAttribute.name, subAttribute.schema, subAttribute.isRequired),
            operationRequestParameters,
            queryParameter.getName());
      }
    } else {
      operationRequestParameters.add(operationRequestParameter);
    }
  }

  private List<OperationRequestParameter> getOperationParams(Action action) {
    List<OperationRequestParameter> operationRequestParameters = new ArrayList<>();
    for (Parameter queryParameter : action.queryParameters()) {
      handleOperationListAttribute(action, queryParameter, operationRequestParameters, null);
    }
    for (Parameter requestBodyParameter : action.requestBodyParameters()) {
      Attribute attribute =
          new Attribute(
              requestBodyParameter.getName(),
              requestBodyParameter.schema,
              requestBodyParameter.isRequired);
      if (requestBodyParameter.isHiddenFromSDK()) continue;
      if (requestBodyParameter.isCompositeArrayBody()) continue;
      if (requestBodyParameter.isCompositeArrayBody()) continue;
      if (!attribute.isFilterAttribute() && !attribute.attributes().isEmpty()) continue;
      if (attribute.isFilterAttribute()) {
        handleOperationListAttribute(
            action, requestBodyParameter, operationRequestParameters, null);
      } else {
        OperationRequestParameter operationRequestParameter = new OperationRequestParameter();
        operationRequestParameter.setHidden(requestBodyParameter.isHiddenFromSDK());
        operationRequestParameter.setDeprecated(requestBodyParameter.isDeprecated());
        operationRequestParameter.setDotNetMethName(
            Inflector.capitalize(getVarName(requestBodyParameter.getName())));
        operationRequestParameter.setVarName(GenUtil.getVarName(requestBodyParameter.getName()));
        operationRequestParameter.setName(requestBodyParameter.getName());
        operationRequestParameter.setSupportsPresenceFilter(
            attribute.isPresenceOperatorSupported());
        operationRequestParameter.setMulti(isMultiFilterAttribute(requestBodyParameter));
        operationRequestParameters.add(operationRequestParameter);
      }
    }
    return operationRequestParameters;
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of(
        "globalEnums",
        "/templates/go/globalEnums.go.hbs",
        "enums",
        "/templates/go/enums.go.hbs",
        "actions",
        "/templates/go/actions.go.hbs",
        "result",
        "/templates/go/result.go.hbs",
        "models",
        "/templates/go/models.go.hbs",
        "responses",
        "/templates/go/responses.go.hbs",
        "exceptions",
        "/templates/go/api_error.go.hbs");
  }

  private FileOp generateGlobalEnumFiles(String outDirectoryPath, List<Enum> globalEnums) throws IOException {
    Template globalEnumTemplate = getTemplateContent("globalEnums");
    List<Map<String, Object>> enumList = globalEnums.stream()
      .sorted(Comparator.comparing(e -> e.name))
      .map(this::globalEnumTemplate).toList();
    var content = globalEnumTemplate.apply(Map.of("globalEnums", enumList));
    return new FileOp.WriteString(outDirectoryPath, "types.go", content);
  }

  private Map<String, Object> globalEnumTemplate(Enum e) {
    return new GlobalEnum(e).template();
  }

  private Map<String, Object> resourceEnumTemplate(Enum e, String resourceName) {
    var _enum = new GlobalEnum(e);
    _enum.setResourceName(resourceName);
    return _enum.template();
  }

  private List<FileOp> generateServices(
      String outputDirectoryPath, List<Resource> resources) throws IOException {
    List<FileOp> fileOps = new ArrayList<>();

    Template actionTemplates = getTemplateContent("actions");
    for (var resource : resources) {
      if (!resource.actions.isEmpty()) {
        var actionPayload = resource.templateParams(this);
        List<Map<String, Object>> actions =
            (List<Map<String, Object>>) actionPayload.get("actions");
        List<Map<String, Object>> mutableActions = new ArrayList<>();

        for (Map<String, Object> action : actions) {
          Map<String, Object> mutableAction = new HashMap<>(action);
          mutableAction.put("goParamName", toCamelCase((String) action.get("name")));
          mutableAction.put("goActionName", toCamelCase((String) action.get("name")));
          mutableActions.add(mutableAction);
        }

        actionPayload.put("actions", mutableActions);
        if (mutableActions.isEmpty()) continue;
        var content = actionTemplates.apply(actionPayload);
        String dirName =
            CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, resource.name).replace("_", "");
        String fileName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, resource.name);
        fileOps.add(
            new FileOp.WriteString(outputDirectoryPath, fileName + "_service.go", content));
      }
    }
    return fileOps;
  }

  private List<FileOp> genModels(String outputDirectoryPath, List<Resource> resources, Spec spec)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();

    Template modelTemplate = getTemplateContent("models");
    Template responseTemplate = getTemplateContent("responses");
    for (var res : resources) {
      if (SDK_DEBUG && !DEBUG_RESOURCE.contains(res.name)) continue;
      activeResource = res;

      StringBuffer buf = new StringBuffer(Constants.ROOT_PACKAGE);
      buf.append(getImportFiles());
      // String resourceDirName =
      //     CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, res.name).replace("_", "");

      // enums
      List<Enum> resourceAssistEnums = new ResourceAssist().setResource(res).goEnums();
      List<Enum> schemaLessEnums = SchemaLessEnumParser.getSchemalessEnum(res, resourceList);
      if (!resourceAssistEnums.isEmpty() || !schemaLessEnums.isEmpty()) {
        buf.append(genModelEnums(res.name, resourceAssistEnums, schemaLessEnums));
      }


      enumImport.clear();
      // main resource
      com.chargebee.sdk.go.model.Resource goRes = new com.chargebee.sdk.go.model.Resource();
      goRes.setPkgName(
          CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, res.name).replace("_", ""));
      goRes.setClazName(res.name);
      goRes.setCols(getCols());

      List<SubResource> subResourceList = new ArrayList<>();
      for (Resource subResource : activeResource.subResources()) {
        String clazName = singularize(subResource.name);
        boolean alreadyExists =
            subResourceList.stream().anyMatch(sr -> sr.getClazName().equals(clazName));
        if (alreadyExists) continue;
        SubResource sr = new SubResource();
        sr.setClazName(clazName);
        sr.setCols(getSubResourceCols(subResource));
        subResourceList.add(sr);
      }
      goRes.setSubResources(subResourceList);

      // operations
      List<com.chargebee.sdk.go.model.Operation> operations = new ArrayList<>();
      for (Action action : activeResource.getSortedAction()) {
        com.chargebee.sdk.go.model.Operation operation =
            new com.chargebee.sdk.go.model.Operation();
        List<InputSubResParam> inputSubResParamList = new ArrayList<>();
        operation.setClazName(toClazName(action.name, "Request"));
        operation.setHasInputParams(hasInputParams(action));
        operation.setInputParams(inputParams(action));
        operation.setHttpRequestType(action.httpRequestType.toString());

        ResponseParser goOperationResponse =
            new ResponseParser(
                action, activeResource.name, action.response().responseParameters(this));
        com.chargebee.sdk.go.model.Response goResponse =
            new com.chargebee.sdk.go.model.Response();
        goResponse.setClassName(goOperationResponse.className());
        goResponse.setResponseParams(goOperationResponse.actionResponseParams());
        goResponse.setSubResponseClassName(goOperationResponse.subResponseClassName());
        goResponse.setSubResponseParams(goOperationResponse.subResponseParams());
        operation.setOperationResponses(goResponse);

        ActionAssist actionAssist =
            ActionAssist.of(action)
                .withFlatMultiAttribute(false)
                .withPagination(true)
                .withFilterSubResource(true);
        for (Attribute subResource : actionAssist.consolidatedSubParams()) {
          InputSubResParam inputSubResParam = new InputSubResParam();
          inputSubResParam.setMethodName(toCamelCase(action.name));
          inputSubResParam.setCamelSingularResName(toCamelCase(singularize(subResource.name)));
          inputSubResParam.setCamelResName(toCamelCase(subResource.name));
          inputSubResParam.setMulti(subResource.isCompositeArrayRequestBody());
          inputSubResParam.setSubParams(subParams(subResource));
          inputSubResParamList.add(inputSubResParam);
        }
        operation.setInputSubResParams(inputSubResParamList);
        operations.add(operation);
      }
      goRes.setOperations(operations);
      goRes.setImportFiles(getImportFiles());
      // if (goRes.getImportFiles() != null && !goRes.getImportFiles().isEmpty()) {
      //   buf.insert(0, goRes.getImportFiles());
      // }
      goRes.setResponseImports(getResponseImports(res));
      ObjectMapper oMapper = new ObjectMapper();
      // models and enums
      buf.append(modelTemplate.apply(oMapper.convertValue(goRes, Map.class)));
      // request and response
      if (!goRes.getOperations().isEmpty()) {
        buf.append(responseTemplate.apply(oMapper.convertValue(goRes, Map.class)));
      }

      String modelFileName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, res.name);
      fileOps.add(
          new FileOp.WriteString(
              outputDirectoryPath, modelFileName + ".go", buf.toString()));
    }
    return fileOps;
  }

  private String getResponseImports(Resource res) {
    Set<String> imports = new HashSet<>();
    for (Action action : res.getSortedAction()) {
      for (com.chargebee.sdk.common.model.OperationResponse r :
          action.response().responseParameters(this)) {
        extractImports(r, res.name, imports);
      }
    }
    StringJoiner buf = new StringJoiner("\n");
    for (String imp : imports) {
      buf.add("\t\"" + imp + "\"");
    }
    if (buf.length() > 0) {
      return "\nimport (\n" + buf.toString() + "\n)";
    }
    return "";
  }

  private void extractImports(
      com.chargebee.sdk.common.model.OperationResponse r, String activeResourceName,
      Set<String> imports) {
    String type = r.getType();
    if (type != null
        && !type.equals("Any")
        && !type.equals("unknown")
        && !ResponseParser.isGoDataType(type)) {
      if (!type.equalsIgnoreCase(activeResourceName)) {
        String pkg = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, type).toLowerCase();
        imports.add("github.com/chargebee/chargebee-go/v3/" + pkg);
      }
    }
    if (r.isListResponse() && r.getListResponse() != null) {
      for (com.chargebee.sdk.common.model.OperationResponse sub : r.getListResponse()) {
        extractImports(sub, activeResourceName, imports);
      }
    }
  }

  private String subParams(Attribute subParam) {
    String type = "";
    StringJoiner buf = new StringJoiner("\n");
    for (Attribute attribute :
        subParam.attributes().stream().filter(Attribute::isNotHiddenAttribute).toList()) {
      if (!attribute.isSubResource()) continue;
      if (attribute.isHiddenParameter()) continue;
      if (attribute.isFilterAttribute()) {
        buf.add(
            "\t"
                + String.join(
                    delimiter,
                    toCamelCase(attribute.name),
                    Constants.FILTER + filterType(attribute.schema),
                    getJsonVal(attribute, attribute.isRequired)));
      } else if (attribute.isEnumAttribute()) {
        if (attribute.isGlobalEnumAttribute()) {
          if (attribute.name.equals("juris_type")) type = "enum.Tax" + toClazName(attribute.name);
          else type = Constants.ENUM_WITH_DELIMITER + toCamelCase(attribute.name);
        } else {
          ResourceAssist resourceAssist = new ResourceAssist().setResource(activeResource);

          if (resourceAssist.enums().stream()
              .anyMatch(a -> a.name.equals(singularize(subParam.name) + "_" + attribute.name))) {
            type =
                firstCharLower(toCamelCase(activeResource.name))
                    + Constants.ENUM_DOT
                    + toCamelCase(singularize(attribute.metaModelName()))
                    + toClazName(attribute.name);
          } else if (attribute.isSubResource() && attribute.metaModelName() != null) {
            type =
                firstCharLower(toCamelCase(singularize(attribute.metaModelName())))
                    + Constants.ENUM_DOT
                    + toClazName(attribute.name);

          } else if (attribute.metaModelName() != null && !attribute.isSubResource()) {
            type =
                firstCharLower(toCamelCase(singularize(attribute.metaModelName())))
                    + Constants.ENUM_DOT
                    + toClazName(attribute.name);
          } else {
            type =
                firstCharLower(toCamelCase(activeResource.name))
                    + Constants.ENUM_DOT
                    + toClazName(
                        attribute.getEnumApiName() != null
                            ? attribute.getEnumApiName()
                            : attribute.name);
          }
        }
        if (type.equalsIgnoreCase("paymentSourceEnum.CardFundingType")) {
          type = "cardEnum.FundingType";
        }
        if (type.equalsIgnoreCase("discountEnum.EntityType")) {
          type = "invoiceEnum.DiscountEntityType";
        }
        addEnumImport(type);
        buf.add(
            "\t"
                + String.join(
                    delimiter,
                    toCamelCase(attribute.name),
                    type,
                    getJsonVal(attribute, attribute.isRequired)));
      } else if (attribute.schema.getItems() != null) {
        buf.add(
            "\t"
                + String.join(
                    delimiter,
                    toCamelCase(attribute.name),
                    getGoType(attribute.schema.getItems(), attribute.name),
                    getJsonVal(attribute, attribute.isRequired)));
      } else {
        buf.add(
            "\t"
                + String.join(
                    delimiter,
                    toCamelCase(attribute.name),
                    getGoType(attribute.schema, attribute.name),
                    getJsonVal(attribute, attribute.isRequired)));
      }
    }
    return formatUsingDelimiter(buf.toString());
  }

  public List<Attribute> getInputSubResParams(Action action) {
    List<Attribute> subResources = new ArrayList<>();
    List<Parameter> params = new ArrayList<>();
    params.addAll(action.requestBodyParameters());
    params.addAll(action.queryParameters());
    List<Attribute> attributes =
        params.stream()
            .map(p -> new Attribute(p.getName(), p.schema, p.isRequired))
            .filter(Attribute::isNotHiddenAttribute)
            .sorted(Comparator.comparing(Attribute::sortOrder))
            .toList();
    for (Attribute attribute : attributes) {
      if (attribute.isSubResource()
          || attribute.isCompositeArrayRequestBody()
          || (!attribute.attributes().isEmpty()
              && attribute.attributes().get(0).isNotHiddenAttribute()
              && attribute.attributes().get(0).isSubResource())) {
        subResources.add(attribute);
      }
    }
    return subResources;
  }

  private boolean hasInputParams(Action a) {
    return !(a.requestBodyParameters().isEmpty() && a.queryParameters().isEmpty());
  }

  private String inputParams(Action action) {
    String type = "";
    StringJoiner buf = new StringJoiner("\n");
    HashMap<String, Integer> m = new HashMap<>();
    ActionAssist actionAssist =
        ActionAssist.of(action)
            .withFlatOuterEntries(true)
            .withPagination(true)
            .withOnlyPagination(true)
            .withFilterSubResource(true);
    for (Attribute attribute : actionAssist.getAllAttribute()) {
      if (attribute.isDeprecated()) continue;
      if (attribute.isSubResource() || attribute.isCompositeArrayRequestBody()) {
        if (m.containsKey(attribute.name)) {
          continue;
        } else {
          m.put(attribute.name, 1);
          type = "*" + activeResource.name + firstCharUpper(action.name) + toCamelCase(singularize(attribute.name));
          if (attribute.isCompositeArrayRequestBody()) {
            type = "[]" + type;
          }
          addEnumImport(type);
          buf.add(
              "\t"
                  + String.join(
                      delimiter,
                      toCamelCase(attribute.name),
                      type,
                      "`json:\"" + attribute.name + ",omitempty\"`"));
          continue;
        }
      }

      boolean req = attribute.isRequired;
      if (attribute.name.equals(Constants.SORT_BY)) {
        var dataType = dataType(attribute.schema, attribute.name);
        if (dataType.equalsIgnoreCase("string")) {
          buf.add(
              "\t"
                  + String.join(
                      delimiter,
                      toCamelCase(attribute.name),
                      dataType(attribute.schema, attribute.name),
                      getJsonVal(attribute, req)));
        } else
          buf.add(
              "\t"
                  + String.join(
                      delimiter,
                      toCamelCase(attribute.name),
                      Constants.FILTER + dataType(attribute.schema, attribute.name),
                      getJsonVal(attribute, req)));
      } else if (attribute.isEnumAttribute() && !attribute.isFilterAttribute()) {
        if (attribute.isListOfEnum()) {
          type = getListOfEnumTypeForAttribute(attribute);
        } else if (attribute.isGenSeparate()) {
          type = activeResource.name + toClazName(attribute.name);
        } else {
          type =
              // firstCharLower(toCamelCase(activeResource.name))
              activeResource.name
                  // + Constants.ENUM_DOT
                  + toClazName(attribute.name);
        }
        addEnumImport(type);
        // if (type.startsWith(getCamelClazName(activeResource.name) + Constants.ENUM_DOT)) {
        //   type = type.replace(getCamelClazName(activeResource.name) + Constants.ENUM_DOT, "");
        // }
        buf.add(
            "\t"
                + String.join(
                    delimiter, toCamelCase(attribute.name), type, getJsonVal(attribute, req)));
      } else if (attribute.isFilterAttribute()) {
        if (!attribute.attributes().isEmpty() && attribute.attributes().get(0).isSubResource()) {
          buf.add(
              "\t"
                  + String.join(
                      delimiter,
                      toCamelCase(attribute.name),
                      "*" + toCamelCase(action.name) + toCamelCase(attribute.name),
                      getJsonVal(attribute, req)));
        } else {
          buf.add(
              "\t"
                  + String.join(
                      delimiter,
                      toCamelCase(attribute.name),
                      Constants.FILTER + dataType(attribute.schema, attribute.name),
                      getJsonVal(attribute, req)));
        }
      } else {
        buf.add(
            "\t"
                + String.join(
                    delimiter,
                    toCamelCase(attribute.name),
                    getGoType(attribute.schema, attribute.name),
                    getJsonVal(attribute, req)));
      }
    }
    return formatUsingDelimiter(buf.toString());
  }

  private StringBuffer genModelEnums(
      String resourceName,
      List<Enum> resourceAssistEnums,
      List<Enum> schemaLessEnums)
      throws IOException {
    StringBuffer buf = new StringBuffer();
    Template enumTemplate = getTemplateContent("enums");

    List<Map<String, Object>> enums =
        resourceAssistEnums.stream()
            .map(e -> this.resourceEnumTemplate(e, resourceName))
            .filter(m -> !m.isEmpty())
            .toList();
    List<Map<String, Object>> schemalessEnum =
        schemaLessEnums.stream()
            .map(e -> this.resourceEnumTemplate(e, resourceName))
            .filter(m -> !m.isEmpty())
            .toList();

    for (var _enum : enums) {
      buf.append(enumTemplate.apply(_enum));
    }
    for (var _enum : schemalessEnum) {
      buf.append(enumTemplate.apply(_enum));
    }

    return buf;
  }

  public String getCols() {
    StringJoiner buf = new StringJoiner("\n");
    String type = "";
    List<Attribute> attributes = activeResource.getSortedResourceAttributes();
    for (Attribute a : attributes) {
      if (a.isDeprecated()) continue;
      if (a.isEnumAttribute()) {
        if (a.isListOfEnum()) {
          type = "[]" + Constants.ENUM_WITH_DELIMITER + getListOfEnumTypeForAttribute(a);
        } else if (a.isGenSeparate()) {
          type =
              activeResource.name
                  + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, a.name);
        } else {
          type =
          activeResource.name
          // + Constants.ENUM_DOT
          + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, a.name);
        }
        addEnumImport(type);
        buf.add("\t" + String.join(delimiter, toCamelCase(a.name), type, getJsonVal(a, true)));
      } else {
        if (a.isSubResource() && a.subResourceName() != null) {
          if (a.isListSubResourceAttribute() && !a.isDependentAttribute()) {
            buf.add(
                "\t"
                    + String.join(
                        delimiter,
                        toCamelCase(a.name),
                        dataType(a.schema, a.name),
                        getJsonVal(a, true)));
          } else {
            buf.add(
                "\t"
                    + String.join(
                        delimiter,
                        toCamelCase(a.name),
                        dataType(a.schema, a.subResourceName()),
                        getJsonVal(a, true)));
          }
        } else {
          buf.add(
              "\t"
                  + String.join(
                      delimiter,
                      toCamelCase(a.name),
                      dataType(a.schema, a.name),
                      getJsonVal(a, true)));
        }
      }
    }
    if (activeResource.isCustomFieldSupported())
      buf.add(
          "\t"
              + String.join(
                  delimiter,
                  "CustomField",
                  Constants.MAP_STRING_INTERFACE,
                  "`json:\"custom_field\"`"));
    if (activeResource.name.equals("Customer"))
      buf.add(
          "\t"
              + String.join(
                  delimiter, "Consents", Constants.MAP_STRING_INTERFACE, "`json:\"consents\"`"));
    buf.add("\t" + String.join(delimiter, "Object", Constants.STRING_TYPE, "`json:\"object\"`"));

    return formatUsingDelimiter(buf.toString());
  }

  private String getListOfEnumTypeForAttribute(Attribute a) {
    return CaseFormat.LOWER_UNDERSCORE.to(
            CaseFormat.UPPER_CAMEL,
            singularize((String) a.getSchema().getItems().getExtensions().get(SDK_ENUM_API_NAME)))
        + "Type";
  }

  public String getSubResourceCols(Resource subResource) {
    String type = "";
    StringJoiner buf = new StringJoiner("\n");
    List<Attribute> attributes =
        subResource.attributes().stream().filter(Attribute::isNotHiddenAttribute).toList();
    for (Attribute attribute : attributes) {
      System.out.println("attribute: " + attribute.name + "," + activeResource.name + "," + subResource.name + "," + attribute.isEnumAttribute() + "," + attribute.isGenSeparate() + "," + attribute.isDependentAttribute() + "," + attribute.isExternalEnum());
      if (attribute.isEnumAttribute()) {
        if (attribute.isGenSeparate()) {
          type = activeResource.name + singularize(subResource.name) + toCamelCase(attribute.name);
        } else if (attribute.isDependentAttribute()) {
          type = attribute.name;
        } else {
          if (attribute.isExternalEnum()) {
            if (attribute.getEnumApiName() == null
                || attribute.getEnumApiName().equalsIgnoreCase(attribute.name)) {
              type = Constants.ENUM_WITH_DELIMITER + toCamelCase(attribute.name);
            } else {
              type = firstCharLower(attribute.getEnumApiName());
              type =
                  type.contains(".")
                      ? type.replace(".", Constants.ENUM_DOT)
                      : Constants.ENUM_WITH_DELIMITER + toCamelCase(attribute.name);
            }
            type = enumTypeCustomLogic(type);
          } else {
            type =
                firstCharLower(activeResource.name)
                    + Constants.ENUM_DOT
                    + singularize(subResource.name)
                    + toCamelCase(attribute.name);
          }
        }
        // Patch to match cb-app based enum naming
        type =
            switch (type) {
              case "creditNoteEnum.TxnStatus",
                      "invoiceEnum.TxnStatus",
                      "transactionEnum.TxnStatus" ->
                  "transactionEnum.Status";
              case "invoiceEnum.CnReasonCode", "transactionEnum.CnReasonCode" ->
                  "creditNoteEnum.ReasonCode";
              case "invoiceEnum.CnStatus", "transactionEnum.CnStatus" -> "creditNoteEnum.Status";
              case "creditNoteEnum.InvoiceStatus", "transactionEnum.InvoiceStatus" ->
                  "invoiceEnum.Status";
              case "orderEnum.LinkedCreditNoteType" -> "orderEnum.OrderLineItemLinkedCreditType";
              case "orderEnum.LinkedCreditNoteStatus" ->
                  "orderEnum.OrderLineItemLinkedCreditStatus";
              case "discountEnum.EntityType" -> "invoiceEnum.DiscountEntityType";
              case "omnichannelSubscriptionEnum.OmnichannelTransactionType" ->
                  "omnichannelSubscriptionEnum.InitialPurchaseTransactionType";
              default -> type;
            };
        if (Set.of("QuotedSubscription", "Subscription", "Gift").contains(activeResource.name)
            && Set.of("SubscriptionItems", "GiftTimelines").contains(subResource.name)) {

          type =
              switch (activeResource.name) {
                case "QuotedSubscription" ->
                    type.replace(
                            "enum.BillingPeriodUnit", "quotedSubscriptionEnum.BillingPeriodUnit")
                        .replace("enum.Status", "giftEnum.Status");
                case "Subscription" ->
                    type.replace("enum.BillingPeriodUnit", "subscriptionEnum.BillingPeriodUnit")
                        .replace("enum.Status", "giftEnum.Status");
                case "Gift" -> type.replace("enum.Status", "giftEnum.Status");
                default -> type;
              };
        }
        addEnumImport(type);
        // if (type.startsWith(getCamelClazName(activeResource.name) + Constants.ENUM_DOT)) {
        //   type = type.replace(getCamelClazName(activeResource.name) + Constants.ENUM_DOT, "");
        // }
        buf.add(
            "\t"
                + String.join(
                    delimiter, toCamelCase(attribute.name), type, getJsonVal(attribute, true)));
      } else if (attribute.isSubResource()) {
        buf.add(
            "\t"
                + String.join(
                    delimiter,
                    toCamelCase(attribute.name),
                    dataType(attribute.schema, attribute.name),
                    getJsonVal(attribute, true)));
      } else {
        buf.add(
            "\t"
                + String.join(
                    delimiter,
                    toCamelCase(attribute.name),
                    dataType(attribute.schema, attribute.name),
                    getJsonVal(attribute, true)));
      }
    }
    buf.add("\t" + String.join(delimiter, "Object", Constants.STRING_TYPE, "`json:\"object\"`"));
    return formatUsingDelimiter(buf.toString());
  }

  private String enumTypeCustomLogic(String colsRetType) {
    if (colsRetType != null) {
      colsRetType = colsRetType.replace("enum.TxnStatus", "transactionEnum.Status");
      colsRetType = colsRetType.replace("enum.InvoiceStatus", "invoiceEnum.Status");
      colsRetType = colsRetType.replace("enum.CnReasonCode", "creditNoteEnum.ReasonCode");
      colsRetType = colsRetType.replace("enum.CnStatus", "creditNoteEnum.Status");
      colsRetType =
          colsRetType.replace("enum.PaymentMethodType", "paymentIntentEnum.PaymentMethodType");
    }
    return colsRetType;
  }

  public String dataType(Schema schema, String attributeName) {
    if (schema instanceof StringSchema
        || schema instanceof EmailSchema
        || schema instanceof PasswordSchema) {
      return Constants.STRING_TYPE;
    }
    if (schema.getExtensions() != null
        && schema.getExtensions().get(IS_MONEY_COLUMN) != null
        && schema.getExtensions().get(IS_MONEY_COLUMN).equals("true")) {
      return Constants.INT_SIXTY_FOUR;
    }
    if (schema instanceof NumberSchema || schema instanceof IntegerSchema) {
      if (schema.getType().equals("integer")) {
        if (schema.getFormat().equals(Constants.INT_THIRTY_TWO)) {
          return Constants.INT_THIRTY_TWO;
        } else if (schema.getFormat().equals(Constants.INT_SIXTY_FOUR)
            || schema.getFormat().equals(Constants.UNIX_TIME)) {
          return Constants.INT_SIXTY_FOUR;
        }
      }
      if (schema.getType().equals("number")
          && (schema.getFormat().equals("decimal") || schema.getFormat().equals("double"))) {
        return "float64";
      }

      return Constants.INT_THIRTY_TWO;
    }
    if (schema instanceof BooleanSchema) {
      return "bool";
    }
    if (schema instanceof ObjectSchema
        && GenUtil.hasAdditionalProperties(schema)
        && !attributeName.equals(SORT_BY)) {
      return Constants.JSON_RAW_MESSAGE;
    }
    if (schema instanceof ArraySchema
        && schema.getItems() != null
        && schema.getItems().getType() == null) {
      return Constants.JSON_RAW_MESSAGE;
    }

    if (isListOfSubResourceSchema(schema)) {
      // System.out.println("dataType isListOfSubResourceSchema: " + attributeName + "," + activeResource.name);
      if (!getDependentResource(activeResource).isEmpty()) {
        // System.out.println("dataType isListOfSubResourceSchema not empty: " + attributeName + "," + activeResource.name);
        String dep =
            getCamelClazName(singularize(attributeName)).toLowerCase().replace("_", "") + ".";
        return "[]*" + dep + toCamelCase(singularize(attributeName));
      } else {
        // System.out.println("dataType isListOfSubResourceSchema empty: " + attributeName + "," + activeResource.name);
        return "[]*" + activeResource.name + toCamelCase(singularize(attributeName));
      }
    }
    if (isSubResourceSchema(schema)) {
      // System.out.println("dataType isSubResourceSchema: " + attributeName + "," + activeResource.name);
      String dep = "";
      if (!getDependentResource(activeResource).isEmpty()) {
        if (schemaNamespaceIsLocal(schema)) {
          return toCamelCase(attributeName);
        }
        dep = toCamelCase(attributeName).toLowerCase() + ".";
        return "*" + dep + toCamelCase(attributeName);
      }
      // Patch to match cb-app based naming
      if ((activeResource.name.equalsIgnoreCase("PaymentIntent")
              && attributeName.equals("error_detail"))
          || (activeResource.name.equalsIgnoreCase("SubscriptionEntitlement")
              && attributeName.equalsIgnoreCase("entitlement_overrides"))) {
        return "*"
            + toCamelCase(subResourceName(schema)).toLowerCase()
            + "."
            + toCamelCase(subResourceName(schema));
      }
      return "*" + toCamelCase(attributeName);
    }
    if (schema.getItems() != null
        && dataType(schema.getItems(), attributeName).equals(Constants.STRING_TYPE)) {
      return "[]string";
    }
    if (schema instanceof ObjectSchema) {
      if (attributeName.equals(Constants.SORT_BY)) {
        return "SortFilter";
      }
      return filterType(schema);
    }
    return "unknown";
  }

  public String dataType(Schema schema) {
    if (schema instanceof StringSchema
        || schema instanceof EmailSchema
        || schema instanceof PasswordSchema) {
      return Constants.STRING_TYPE;
    }
    if (schema.getExtensions() != null
        && schema.getExtensions().get(IS_MONEY_COLUMN) != null
        && schema.getExtensions().get(IS_MONEY_COLUMN).equals("true")) {
      return Constants.INT_SIXTY_FOUR;
    }
    if (schema instanceof NumberSchema || schema instanceof IntegerSchema) {
      if (schema.getType().equals("integer")) {
        if (schema.getFormat().equals(Constants.INT_THIRTY_TWO)) {
          return Constants.INT_THIRTY_TWO;
        } else if (schema.getFormat().equals(Constants.INT_SIXTY_FOUR)
            || schema.getFormat().equals(Constants.UNIX_TIME)) {
          return Constants.INT_SIXTY_FOUR;
        }
      }
      if (schema.getType().equals("number")
          && (schema.getFormat().equals("decimal") || schema.getFormat().equals("double"))) {
        return "float64";
      }

      return Constants.INT_THIRTY_TWO;
    }
    if (schema instanceof BooleanSchema) {
      return "bool";
    }
    if (schema instanceof ObjectSchema && GenUtil.hasAdditionalProperties(schema)) {
      return Constants.JSON_RAW_MESSAGE;
    }
    if (schema instanceof ArraySchema
        && schema.getItems() != null
        && schema.getItems().getType() == null) {
      return "[]interface{}";
    }
    return "unknown";
  }

  private String subResourceName(Schema schema) {
    if (schema.getExtensions() != null && schema.getExtensions().get(SUB_RESOURCE_NAME) != null) {
      return (String) schema.getExtensions().get(SUB_RESOURCE_NAME);
    }
    return null;
  }

  private String getGoType(Schema schema, String attributeName) {
    String type = "";
    if (isMoneyColumn(schema)) {
      type = "*int64";
    } else if (schema instanceof StringSchema
        || schema instanceof EmailSchema
        || schema instanceof PasswordSchema) {
      type = Constants.STRING_TYPE;
    } else if (schema instanceof NumberSchema || schema instanceof IntegerSchema) {
      if (schema.getType().equals("integer")) {

        if (schema.getFormat().equals(Constants.INT_THIRTY_TWO)) {
          return "*int32";
        } else if (schema.getFormat().equals(Constants.INT_SIXTY_FOUR)
            || schema.getFormat().equals(Constants.UNIX_TIME)) {
          return "*int64";
        }
      }
      if (schema.getType().equals("number")
          && (schema.getFormat().equals("decimal") || schema.getFormat().equals("double"))) {
        return "*float64";
      }

      return Constants.INT_THIRTY_TWO;
    } else if (schema instanceof BooleanSchema) {
      type = "*bool";
    } else if (schema instanceof ObjectSchema && GenUtil.hasAdditionalProperties(schema)) {
      type = Constants.MAP_STRING_INTERFACE;
    } else if (schema instanceof ArraySchema
        && schema.getItems() != null
        && schema.getItems().getType() == null) {
      if (attributeName.contains("exemption_details")) {
        type = "[]map[string]interface{}";
      } else {
        type = "[]interface{}";
      }
    } else if (isListOfSubResourceSchema(schema)) {
      type = "[]*" + toCamelCase(singularize(attributeName));
    } else if (isSubResourceSchema(schema)) {
      type = "*" + toCamelCase(attributeName);
    } else if (schema.getItems() != null
        && dataType(schema.getItems(), attributeName).equals(Constants.STRING_TYPE)) {
      type = "[]string";
    } else if (attributeName.contains("exemption_details")) {
      type = "[]map[string]interface{}";
    }
    return type;
  }

  private String filterType(Schema schema) {
    if (schema.getExtensions() != null && schema.getExtensions().get(SDK_FILTER_NAME) != null) {
      return (String) schema.getExtensions().get(SDK_FILTER_NAME);
    }
    Optional<Schema> filterSch = schema.getProperties().values().stream().findFirst();
    if (filterSch.isEmpty()) return null;
    Schema filterSchema = filterSch.get();
    if (filterSchema.getEnum() != null) {
      if (filterSchema.getFormat() != null && filterSchema.getFormat().equals("boolean")) {
        return "BooleanFilter";
      }
      return "EnumFilter";
    }
    if (filterSchema instanceof StringSchema) {
      if (filterSchema.getFormat() != null) {
        if (filterSchema.getFormat().equals(Constants.UNIX_TIME)) {
          return "TimestampFilter";
        }
        return "NumberFilter";
      }
      return "StringFilter";
    }
    return "unknown";
  }

  private FileOp generateResultFile(String outputDirectory, List<Resource> resources)
      throws IOException {
    Map<String, Object> templateParams = this.resourceResponses(resources);
    // ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    // System.out.println(mapper.writeValueAsString(templateParams));
    Template resultTemplate = this.getTemplateContent("result");
    return new FileOp.WriteString(
        outputDirectory, "result.go", resultTemplate.apply(templateParams));
  }

  public boolean isDependedAttribute(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_DEPENDENT_ATTRIBUTE) != null
        && ((boolean) schema.getExtensions().get(IS_DEPENDENT_ATTRIBUTE));
  }

  public boolean isGobalResourceReference(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_GLOBAL_RESOURCE_REFERENCE) != null
        && ((boolean) schema.getExtensions().get(IS_GLOBAL_RESOURCE_REFRENCE));
  }

  public boolean schemaNamespaceIsLocal(Schema schema) {
    return !isDependedAttribute(schema) && !isGobalResourceReference(schema);
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
