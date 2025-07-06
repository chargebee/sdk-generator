package com.chargebee.sdk.test_data;

import static com.chargebee.openapi.Extension.API_VERSION;
import static com.chargebee.openapi.Extension.PRODUCT_CATALOG_VERSION;
import static com.chargebee.sdk.test_data.ResourceBuilder.buildResource;

import com.chargebee.openapi.ApiVersion;
import com.chargebee.openapi.ProductCatalogVersion;
import com.chargebee.openapi.Spec;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.assertj.core.data.MapEntry;

public class SpecBuilder {
  private final OpenAPI openAPI;

  private SpecBuilder() {
    openAPI = new OpenAPI().components(new Components());
  }

  public static SpecBuilder buildSpec() {
    return new SpecBuilder();
  }

  public Spec done() {
    return new Spec(openAPI);
  }

  public SpecBuilder withResources(String... resourceIds) {
    for (var resourceId : resourceIds) {
      withResource(buildResource(resourceId).done());
    }
    return this;
  }

  public SpecBuilder withResources(MapEntry<String, Schema<?>>... resources) {
    for (var resource : resources) {
      openAPI.getComponents().addSchemas(resource.key, resource.value);
    }
    return this;
  }

  public SpecBuilder withEnums(MapEntry<String, Schema<?>>... enums) {
    for (var anEnum : enums) {
      openAPI.getComponents().addSchemas(anEnum.key, anEnum.value);
    }
    return this;
  }

  public SpecBuilder withResource(MapEntry<String, Schema<?>> resource) {
    openAPI.getComponents().addSchemas(resource.key, resource.value);
    return this;
  }

  public SpecBuilder withTwoResources(
      MapEntry<String, Schema<?>> resourceA, MapEntry<String, Schema<?>> resourceB) {
    openAPI.getComponents().addSchemas(resourceA.key, resourceA.value);
    openAPI.getComponents().addSchemas(resourceB.key, resourceB.value);
    return this;
  }

  public SpecBuilder withVersion(
      ApiVersion apiVersion, ProductCatalogVersion productCatalogVersion) {
    Info info = openAPI.getInfo();
    if (info == null) {
      openAPI.info(
          new Info()
              .extensions(
                  Map.of(
                      API_VERSION,
                      apiVersion.number,
                      PRODUCT_CATALOG_VERSION,
                      productCatalogVersion.number)));
      return this;
    }
    info.addExtension(API_VERSION, apiVersion.number);
    info.addExtension(PRODUCT_CATALOG_VERSION, productCatalogVersion.number);
    return this;
  }

  public SpecBuilder withOperation(String path, Operation operation) {
    openAPI.path(path, new PathItem().get(operation));
    return this;
  }

  public SpecBuilder withOperations(OperationWithPath... operationWithPaths) {
    for (var operationWithPath : operationWithPaths) {
      withOperation(operationWithPath.path(), operationWithPath.operation());
    }
    return this;
  }

  public SpecBuilder withPostOperation(String path, Operation operation) {
    openAPI.path(path, new PathItem().post(operation));
    return this;
  }

  public SpecBuilder withPostOperations(OperationWithPath... operationWithPaths) {
    for (var operationWithPath : operationWithPaths) {
      withPostOperation(operationWithPath.path(), operationWithPath.operation());
    }
    return this;
  }
}
