package com.chargebee.openapi;

import io.swagger.v3.oas.models.info.Info;
import java.util.Map;

public class Version {
  public final ApiVersion apiVersion;
  public final ProductCatalogVersion productCatalogVersion;

  public static final String PRODUCT_CATALOG_VERSION = "x-cb-product-catalog-version";
  public static final String API_VERSION = "x-cb-api-version";

  private Version(ApiVersion apiVersion, ProductCatalogVersion productCatalogVersion) {
    this.apiVersion = apiVersion;
    this.productCatalogVersion = productCatalogVersion;
  }

  public static Version get(Info info) {
    var apiVersion = ApiVersion.V2;
    var productCatalogVersion = ProductCatalogVersion.PC2;
    if (info == null) {
      return new Version(apiVersion, productCatalogVersion);
    }
    Map<String, Object> extensions = info.getExtensions();
    if (extensions != null) {
      var apiVer = extensions.get(API_VERSION);
      if (apiVer != null && (int) apiVer == ApiVersion.V1.number) {
        apiVersion = ApiVersion.V1;
        productCatalogVersion = ProductCatalogVersion.PC1;
      }
      if (apiVer != null && (int) apiVer == ApiVersion.V2.number) {
        var pcVer = extensions.get(PRODUCT_CATALOG_VERSION);
        if (pcVer != null && (int) pcVer == ProductCatalogVersion.PC1.number) {
          productCatalogVersion = ProductCatalogVersion.PC1;
        }
      }
    }
    return new Version(apiVersion, productCatalogVersion);
  }
}
