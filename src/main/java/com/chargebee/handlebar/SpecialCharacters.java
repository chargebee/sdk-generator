package com.chargebee.handlebar;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;

public enum SpecialCharacters implements Helper<Object> {
  CURLY_BRACKETS {
    @Override
    public CharSequence apply(final Object option, final Options options) {
      if (!(option instanceof String)) {
        return String.format("<invalid type : %s>", option.getClass());
      }
      return option.toString().equals("open") ? "{" : "}";
    }
  },

  BACK_SLASH {
    @Override
    public CharSequence apply(final Object option, final Options options) {
      return "\\";
    }
  }
}
