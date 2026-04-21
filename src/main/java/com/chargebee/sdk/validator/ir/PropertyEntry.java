package com.chargebee.sdk.validator.ir;

/**
 * Wraps a ValidationNode with field-level metadata (required, optional, default).
 */
public record PropertyEntry(
    ValidationNode node,
    boolean required,
    boolean optional,
    Object defaultValue,
    String description) {}
