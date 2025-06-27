package com.chargebee.sdk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public abstract class FileOp {
  private FileOp() {}

  public abstract void exec() throws IOException;

  public static final class CreateDirectory extends FileOp {
    public final String basePath;
    public final String directoryName;

    public CreateDirectory(String basePath, String directoryName) {
      this.basePath = basePath;
      this.directoryName = directoryName;
    }

    @Override
    public void exec() throws IOException {
      Files.createDirectories(Paths.get(basePath, directoryName));
    }
  }

  public static final class WriteString extends FileOp {

    public final String baseFilePath;
    public final String fileName;
    public final String fileContent;

    public WriteString(String baseFilePath, String fileName, String fileContent) {
      this.baseFilePath = baseFilePath;
      this.fileName = fileName;
      this.fileContent = fileContent;
    }

    @Override
    public void exec() throws IOException {
      Files.writeString(
          Paths.get(baseFilePath, fileName),
          fileContent,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.CREATE);
    }
  }

  public static String fetchFileContent(String filePath) throws IOException {
    return new String(Files.readAllBytes(Paths.get(filePath)));
  }
}
