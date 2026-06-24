package com.chargebee.sdk.changelog.generators;

import static com.chargebee.openapi.Extension.IS_FILTER_PARAMETER;
import static com.chargebee.sdk.test_data.OperationBuilder.buildListOperation;
import static com.chargebee.sdk.test_data.OperationBuilder.buildOperation;
import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.HttpRequestType;
import com.chargebee.openapi.parameter.Parameter;
import com.chargebee.sdk.changelog.ChangeLogDocs;
import com.chargebee.sdk.changelog.models.ChangeLogEntry;
import com.chargebee.sdk.changelog.models.ChangeLogEntry.EntryType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for the three changelog-docs generator fixes:
 *
 * <ol>
 *   <li>Change 1 — enum changes emit the documented subset instead of being dropped wholesale.
 *   <li>Change 2 — filter (query) params anchor as {@code <actionId>_<param>}.
 *   <li>Change 3 — the second {@code [code ...]} label is the endpoint path (no resource prefix;
 *       nested params use the immediate parent).
 * </ol>
 */
class ChangeLogDocsGeneratorTest {

  private final ChangeLogDocsGenerator generator = new ChangeLogDocsGenerator(new ChangeLogDocs());

  private static Action listAction(String actionId) {
    return new Action(HttpRequestType.GET, buildListOperation(actionId).done(), "/" + actionId);
  }

  private static Action postAction(String actionId) {
    return new Action(HttpRequestType.POST, buildOperation(actionId).done(), "/" + actionId);
  }

  private static Parameter filterParam(String name) {
    Schema<?> schema = new StringSchema();
    schema.addExtension(IS_FILTER_PARAMETER, true);
    return new Parameter(name, schema);
  }

  private static Parameter param(String name) {
    return new Parameter(name, new StringSchema());
  }

  // ---- Change 2: filter-param anchors ----

  @Test
  void filterParamAnchorIsPrefixedWithActionId() {
    Action action = listAction("list_omnichannel_subscriptions");

    assertThat(generator.parameterAnchor(action, filterParam("customer_id")))
        .isEqualTo("list_omnichannel_subscriptions_customer_id");
  }

  @Test
  void nonFilterParamAnchorIsBareName() {
    Action action = listAction("list_omnichannel_subscriptions");

    assertThat(generator.parameterAnchor(action, param("sort_by"))).isEqualTo("sort_by");
  }

  @Test
  void bodyParamAnchorFlattensDotsToUnderscores() {
    Action action = postAction("create_a_payment_intent");

    assertThat(generator.parameterAnchor(action, param("entitlement_overrides.entity_id")))
        .isEqualTo("entitlement_overrides_entity_id");
  }

  // ---- Change 3: endpoint label (no resource prefix; nested = immediate parent) ----

  @Test
  void flatParamLabelIsEndpointOnly() {
    Action action = listAction("list_omnichannel_subscriptions");

    assertThat(generator.endpointParamLabel(action, param("customer_id")))
        .isEqualTo("list-omnichannel-subscriptions");
  }

  @Test
  void nestedParamLabelUsesImmediateParent() {
    Action action = postAction("create_a_quote_for_a_new_subscription_items");

    assertThat(generator.endpointParamLabel(action, param("subscription.free_period")))
        .isEqualTo("create-a-quote-for-a-new-subscription-items.subscription");
  }

  @Test
  void deeplyNestedParamLabelUsesImmediateParentOnly() {
    Action action = postAction("create_a_payment_intent");

    assertThat(generator.endpointParamLabel(action, param("a.b.c")))
        .isEqualTo("create-a-payment-intent.b");
  }

  @Test
  void enumLabelAppendsOwningParam() {
    Action action = listAction("list_grant_blocks");

    assertThat(generator.endpointEnumLabel(action, "sort_by"))
        .isEqualTo("list-grant-blocks.sort_by");
  }

  // ---- Change 1: enum split (documented subset kept, rest reported) ----

