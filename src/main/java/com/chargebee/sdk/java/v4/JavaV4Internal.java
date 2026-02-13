package com.chargebee.sdk.java.v4;

import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal SDK generator that extends the public JavaV4 generator with internal-only
 * infrastructure. Generates batch classes (BatchRequest, BatchEntry, BatchResult, BatchConstants)
 * and the InternalChargebeeClient into the {@code com.chargebee.v4.internal} package.
 */
public class JavaV4Internal extends JavaV4 {

  private static final String[] INTERNAL_TEMPLATE_FILES = {
    "batch.request", "batch.entry", "batch.result", "batch.constants", "internal.client"
  };

  private static final Map<String, String> INTERNAL_FILE_NAMES =
      Map.of(
          "batch.request", "BatchRequest.java",
          "batch.entry", "BatchEntry.java",
          "batch.result", "BatchResult.java",
          "batch.constants", "BatchConstants.java",
          "internal.client", "InternalChargebeeClient.java");

  @Override
  public List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    List<FileOp> fileOps = new ArrayList<>(super.generateSDK(outputDirectoryPath, spec));
    fileOps.addAll(generateInternalInfrastructure(outputDirectoryPath));
    return fileOps;
  }

  private List<FileOp> generateInternalInfrastructure(String outputDirectoryPath)
      throws IOException {
    List<FileOp> fileOps = new ArrayList<>();
    String internalDir = outputDirectoryPath + "/com/chargebee/v4/internal";
    fileOps.add(new FileOp.CreateDirectory(internalDir, ""));

    for (String templateName : INTERNAL_TEMPLATE_FILES) {
      String resourcePath = "/templates/java/next/" + templateName + ".hbs";
      String content = readResourceFile(resourcePath);
      String formatted = JavaFormatter.formatSafely(content);
      String fileName = INTERNAL_FILE_NAMES.get(templateName);
      fileOps.add(new FileOp.WriteString(internalDir, fileName, formatted));
    }

    return fileOps;
  }

  private String readResourceFile(String resourcePath) throws IOException {
    try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Resource not found: " + resourcePath);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    Map<String, String> templates = new HashMap<>(super.templatesDefinition());
    // Internal templates are read as raw files, not compiled as Handlebars
    // so no need to add them here
    return templates;
  }
}
