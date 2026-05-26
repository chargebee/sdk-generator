package com.chargebee.sdk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

public abstract class FileOp {
  private FileOp() {}

  public static final class Composite extends FileOp {
    public final List<FileOp> ops;

    public Composite(List<FileOp> ops) {
      this.ops = ops;
    }

    @Override
    public void exec() throws IOException {
      for (FileOp op : ops) {
        op.exec();
      }
    }
  }

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

  public static final class PrependString extends FileOp {

    public final String baseFilePath;
    public final String fileName;
    public final String contentToPrepend;

    public PrependString(String baseFilePath, String fileName, String contentToPrepend) {
      this.baseFilePath = baseFilePath;
      this.fileName = fileName;
      this.contentToPrepend = contentToPrepend;
    }

    @Override
    public void exec() throws IOException {
      Path filePath = Paths.get(baseFilePath, fileName);
      String existingContent = "";
      if (Files.exists(filePath)) {
        existingContent = Files.readString(filePath);
      }
      Files.writeString(
          filePath,
          contentToPrepend + existingContent,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.CREATE);
    }
  }

  public static String fetchFileContent(String filePath) throws IOException {
    return new String(Files.readAllBytes(Paths.get(filePath)));
  }
}
