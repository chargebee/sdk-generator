package com.chargebee.sdk.php.v4;

import static com.chargebee.GenUtil.toCamelCase;
import static com.chargebee.sdk.php.v4.Constants.*;

import com.chargebee.openapi.Resource;
import com.chargebee.sdk.php.v4.models.Action;
import com.chargebee.sdk.php.v4.models.ResourceAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ActionContractParser extends ActionParser {
  public static ResourceAction getActionsContractsForAction(Resource res) {
    List<Action> actions = new ArrayList<>();
    Set<String> imports = new HashSet<>();

    for (var action : res.actions) {
      if (!action.isNotHiddenFromSDK()) {
        continue;
      }
      var act = buildAction(action, res);
      actions.add(act);

      imports.add(RESPONSE_NAMESPACE + toCamelCase(res.name) + "Response\\" + act.getReturnType());
    }
    return new ResourceAction()
        .setResourceName(toCamelCase(res.name) + ACTIONS)
        .setActions(actions)
        .setNamespace(ACTIONS_NAMESPACE)
        .setImports(new ArrayList<>(imports));
  }
}