  @Test
  void splitEnumValuesKeepsDocumentedValuesAndReportsTheRest(@TempDir Path docsRoot)
      throws IOException {
    // Resource is published; action doc has the slug + only the "klarna" enum value.
    writeToc(docsRoot, "payment_intents");
    writeSlugs(
        docsRoot.resolve("payment_intent").resolve("create_a_payment_intent.yaml"),
        "payment_method_type",
        "payment_method_type.enum.klarna");

    LocalDocsAvailabilityChecker checker = new LocalDocsAvailabilityChecker(docsRoot);
    ChangeLogEntry entry =
        parameterEnumEntry(
            "payment_intent",
            "create_a_payment_intent",
            "payment_intents",
            "payment_method_type",
            List.of("klarna", "alipay_hk", "paypay", "gcash", "south_korean_cards"));

    LocalDocsAvailabilityChecker.EnumSplit split = checker.splitEnumValues(entry);

    assertThat(split.present).containsExactly("klarna");
    assertThat(split.missing).containsExactly("alipay_hk", "paypay", "gcash", "south_korean_cards");
  }

  @Test
  void splitEnumValuesReportsAllWhenSlugMissing(@TempDir Path docsRoot) throws IOException {
    writeToc(docsRoot, "payment_intents");
    // Action doc exists but does not contain the param slug at all.
    writeSlugs(
        docsRoot.resolve("payment_intent").resolve("create_a_payment_intent.yaml"), "some_other");

    LocalDocsAvailabilityChecker checker = new LocalDocsAvailabilityChecker(docsRoot);
    ChangeLogEntry entry =
        parameterEnumEntry(
            "payment_intent",
            "create_a_payment_intent",
            "payment_intents",
            "payment_method_type",
            List.of("klarna", "alipay_hk"));

    LocalDocsAvailabilityChecker.EnumSplit split = checker.splitEnumValues(entry);

    assertThat(split.present).isEmpty();
    assertThat(split.missing).containsExactly("klarna", "alipay_hk");
  }

  @Test
  void splitEnumValuesReportsAllWhenResourceUnpublished(@TempDir Path docsRoot) throws IOException {
    // TOC publishes a different resource -> our resource is not published.
    writeToc(docsRoot, "something_else");
    writeSlugs(
        docsRoot.resolve("payment_intent").resolve("create_a_payment_intent.yaml"),
        "payment_method_type",
        "payment_method_type.enum.klarna");

    LocalDocsAvailabilityChecker checker = new LocalDocsAvailabilityChecker(docsRoot);
    ChangeLogEntry entry =
        parameterEnumEntry(
            "payment_intent",
            "create_a_payment_intent",
            "payment_intents",
            "payment_method_type",
            List.of("klarna"));

    LocalDocsAvailabilityChecker.EnumSplit split = checker.splitEnumValues(entry);

    assertThat(split.present).isEmpty();
    assertThat(split.missing).containsExactly("klarna");
  }

  private static ChangeLogEntry parameterEnumEntry(
      String resourceId,
      String actionId,
      String docsResourcePath,
      String slugPath,
      List<String> values) {
    return ChangeLogEntry.builder()
        .type(EntryType.NEW_PARAMETER_ENUM_VALUE)
        .resourceId(resourceId)
        .actionId(actionId)
        .docsResourcePath(docsResourcePath)
        .slugPath(slugPath)
        .enumValues(values)
        .build();
  }

  private static void writeToc(Path docsRoot, String publishedResource) throws IOException {
    Files.createDirectories(docsRoot);
    Files.writeString(
        docsRoot.resolve("TOC.yaml"), "- href: \"/docs/api/" + publishedResource + "\"\n");
  }

  private static void writeSlugs(Path file, String... slugIds) throws IOException {
    Files.createDirectories(file.getParent());
    StringBuilder sb = new StringBuilder();
    for (String slugId : slugIds) {
      sb.append("  slugId: \"").append(slugId).append("\"\n");
    }
    Files.writeString(file, sb.toString());
  }
}
