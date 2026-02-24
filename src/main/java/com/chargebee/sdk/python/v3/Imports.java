package com.chargebee.sdk.python.v3;

import static com.chargebee.GenUtil.singularize;
import static com.chargebee.sdk.python.v3.Common.*;
import static com.chargebee.sdk.python.v3.Common.getDependentResource;
import static com.chargebee.sdk.python.v3.Constants.GLOBAL_ENUM_PATTERN;
import static com.chargebee.sdk.python.v3.Constants.REFEREMCE_ENUM_PATTERN;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.common.model.OperationResponse;
import com.chargebee.sdk.go.v3.SchemaLessEnumParser;
import com.chargebee.sdk.python.v3.model.ResponseParser;
import com.google.common.base.CaseFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Imports {

  private static Set<String> resourceReferenceEnumImports(List<String> enumImports) {
    Set<String> imports =
        enumImports.stream()
            .filter(_enum -> REFEREMCE_ENUM_PATTERN.matcher(_enum).matches())
            .filter(_enum -> !GLOBAL_ENUM_PATTERN.matcher(_enum).matches())
            .map(_enum -> _enum.split("\"")[1])
            .collect(Collectors.toSet());

    if (!imports.isEmpty()) {
      return imports.stream().map(_enum -> _enum.split("\\.")[0]).collect(Collectors.toSet());
    }
    return Collections.emptySet();
  }

  protected static String operationImports(
      Resource resource,
      List<Resource> resourceList,
      List<String> resourceOperationImport,
      List<String> resourceTypeImport) {
    String imports =
        "from .responses import *"
            + "\nfrom chargebee import request, environment"
            + "\nfrom typing import TypedDict, Required, NotRequired, Dict, List, Any, cast";

    if (!resource.enums().isEmpty()
        || !SchemaLessEnumParser.getSchemalessEnum(resource, resourceList).isEmpty()
        || resource.subResources().stream().anyMatch(subResource -> !subResource.enums().isEmpty())
        || resource.name.equalsIgnoreCase("Estimate")) {
      imports += "\nfrom enum import Enum";
    }

    if (resource.isEvent()) {
      imports += "\nimport json\nfrom chargebee.main import Environment";
    }
    if (resource.isTimeMachine()) {
      imports += "\nimport json\nfrom chargebee import OperationFailedError";
    }

    if (hasFilterImports(resourceOperationImport) || hasFilterImports(resourceTypeImport)) {
      imports += "\nfrom chargebee.filters import Filters";
    }

    if (hasGlobalEnumImports(resourceOperationImport) || hasGlobalEnumImports(resourceTypeImport)) {
      imports += "\nfrom chargebee.models import enums";
    }

    Set<String> resourceReferenceEnumImports = resourceReferenceEnumImports(resourceTypeImport);
    if (!resourceReferenceEnumImports.isEmpty()) {
      String resourceReferenceImports = String.join(", ", resourceReferenceEnumImports);
      if (imports.contains("from chargebee.models import enums")) {
        imports += ", " + resourceReferenceImports;
      } else {
        imports += Constants.MODEL_IMPORT + resourceReferenceImports;
      }
    }

    if (resource.name.equalsIgnoreCase("PaymentIntent")) {
      imports += "\nfrom chargebee.models import gateway_error_detail";
    }

    if (resource.name.equalsIgnoreCase("SubscriptionEntitlement")) {
      imports += "\nfrom chargebee.models import entitlement_override";
    }

    if (!getDependentResource(resource).isEmpty()) {
      for (Attribute dr : getDependentResource(resource)) {
        var refModelName = singularize(dr.name);
        if (dr.isDependentAttribute()
            && resourceList.stream().noneMatch(r -> r.id.contains(singularize(dr.name)))) {
          if (dr.subResourceName() != null) {
            refModelName = dr.subResourceName();
            refModelName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, refModelName);
          } else {
            refModelName = refModelName.replace("_" + resource.id, "");
          }
        }
        if (imports.contains("from chargebee.models import")) {
          imports += ", " + refModelName;
        } else {
          imports += Constants.MODEL_IMPORT + refModelName;
        }
      }
    }
    return imports;
  }

  protected static String responseImports(
      Resource resource,
      List<Resource> resourceList,
      List<String> resourceTypeImport,
      Language language) {
    String imports =
        "from dataclasses import dataclass"
            + "\nfrom chargebee.model import Model"
            + "\nfrom typing import Dict, List, Any";

    if (resource.hasAnyAction()) {
      imports += "\nfrom chargebee.response import Response";
    }

    Set<String> resourceReferenceEnumImports = resourceReferenceEnumImports(resourceTypeImport);
    if (!resourceReferenceEnumImports.isEmpty()) {
      String resourceReferenceImports = String.join(", ", resourceReferenceEnumImports);
      if (imports.contains("from chargebee.models import enums")) {
        imports += ", " + resourceReferenceImports;
      } else {
        imports += Constants.MODEL_IMPORT + resourceReferenceImports;
      }
    }

    if (resource.name.equalsIgnoreCase("PaymentIntent")) {
      imports += "\nfrom chargebee.models import gateway_error_detail";
    }

    if (resource.name.equalsIgnoreCase("SubscriptionEntitlement")) {
      imports += "\nfrom chargebee.models import entitlement_override";
    }

    if (!getDependentResource(resource).isEmpty()) {
      for (Attribute dr : getDependentResource(resource)) {
        var refModelName = singularize(dr.name);
        if (dr.isDependentAttribute()
            && resourceList.stream().noneMatch(r -> r.id.contains(singularize(dr.name)))) {
          if (dr.subResourceName() != null) {
            refModelName = dr.subResourceName();
            refModelName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, refModelName);
          } else {
            refModelName = refModelName.replace("_" + resource.id, "");
          }
        }
        if (imports.contains("from chargebee.models import")) {
          imports += ", " + refModelName;
        } else {
          imports += Constants.MODEL_IMPORT + refModelName;
        }
      }
    }
    ArrayList<String> externalResourceResponseImports = new ArrayList<>();
    for (Action a : resource.actions) {
      for (OperationResponse response : a.response().responseParameters(language)) {
        if (!response.getListResponse().isEmpty()) {
          for (OperationResponse operationResponse : response.getListResponse()) {
            String type = operationResponse.getType() != null ? operationResponse.getType() : "";
            if (!operationResponse.getListResponse().isEmpty()
                || operationResponse.getName().equals("next_offset")
                || ResponseParser.isPythonDataType(type)) continue;
            if (!type.equalsIgnoreCase(resource.name)
                && !externalResourceResponseImports.contains(type)) {
              externalResourceResponseImports.add(type);
              if (imports.contains("from chargebee.models import")) {
                imports += ", " + singularize(operationResponse.getName());
              } else {
                imports += Constants.MODEL_IMPORT + singularize(operationResponse.getName());
              }
            }
          }
        }
        String type = response.getType() != null ? response.getType() : "";
        if (!response.getListResponse().isEmpty()
            || response.getName().equals("next_offset")
            || ResponseParser.isPythonDataType(type)) continue;
        if (type.equals("Any")) continue;
        if (!type.equalsIgnoreCase(resource.name)
            && !externalResourceResponseImports.contains(type)) {
          externalResourceResponseImports.add(type);
          if (imports.contains("from chargebee.models import")) {
            imports += ", " + singularize(response.getName());
          } else {
            imports += Constants.MODEL_IMPORT + singularize(response.getName());
          }
        }
      }
    }
    return imports;
  }
}
