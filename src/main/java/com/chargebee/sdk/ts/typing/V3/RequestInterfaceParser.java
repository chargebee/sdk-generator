package com.chargebee.sdk.ts.typing.V3;

import static com.chargebee.GenUtil.toCamelCase;
import static com.chargebee.sdk.ts.typing.V3.Common.primitiveDataType;
import static com.chargebee.sdk.ts.typing.V3.DataTypesParser.*;
import static com.chargebee.sdk.ts.typing.V3.Utils.getHasSortParam;
import static com.chargebee.sdk.ts.typing.V3.Utils.getTypescriptPutMethName;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.parameter.Parameter;
import com.chargebee.sdk.common.ActionAssist;
import com.chargebee.sdk.ts.typing.Constants;
import com.chargebee.sdk.ts.typing.V3.models.OperationRequestInterface;
import com.chargebee.sdk.ts.typing.V3.models.OperationRequestParameter;
import com.chargebee.sdk.ts.typing.V3.models.SingularSubResource;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RequestInterfaceParser {
  public static List<OperationRequestInterface> getOperRequestInterfaces(
      Resource res, Resource activeResource) {
    List<OperationRequestInterface> operationRequestInterfaces = new ArrayList<>();
    for (Action action : res.getSortedAction()) {
      OperationRequestInterface operationRequestInterface = new OperationRequestInterface();
      operationRequestInterface.setClazName(Utils.getClazName(action, res));
      operationRequestInterface.setParams(getOperationRequestParameter(action, activeResource));
      operationRequestInterface.setHasSortParam(getHasSortParam(action));
      operationRequestInterface.setSubParamsForOperation(getSubParamsForOperation(action));
      operationRequestInterface.setSingularSubs(getSingularSubAttribute(action, activeResource));
      operationRequestInterface.setHasSingularSubs(
          !operationRequestInterface.getSingularSubs().isEmpty());
      operationRequestInterface.setMultiSubs(getMultiSubsParameter(action));
      operationRequestInterface.setHasMultiSubs(!getMultiSubs(action).isEmpty());
      operationRequestInterface.setCustomFieldSupported(action.isCustomFieldSupported());
      if (operationRequestInterface.getParams().isEmpty()
          && operationRequestInterface.getSingularSubs().isEmpty()
          && operationRequestInterface.getMultiSubs().isEmpty()) {
        continue;
      }
      operationRequestInterfaces.add(operationRequestInterface);
    }
    return operationRequestInterfaces;
  }

  public static List<String> getSubParamsForOperation(Action action) {
    List<String> toRet = new ArrayList<>();
    for (Attribute param : getSingularSubs(action)) {
      String singularSub =
          param.name
              + "?: "
              + toCamelCase(param.name)
              + toCamelCase(Utils.getClazName(action))
              + ";";

      if (!toRet.contains(singularSub)) {
        toRet.add(singularSub);
      }
    }

    for (Attribute param : getMultiSubs(action)) {
      String multiSub =
          param.name
              + "?: "
              + toCamelCase(param.name)
              + toCamelCase(Utils.getClazName(action))
              + "[];";

      if (!toRet.contains(multiSub)) {
        toRet.add(multiSub);
      }
    }
    return toRet;
  }

  public static List<Attribute> getSingularSubs(Action action) {
    List<Attribute> attributes = new ArrayList<>();
    List<Parameter> actionParameters = new ArrayList<>();
    actionParameters.addAll(action.queryParameters());
    actionParameters.addAll(action.requestBodyParameters());
    for (Parameter iparam : actionParameters) {
      Attribute attribute = new Attribute(iparam.getName(), iparam.schema, iparam.isRequired);
      boolean isCodeGen =
          (attribute.isSubResource()
              && !attribute.isMultiAttribute()
              && (attribute.isNotHiddenAttribute()));
      if (isCodeGen) attributes.add(attribute);
    }
    return attributes;
  }

  public static List<Attribute> getMultiSubs(Action action) {
    List<Attribute> attributes = new ArrayList<>();
    List<Parameter> actionParameters = new ArrayList<>();
    actionParameters.addAll(action.queryParameters());
    actionParameters.addAll(action.requestBodyParameters());
    for (Parameter iparam : actionParameters) {
      Attribute attribute = new Attribute(iparam.getName(), iparam.schema, iparam.isRequired);
      boolean isCodeGen = (iparam.isCompositeArrayBody() && attribute.isNotHiddenAttribute());
      if (isCodeGen) attributes.add(attribute);
    }
    return attributes;
  }

  private static List<OperationRequestParameter> getOperationRequestParameter(
      Action action, Resource activeResource) {
    ActionAssist actionAssist =
        ActionAssist.of(action).withSortBy(true).withOnlyPagination(true).withPagination(true);
    List<OperationRequestParameter> operationRequestParameters = new ArrayList<>();
    for (Attribute attribute : actionAssist.getAllAttribute()) {
      if (attribute.isFilterAttribute() && !attribute.isSubResource()) {
        handleOperationListAttribute(action, attribute, operationRequestParameters, activeResource);
      } else {
        if (!attribute.attributes().isEmpty() && !attribute.isMultiAttribute()) continue;
        OperationRequestParameter operationRequestParameter = new OperationRequestParameter();
        operationRequestParameter.setHidden(!attribute.isNotHiddenAttribute());
        operationRequestParameter.setDeprecated(attribute.isDeprecated());
        operationRequestParameter.setReturnGeneric(dataTypePrimitiveParameters(attribute));
        operationRequestParameter.setName(attribute.name);
        operationRequestParameter.setTypescriptPutMethName(getTypescriptPutMethName(attribute));
        operationRequestParameter.setSubFilterParam(Utils.getSubFilterParam(attribute));
        operationRequestParameter.setSortParam(getHasSortParam(action));
        operationRequestParameters.add(operationRequestParameter);
      }
    }
    return operationRequestParameters;
  }

  public static Map<String, List<SingularSubResource>> getSingularSubAttribute(
      Action action, Resource activeResource) {
    List<SingularSubResource> subResources = new ArrayList<>();
    ActionAssist actionAssist = ActionAssist.of(action);

    for (Attribute attribute : actionAssist.singularSubAttributes()) {
      attribute
          .attributes()
          .forEach(
              value -> {
                SingularSubResource subResource = new SingularSubResource();
                if (action.isListResourceAction()) {
                  subResource.setResName(
                      toCamelCase(attribute.name) + toCamelCase(activeResource.name));
                } else {
                  subResource.setResName(toCamelCase(attribute.name));
                }
                subResource.setClazName(Utils.getClazName(action));
                subResource.setHidden(value.isHiddenParameter());
                subResource.setDeprecated(value.isDeprecated());
                subResource.setName(value.name);
                subResource.setTypescriptPutMethName(getTypescriptPutMethName(value));
                subResource.setReturnGeneric(dataTypeForSingularSubResources(value));
                subResources.add(subResource);
              });
    }

    return subResources.stream().collect(Collectors.groupingBy(SingularSubResource::getResName));
  }

  public static Map<String, List<SingularSubResource>> getMultiSubsParameter(Action action) {
    List<SingularSubResource> subResources = new ArrayList<>();
    ActionAssist actionAssist = ActionAssist.of(action);
    for (Attribute attribute : actionAssist.multiSubAttributes()) {
      attribute
          .attributes()
          .forEach(
              subAttribute -> {
                SingularSubResource subResource = new SingularSubResource();
                subResource.setDeprecated(subAttribute.isDeprecated());
                subResource.setResName(attribute.name);
                subResource.setClazName(Utils.getClazName(action));
                subResource.setHidden(subAttribute.isHiddenParameter());
                subResource.setDeprecated(subAttribute.isDeprecated());
                subResource.setName(subAttribute.name);
                subResource.setTypescriptPutMethName(getTypescriptPutMethName(subAttribute));
                subResource.setReturnGeneric(dataTypeForSubResources(subAttribute.schema));
                subResources.add(subResource);
              });
    }
    return subResources.stream().collect(Collectors.groupingBy(SingularSubResource::getResName));
  }

  private static void handleOperationListAttribute(
      Action action,
      Attribute attribute,
      List<OperationRequestParameter> operationRequestParameters,
      Resource activeResource) {
    OperationRequestParameter operationRequestParameter = new OperationRequestParameter();
    if (!action.isInputObjNeeded() && attribute.isPaginationProperty()) return;
    operationRequestParameter.setHidden(!attribute.isNotHiddenAttribute());
    operationRequestParameter.setDeprecated(attribute.isDeprecated());
    operationRequestParameter.setReturnGeneric(
        getFilterReturnGeneric(attribute, action, activeResource));
    operationRequestParameter.setTypescriptPutMethName(getTypescriptPutMethName(attribute));
    operationRequestParameter.setName(attribute.name);
    operationRequestParameters.add(operationRequestParameter);
  }

  public static String getFilterReturnGeneric(
      Attribute attribute, Action action, Resource activeResource) {
    final String attributeName = attribute.name;
    if (activeResource.subResources().stream()
        .anyMatch(a -> a.id != null && a.id.equals(attributeName))) {
      return (toCamelCase(attributeName) + Utils.getClazName(action, activeResource, true));
    }
    if (attribute.schema instanceof ArraySchema) {
      return String.format(
          Constants.ARRAY_OF_STRING, primitiveDataType(attribute.schema.getItems()));
    }
    if (!attribute.attributes().isEmpty()
        && attribute.attributes().stream().anyMatch(a -> a.schema instanceof ObjectSchema)) {
      attribute = attribute.attributes().get(0);
    }
    if (attribute.isFilterAttribute() && attribute.schema instanceof ObjectSchema) {
      if (attribute.isSubResource()) {
        return (toCamelCase(attributeName) + Utils.getClazName(action, activeResource, true));
      } else {
        return dataTypeForFilterAttribute(attribute);
      }
    }
    return primitiveDataType(attribute.schema);
  }
}
