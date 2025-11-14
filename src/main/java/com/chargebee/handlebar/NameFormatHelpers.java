package com.chargebee.handlebar;

import static com.chargebee.GenUtil.firstCharLower;
import static com.chargebee.GenUtil.toCamelCase;
import static com.chargebee.handlebar.Inflector.capitalize;
import static com.chargebee.handlebar.Inflector.singularize;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.google.common.base.CaseFormat;

public enum NameFormatHelpers implements Helper<Object> {
  CAMEL_CASE_TO_PASCAL_CASE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, value.toString());
    }
  },
  CAMEL_CASE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      if (value == null || value.toString().isEmpty()) {
        return "";
      }
      String stringValue = value.toString();
      return toCamelCase(stringValue);
    }
  },
  CAMEL_CASE_TO_SNAKE_CASE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, value.toString());
    }
  },

  SNAKE_CASE_TO_PASCAL_CASE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, value.toString());
    }
  },

  SNAKE_CASE_TO_CAMEL_CASE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, value.toString());
    }
  },

  TO_PASCAL {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return firstCharLower(toCamelCase(value.toString()));
    }
  },

  PASCAL_CASE_TO_SNAKE_CASE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, value.toString());
    }
  },

  SNAKE_CASE_TO_CAMEL_CASE_SINGULAR {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return singularize(CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, value.toString()));
    }
  },

  PASCAL_CASE_TO_CAMEL_CASE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, value.toString());
    }
  },

  PASCAL_CASE_TO_GO_CASE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return CaseFormat.UPPER_CAMEL
          .to(CaseFormat.LOWER_UNDERSCORE, value.toString())
          .replace("_", "");
    }
  },

  SNAKE_CASE_TO_LOWERCASE_NO_UNDERSCORE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return value.toString().toLowerCase().replace("_", "");
    }
  },

  PLURALIZE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return Inflector.pluralize(value.toString());
    }
  },

  SINGULARIZE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return Inflector.singularize(value.toString());
    }
  },
  SNAKE_CASE_TO_PASCAL_CASE_AND_SINGULARIZE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return Inflector.singularize(
          CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, value.toString()));
    }
  },

  SNAKE_CASE_TO_CAMEL_CASE_AND_SINGULARIZE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return Inflector.singularize(
          CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, value.toString()));
    }
  },

  PASCAL_CASE_TO_CAMEL_CASE_AND_PLURALIZE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return Inflector.pluralize(
          CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, value.toString()));
    }
  },

  PASCAL_CASE_TO_SNAKE_CASE_AND_PLURALIZE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return Inflector.pluralize(
          CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, value.toString()));
    }
  },

  TO_UPPER_CASE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      return value.toString().toUpperCase();
    }
  },

  OPERATION_NAME_TO_PASCAL_CASE {
    @Override
    public CharSequence apply(final Object value, final Options options) {
      String[] parts = value.toString().split("_");
      StringBuilder result = new StringBuilder();
      for (String part : parts) {
        result.append(capitalize(part));
      }
      return result.toString();
    }
  }
}
