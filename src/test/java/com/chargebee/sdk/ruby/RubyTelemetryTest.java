package com.chargebee.sdk.ruby;

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

@DisplayName("Ruby Telemetry Generator")
class RubyTelemetryTest {

  private Ruby generator;
  private static final String OUTPUT_PATH = "/test/output";

  @BeforeEach
  void setUp() {
    generator = new Ruby();
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
            "telemetry_attribute_keys.rb",
            "request_telemetry_context.rb",
            "request_telemetry_error.rb",
            "request_telemetry_result.rb",
            "telemetry_adapter.rb",
            "telemetry_support.rb")) {
      FileOp.WriteString writeOp = findWriteOp(fileOps, "/telemetry/" + fileName);
      assertThat(writeOp.baseFilePath).isEqualTo(expectedDir);
    }
  }

  @Test
  @DisplayName("Should emit chargebee-ruby SDK identifier and adapter hooks")
  void shouldEmitSdkIdentifierAndAdapterHooks() throws IOException {
    List<FileOp> fileOps = generate();

    FileOp.WriteString attributeKeys = findWriteOp(fileOps, "/telemetry/telemetry_attribute_keys.rb");
    assertThat(attributeKeys.fileContent).contains("SDK_NAME = 'chargebee-ruby'");
    assertThat(attributeKeys.fileContent).contains("URL_FULL = 'url.full'");

    FileOp.WriteString adapter = findWriteOp(fileOps, "/telemetry/telemetry_adapter.rb");
    assertThat(adapter.fileContent).contains("module TelemetryAdapter");
    assertThat(adapter.fileContent).contains("on_request_start");
    assertThat(adapter.fileContent).contains("on_request_end");
    assertThat(adapter.fileContent).contains("zero overhead");
    assertThat(adapter.fileContent).contains("including before retries");
    assertThat(adapter.fileContent).contains("Optional telemetry adapter");

    FileOp.WriteString support = findWriteOp(fileOps, "/telemetry/telemetry_support.rb");
    assertThat(support.fileContent).contains("build_request_telemetry_context");
    assertThat(support.fileContent).contains("extract_request_telemetry_error");
    assertThat(support.fileContent).contains("extract_http_status_code");
    assertThat(support.fileContent).contains("ChargeBee::APIError");
    assertThat(support.fileContent).doesNotContain("ERROR_TYPE] = http_status_code.to_s");
    assertThat(support.fileContent)
        .contains("ERROR_TYPE] = error.chargebee_api_error_type");
  }

  @Test
  @DisplayName("Should pass resource and operation metadata from generated actions")
  void shouldPassResourceAndOperationMetadata() throws IOException {
    List<FileOp> fileOps = generate();

    FileOp.WriteString customer = findWriteOp(fileOps, "/models/customer.rb");
    assertThat(customer.fileContent).contains("\"customer\", \"retrieve\"");
  }
}
