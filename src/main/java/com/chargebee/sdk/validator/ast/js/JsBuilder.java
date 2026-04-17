package com.chargebee.sdk.validator.ast.js;

import java.util.Arrays;
import java.util.List;

/**
 * Fluent factory for constructing {@link JsNode} instances.
 */
public final class JsBuilder {

  private JsBuilder() {}

  public static JsNode.Identifier id(String name) {
    return new JsNode.Identifier(name);
  }

  public static JsNode.Literal lit(Object value) {
    return new JsNode.Literal(value);
  }

  public static JsNode.MemberAccess member(JsNode object, String property) {
    return new JsNode.MemberAccess(object, property);
  }

  public static JsNode.MemberAccess member(String objectName, String property) {
    return new JsNode.MemberAccess(id(objectName), property);
  }

  public static JsNode.MethodChain chain(JsNode receiver, JsNode.MethodChain.MethodCall... calls) {
    return new JsNode.MethodChain(receiver, Arrays.asList(calls));
  }

  public static JsNode.MethodChain chain(JsNode receiver, List<JsNode.MethodChain.MethodCall> calls) {
    return new JsNode.MethodChain(receiver, calls);
  }

  public static JsNode.MethodChain.MethodCall call(String name, JsNode... args) {
    return new JsNode.MethodChain.MethodCall(name, Arrays.asList(args));
  }

  public static JsNode.MethodChain.MethodCall call(String name, List<JsNode> args) {
    return new JsNode.MethodChain.MethodCall(name, args);
  }

  public static JsNode.ObjectExpression obj(List<JsNode.ObjectExpression.ObjectProperty> props) {
    return new JsNode.ObjectExpression(props);
  }

  public static JsNode.ObjectExpression.ObjectProperty prop(String key, JsNode value) {
    return new JsNode.ObjectExpression.ObjectProperty(key, value);
  }

  public static JsNode.ArrayExpression arr(List<JsNode> elements) {
    return new JsNode.ArrayExpression(elements);
  }

  public static JsNode.VariableDeclaration constDecl(String name, JsNode init) {
    return new JsNode.VariableDeclaration(name, init, "const");
  }

  public static JsNode.RequireCall require(String module) {
    return new JsNode.RequireCall(module, null);
  }

  public static JsNode.RequireCall requireDestructured(String module, String... names) {
    return new JsNode.RequireCall(module, Arrays.asList(names));
  }

  public static JsNode.ExportAssignment exportNamed(String name, JsNode value) {
    return new JsNode.ExportAssignment(name, value);
  }

  public static JsNode.Program program(List<JsNode> body) {
    return new JsNode.Program(body);
  }

  public static JsNode.CallExpression callExpr(JsNode callee, JsNode... args) {
    return new JsNode.CallExpression(callee, Arrays.asList(args));
  }
}
