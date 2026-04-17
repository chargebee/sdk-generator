package com.chargebee.sdk.validator.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks shared schemas that should be extracted into a common file.
 * Per the plan, every $ref becomes a shared schema.
 */
public class SharedSchemaRegistry {

  private final Map<String, ValidationNode> schemas = new LinkedHashMap<>();

  /** Register a named shared schema (idempotent – first registration wins). */
  public void register(String name, ValidationNode node) {
    schemas.putIfAbsent(name, node);
  }

  public boolean contains(String name) {
    return schemas.containsKey(name);
  }

  public ValidationNode get(String name) {
    return schemas.get(name);
  }

  public Map<String, ValidationNode> all() {
    return Collections.unmodifiableMap(schemas);
  }
}
