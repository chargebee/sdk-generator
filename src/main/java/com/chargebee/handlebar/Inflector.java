package com.chargebee.handlebar;

import java.util.ArrayList;

public class Inflector {

  private static final ArrayList<ReplacementRule> singulars;
  private static final ArrayList<String> uncountable;
  private static final ArrayList<ReplacementRule> plurals;

  static {
    singulars = new ArrayList<>(24);
    singulars.add(0, new ReplacementRule("s$", ""));
    singulars.add(0, new ReplacementRule("ss$", "ss"));
    singulars.add(0, new ReplacementRule("(n)ews$", "$1ews"));
    singulars.add(
        0,
        new ReplacementRule(
            "((a)naly|(b)a|(d)iagno|(p)arenthe|(p)rogno|(s)ynop|(t)he)ses$", "$1$2sis"));
    singulars.add(0, new ReplacementRule("(^analy)ses$", "$1sis"));
    singulars.add(0, new ReplacementRule("([^f])ves$", "$1fe"));
    singulars.add(0, new ReplacementRule("(hive)s$", "$1"));
    singulars.add(0, new ReplacementRule("(?i)(slave)s$", "$1"));
    singulars.add(0, new ReplacementRule("(tive)s$", "$1"));
    singulars.add(0, new ReplacementRule("([lr])ves$", "$1f"));
    singulars.add(0, new ReplacementRule("([^aeiouy]|qu)ies$", "$1y"));
    singulars.add(0, new ReplacementRule("(s)eries$", "$1eries"));
    singulars.add(0, new ReplacementRule("(m)ovies$", "$1ovie"));
    singulars.add(0, new ReplacementRule("(x|ch|ss|sh)es$", "$1"));
    singulars.add(0, new ReplacementRule("([m|l])ice$", "$1ouse"));
    singulars.add(0, new ReplacementRule("(bus)es$", "$1"));
    singulars.add(0, new ReplacementRule("(o)es$", "$1"));
    singulars.add(0, new ReplacementRule("(shoe)s$", "$1"));
    singulars.add(0, new ReplacementRule("(cris|ax|test)es$", "$1is"));
    singulars.add(0, new ReplacementRule("(?i)(tax)es$", "$1"));
    singulars.add(0, new ReplacementRule("(octop|vir)i$", "$1us"));
    singulars.add(0, new ReplacementRule("(alias|status)es$", "$1"));
    singulars.add(0, new ReplacementRule("(ox)en$", "$1"));
    singulars.add(0, new ReplacementRule("(virt|ind)ices$", "$1ex"));
    singulars.add(0, new ReplacementRule("(matr)ices$", "$1ix"));
    singulars.add(0, new ReplacementRule("(quiz)zes$", "$1"));
    singulars.add(0, new ReplacementRule("(database)s$", "$1"));
    singulars.add(0, new ReplacementRule("(data)$", "$1"));

    plurals = new ArrayList<>(17);
    plurals.add(0, new ReplacementRule("$", "s"));
    plurals.add(0, new ReplacementRule("(?i)s$", "s"));
    plurals.add(0, new ReplacementRule("(?i)(ax|test)is$", "$1es"));
    plurals.add(0, new ReplacementRule("(?i)(tax)$", "$1es"));
    plurals.add(0, new ReplacementRule("(?i)(octop|vir)us$", "$1i"));
    plurals.add(0, new ReplacementRule("(?i)(alias|status)$", "$1es"));
    plurals.add(0, new ReplacementRule("(?i)(bu)s$", "$1es"));
    plurals.add(0, new ReplacementRule("(?i)(buffal|tomat)o$", "$1oes"));
    plurals.add(0, new ReplacementRule("(?i)([ti])um$", "$1a"));
    plurals.add(0, new ReplacementRule("sis$", "ses"));
    plurals.add(0, new ReplacementRule("(?i)(?:([^f])fe|([lr])f)$", "$1$2ves"));
    plurals.add(0, new ReplacementRule("(?i)(hive)$", "$1s"));
    plurals.add(0, new ReplacementRule("(?i)(slave)$", "$1s"));
    plurals.add(0, new ReplacementRule("(?i)([^aeiouy]|qu)y$", "$1ies"));
    plurals.add(0, new ReplacementRule("(?i)(x|ch|ss|sh)$", "$1es"));
    plurals.add(0, new ReplacementRule("(?i)(matr|vert|ind)(?:ix|ex)$", "$1ices"));
    plurals.add(0, new ReplacementRule("(?i)([m|l])ouse$", "$1ice"));
    plurals.add(0, new ReplacementRule("^(?i)(ox)$", "$1en"));
    plurals.add(0, new ReplacementRule("(?i)(quiz)$", "$1zes"));
    plurals.add(0, new ReplacementRule("(data)$", "$1"));

    uncountable = new ArrayList<>(8);
    uncountable.add("equipment");
    uncountable.add("information");
    uncountable.add("rice");
    uncountable.add("money");
    uncountable.add("species");
    uncountable.add("series");
    uncountable.add("fish");
    uncountable.add("sheep");
    uncountable.add("data");
    uncountable.add("item_constraint_criteria");
  }

  private Inflector() {}

  public static String singularize(String word) {
    String out = word;
    if ((out.isEmpty()) || (!uncountable.contains(out.toLowerCase()))) {
      for (ReplacementRule r : singulars) {
        if (r.find(word)) {
          out = r.replace(word);
          break;
        }
      }
    }
    return out;
  }

  public static String pluralize(String word) {
    String out = word;
    if ((out.isEmpty()) || (!uncountable.contains(word.toLowerCase()))) {
      for (ReplacementRule r : plurals) {
        if (r.find(word)) {
          out = r.replace(word);
          break;
        }
      }
    }
    return out;
  }

  public static String capitalize(String str) {
    int strLen = str.length();
    if (strLen == 0) {
      return str;
    }
    return Character.toTitleCase(str.charAt(0)) + str.substring(1);
  }
}
