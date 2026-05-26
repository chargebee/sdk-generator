package com.chargebee.sdk.changelog;

import static com.chargebee.sdk.changelog.Constants.*;

import com.chargebee.openapi.*;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.changelog.generators.ChangeLogDocsGenerator;
import com.chargebee.sdk.changelog.generators.FileGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

public class ChangeLogDocs extends Language {
  public final String[] hiddenOverride = {"media"};
  private final ObjectMapper objectMapper;
  private Map<String, FileGenerator> generators;

  public ChangeLogDocs() {
    this.objectMapper = new ObjectMapper();
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of(CHANGELOG_DOCS, "/templates/changelog/changelog_docs.txt.hbs");
  }

  private Map<String, FileGenerator> initializeGenerators() {
    return Map.of("changelog_docs", new ChangeLogDocsGenerator(this));
  }

  @Override
  protected List<FileOp> generateSDK(String outputPath, Spec spec) throws IOException {
    return null;
  }

  @Override
  protected FileOp generateChangeLog(String outputPath, Spec oldVersion, Spec newerVersion)
      throws IOException {
    this.generators = initializeGenerators();
    return generators.get("changelog_docs").generate(outputPath, oldVersion, newerVersion);
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
