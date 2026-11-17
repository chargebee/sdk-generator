package com.chargebee.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.openapi.ApiVersion;
import com.chargebee.openapi.Spec;
import com.chargebee.sdk.dotnet.Dotnet;
import com.chargebee.sdk.go.v3.Go_V3;
import com.chargebee.sdk.go.v4.Go_V4;
import com.chargebee.sdk.java.GenerationMode;
import com.chargebee.sdk.java.JarType;
import com.chargebee.sdk.java.Java;
import com.chargebee.sdk.java.v4.JavaV4;
import com.chargebee.sdk.java.v4.JavaV4Internal;
import com.chargebee.sdk.node.NodeV3;
import com.chargebee.sdk.php.v4.PHP_V4;
import com.chargebee.sdk.python.v3.PythonV3;
import com.chargebee.sdk.ruby.Ruby;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiVersionFileTest {
  private static final String SPEC_VERSION = "2026-10-29.1fb45a134bd39092ac91857ed8597de9f919785a";

  @TempDir Path sdkRoot;

  private static Spec spec(String version) {
    return new Spec(new OpenAPI().info(new Info().version(version)).components(new Components()));
  }

  private String outputDir(String relative) throws IOException {
    Files.writeString(sdkRoot.resolve("VERSION"), "1.0.0\n");
    Path output = sdkRoot.resolve(relative);
    Files.createDirectories(output);
    return output.toString();
  }

  private void assertWritesAtSdkRoot(Language language, String relativeOutputDir)
      throws IOException {
    FileOp op = language.openApiVersionFile(outputDir(relativeOutputDir), spec(SPEC_VERSION));

    assertThat(op).isInstanceOf(FileOp.WriteString.class);
    var write = (FileOp.WriteString) op;
    assertThat(Path.of(write.baseFilePath)).isEqualTo(sdkRoot.toAbsolutePath().normalize());
    assertThat(write.fileName).isEqualTo("OPENAPI_VERSION");
    assertThat(write.fileContent).isEqualTo(SPEC_VERSION + "\n");
  }

  @Test
  void writesAtSdkRootForEveryActiveSdk() throws IOException {
    assertWritesAtSdkRoot(
        new Java(GenerationMode.EXTERNAL, ApiVersion.V2, JarType.HVC),
        "src/main/java/com/chargebee");
    assertWritesAtSdkRoot(new JavaV4(), "src/main/java");
    assertWritesAtSdkRoot(new Go_V3(), ".");
    assertWritesAtSdkRoot(new Go_V4(), ".");
    assertWritesAtSdkRoot(new PHP_V4(), "src");
    assertWritesAtSdkRoot(new PythonV3(), "chargebee");
    assertWritesAtSdkRoot(new NodeV3(), "src/resources");
    assertWritesAtSdkRoot(new Ruby(), "lib/chargebee");
    assertWritesAtSdkRoot(new Dotnet(), "ChargeBee");
  }

  @Test
  void skipsInternalAndDeprecatedGenerators() throws IOException {
    String output = outputDir("src/main/java");
    for (Language language :
        List.of(
            new JavaV4Internal(),
            new Java(GenerationMode.INTERNAL, ApiVersion.V2, JarType.HVC),
            new com.chargebee.sdk.python.Python(),
            new com.chargebee.sdk.node.Node(),
            new com.chargebee.sdk.php.Php())) {
      assertThat(language.openApiVersionFile(output, spec(SPEC_VERSION))).isNull();
    }
  }

  @Test
  void skipsWhenResolvedRootIsNotAnSdkRepo() throws IOException {
    Path output = sdkRoot.resolve("src");
    Files.createDirectories(output);

    assertThat(new PHP_V4().openApiVersionFile(output.toString(), spec(SPEC_VERSION))).isNull();
  }

  @Test
  void skipsWhenOutputHasTooFewParents() {
    assertThat(new JavaV4().openApiVersionFile("/java", spec(SPEC_VERSION))).isNull();
  }

  @Test
  void skipsWhenSpecHasNoVersion() throws IOException {
    assertThat(new PHP_V4().openApiVersionFile(outputDir("src"), spec(null))).isNull();
  }

  @Test
  void generateAppendsTheFileAfterTheSdkFiles() throws IOException {
    String output = outputDir(".");

    List<FileOp> fileOps = new Go_V4().generate(output, spec(SPEC_VERSION));

    var last = (FileOp.WriteString) fileOps.get(fileOps.size() - 1);
    assertThat(last.fileName).isEqualTo("OPENAPI_VERSION");
    assertThat(last.fileContent).isEqualTo(SPEC_VERSION + "\n");
  }
}
