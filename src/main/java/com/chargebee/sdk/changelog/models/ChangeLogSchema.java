package com.chargebee.sdk.changelog.models;

import java.util.List;
import lombok.Data;
import lombok.Getter;

// here all the attributes of model is a key value
// while key being the name and value being docs url.
@Getter
public @Data class ChangeLogSchema {
  List<String> newResource;
  List<String> newActions;
  List<String> newParams;
  List<String> newResourceAttribute;
  List<String> newEventType;
}
