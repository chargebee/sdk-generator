package com.chargebee.sdk.changelog.models;

import java.util.List;
import lombok.Data;
import lombok.Getter;

@Getter
public @Data class ChangeLogSchema {
  List<String> newResource;
  List<String> newActions;
  List<String> newParams;
  List<String> newResourceAttribute;
  List<String> newEventType;
  List<String> deletedResource;
  List<String> deletedActions;
  List<String> deletedParams;
  List<String> deletedResourceAttribute;
  List<String> deletedEventType;
}
