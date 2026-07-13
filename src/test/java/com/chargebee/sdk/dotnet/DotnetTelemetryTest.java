package com.chargebee.sdk.dotnet;

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

@DisplayName("Dotnet Telemetry Generator")
class DotnetTelemetryTest {

  private Dotnet generator;
  private static final String OUTPUT_PATH = "/test/output";

  @BeforeEach
  void setUp() {
    generator = new Dotnet();
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
  @DisplayName("Should generate telemetry package under Telemetry/")
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
            "TelemetryAttributeKeys.cs",
            "RequestTelemetryContext.cs",
            "RequestTelemetryError.cs",
            "RequestTelemetryResult.cs",
            "ITelemetryAdapter.cs",
            "TelemetrySupport.cs")) {
      FileOp.WriteString writeOp = findWriteOp(fileOps, "/Telemetry/" + fileName);
      assertThat(writeOp.baseFilePath).isEqualTo(expectedDir);
    }
  }

  @Test
  @DisplayName("Should emit block-form telemetry wiring that compiles for EntityRequest<Type>")
  void shouldEmitBlockFormTelemetryWiring() throws IOException {
    List<FileOp> fileOps = generate();

    FileOp.WriteString customer = findWriteOp(fileOps, "/Models/Customer.cs");
    assertThat(customer.fileContent)
        .contains("var request = new EntityRequest<Type>(url, HttpMethod.GET);")
        .contains("request.SetTelemetryResource(\"customer\");")
        .contains("request.SetTelemetryOperation(\"retrieve\");")
        .contains("return request;")
        .doesNotContain(
            "new EntityRequest<Type>(url, HttpMethod.GET).SetTelemetryResource(\"customer\")");
  }

  @Test
  @DisplayName("Should emit chargebee-dotnet SDK identifier and adapter hooks")
  void shouldEmitSdkIdentifierAndAdapterHooks() throws IOException {
    List<FileOp> fileOps = generate();

    FileOp.WriteString attributeKeys =
        findWriteOp(fileOps, "/Telemetry/TelemetryAttributeKeys.cs");
    assertThat(attributeKeys.fileContent).contains("SDK_NAME = \"chargebee-dotnet\"");
    assertThat(attributeKeys.fileContent).contains("URL_FULL = \"url.full\"");

    FileOp.WriteString adapter = findWriteOp(fileOps, "/Telemetry/ITelemetryAdapter.cs");
    assertThat(adapter.fileContent).contains("interface ITelemetryAdapter");
    assertThat(adapter.fileContent).contains("OnRequestStart");
    assertThat(adapter.fileContent).contains("OnRequestEnd");
    assertThat(adapter.fileContent).contains("zero overhead");
    assertThat(adapter.fileContent).contains("including before retries");
    assertThat(adapter.fileContent).contains("Optional telemetry adapter");

    FileOp.WriteString support = findWriteOp(fileOps, "/Telemetry/TelemetrySupport.cs");
    assertThat(support.fileContent).contains("BuildRequestTelemetryContext");
    assertThat(support.fileContent).contains("ExtractRequestTelemetryError");
    assertThat(support.fileContent).contains("ExtractHttpStatusCode");
    assertThat(support.fileContent).contains("ApiException");
    assertThat(support.fileContent).doesNotContain("ERROR_TYPE] = httpStatusCode.ToString()");
    assertThat(support.fileContent)
        .contains("ERROR_TYPE] = error.ChargebeeApiErrorType");
  }
}
