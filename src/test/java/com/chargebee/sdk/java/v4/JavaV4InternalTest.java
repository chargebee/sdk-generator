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
import org.junit.jupiter.api.*;

/**
 * Tests for {@link JavaV4Internal} generator. Verifies that internal-only infrastructure files
 * (batch classes and InternalChargebeeClient) are generated correctly into the {@code
 * com.chargebee.v4.internal} package.
 */
@DisplayName("JavaV4Internal Generator")
class JavaV4InternalTest {

  private JavaV4Internal generator;
  private static final String OUTPUT_PATH = "/test/output";

  @BeforeEach
  void setUp() {
    generator = new JavaV4Internal();
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

  private boolean hasWriteOp(List<FileOp> fileOps, String fileName) {
    return fileOps.stream()
        .filter(op -> op instanceof FileOp.WriteString)
        .map(op -> (FileOp.WriteString) op)
        .anyMatch(op -> op.fileName.equals(fileName));
  }

  // === Internal Infrastructure Generation ===

  @Nested
  @DisplayName("Internal Infrastructure Files")
  class InternalInfrastructureTests {

    @Test
    @DisplayName("Should generate InternalChargebeeClient.java in internal directory")
    void shouldGenerateInternalChargebeeClient() throws IOException {
      List<FileOp> fileOps = generate();

      assertThat(hasWriteOp(fileOps, "InternalChargebeeClient.java")).isTrue();

      FileOp.WriteString writeOp = findWriteOp(fileOps, "InternalChargebeeClient.java");
      assertThat(writeOp.baseFilePath).endsWith("/com/chargebee/v4/internal");
    }

    @Test
    @DisplayName("Should generate all batch infrastructure files")
    void shouldGenerateAllBatchFiles() throws IOException {
      List<FileOp> fileOps = generate();

      assertThat(hasWriteOp(fileOps, "BatchRequest.java")).isTrue();
      assertThat(hasWriteOp(fileOps, "BatchEntry.java")).isTrue();
      assertThat(hasWriteOp(fileOps, "BatchResult.java")).isTrue();
      assertThat(hasWriteOp(fileOps, "BatchConstants.java")).isTrue();
    }

    @Test
    @DisplayName("Should place all internal files in com.chargebee.v4.internal directory")
    void shouldPlaceFilesInInternalDirectory() throws IOException {
      List<FileOp> fileOps = generate();

      String expectedDir = OUTPUT_PATH + "/com/chargebee/v4/internal";

      for (String fileName :
          List.of(
              "InternalChargebeeClient.java",
              "BatchRequest.java",
              "BatchEntry.java",
              "BatchResult.java",
              "BatchConstants.java")) {
        FileOp.WriteString writeOp = findWriteOp(fileOps, fileName);
        assertThat(writeOp.baseFilePath).isEqualTo(expectedDir);
      }
    }

    @Test
    @DisplayName("Should create internal directory")
    void shouldCreateInternalDirectory() throws IOException {
      List<FileOp> fileOps = generate();

      boolean hasInternalDir =
          fileOps.stream()
              .filter(op -> op instanceof FileOp.CreateDirectory)
              .map(op -> (FileOp.CreateDirectory) op)
              .anyMatch(op -> op.basePath.equals(OUTPUT_PATH + "/com/chargebee/v4/internal"));
      assertThat(hasInternalDir).isTrue();
    }

    @Test
    @DisplayName("Should also generate standard public SDK files")
    void shouldAlsoGeneratePublicSdkFiles() throws IOException {
      List<FileOp> fileOps = generate();

      // Verify some standard files from the parent JavaV4 generator are present
      assertThat(hasWriteOp(fileOps, "CustomerService.java")).isTrue();
    }
  }

  // === InternalChargebeeClient Content ===

  @Nested
  @DisplayName("InternalChargebeeClient Content")
  class InternalChargebeeClientContentTests {

    @Test
    @DisplayName("Should have internal User-Agent prefix constant")
    void shouldHaveInternalUserAgentPrefix() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "InternalChargebeeClient.java");

      assertThat(writeOp.fileContent).contains("Chargebee-Java-Internal-Client");
    }

