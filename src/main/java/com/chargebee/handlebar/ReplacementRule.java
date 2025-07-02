package com.chargebee.handlebar;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReplacementRule {

  private final Pattern p;
  private final String r;

  public ReplacementRule(String regexp, String replacement) {
    p = Pattern.compile(regexp);
    r = replacement;
  }

  public boolean find(String word) {
    Matcher m = p.matcher(word);
    return m.find();
  }

  public String replace(String word) {
    Matcher m = p.matcher(word);
    return m.replaceAll(this.r);
  }

  @Override
  public String toString() {
    return String.format("[%s ,%s]", p.pattern(), r);
  }
}
