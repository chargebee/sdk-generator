package com.chargebee.handlebar;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class TokenBasedSplitTextToLinesHelper implements Helper<Object> {

  @Override
  public String apply(final Object text, final Options options) {

    if (!(text instanceof String)) {
      return String.format("<invalid type : %s>", text.getClass());
    }

    var characterLimitForEachLine = options.param(0, 90);
    var characterLimitForFirstLine = options.param(1, 75);
    var indentationSpaceCount = options.param(2, 4);
    var indentation = " ".repeat(indentationSpaceCount);
    var lineDelimiter = options.param(3, ", \\");
    var tokenDelimiter = ",";
    var tokenJoinString = ", ";

    // Cloned from chargebee-app
    List<String> toRet = new ArrayList<>();
    var tokens = ((String) text).split(tokenDelimiter);
    StringJoiner buf = new StringJoiner(tokenJoinString);
    for (var token : tokens) {
      int maxAllowed = (toRet.isEmpty() ? characterLimitForFirstLine : characterLimitForEachLine);
      buf.add(token);
      if (buf.length() >= maxAllowed) {
        toRet.add(buf + lineDelimiter);
        buf = new StringJoiner(tokenJoinString); // reset buffer
      }
    }
    if (buf.length() != 0) {
      toRet.add(buf.toString());
    } else {
      int lastIndex = toRet.size() - 1;
      String last = toRet.get(lastIndex);
      last = last.substring(0, last.length() - (indentationSpaceCount - 1));
      toRet.set(lastIndex, last);
    }
    return String.join("\n" + indentation, toRet);
  }
}
