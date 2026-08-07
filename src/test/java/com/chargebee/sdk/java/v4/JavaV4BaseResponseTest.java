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

@DisplayName("JavaV4 BaseResponse Generator")
class JavaV4BaseResponseTest {

  private JavaV4 generator;
  private static final String OUTPUT_PATH = "/test/output";

  @BeforeEach
  void setUp() {
    generator = new JavaV4();
  }

  private List<FileOp> generate() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var retrieveOp =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer-id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    Spec spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}", retrieveOp)
            .done();
    return generator.generate(OUTPUT_PATH, spec);
  }

  @Test
  @DisplayName("Should null-safe header lookup in BaseResponse and list responses")
  void shouldNullSafeHeaderLookup() throws IOException {
    List<FileOp> fileOps = generate();

    FileOp.WriteString baseResponse =
        fileOps.stream()
            .filter(op -> op instanceof FileOp.WriteString)
            .map(op -> (FileOp.WriteString) op)
            .filter(op -> op.fileName.equals("BaseResponse.java"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("BaseResponse.java not generated"));

    assertThat(baseResponse.fileContent)
        .contains(".filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(name))");

    boolean listResponseHasNullSafeFilter =
        fileOps.stream()
            .filter(op -> op instanceof FileOp.WriteString)
            .map(op -> (FileOp.WriteString) op)
            .filter(op -> op.fileName.endsWith("Response.java"))
            .anyMatch(
                op ->
                    op.fileContent.contains("header(String name)")
                        && op.fileContent.contains(
                            ".filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(name))"));

    assertThat(listResponseHasNullSafeFilter).isTrue();
  }
}
