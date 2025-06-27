package com.chargebee.sdk.php.v4.generators;

import com.chargebee.sdk.FileOp;
import java.io.IOException;
import java.util.List;

public interface FileGenerator {
  List<FileOp> generate(String outputPath, List<?> items) throws IOException;

  default FileOp generateSingle(String outputPath, List<?> items) throws IOException {
    throw new UnsupportedOperationException("Single file generation not supported");
  }
}
