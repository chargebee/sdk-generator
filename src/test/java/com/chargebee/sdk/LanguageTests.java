package com.chargebee.sdk;

import static org.assertj.core.api.Assertions.assertThat;

public class LanguageTests {
  protected void assertCreateDirectoryFileOp(
      FileOp fileOp, String expectedBasePath, String expectedDirectoryName) {
    assertThat(fileOp).isInstanceOf(FileOp.CreateDirectory.class);
    var createDirectoryFileOp = (FileOp.CreateDirectory) fileOp;
    assertThat(createDirectoryFileOp.basePath).isEqualTo(expectedBasePath);
    assertThat(createDirectoryFileOp.directoryName).isEqualTo(expectedDirectoryName);
  }

  protected void assertWriteStringFileOp(
      FileOp fileOp, String expectedBaseFilePath, String expectedFileName) {
    assertThat(fileOp).isInstanceOf(FileOp.WriteString.class);
    var writeStringFileOp = (FileOp.WriteString) fileOp;
    assertThat(writeStringFileOp.baseFilePath).isEqualTo(expectedBaseFilePath);
    assertThat(writeStringFileOp.fileName).isEqualTo(expectedFileName);
  }

  protected void assertWriteStringFileOp(
      FileOp fileOp,
      String expectedBaseFilePath,
      String expectedFileName,
      String expectedFileContent) {
    assertThat(fileOp).isInstanceOf(FileOp.WriteString.class);
    var writeStringFileOp = (FileOp.WriteString) fileOp;
    assertThat(writeStringFileOp.baseFilePath).isEqualTo(expectedBaseFilePath);
    assertThat(writeStringFileOp.fileName).isEqualTo(expectedFileName);
    assertThat(writeStringFileOp.fileContent.replaceAll("\\s+", ""))
        .isEqualTo(expectedFileContent.replaceAll("\\s+", ""));
  }
}
