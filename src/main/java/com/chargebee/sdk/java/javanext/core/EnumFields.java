package com.chargebee.sdk.java.javanext.core;

import com.google.common.base.CaseFormat;
import java.util.AbstractMap;
import java.util.List;
import java.util.stream.Collectors;

@lombok.Data
public class EnumFields {
  private String name;
  private List<String> enums;
  private AbstractMap.SimpleEntry<String, String> values;

  public String getName() {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name);
  }

  public List<AbstractMap.SimpleEntry<String, String>> getValues() {
    return enums.stream()
        .map(
            value ->
                new AbstractMap.SimpleEntry<>(
                    CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, value), value))
        .collect(Collectors.toList());
  }
}
