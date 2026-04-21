package com.chargebee.sdk.validator.ir;

import java.util.List;
import java.util.Map;

/**
 * Language-agnostic intermediate representation of a validation schema tree.
 * Each subtype describes what constraints to apply; emitters translate this into
 * language-specific code (Joi, Pydantic, etc.).
 */
public sealed interface ValidationNode
    permits ValidationNode.ObjectNode,
        ValidationNode.StringNode,
        ValidationNode.NumberNode,
        ValidationNode.BooleanNode,
        ValidationNode.ArrayNode,
        ValidationNode.MapNode,
        ValidationNode.RefNode {

  record ObjectNode(Map<String, PropertyEntry> properties, boolean allowUnknown, String ref)
      implements ValidationNode {}

  record StringNode(
      Integer maxLength, Integer minLength, String pattern, String format, List<String> enumValues)
      implements ValidationNode {}

  record NumberNode(boolean integer, Number minimum, Number maximum) implements ValidationNode {}

  record BooleanNode(Boolean defaultValue) implements ValidationNode {}

  record ArrayNode(ValidationNode items, Integer minItems, Integer maxItems)
      implements ValidationNode {}

  /** For additionalProperties with typed values (map/dictionary schemas). */
  record MapNode(ValidationNode valueSchema) implements ValidationNode {}

  /** Reference to a named shared schema; resolved during emission. */
  record RefNode(String targetName) implements ValidationNode {}
}
