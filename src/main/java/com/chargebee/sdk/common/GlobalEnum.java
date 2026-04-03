package com.chargebee.sdk.common;

import com.chargebee.openapi.Enum;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GlobalEnum {
  private String name;
  private List<Map<String, String>> possibleValues;
  private List<Map<String, String>> deprecatedEnums;
  private boolean isParamBlankOption;
  private String resourceName;

  public GlobalEnum(Enum e) {
    this.setName(e.name);
    this.setDeprecatedEnums(this.enumValuesAsMap(e.deprecatedValues()));
    this.setPossibleValues(this.enumValuesAsMap(e.validValues()));
    this.setParamBlankOption(e.isParamBlankOption());
  }

  private List<Map<String, String>> enumValuesAsMap(List<String> enums) {
    if (enums.isEmpty()) {
      return new ArrayList<>();
    }
    return enums.stream().map(value -> Map.of("name", value)).toList();
  }

  public Map<String, Object> template() {
    return Map.ofEntries(
        new AbstractMap.SimpleEntry<>("name", this.name),
        new AbstractMap.SimpleEntry<>("resourceName", this.resourceName != null ? this.resourceName : ""),
        new AbstractMap.SimpleEntry<>("possibleValues", this.possibleValues),
        new AbstractMap.SimpleEntry<>("deprecatedEnums", this.deprecatedEnums),
        new AbstractMap.SimpleEntry<>("isParamBlankOption", this.isParamBlankOption));
  }
}
