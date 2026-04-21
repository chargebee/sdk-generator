package com.chargebee.sdk.validator.ast.js;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts a {@link JsNode} AST into formatted TypeScript source code.
 *
 * <p>Differences from {@link JsPrinter}:
 * <ul>
 *   <li>{@link JsNode.RequireCall} → ES {@code import} statement</li>
 *   <li>{@link JsNode.ExportAssignment} → named {@code export const}</li>
 *   <li>Preserves all other JS semantics (Joi API is identical in TS)</li>
 * </ul>
 */
public class TsPrinter {

  private static final String INDENT = "  ";

  public String print(JsNode node) {
    return print(node, 0);
  }

  private String print(JsNode node, int depth) {
    if (node instanceof JsNode.Program p) return printProgram(p, depth);
    if (node instanceof JsNode.VariableDeclaration v) return printVarDecl(v, depth);
    if (node instanceof JsNode.RequireCall r) return printImport(r);
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
    return p.body().stream().map(n -> print(n, depth)).collect(Collectors.joining("\n"));
  }

  private String printVarDecl(JsNode.VariableDeclaration v, int depth) {
    return indent(depth) + v.kind() + " " + v.name() + " = " + print(v.init(), depth) + ";";
  }

  /** Emit ES import. If destructured, emits `import { A, B } from 'module';` else namespace import. */
  private String printImport(JsNode.RequireCall r) {
    if (r.destructured() != null && !r.destructured().isEmpty()) {
      String names = String.join(", ", r.destructured());
      return "import { " + names + " } from '" + r.module() + "';";
    }
    // Use namespace import (import * as X) to be compatible with esModuleInterop: false
    // and CJS modules like joi that use `export = Joi`
    String alias = moduleAlias(r.module());
    return "import * as " + alias + " from '" + r.module() + "';";
  }

  private String moduleAlias(String module) {
    String base = module.contains("/") ? module.substring(module.lastIndexOf('/') + 1) : module;
    return Character.toUpperCase(base.charAt(0)) + base.substring(1);
  }

  /**
   * Named export. When value is an Identifier with the same name, emits `export { name };`
   * to avoid the self-referential `export const x = x` pattern.
   * Otherwise emits `export const name = value;`.
   */
  private String printExport(JsNode.ExportAssignment e, int depth) {
    if (e.name() == null || e.name().isBlank()) {
      return "export default " + print(e.value(), depth) + ";";
    }
    if (e.value() instanceof JsNode.Identifier id && id.name().equals(e.name())) {
      return "export { " + e.name() + " };";
    }
    return "export const " + e.name() + " = " + print(e.value(), depth) + ";";
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
