package com.chargebee.sdk.java.v4.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.openapi.Extension;
import com.chargebee.sdk.FileOp;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.*;

@DisplayName("SubDomain Enum Builder")
class SubDomainEnumBuilderTest {

  private SubDomainEnumBuilder builder;
  private Template template;
  private OpenAPI openAPI;
  private String outputPath;

  @BeforeEach
  void setUp() throws IOException {
    builder = new SubDomainEnumBuilder();
    outputPath = "/test/output";
    openAPI = new OpenAPI();

    Handlebars handlebars =
        new Handlebars(
            new com.github.jknack.handlebars.io.ClassPathTemplateLoader(
                "/templates/java/next", ""));
    HandlebarsUtil.registerAllHelpers(handlebars);
    template = handlebars.compile("subdomain.enum.hbs");
  }

  @Test
  @DisplayName("Should not generate SubDomain.java when no operations have subdomains")
  void shouldNotGenerateWhenNoSubDomains() throws IOException {
    Operation op = new Operation();
    op.addExtension(Extension.RESOURCE_ID, "customer");
    op.addExtension(Extension.SDK_METHOD_NAME, "create");

    PathItem pathItem = new PathItem();
    pathItem.operation(PathItem.HttpMethod.POST, op);
    openAPI.setPaths(new Paths());
    openAPI.getPaths().addPathItem("/customers", pathItem);

    builder.withOutputDirectoryPath(outputPath).withTemplate(template);
    List<FileOp> fileOps = builder.build(openAPI);

    // Only directory creation, no file write
    assertThat(fileOps).hasSize(1);
    assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
  }

  @Test
  @DisplayName("Should generate SubDomain.java with single subdomain value")
  void shouldGenerateWithSingleSubDomain() throws IOException {
    Operation op = new Operation();
    op.addExtension(Extension.RESOURCE_ID, "offer_event");
    op.addExtension(Extension.SDK_METHOD_NAME, "retrieve");
    op.addExtension(Extension.OPERATION_SUB_DOMAIN, "grow");

    PathItem pathItem = new PathItem();
    pathItem.operation(PathItem.HttpMethod.GET, op);
    openAPI.setPaths(new Paths());
    openAPI.getPaths().addPathItem("/offer_events/{id}", pathItem);

    builder.withOutputDirectoryPath(outputPath).withTemplate(template);
    List<FileOp> fileOps = builder.build(openAPI);

    FileOp.WriteString writeOp = findWriteOp(fileOps, "SubDomain.java");
    assertThat(writeOp.fileContent).contains("GROW(\"grow\")");
    assertThat(writeOp.fileContent).contains("public String getValue()");
  }

  @Test
  @DisplayName("Should generate SubDomain.java with multiple deduplicated subdomain values")
  void shouldGenerateWithMultipleDeduplicatedSubDomains() throws IOException {
    addOperationWithSubDomain("/offer_events/{id}", PathItem.HttpMethod.GET, "grow");
    addOperationWithSubDomain("/usage_events", PathItem.HttpMethod.POST, "ingest");
    // Duplicate "grow" should be deduped
    addOperationWithSubDomain("/offer_fulfillments", PathItem.HttpMethod.POST, "grow");

    builder.withOutputDirectoryPath(outputPath).withTemplate(template);
    List<FileOp> fileOps = builder.build(openAPI);

    FileOp.WriteString writeOp = findWriteOp(fileOps, "SubDomain.java");
    assertThat(writeOp.fileContent).contains("GROW(\"grow\")");
    assertThat(writeOp.fileContent).contains("INGEST(\"ingest\")");
    // Only 2 enum values, not 3 (grow is deduped)
    assertThat(countOccurrences(writeOp.fileContent, "(\"grow\")")).isEqualTo(1);
  }

  @Test
  @DisplayName("Should convert file-ingest to FILE_INGEST enum constant")
  void shouldConvertHyphenatedSubDomainToUpperUnderscore() throws IOException {
    addOperationWithSubDomain("/file_uploads", PathItem.HttpMethod.POST, "file-ingest");

    builder.withOutputDirectoryPath(outputPath).withTemplate(template);
    List<FileOp> fileOps = builder.build(openAPI);

    FileOp.WriteString writeOp = findWriteOp(fileOps, "SubDomain.java");
    assertThat(writeOp.fileContent).contains("FILE_INGEST(\"file-ingest\")");
  }

  @Test
  @DisplayName("Should handle empty OpenAPI spec without paths")
  void shouldHandleEmptySpec() throws IOException {
    builder.withOutputDirectoryPath(outputPath).withTemplate(template);
    List<FileOp> fileOps = builder.build(openAPI);

    assertThat(fileOps).hasSize(1);
    assertThat(fileOps.get(0)).isInstanceOf(FileOp.CreateDirectory.class);
  }

  @Test
  @DisplayName("Should write to com/chargebee/v4/internal directory")
  void shouldWriteToInternalDirectory() throws IOException {
    addOperationWithSubDomain("/events", PathItem.HttpMethod.GET, "grow");

    builder.withOutputDirectoryPath(outputPath).withTemplate(template);
    List<FileOp> fileOps = builder.build(openAPI);

    FileOp.CreateDirectory dirOp = (FileOp.CreateDirectory) fileOps.get(0);
    assertThat(dirOp.basePath).endsWith("/com/chargebee/v4/internal");
  }

  // --- helpers ---

  private void addOperationWithSubDomain(
      String path, PathItem.HttpMethod method, String subDomain) {
    Operation op = new Operation();
    op.addExtension(Extension.RESOURCE_ID, "test_resource");
    op.addExtension(Extension.SDK_METHOD_NAME, "testMethod");
    op.addExtension(Extension.OPERATION_SUB_DOMAIN, subDomain);

    PathItem pathItem = new PathItem();
    pathItem.operation(method, op);

    if (openAPI.getPaths() == null) {
      openAPI.setPaths(new Paths());
    }
    openAPI.getPaths().addPathItem(path, pathItem);
  }

  private FileOp.WriteString findWriteOp(List<FileOp> fileOps, String fileName) {
    return fileOps.stream()
        .filter(op -> op instanceof FileOp.WriteString)
        .map(op -> (FileOp.WriteString) op)
        .filter(op -> op.fileName.equals(fileName))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("WriteString operation not found for file: " + fileName));
  }

  private int countOccurrences(String text, String search) {
    int count = 0;
    int idx = 0;
    while ((idx = text.indexOf(search, idx)) != -1) {
      count++;
      idx += search.length();
    }
    return count;
  }
}
