package com.chargebee.sdk.python.v3;

import static com.chargebee.GenUtil.*;
import static com.chargebee.openapi.Extension.IS_MONEY_COLUMN;
import static com.chargebee.openapi.Resource.isListOfSubResourceSchema;
import static com.chargebee.openapi.Resource.isSubResourceSchema;
import static com.chargebee.sdk.common.Constant.DEBUG_RESOURCE;
import static com.chargebee.sdk.common.Constant.SDK_DEBUG;
import static com.chargebee.sdk.python.v3.Common.*;
import static com.chargebee.sdk.python.v3.Constants.SORT_BY;
import static com.chargebee.sdk.python.v3.Constants.STRING_JOIN_DELIMITER;
import static com.chargebee.sdk.python.v3.DataTypeParser.*;
import static com.chargebee.sdk.python.v3.Imports.operationImports;
import static com.chargebee.sdk.python.v3.Imports.responseImports;

import com.chargebee.GenUtil;
import com.chargebee.openapi.*;
import com.chargebee.openapi.Enum;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.common.ActionAssist;
import com.chargebee.sdk.common.ResourceAssist;
import com.chargebee.sdk.python.v3.model.InputSubResParam;
import com.chargebee.sdk.python.v3.model.Operation;
import com.chargebee.sdk.python.v3.model.ResponseParser;
import com.chargebee.sdk.python.v3.model.SubResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.*;

public class PythonV3 extends Language {

  static Resource activeResource;

  static List<Resource> resourceList = new ArrayList<>();
  static List<String> resourceTypeImport = new ArrayList<>();
  static List<String> resourceOperationImport = new ArrayList<>();

  @Override
  public List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    String modelsDirectoryPath = "/models";
    List<Enum> globalEnums = spec.globalEnums();
    var resources =
        spec.resources().stream()
            .filter(resource -> !Arrays.stream(this.hiddenOverride).toList().contains(resource.id))
            .toList();
    resourceList = resources;
    var createModelsDirectory =
        new FileOp.CreateDirectory(outputDirectoryPath, modelsDirectoryPath);
    List<FileOp> fileOps = new ArrayList<>();

    fileOps.add(createModelsDirectory);
    fileOps.add(
        generateModelsInitFile(outputDirectoryPath + modelsDirectoryPath, resources, globalEnums));
    fileOps.add(generateGlobalEnums(outputDirectoryPath + modelsDirectoryPath, globalEnums));
    fileOps.addAll(genModels(outputDirectoryPath + modelsDirectoryPath, resources));
    fileOps.add(genMain(outputDirectoryPath, resources));

