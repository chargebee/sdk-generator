package com.chargebee.sdk.changelog.models;


import lombok.Data;
import lombok.Getter;

import java.util.List;
import java.util.Map;

// here all the attributes of model is a key value
// while key being the name and value being docs url.
@Getter
public @Data class ChangeLogSchema {
    List<String> newResource;
    List<String> newActions;
    List<Map<String, String>> newParams;
    List<String> newResourceAttribute;
    List<Map<String, String>> newEventType;
    List<Map<String, String>> newGlobalEnums;
}
