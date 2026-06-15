package com.chargebee.sdk.java.v4;

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

@DisplayName("JavaV4 Telemetry Generator")
class JavaV4TelemetryTest {

  private JavaV4 generator;
  private static final String OUTPUT_PATH = "/test/output";

  @BeforeEach
  void setUp() {
    generator = new JavaV4();
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

  private FileOp.WriteString findWriteOp(List<FileOp> fileOps, String fileName) {
    return fileOps.stream()
        .filter(op -> op instanceof FileOp.WriteString)
        .map(op -> (FileOp.WriteString) op)
        .filter(op -> op.fileName.equals(fileName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected file not found: " + fileName));
  }

  @Test
  @DisplayName("Should generate telemetry package under com.chargebee.v4.telemetry")
  void shouldGenerateTelemetryPackage() throws IOException {
    List<FileOp> fileOps = generate();
    String expectedDir = OUTPUT_PATH + "/com/chargebee/v4/telemetry";

    assertThat(
            fileOps.stream()
                .filter(op -> op instanceof FileOp.CreateDirectory)
                .map(op -> (FileOp.CreateDirectory) op)
                .anyMatch(op -> op.basePath.equals(expectedDir)))
        .isTrue();

    for (String fileName :
        List.of(
            "TelemetryAttributeKeys.java",
            "RequestTelemetryContext.java",
            "RequestTelemetryError.java",
            "RequestTelemetryResult.java",
            "TelemetryAdapter.java",
            "TelemetrySupport.java")) {
      FileOp.WriteString writeOp = findWriteOp(fileOps, fileName);
      assertThat(writeOp.baseFilePath).isEqualTo(expectedDir);
    }
  }

  @Test
  @DisplayName("Should emit chargebee-java SDK identifier and span attribute keys")
  void shouldEmitSdkIdentifierAndAttributeKeys() throws IOException {
    List<FileOp> fileOps = generate();

    FileOp.WriteString attributeKeys = findWriteOp(fileOps, "TelemetryAttributeKeys.java");
    assertThat(attributeKeys.fileContent).contains("SDK_NAME = \"chargebee-java\"");
    assertThat(attributeKeys.fileContent).contains("URL_FULL = \"url.full\"");
    assertThat(attributeKeys.fileContent).contains("CHARGEBEE_RESOURCE = \"chargebee.resource\"");

    FileOp.WriteString adapter = findWriteOp(fileOps, "TelemetryAdapter.java");
    assertThat(adapter.fileContent).contains("interface TelemetryAdapter");
    assertThat(adapter.fileContent).contains("onRequestStart");
    assertThat(adapter.fileContent).contains("onRequestEnd");
    assertThat(adapter.fileContent).contains("zero overhead");

    FileOp.WriteString support = findWriteOp(fileOps, "TelemetrySupport.java");
    assertThat(support.fileContent).contains("buildRequestTelemetryContext");
    assertThat(support.fileContent).contains("extractRequestTelemetryError");
    assertThat(support.fileContent).contains("extractHttpStatusCode");
    assertThat(support.fileContent).contains("instanceof APIException");
  }

  @Test
  @DisplayName("JavaV4Internal should inherit telemetry generation from JavaV4")
  void internalGeneratorShouldIncludeTelemetry() throws IOException {
    JavaV4Internal internalGenerator = new JavaV4Internal();
    List<FileOp> fileOps = internalGenerator.generate(OUTPUT_PATH, minimalSpec());

    assertThat(findWriteOp(fileOps, "TelemetrySupport.java").baseFilePath)
        .endsWith("/com/chargebee/v4/telemetry");
    assertThat(findWriteOp(fileOps, "InternalChargebeeClient.java").baseFilePath)
        .endsWith("/com/chargebee/v4/internal");
  }
}
