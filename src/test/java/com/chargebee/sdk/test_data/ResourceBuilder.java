package com.chargebee.sdk.test_data;

import static com.chargebee.openapi.Resource.RESOURCE_ID_EXTENSION;
import static com.chargebee.openapi.Version.PRODUCT_CATALOG_VERSION;

import com.chargebee.openapi.ProductCatalogVersion;
import io.swagger.v3.oas.models.media.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.data.MapEntry;

public class ResourceBuilder {
  private final String resourceId;
  private final String resourceName;
  private final Schema<?> schema;

  private ResourceBuilder(String resourceId) {
    this.resourceId = resourceId;
    this.resourceName = snakeCaseToPascalCase(resourceId);
    LinkedHashMap extensions = new LinkedHashMap<>();
    extensions.put(RESOURCE_ID_EXTENSION, resourceId);
    this.schema = new ObjectSchema().extensions(extensions);
  }

  public static ResourceBuilder buildResource(String resourceId) {
    return new ResourceBuilder(resourceId);
  }

  public ResourceBuilder withProductCatalogVersion(ProductCatalogVersion productCatalogVersion) {
    schema.addExtension(PRODUCT_CATALOG_VERSION, productCatalogVersion.number);
    return this;
  }

  public ResourceBuilder withAttribute(String attributeName) {
    return withAttribute(attributeName, false);
  }

  public ResourceBuilder withAttribute(
      String attributeName, String description, boolean isRequired) {
    return withAttribute(attributeName, new StringSchema().description(description), isRequired);
  }

  public ResourceBuilder withAttribute(String attributeName, boolean isRequired) {
    return withAttribute(attributeName, new StringSchema(), isRequired);
  }

  public ResourceBuilder withAttribute(String attributeName, Schema attributeSchema) {
    return withAttribute(attributeName, attributeSchema, false);
  }

  public ResourceBuilder withAttribute(
      String attributeName, MapEntry<String, Schema<?>> subResource) {
    return withAttribute(attributeName, subResource.value, false);
  }

  public ResourceBuilder withAttribute(
      String attributeName, Schema attributeSchema, boolean isRequired) {
    schema.addProperty(attributeName, attributeSchema);
    if (isRequired) {
      List required = schema.getRequired();
      if (required == null) {
        schema.setRequired(List.of(attributeName));
        return this;
      }
      required.add(attributeName);
    }
    return this;
  }

  private String snakeCaseToPascalCase(String str) {
    return Arrays.stream(str.split("_"))
        .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
        .collect(Collectors.joining());
  }

  public MapEntry<String, Schema<?>> done() {
    return MapEntry.entry(resourceName, schema);
  }

  public ResourceBuilder withEmailAttribute(String attributeName) {
    return withAttribute(attributeName, new EmailSchema(), false);
  }

  public ResourceBuilder withPasswordAttribute(String attributeName) {
    return withAttribute(attributeName, new PasswordSchema(), false);
  }

  public Schema arraySchema() {
    return new ArraySchema().items(new Schema().$ref(resourceName));
  }

  public ResourceBuilder withEnumRefAttribute(String attributeName, String typeName) {
    Map<String, Object> extensions =
        Map.of("x-cb-global-enum-reference", "./enums/" + typeName + ".yaml");
    return withAttribute(
        attributeName,
        new StringSchema()._enum(List.of("foo", "bar")).extensions(extensions),
        false);
  }

  public ResourceBuilder withEnumRefAttribute(
      String attributeName, String typeName, Boolean required) {
    Map<String, Object> extensions =
        Map.of("x-cb-global-enum-reference", "./enums/" + typeName + ".yaml");
    return withAttribute(
        attributeName,
        new StringSchema()._enum(List.of("foo", "bar")).extensions(extensions),
        required);
  }

  public ResourceBuilder withEnumAttribute(String attributeName, List<String> values) {
    return withAttribute(attributeName, new StringSchema()._enum(values), false);
  }

  public ResourceBuilder withEnumAttribute(
      String attributeName, List<String> values, Boolean required) {
    return withAttribute(attributeName, new StringSchema()._enum(values), required);
  }

  public ResourceBuilder withEnumAttribute(MapEntry<String, Schema<?>> attribute) {
    return withAttribute(attribute.key, attribute.value, false);
  }

  public ResourceBuilder withEnumAttribute(
      MapEntry<String, Schema<?>> attribute, Boolean required) {
    return withAttribute(attribute.key, attribute.value, required);
  }

  public ResourceBuilder withDeprecatedAttributes(List<String> attributeNames) {
    for (String attributeName : attributeNames) {
      if (schema.getProperties() != null && schema.getProperties().containsKey(attributeName)) {
        schema.getProperties().get(attributeName).setDeprecated(true);
      }
    }
    return this;
  }

  public ResourceBuilder withSubResourceAttribute(
      String attributeName, MapEntry<String, Schema<?>> subResource) {
    subResource.value.addExtension("x-cb-is-sub-resource", true);
    subResource.value.addExtension("x-cb-sub-resource-name", subResource.key);
    subResource.value.addExtension("x-cb-sub-resource-parent-name", resourceName);
    return withAttribute(attributeName, subResource.value);
  }

  public ResourceBuilder withSubResourceAttributeReference(
      String attributeName, MapEntry<String, Schema<?>> subResource) {
    subResource.value.addExtension("x-cb-is-sub-resource", true);
    subResource.value.addExtension("x-cb-sub-resource-name", subResource.key);
    subResource.value.addExtension("x-cb-is-global-resource-reference", true);
    return withAttribute(attributeName, subResource.value);
  }

  public ResourceBuilder withSubResourceArrayAttribute(
      String attributeName, MapEntry<String, Schema<?>> subResource) {
    subResource.value.addExtension("x-cb-is-sub-resource", true);
    subResource.value.addExtension("x-cb-sub-resource-name", subResource.key);
    subResource.value.addExtension("x-cb-sub-resource-parent-name", resourceName);
    return withAttribute(attributeName, new ArraySchema().items(subResource.value));
  }

  public ResourceBuilder withSubResourceArrayAttributeReference(
      String attributeName, MapEntry<String, Schema<?>> subResource) {
    subResource.value.addExtension("x-cb-is-sub-resource", true);
    subResource.value.addExtension("x-cb-sub-resource-name", subResource.key);
    subResource.value.addExtension("x-cb-is-global-resource-reference", true);
    return withAttribute(attributeName, new ArraySchema().items(subResource.value));
  }

  public ResourceBuilder asHiddenFromSDKGeneration() {
    schema.addExtension("x-cb-hidden-from-client-sdk", true);
    return this;
  }

  public ResourceBuilder asThirdPartyFromSDKGeneration() {
    schema.addExtension("x-cb-is-third-party-resource", true);
    return this;
  }

  public ResourceBuilder asDependentResource() {
    schema.addExtension("x-cb-is-dependent-resource", true);
    return this;
  }

  public ResourceBuilder withCustomFieldSupport() {
    schema.setAdditionalProperties(true);
    return this;
  }

  public ResourceBuilder withSortOrder(int sortOrder) {
    schema.addExtension("x-cb-sort-order", sortOrder);
    return this;
  }

  public ResourceBuilder asDependentAttribute() {
    schema.addExtension("x-cb-is-dependent-attribute", true);
    return this;
  }

  public ResourceBuilder withExtensions(java.util.Map<String, Object> extensions) {
    for (Map.Entry<String, Object> entry : extensions.entrySet()) {
      schema.addExtension(entry.getKey(), entry.getValue());
    }
    return this;
  }
}
