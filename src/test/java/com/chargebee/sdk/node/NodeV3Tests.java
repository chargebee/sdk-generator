package com.chargebee.sdk.node;

import static com.chargebee.sdk.test_data.OperationBuilder.*;
import static com.chargebee.sdk.test_data.ResourceBuilder.buildResource;
import static com.chargebee.sdk.test_data.ResourceResponseParam.resourceResponseParam;
import static com.chargebee.sdk.test_data.SpecBuilder.buildSpec;
import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.LanguageTests;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class NodeV3Tests extends LanguageTests {
  public static NodeV3 node;

  @BeforeAll
  static void beforeAll() throws IOException {
    node = new NodeV3();
  }

  @Test
  void shouldCreateApiEndpointsFile() throws IOException {
    var spec = buildSpec().done();
    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
        }
        export const Endpoints:Endpoints = {
        };""");
  }

  @Test
  void eachResourceShouldBeOrderedBasedOnTheirSortOrder() throws IOException {
    var subscription = buildResource("subscription").withSortOrder(0).done();
    var contract_term = buildResource("contract_term").withSortOrder(2).done();

    var spec = buildSpec().withResources(subscription, contract_term).done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            subscription : EndpointTuple[]
            contractTerm : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "subscription": [],
          "contractTerm": []
        };""");
  }

  @Test
  void shouldIgnoreHiddenFromSDKResources() throws IOException {
    var subscription = buildResource("subscription").withSortOrder(0).done();
    var subscription_preview =
        buildResource("subscription_preview").asHiddenFromSDKGeneration().withSortOrder(1).done();

    var spec = buildSpec().withResources(subscription, subscription_preview).done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            subscription : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "subscription": []
        };""");
  }

  @Test
  void shouldIgnoreHiddenFromSDKOverrideResources() throws IOException {
    var subscription = buildResource("subscription").withSortOrder(0).done();
    var subscription_preview = buildResource("media").withSortOrder(1).done();

    var spec = buildSpec().withResources(subscription, subscription_preview).done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            subscription : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "subscription": []
        };""");
  }

  @Test
  void shouldIgnoreDependentResources() throws IOException {
    var subscription = buildResource("subscription").withSortOrder(0).done();
    var contract_term = buildResource("contract_term").withSortOrder(2).done();
    var credit_note_estimate =
        buildResource("credit_note_estimate").asDependentResource().withSortOrder(26).done();

    var spec = buildSpec().withResources(subscription, contract_term, credit_note_estimate).done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            subscription : EndpointTuple[]
            contractTerm : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "subscription": [],
          "contractTerm": []
        };""");
  }

  @Test
  void eachActionOfResourceShouldBeOrderedBasedOnTheirSortOrder() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var createWithItemsOperation =
        buildPostOperation("createWithItems")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withPathParam("customer-id")
            .withSortOrder(1)
            .done();
    var listSubscriptionOperation =
        buildListOperation("list")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withSortOrder(3)
            .done();
    var spec =
        buildSpec()
            .withResource(subscription)
            .withPostOperation(
                "/customers/{customer-id}/subscription_for_items", createWithItemsOperation)
            .withOperation("/subscriptions", listSubscriptionOperation)
            .done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            subscription : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "subscription": [
            [
              "createWithItems",
              "POST",
              "/customers",
              "/subscription_for_items",
              true,null, false,
              {\s
              },
              {}
            ],
            [
              "list",
              "GET",
              "/subscriptions",
              null,
              false,null, false,
            {\s
            }, {}
            ]
          ]
        };""");
  }

  @Test
  void shouldIgnoreHiddenFromSDKActions() throws IOException {
    var token = buildResource("token").withAttribute("id", true).done();
    var createForCardOperation =
        buildPostOperation("create_for_card")
            .forResource("token")
            .withResponse(resourceResponseParam("token", token))
            .asHiddenFromSDKGeneration()
            .withSortOrder(0)
            .done();
    var spec =
        buildSpec()
            .withResource(token)
            .withPostOperation("/tokens/create_for_card", createForCardOperation)
            .done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            token : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "token": []
        };""");
  }

  @Test
  void shouldIgnoreBulkOperationFromSDKActions() throws IOException {
    var token = buildResource("token").withAttribute("id", true).done();
    var createForCardOperation =
        buildPostOperation("create_for_card")
            .forResource("token")
            .withResponse(resourceResponseParam("token", token))
            .asBulkOperationFromSDKGeneration()
            .withSortOrder(0)
            .done();
    var spec =
        buildSpec()
            .withResource(token)
            .withPostOperation("/tokens/create_for_card", createForCardOperation)
            .done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            token : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "token": []
        };""");
  }

  @Test
  void shouldSupportPostAction() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var createWithItemsOperation =
        buildPostOperation("createWithItems")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withPathParam("customer-id")
            .withSortOrder(1)
            .done();

    var spec =
        buildSpec()
            .withResource(subscription)
            .withPostOperation(
                "/customers/{customer-id}/subscription_for_items", createWithItemsOperation)
            .done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            subscription : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "subscription": [
            [
              "createWithItems",
              "POST",
              "/customers",
              "/subscription_for_items",
              true, null, false, {}, {}
            ]
          ]
        };""");
  }

  @Test
  void shouldSupportGetAction() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withSortOrder(3)
            .withPathParam("subscription-id")
            .done();
    var spec =
        buildSpec()
            .withResource(subscription)
            .withOperation("/subscriptions/{subscription-id}", operation)
            .done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            subscription : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "subscription": [
            [
              "retrieve",
              "GET",
              "/subscriptions",
              null,
              true, null, false, {}, {}
            ]
          ]
        };""");
  }

  @Test
  void eachActionShouldHaveUrlPrefix() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var operation =
        buildOperation("retrieve")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withSortOrder(3)
            .withPathParam("subscription-id")
            .done();
    var spec =
        buildSpec()
            .withResource(subscription)
            .withOperation("/subscriptions/{subscription-id}", operation)
            .done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            subscription : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "subscription": [
            [
              "retrieve",
              "GET",
              "/subscriptions",
              null,
              true, null, false, {}, {}
            ]
          ]
        };""");
  }

  @Test
  void ifActionHasUrlSuffixItWillShowUpTheUrlSuffix() throws IOException {
    var subscription =
        buildResource("subscription").withAttribute("id", true).withSortOrder(0).done();
    var createWithItemsOperation =
        buildPostOperation("createWithItems")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withPathParam("customer-id")
            .withSortOrder(1)
            .done();

    var subscriptionEntitlement =
        buildResource("subscription_entitlement").withAttribute("id", true).withSortOrder(0).done();
    var setSubscriptionEntitlementAvailabilityOperation =
        buildPostOperation("setSubscriptionEntitlementAvailability")
            .forResource("subscription_entitlement")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withPathParam("subscription-id")
            .withSortOrder(71)
            .done();

    var spec =
        buildSpec()
            .withResources(subscription, subscriptionEntitlement)
            .withPostOperation(
                "/customers/{customer-id}/subscription_for_items", createWithItemsOperation)
            .withPostOperation(
                "/subscriptions/{subscription-id}/subscription_entitlements/set_availability",
                setSubscriptionEntitlementAvailabilityOperation)
            .done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            subscription : EndpointTuple[]
            subscriptionEntitlement : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "subscription": [
            [
              "createWithItems",
              "POST",
              "/customers",
              "/subscription_for_items",
              true, null, false, {}, {}
            ]
          ],
          "subscriptionEntitlement": [
            [
              "setSubscriptionEntitlementAvailability",
              "POST",
              "/subscriptions",
              "/subscription_entitlements/set_availability",
              true, null, false, {}, {}
            ]
          ]
        };""");
  }

  @Test
  void ifActionHasPathParamItWillShowUpAsTrue() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var createWithItemsOperation =
        buildPostOperation("createWithItems")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withPathParam("customer-id")
            .withSortOrder(1)
            .done();

    var spec =
        buildSpec()
            .withResource(subscription)
            .withPostOperation(
                "/customers/{customer-id}/subscription_for_items", createWithItemsOperation)
            .done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            subscription : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "subscription": [
            [
              "createWithItems",
              "POST",
              "/customers",
              "/subscription_for_items",
              true, null, false, {}, {}
            ]
          ]
        };""");
  }

  @Test
  void ifActionHasNoPathParamItWillShowUpAsFalse() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var listSubscriptionOperation =
        buildListOperation("list")
            .forResource("subscription")
            .withResponse(resourceResponseParam("subscription", subscription))
            .withSortOrder(3)
            .done();
    var spec =
        buildSpec()
            .withResource(subscription)
            .withOperation("/subscriptions", listSubscriptionOperation)
            .done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            subscription : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "subscription": [
            [
              "list",
              "GET",
              "/subscriptions",
              null,
              false, null, false, {}, {}
            ]
          ]
        };""");
  }

  @Test
  void shouldIncludeIdempotencyOptionAtEndpoint() throws IOException {
    var subscription = buildResource("subscription").withAttribute("id", true).done();
    var listSubscriptionOperation =
        buildListOperation("list")
            .forResource("subscription")
            .asIdempotentEndpoint()
            .withResponse(resourceResponseParam("subscription", subscription))
            .withSortOrder(3)
            .done();
    var spec =
        buildSpec()
            .withResource(subscription)
            .withOperation("/subscriptions", listSubscriptionOperation)
            .done();

    List<FileOp> fileOps = node.generate("/node/lib/resources", spec);
    assertThat(fileOps).hasSize(4);
    assertWriteStringFileOp(
        fileOps.get(0),
        "/node/lib/resources",
        "api_endpoints.ts",
        """
        type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';
        type EndpointTuple = [
            action: string,
            method: Method,
            urlPrefix: string,
            urlSuffix: string | null,
            hasPathParameters: boolean,
            subDomain?: string | null,
            isJsonRequest?: boolean,
            jsonKeys?: any,
            options?:{
              isIdempotent?:boolean;
            }
        ];
        interface Endpoints {
            subscription : EndpointTuple[]
        }
        export const Endpoints:Endpoints = {
          "subscription": [
            [
              "list",
              "GET",
              "/subscriptions",
              null,
              false, null, false, {},{isIdempotent:true}
            ]
          ]
        };""");
  }
}
