package com.chargebee.sdk.php.v4;

import static com.chargebee.GenUtil.pluralize;
import static com.chargebee.GenUtil.toCamelCase;
import static com.chargebee.sdk.php.v4.Common.toPascalCase;
import static com.chargebee.sdk.php.v4.Constants.*;

import com.chargebee.GenUtil;
import com.chargebee.openapi.Action;
import com.chargebee.openapi.Extension;
import com.chargebee.openapi.Resource;
import com.chargebee.sdk.php.v4.models.ResourceAction;
import io.swagger.v3.oas.models.media.Schema;
import java.util.*;

public class ActionParser {
  public static ResourceAction getActionsForResources(Resource res) {
    List<com.chargebee.sdk.php.v4.models.Action> actions = new ArrayList<>();
    Set<String> imports = new HashSet<>();

    for (var action : res.actions) {
      if (!action.isNotHiddenFromSDK()) {
        continue;
      }
      if (!action.isNotInternalOperation()) {
        continue;
      }

      var act = buildAction(action, res);
      actions.add(act);

      // Add required imports
      imports.add(RESPONSE_NAMESPACE + toCamelCase(res.name) + "Response\\" + act.getReturnType());
      if (action.isContentTypeJsonAction()) imports.add(JSON_PARAM_ENCODER_NAMESPACE);
      if (action.isListResourceAction()) imports.add(LIST_PARAM_ENCODER_NAMESPACE);
    }
    imports.add("Chargebee\\Actions\\Contracts\\" + toCamelCase(res.name) + ACTIONS + INTERFACE);
    return new ResourceAction()
        .setResourceName(toCamelCase(res.name) + ACTIONS)
        .setActions(actions)
        .setNamespace(ACTIONS_NAMESPACE)
        .setImports(new ArrayList<>(imports));
  }

  protected static com.chargebee.sdk.php.v4.models.Action buildAction(Action action, Resource res) {
    return new com.chargebee.sdk.php.v4.models.Action()
        .setName(GenUtil.firstCharLower(toCamelCase(action.name)))
        .setLanguageMethodName(getMethodName(action))
        .setHttpMethodName(String.valueOf(action.httpRequestType))
        .setHasHandle(getHasHandle(action))
        .setListAction(action.isListResourceAction())
        .setHasRequestBodyParameters(!action.requestBodyParameters().isEmpty())
        .setHasQueryParameters(!action.queryParameters().isEmpty())
        .setUrlPrefix(action.getUrlPrefix())
        .setUrlSuffix(action.getUrlSuffix())
        .setAllQueryParamsOptional(action.isAllQueryParamsOptional())
        .setAllRequestBodyParamsOptional(action.isAllRequestBodyParamsOptional())
        .setOperationNeedsJsonInput(action.isContentTypeJsonAction())
        .setSubDomain(action.subDomain())
        .setJsonKeys(action.getJsonKeysInRequestBody())
        .setReturnType(toCamelCase(action.name) + res.name + "Response")
        .setPhpDocField(PHPDocSerializer.serializeActionParameter(action))
        .setActionDocLink(API_DOCS_URL + pluralize(res.id) + API_DOCS_QUERY + action.id)
        .setDeprecated(action.isOperationDeprecated())
        .setIdempotent(action.isIdempotent());
  }

  private static String getMethodName(Action action) {
    return toPascalCase(action.name);
  }

  private static boolean getHasHandle(Action action) {
    return !action.pathParameters().isEmpty();
  }

  public static boolean isCompositeArrayBody(Schema<?> schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(Extension.IS_COMPOSITE_ARRAY_REQUEST_BODY) != null
        && (boolean) schema.getExtensions().get(Extension.IS_COMPOSITE_ARRAY_REQUEST_BODY);
  }
}
