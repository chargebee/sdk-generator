package com.chargebee.sdk.java.javanext.datatype;

import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.jetbrains.annotations.NotNull;

// ===== Containers =====
public record ListType(String fieldName, Schema schema) implements FieldType {
  @Override
  public String display() {
    // If schema is null, return generic List<Object>
    if (schema == null) {
      return "List<Object>";
    }

    if (!(schema instanceof ArraySchema)) {
      return "List<Object>";
    }

    ArraySchema arraySchema = (ArraySchema) schema;
    Schema<?> items = arraySchema.getItems();

    // If items is null, return generic List<Object>
    if (items == null) {
      return "List<Object>";
    }

    // If items has a $ref, extract the type name
    if (items.get$ref() != null && !items.get$ref().isEmpty()) {
      String refName = items.get$ref().substring(items.get$ref().lastIndexOf("/") + 1);
      return "List<" + refName + ">";
    }

    String itemType = items.getType();
    if (itemType == null || itemType.isEmpty()) {
      return "List<Object>";
    }

    if ("object".equals(itemType)) {
      // Inline object definition with properties present -> generate a submodel list
      if (items.getProperties() != null && !items.getProperties().isEmpty()) {
        String className =
            fieldName != null
                ? CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, fieldName)
                : "Object";
        return "List<" + className + ">";
      }
      // Otherwise treat as free-form object -> List<Map<String, Object>>
      return "List<java.util.Map<String, Object>>";
    }

    // For primitive types (string, number, integer, boolean)
    if ("string".equals(itemType)) {
      return "List<String>";
    }
    if ("integer".equals(itemType)) {
      return "List<Integer>";
    }
    if ("number".equals(itemType)) {
      return "List<Double>";
    }
    if ("boolean".equals(itemType)) {
      return "List<Boolean>";
    }

    return "List<Object>";
  }

  @Override
  public @NotNull String toString() {
    return display();
  }
}
