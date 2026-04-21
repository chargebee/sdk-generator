package com.chargebee.sdk.validator.ast.js;

import java.util.List;

/**
 * Minimal JavaScript AST covering the patterns needed to emit Joi validation code.
 */
public sealed interface JsNode
    permits JsNode.Program,
        JsNode.VariableDeclaration,
        JsNode.RequireCall,
        JsNode.ExportAssignment,
        JsNode.MethodChain,
        JsNode.ObjectExpression,
        JsNode.ArrayExpression,
        JsNode.Identifier,
        JsNode.Literal,
        JsNode.MemberAccess,
        JsNode.CallExpression {

  /** Top-level file: a list of statements. */
  record Program(List<JsNode> body) implements JsNode {}

  /** const name = init; */
  record VariableDeclaration(String name, JsNode init, String kind) implements JsNode {}

  /** const { destructured } = require('module'); or const name = require('module'); */
  record RequireCall(String module, List<String> destructured) implements JsNode {}

  /** module.exports.name = value; or module.exports = value; */
  record ExportAssignment(String name, JsNode value) implements JsNode {}

  /**
   * Fluent method chain: receiver.method1(args1).method2(args2)...
   * e.g. Joi.string().max(50).required()
   */
  record MethodChain(JsNode receiver, List<MethodCall> calls) implements JsNode {
    public record MethodCall(String name, List<JsNode> args) {}
  }

  /** { key: value, ... } */
  record ObjectExpression(List<ObjectProperty> entries) implements JsNode {
    public record ObjectProperty(String key, JsNode value) {}
  }

  /** [element1, element2, ...] */
  record ArrayExpression(List<JsNode> elements) implements JsNode {}

  /** A bare identifier: e.g. Joi, postalAddressBlockSchema */
  record Identifier(String name) implements JsNode {}

  /** A string, number, or boolean literal. */
  record Literal(Object value) implements JsNode {}

  /** object.property access, e.g. Joi.string */
  record MemberAccess(JsNode object, String property) implements JsNode {}

  /** A plain function/method call: callee(args). */
  record CallExpression(JsNode callee, List<JsNode> args) implements JsNode {}
}
