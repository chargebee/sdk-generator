package com.chargebee.sdk.go.v3;

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

@DisplayName("Go_V3 Telemetry Generator")
class Go_V3TelemetryTest {

  private Go_V3 generator;
  private static final String OUTPUT_PATH = "/test/output";

  @BeforeEach
  void setUp() {
    generator = new Go_V3();
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
  @DisplayName("Should generate telemetry package under telemetry/")
  void shouldGenerateTelemetryPackage() throws IOException {
    List<FileOp> fileOps = generate();
    String expectedDir = OUTPUT_PATH + "/telemetry";

    assertThat(
            fileOps.stream()
                .filter(op -> op instanceof FileOp.CreateDirectory)
                .map(op -> (FileOp.CreateDirectory) op)
                .anyMatch(op -> op.basePath.equals(expectedDir)))
        .isTrue();

    for (String fileName :
        List.of(
            "attribute_keys.go",
            "request_telemetry_context.go",
            "request_telemetry_error.go",
            "request_telemetry_result.go",
            "telemetry_adapter.go",
            "telemetry_support.go")) {
      FileOp.WriteString writeOp = findWriteOp(fileOps, "/telemetry/" + fileName);
      assertThat(writeOp.baseFilePath).isEqualTo(expectedDir);
    }
  }

  @Test
  @DisplayName("Should emit chargebee-go SDK identifier and adapter hooks")
  void shouldEmitSdkIdentifierAndAdapterHooks() throws IOException {
    List<FileOp> fileOps = generate();

    FileOp.WriteString attributeKeys = findWriteOp(fileOps, "/telemetry/attribute_keys.go");
    assertThat(attributeKeys.fileContent).contains("SDKName");
    assertThat(attributeKeys.fileContent).contains("= \"chargebee-go\"");
    assertThat(attributeKeys.fileContent).contains("URLFull");
    assertThat(attributeKeys.fileContent).contains("= \"url.full\"");

    FileOp.WriteString adapter = findWriteOp(fileOps, "/telemetry/telemetry_adapter.go");
    assertThat(adapter.fileContent).contains("type TelemetryAdapter interface");
    assertThat(adapter.fileContent).contains("OnRequestStart");
    assertThat(adapter.fileContent).contains("OnRequestEnd");
    assertThat(adapter.fileContent).contains("zero overhead");
    assertThat(adapter.fileContent).contains("including before retries");
    assertThat(adapter.fileContent).contains("optional telemetry adapter");

    FileOp.WriteString support = findWriteOp(fileOps, "/telemetry/telemetry_support.go");
    assertThat(support.fileContent).contains("BuildRequestTelemetryContext");
    assertThat(support.fileContent).contains("BuildRequestTelemetryResult");
  }

  @Test
  @DisplayName("Should pass resource and operation metadata from generated actions")
  void shouldPassResourceAndOperationMetadata() throws IOException {
    List<FileOp> fileOps = generate();

    FileOp.WriteString actions = findWriteOp(fileOps, "/actions/customer/customer.go");
    assertThat(actions.fileContent).contains(".WithTelemetryResource(\"customer\")");
    assertThat(actions.fileContent).contains(".WithTelemetryOperation(\"retrieve\")");
  }
}
