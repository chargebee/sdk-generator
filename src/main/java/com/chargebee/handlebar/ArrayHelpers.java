package com.chargebee.handlebar;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public enum ArrayHelpers implements Helper<Object> {
  IN {
    @Override
    public Object apply(final Object stringToSearch, final Options options) throws IOException {
      ArrayList<String> arr =
          new ArrayList<>(Arrays.asList(options.param(0).toString().split(",")));
      Options.Buffer buffer = options.buffer();
      if (options.isFalsy(arr.contains(stringToSearch))) {
        buffer.append(options.inverse());
      } else {
        buffer.append(options.fn());
      }
      return buffer;
    }
  }
}
