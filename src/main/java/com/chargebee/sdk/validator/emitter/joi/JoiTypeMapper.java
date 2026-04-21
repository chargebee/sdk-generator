package com.chargebee.sdk.validator.emitter.joi;

import com.chargebee.sdk.validator.ast.js.JsBuilder;
import com.chargebee.sdk.validator.ast.js.JsNode;
import com.chargebee.sdk.validator.ir.PropertyEntry;
import com.chargebee.sdk.validator.ir.SharedSchemaRegistry;
import com.chargebee.sdk.validator.ir.ValidationNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps IR ValidationNodes to Joi method-chain AST expressions.
 *
 * <p>Also handles nested object extraction: when an ObjectNode property is itself an ObjectNode,
 * it is registered as a named nested schema and referenced by its constant name.
 */
public class JoiTypeMapper {

  private final SharedSchemaRegistry registry;

  /** Accumulates inline nested const declarations to be emitted before the main schema. */
  private final List<JsNode.VariableDeclaration> nestedDecls;

  /** Tracks which const names have already been declared to prevent duplicate declarations. */
  private final Set<String> declaredNames = new HashSet<>();

  private final String actionName;
  private final String resourceName;

  public JoiTypeMapper(
      String actionName,
      String resourceName,
      SharedSchemaRegistry registry,
      List<JsNode.VariableDeclaration> nestedDecls) {
    this.actionName = actionName;
    this.resourceName = resourceName;
    this.registry = registry;
    this.nestedDecls = nestedDecls;
  }

  /**
   * Convert an IR node to a Joi expression AST node.
   *
   * @param node the IR node
   * @param propName the property name (used for nested schema naming)
   * @param required whether this property is required
   */
  public JsNode toJoi(ValidationNode node, String propName, boolean required) {
    JsNode base;
    if (node instanceof ValidationNode.StringNode sn) {
      base = stringToJoi(sn);
    } else if (node instanceof ValidationNode.NumberNode nn) {
      base = numberToJoi(nn);
    } else if (node instanceof ValidationNode.BooleanNode bn) {
      base = booleanToJoi(bn);
    } else if (node instanceof ValidationNode.ArrayNode an) {
      base = arrayToJoi(an, propName);
    } else if (node instanceof ValidationNode.ObjectNode on) {
      base = objectToJoi(on, propName);
    } else if (node instanceof ValidationNode.MapNode mn) {
      base = mapToJoi(mn, propName);
    } else if (node instanceof ValidationNode.RefNode rn) {
      base = refToJoi(rn);
    } else {
      throw new IllegalArgumentException("Unknown ValidationNode type: " + node.getClass());
    }

    return applyRequiredOptional(base, required);
  }

  // ---- per-type mappers ----

  private JsNode stringToJoi(ValidationNode.StringNode sn) {
    JsNode receiver = JsBuilder.member("Joi", "string");
    List<JsNode.MethodChain.MethodCall> calls = new ArrayList<>();

    if (sn.enumValues() != null && !sn.enumValues().isEmpty()) {
      List<JsNode> args = sn.enumValues().stream().map(v -> (JsNode) JsBuilder.lit(v)).toList();
      calls.add(JsBuilder.call("valid", args));
    } else {
      if ("email".equals(sn.format())) {
        JsNode opts =
            JsBuilder.obj(
                List.of(
                    JsBuilder.prop(
                        "tlds",
                        JsBuilder.obj(List.of(JsBuilder.prop("allow", JsBuilder.lit(false)))))));
        calls.add(JsBuilder.call("email", opts));
      } else if ("uri".equals(sn.format())) {
        calls.add(JsBuilder.call("uri"));
      } else if ("date-time".equals(sn.format()) || "date".equals(sn.format())) {
        calls.add(JsBuilder.call("isoDate"));
      }
      if (sn.maxLength() != null) calls.add(JsBuilder.call("max", JsBuilder.lit(sn.maxLength())));
      if (sn.minLength() != null) calls.add(JsBuilder.call("min", JsBuilder.lit(sn.minLength())));
      if (sn.pattern() != null) {
        // emit as Joi.string().pattern(new RegExp('...'))
        JsNode regex = JsBuilder.callExpr(JsBuilder.id("RegExp"), JsBuilder.lit(sn.pattern()));
        calls.add(JsBuilder.call("pattern", regex));
      }
    }

    return calls.isEmpty()
        ? JsBuilder.chain(JsBuilder.callExpr(receiver))
        : JsBuilder.chain(JsBuilder.callExpr(receiver), calls);
  }