    return fileOps;
  }

  private FileOp generateModelsInitFile(
      String outputDirectoryPath, List<Resource> resources, List<Enum> globalEnums)
      throws IOException {
    List<Map<String, Object>> resourceList =
        resources.stream().map(r -> r.templateParams(this)).toList();

    Template initTemplate = getTemplateContent("models.init");
    Map<String, List<Map<String, Object>>> templateParams =
        Map.of("resources", resourceList, "globalEnums", globalEnumTemplate(globalEnums));

    return new FileOp.WriteString(
        outputDirectoryPath, "__init__.py", initTemplate.apply(templateParams));
  }

  private FileOp generateGlobalEnums(String outDirectoryPath, List<Enum> globalEnums)
      throws IOException {
    Template globalEnumTemplate = getTemplateContent("globalEnums");
    globalEnums = globalEnums.stream().sorted(Comparator.comparing(e -> e.name)).toList();
    var content = globalEnumTemplate.apply(Map.of("globalEnums", globalEnumTemplate(globalEnums)));
    return new FileOp.WriteString(outDirectoryPath, "enums.py", content);
  }

  private FileOp genMain(String outputDirectoryPath, List<Resource> resources) throws IOException {
    Template mainFunctionTemplate = getTemplateContent("chargebee.main");
    List<Map<String, Object>> templateParams =
        resources.stream().map(r -> r.templateParams(this)).toList();
    var content = mainFunctionTemplate.apply(Map.of("resources", templateParams));
    return new FileOp.WriteString(outputDirectoryPath, "main.py", content);
  }

  private void addOperationEnumImport(String enumName) {
    resourceOperationImport.add(enumName);
  }

  private void addTypesEnumImport(String enumName) {
    resourceTypeImport.add(enumName);
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of(
        "models.init",
        "/templates/python/v3/models.init.py.hbs",
        "globalEnums",
        "/templates/python/v3/globalEnums.py.hbs",
        "resource.operations",
        "/templates/python/v3/models.resource.operations.py.hbs",
        "resource.init",
        "/templates/python/v3/models.resource.init.py.hbs",
        "resource.response",
        "/templates/python/v3/models.resource.responses.py.hbs",
        "chargebee.main",
        "/templates/python/v3/main.py.hbs");
  }

  private List<FileOp> genModels(String outputDirectoryPath, List<Resource> resources)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();

    Template resourceOperations = getTemplateContent("resource.operations");
    Template resourceInit = getTemplateContent("resource.init");
    Template resourceResponse = getTemplateContent("resource.response");

    for (var res : resources) {
      String resourceDirRelPath = "/" + res.id;
      String resourceDirAbsPath = outputDirectoryPath + resourceDirRelPath;
      if (SDK_DEBUG && !DEBUG_RESOURCE.contains(res.name)) continue;
      fileOps.add(new FileOp.CreateDirectory(outputDirectoryPath, resourceDirRelPath));
      activeResource = res;
      resourceTypeImport.clear();
      resourceOperationImport.clear();
      com.chargebee.sdk.python.v3.model.Resource pyRes =
          new com.chargebee.sdk.python.v3.model.Resource();
      pyRes.setClazName(res.name);
      pyRes.setResponseCols(getResponseCols());
      pyRes.setEnums(genModelEnums(res, resourceList));

      List<SubResource> subResourceList = new ArrayList<>();
      for (Resource subResource : activeResource.subResources()) {
        SubResource sr = new SubResource();
        sr.setClazName(singularize(subResource.name));
        sr.setCols(getSubResourceCols(subResource));
        sr.setResponseCols(getSubResourceResponseCols(subResource));
        subResourceList.add(sr);
      }
      pyRes.setSubResources(subResourceList);
      List<Operation> operations = new ArrayList<>();
      for (Action action : activeResource.getSortedAction()) {
        Operation operation = new Operation();
        List<InputSubResParam> inputSubResParamList = new ArrayList<>();
        operation.setClazName(toClazName(action.name));
        operation.setHasInputParams(hasInputParams(action));
        operation.setInputParams(inputParams(action));
        operation.setHttpRequestType(action.httpRequestType.toString());
        operation.setJsonKeys(action.getJsonKeysInRequestBody());
        ActionAssist actionAssist =
            ActionAssist.of(action)
                .withFlatMultiAttribute(false)
                .withPagination(true)
                .withFilterSubResource(true);
        ResponseParser pyOperationResponse =
            new ResponseParser(
                action, activeResource.name, action.response().responseParameters(this));
        com.chargebee.sdk.python.v3.model.Response pyResponse =
            new com.chargebee.sdk.python.v3.model.Response();
        pyResponse.setClassName(pyOperationResponse.className());
        pyResponse.setResponseParams(pyOperationResponse.actionResponseParams());
        pyResponse.setSubResponseClassName(pyOperationResponse.subResponseClassName());
        pyResponse.setSubResponseParams(pyOperationResponse.subResponseParams());
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
        operation.setOperationResponses(pyResponse);
        operation.setSubDomain(action.subDomain());
        operation.setContentTypeJson(action.isOperationNeedsJsonInput());
        operations.add(operation);
      }
      pyRes.setOperations(operations);
      pyRes.setActions(setActions(res));
      pyRes.setOperationImports(
          operationImports(
              activeResource, resourceList, resourceOperationImport, resourceTypeImport));
      pyRes.setResponseImports(
          responseImports(activeResource, resourceList, resourceTypeImport, this));
      pyRes.setEvent(res.isEvent());
      pyRes.setTimeMachine(res.isTimeMachine());
      pyRes.setExport(res.isExport());
      pyRes.setSession(res.isSession());
      pyRes.setHostedPage(res.isHostedPage());
      ObjectMapper oMapper = new ObjectMapper();
      var pythonObject = oMapper.convertValue(pyRes, Map.class);
      var operationContents = resourceOperations.apply(pythonObject);
      var initContents = resourceInit.apply(activeResource.templateParams(this));
      var responseContents = resourceResponse.apply(pythonObject);
      fileOps.addAll(
          List.of(
              new FileOp.WriteString(resourceDirAbsPath, "__init__.py", initContents),
              new FileOp.WriteString(resourceDirAbsPath, "operations.py", operationContents),
              new FileOp.WriteString(resourceDirAbsPath, "responses.py", responseContents)));
    }
    return fileOps;
  }

  private List<Map<String, Object>> setActions(Resource resource) {
    return resource.actions.stream()
        .filter(Action::isNotHiddenFromSDK)
        .filter(Action::isNotBulkOperation)
        .filter(Action::isNotInternalOperation)
        .sorted(Comparator.comparing(Action::sortOrder))
        .map(a -> a.templateParams(this))
        .toList();
  }

  private String subParams(Attribute subParam) {
    String type = "";
    String typePrefix = "";
    StringJoiner buf = new StringJoiner("\n");
    for (Attribute attribute :
        subParam.attributes().stream().filter(Attribute::isNotHiddenAttribute).toList()) {
      typePrefix = attribute.isRequired ? ": Required" : ": NotRequired";
      if (!attribute.isSubResource()) continue;
      if (attribute.isHiddenParameter()) continue;
      if (attribute.isFilterAttribute()) {
        buf.add(
            Constants.DOUBLE_INDENT_DELIMITER
                + String.join(
                    STRING_JOIN_DELIMITER,
                    attribute.name,
                    typePrefix,
                    "[",
                    Constants.FILTER + filterType(attribute.schema),
                    "]"));
        resourceTypeImport.add(Constants.FILTER);
      } else if (attribute.isEnumAttribute()) {
        if (attribute.isGlobalEnumAttribute()) {
          if (attribute.name.equals("juris_type")) type = "enums.Tax" + toClazName(attribute.name);
          else type = Constants.ENUM_WITH_DELIMITER + toCamelCase(attribute.name);
        } else {
          ResourceAssist resourceAssist = new ResourceAssist().setResource(activeResource);

          if (resourceAssist.pyEnums().stream()
              .anyMatch(a -> a.name.equals(singularize(subParam.name) + "_" + attribute.name))) {
            type =
                "\""
                    + activeResource.name
                    + Constants.REFERENCE_ENUM_DELIMITER
                    + toCamelCase(singularize(attribute.metaModelName()))
                    + toClazName(attribute.name)
                    + "\"";
          } else if (attribute.metaModelName() != null
              && !singularize(attribute.metaModelName()).equals(activeResource.id)) {
            type =
                "\""
                    + singularize(attribute.metaModelName())
                    + Constants.REFERENCE_ENUM_DELIMITER
                    + toCamelCase(singularize(attribute.metaModelName()))
                    + Constants.REFERENCE_ENUM_DELIMITER
                    + toClazName(attribute.name)
                    + "\"";

          } else {
            type =
                "\""
                    + activeResource.name
                    + Constants.REFERENCE_ENUM_DELIMITER
                    + toClazName(
                        attribute.getEnumApiName() != null
                            ? attribute.getEnumApiName()
                            : attribute.name)
                    + "\"";
          }
        }
        if (type.equalsIgnoreCase("\"payment_source.PaymentSource.CardFundingType\"")) {
          type = "\"card.Card.FundingType\"";
        }
        if (type.equalsIgnoreCase("\"discount.Discount.EntityType\"")) {
          type = "\"invoice.Invoice.DiscountEntityType\"";
        }
        addTypesEnumImport(type);
        buf.add(
            Constants.DOUBLE_INDENT_DELIMITER
                + String.join(STRING_JOIN_DELIMITER, (attribute.name), typePrefix, "[", type, "]"));
      } else if (attribute.schema.getItems() != null) {
        buf.add(
            Constants.DOUBLE_INDENT_DELIMITER
                + String.join(
                    STRING_JOIN_DELIMITER,
                    (attribute.name),
                    typePrefix,
                    "[",
                    getPyType(attribute.schema.getItems(), attribute.name),
                    "]"));
      } else {
        buf.add(
            Constants.DOUBLE_INDENT_DELIMITER
                + String.join(
                    STRING_JOIN_DELIMITER,
                    (attribute.name),
                    typePrefix,
                    "[",
                    getPyType(attribute.schema, attribute.name),
                    "]"));
      }
    }
    return buf.toString();
  }

  private String inputParams(Action action) {
    String type = "";
    String typePrefix = "";
    StringJoiner buf = new StringJoiner("\n");
    HashMap<String, Integer> m = new HashMap<>();
    ActionAssist actionAssist =
        ActionAssist.of(action)
            .withFlatOuterEntries(true)
            .withPagination(true)
            .withOnlyPagination(true)
            .withFilterSubResource(true);
    for (Attribute attribute : actionAssist.getAllAttribute()) {
      typePrefix = attribute.isRequired ? ": Required" : ": NotRequired";
      if (attribute.isSubResource() || attribute.isCompositeArrayRequestBody()) {
        if (m.containsKey(attribute.name)) {
          continue;
        } else {
          m.put(attribute.name, 1);
          type = toCamelCase(action.name) + toCamelCase(singularize(attribute.name));
          if (attribute.isCompositeArrayRequestBody()) {
            buf.add(
                Constants.DOUBLE_INDENT_DELIMITER
                    + String.join(
                        STRING_JOIN_DELIMITER,
                        attribute.name,
                        typePrefix,
                        "[",
                        "List["
                            + "\""
                            + activeResource.name
                            + Constants.REFERENCE_ENUM_DELIMITER
                            + type
                            + "Params\"]]"));
            type = "List[" + type + "]";
          } else {
            buf.add(
                Constants.DOUBLE_INDENT_DELIMITER
                    + String.join(
                        STRING_JOIN_DELIMITER,
                        (attribute.name),
                        typePrefix,
                        "[" + "\"" + activeResource.name + Constants.REFERENCE_ENUM_DELIMITER,
                        type + "Params\"]"));
          }
          addOperationEnumImport(type);
          continue;
        }
      }

      if (attribute.name.equals(Constants.SORT_BY)) {
        buf.add(
            Constants.DOUBLE_INDENT_DELIMITER
                + String.join(
                    STRING_JOIN_DELIMITER,
                    (attribute.name),
                    typePrefix,
                    "[",
                    Constants.FILTER + dataType(attribute.schema, attribute.name) + "]"));
        resourceOperationImport.add(Constants.FILTER);
      } else if (attribute.isEnumAttribute() && !attribute.isFilterAttribute()) {
        if (attribute.isGenSeparate()) {
          type = Constants.ENUM_WITH_DELIMITER + toClazName(attribute.name);
        } else {
          type =
              "\""
                  + activeResource.name
                  + Constants.REFERENCE_ENUM_DELIMITER
                  + toClazName(attribute.name)
                  + "\"";
        }
        addOperationEnumImport(type);
        buf.add(
            Constants.DOUBLE_INDENT_DELIMITER
                + String.join(STRING_JOIN_DELIMITER, (attribute.name), typePrefix, "[", type, "]"));
      } else if (attribute.isFilterAttribute()) {
        if (!attribute.attributes().isEmpty() && attribute.attributes().get(0).isSubResource()) {
          buf.add(
              Constants.DOUBLE_INDENT_DELIMITER
                  + String.join(
                      STRING_JOIN_DELIMITER,
                      (attribute.name),
                      typePrefix,
                      "[",
                      "\"" + activeResource.name + Constants.REFERENCE_ENUM_DELIMITER,
                      toCamelCase(action.name) + toCamelCase(attribute.name) + "Params\"",
                      "]"));
        } else {
          buf.add(
              Constants.DOUBLE_INDENT_DELIMITER
                  + String.join(
                      STRING_JOIN_DELIMITER,
                      (attribute.name),
                      typePrefix,
                      "[",
                      Constants.FILTER + dataType(attribute.schema, attribute.name),
                      "]"));
          resourceOperationImport.add(Constants.FILTER);
        }
      } else {
        buf.add(
            Constants.DOUBLE_INDENT_DELIMITER
                + String.join(
                    STRING_JOIN_DELIMITER,
                    (attribute.name),
                    typePrefix,
                    "[",
                    getPyType(attribute.schema, attribute.name),
                    "]"));
      }
    }
    return buf.toString();
  }

  public String getResponseCols() {
    String type = "";
    String typePrefix = ": ";
    String typeSuffix = " = None";
    StringJoiner buf = new StringJoiner("\n");
    List<Attribute> attributes = activeResource.getSortedResourceAttributes();
    for (Attribute a : attributes) {
      if (a.isEnumAttribute()) {
        type = Constants.STRING_TYPE;
        buf.add(
            Constants.INDENT_DELIMITER
                + String.join(STRING_JOIN_DELIMITER, a.name, typePrefix, type, typeSuffix));
      } else {
        if (a.isSubResource() && a.subResourceName() != null) {
          if (a.isListSubResourceAttribute() && !a.isDependentAttribute()) {
            String dataType =
                formatDependantResponseDataType(formatResponseDataType(dataType(a.schema, a.name)));
            buf.add(
                Constants.INDENT_DELIMITER
                    + String.join(STRING_JOIN_DELIMITER, a.name, typePrefix, dataType, typeSuffix));
          } else {
            String dataType =
                formatDependantResponseDataType(
                    formatResponseDataType(dataType(a.schema, a.subResourceName())));
            buf.add(
                Constants.INDENT_DELIMITER
                    + String.join(STRING_JOIN_DELIMITER, a.name, typePrefix, dataType, typeSuffix));
          }
        } else {
          buf.add(
              Constants.INDENT_DELIMITER
                  + String.join(
                      STRING_JOIN_DELIMITER,
                      a.name,
                      typePrefix,
                      dataType(a.schema, a.name),
                      typeSuffix));
        }
      }
    }
    return buf.toString();
  }

  public String getSubResourceCols(Resource subResource) {
    String type = "";
    String typePrefix = "";
    StringJoiner buf = new StringJoiner("\n");
    List<Attribute> attributes =
        subResource.attributes().stream().filter(Attribute::isNotHiddenAttribute).toList();
    for (Attribute attribute : attributes) {
      typePrefix = attribute.isRequired ? ": Required" : ": NotRequired";
      if (attribute.isEnumAttribute()) {
        if (attribute.isGenSeparate()) {
          type = Constants.ENUM_WITH_DELIMITER + toCamelCase(attribute.name);
        } else if (attribute.isDependentAttribute()) {
          type = activeResource.name + Constants.ENUM_DOT + attribute.name;
        } else {
          if (attribute.isExternalEnum()) {
            type =
                CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, attribute.name.toString());
            if (!List.of("InvoiceStatus", "CnStatus", "CnReasonCode", "TxnStatus").contains(type)) {
              type = "\"" + activeResource.name + Constants.REFERENCE_ENUM_DELIMITER + type + "\"";
            }
          } else {
            type =
                "\""
                    + activeResource.name
                    + Constants.REFERENCE_ENUM_DELIMITER
                    + singularize(Resource.subResourceName(subResource))
                    + CaseFormat.LOWER_UNDERSCORE.to(
                        CaseFormat.UPPER_CAMEL, attribute.name.toString())
                    + "\"";
          }
        }
        // Patch to match cb-app based enum naming
        type =
            switch (type) {
              case "TxnStatus" -> "\"transaction.Transaction.Status\"";
              case "CnReasonCode" -> "\"credit_note.CreditNote.ReasonCode\"";
              case "CnStatus" -> "\"credit_note.CreditNote.Status\"";
              case "InvoiceStatus" -> "\"invoice.Invoice.Status\"";
              default -> type;
            };
        addTypesEnumImport(type);
        buf.add(
            Constants.DOUBLE_INDENT_DELIMITER
                + String.join(STRING_JOIN_DELIMITER, attribute.name, typePrefix, "[", type, "]"));
      } else if (attribute.isSubResource()) {
        buf.add(
            Constants.DOUBLE_INDENT_DELIMITER
                + String.join(
                    STRING_JOIN_DELIMITER,
                    attribute.name,
                    typePrefix,
                    "[",
                    dataType(attribute.schema, attribute.name),
                    "]"));
      } else {
        buf.add(
            Constants.DOUBLE_INDENT_DELIMITER
                + String.join(
                    STRING_JOIN_DELIMITER,
                    attribute.name,
                    typePrefix,
                    "[",
                    dataType(attribute.schema, attribute.name),
                    "]"));
      }
    }
    return buf.toString();
  }

  public String getSubResourceResponseCols(Resource subResource) {
    String type = "";
    String typePrefix = ": ";
    String typeSuffix = " = None";
    StringJoiner buf = new StringJoiner("\n");
    List<Attribute> attributes =
        subResource.attributes().stream().filter(Attribute::isNotHiddenAttribute).toList();
    for (Attribute attribute : attributes) {
      if (attribute.isEnumAttribute()) {
        type = Constants.STRING_TYPE;
        buf.add(
            Constants.INDENT_DELIMITER
                + String.join(STRING_JOIN_DELIMITER, attribute.name, typePrefix, type, typeSuffix));
      } else if (attribute.isSubResource()) {
        buf.add(
            Constants.INDENT_DELIMITER
                + String.join(
                    STRING_JOIN_DELIMITER,
                    attribute.name,
                    typePrefix,
                    dataType(attribute.schema, attribute.name),
                    typeSuffix));
      } else {
        buf.add(
            Constants.INDENT_DELIMITER
                + String.join(
                    STRING_JOIN_DELIMITER,
                    attribute.name,
                    typePrefix,
                    dataType(attribute.schema, attribute.name),
                    typeSuffix));
      }
    }
    return buf.toString();
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
      return Constants.INT_TYPE;
    }
    if (schema instanceof NumberSchema || schema instanceof IntegerSchema) {
      if (schema.getType().equals("integer")) {
        if (schema.getFormat().equals(Constants.INT_THIRTY_TWO)) {
          return Constants.INT_TYPE;
        } else if (schema.getFormat().equals(Constants.INT_SIXTY_FOUR)
            || schema.getFormat().equals(Constants.UNIX_TIME)) {
          return Constants.INT_TYPE;
        }
      }
      if (schema.getType().equals("number")
          && (schema.getFormat().equals("decimal") || schema.getFormat().equals("double"))) {
        return Constants.FLOAT_TYPE;
      }
      return Constants.INT_TYPE;
    }
    if (schema instanceof BooleanSchema) {
      return Constants.BOOLEAN_TYPE;
    }
    if (schema instanceof ObjectSchema
        && GenUtil.hasAdditionalProperties(schema)
        && !attributeName.equalsIgnoreCase(SORT_BY)) {
      return Constants.JSON_OBJECT_TYPE;
    }
    if (schema instanceof ArraySchema
        && schema.getItems() != null
        && schema.getItems().getType() == null) {
      return Constants.JSON_ARRAY_TYPE;
    }

    if (isListOfSubResourceSchema(schema)) {
      String dep = "";
      if (!getDependentResource(activeResource).isEmpty()) {
        dep = getSnakeClazName(singularize(attributeName)) + ".";
        return "List[" + dep + toCamelCase(attributeName) + "]";
      }
      return "List[" + dep + toCamelCase(singularize(attributeName)) + "]";
    }
    if (isSubResourceSchema(schema)) {
      String dep = "";
      if (!getDependentResource(activeResource).isEmpty()) {
        dep = getSnakeClazName(attributeName) + ".";
        return dep + pluralize(toCamelCase(attributeName));
      }
      // Patch to match cb-app based naming
      if ((activeResource.name.equalsIgnoreCase("PaymentIntent")
              && attributeName.equals("error_detail"))
          || (activeResource.name.equalsIgnoreCase("SubscriptionEntitlement")
              && attributeName.equalsIgnoreCase("entitlement_overrides"))) {
        String subResourceName = subResourceName(schema);
        if (subResourceName != null) {
          return CaseFormat.UPPER_CAMEL
                  .to(CaseFormat.LOWER_UNDERSCORE, subResourceName)
                  .toLowerCase()
              + "."
              + singularize(toCamelCase(subResourceName))
              + "Response";
        }
      }
      return toCamelCase(attributeName);
    }
    if (schema.getItems() != null
        && dataType(schema.getItems(), attributeName).equals(Constants.STRING_TYPE)) {
      return "List[str]";
    }
    if (schema instanceof ObjectSchema) {
      if (attributeName.equals(Constants.SORT_BY)) {
        return "SortFilter";
      }
      return filterType(schema);
    }
    return Constants.ANY_TYPE;
  }

  private String getPyType(Schema schema, String attributeName) {
    String type = Constants.ANY_TYPE;
    if (isMoneyColumn(schema)) {
      type = Constants.INT_TYPE;
    } else if (schema instanceof StringSchema
        || schema instanceof EmailSchema
        || schema instanceof PasswordSchema) {
      type = Constants.STRING_TYPE;
    } else if (schema instanceof NumberSchema || schema instanceof IntegerSchema) {
      if (schema.getType().equals("integer")) {

        if (schema.getFormat().equals(Constants.INT_THIRTY_TWO)) {
          return Constants.INT_TYPE;
        } else if (schema.getFormat().equals(Constants.INT_SIXTY_FOUR)
            || schema.getFormat().equals(Constants.UNIX_TIME)) {
          return Constants.INT_TYPE;
        }
      }
      if (schema.getType().equals("number")
          && (schema.getFormat().equals("decimal") || schema.getFormat().equals("double"))) {
        return Constants.FLOAT_TYPE;
      }

      return Constants.INT_TYPE;
    } else if (schema instanceof BooleanSchema) {
      type = Constants.BOOLEAN_TYPE;
    } else if (schema instanceof ObjectSchema && GenUtil.hasAdditionalProperties(schema)) {
      type = Constants.JSON_OBJECT_TYPE;
    } else if (schema instanceof ArraySchema
        && schema.getItems() != null
        && schema.getItems().getType() == null) {
      type = Constants.JSON_ARRAY_TYPE;
    } else if (isListOfSubResourceSchema(schema)) {
      type = "List[" + toCamelCase(singularize(attributeName)) + "]";
    } else if (isSubResourceSchema(schema)) {
      type = toCamelCase(attributeName);
    } else if (schema.getItems() != null
        && dataType(schema.getItems(), attributeName).equals(Constants.STRING_TYPE)) {
      type = "List[str]";
    } else if (attributeName.contains("exemption_details")) {
      type = Constants.JSON_ARRAY_TYPE;
    }
    return type;
  }

  @Override
  public String dataType(Schema schema) {
    if (schema instanceof StringSchema
        || schema instanceof EmailSchema
        || schema instanceof PasswordSchema) {
      return Constants.STRING_TYPE;
    }
    if (schema.getExtensions() != null
        && schema.getExtensions().get(IS_MONEY_COLUMN) != null
        && schema.getExtensions().get(IS_MONEY_COLUMN).equals("true")) {
      return Constants.INT_TYPE;
    }
    if (schema instanceof NumberSchema || schema instanceof IntegerSchema) {
      if (schema.getType().equals("integer")) {
        if (schema.getFormat().equals(Constants.INT_THIRTY_TWO)) {
          return Constants.INT_TYPE;
        } else if (schema.getFormat().equals(Constants.INT_SIXTY_FOUR)
            || schema.getFormat().equals(Constants.UNIX_TIME)) {
          return Constants.INT_TYPE;
        }
      }
      if (schema.getType().equals("number")
          && (schema.getFormat().equals("decimal") || schema.getFormat().equals("double"))) {
        return Constants.FLOAT_TYPE;
      }
      return Constants.INT_TYPE;
    }
    if (schema instanceof BooleanSchema) {
      return Constants.BOOLEAN_TYPE;
    }
    if (schema instanceof ObjectSchema && GenUtil.hasAdditionalProperties(schema)) {
      return Constants.JSON_OBJECT_TYPE;
    }
    if (schema instanceof ArraySchema
        && schema.getItems() != null
        && schema.getItems().getType() == null) {
      return Constants.JSON_ARRAY_TYPE;
    }
    return Constants.ANY_TYPE;
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
