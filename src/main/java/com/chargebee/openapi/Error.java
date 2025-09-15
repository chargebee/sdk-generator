package com.chargebee.openapi;

import com.chargebee.sdk.DataType;
import io.swagger.v3.oas.models.media.Schema;
import java.util.*;

public class Error {
  public final String name;
  private final Schema<?> schema;

  public Error(String name, Schema<?> schema) {
    this.name = name;
    this.schema = schema;
  }

  public List<Attribute> attributes() {
    var properties = schema.getProperties();
    if (properties == null) {
      return List.of();
    }
    Set<String> requiredProperties =
        schema.getRequired() == null ? Set.of() : new HashSet<>(schema.getRequired());
    return properties.entrySet().stream()
        .map(
            entry ->
                new Attribute(
                    entry.getKey(), entry.getValue(), requiredProperties.contains(entry.getKey())))
        .toList();
  }

  public Map<String, Object> templateParams() {
    return Map.of("name", name);
  }

  public Map<String, Object> templateParams(DataType lang) {
    List<String> superAttributes =
        Arrays.asList("message", "error_msg", "type", "error_code", "api_error_code");
    var attributes =
        attributes().stream()
            .filter(attribute -> !superAttributes.contains(attribute.name))
            .filter(Attribute::isNotHiddenAttribute)
            .sorted(Comparator.comparing(Attribute::sortOrder))
            .map(attr -> attr.templateParams(lang))
            .filter(m -> !m.isEmpty())
            .toList();

    var templateParams =
        Map.ofEntries(
            new AbstractMap.SimpleEntry<String, Object>("name", name),
            new AbstractMap.SimpleEntry<String, Object>("attributes", attributes));

    return templateParams;
  }
}