  private JsNode numberToJoi(ValidationNode.NumberNode nn) {
    String joiType = nn.integer() ? "number" : "number";
    JsNode receiver = JsBuilder.member("Joi", joiType);
    List<JsNode.MethodChain.MethodCall> calls = new ArrayList<>();
    if (nn.integer()) calls.add(JsBuilder.call("integer"));
    if (nn.minimum() != null) calls.add(JsBuilder.call("min", JsBuilder.lit(nn.minimum())));
    if (nn.maximum() != null) calls.add(JsBuilder.call("max", JsBuilder.lit(nn.maximum())));
    return calls.isEmpty()
        ? JsBuilder.chain(JsBuilder.callExpr(receiver))
        : JsBuilder.chain(JsBuilder.callExpr(receiver), calls);
  }

  private JsNode booleanToJoi(ValidationNode.BooleanNode bn) {
    JsNode receiver = JsBuilder.member("Joi", "boolean");
    List<JsNode.MethodChain.MethodCall> calls = new ArrayList<>();
    if (bn.defaultValue() != null)
      calls.add(JsBuilder.call("default", JsBuilder.lit(bn.defaultValue())));
    return calls.isEmpty()
        ? JsBuilder.chain(JsBuilder.callExpr(receiver))
        : JsBuilder.chain(JsBuilder.callExpr(receiver), calls);
  }

  private JsNode arrayToJoi(ValidationNode.ArrayNode an, String propName) {
    JsNode itemsExpr = toJoi(an.items(), propName + "_item", false);
    JsNode receiver = JsBuilder.member("Joi", "array");
    List<JsNode.MethodChain.MethodCall> calls = new ArrayList<>();
    calls.add(JsBuilder.call("items", itemsExpr));
    if (an.minItems() != null) calls.add(JsBuilder.call("min", JsBuilder.lit(an.minItems())));
    if (an.maxItems() != null) calls.add(JsBuilder.call("max", JsBuilder.lit(an.maxItems())));
    return JsBuilder.chain(JsBuilder.callExpr(receiver), calls);
  }

  private JsNode objectToJoi(ValidationNode.ObjectNode on, String propName) {
    String constName = JoiNamingStrategy.nestedSchemaName(actionName, resourceName, propName);

    // Only declare once – if already declared, just return the reference
    if (!declaredNames.contains(constName)) {
      declaredNames.add(constName);
      JsNode objectExpr = buildJoiObjectExpr(on, propName);
      nestedDecls.add(JsBuilder.constDecl(constName, objectExpr));
    }

    return JsBuilder.id(constName);
  }

  private JsNode mapToJoi(ValidationNode.MapNode mn, String propName) {
    JsNode valueExpr = toJoi(mn.valueSchema(), propName, false);
    JsNode receiver = JsBuilder.member("Joi", "object");
    return JsBuilder.chain(
        JsBuilder.callExpr(receiver),
        List.of(
            JsBuilder.call("pattern", JsBuilder.member("Joi", "string"), valueExpr),
            JsBuilder.call("unknown", JsBuilder.lit(true))));
  }

  private JsNode refToJoi(ValidationNode.RefNode rn) {
    return JsBuilder.id(JoiNamingStrategy.sharedSchemaName(rn.targetName()));
  }

  /** Build a Joi.object({ prop: schema, ... }).unknown(true)? expression. */
  public JsNode buildJoiObjectExpr(ValidationNode.ObjectNode on, String contextName) {
    List<JsNode.ObjectExpression.ObjectProperty> props = new ArrayList<>();
    for (Map.Entry<String, PropertyEntry> entry : on.properties().entrySet()) {
      String key = entry.getKey();
      PropertyEntry pe = entry.getValue();
      JsNode valExpr = toJoi(pe.node(), key, pe.required());
      props.add(JsBuilder.prop(key, valExpr));
    }

    JsNode propsObj = JsBuilder.obj(props);
    JsNode receiver = JsBuilder.member("Joi", "object");
    List<JsNode.MethodChain.MethodCall> calls = new ArrayList<>();
    calls.add(JsBuilder.call("keys", propsObj));
    if (on.allowUnknown()) {
      calls.add(JsBuilder.call("unknown", JsBuilder.lit(true)));
    }
    return JsBuilder.chain(JsBuilder.callExpr(receiver), calls);
  }

  private JsNode applyRequiredOptional(JsNode base, boolean required) {
    if (base instanceof JsNode.Identifier) {
      // Reference to a shared/nested schema – append .required()/.optional() directly
      JsNode.MethodChain.MethodCall modifier =
          required ? JsBuilder.call("required") : JsBuilder.call("optional");
      return JsBuilder.chain(base, List.of(modifier));
    }
    if (base instanceof JsNode.MethodChain chain) {
      List<JsNode.MethodChain.MethodCall> calls = new ArrayList<>(chain.calls());
      calls.add(required ? JsBuilder.call("required") : JsBuilder.call("optional"));
      return new JsNode.MethodChain(chain.receiver(), calls);
    }
    return base;
  }
}
