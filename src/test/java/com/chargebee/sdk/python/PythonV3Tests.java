package com.chargebee.sdk.python;

import static com.chargebee.sdk.test_data.EnumBuilder.buildEnum;
import static com.chargebee.sdk.test_data.OperationBuilder.*;
import static com.chargebee.sdk.test_data.ResourceBuilder.buildResource;
import static com.chargebee.sdk.test_data.ResourceResponseParam.resourceResponseParam;
import static com.chargebee.sdk.test_data.SpecBuilder.buildSpec;
import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.LanguageTests;
import com.chargebee.sdk.python.v3.PythonV3;
import io.swagger.v3.oas.models.media.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PythonV3Tests extends LanguageTests {
  public static PythonV3 pythonSdkGen;

  private final String basePath = "/python/chargebee";

  private final String modelsDirectoryPath = "/python/chargebee/models";

  void assertPythonGlobalEnumFileContent(FileOp.WriteString fileOp, String body) {
    assertThat(fileOp.fileContent).startsWith("from enum import Enum\n" + "\n" + "\n" + body);
  }

  void assertResourceOperationContents(FileOp.WriteString fileOp, String body) {
    assertThat(fileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + body);
  }

  void assertResourceResponseContents(FileOp.WriteString fileOp, String body) {
    assertThat(fileOp.fileContent)
        .startsWith(
            "from dataclasses import dataclass\n"
                + "from chargebee.model import Model\n"
                + "from typing import Dict, List, Any\n"
                + "\n"
                + body);
  }

  @BeforeAll
  static void beforeAll() {
    pythonSdkGen = new PythonV3();
  }

  @Test
  void baseDirectoryShouldNotBeClearedBeforeGeneration() {
    assertThat(pythonSdkGen.cleanDirectoryBeforeGenerate()).isFalse();
  }

  @Test
  void shouldCreateModelsDirectory() throws IOException {
    var spec = buildSpec().done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(0), basePath, "/models");
  }

  @Test
  void shouldCreateInitFileInModelsDirectoryWithSpecifiedResources() throws IOException {
    var spec = buildSpec().withResources("customer", "advance_invoice_schedule").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(1),
        modelsDirectoryPath,
        "__init__.py",
        "\n"
            + "\n"
            + "from chargebee.models.advance_invoice_schedule.operations import"
            + " AdvanceInvoiceSchedule\n"
            + "\n"
            + "from chargebee.models.customer.operations import Customer\n"
            + "\n");
  }

  @Test
  void shouldHaveImportsForAllSubResourcesAndEnumsOfResource() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var vat_number_status =
        buildEnum("vat_number_status", List.of("valid", "invalid", "not_validated", "undetermined"))
            .asApi()
            .done();
    var card_status = buildEnum("card_status", List.of("no_card", "valid")).asApi().done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .withEnumAttribute(vat_number_status)
            .withEnumAttribute(card_status)
            .done();
    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(1),
        modelsDirectoryPath,
        "__init__.py",
        "\n" + "\n" + "from chargebee.models.customer.operations import Customer\n" + "\n");
  }

  @Test
  void shouldHaveGlobalEnumImportsInInitFile() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var channel = buildEnum("Channel", List.of("app_store", "web", "play_store")).done();
    var spec = buildSpec().withEnums(channel, autoCollection).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(1),
        modelsDirectoryPath,
        "__init__.py",
        "from chargebee.models.enums import (\n"
            + "    Channel,\n"
            + "    AutoCollection\n"
            + ")\n"
            + "\n");
  }

  @Test
  void shouldCreateEnumsFileWIthGlobalEnumsUnderModelsDir() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var channel = buildEnum("Channel", List.of("app_store", "web", "play_store")).done();
    var spec = buildSpec().withEnums(channel, autoCollection).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(2), modelsDirectoryPath, "enums.py");
  }

  @Test
  void shouldHaveAppropriateDependancyImportInGlobalEnumsFile() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var channel = buildEnum("Channel", List.of("app_store", "web", "play_store")).done();
    var spec = buildSpec().withEnums(channel, autoCollection).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonGlobalEnumFileContent(writeStringFileOp, "");
  }

  @Test
  void shouldHaveDefinitionForGlobalEnums() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("on", "off")).done();
    var spec = buildSpec().withEnums(autoCollection).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonGlobalEnumFileContent(
        writeStringFileOp,
        "class AutoCollection(Enum):\n"
            + "    ON = \"on\"\n"
            + "    OFF = \"off\"\n"
            + "\n"
            + "    def __str__(self):\n"
            + "        return self.value\n");
  }

  @Test
  void eachGlobalEnumShouldHaveToStringMethod() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("on", "off")).done();
    var accountHolderType = buildEnum("AccountHolderType", List.of("individual", "company")).done();
    var spec = buildSpec().withEnums(autoCollection, accountHolderType).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonGlobalEnumFileContent(
        writeStringFileOp,
        "class AccountHolderType(Enum):\n"
            + "    INDIVIDUAL = \"individual\"\n"
            + "    COMPANY = \"company\"\n"
            + "\n"
            + "    def __str__(self):\n"
            + "        return self.value\n"
            + "\n"
            + "\n"
            + "class AutoCollection(Enum):\n"
            + "    ON = \"on\"\n"
            + "    OFF = \"off\"\n"
            + "\n"
            + "    def __str__(self):\n"
            + "        return self.value\n");
  }

  @Test
  void eachGlobalEnumShouldHaveToPossibleValuesInOrderOfDefinition() throws IOException {
    var accountType =
        buildEnum("AccountType", List.of("checking", "savings", "business_checking", "current"))
            .done();
    var spec = buildSpec().withEnums(accountType).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(2);
    assertPythonGlobalEnumFileContent(
        writeStringFileOp,
        "class AccountType(Enum):\n"
            + "    CHECKING = \"checking\"\n"
            + "    SAVINGS = \"savings\"\n"
            + "    BUSINESS_CHECKING = \"business_checking\"\n"
            + "    CURRENT = \"current\"\n"
            + "\n"
            + "    def __str__(self):\n"
            + "        return self.value");
  }

  @Test
  void shouldCreateSeparatePackageForEachResource() throws IOException {
    var spec = buildSpec().withResources("customer", "subscription").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertCreateDirectoryFileOp(fileOps.get(3), modelsDirectoryPath, "/customer");
    assertCreateDirectoryFileOp(fileOps.get(7), modelsDirectoryPath, "/subscription");
  }

  @Test
  void eachResourceShouldHaveInitFile() throws IOException {
    var spec = buildSpec().withResources("customer", "subscription").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(4), "/python/chargebee/models/customer", "__init__.py");
    assertWriteStringFileOp(fileOps.get(8), "/python/chargebee/models/subscription", "__init__.py");
  }

  @Test
  void eachResourceShouldHaveOperationsFile() throws IOException {
    var spec = buildSpec().withResources("customer", "subscription").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(5), "/python/chargebee/models/customer", "operations.py");
    assertWriteStringFileOp(
        fileOps.get(9), "/python/chargebee/models/subscription", "operations.py");
  }

  @Test
  void shouldHaveImportsForAllSubResourcesAndEnumsOfResourceInInitFile() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var vat_number_status =
        buildEnum("vat_number_status", List.of("valid", "invalid", "not_validated", "undetermined"))
            .asApi()
            .done();
    var card_status = buildEnum("card_status", List.of("no_card", "valid")).asApi().done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .withEnumAttribute(vat_number_status)
            .withEnumAttribute(card_status)
            .done();
    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(4),
        modelsDirectoryPath + "/customer",
        "__init__.py",
        "from .operations import Customer\n" + "from .responses import CustomerResponse\n");
  }

  @Test
  void shouldHaveImportInOperationFiles() throws IOException {
    var spec = buildSpec().withResources("customer").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(writeStringFileOp, "");
  }

  @Test
  void shouldHaveEnumImportsInOperationFileIfResourceSpecificEnumsArePresent() throws IOException {
    var customer =
        buildResource("customer")
            .withAttribute(
                "billing_day_of_week",
                new StringSchema()._enum(List.of("monday", "tuesday", "wednesday")))
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(writeStringFileOp, "from enum import Enum");
  }

  @Test
  void shouldHaveEnumImportsInOperationFileIfResourceSpecificEnumsArePresentAtSubResourceLevel()
      throws IOException {
    var parentAccountAccess =
        buildResource("parent_account_access")
            .withAttribute(
                "portal_edit_child_subscriptions",
                new StringSchema()._enum(List.of("yes", "view_only", "no")))
            .done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("parent_account_access", parentAccountAccess)
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(writeStringFileOp, "from enum import Enum");
  }

  @Test
  void shouldHaveEnumDefinitionsInOperationFileIfResourceSpecificEnumsArePresent()
      throws IOException {
    var customer =
        buildResource("customer")
            .withAttribute(
                "billing_day_of_week",
                new StringSchema()._enum(List.of("monday", "tuesday", "wednesday")))
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from enum import Enum\n"
            + "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "    class BillingDayOfWeek(Enum):\n"
            + "        MONDAY = \"monday\"\n"
            + "        TUESDAY = \"tuesday\"\n"
            + "        WEDNESDAY = \"wednesday\"\n"
            + "\n"
            + "        def __str__(self):\n"
            + "            return self.value\n");
  }

  @Test
  void shouldHaveDefinitionForSubResourceEnumsIfPresent() throws IOException {
    var parentAccountAccess =
        buildResource("parent_account_access")
            .withAttribute(
                "portal_edit_child_subscriptions",
                new StringSchema()._enum(List.of("yes", "view_only", "no")))
            .done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("parent_account_access", parentAccountAccess)
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from enum import Enum\n"
            + "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "    class ParentAccountAccessPortalEditChildSubscriptions(Enum):\n"
            + "        YES = \"yes\"\n"
            + "        VIEW_ONLY = \"view_only\"\n"
            + "        NO = \"no\"\n"
            + "\n"
            + "        def __str__(self):\n"
            + "            return self.value\n");
  }

  @Test
  void shouldHaveClassDefinitionForSubResourceUnderSubResource() throws IOException {
    var entitlementOverrides =
        buildResource("entitlement_overrides")
            .withAttribute("value", new StringSchema())
            .withAttribute("name", new StringSchema())
            .done();
    var components =
        buildResource("components")
            .withSubResourceAttribute("entitlement_overrides", entitlementOverrides)
            .done();
    var subscriptionEntitlements =
        buildResource("subscription_entitlements")
            .withSubResourceAttribute("components", components)
            .done();
    var spec = buildSpec().withResources(subscriptionEntitlements).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);

    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class SubscriptionEntitlements:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class Component(TypedDict):\n"
            + "        entitlement_overrides: NotRequired[EntitlementOverrides]\n");
  }

  @Test
  void shouldHaveDefinitionForNormalAttributesAlongWithSubResourceUnderSubResource()
      throws IOException {
    var entitlementOverrides =
        buildResource("entitlement_overrides")
            .withAttribute("value", new StringSchema())
            .withAttribute("name", new StringSchema())
            .done();
    var components =
        buildResource("components")
            .withAttribute("is_overridden", new BooleanSchema())
            .withSubResourceAttribute("entitlement_overrides", entitlementOverrides)
            .done();
    var subscriptionEntitlements =
        buildResource("subscription_entitlements")
            .withSubResourceAttribute("components", components)
            .done();
    var spec = buildSpec().withResources(subscriptionEntitlements).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class SubscriptionEntitlements:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class Component(TypedDict):\n"
            + "        is_overridden: NotRequired[bool]\n"
            + "        entitlement_overrides: NotRequired[EntitlementOverrides]\n");
  }

  @Test
  void shouldHaveClassDefinitionForSubResources() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var parentAccountAccess =
        buildResource("parent_account_access").withAttribute("send_invoice_emails", true).done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .withSubResourceAttribute("parent_account_access", parentAccountAccess)
            .done();
    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);

    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class BillingAddress(TypedDict):\n"
            + "        first_name: NotRequired[str]\n"
            + "\n"
            + "    class ParentAccountAccess(TypedDict):\n"
            + "        send_invoice_emails: Required[str]\n");
  }

  @Test
  void shouldHaveDefinitionForSubResourceAttributes_AttributeOfTypeSubResource()
      throws IOException {
    var entitlementOverrides =
        buildResource("entitlement_overrides")
            .withAttribute("value", new StringSchema())
            .withAttribute("name", new StringSchema())
            .done();
    var components =
        buildResource("component")
            .withSubResourceAttribute("entitlement_overrides", entitlementOverrides)
            .done();
    var subscriptionEntitlements =
        buildResource("subscription_entitlements")
            .withAttribute("is_overridden", new BooleanSchema())
            .withSubResourceAttribute("components", components)
            .done();
    var spec = buildSpec().withResources(subscriptionEntitlements).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);

    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class SubscriptionEntitlements:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class Component(TypedDict):\n"
            + "        entitlement_overrides: NotRequired[EntitlementOverrides]\n");
  }

  @Test
  void shouldHaveDefinitionForSubResourceAttributes_AttributeOfTypeGlobalEnum() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off"));
    var billingAddress =
        buildResource("billing_address")
            .withAttribute(
                "auto_collection",
                new StringSchema()
                    ._enum(List.of("on", "off"))
                    .extensions(
                        Map.of(
                            "x-cb-is-global-enum",
                            true,
                            "x-cb-global-enum-reference",
                            "./enums/" + autoCollection.typeName + ".yaml",
                            "x-cb-is-gen-separate",
                            true)))
            .done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);

    assertResourceOperationContents(
        writeStringFileOp,
        "from chargebee.models import enums\n"
            + "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class BillingAddress(TypedDict):\n"
            + "        auto_collection: NotRequired[enums.AutoCollection]");
  }

  @Test
  void shouldHaveDefinitionForSubResourceAttributes_AttributeOfTypeEnum() throws IOException {
    var parentAccountAccess =
        buildResource("parent_account_access")
            .withAttribute(
                "portal_edit_child_subscriptions",
                new StringSchema()._enum(List.of("yes", "view_only", "no")))
            .done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("parent_account_access", parentAccountAccess)
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from enum import Enum\n"
            + "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "    class ParentAccountAccessPortalEditChildSubscriptions(Enum):\n"
            + "        YES = \"yes\"\n"
            + "        VIEW_ONLY = \"view_only\"\n"
            + "        NO = \"no\"\n"
            + "\n"
            + "        def __str__(self):\n"
            + "            return self.value\n"
            + "\n"
            + "    class ParentAccountAccess(TypedDict):\n"
            + "        portal_edit_child_subscriptions:"
            + " NotRequired[\"Customer.ParentAccountAccessPortalEditChildSubscriptions\"]\n");
  }

  @Test
  void shouldHaveDefinitionForSubResourceAttributes_NormalAttribute() throws IOException {
    var parentAccountAccess =
        buildResource("parent_account_access")
            .withAttribute("send_invoice_emails")
            .withAttribute("send_payment_emails")
            .done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("parent_account_access", parentAccountAccess)
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class ParentAccountAccess(TypedDict):\n"
            + "        send_invoice_emails: NotRequired[str]\n"
            + "        send_payment_emails: NotRequired[str]\n");
  }

  @Test
  void shouldHaveSeparateClassDefinitionForEachResource() throws IOException {
    var spec = buildSpec().withResources("customer", "subscription").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertResourceResponseContents(
        writeStringFileOp,
        "@dataclass\n"
            + "class CustomerResponse(Model):\n"
            + "    raw_data: Dict[Any, Any] = None");

    writeStringFileOp = (FileOp.WriteString) fileOps.get(10);
    assertResourceResponseContents(
        writeStringFileOp,
        "@dataclass\n"
            + "class SubscriptionResponse(Model):\n"
            + "    raw_data: Dict[Any, Any] = None");
  }

  @Test
  void shouldHaveDefinitionForResourceAttributes_AttributeOfTypeSubResource() throws IOException {
    var parentAccountAccess =
        buildResource("parent_account_access")
            .withAttribute("send_invoice_emails")
            .withAttribute("send_payment_emails")
            .done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("parent_account_access", parentAccountAccess)
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class ParentAccountAccess(TypedDict):\n"
            + "        send_invoice_emails: NotRequired[str]\n"
            + "        send_payment_emails: NotRequired[str]\n");
  }

  @Test
  void shouldHaveDefinitionForResourceAttributes_AttributeOfTypeGlobalEnum() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off"));
    var customer =
        buildResource("customer")
            .withAttribute(
                "auto_collection",
                new StringSchema()
                    ._enum(List.of("on", "off"))
                    .extensions(
                        Map.of(
                            "x-cb-is-global-enum",
                            true,
                            "x-cb-global-enum-reference",
                            "./enums/" + autoCollection.typeName + ".yaml",
                            "x-cb-is-gen-separate",
                            true)))
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);

    assertResourceResponseContents(
        writeStringFileOp,
        "@dataclass\n"
            + "class CustomerResponse(Model):\n"
            + "    raw_data: Dict[Any, Any] = None\n"
            + "    auto_collection: str = None");
  }

  @Test
  void shouldHaveDefinitionForResourceAttributes_AttributeOfTypeEnum() throws IOException {
    var customer =
        buildResource("customer")
            .withAttribute(
                "billing_day_of_week",
                new StringSchema()._enum(List.of("monday", "tuesday", "wednesday")))
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from enum import Enum\n"
            + "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "    class BillingDayOfWeek(Enum):\n"
            + "        MONDAY = \"monday\"\n"
            + "        TUESDAY = \"tuesday\"\n"
            + "        WEDNESDAY = \"wednesday\"\n"
            + "\n"
            + "        def __str__(self):\n"
            + "            return self.value\n");
  }

  @Test
  void shouldHaveDefinitionForResourceAttributes_NormalAttribute() throws IOException {
    var customer =
        buildResource("customer").withAttribute("first_name").withAttribute("last_name").done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertResourceResponseContents(
        writeStringFileOp,
        "@dataclass\n"
            + "class CustomerResponse(Model):\n"
            + "    raw_data: Dict[Any, Any] = None\n"
            + "    first_name: str = None\n"
            + "    last_name: str = None\n");
  }

  @Test
  void shouldHaveRequiredSuffixForRequiredAttribute() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("first_name", true).done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .withAttribute("id", true)
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class BillingAddress(TypedDict):\n"
            + "        first_name: Required[str]\n");
  }

  @Test
  void shouldHaveNotRequiredSuffixForOptionalAttribute() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class BillingAddress(TypedDict):\n"
            + "        first_name: NotRequired[str]\n");
  }

  @Test
  void shouldHaveDataTypeForAttributes_AttributeOfTypeString() throws IOException {
    var customer = buildResource("customer").withAttribute("email").done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertResourceResponseContents(
        writeStringFileOp,
        "@dataclass\n"
            + "class CustomerResponse(Model):\n"
            + "    raw_data: Dict[Any, Any] = None\n"
            + "    email: str = None");
  }

  @Test
  void shouldHaveDataTypeForAttributes_AttributeOfTypeBoolean() throws IOException {
    var billingAddress =
        buildResource("billing_address")
            .withAttribute("is_location_valid", new BooleanSchema())
            .done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class BillingAddress(TypedDict):\n"
            + "        is_location_valid: NotRequired[bool]\n");
  }

  @Test
  void shouldHaveDataTypeForAttributes_AttributeOfTypeInteger() throws IOException {
    var customer =
        buildResource("customer")
            .withAttribute("billing_date", new IntegerSchema())
            .withAttribute(
                "resource_version", new IntegerSchema().type("integer").format("int64"), false)
            .withAttribute(
                "vat_number_validated_time",
                new IntegerSchema().format("unix-time").pattern("^[0-9]{10}$"))
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertResourceResponseContents(
        writeStringFileOp,
        "@dataclass\n"
            + "class CustomerResponse(Model):\n"
            + "    raw_data: Dict[Any, Any] = None\n"
            + "    billing_date: int = None\n"
            + "    resource_version: int = None\n"
            + "    vat_number_validated_time: int = None\n");
  }

  @Test
  void shouldHaveDataTypeForAttributes_AttributeOfTypeFloat() throws IOException {
    var billingAddress =
        buildResource("billing_address")
            .withAttribute("prorated_taxable_amount", new NumberSchema().format("decimal"))
            .withAttribute("discount_percentage", new NumberSchema().format("double"), false)
            .done();
    var invoice =
        buildResource("invoice").withSubResourceAttribute("billing_address", billingAddress).done();

    var spec = buildSpec().withResources(invoice).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Invoice:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class BillingAddress(TypedDict):\n"
            + "        prorated_taxable_amount: NotRequired[float]\n"
            + "        discount_percentage: NotRequired[float]\n");
  }

  @Test
  void shouldHaveDataTypeForAttributes_AttributeOfTypeDict() throws IOException {
    var customer =
        buildResource("customer")
            .withAttribute(
                "meta_data", new ObjectSchema().type("object").additionalProperties(true), false)
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertResourceResponseContents(
        writeStringFileOp,
        "@dataclass\n"
            + "class CustomerResponse(Model):\n"
            + "    raw_data: Dict[Any, Any] = None\n"
            + "    meta_data: Dict[Any, Any] = None");
  }

  @Test
  void shouldHaveDataTypeForAttributes_AttributeOfTypeArray() throws IOException {
    var customer =
        buildResource("customer")
            .withAttribute("exemption_details", new ArraySchema().items(new StringSchema()))
            .done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertResourceResponseContents(
        writeStringFileOp,
        "@dataclass\n"
            + "class CustomerResponse(Model):\n"
            + "    raw_data: Dict[Any, Any] = None\n"
            + "    exemption_details: List[str] = None\n");
  }

  @Test
  void shouldHaveClassDefinitionForOperationSpecificSubResources() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "billing_address",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "first_name",
                            new StringSchema().extensions(Map.of("x-cb-is-sub-resource", true))))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class CreateBillingAddressParams(TypedDict):\n"
            + "        first_name: NotRequired[str]\n");
  }

  @Test
  void shouldHaveGlobalEnumImportIfGlobalEnumAttributesInRequestBodyParams() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off"));
    var customer = buildResource("customer").done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "auto_collection",
                new StringSchema()
                    ._enum(List.of("on", "off"))
                    .extensions(
                        Map.of(
                            "x-cb-is-global-enum",
                            true,
                            "x-cb-global-enum-reference",
                            "./enums/" + autoCollection.typeName + ".yaml",
                            "x-cb-is-gen-separate",
                            true)))
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "from chargebee.models import enums");
  }

  @Test
  void shouldSupportGlobalEnumAttributesInRequestBodyParams() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off"));
    var customer = buildResource("customer").done();
    var createOperation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "auto_collection",
                new StringSchema()
                    ._enum(List.of("on", "off"))
                    .extensions(
                        Map.of(
                            "x-cb-is-global-enum",
                            true,
                            "x-cb-global-enum-reference",
                            "./enums/" + autoCollection.typeName + ".yaml",
                            "x-cb-is-gen-separate",
                            true)))
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers", createOperation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "from chargebee.models import enums\n"
                + "\n"
                + "@dataclass\n"
                + "class Customer:\n"
                + "\n"
                + "    env: environment.Environment\n"
                + "\n"
                + "\n"
                + "\n"
                + "    class CreateParams(TypedDict):\n"
                + "        auto_collection: NotRequired[enums.AutoCollection]\n");
  }

  @Test
  void shouldHaveImportStatementsInOperationsFile() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "billing_address",
                new ObjectSchema()
                    .properties(
                        Map.of("first_name", new StringSchema(), "last_name", new StringSchema()))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(writeStringFileOp, "");
  }

  @Test
  void EventsResourceShouldHaveEnvironmentInImportStatements() throws IOException {
    var spec = buildSpec().withResources("event").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "import json\n"
                + "from chargebee.main import Environment\n");
  }

  @Test
  void TimeMachineResourceShouldHaveErrorHandlingInImportStatements() throws IOException {
    var spec = buildSpec().withResources("time_machine").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "import json\n"
                + "from chargebee import OperationFailedError\n");
  }

  @Test
  void shouldHaveClassDefinitionForResource() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "billing_address",
                new ObjectSchema()
                    .properties(
                        Map.of("first_name", new StringSchema(), "last_name", new StringSchema()))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n");
  }

  @Test
  void shouldHaveSpecifiedSnippetIneEventsResource() throws IOException {
    var event = buildResource("event").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("event")
            .withPathParam("event_id")
            .withResponse(resourceResponseParam("event", event))
            .done();
    var spec =
        buildSpec().withResource(event).withOperation("/events/{event-id}", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "import json\n"
                + "from chargebee.main import Environment\n"
                + "\n"
                + "@dataclass\n"
                + "class Event:\n"
                + "\n"
                + "    env: environment.Environment\n"
                + "\n"
                + FileOp.fetchFileContent("src/main/resources/templates/python/v3/event.py.hbs")
                + "\n"
                + "\n"
                + "\n"
                + "    def retrieve(self, id, headers=None) -> RetrieveResponse:\n"
                + "        jsonKeys = { \n"
                + "        }\n"
                + "        options = {}\n"
                + "        return request.send('get', request.uri_path(\"events\", id), self.env,"
                + " None, headers, RetrieveResponse,None, False, jsonKeys, options)");
  }

  @Test
  void shouldHaveSpecifiedSnippetInExportsResource() throws IOException {
    var export = buildResource("export").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("export")
            .withPathParam("export-id")
            .withResponse(resourceResponseParam("export", export))
            .done();
    var spec =
        buildSpec().withResource(export).withOperation("/exports/{export-id}", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Export:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + FileOp.fetchFileContent("src/main/resources/templates/python/v3/export.py.hbs")
            + "\n"
            + "\n"
            + "\n"
            + "    def retrieve(self, id, headers=None) -> RetrieveResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('get', request.uri_path(\"exports\", id), self.env,"
            + " None, headers, RetrieveResponse,None, False, jsonKeys, options)");
  }

  @Test
  void timeMachineResourceShouldHaveSpecifiedSnippet() throws IOException {
    var timeMachine = buildResource("time_machine").withAttribute("name", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("time_machine")
            .withPathParam("time-machine-name")
            .withResponse(resourceResponseParam("time_machine", timeMachine))
            .done();
    var spec =
        buildSpec()
            .withResource(timeMachine)
            .withOperation("/time_machines/{time-machine-name}", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "import json\n"
                + "from chargebee import OperationFailedError\n"
                + "\n"
                + "@dataclass\n"
                + "class TimeMachine:\n"
                + "\n"
                + "    env: environment.Environment\n"
                + "\n"
                + FileOp.fetchFileContent(
                    "src/main/resources/templates/python/v3/timeMachine.py.hbs")
                + "\n"
                + "\n"
                + "\n"
                + "    def retrieve(self, id, headers=None) -> RetrieveResponse:\n"
                + "        jsonKeys = { \n"
                + "        }\n"
                + "        options = {}\n"
                + "        return request.send('get', request.uri_path(\"time_machines\", id),"
                + " self.env, None, headers, RetrieveResponse,None, False, jsonKeys, options)");
  }

  @Test
  void shouldHaveAbstractionClassDefinitionForOperationInputParams() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off"));
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("first_name", new StringSchema())
            .withRequestBody("prorated_taxable_amount", new NumberSchema().format("decimal"))
            .withRequestBody("billing_date", new IntegerSchema())
            .withRequestBody("is_location_valid", new BooleanSchema())
            .withRequestBody(
                "auto_collection",
                new StringSchema()
                    ._enum(List.of("on", "off"))
                    .extensions(
                        Map.of(
                            "x-cb-is-global-enum", true,
                            "x-cb-global-enum-reference",
                                "./enums/" + autoCollection.typeName + ".yaml",
                            "x-cb-is-gen-separate", true)))
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "from chargebee.models import enums\n"
                + "\n"
                + "@dataclass\n"
                + "class Customer:\n"
                + "\n"
                + "    env: environment.Environment\n"
                + "\n"
                + "\n"
                + "\n"
                + "    class CreateParams(TypedDict):\n"
                + "        first_name: NotRequired[str]\n"
                + "        prorated_taxable_amount: NotRequired[float]\n"
                + "        billing_date: NotRequired[int]\n"
                + "        is_location_valid: NotRequired[bool]\n"
                + "        auto_collection: NotRequired[enums.AutoCollection]\n"
                + "\n"
                + "    def create(self, params: CreateParams = None, headers=None) ->"
                + " CreateResponse:\n"
                + "        jsonKeys = { \n"
                + "        }\n"
                + "        options = {}\n"
                + "        return request.send('post', request.uri_path(\"customers\"), self.env,"
                + " cast(Dict[Any, Any], params), headers, CreateResponse,None, False, jsonKeys,"
                + " options)");
  }

  @Test
  void noParamsInArgsIfNoQueryParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer_id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    def retrieve(self, id, headers=None) -> RetrieveResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('get', request.uri_path(\"customers\", id), self.env,"
            + " None, headers, RetrieveResponse,None, False, jsonKeys, options)");
  }

  @Test
  void noParamsInArgsIfNoRequestBodyParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id").done();
    var operation =
        buildPostOperation("clear_personal_data")
            .forResource("customer")
            .withPathParam("customer_id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers/{customer-id}/clear_personal_data", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    def clear_personal_data(self, id, headers=None) -> ClearPersonalDataResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('post', request.uri_path(\"customers\", id,"
            + " \"clear_personal_data\"), self.env, None, headers, ClearPersonalDataResponse,None,"
            + " False, jsonKeys, options)");
  }

  @Test
  void paramsInArgsIfAtleastOneQueryParamRequired() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildOperation("hierarchy")
            .forResource("customer")
            .withPathParam("customer_id")
            .withQueryParam("hierarchy_operation_type", true)
            .withQueryParam("hierarchy_type")
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}/hierarchy", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    class HierarchyParams(TypedDict):\n"
            + "        hierarchy_operation_type: Required[str]\n"
            + "        hierarchy_type: NotRequired[str]\n"
            + "\n"
            + "    def hierarchy(self, id, params: HierarchyParams, headers=None) ->"
            + " HierarchyResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('get', request.uri_path(\"customers\", id,"
            + " \"hierarchy\"), self.env, cast(Dict[Any, Any], params), headers,"
            + " HierarchyResponse,None, False, jsonKeys, options)");
  }

  @Test
  void paramsInArgsIfAtleastOneRequestBodyParamRequired() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("merge")
            .forResource("customer")
            .withRequestBody("from_customer_id", new StringSchema(), true)
            .withRequestBody("to_customer_id", new StringSchema(), true)
            .withRequestBody("customer_type", new StringSchema()._enum(List.of()), false)
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec =
        buildSpec().withResource(customer).withPostOperation("/customers/merge", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    class MergeParams(TypedDict):\n"
            + "        from_customer_id: Required[str]\n"
            + "        to_customer_id: NotRequired[str]\n"
            + "        customer_type: NotRequired[str]\n"
            + "\n"
            + "    def merge(self, params: MergeParams, headers=None) -> MergeResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('post', request.uri_path(\"customers\", \"merge\"),"
            + " self.env, cast(Dict[Any, Any], params), headers, MergeResponse,None, False,"
            + " jsonKeys, options)");
  }

  @Test
  void paramsInArgsIfAtleastOneSubRequestBodyParamIsRequired() throws IOException {
    var quote = buildResource("quote").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create_for_charge_items_and_charges")
            .forResource("quote")
            .withRequestBody("name", new StringSchema(), false)
            .withCompositeArrayRequestBody(
                "discounts",
                new ObjectSchema()
                    .properties(
                        Map.of("apply_on", new StringSchema(), "amount", new IntegerSchema()))
                    .required(List.of("apply_on")))
            .withResponse(resourceResponseParam("quotes", quote))
            .done();

    var spec =
        buildSpec()
            .withResource(quote)
            .withPostOperation("/quotes/create_for_charge_items_and_charges", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Quote:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    class CreateForChargeItemsAndChargesParams(TypedDict):\n"
            + "        name: NotRequired[str]\n"
            + "        discounts:"
            + " Required[List[\"Quote.CreateForChargeItemsAndChargesDiscountParams\"]]\n"
            + "\n"
            + "    def create_for_charge_items_and_charges(self, params:"
            + " CreateForChargeItemsAndChargesParams, headers=None) ->"
            + " CreateForChargeItemsAndChargesResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('post', request.uri_path(\"quotes\","
            + " \"create_for_charge_items_and_charges\"), self.env, cast(Dict[Any, Any], params),"
            + " headers, CreateForChargeItemsAndChargesResponse,None, False, jsonKeys, options)");
  }

  @Test
  void paramsInArgsEvenIfSubRequestBodyParamIsHiddenFromSDKButRequired() throws IOException {
    var quote = buildResource("quote").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create_for_charge_items_and_charges")
            .forResource("quote")
            .withRequestBody("name", new StringSchema(), false)
            .withCompositeArrayRequestBody(
                "discounts",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "apply_on", hiddenFromSDKPropSchema(), "amount", new IntegerSchema()))
                    .required(List.of("apply_on")))
            .withResponse(resourceResponseParam("quotes", quote))
            .done();

    var spec =
        buildSpec()
            .withResource(quote)
            .withPostOperation("/quotes/create_for_charge_items_and_charges", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Quote:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    class CreateForChargeItemsAndChargesParams(TypedDict):\n"
            + "        name: NotRequired[str]\n"
            + "        discounts:"
            + " Required[List[\"Quote.CreateForChargeItemsAndChargesDiscountParams\"]]\n"
            + "\n"
            + "    def create_for_charge_items_and_charges(self, params:"
            + " CreateForChargeItemsAndChargesParams, headers=None) ->"
            + " CreateForChargeItemsAndChargesResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('post', request.uri_path(\"quotes\","
            + " \"create_for_charge_items_and_charges\"), self.env, cast(Dict[Any, Any], params),"
            + " headers, CreateForChargeItemsAndChargesResponse,None, False, jsonKeys, options)");
  }

  @Test
  void paramsWithNoneInArgsIfAllQueryParamsOptional() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("limit")
            .withQueryParam("offset")
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec = buildSpec().withResource(customer).withOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    class ListParams(TypedDict):\n"
            + "        limit: NotRequired[str]\n"
            + "        offset: NotRequired[str]\n"
            + "\n"
            + "    def list(self, params: ListParams = None, headers=None) -> ListResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send_list_request('get', request.uri_path(\"customers\"),"
            + " self.env, cast(Dict[Any, Any], params), headers, ListResponse,None, False,"
            + " jsonKeys, options)");
  }

  @Test
  void paramsWithNoneInArgsIfAllRequestBodyParamsOptional() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema())
            .withRequestBody("first_name", new StringSchema())
            .withRequestBody(
                "auto_collection",
                new StringSchema()
                    ._enum(List.of("on", "off"))
                    .extensions(
                        Map.of(
                            "x-cb-is-api-column",
                            true,
                            "x-cb-is-global-enum",
                            false,
                            "x-cb-is-external-enum",
                            false,
                            "x-cb-is-gen-separate",
                            false,
                            "x-cb-is-ignore-generation",
                            false)))
            .withRequestBody("net_term_days", new IntegerSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .done();

    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    class CreateParams(TypedDict):\n"
            + "        id: NotRequired[str]\n"
            + "        first_name: NotRequired[str]\n"
            + "        auto_collection: NotRequired[\"Customer.AutoCollection\"]\n"
            + "        net_term_days: NotRequired[int]\n"
            + "\n"
            + "    def create(self, params: CreateParams = None, headers=None) -> CreateResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('post', request.uri_path(\"customers\"), self.env,"
            + " cast(Dict[Any, Any], params), headers, CreateResponse,None, False, jsonKeys,"
            + " options)");
  }

  @Test
  void shouldSupportGetAction() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("customer")
            .withPathParam("customer_id")
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    def retrieve(self, id, headers=None) -> RetrieveResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('get', request.uri_path(\"customers\", id), self.env,"
            + " None, headers, RetrieveResponse,None, False, jsonKeys, options)");
  }

  @Test
  void shouldSupportPostAction() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "billing_address",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "first_name",
                            new StringSchema().extensions(Map.of("x-cb-is-sub-resource", true))))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(modelsDirectoryPath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class CreateBillingAddressParams(TypedDict):\n"
            + "        first_name: NotRequired[str]\n"
            + "\n"
            + "\n"
            + "    class CreateParams(TypedDict):\n"
            + "        billing_address: NotRequired[\"Customer.CreateBillingAddressParams\"]\n"
            + "\n"
            + "    def create(self, params: CreateParams = None, headers=None) -> CreateResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('post', request.uri_path(\"customers\"), self.env,"
            + " cast(Dict[Any, Any], params), headers, CreateResponse,None, False, jsonKeys,"
            + " options)");
  }

  @Test
  void sendListRequestMethodOnlyForListOperationNamedList() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam("limit", new IntegerSchema())
            .withQueryParam("offset", new StringSchema())
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    class ListParams(TypedDict):\n"
            + "        limit: NotRequired[int]\n"
            + "        offset: NotRequired[str]\n"
            + "\n"
            + "    def list(self, params: ListParams = None, headers=None) -> ListResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send_list_request('get', request.uri_path(\"customers\"),"
            + " self.env, cast(Dict[Any, Any], params), headers, ListResponse,None, False,"
            + " jsonKeys, options)");
  }

  @Test
  void NotAllListOperationsShouldHaveSendListRequestMethod() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildListOperation("contactsForCustomer")
            .forResource("customer")
            .withPathParam("customer-id")
            .withQueryParam("limit", new IntegerSchema())
            .withQueryParam("offset", new StringSchema())
            .withResponse(resourceResponseParam("customers", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}/contacts", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    class ContactsForCustomerParams(TypedDict):\n"
            + "        limit: NotRequired[int]\n"
            + "        offset: NotRequired[str]\n"
            + "\n"
            + "    def contacts_for_customer(self, id, params: ContactsForCustomerParams = None,"
            + " headers=None) -> ContactsForCustomerResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('get', request.uri_path(\"customers\", id,"
            + " \"contacts\"), self.env, cast(Dict[Any, Any], params), headers,"
            + " ContactsForCustomerResponse,None, False, jsonKeys, options)");
  }

  @Test
  void shouldSupportGetActionWithPathParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id").done();
    var operation =
        buildOperation("contactsForCustomer")
            .forResource("customer")
            .withPathParam("customer_id")
            .withQueryParam("limit", new IntegerSchema())
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withOperation("/customers/{customer-id}/contacts", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    class ContactsForCustomerParams(TypedDict):\n"
            + "        limit: NotRequired[int]\n"
            + "\n"
            + "    def contacts_for_customer(self, id, params: ContactsForCustomerParams = None,"
            + " headers=None) -> ContactsForCustomerResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('get', request.uri_path(\"customers\", id,"
            + " \"contacts\"), self.env, cast(Dict[Any, Any], params), headers,"
            + " ContactsForCustomerResponse,None, False, jsonKeys, options)");
  }

  @Test
  void shouldSupportPostActionWithPathParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id").done();
    var operation =
        buildPostOperation("updatePaymentMethod")
            .forResource("customer")
            .withPathParam("customer_id")
            .withRequestBody("payment_method", new StringSchema(), true)
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers/{customer-id}/update_payment_method", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "\n"
            + "\n"
            + "    class UpdatePaymentMethodParams(TypedDict):\n"
            + "        payment_method: Required[str]\n"
            + "\n"
            + "    def update_payment_method(self, id, params: UpdatePaymentMethodParams,"
            + " headers=None) -> UpdatePaymentMethodResponse:\n"
            + "        jsonKeys = { \n"
            + "        }\n"
            + "        options = {}\n"
            + "        return request.send('post', request.uri_path(\"customers\", id,"
            + " \"update_payment_method\"), self.env, cast(Dict[Any, Any], params), headers,"
            + " UpdatePaymentMethodResponse,None, False, jsonKeys, options)");
  }

  @Test
  void shouldSupportCustomEnumFormatting_TxnStatus() throws IOException {
    var linkedPayment =
        buildResource("linked_payment")
            .withAttribute(
                "txn_status",
                new StringSchema()
                    ._enum(
                        List.of(
                            "in_progress",
                            "success",
                            "voided",
                            "failure",
                            "timeout",
                            "needs_attention"))
                    .extensions(
                        Map.of(
                            "x-cb-is-external-enum",
                            true,
                            "x-cb-is-global-enum",
                            false,
                            "x-cb-is-api-column",
                            true,
                            "x-cb-is-gen-separate",
                            false,
                            "x-cb-is-ignore-generation",
                            false)))
            .done();
    var customer =
        buildResource("invoice").withSubResourceAttribute("linked_payment", linkedPayment).done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from enum import Enum\n"
            + "from chargebee.models import transaction\n"
            + "\n"
            + "@dataclass\n"
            + "class Invoice:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class LinkedPayment(TypedDict):\n"
            + "        txn_status: NotRequired[\"transaction.Transaction.Status\"]\n");
  }

  @Test
  void shouldSupportCustomEnumFormatting_CnReasonCode() throws IOException {
    var appliedCredit =
        buildResource("applied_credit")
            .withAttribute(
                "cn_reason_code",
                new StringSchema()
                    ._enum(
                        List.of(
                            "write_off",
                            "subscription_change",
                            "subscription_cancellation",
                            "subscription_pause"))
                    .extensions(
                        Map.of(
                            "x-cb-is-external-enum",
                            true,
                            "x-cb-is-global-enum",
                            false,
                            "x-cb-is-api-column",
                            true,
                            "x-cb-is-gen-separate",
                            false,
                            "x-cb-is-ignore-generation",
                            false)))
            .done();
    var customer =
        buildResource("invoice").withSubResourceAttribute("applied_credit", appliedCredit).done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from enum import Enum\n"
            + "from chargebee.models import credit_note\n"
            + "\n"
            + "@dataclass\n"
            + "class Invoice:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class AppliedCredit(TypedDict):\n"
            + "        cn_reason_code: NotRequired[\"credit_note.CreditNote.ReasonCode\"]\n");
  }

  @Test
  void shouldSupportCustomEnumFormatting_CnStatus() throws IOException {
    var appliedCredit =
        buildResource("applied_credit")
            .withAttribute(
                "cn_status",
                new StringSchema()
                    ._enum(List.of("adjusted", "refunded", "refund_due", "voided"))
                    .extensions(
                        Map.of(
                            "x-cb-is-external-enum",
                            true,
                            "x-cb-is-global-enum",
                            false,
                            "x-cb-is-api-column",
                            true,
                            "x-cb-is-gen-separate",
                            false,
                            "x-cb-is-ignore-generation",
                            false)))
            .done();
    var customer =
        buildResource("invoice").withSubResourceAttribute("applied_credit", appliedCredit).done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from enum import Enum\n"
            + "from chargebee.models import credit_note\n"
            + "\n"
            + "@dataclass\n"
            + "class Invoice:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class AppliedCredit(TypedDict):\n"
            + "        cn_status: NotRequired[\"credit_note.CreditNote.Status\"]\n");
  }

  @Test
  void shouldSupportCustomEnumFormatting_InvoiceStatus() throws IOException {
    var allocation =
        buildResource("allocation")
            .withAttribute(
                "invoice_status",
                new StringSchema()
                    ._enum(
                        List.of("paid", "posted", "payment_due", "not_paid", "voided", "pending"))
                    .extensions(
                        Map.of(
                            "x-cb-is-external-enum",
                            true,
                            "x-cb-is-global-enum",
                            false,
                            "x-cb-is-api-column",
                            true,
                            "x-cb-is-gen-separate",
                            false,
                            "x-cb-is-ignore-generation",
                            false)))
            .done();
    var customer =
        buildResource("credit_note").withSubResourceAttribute("allocation", allocation).done();

    var spec = buildSpec().withResources(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from enum import Enum\n"
            + "from chargebee.models import invoice\n"
            + "\n"
            + "@dataclass\n"
            + "class CreditNote:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class Allocation(TypedDict):\n"
            + "        invoice_status: NotRequired[\"invoice.Invoice.Status\"]\n");
  }

  @Test
  void shouldSupportCustomEnumFormatting_JurisType() throws IOException {
    var invoice = buildResource("invoice").withAttribute("id", true).done();
    var operation =
        buildPostOperation("import_invoice")
            .forResource("invoice")
            .withRequestBody(
                "taxes",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "juris_type",
                            new StringSchema()
                                ._enum(
                                    List.of(
                                        "country",
                                        "federal",
                                        "state",
                                        "county",
                                        "city",
                                        "special",
                                        "unincorporated",
                                        "other"))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-sub-resource",
                                        true,
                                        "x-cb-is-api-column",
                                        true,
                                        "x-cb-is-global-enum",
                                        true,
                                        "x-cb-is-external-enum",
                                        true,
                                        "x-cb-is-gen-separate",
                                        true,
                                        "x-cb-is-ignore-generation",
                                        true))))
                    .extensions(Map.of("x-cb-is-composite-array-request-body", true)))
            .withResponse(resourceResponseParam("invoice", invoice))
            .done();
    var spec =
        buildSpec()
            .withResource(invoice)
            .withPostOperation("/invoices/import_invoice", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from chargebee.models import enums\n"
            + "\n"
            + "@dataclass\n"
            + "class Invoice:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class ImportInvoiceTaxParams(TypedDict):\n"
            + "        juris_type: NotRequired[enums.TaxJurisType]");
  }

  @Test
  void shouldHaveGlobalEnumImportInOperation() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off"));
    var invoice = buildResource("invoice").withAttribute("id", true).done();
    var operation =
        buildPostOperation("import_invoice")
            .forResource("invoice")
            .withRequestBody(
                "auto_collection",
                new StringSchema()
                    ._enum(List.of("on", "off"))
                    .extensions(
                        Map.of(
                            "x-cb-is-global-enum",
                            true,
                            "x-cb-global-enum-reference",
                            "./enums/" + autoCollection.typeName + ".yaml",
                            "x-cb-is-gen-separate",
                            true)))
            .withResponse(resourceResponseParam("invoice", invoice))
            .done();
    var spec =
        buildSpec()
            .withResource(invoice)
            .withPostOperation("/invoices/import_invoice", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "from chargebee.models import enums");
  }

  @Test
  void shouldNotHaveEnumReferenceIfEnumPresentInSameResource() throws IOException {
    var entitlement = buildResource("entitlement").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("entitlement")
            .withRequestBody(
                "entitlements",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "entity_type",
                            new ArraySchema()
                                .items(
                                    new StringSchema()
                                        ._enum(
                                            List.of(
                                                "plan",
                                                "addon",
                                                "charge",
                                                "plan_price",
                                                "addon_price"))
                                        .extensions(
                                            Map.of(
                                                "x-cb-is-sub-resource",
                                                true,
                                                "x-cb-is-api-column",
                                                true,
                                                "x-cb-is-global-enum",
                                                false,
                                                "x-cb-is-external-enum",
                                                false,
                                                "x-cb-is-gen-separate",
                                                false,
                                                "x-cb-is-ignore-generation",
                                                false)))))
                    .extensions(Map.of("x-cb-is-composite-array-request-body", true)))
            .withResponse(resourceResponseParam("entitlement", entitlement))
            .done();
    var spec =
        buildSpec().withResource(entitlement).withPostOperation("/entitlements", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Entitlement:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class CreateEntitlementParams(TypedDict):\n"
            + "        entity_type: NotRequired[\"Entitlement.EntityType\"]");
  }

  @Test
  void operationParamsAbstractionClassNameShouldBeInPascalCase() throws IOException {
    var paymentVoucher = buildResource("payment_voucher").withAttribute("id", true).done();
    var operation =
        buildOperation("payment_vouchersForCustomer")
            .forResource("payment_voucher")
            .withPathParam("customer_id")
            .withQueryParam("limit")
            .withQueryParam(
                "status",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "is",
                            new StringSchema()
                                ._enum(List.of("active", "consumed", "expired", "failure"))))
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "EnumFilter")))
            .withResponse(resourceResponseParam("payment_voucher", paymentVoucher))
            .done();
    var spec =
        buildSpec()
            .withResource(paymentVoucher)
            .withOperation("/customers/{customer-id}/payment_vouchers", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "from chargebee.filters import Filters\n"
                + "\n"
                + "@dataclass\n"
                + "class PaymentVoucher:\n"
                + "\n"
                + "    env: environment.Environment\n"
                + "\n"
                + "\n"
                + "\n"
                + "    class PaymentVouchersForCustomerParams(TypedDict):\n"
                + "        limit: NotRequired[str]\n"
                + "        status: NotRequired[Filters.EnumFilter]\n"
                + "\n"
                + "    def payment_vouchers_for_customer(self, id, params:"
                + " PaymentVouchersForCustomerParams = None, headers=None) ->"
                + " PaymentVouchersForCustomerResponse:\n"
                + "        jsonKeys = { \n"
                + "        }\n"
                + "        options = {}\n"
                + "        return request.send('get', request.uri_path(\"customers\", id,"
                + " \"payment_vouchers\"), self.env, cast(Dict[Any, Any], params), headers,"
                + " PaymentVouchersForCustomerResponse,None, False, jsonKeys, options)");
  }

  @Test
  void shouldSupportResourceEnumsInOperationParamsAbstractionClass() throws IOException {
    var customer =
        buildResource("customer")
            .withAttribute("id", true)
            .withAttribute(
                "billing_day_of_week",
                new StringSchema()
                    ._enum(
                        List.of(
                            "sunday",
                            "monday",
                            "tuesday",
                            "wednesday",
                            "thursday",
                            "friday",
                            "saturday")))
            .done();
    var operation =
        buildPostOperation("change_billing_date")
            .forResource("customer")
            .withPathParam("customer_id")
            .withRequestBody("billing_date", new IntegerSchema())
            .withRequestBody(
                "billing_day_of_week",
                new StringSchema()
                    ._enum(
                        List.of(
                            "sunday",
                            "monday",
                            "tuesday",
                            "wednesday",
                            "thursday",
                            "friday",
                            "saturday"))
                    .extensions(
                        Map.of(
                            "x-cb-parameter-blank-option",
                            "as_null",
                            "x-cb-attribute-meta-comment",
                            "optional",
                            "x-cb-meta-model-name",
                            "customers",
                            "x-cb-is-api-column",
                            true,
                            "x-cb-is-global-enum",
                            false,
                            "x-cb-is-external-enum",
                            false,
                            "x-cb-is-gen-separate",
                            false,
                            "x-cb-is-ignore-generation",
                            false,
                            "x-cb-sdk-enum-api-name",
                            "BillingDayOfWeek")))
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers/{customer-id}/change_billing_date", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "from enum import Enum\n"
                + "\n"
                + "@dataclass\n"
                + "class Customer:\n"
                + "\n"
                + "    env: environment.Environment\n"
                + "    class BillingDayOfWeek(Enum):\n"
                + "        SUNDAY = \"sunday\"\n"
                + "        MONDAY = \"monday\"\n"
                + "        TUESDAY = \"tuesday\"\n"
                + "        WEDNESDAY = \"wednesday\"\n"
                + "        THURSDAY = \"thursday\"\n"
                + "        FRIDAY = \"friday\"\n"
                + "        SATURDAY = \"saturday\"\n"
                + "\n"
                + "        def __str__(self):\n"
                + "            return self.value\n"
                + "\n"
                + "\n"
                + "\n"
                + "    class ChangeBillingDateParams(TypedDict):\n"
                + "        billing_date: NotRequired[int]\n"
                + "        billing_day_of_week: NotRequired[\"Customer.BillingDayOfWeek\"]\n"
                + "\n"
                + "    def change_billing_date(self, id, params: ChangeBillingDateParams = None,"
                + " headers=None) -> ChangeBillingDateResponse:\n"
                + "        jsonKeys = { \n"
                + "        }\n"
                + "        options = {}\n"
                + "        return request.send('post', request.uri_path(\"customers\", id,"
                + " \"change_billing_date\"), self.env, cast(Dict[Any, Any], params), headers,"
                + " ChangeBillingDateResponse,None, False, jsonKeys, options)");
  }

  @Test
  void shouldSupportResourceEnumsInOperationSubParams() throws IOException {
    var parentAccountAccess =
        buildResource("parent_account_access")
            .withAttribute(
                "portal_edit_child_subscriptions",
                new StringSchema()._enum(List.of("yes", "view_only", "no")))
            .done();
    var customer =
        buildResource("customer")
            .withAttribute("id", true)
            .withSubResourceAttribute("parent_account_access", parentAccountAccess)
            .done();
    var operation =
        buildPostOperation("relationships")
            .forResource("customer")
            .withPathParam("customer_id")
            .withRequestBody(
                "parent_account_access",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "portal_edit_child_subscriptions",
                            new StringSchema()
                                ._enum(List.of("yes", "view_only", "no"))
                                .extensions(
                                    Map.of(
                                        "x-cb-parameter-blank-option",
                                        "as_null",
                                        "x-cb-attribute-meta-comment",
                                        "optional",
                                        "x-cb-is-sub-resource",
                                        true,
                                        "x-cb-meta-model-name",
                                        "parent_account_accesses",
                                        "x-cb-is-api-column",
                                        true,
                                        "x-cb-is-global-enum",
                                        false,
                                        "x-cb-is-external-enum",
                                        false,
                                        "x-cb-is-gen-separate",
                                        false,
                                        "x-cb-is-ignore-generation",
                                        false,
                                        "x-cb-sdk-enum-api-name",
                                        "PortalEditChildSubscriptions"))))
                    .extensions(
                        Map.of(
                            "x-cb-hidden-from-client-sdk",
                            false,
                            "x-cb-is-multi-value-attribute",
                            false,
                            "x-cb-is-sub-resource",
                            true)))
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec =
        buildSpec()
            .withResource(customer)
            .withPostOperation("/customers/{customer-id}/relationships", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "from enum import Enum\n"
                + "\n"
                + "@dataclass\n"
                + "class Customer:\n"
                + "\n"
                + "    env: environment.Environment\n"
                + "    class ParentAccountAccessPortalEditChildSubscriptions(Enum):\n"
                + "        YES = \"yes\"\n"
                + "        VIEW_ONLY = \"view_only\"\n"
                + "        NO = \"no\"\n"
                + "\n"
                + "        def __str__(self):\n"
                + "            return self.value\n"
                + "\n"
                + "    class ParentAccountAccess(TypedDict):\n"
                + "        portal_edit_child_subscriptions:"
                + " NotRequired[\"Customer.ParentAccountAccessPortalEditChildSubscriptions\"]\n"
                + "\n"
                + "    class RelationshipsParentAccountAccessParams(TypedDict):\n"
                + "        portal_edit_child_subscriptions:"
                + " NotRequired[\"Customer.ParentAccountAccessPortalEditChildSubscriptions\"]\n"
                + "\n"
                + "\n"
                + "    class RelationshipsParams(TypedDict):\n"
                + "        parent_account_access:"
                + " NotRequired[\"Customer.RelationshipsParentAccountAccessParams\"]\n"
                + "\n"
                + "    def relationships(self, id, params: RelationshipsParams = None,"
                + " headers=None) -> RelationshipsResponse:\n"
                + "        jsonKeys = { \n"
                + "        }\n"
                + "        options = {}\n"
                + "        return request.send('post', request.uri_path(\"customers\", id,"
                + " \"relationships\"), self.env, cast(Dict[Any, Any], params), headers,"
                + " RelationshipsResponse,None, False, jsonKeys, options)");
  }

  @Test
  void shouldSupportFilterAttributes() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off"));
    var customer = buildResource("customer").withAttribute("id", true).done();
    var listOperation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam(
                "first_name",
                new ObjectSchema()
                    .properties(Map.of("is", new StringSchema(), "is_not", new StringSchema()))
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-is-presence-operator-supported",
                            true,
                            "x-cb-sdk-filter-name",
                            "StringFilter")))
            .withQueryParam(
                "auto_collection",
                new ObjectSchema()
                    .properties(Map.of("is", new StringSchema()._enum(List.of("on", "off"))))
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "EnumFilter",
                            "x-cb-is-api-column",
                            true,
                            "x-cb-is-global-enum",
                            true,
                            "x-cb-global-enum-reference",
                            "./enums/" + autoCollection.typeName + ".yaml")))
            .withQueryParam(
                "created_at",
                new ObjectSchema()
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "TimestampFilter")))
            .withQueryParam(
                "auto_close_invoices",
                new ObjectSchema()
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "BooleanFilter")))
            .withQueryParam(
                "sort_by",
                new ObjectSchema()
                    .extensions(
                        Map.of(
                            "x-cb-is-filter-parameter",
                            true,
                            "x-cb-sdk-filter-name",
                            "SortFilter")))
            .withQueryParam(
                "einvoice",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "status",
                            new ObjectSchema()
                                .properties(
                                    Map.of(
                                        "is",
                                        new StringSchema()
                                            ._enum(
                                                List.of(
                                                    "scheduled",
                                                    "skipped",
                                                    "in_progress",
                                                    "success",
                                                    "failed",
                                                    "registered"))
                                            .extensions(
                                                Map.of(
                                                    "x-cb-is-external-enum",
                                                    false,
                                                    "x-cb-is-api-column",
                                                    true,
                                                    "x-cb-is-gen-separate",
                                                    false,
                                                    "x-cb-sdk-enum-api-name",
                                                    "Status"))))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-sub-resource", true,
                                        "x-cb-is-filter-parameter", true,
                                        "x-cb-is-api-column", true,
                                        "x-cb-sdk-filter-name", "EnumFilter")))))
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", listOperation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "from chargebee.filters import Filters\n"
                + "\n"
                + "@dataclass\n"
                + "class Customer:\n"
                + "\n"
                + "    env: environment.Environment\n"
                + "\n"
                + "    class ListEinvoiceParams(TypedDict):\n"
                + "        status: NotRequired[Filters.EnumFilter]\n"
                + "\n"
                + "\n"
                + "    class ListParams(TypedDict):\n"
                + "        first_name: NotRequired[Filters.StringFilter]\n"
                + "        auto_collection: NotRequired[Filters.EnumFilter]\n"
                + "        created_at: NotRequired[Filters.TimestampFilter]\n"
                + "        auto_close_invoices: NotRequired[Filters.BooleanFilter]\n"
                + "        sort_by: NotRequired[Filters.SortFilter]\n"
                + "        einvoice: NotRequired[\"Customer.ListEinvoiceParams\"]\n"
                + "\n"
                + "    def list(self, params: ListParams = None, headers=None) -> ListResponse:\n"
                + "        jsonKeys = { \n"
                + "        }\n"
                + "        options = {}\n"
                + "        return request.send_list_request('get', request.uri_path(\"customers\"),"
                + " self.env, cast(Dict[Any, Any], params), headers, ListResponse,None, False,"
                + " jsonKeys, options)");
  }

  @Test
  void shouldSupportNestedFilterAttributes() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var listOperation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam(
                "relationship",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "parent_id",
                            new ObjectSchema()
                                .properties(
                                    Map.of(
                                        "is",
                                        new StringSchema()
                                            ._enum(
                                                List.of(
                                                    "scheduled",
                                                    "skipped",
                                                    "in_progress",
                                                    "success",
                                                    "failed",
                                                    "registered"))
                                            .extensions(
                                                Map.of(
                                                    "x-cb-is-external-enum",
                                                    false,
                                                    "x-cb-is-api-column",
                                                    true,
                                                    "x-cb-is-gen-separate",
                                                    false,
                                                    "x-cb-sdk-enum-api-name",
                                                    "Status"))))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-sub-resource", true,
                                        "x-cb-is-filter-parameter", true,
                                        "x-cb-is-api-column", true,
                                        "x-cb-sdk-filter-name", "EnumFilter")))))
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", listOperation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from .responses import *\n"
                + "from chargebee import request, environment\n"
                + "from typing import TypedDict, Required, NotRequired, Dict, List, Any, cast\n"
                + "from chargebee.filters import Filters\n"
                + "\n"
                + "@dataclass\n"
                + "class Customer:\n"
                + "\n"
                + "    env: environment.Environment\n"
                + "\n"
                + "    class ListRelationshipParams(TypedDict):\n"
                + "        parent_id: NotRequired[Filters.EnumFilter]\n"
                + "\n"
                + "\n"
                + "    class ListParams(TypedDict):\n"
                + "        relationship: NotRequired[\"Customer.ListRelationshipParams\"]\n"
                + "\n"
                + "    def list(self, params: ListParams = None, headers=None) -> ListResponse:\n"
                + "        jsonKeys = { \n"
                + "        }\n"
                + "        options = {}\n"
                + "        return request.send_list_request('get', request.uri_path(\"customers\"),"
                + " self.env, cast(Dict[Any, Any], params), headers, ListResponse,None, False,"
                + " jsonKeys, options)");
  }

  @Test
  void shouldHaveDefinitionForFilterSubResourceAttribute() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var listOperation =
        buildListOperation("list")
            .forResource("customer")
            .withQueryParam(
                "einvoice",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "status",
                            new ObjectSchema()
                                .properties(
                                    Map.of(
                                        "is",
                                        new StringSchema()
                                            ._enum(
                                                List.of(
                                                    "scheduled",
                                                    "skipped",
                                                    "in_progress",
                                                    "success",
                                                    "failed",
                                                    "registered"))
                                            .extensions(
                                                Map.of(
                                                    "x-cb-is-external-enum",
                                                    false,
                                                    "x-cb-is-api-column",
                                                    true,
                                                    "x-cb-is-gen-separate",
                                                    false,
                                                    "x-cb-sdk-enum-api-name",
                                                    "Status"))))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-sub-resource", true,
                                        "x-cb-is-filter-parameter", true,
                                        "x-cb-is-api-column", true,
                                        "x-cb-sdk-filter-name", "EnumFilter")))))
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();
    var spec = buildSpec().withResource(customer).withOperation("/customers", listOperation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from chargebee.filters import Filters\n"
            + "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class ListEinvoiceParams(TypedDict):\n"
            + "        status: NotRequired[Filters.EnumFilter]\n");
  }

  @Test
  void shouldSupportEnumsInMultiAttributesInRequestBodyParams() throws IOException {
    var invoice = buildResource("invoice").withAttribute("id").done();
    var createForChargeItemsAndChargesOperation =
        buildPostOperation("createForChargeItemsAndCharges")
            .forResource("invoice")
            .withRequestBody(
                "payment_intent",
                new StringSchema()
                    .properties(
                        Map.of(
                            "payment_method_type",
                            new StringSchema()
                                ._enum(List.of("card", "ideal", "sofort"))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-sub-resource",
                                        true,
                                        "x-cb-meta-model-name",
                                        "payment_intent"))))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("invoice", invoice))
            .withQueryParam("paid_on_after")
            .asInputObjNeeded()
            .done();
    var spec =
        buildSpec()
            .withResource(invoice)
            .withPostOperation(
                "/invoices/create_for_charge_items_and_charges",
                createForChargeItemsAndChargesOperation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from chargebee.models import payment_intent\n"
            + "\n"
            + "@dataclass\n"
            + "class Invoice:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class CreateForChargeItemsAndChargesPaymentIntentParams(TypedDict):\n"
            + "        payment_method_type:"
            + " NotRequired[\"payment_intent.PaymentIntent.PaymentMethodType\"]");
  }

  @Test
  void shouldHaveGlobalEnumSupportForOperationSubParams() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off"));
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "billing_address",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "first_name",
                            new StringSchema(),
                            "last_name",
                            new StringSchema(),
                            "auto_collection",
                            new StringSchema()
                                ._enum(List.of("on", "off"))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-global-enum",
                                        true,
                                        "x-cb-global-enum-reference",
                                        "./enums/" + autoCollection.typeName + ".yaml",
                                        "x-cb-is-gen-separate",
                                        true,
                                        "x-cb-is-sub-resource",
                                        true))))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from chargebee.models import enums\n"
            + "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class CreateBillingAddressParams(TypedDict):\n"
            + "        auto_collection: NotRequired[enums.AutoCollection]");
  }

  @Test
  void shouldHaveIntDataTypeForMoneyColumn() throws IOException {
    var billingAddress =
        buildResource("billing_address")
            .withAttribute(
                "price",
                new IntegerSchema()
                    .type("integer")
                    .format("int64")
                    .extensions(Map.of("x-cb-is-money-column", true)))
            .done();
    var addon =
        buildResource("addon").withSubResourceAttribute("billing_address", billingAddress).done();

    var spec = buildSpec().withResource(addon).done();
    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Addon:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class BillingAddress(TypedDict):\n"
            + "        price: NotRequired[int]");
  }

  @Test
  void shouldHaveIntDataTypeForMoneyColumnInOperationParams() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var operation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "billing_address",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "price",
                            new IntegerSchema()
                                .type("integer")
                                .format("int64")
                                .extensions(
                                    Map.of(
                                        "x-cb-is-money-column",
                                        true,
                                        "x-cb-is-sub-resource",
                                        true))))
                    .extensions(Map.of("x-cb-is-sub-resource", true)))
            .withResponse(resourceResponseParam("customer", customer))
            .done();
    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "\n"
            + "    class CreateBillingAddressParams(TypedDict):\n"
            + "        price: NotRequired[int]");
  }

  @Test
  void shouldSupportEnumApiName() throws IOException {
    var customer =
        buildResource("customer")
            .withAttribute("id", true)
            .withAttribute(
                "auto_collection",
                new StringSchema()
                    ._enum(List.of("on", "off"))
                    .extensions(Map.of("x-cb-sdk-enum-api-name", "AutoCollection")))
            .done();
    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(5);
    assertResourceOperationContents(
        writeStringFileOp,
        "from enum import Enum\n"
            + "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "    class AutoCollection(Enum):\n"
            + "        ON = \"on\"\n"
            + "        OFF = \"off\"\n"
            + "\n"
            + "        def __str__(self):\n"
            + "            return self.value\n");
  }

  @Test
  void shouldSupportSingularAndListDependentResources() throws IOException {
    var shipping_address = buildResource("shipping_address").done();
    var contract_term = buildResource("contract_term").done();
    var subscriptionEstimate =
        buildResource("subscription_estimate")
            .asDependentAttribute()
            .asDependentResource()
            .withSubResourceAttribute("shipping_address", shipping_address)
            .withSubResourceAttribute("contract_term", contract_term)
            .done();
    var estimate =
        buildResource("estimate")
            .withSubResourceAttributeReference("subscription_estimate", subscriptionEstimate)
            .withSubResourceArrayAttributeReference("subscription_estimates", subscriptionEstimate)
            .done();
    var spec = buildSpec().withResources(subscriptionEstimate, estimate).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from dataclasses import dataclass\n"
                + "from chargebee.model import Model\n"
                + "from typing import Dict, List, Any\n"
                + "from chargebee.models import subscription_estimate\n"
                + "\n"
                + "@dataclass\n"
                + "class EstimateResponse(Model):\n"
                + "    raw_data: Dict[Any, Any] = None\n"
                + "    subscription_estimate:"
                + " \"subscription_estimate.SubscriptionEstimateResponse\" = None\n"
                + "    subscription_estimates:"
                + " List[\"subscription_estimate.SubscriptionEstimateResponse\"] = None");
  }

  @Test
  void eachResourceShouldHaveResponseFile() throws IOException {
    var spec = buildSpec().withResources("customer", "subscription").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(fileOps.get(6), "/python/chargebee/models/customer", "responses.py");
    assertWriteStringFileOp(
        fileOps.get(10), "/python/chargebee/models/subscription", "responses.py");
  }

  @Test
  void shouldHaveDataClassDefinitionForAllSubResources() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var vat_number_status =
        buildEnum("vat_number_status", List.of("valid", "invalid", "not_validated", "undetermined"))
            .asApi()
            .done();
    var card_status = buildEnum("card_status", List.of("no_card", "valid")).asApi().done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .withEnumAttribute(vat_number_status)
            .withEnumAttribute(card_status)
            .done();
    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertResourceResponseContents(
        writeStringFileOp,
        "@dataclass\n"
            + "class BillingAddressResponse(Model):\n"
            + "    raw_data: Dict[Any, Any] = None\n"
            + "    first_name: str = None\n");
  }

  @Test
  void shouldHaveDataClassDefinitionForResource() throws IOException {
    var billingAddress = buildResource("billing_address").withAttribute("first_name").done();
    var vat_number_status =
        buildEnum("vat_number_status", List.of("valid", "invalid", "not_validated", "undetermined"))
            .asApi()
            .done();
    var card_status = buildEnum("card_status", List.of("no_card", "valid")).asApi().done();
    var customer =
        buildResource("customer")
            .withSubResourceAttribute("billing_address", billingAddress)
            .withEnumAttribute(vat_number_status)
            .withEnumAttribute(card_status)
            .done();
    var spec = buildSpec().withResource(customer).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertResourceResponseContents(
        writeStringFileOp,
        "@dataclass\n"
            + "class BillingAddressResponse(Model):\n"
            + "    raw_data: Dict[Any, Any] = None\n"
            + "    first_name: str = None\n"
            + "\n"
            + "\n"
            + "@dataclass\n"
            + "class CustomerResponse(Model):\n"
            + "    raw_data: Dict[Any, Any] = None\n"
            + "    billing_address: BillingAddressResponse = None\n"
            + "    vat_number_status: str = None\n"
            + "    card_status: str = None\n");
  }

  @Test
  void shouldHaveOperationResponseAbstractionClassDefinitionInResponseFile() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var card = buildResource("card").done();
    var operation =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody("id", new StringSchema())
            .withRequestBody("first_name", new StringSchema())
            .withRequestBody("auto_collection", new StringSchema()._enum(List.of("on", "off")))
            .withRequestBody("net_term_days", new IntegerSchema())
            .withResponse(
                resourceResponseParam("customer", customer),
                resourceResponseParam("card", card, false))
            .done();

    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from dataclasses import dataclass\n"
                + "from chargebee.model import Model\n"
                + "from typing import Dict, List, Any\n"
                + "from chargebee.response import Response\n"
                + "from chargebee.models import card\n"
                + "\n"
                + "@dataclass\n"
                + "class CustomerResponse(Model):\n"
                + "    raw_data: Dict[Any, Any] = None\n"
                + "    id: str = None\n"
                + "\n"
                + "\n"
                + "@dataclass\n"
                + "class CreateResponse(Response):\n"
                + "    is_idempotency_replayed: bool\n"
                + "    customer: CustomerResponse\n"
                + "    card: \"card.CardResponse\" = None");
  }

  @Test
  void shouldHaveResponseClassInheritanceForPostOperationResponseClass() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var card = buildResource("card").done();
    var operation =
        buildPostOperation("update")
            .forResource("customer")
            .withRequestBody("id", new StringSchema())
            .withRequestBody("first_name", new StringSchema())
            .withRequestBody("auto_collection", new StringSchema()._enum(List.of("on", "off")))
            .withRequestBody("net_term_days", new IntegerSchema())
            .withResponse(
                resourceResponseParam("customer", customer),
                resourceResponseParam("card", card, false))
            .done();

    var spec = buildSpec().withResource(customer).withPostOperation("/customers", operation).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from dataclasses import dataclass\n"
                + "from chargebee.model import Model\n"
                + "from typing import Dict, List, Any\n"
                + "from chargebee.response import Response\n"
                + "from chargebee.models import card\n"
                + "\n"
                + "@dataclass\n"
                + "class CustomerResponse(Model):\n"
                + "    raw_data: Dict[Any, Any] = None\n"
                + "    id: str = None\n"
                + "\n"
                + "\n"
                + "@dataclass\n"
                + "class UpdateResponse(Response):\n"
                + "    is_idempotency_replayed: bool\n"
                + "    customer: CustomerResponse\n"
                + "    card: \"card.CardResponse\" = None");
  }

  @Test
  void shouldNotHaveResponseClassInheritanceForGetOperationResponseClass() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var card = buildResource("card").withAttribute("id", true).done();
    var listCustomerOperation =
        buildListOperation("list")
            .forResource("customer")
            .withResponse(
                resourceResponseParam("customer", customer),
                resourceResponseParam("card", card, false))
            .done();

    var spec =
        buildSpec()
            .withResources(customer)
            .withOperation("/customers", listCustomerOperation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from dataclasses import dataclass\n"
                + "from chargebee.model import Model\n"
                + "from typing import Dict, List, Any\n"
                + "from chargebee.response import Response\n"
                + "from chargebee.models import card\n"
                + "\n"
                + "@dataclass\n"
                + "class CustomerResponse(Model):\n"
                + "    raw_data: Dict[Any, Any] = None\n"
                + "    id: str = None\n"
                + "\n"
                + "\n"
                + "@dataclass\n"
                + "class ListCustomerResponse:\n"
                + "    customer: CustomerResponse\n"
                + "    card: \"card.CardResponse\" = None\n"
                + "\n"
                + "@dataclass\n"
                + "class ListResponse(Response):\n"
                + "\n"
                + "    list: List[ListCustomerResponse]\n"
                + "    next_offset: str = None");
  }

  @Test
  void shouldSupportArrayTypeInReturnResponse() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var hierarchy = buildResource("hierarchy").withAttribute("id", true);
    var operation =
        buildOperation("hierarchy")
            .forResource("customer")
            .withPathParam("customer-id")
            .withResponse(resourceResponseParam("hierarchies", hierarchy.arraySchema()))
            .done();
    var spec =
        buildSpec()
            .withResources(customer, hierarchy.done())
            .withOperation("/customers/{customer-id}/hierarchy", operation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from dataclasses import dataclass\n"
                + "from chargebee.model import Model\n"
                + "from typing import Dict, List, Any\n"
                + "from chargebee.response import Response\n"
                + "from chargebee.models import hierarchy\n"
                + "\n"
                + "@dataclass\n"
                + "class CustomerResponse(Model):\n"
                + "    raw_data: Dict[Any, Any] = None\n"
                + "    id: str = None\n"
                + "\n"
                + "\n"
                + "@dataclass\n"
                + "class HierarchyResponse(Response):\n"
                + "\n"
                + "    hierarchies: List[\"hierarchy.HierarchyResponse\"]");
  }

  @Test
  void shouldSupportListOperationResponses() throws IOException {
    var customer = buildResource("customer").withAttribute("id", true).done();
    var card = buildResource("card").withAttribute("id", true).done();
    var listCustomerOperation =
        buildListOperation("list")
            .forResource("customer")
            .withResponse(
                resourceResponseParam("customer", customer),
                resourceResponseParam("card", card, false))
            .done();

    var spec =
        buildSpec()
            .withResources(customer)
            .withOperation("/customers", listCustomerOperation)
            .done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from dataclasses import dataclass\n"
                + "from chargebee.model import Model\n"
                + "from typing import Dict, List, Any\n"
                + "from chargebee.response import Response\n"
                + "from chargebee.models import card\n"
                + "\n"
                + "@dataclass\n"
                + "class CustomerResponse(Model):\n"
                + "    raw_data: Dict[Any, Any] = None\n"
                + "    id: str = None\n"
                + "\n"
                + "\n"
                + "@dataclass\n"
                + "class ListCustomerResponse:\n"
                + "    customer: CustomerResponse\n"
                + "    card: \"card.CardResponse\" = None\n"
                + "\n"
                + "@dataclass\n"
                + "class ListResponse(Response):\n"
                + "\n"
                + "    list: List[ListCustomerResponse]\n"
                + "    next_offset: str = None");
  }

  @Test
  void shouldSupportCustomFormattingForGatewayErrorDetails() throws IOException {
    var activePaymentAttempt =
        buildResource("active_payment_attempt")
            .withAttribute(
                "error_detail",
                new ObjectSchema()
                    .extensions(
                        Map.of(
                            "x-cb-is-sub-resource",
                            true,
                            "x-cb-sub-resource-name",
                            "GatewayErrorDetail",
                            "x-cb-is-global-resource-reference",
                            true)))
            .withExtensions(
                Map.of(
                    "x-cb-is-sub-resource",
                    true,
                    "x-cb-sub-resource-name",
                    "PaymentAttempt",
                    "x-cb-sub-resource-parent-name",
                    "PaymentIntent"))
            .done();
    var paymentIntent =
        buildResource("payment_intent")
            .withAttribute("active_payment_attempt", activePaymentAttempt)
            .done();

    var spec = buildSpec().withResources(paymentIntent).done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    var writeStringFileOp = (FileOp.WriteString) fileOps.get(6);
    assertThat(writeStringFileOp.fileContent)
        .startsWith(
            "from dataclasses import dataclass\n"
                + "from chargebee.model import Model\n"
                + "from typing import Dict, List, Any\n"
                + "from chargebee.models import gateway_error_detail\n"
                + "\n"
                + "@dataclass\n"
                + "class PaymentAttemptResponse(Model):\n"
                + "    raw_data: Dict[Any, Any] = None\n"
                + "    error_detail: gateway_error_detail.GatewayErrorDetailResponse = None\n"
                + "\n"
                + "\n"
                + "@dataclass\n"
                + "class PaymentIntentResponse(Model):\n"
                + "    raw_data: Dict[Any, Any] = None\n"
                + "    active_payment_attempt: PaymentAttemptResponse = None\n");
  }

  @Test
  void shouldHaveResourceInstantiationLogicInMainFunction() throws IOException {
    var spec = buildSpec().withResources("customer", "subscription").done();

    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);

    assertWriteStringFileOp(
        fileOps.get(11),
        basePath,
        "main.py",
        FileOp.fetchFileContent("src/test/java/com/chargebee/sdk/python/samples/main.txt"));
  }

  @Test
  void shouldSupportSchemalessGlobalEnum() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var customer =
        buildResource("customer")
            .withAttribute("id", true)
            .withEnumAttribute(autoCollection)
            .done();
    var card =
        buildResource("card").withEnumAttribute(autoCollection).withAttribute("cvv", false).done();
    var createCustomer =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "card",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "preferred_scheme",
                            new StringSchema()
                                ._enum(List.of("plan", "plan_setup"))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-global-enum",
                                        false,
                                        "x-cb-meta-model-name",
                                        "cards")))))
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();

    var spec =
        buildSpec()
            .withTwoResources(customer, card)
            .withPostOperation("/customer/create", createCustomer)
            .done();
    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(9);
    assertResourceOperationContents(
        writeStringFileOp,
        "from enum import Enum\n"
            + "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "    class Autocollection(Enum):\n"
            + "        ON = \"On\"\n"
            + "        OFF = \"Off\"\n"
            + "\n"
            + "        def __str__(self):\n"
            + "            return self.value");
  }

  @Test
  void shouldSupportSchemalessGlobalEnumNegativeBranch() throws IOException {
    var autoCollection = buildEnum("AutoCollection", List.of("On", "Off")).done();
    var customer =
        buildResource("customer")
            .withAttribute("id", true)
            .withEnumAttribute(autoCollection)
            .done();
    var card = buildResource("card").withAttribute("cvv", false).done();
    var createCustomer =
        buildPostOperation("create")
            .forResource("customer")
            .withRequestBody(
                "card",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "preferred_scheme",
                            new StringSchema()
                                ._enum(List.of("plan", "plan_setup"))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-global-enum",
                                        true,
                                        "x-cb-meta-model-name",
                                        "cards")))))
            .withRequestBody(
                "card",
                new ObjectSchema()
                    .properties(
                        Map.of(
                            "preferred_scheme",
                            new StringSchema()
                                ._enum(List.of("plan", "plan_setup"))
                                .extensions(
                                    Map.of(
                                        "x-cb-is-global-enum",
                                        true,
                                        "x-cb-meta-model-name",
                                        "abc")))))
            .withRequestBody("id", new StringSchema(), true)
            .withResponse(resourceResponseParam("customer", customer))
            .asInputObjNeeded()
            .done();

    var spec =
        buildSpec()
            .withTwoResources(customer, card)
            .withPostOperation("/customer/create", createCustomer)
            .done();
    List<FileOp> fileOps = pythonSdkGen.generate(basePath, spec);
    var writeStringFileOp = (FileOp.WriteString) fileOps.get(9);
    assertResourceOperationContents(
        writeStringFileOp,
        "from enum import Enum\n"
            + "\n"
            + "@dataclass\n"
            + "class Customer:\n"
            + "\n"
            + "    env: environment.Environment\n"
            + "    class Autocollection(Enum):\n"
            + "        ON = \"On\"\n"
            + "        OFF = \"Off\"\n"
            + "\n"
            + "        def __str__(self):\n"
            + "            return self.value");
  }
}
