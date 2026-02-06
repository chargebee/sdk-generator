package com.chargebee.sdk.changelog.generators;

import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import java.io.IOException;
import java.util.List;

public interface FileGenerator {
  FileOp generate(String outputPath, Spec itemsOld, Spec itemsNew) throws IOException;

  default FileOp generateSingle(String outputPath, List<?> items) throws IOException {
    throw new UnsupportedOperationException("Single file generation not supported");
  }
}
