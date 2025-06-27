package com.chargebee.openapi;

public enum ProductCatalogVersion {
  PC1(1),
  PC2(2);

  public final int number;

  ProductCatalogVersion(int number) {

    this.number = number;
  }
}
