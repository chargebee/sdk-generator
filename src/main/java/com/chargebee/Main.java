package com.chargebee;

import com.chargebee.openapi.ApiVersion;
import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.dotnet.Dotnet;
import com.chargebee.sdk.go.Go;
import com.chargebee.sdk.java.GenerationMode;
import com.chargebee.sdk.java.JarType;
import com.chargebee.sdk.java.Java;
import com.chargebee.sdk.node.Node;
import com.chargebee.sdk.node.NodeV3;
import com.chargebee.sdk.php.Php;
import com.chargebee.sdk.php.v4.PHP_V4;
import com.chargebee.sdk.python.Python;
import com.chargebee.sdk.python.v3.PythonV3;
import com.chargebee.sdk.ruby.Ruby;
import com.chargebee.sdk.ts.TypeScript;
import com.chargebee.sdk.ts.typing.TypeScriptTyping;
import com.chargebee.sdk.ts.typing.V3.TypeScriptTypings;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "",
    description =
        "Auto generate code for the provided language based on the Open API Specification")
class Generate implements Callable<Integer> {
  @Option(names = "-l", required = true, description = "Possible values: ${COMPLETION-CANDIDATES}")
  Lang lang;

  @Option(names = "-o", required = true, description = "Output directory path")
  String outputDirectoryPath;

  @Option(names = "-i", required = true, description = "Open API Spec file")
  String openAPISpecFilePath;

  @Override
  public Integer call() throws Exception {
    // Check if the OpenAPI spec file exists
    File specFile = new File(openAPISpecFilePath);
    if (!specFile.exists()) {
      System.err.println("\u001B[31m❌ Error: OpenAPI specification file not found\u001B[0m");
      System.err.println("\u001B[33m📁 File path: \u001B[0m" + openAPISpecFilePath);
      System.err.println(
          "\u001B[36m💡 Please ensure the file path is correct and the file exists.\u001B[0m");
      return 1;
    }

    if (!specFile.canRead()) {
      System.err.println("\u001B[31m❌ Error: Cannot read OpenAPI specification file\u001B[0m");
      System.err.println("\u001B[33m📁 File path: \u001B[0m" + openAPISpecFilePath);
      System.err.println("\u001B[36m🔒 Please check file permissions.\u001B[0m");
      return 1;
    }

    var openAPI = new OpenAPIV3Parser().read(openAPISpecFilePath);
    new JsonSchemaUpcaster(openAPI).upcastAllSchemas();
    Language language = Lang.sdkLanguage(lang);
    if (language.cleanDirectoryBeforeGenerate()) {
      cleanDirectory(Paths.get(outputDirectoryPath));
    }
    List<FileOp> fileOps = language.generate(outputDirectoryPath, new Spec(openAPI));
    for (var fileOp : fileOps) {
      fileOp.exec();
    }
    return 0;
  }

  private static void cleanDirectory(Path directoryPath) throws IOException {
    Files.createDirectories(directoryPath);
    Files.walk(directoryPath)
        .sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .filter(file -> !file.getPath().equals(directoryPath.toString()))
        .forEach(File::delete);
  }
}

enum Lang {
  TYPESCRIPT_TYPINGS,
  TYPESCRIPT_TYPINGS_V3,
  PYTHON,
  PYTHON_V3,
  NODE,
  NODE_V3,
  PHP,
  PHP_V4,
  RUBY,
  TYPESCRIPT,
  JAVA,
  DOTNET,
  GO,
  JAVA_INTERNAL_INT,
  JAVA_INTERNAL_INT_V2,
  JAVA_INTERNAL_HVC,
  JAVA_INTERNAL_HVC_V2;

  public static Language sdkLanguage(Lang lang) {
    if (lang == Lang.TYPESCRIPT_TYPINGS_V3) {
      return new TypeScriptTypings();
    }
    if (lang == Lang.TYPESCRIPT_TYPINGS) {
      return new TypeScriptTyping();
    }
    if (lang == Lang.NODE_V3) {
      return new NodeV3();
    }
    if (lang == Lang.PYTHON) {
      return new Python();
    }
    if (lang == Lang.PYTHON_V3) {
      return new PythonV3();
    }
    if (lang == Lang.NODE) {
      return new Node();
    }
    if (lang == Lang.PHP) {
      return new Php();
    }
    if (lang == Lang.RUBY) {
      return new Ruby();
    }
    if (lang == Lang.TYPESCRIPT) {
      return new TypeScript();
    }
    if (lang == Lang.JAVA) {
      return new Java(GenerationMode.EXTERNAL, ApiVersion.V2, JarType.HVC);
    }
    if (lang == Lang.JAVA_INTERNAL_INT) {
      return new Java(GenerationMode.INTERNAL, ApiVersion.V1, JarType.INT);
    }
    if (lang == Lang.JAVA_INTERNAL_INT_V2) {
      return new Java(GenerationMode.INTERNAL, ApiVersion.V2, JarType.INT);
    }
    if (lang == Lang.JAVA_INTERNAL_HVC) {
      return new Java(GenerationMode.INTERNAL, ApiVersion.V1, JarType.HVC);
    }
    if (lang == Lang.JAVA_INTERNAL_HVC_V2) {
      return new Java(GenerationMode.INTERNAL, ApiVersion.V2, JarType.HVC);
    }
    if (lang == Lang.DOTNET) {
      return new Dotnet();
    }
    if (lang == Lang.GO) {
      return new Go();
    }
    if (lang == Lang.PHP_V4) {
      return new PHP_V4();
    }
    throw new IllegalArgumentException("Lang " + lang + " not supported");
  }
}

public class Main {
  public static void main(String[] args) {
    int exitCode = new CommandLine(new Generate()).execute(args);
    System.exit(exitCode);
  }
}
