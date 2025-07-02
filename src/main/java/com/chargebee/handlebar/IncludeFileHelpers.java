package com.chargebee.handlebar;

import com.chargebee.sdk.FileOp;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import java.io.IOException;

public enum IncludeFileHelpers implements Helper<Object> {
  INCLUDE_FILE {
    @Override
    public CharSequence apply(final Object path, final Options options) throws IOException {
      return FileOp.fetchFileContent((String) path);
    }
  }
}