    @Test
    @DisplayName("Should have correct package declaration")
    void shouldHaveCorrectPackage() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "InternalChargebeeClient.java");

      assertThat(writeOp.fileContent).contains("package com.chargebee.v4.internal;");
    }

    @Test
    @DisplayName("Should have builder with mandatory serviceName parameter")
    void shouldHaveBuilderWithServiceName() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "InternalChargebeeClient.java");

      assertThat(writeOp.fileContent)
          .contains("builder(String apiKey, String siteName, String serviceName)");
    }

    @Test
    @DisplayName("Should validate serviceName is not null or empty")
    void shouldValidateServiceName() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "InternalChargebeeClient.java");

      assertThat(writeOp.fileContent)
          .contains("serviceName == null || serviceName.trim().isEmpty()");
      assertThat(writeOp.fileContent).contains("ConfigurationException");
    }

    @Test
    @DisplayName("Should construct User-Agent with version and serviceName")
    void shouldConstructUserAgentWithVersionAndServiceName() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "InternalChargebeeClient.java");

      assertThat(writeOp.fileContent)
          .contains("USER_AGENT_PREFIX + \" v\" + version + \"-\" + serviceName");
    }

    @Test
    @DisplayName("Should set User-Agent via delegate header method")
    void shouldSetUserAgentViaDelegateHeader() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "InternalChargebeeClient.java");

      assertThat(writeOp.fileContent).contains("delegate.header(\"User-Agent\", userAgent)");
    }

    @Test
    @DisplayName("Should delegate to ChargebeeClient.Builder")
    void shouldDelegateToChargebeeClientBuilder() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "InternalChargebeeClient.java");

      assertThat(writeOp.fileContent).contains("ChargebeeClient.builder(apiKey, siteName)");
      assertThat(writeOp.fileContent).contains("delegate.build()");
    }

    @Test
    @DisplayName("Should return ChargebeeClient from build method")
    void shouldReturnChargebeeClientFromBuild() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "InternalChargebeeClient.java");

      assertThat(writeOp.fileContent).contains("public ChargebeeClient build()");
    }

    @Test
    @DisplayName("Should read version from version.properties")
    void shouldReadVersionFromProperties() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "InternalChargebeeClient.java");

      assertThat(writeOp.fileContent).contains("version.properties");
      assertThat(writeOp.fileContent).contains("getProperty(\"version\"");
    }

    @Test
    @DisplayName("Should not be instantiable")
    void shouldNotBeInstantiable() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "InternalChargebeeClient.java");

      assertThat(writeOp.fileContent).contains("private InternalChargebeeClient()");
    }
  }

  // === BatchRequest Client Headers Fix ===

  @Nested
  @DisplayName("BatchRequest Client Headers Propagation")
  class BatchRequestClientHeadersTests {

    @Test
    @DisplayName("Should propagate clientHeaders in batch request")
    void shouldPropagateClientHeadersInBatchRequest() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "BatchRequest.java");

      assertThat(writeOp.fileContent).contains("client.getClientHeaders().getHeaders()");
    }

    @Test
    @DisplayName("Should set client headers on request builder before building")
    void shouldSetClientHeadersOnRequestBuilder() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "BatchRequest.java");

      assertThat(writeOp.fileContent).contains("reqBuilder.header(h.getKey(), h.getValue())");
    }
  }

  // === BatchRequest Subdomain Support ===

  @Nested
  @DisplayName("BatchRequest Subdomain Routing")
  class BatchRequestSubdomainTests {

    @Test
    @DisplayName("Should build subdomain URL internally without exposing it on client")
    void shouldBuildSubDomainUrlInternally() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "BatchRequest.java");

      assertThat(writeOp.fileContent).contains("baseUrlWithSubDomain(subDomain.getValue())");
      assertThat(writeOp.fileContent).doesNotContain("client.getBaseUrlWithSubDomain");
    }

    @Test
    @DisplayName("Should fall back to getBaseUrl when subdomain is null")
    void shouldFallBackToBaseUrlWhenSubDomainNull() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "BatchRequest.java");

      assertThat(writeOp.fileContent).contains("client.getBaseUrl()");
    }

    @Test
    @DisplayName("Should check subdomain for null and empty before using it")
    void shouldCheckSubDomainForNullAndEmpty() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "BatchRequest.java");

      assertThat(writeOp.fileContent).contains("subDomain != null");
    }

    @Test
    @DisplayName("Should store subdomain field from constructor")
    void shouldStoreSubDomainFromConstructor() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "BatchRequest.java");

      assertThat(writeOp.fileContent).contains("private final SubDomain subDomain");
      assertThat(writeOp.fileContent).contains("this.subDomain = subDomain");
    }

    @Test
    @DisplayName("Should have constructor overload without subdomain that defaults to null")
    void shouldHaveConstructorOverloadWithoutSubDomain() throws IOException {
      List<FileOp> fileOps = generate();
      FileOp.WriteString writeOp = findWriteOp(fileOps, "BatchRequest.java");

      assertThat(writeOp.fileContent)
          .contains(
              "public BatchRequest(String uri, String pathParamName, ChargebeeClient client)");
      assertThat(writeOp.fileContent).contains("this(uri, pathParamName, null, client)");
    }
  }
}
