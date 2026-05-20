package com.chargebee.sdk.validator.ir;

import static com.chargebee.openapi.Extension.ATTRIBUTE_META_COMMENT;
import static com.chargebee.openapi.Extension.HIDDEN_FROM_CLIENT_SDK;
import static com.chargebee.openapi.Extension.IS_MULTI_ATTRIBUTE;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recursively converts an OpenAPI Schema into a ValidationNode IR tree.
 *
 * <p>Shared $ref schemas are registered in the {@link SharedSchemaRegistry} so the emitter can
 * extract them into a shared file.
 */
public class ValidationIRBuilder {

  private final OpenAPI openAPI;
  private final SharedSchemaRegistry registry;

  public ValidationIRBuilder(OpenAPI openAPI, SharedSchemaRegistry registry) {
    this.openAPI = openAPI;
    this.registry = registry;
  }

  /**
   * Build an IR node from an OpenAPI schema. Resolves $refs, handles objects, arrays, and
   * primitives. Uses {@code visiting} to break circular references.
   */
  public ValidationNode buildNode(Schema<?> schema, Set<String> visiting) {
    if (schema == null) {
      return new ValidationNode.StringNode(null, null, null, null, null);
    }

    // Resolve $ref
    if (schema.get$ref() != null) {
      String refName = extractRefName(schema.get$ref());
      if (visiting.contains(refName)) {
        // Circular reference – emit a RefNode pointer
        return new ValidationNode.RefNode(refName);
      }
      if (!registry.contains(refName)) {
        Schema<?> resolved = resolveRef(refName);
        if (resolved != null) {
          visiting = new HashSet<>(visiting);
          visiting.add(refName);
          ValidationNode resolvedNode = buildNode(resolved, visiting);
          registry.register(refName, resolvedNode);
        }
      }
      return new ValidationNode.RefNode(refName);
    }

    String type = schema.getType();

    if ("object".equals(type) || (schema instanceof ObjectSchema)) {
      return buildObjectNode(schema, visiting);
    }

    if ("array".equals(type) || (schema instanceof ArraySchema)) {
      return buildArrayNode(schema, visiting);
    }

    if ("string".equals(type)) {
      return buildStringNode(schema);
    }

    if ("integer".equals(type)) {
      return new ValidationNode.NumberNode(
          true,
          schema.getMinimum() != null ? schema.getMinimum().intValue() : null,
          schema.getMaximum() != null ? schema.getMaximum().intValue() : null);
    }

    if ("number".equals(type)) {
      return new ValidationNode.NumberNode(false, schema.getMinimum(), schema.getMaximum());
    }

    if ("boolean".equals(type)) {
      return new ValidationNode.BooleanNode(schema.getDefault() instanceof Boolean b ? b : null);
    }

    // Fallback – treat as opaque string
    return new ValidationNode.StringNode(null, null, null, null, null);
  }

  private ValidationNode buildObjectNode(Schema<?> schema, Set<String> visiting) {
    Map<String, Schema> properties = schema.getProperties();

    // Check for multi-value attributes – flatten into ArrayNode<ObjectNode>
    if (hasMultiValueAttributes(properties)) {
      return buildMultiValueObjectNode(schema, visiting);
    }

    Map<String, PropertyEntry> propEntries = new LinkedHashMap<>();
    Set<String> requiredSet =
        schema.getRequired() == null ? Set.of() : new HashSet<>(schema.getRequired());

    if (properties != null) {
      for (Map.Entry<String, Schema> entry : properties.entrySet()) {
        String propName = entry.getKey();
        Schema<?> propSchema = entry.getValue();

        if (isHidden(propSchema)) continue;

        boolean isRequired = requiredSet.contains(propName) || isMetaCommentRequired(propSchema);
        boolean isOptional = !isRequired;
        Object defaultVal = propSchema.getDefault();
        String desc = propSchema.getDescription();

        ValidationNode childNode = buildNode(propSchema, visiting);
        propEntries.put(
            propName, new PropertyEntry(childNode, isRequired, isOptional, defaultVal, desc));
      }
    }

    boolean allowUnknown = schema.getAdditionalProperties() != null;
    return new ValidationNode.ObjectNode(propEntries, allowUnknown, null);
  }

  /**
   * Multi-value attributes (x-cb-is-multi-value-attribute) are flat-encoded array params.
   * We restructure them as ArrayNode<ObjectNode>.
   */
  private ValidationNode buildMultiValueObjectNode(Schema<?> schema, Set<String> visiting) {
    Map<String, Schema> properties = schema.getProperties();
    Map<String, PropertyEntry> propEntries = new LinkedHashMap<>();
    Set<String> requiredSet =
        schema.getRequired() == null ? Set.of() : new HashSet<>(schema.getRequired());

    if (properties != null) {
      for (Map.Entry<String, Schema> entry : properties.entrySet()) {
        String propName = entry.getKey();
        Schema<?> propSchema = entry.getValue();

        if (isHidden(propSchema)) continue;

        boolean isRequired = requiredSet.contains(propName) || isMetaCommentRequired(propSchema);
        ValidationNode childNode = buildNode(propSchema, visiting);
        propEntries.put(
            propName,
            new PropertyEntry(
                childNode,
                isRequired,
                !isRequired,
                propSchema.getDefault(),
                propSchema.getDescription()));
      }
    }

    ValidationNode.ObjectNode innerObject = new ValidationNode.ObjectNode(propEntries, false, null);
    return new ValidationNode.ArrayNode(innerObject, null, null);
  }

  private ValidationNode buildArrayNode(Schema<?> schema, Set<String> visiting) {
    Schema<?> items = schema.getItems();
    ValidationNode itemsNode =
        items != null
            ? buildNode(items, visiting)
            : new ValidationNode.StringNode(null, null, null, null, null);

    Integer minItems = schema.getMinItems();
    Integer maxItems = schema.getMaxItems();
    return new ValidationNode.ArrayNode(itemsNode, minItems, maxItems);
  }

  @SuppressWarnings("unchecked")
  private ValidationNode buildStringNode(Schema<?> schema) {
    List<String> enumValues = null;
    if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
      enumValues = new ArrayList<>();
      for (Object v : schema.getEnum()) {
        if (v != null) enumValues.add(v.toString());
      }
    }
    return new ValidationNode.StringNode(
        schema.getMaxLength(),
        schema.getMinLength(),
        schema.getPattern(),
        schema.getFormat(),
        enumValues);
  }

  private Schema<?> resolveRef(String refName) {
    if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
      return null;
    }
    return openAPI.getComponents().getSchemas().get(refName);
  }

  private static String extractRefName(String ref) {
    // e.g. "#/components/schemas/PostalAddress"
    int lastSlash = ref.lastIndexOf('/');
    return lastSlash >= 0 ? ref.substring(lastSlash + 1) : ref;
  }

  private static boolean hasMultiValueAttributes(Map<String, Schema> properties) {
    if (properties == null) return false;
    return properties.values().stream()
        .anyMatch(
            s ->
                s.getExtensions() != null
                    && Boolean.TRUE.equals(s.getExtensions().get(IS_MULTI_ATTRIBUTE)));
  }

  private static boolean isHidden(Schema<?> schema) {
    return schema.getExtensions() != null
        && Boolean.TRUE.equals(schema.getExtensions().get(HIDDEN_FROM_CLIENT_SDK));
  }

  private static boolean isMetaCommentRequired(Schema<?> schema) {
    return schema.getExtensions() != null
        && "required".equals(schema.getExtensions().get(ATTRIBUTE_META_COMMENT));
  }
}
