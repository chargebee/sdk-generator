package com.chargebee.sdk.common;

import java.util.List;

public class Constant {

  public static final boolean SDK_DEBUG = false;
  public static final List<String> DEBUG_RESOURCE =
      List.of("Customer", "Subscription", "Gift", "PaymentIntent", "Export", "Estimate");
  public static final String SORT_BY = "sort_by";

  private Constant() {}
}
