package com.chargebee.sdk.python.v3;

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

@DisplayName("PythonV3 Telemetry Generator")
class PythonV3TelemetryTest {

  private PythonV3 generator;
  private static final String OUTPUT_PATH = "/test/output";

  @BeforeEach
  void setUp() {
    generator = new PythonV3();
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
  @DisplayName("Should generate telemetry package under chargebee/telemetry")
  void shouldGenerateTelemetryPackage() throws IOException {
    List<FileOp> fileOps = generate();
    String expectedDir = OUTPUT_PATH + "/telemetry";

    assertThat(
            fileOps.stream()
                .filter(op -> op instanceof FileOp.CreateDirectory)
                .map(op -> (FileOp.CreateDirectory) op)
                .anyMatch(op -> op.basePath.equals(expectedDir)))
        .isTrue();

    for (String fileName : List.of("types.py", "telemetry_support.py", "__init__.py")) {
      FileOp.WriteString writeOp = findWriteOp(fileOps, "/telemetry/" + fileName);
      assertThat(writeOp.baseFilePath).isEqualTo(expectedDir);
    }
  }

  @Test
  @DisplayName("Should emit chargebee-python SDK identifier and adapter hooks")
  void shouldEmitSdkIdentifierAndAdapterHooks() throws IOException {
    List<FileOp> fileOps = generate();

    FileOp.WriteString types = findWriteOp(fileOps, "/telemetry/types.py");
    assertThat(types.fileContent).contains("CHARGEBEE_SDK_NAME = \"chargebee-python\"");
    assertThat(types.fileContent).contains("URL_FULL = \"url.full\"");
    assertThat(types.fileContent).contains("CHARGEBEE_RESOURCE = \"chargebee.resource\"");

    FileOp.WriteString support = findWriteOp(fileOps, "/telemetry/telemetry_support.py");
    assertThat(support.fileContent).contains("class TelemetryAdapter(Protocol)");
    assertThat(support.fileContent).contains("on_request_start");
    assertThat(support.fileContent).contains("on_request_end");
    assertThat(support.fileContent).contains("zero overhead");
    assertThat(support.fileContent).contains("build_request_telemetry_context");
    assertThat(support.fileContent).contains("extract_request_telemetry_error");
    assertThat(support.fileContent).contains("extract_http_status_code");
    assertThat(support.fileContent).contains("isinstance(err, APIError)");
  }

  @Test
  @DisplayName("Should pass resource and operation metadata from generated operations")
  void shouldPassResourceAndOperationMetadata() throws IOException {
    List<FileOp> fileOps = generate();

    FileOp.WriteString operations =
        findWriteOp(fileOps, "/models/customer/operations.py");
    assertThat(operations.fileContent).contains("resource=\"customer\"");
    assertThat(operations.fileContent).contains("operation=\"retrieve\"");
  }
}
