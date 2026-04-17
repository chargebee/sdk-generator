package com.chargebee.sdk.validator.ast.js;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts a {@link JsNode} AST into formatted JavaScript source code.
 * Produces consistent indentation without requiring an external formatter.
 */
public class JsPrinter {

  private static final String INDENT = "  ";

  public String print(JsNode node) {
    return print(node, 0);
  }

  private String print(JsNode node, int depth) {
    if (node instanceof JsNode.Program p) return printProgram(p, depth);
    if (node instanceof JsNode.VariableDeclaration v) return printVarDecl(v, depth);
    if (node instanceof JsNode.RequireCall r) return printRequire(r);
    if (node instanceof JsNode.ExportAssignment e) return printExport(e, depth);
    if (node instanceof JsNode.MethodChain m) return printMethodChain(m, depth);
    if (node instanceof JsNode.ObjectExpression o) return printObject(o, depth);
    if (node instanceof JsNode.ArrayExpression a) return printArray(a, depth);
    if (node instanceof JsNode.Identifier i) return i.name();
    if (node instanceof JsNode.Literal l) return printLiteral(l);
    if (node instanceof JsNode.MemberAccess ma)
      return print(ma.object(), depth) + "." + ma.property();
    if (node instanceof JsNode.CallExpression ce) return printCallExpression(ce, depth);
    throw new IllegalArgumentException("Unknown JsNode type: " + node.getClass());
  }

  private String printProgram(JsNode.Program p, int depth) {
    return p.body().stream()
        .map(n -> print(n, depth))
        .collect(Collectors.joining("\n"));
  }

  private String printVarDecl(JsNode.VariableDeclaration v, int depth) {
    return indent(depth) + v.kind() + " " + v.name() + " = " + print(v.init(), depth) + ";";
  }

  private String printRequire(JsNode.RequireCall r) {
    String requireExpr = "require('" + r.module() + "')";
    if (r.destructured() == null || r.destructured().isEmpty()) {
      return "const " + moduleAlias(r.module()) + " = " + requireExpr + ";";
    }
    String names = String.join(", ", r.destructured());
    return "const { " + names + " } = " + requireExpr + ";";
  }

  private String moduleAlias(String module) {
    // e.g. "joi" → "Joi", "@hapi/joi" → "Joi"
    String base = module.contains("/") ? module.substring(module.lastIndexOf('/') + 1) : module;
    return Character.toUpperCase(base.charAt(0)) + base.substring(1);
  }

  private String printExport(JsNode.ExportAssignment e, int depth) {
    if (e.name() == null || e.name().isBlank()) {
      return "module.exports = " + print(e.value(), depth) + ";";
    }
    return "module.exports." + e.name() + " = " + print(e.value(), depth) + ";";
  }

  private String printMethodChain(JsNode.MethodChain m, int depth) {
    StringBuilder sb = new StringBuilder(print(m.receiver(), depth));
    for (JsNode.MethodChain.MethodCall call : m.calls()) {
      sb.append(".").append(call.name()).append("(");
      sb.append(printArgList(call.args(), depth));
      sb.append(")");
    }
    return sb.toString();
  }

  private String printObject(JsNode.ObjectExpression o, int depth) {
    if (o.entries().isEmpty()) {
      return "{}";
    }
    String inner =
        o.entries().stream()
            .map(p -> indent(depth + 1) + quoteKey(p.key()) + ": " + print(p.value(), depth + 1))
            .collect(Collectors.joining(",\n"));
    return "{\n" + inner + "\n" + indent(depth) + "}";
  }

  private String quoteKey(String key) {
    // Quote keys that contain special characters or hyphens
    if (key.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) return key;
    return "'" + key + "'";
  }

  private String printArray(JsNode.ArrayExpression a, int depth) {
    if (a.elements().isEmpty()) return "[]";
    String inner =
        a.elements().stream()
            .map(e -> indent(depth + 1) + print(e, depth + 1))
            .collect(Collectors.joining(",\n"));
    return "[\n" + inner + "\n" + indent(depth) + "]";
  }

  private String printLiteral(JsNode.Literal l) {
    if (l.value() == null) return "null";
    if (l.value() instanceof String s) return "'" + s.replace("'", "\\'") + "'";
    if (l.value() instanceof Boolean b) return b.toString();
    return l.value().toString();
  }

  private String printCallExpression(JsNode.CallExpression ce, int depth) {
    return print(ce.callee(), depth) + "(" + printArgList(ce.args(), depth) + ")";
  }

  private String printArgList(List<JsNode> args, int depth) {
    return args.stream().map(a -> print(a, depth)).collect(Collectors.joining(", "));
  }

  private static String indent(int depth) {
    return INDENT.repeat(depth);
  }
}
