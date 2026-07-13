package com.chargebee.sdk.php.v4;

import static com.chargebee.sdk.test_data.OperationBuilder.buildOperation;
import static com.chargebee.sdk.test_data.ResourceBuilder.buildResource;
import static com.chargebee.sdk.test_data.ResourceResponseParam.resourceResponseParam;
import static com.chargebee.sdk.test_data.SpecBuilder.buildSpec;
import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PHP_V4 Telemetry Generator")
class PHP_V4TelemetryTest {

  private PHP_V4 generator;
  private static final String OUTPUT_PATH = "/test/output";

  @BeforeEach
  void setUp() {
    generator = new PHP_V4();
  }

  private Spec minimalSpec() {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var retrieveOp =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer-id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    return buildSpec()
        .withResource(customer)
        .withOperation("/customers/{customer-id}", retrieveOp)
        .done();
  }

  private List<FileOp> generate() throws IOException {
    return generator.generate(OUTPUT_PATH, minimalSpec());
  }

  private FileOp.WriteString findWriteOp(List<FileOp> fileOps, String relativePath) {
    return fileOps.stream()
        .filter(op -> op instanceof FileOp.WriteString)
        .map(op -> (FileOp.WriteString) op)
        .filter(op -> (op.baseFilePath + "/" + op.fileName).endsWith(relativePath))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected file not found: " + relativePath));
  }

  @Test
  @DisplayName("Should generate telemetry package under src/Telemetry")
  void shouldGenerateTelemetryPackage() throws IOException {
    List<FileOp> fileOps = generate();
    String expectedDir = OUTPUT_PATH + "/Telemetry";

    assertThat(
            fileOps.stream()
                .filter(op -> op instanceof FileOp.CreateDirectory)
                .map(op -> (FileOp.CreateDirectory) op)
                .anyMatch(op -> op.basePath.equals(expectedDir)))
        .isTrue();

    for (String fileName :
        List.of(
            "TelemetryAttributeKeys.php",
            "RequestTelemetryContext.php",
            "RequestTelemetryError.php",
            "RequestTelemetryResult.php",
            "TelemetryAdapter.php",
            "TelemetrySupport.php")) {
      FileOp.WriteString writeOp = findWriteOp(fileOps, "/Telemetry/" + fileName);
      assertThat(writeOp.baseFilePath).isEqualTo(expectedDir);
    }
  }

  @Test
  @DisplayName("Should emit chargebee-php SDK identifier and adapter hooks")
  void shouldEmitSdkIdentifierAndAdapterHooks() throws IOException {
    List<FileOp> fileOps = generate();

    FileOp.WriteString attributeKeys = findWriteOp(fileOps, "/Telemetry/TelemetryAttributeKeys.php");
    assertThat(attributeKeys.fileContent).contains("SDK_NAME = 'chargebee-php'");
    assertThat(attributeKeys.fileContent).contains("URL_FULL = 'url.full'");
    assertThat(attributeKeys.fileContent).contains("CHARGEBEE_RESOURCE = 'chargebee.resource'");

    FileOp.WriteString adapter = findWriteOp(fileOps, "/Telemetry/TelemetryAdapter.php");
    assertThat(adapter.fileContent).contains("interface TelemetryAdapter");
    assertThat(adapter.fileContent).contains("onRequestStart");
    assertThat(adapter.fileContent).contains("onRequestEnd");
    assertThat(adapter.fileContent).contains("zero overhead");

    FileOp.WriteString support = findWriteOp(fileOps, "/Telemetry/TelemetrySupport.php");
    assertThat(support.fileContent).contains("buildRequestTelemetryContext");
    assertThat(support.fileContent).contains("extractRequestTelemetryError");
    assertThat(support.fileContent).contains("extractHttpStatusCode");
    assertThat(support.fileContent).contains("instanceof APIError");
  }

  @Test
  @DisplayName("Should pass resource and operation metadata from generated actions")
  void shouldPassResourceAndOperationMetadata() throws IOException {
    List<FileOp> fileOps = generate();

    FileOp.WriteString actions = findWriteOp(fileOps, "/Actions/CustomerActions.php");
    assertThat(actions.fileContent).contains("->withTelemetryResource(\"customer\")");
    assertThat(actions.fileContent).contains("->withTelemetryOperation(\"retrieve\")");
  }
}
