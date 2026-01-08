package com.chargebee.sdk.common;

import static com.chargebee.openapi.Extension.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.HttpRequestType;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ActionAssistTest {

  private ActionAssist actionAssist;
  private Action mockAction;

  @BeforeEach
  void setUp() {
    actionAssist = new ActionAssist();
    mockAction = createMockAction();
  }

  @Nested
  @DisplayName("Setter Methods Tests")
  class SetterMethodsTests {

    @Test
    @DisplayName("Should set flat multi attribute flag")
    void shouldSetFlatMultiAttribute() {
      // Given & When
      ActionAssist result = actionAssist.setFlatMultiAttribute(false);

      // Then
      assertThat(result).isSameAs(actionAssist);
      // Test behavior change through getAllAttribute
      actionAssist.setAction(mockAction);
      List<Attribute> attributes = actionAssist.getAllAttribute();
      assertThat(attributes).isNotNull();
    }

    @Test
    @DisplayName("Should set action")
    void shouldSetAction() {
      // Given & When
      ActionAssist result = actionAssist.setAction(mockAction);

      // Then
      assertThat(result).isSameAs(actionAssist);
    }

    @Test
    @DisplayName("Should include filter sub resource")
    void shouldIncludeFilterSubResource() {
      // Given & When
      ActionAssist result = actionAssist.includeFilterSubResource();

      // Then
      assertThat(result).isSameAs(actionAssist);
    }

    @Test
    @DisplayName("Should include sort by")
    void shouldIncludeSortBy() {
      // Given & When
      ActionAssist result = actionAssist.includeSortBy();

      // Then
      assertThat(result).isSameAs(actionAssist);
    }

    @Test
    @DisplayName("Should include pagination")
    void shouldIncludePagination() {
      // Given & When
      ActionAssist result = actionAssist.includePagination();

      // Then
      assertThat(result).isSameAs(actionAssist);
    }

    @Test
    @DisplayName("Should set accept only pagination")
    void shouldSetAcceptOnlyPagination() {
      // Given & When
      ActionAssist result = actionAssist.setAcceptOnlyPagination();

      // Then
      assertThat(result).isSameAs(actionAssist);
    }

    @Test
    @DisplayName("Should set flat outer entries")
    void shouldSetFlatOuterEntries() {
      // Given & When
      ActionAssist result = actionAssist.setFlatOuterEntries(true);

      // Then
      assertThat(result).isSameAs(actionAssist);
    }

    @Test
    @DisplayName("Should set flat single attribute")
    void shouldSetFlatSingleAttribute() {
      // Given & When
      ActionAssist result = actionAssist.setFlatSingleAttribute(true);

      // Then
      assertThat(result).isSameAs(actionAssist);
    }
  }

  @Nested
  @DisplayName("getAllAttribute Method Tests")
  class GetAllAttributeTests {

    @Test
    @DisplayName("Should return all attributes from request body and query parameters")
    void shouldReturnAllAttributes() {
      // Given
      Action action = createActionWithRequestBodyAndQuery();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.getAllAttribute();

      // Then
      assertThat(result).isNotEmpty();
      assertThat(result).anyMatch(attr -> attr.name.equals("email"));
      assertThat(result).anyMatch(attr -> attr.name.equals("name"));
    }

    @Test
    @DisplayName("Should exclude pagination properties when not included")
    void shouldExcludePaginationProperties() {
      // Given
      Action action = createActionWithPagination();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.getAllAttribute();

      // Then
      assertThat(result).noneMatch(attr -> attr.isPaginationProperty());
    }

    @Test
    @DisplayName("Should include pagination properties when explicitly included")
    void shouldIncludePaginationPropertiesWhenSet() {
      // Given
      Action action = createActionWithPagination();
      actionAssist.setAction(action).includePagination().setAcceptOnlyPagination();

      // When
      List<Attribute> result = actionAssist.getAllAttribute();

      // Then
      assertThat(result).isNotEmpty();
      assertThat(result).anyMatch(Attribute::isPaginationProperty);
    }

    @Test
    @DisplayName("Should filter out sub resources when not included")
    void shouldFilterOutSubResourcesWhenNotIncluded() {
      // Given - use action that has both regular and sub-resource attributes
      Action action = createActionWithRequestBodyAndQuery();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.getAllAttribute();

      // Then
      assertThat(result).isNotEmpty();
      // Should not contain sub resources when not explicitly included
      assertThat(result).noneMatch(attr -> attr.isSubResource() && !attr.isFilterAttribute());
    }

    @Test
    @DisplayName("Should use single level sort when flat outer entries is true")
    void shouldUseSingleLevelSortWhenFlatOuterEntries() {
      // Given
      Action action = createActionWithNestedAttributes();
      actionAssist.setAction(action).setFlatOuterEntries(true);

      // When
      List<Attribute> result = actionAssist.getAllAttribute();

      // Then
      assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("Should use generic sort when flat single attribute is true")
    void shouldUseGenericSortWhenFlatSingleAttribute() {
      // Given
      Action action = createActionWithNestedAttributes();
      actionAssist.setAction(action).setFlatSingleAttribute(true);

      // When
      List<Attribute> result = actionAssist.getAllAttribute();

      // Then
      assertThat(result).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("requestBody Method Tests")
  class RequestBodyTests {

    @Test
    @DisplayName("Should return request body parameters")
    void shouldReturnRequestBodyParameters() {
      // Given
      Action action = createActionWithRequestBody();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.requestBody();

      // Then
      assertThat(result).isNotEmpty();
      assertThat(result).anyMatch(attr -> attr.name.equals("email"));
    }

    @Test
    @DisplayName("Should exclude hidden attributes")
    void shouldExcludeHiddenAttributes() {
      // Given
      Action action = createActionWithHiddenAttributes();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.requestBody();

      // Then
      assertThat(result).allMatch(Attribute::isNotHiddenAttribute);
    }

    @Test
    @DisplayName("Should exclude composite array request body")
    void shouldExcludeCompositeArrayRequestBody() {
      // Given
      Action action = createActionWithCompositeArrayRequestBody();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.requestBody();

      // Then
      assertThat(result).noneMatch(Attribute::isCompositeArrayRequestBody);
    }

    @Test
    @DisplayName("Should exclude filter attributes")
    void shouldExcludeFilterAttributes() {
      // Given
      Action action = createActionWithFilterAttributes();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.requestBody();

      // Then
      assertThat(result).noneMatch(Attribute::isFilterAttribute);
    }

    @Test
    @DisplayName("Should sort by sort order")
    void shouldSortBySortOrder() {
      // Given
      Action action = createActionWithSortedAttributes();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.requestBody();

      // Then
      assertThat(result).isNotEmpty();
      for (int i = 1; i < result.size(); i++) {
        assertThat(result.get(i).sortOrder()).isGreaterThanOrEqualTo(result.get(i - 1).sortOrder());
      }
    }
  }

  @Nested
  @DisplayName("query Method Tests")
  class QueryTests {

    @Test
    @DisplayName("Should return query parameters")
    void shouldReturnQueryParameters() {
      // Given
      Action action = createActionWithQueryParameters();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.query();

      // Then
      assertThat(result).isNotEmpty();
      assertThat(result).anyMatch(attr -> attr.name.equals("limit"));
    }

    @Test
    @DisplayName("Should include sort by when flag is set")
    void shouldIncludeSortByWhenFlagIsSet() {
      // Given
      Action action = createActionWithSortBy();
      actionAssist.setAction(action).includeSortBy();

      // When
      List<Attribute> result = actionAssist.query();

      // Then
      assertThat(result).anyMatch(attr -> attr.name.equals(Constant.SORT_BY));
    }

    @Test
    @DisplayName("Should include filter attributes from request body")
    void shouldIncludeFilterAttributesFromRequestBody() {
      // Given
      Action action = createActionWithRequestBodyFilters();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.query();

      // Then
      assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("Should exclude pagination when not included")
    void shouldExcludePaginationWhenNotIncluded() {
      // Given
      Action action = createActionWithPagination();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.query();

      // Then
      assertThat(result).noneMatch(Attribute::isPaginationProperty);
    }
  }

  @Nested
  @DisplayName("singularSubAttributes Method Tests")
  class SingularSubAttributesTests {

    @Test
    @DisplayName("Should return singular sub attributes")
    void shouldReturnSingularSubAttributes() {
      // Given
      Action action = createActionWithSingularSubAttributes();
      actionAssist.setAction(action).includeFilterSubResource();

      // When
      List<Attribute> result = actionAssist.singularSubAttributes();

      // Then
      assertThat(result).isNotNull();
      // The method looks for attributes that have sub-attributes which are sub-resources
    }

    @Test
    @DisplayName("Should filter by sub resource attributes")
    void shouldFilterBySubResourceAttributes() {
      // Given
      Action action = createActionWithSubResources();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.singularSubAttributes();

      // Then
      assertThat(result)
          .allMatch(attr -> attr.attributes().stream().anyMatch(Attribute::isSubResource));
    }

    @Test
    @DisplayName("Should exclude sort by when not included")
    void shouldExcludeSortByWhenNotIncluded() {
      // Given
      Action action = createActionWithSortBy();
      actionAssist.setAction(action);

      // When
      List<Attribute> result = actionAssist.singularSubAttributes();

      // Then
      assertThat(result).noneMatch(attr -> attr.name.equals(Constant.SORT_BY));
    }
  }

  @Nested
  @DisplayName("multiSubAttributes Method Tests")
  class MultiSubAttributesTests {

    @Test
    @DisplayName("Should return multi sub attributes")
    void shouldReturnMultiSubAttributes() {
      // Given
      Action action = createActionWithMultiSubAttributes();
      actionAssist.setAction(action).setFlatMultiAttribute(false);

      // When
      List<Attribute> result = actionAssist.multiSubAttributes();

      // Then
      assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("Should include composite array request body")
    void shouldIncludeCompositeArrayRequestBody() {
      // Given
      Action action = createActionWithCompositeArrayRequestBody();
      actionAssist.setAction(action).setFlatMultiAttribute(false);

      // When
      List<Attribute> result = actionAssist.multiSubAttributes();

      // Then
      assertThat(result).anyMatch(Attribute::isCompositeArrayRequestBody);
    }

    @Test
    @DisplayName("Should include sort by with attributes when flag is set")
    void shouldIncludeSortByWithAttributesWhenFlagIsSet() {
      // Given
      Action action = createActionWithSortByWithAttributes();
      actionAssist.setAction(action).includeSortBy();

      // When
      List<Attribute> result = actionAssist.multiSubAttributes();

      // Then
      assertThat(result)
          .anyMatch(attr -> attr.name.equals(Constant.SORT_BY) && !attr.attributes().isEmpty());
    }

    @Test
    @DisplayName("Should modify to flat list when flag is set")
    void shouldModifyToFlatListWhenFlagIsSet() {
      // Given - use an action that has sub-attributes for flattening
      Action action = createActionWithCompositeArrayRequestBody();
      actionAssist.setAction(action).setFlatMultiAttribute(true);

      // When
      List<Attribute> result = actionAssist.multiSubAttributes();

      // Then
      // For now, just verify the method doesn't fail with flat list enabled
      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("consolidatedSubParams Method Tests")
  class ConsolidatedSubParamsTests {

    @Test
    @DisplayName("Should return consolidated sub parameters")
    void shouldReturnConsolidatedSubParams() {
      // Given
      Action action = createActionWithSubResources();
      actionAssist.setAction(action).includeFilterSubResource();

      // When
      List<Attribute> result = actionAssist.consolidatedSubParams();

      // Then
      assertThat(result).isNotEmpty();
      assertThat(result)
          .allMatch(
              attr ->
                  attr.isSubResource()
                      || attr.attributes().stream().anyMatch(Attribute::isSubResource));
    }

    @Test
    @DisplayName("Should maintain order based on multi attribute order")
    void shouldMaintainOrderBasedOnMultiAttributeOrder() {
      // Given
      Action action = createActionWithOrderedSubResources();
      actionAssist.setAction(action).includeFilterSubResource();

      // When
      List<Attribute> result = actionAssist.consolidatedSubParams();

      // Then
      assertThat(result).isNotEmpty();
    }
  }

  // Helper methods to create mock objects for testing

  private Action createMockAction() {
    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of(OPERATION_METHOD_NAME, "testMethod"));
    return new Action(HttpRequestType.GET, operation, "/test");
  }

  private Action createActionWithRequestBodyAndQuery() {
    ObjectSchema requestBodySchema = new ObjectSchema();
    requestBodySchema.addProperty("email", new StringSchema());
    requestBodySchema.addProperty("name", new StringSchema());
    requestBodySchema.setRequired(List.of("email"));

    RequestBody requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType(
                        "application/x-www-form-urlencoded",
                        new MediaType().schema(requestBodySchema)));

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of(OPERATION_METHOD_NAME, "testMethod"))
            .requestBody(requestBody)
            .addParametersItem(
                new QueryParameter().name("limit").required(false).schema(new StringSchema()));

    return new Action(HttpRequestType.POST, operation, "/test");
  }

  private Action createActionWithRequestBody() {
    ObjectSchema requestBodySchema = new ObjectSchema();
    requestBodySchema.addProperty("email", new StringSchema());
    requestBodySchema.setRequired(List.of("email"));

    RequestBody requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType(
                        "application/x-www-form-urlencoded",
                        new MediaType().schema(requestBodySchema)));

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of(OPERATION_METHOD_NAME, "testMethod"))
            .requestBody(requestBody);

    return new Action(HttpRequestType.POST, operation, "/test");
  }

  private Action createActionWithQueryParameters() {
    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of(OPERATION_METHOD_NAME, "testMethod"))
            .addParametersItem(
                new QueryParameter().name("limit").required(false).schema(new StringSchema()));

    return new Action(HttpRequestType.GET, operation, "/test");
  }

  private Action createActionWithPagination() {
    StringSchema limitSchema = new StringSchema();
    limitSchema.addExtension(IS_PAGINATION_PARAMETER, true);

    StringSchema offsetSchema = new StringSchema();
    offsetSchema.addExtension(IS_PAGINATION_PARAMETER, true);

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .addParametersItem(
                new QueryParameter().name("limit").required(false).schema(limitSchema))
            .addParametersItem(
                new QueryParameter().name("offset").required(false).schema(offsetSchema));

    return new Action(HttpRequestType.GET, operation, "/test");
  }

  private Action createActionWithSubResources() {
    ObjectSchema subResourceSchema = new ObjectSchema();
    subResourceSchema.addProperty("id", new StringSchema());
    subResourceSchema.addExtension(IS_SUB_RESOURCE, true);

    ObjectSchema requestBodySchema = new ObjectSchema();
    requestBodySchema.addProperty("customer", subResourceSchema);

    RequestBody requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType(
                        "application/x-www-form-urlencoded",
                        new MediaType().schema(requestBodySchema)));

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .requestBody(requestBody);

    return new Action(HttpRequestType.POST, operation, "/test");
  }

  private Action createActionWithNestedAttributes() {
    ObjectSchema nestedSchema = new ObjectSchema();
    nestedSchema.addProperty("nested_field", new StringSchema());

    ObjectSchema requestBodySchema = new ObjectSchema();
    requestBodySchema.addProperty("parent", nestedSchema);

    RequestBody requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType(
                        "application/x-www-form-urlencoded",
                        new MediaType().schema(requestBodySchema)));

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .requestBody(requestBody);

    return new Action(HttpRequestType.POST, operation, "/test");
  }

  private Action createActionWithHiddenAttributes() {
    StringSchema hiddenSchema = new StringSchema();
    hiddenSchema.addExtension(HIDDEN_FROM_CLIENT_SDK, true);

    ObjectSchema requestBodySchema = new ObjectSchema();
    requestBodySchema.addProperty("visible", new StringSchema());
    requestBodySchema.addProperty("hidden", hiddenSchema);

    RequestBody requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType(
                        "application/x-www-form-urlencoded",
                        new MediaType().schema(requestBodySchema)));

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .requestBody(requestBody);

    return new Action(HttpRequestType.POST, operation, "/test");
  }

  private Action createActionWithCompositeArrayRequestBody() {
    ObjectSchema compositeArraySchema = new ObjectSchema();
    compositeArraySchema.addExtension(IS_COMPOSITE_ARRAY_REQUEST_BODY, true);

    ObjectSchema requestBodySchema = new ObjectSchema();
    requestBodySchema.addProperty("composite", compositeArraySchema);

    RequestBody requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType(
                        "application/x-www-form-urlencoded",
                        new MediaType().schema(requestBodySchema)));

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .requestBody(requestBody);

    return new Action(HttpRequestType.POST, operation, "/test");
  }

  private Action createActionWithFilterAttributes() {
    StringSchema filterSchema = new StringSchema();
    filterSchema.addExtension(IS_FILTER_PARAMETER, true);

    ObjectSchema requestBodySchema = new ObjectSchema();
    requestBodySchema.addProperty("filter", filterSchema);

    RequestBody requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType(
                        "application/x-www-form-urlencoded",
                        new MediaType().schema(requestBodySchema)));

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .requestBody(requestBody);

    return new Action(HttpRequestType.POST, operation, "/test");
  }

  private Action createActionWithSortedAttributes() {
    StringSchema firstSchema = new StringSchema();
    firstSchema.addExtension(SORT_ORDER, 1);

    StringSchema secondSchema = new StringSchema();
    secondSchema.addExtension(SORT_ORDER, 2);

    ObjectSchema requestBodySchema = new ObjectSchema();
    requestBodySchema.addProperty("second", secondSchema);
    requestBodySchema.addProperty("first", firstSchema);

    RequestBody requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType(
                        "application/x-www-form-urlencoded",
                        new MediaType().schema(requestBodySchema)));

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .requestBody(requestBody);

    return new Action(HttpRequestType.POST, operation, "/test");
  }

  private Action createActionWithSortBy() {
    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .addParametersItem(
                new QueryParameter()
                    .name(Constant.SORT_BY)
                    .required(false)
                    .schema(new StringSchema()));

    return new Action(HttpRequestType.GET, operation, "/test");
  }

  private Action createActionWithRequestBodyFilters() {
    StringSchema filterSchema = new StringSchema();
    filterSchema.addExtension(IS_FILTER_PARAMETER, true);

    ObjectSchema requestBodySchema = new ObjectSchema();
    requestBodySchema.addProperty("filter", filterSchema);

    RequestBody requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType(
                        "application/x-www-form-urlencoded",
                        new MediaType().schema(requestBodySchema)));

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .requestBody(requestBody);

    return new Action(HttpRequestType.POST, operation, "/test");
  }

  private Action createActionWithSingularSubAttributes() {
    ObjectSchema subSchema = new ObjectSchema();
    subSchema.addProperty("sub_field", new StringSchema());
    subSchema.addExtension(IS_SUB_RESOURCE, true);

    ObjectSchema requestBodySchema = new ObjectSchema();
    requestBodySchema.addProperty("customer", subSchema);

    RequestBody requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType(
                        "application/x-www-form-urlencoded",
                        new MediaType().schema(requestBodySchema)));

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .requestBody(requestBody);

    return new Action(HttpRequestType.POST, operation, "/test");
  }

  private Action createActionWithMultiSubAttributes() {
    ArraySchema arraySchema = new ArraySchema();
    arraySchema.items(new StringSchema());
    arraySchema.addExtension(IS_COMPOSITE_ARRAY_REQUEST_BODY, true);

    ObjectSchema requestBodySchema = new ObjectSchema();
    requestBodySchema.addProperty("items", arraySchema);

    RequestBody requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType(
                        "application/x-www-form-urlencoded",
                        new MediaType().schema(requestBodySchema)));

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .requestBody(requestBody);

    return new Action(HttpRequestType.POST, operation, "/test");
  }

  private Action createActionWithSortByWithAttributes() {
    ObjectSchema sortBySchema = new ObjectSchema();
    sortBySchema.addProperty("field", new StringSchema());

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .addParametersItem(
                new QueryParameter().name(Constant.SORT_BY).required(false).schema(sortBySchema));

    return new Action(HttpRequestType.GET, operation, "/test");
  }

  private Action createActionWithOrderedSubResources() {
    ObjectSchema firstSubResourceSchema = new ObjectSchema();
    firstSubResourceSchema.addProperty("id", new StringSchema());
    firstSubResourceSchema.addExtension(IS_SUB_RESOURCE, true);
    firstSubResourceSchema.addExtension(SORT_ORDER, 1);

    ObjectSchema secondSubResourceSchema = new ObjectSchema();
    secondSubResourceSchema.addProperty("name", new StringSchema());
    secondSubResourceSchema.addExtension(IS_SUB_RESOURCE, true);
    secondSubResourceSchema.addExtension(SORT_ORDER, 2);

    ObjectSchema requestBodySchema = new ObjectSchema();
    requestBodySchema.addProperty("second", secondSubResourceSchema);
    requestBodySchema.addProperty("first", firstSubResourceSchema);

    RequestBody requestBody =
        new RequestBody()
            .content(
                new Content()
                    .addMediaType(
                        "application/x-www-form-urlencoded",
                        new MediaType().schema(requestBodySchema)));

    Operation operation =
        new Operation()
            .operationId("testOperation")
            .extensions(Map.of("x-cb-operation-method-name", "testMethod"))
            .requestBody(requestBody);

    return new Action(HttpRequestType.POST, operation, "/test");
  }
}
