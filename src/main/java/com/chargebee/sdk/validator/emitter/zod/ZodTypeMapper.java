package com.chargebee.sdk.validator.emitter.zod;

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
 * Maps IR ValidationNodes to Zod method-chain AST expressions.
 *
 * <p>IR → Zod mapping:
 * <ul>
 *   <li>StringNode{max:50}         → z.string().max(50).optional()
 *   <li>StringNode{enum:["a","b"]} → z.enum(["a","b"]).optional()
 *   <li>StringNode{format:"email"} → z.string().email().optional()
 *   <li>NumberNode{integer:true}   → z.number().int().optional()
 *   <li>BooleanNode                → z.boolean().optional()
 *   <li>ArrayNode{items}           → z.array(itemSchema).optional()
 *   <li>ObjectNode{props}          → z.object({...}).passthrough().optional()
 *   <li>RefNode                    → refNameBlockSchema (identifier)
 * </ul>
 */
public class ZodTypeMapper {

  private final SharedSchemaRegistry registry;
  private final List<JsNode.VariableDeclaration> nestedDecls;
  private final Set<String> declaredNames = new HashSet<>();

  private final String actionName;
  private final String resourceName;

  public ZodTypeMapper(
      String actionName,
      String resourceName,
      SharedSchemaRegistry registry,
      List<JsNode.VariableDeclaration> nestedDecls) {
    this.actionName = actionName;
    this.resourceName = resourceName;
    this.registry = registry;
    this.nestedDecls = nestedDecls;
  }

  public JsNode toZod(ValidationNode node, String propName, boolean required) {
    JsNode base;
    if (node instanceof ValidationNode.StringNode sn) {
      base = stringToZod(sn);
    } else if (node instanceof ValidationNode.NumberNode nn) {
      base = numberToZod(nn);
    } else if (node instanceof ValidationNode.BooleanNode bn) {
      base = booleanToZod(bn);
    } else if (node instanceof ValidationNode.ArrayNode an) {
      base = arrayToZod(an, propName);
    } else if (node instanceof ValidationNode.ObjectNode on) {
      base = objectToZod(on, propName);
    } else if (node instanceof ValidationNode.MapNode mn) {
      base = mapToZod(mn, propName);
    } else if (node instanceof ValidationNode.RefNode rn) {
      base = refToZod(rn);
    } else {
      throw new IllegalArgumentException("Unknown ValidationNode: " + node.getClass());
    }
    return applyOptional(base, required);
  }

  // ---- per-type mappers ----

  private JsNode stringToZod(ValidationNode.StringNode sn) {
    // Enum strings → z.enum([...])
    if (sn.enumValues() != null && !sn.enumValues().isEmpty()) {
      List<JsNode> elems = sn.enumValues().stream().map(v -> (JsNode) JsBuilder.lit(v)).toList();
      return JsBuilder.callExpr(JsBuilder.member("z", "enum"), JsBuilder.arr(elems));
    }

    JsNode base = JsBuilder.callExpr(JsBuilder.member("z", "string"));
    List<JsNode.MethodChain.MethodCall> calls = new ArrayList<>();

    if ("email".equals(sn.format())) {
      calls.add(JsBuilder.call("email"));
    } else if ("uri".equals(sn.format())) {
      calls.add(JsBuilder.call("url"));
    } else if ("date-time".equals(sn.format()) || "date".equals(sn.format())) {
      calls.add(JsBuilder.call("datetime"));
    }
    if (sn.maxLength() != null) calls.add(JsBuilder.call("max", JsBuilder.lit(sn.maxLength())));
    if (sn.minLength() != null) calls.add(JsBuilder.call("min", JsBuilder.lit(sn.minLength())));
    if (sn.pattern() != null) {
      JsNode regex = JsBuilder.callExpr(JsBuilder.id("RegExp"), JsBuilder.lit(sn.pattern()));
      calls.add(JsBuilder.call("regex", regex));
    }

    return calls.isEmpty() ? base : JsBuilder.chain(base, calls);
  }

