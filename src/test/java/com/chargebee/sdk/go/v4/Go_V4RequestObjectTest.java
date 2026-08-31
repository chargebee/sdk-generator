package com.chargebee.sdk.go.v4;

import static com.chargebee.sdk.test_data.OperationBuilder.buildOperation;
import static com.chargebee.sdk.test_data.OperationBuilder.buildPostOperation;
import static com.chargebee.sdk.test_data.ResourceBuilder.buildResource;
import static com.chargebee.sdk.test_data.ResourceResponseParam.resourceResponseParam;
import static com.chargebee.sdk.test_data.SpecBuilder.buildSpec;
import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import io.swagger.v3.oas.models.media.StringSchema;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Go_V4 request objects")
class Go_V4RequestObjectTest {

  private static final String OUTPUT_PATH = "/test/output";

  private Go_V4 generator;
  private List<FileOp> fileOps;

  @BeforeEach
  void setUp() throws IOException {
    generator = new Go_V4();
    fileOps = generator.generate(OUTPUT_PATH, spec());
  }

  /**
   * A customer resource with one operation of each shape: path parameter only, path parameter plus
   * a body parameter, body parameter only, and no input at all.
   */
  private Spec spec() {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var response = resourceResponseParam("customer", customer);

    var retrieve =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer-id")
            .withResponse(response)
            .done();
    var update =
        buildPostOperation("update")
            .forResource("customer")
            .withPathParam("customer-id")
            .withRequestBody("auto_collection", new StringSchema())
            .withResponse(response)
            .done();
    var create =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("first_name", new StringSchema())
            .withResponse(response)
            .done();
    var hierarchy =
        buildOperation("hierarchy").forResource("customer").withResponse(response).done();

    return buildSpec()
        .withResource(customer)
        .withOperation("/customers/{customer-id}", retrieve)
        .withPostOperation("/customers/{customer-id}/update", update)
        .withPostOperation("/customers", create)
        .withOperation("/customers/hierarchy", hierarchy)
        .done();
  }

  private String contentOf(String relativePath) {
    return fileOps.stream()
        .filter(op -> op instanceof FileOp.WriteString)
        .map(op -> (FileOp.WriteString) op)
        .filter(op -> (op.baseFilePath + "/" + op.fileName).endsWith(relativePath))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected file not found: " + relativePath))
        .fileContent;
  }

  private String methodBody(String methodName) {
    String service = contentOf("/customer_service.go");
    int start = service.indexOf("func (s *CustomerService) " + methodName + "(");
    assertThat(start).as("method %s", methodName).isNotNegative();
    int end = service.indexOf("\nfunc ", start + 1);
    return end < 0 ? service.substring(start) : service.substring(start, end);
  }

  @Test
  @DisplayName("Should take a request object when the path parameter is the only input")
  void shouldTakeRequestObjectForPathParamOnlyAction() {
    assertThat(contentOf("/customer_service.go"))
        .contains(
            "func (s *CustomerService) Retrieve(req *CustomerRetrieveRequest)"
                + " (*CustomerRetrieveResponse, error) {")
        .contains("req.path = fmt.Sprintf(\"/customers/%v\", url.PathEscape(req.Id))");
  }

  @Test
  @DisplayName("Should generate a request type carrying the path id and request metadata")
  void shouldGenerateRequestTypeForPathParamOnlyAction() {
    assertThat(contentOf("/customer.go"))
        .contains(
            """
            type CustomerRetrieveRequest struct {
                // Id is the identifier of the resource in the request path.
                Id         string `json:"-" form:"-"`
                apiRequest `json:"-" form:"-"`
            }""")
        .contains("func (r *CustomerRetrieveRequest) payload() any { return r }");
  }

  @Test
  @DisplayName("Should not build a BlankRequest when the action has a request type")
  void shouldNotUseBlankRequestForPathParamOnlyAction() {
    assertThat(methodBody("Retrieve")).doesNotContain("BlankRequest");
  }

  @Test
  @DisplayName("Should keep the id positional when the action also takes body parameters")
  void shouldKeepPositionalIdForActionWithBodyParams() {
    assertThat(contentOf("/customer_service.go"))
        .contains(
            "func (s *CustomerService) Update(id string, req *CustomerUpdateRequest)"
                + " (*CustomerUpdateResponse, error) {")
        .contains("req.path = fmt.Sprintf(\"/customers/%v/update\", url.PathEscape(id))");

    assertThat(contentOf("/customer.go"))
        .doesNotContain("type CustomerUpdateRequest struct {\n    Id ");
  }

  @Test
  @DisplayName("Should leave actions without a path parameter unchanged")
  void shouldLeaveActionsWithoutPathParamUnchanged() {
    assertThat(contentOf("/customer_service.go"))
        .contains(
            "func (s *CustomerService) Create(req *CustomerCreateRequest)"
                + " (*CustomerCreateResponse, error) {")
        .contains("func (s *CustomerService) Hierarchy() (*CustomerHierarchyResponse, error) {");
    assertThat(methodBody("Hierarchy")).contains("req := &BlankRequest{}");
  }

  @Test
  @DisplayName("Should generate the call-site migration tool as a separate module")
  void shouldGenerateMigrationTool() {
    assertThat(contentOf("/migrate/go.mod"))
        .contains("module github.com/chargebee/chargebee-go/migrate")
        .contains("golang.org/x/tools");
    assertThat(contentOf("/migrate/analyzer.go"))
        .contains("var Analyzer = &analysis.Analyzer{")
        .contains("Name:     \"chargebeerequest\",");
    assertThat(contentOf("/migrate/cmd/chargebee-go-migrate/main.go"))
        .contains("singlechecker.Main(migrate.Analyzer)");
    assertThat(contentOf("/migrate/README.md"))
        .contains(
            "go run github.com/chargebee/chargebee-go/migrate/cmd/chargebee-go-migrate@latest"
                + " -fix ./...");

    assertThat(
            fileOps.stream()
                .filter(op -> op instanceof FileOp.CreateDirectory)
                .map(op -> (FileOp.CreateDirectory) op)
                .anyMatch(op -> op.basePath.equals(OUTPUT_PATH + "/migrate")))
        .isTrue();
  }

  @Test
  @DisplayName("Should ship the analyzer fixtures as an archive rather than loose Go files")
  void shouldNotWriteLooseTestdataGoFiles() {
    assertThat(contentOf("/migrate/testdata/analyzer.txtar"))
        .contains("-- src/callsites/callsites.go --")
        .contains("-- src/callsites/callsites.go.golden --")
        .contains("-- src/github.com/chargebee/chargebee-go/v4/chargebee.go --");

    // Loose fixtures under testdata/ only type-check inside the GOPATH that analysistest
    // builds, which breaks tools that walk the repository for Go source.
    assertThat(
            fileOps.stream()
                .filter(op -> op instanceof FileOp.WriteString)
                .map(op -> (FileOp.WriteString) op)
                .map(op -> op.baseFilePath + "/" + op.fileName)
                .filter(path -> path.contains("/testdata/"))
                .filter(path -> path.endsWith(".go")))
        .isEmpty();
  }
}
