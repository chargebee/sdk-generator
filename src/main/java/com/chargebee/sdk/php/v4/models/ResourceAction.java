package com.chargebee.sdk.php.v4.models;

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@ToString
@Accessors(chain = true)
public @Data class ResourceAction {
  List<Action> actions;
  String resourceName;
  String namespace;
  List<String> imports;
}