  private JsNode numberToZod(ValidationNode.NumberNode nn) {
    JsNode base = JsBuilder.callExpr(JsBuilder.member("z", "number"));
    List<JsNode.MethodChain.MethodCall> calls = new ArrayList<>();
    if (nn.integer()) calls.add(JsBuilder.call("int"));
    if (nn.minimum() != null) calls.add(JsBuilder.call("min", JsBuilder.lit(nn.minimum())));
    if (nn.maximum() != null) calls.add(JsBuilder.call("max", JsBuilder.lit(nn.maximum())));
    return calls.isEmpty() ? base : JsBuilder.chain(base, calls);
  }

  private JsNode booleanToZod(ValidationNode.BooleanNode bn) {
    JsNode base = JsBuilder.callExpr(JsBuilder.member("z", "boolean"));
    if (bn.defaultValue() != null) {
      return JsBuilder.chain(
          base, List.of(JsBuilder.call("default", JsBuilder.lit(bn.defaultValue()))));
    }
    return base;
  }

  private JsNode arrayToZod(ValidationNode.ArrayNode an, String propName) {
    JsNode itemsExpr = toZod(an.items(), propName + "_item", false);
    JsNode base = JsBuilder.callExpr(JsBuilder.member("z", "array"), itemsExpr);
    List<JsNode.MethodChain.MethodCall> calls = new ArrayList<>();
    if (an.minItems() != null) calls.add(JsBuilder.call("min", JsBuilder.lit(an.minItems())));
    if (an.maxItems() != null) calls.add(JsBuilder.call("max", JsBuilder.lit(an.maxItems())));
    return calls.isEmpty() ? base : JsBuilder.chain(base, calls);
  }

  private JsNode objectToZod(ValidationNode.ObjectNode on, String propName) {
    String constName = ZodNamingStrategy.nestedSchemaName(actionName, resourceName, propName);
    if (!declaredNames.contains(constName)) {
      declaredNames.add(constName);
      JsNode expr = buildZodObjectExpr(on, propName);
      nestedDecls.add(JsBuilder.constDecl(constName, expr));
    }
    return JsBuilder.id(constName);
  }

  private JsNode mapToZod(ValidationNode.MapNode mn, String propName) {
    JsNode valueExpr = toZod(mn.valueSchema(), propName, false);
    return JsBuilder.callExpr(
        JsBuilder.member("z", "record"),
        JsBuilder.callExpr(JsBuilder.member("z", "string")),
        valueExpr);
  }

  private JsNode refToZod(ValidationNode.RefNode rn) {
    return JsBuilder.id(ZodNamingStrategy.sharedSchemaName(rn.targetName()));
  }

  /**
   * Build a Zod object expression.
   *
   * <ul>
   *   <li>allowUnknown=true  → {@code z.looseObject({...})}  (extra keys passed through)
   *   <li>allowUnknown=false → {@code z.object({...})}       (extra keys stripped, Zod v4 default)
   * </ul>
   *
   * <p>Note: {@code .passthrough()} is deprecated in Zod v4 — use {@code z.looseObject()} instead.
   */
  public JsNode buildZodObjectExpr(ValidationNode.ObjectNode on, String contextName) {
    List<JsNode.ObjectExpression.ObjectProperty> props = new ArrayList<>();
    for (Map.Entry<String, PropertyEntry> entry : on.properties().entrySet()) {
      String key = entry.getKey();
      PropertyEntry pe = entry.getValue();
      JsNode valExpr = toZod(pe.node(), key, pe.required());
      props.add(JsBuilder.prop(key, valExpr));
    }
    JsNode propsObj = JsBuilder.obj(props);
    // z.looseObject allows unknown keys; z.object strips them (Zod v4 default)
    String factory = on.allowUnknown() ? "looseObject" : "object";
    return JsBuilder.callExpr(JsBuilder.member("z", factory), propsObj);
  }

  private JsNode applyOptional(JsNode base, boolean required) {
    if (required) return base;
    // Identifiers (refs/nested schemas) get .optional() chained
    if (base instanceof JsNode.Identifier) {
      return JsBuilder.chain(base, List.of(JsBuilder.call("optional")));
    }
    if (base instanceof JsNode.MethodChain chain) {
      List<JsNode.MethodChain.MethodCall> calls = new ArrayList<>(chain.calls());
      calls.add(JsBuilder.call("optional"));
      return new JsNode.MethodChain(chain.receiver(), calls);
    }
    if (base instanceof JsNode.CallExpression) {
      return JsBuilder.chain(base, List.of(JsBuilder.call("optional")));
    }
    return base;
  }
}
