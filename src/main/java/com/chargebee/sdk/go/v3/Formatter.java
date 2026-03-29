package com.chargebee.sdk.go.v3;

import java.util.ArrayList;
import java.util.List;

public class Formatter {
  public static final String delimiter = "<<>>";

  public static String formatUsingDelimiter(String input) {
    int maxName = 0;
    int maxType = 0;
    List<String> rows = List.of(input.split("\n"));
    List<String> output = new ArrayList<>();
    for (String row : rows) {
      if (row.trim().startsWith("//")) {
        continue;
      }
      List<String> cols = List.of(row.split(delimiter));
      if (cols.size() < 2) continue;
      maxName = Math.max(maxName, cols.get(0).length());
      maxType = Math.max(maxType, cols.get(1).length());
    }
    maxName = maxName + 1;
    maxType = maxType + 1;
    for (String row : rows) {
      if (row.trim().startsWith("//")) {
        output.add(row);
        continue;
      }
      List<String> cols = List.of(row.split(delimiter));
      if (cols.size() < 2) continue;
      output.add(
          String.format(
              "%-" + maxName + "s%-" + maxType + "s%-1s", cols.get(0), cols.get(1), cols.get(2)));
    }
    return String.join("\n", output);
  }
}
