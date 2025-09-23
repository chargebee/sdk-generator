package com.chargebee.sdk.java.javanext.datatype;

import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.jetbrains.annotations.NotNull;

// ===== Containers =====
public record ListType(String fieldName, Schema schema) implements FieldType {
  @Override
  public String display() {
    if (!(schema instanceof ArraySchema)) {
      return "List<Object>";
    }
    ArraySchema arraySchema = (ArraySchema) schema;
    Schema<?> items = arraySchema.getItems();
    if (items == null) {
      return "List<Object>";
    }

    if (items.get$ref() != null) {
      String refName = items.get$ref().substring(items.get$ref().lastIndexOf("/") + 1);
      return "List<" + refName + ">";
    }

    if ("object".equals(items.getType())) {
      // Inline object definition with properties present
      if (items.getProperties() != null && !items.getProperties().isEmpty()) {
        return "List<" + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, fieldName) + ">";
      }
      return "List<Object>";
    }

    // Default to List<String> for primitive arrays
    return "List<String>";
  }

  @Override
  public @NotNull String toString() {
    return display();
  }
}
