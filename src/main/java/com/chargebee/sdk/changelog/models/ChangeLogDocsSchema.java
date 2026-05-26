package com.chargebee.sdk.changelog.models;

import java.util.List;
import lombok.Data;
import lombok.Getter;

/**
 * Schema variant for the docs changelog. Mirrors {@link ChangeLogSchema} but carries structured
 * entries instead of raw strings so the docs availability checker can verify each entity against
 * the local docs repo.
 */
@Getter
public @Data class ChangeLogDocsSchema {
  List<ChangeLogEntry> newResource;
  List<ChangeLogEntry> newActions;
  List<ChangeLogEntry> newParams;
  List<ChangeLogEntry> newResourceAttribute;
  List<ChangeLogEntry> newEventType;
  List<ChangeLogEntry> deletedResource;
  List<ChangeLogEntry> deletedActions;
  List<ChangeLogEntry> deletedParams;
  List<ChangeLogEntry> deletedResourceAttribute;
  List<ChangeLogEntry> deletedEventType;
  List<ChangeLogEntry> newEnumValues;
  List<ChangeLogEntry> deletedEnumValues;
  List<ChangeLogEntry> parameterRequirementChangesValues;
}
