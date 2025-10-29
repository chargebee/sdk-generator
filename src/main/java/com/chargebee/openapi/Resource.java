package com.chargebee.openapi;

import static com.chargebee.openapi.Extension.*;

import com.chargebee.ApiVersionHandler;
import com.chargebee.GenUtil;
import com.chargebee.QAModeHandler;
import com.chargebee.handlebar.Inflector;
import com.chargebee.openapi.parameter.Response;
import com.chargebee.sdk.DataType;
import com.google.common.base.CaseFormat;
import com.google.common.collect.Streams;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.*;
import java.util.stream.Collectors;

public class Resource {
  public final String name;
  public final String id;
  public final List<Action> actions;
  private final Schema<?> schema;

  public Resource(String id, String name, Schema<?> schema) {
    this.id = id;
    this.name = name;
    this.schema = schema;
    this.actions = List.of();
  }

  public Resource(String id, String name, Schema<?> schema, int sortOrder) {
    this.id = id;
    this.name = name;
    this.schema = schema;
    this.actions = List.of();
    this.schema.addExtension(SORT_ORDER, sortOrder);
  }

  public Resource(String name, Schema<?> schema, List<Action> actions) {
    this.name = name;
    this.schema = schema;
    id = resourceId(schema);
    this.actions = actions.stream().filter(Action::isNotHiddenFromSDK).toList();
  }

  public static String resourceId(Schema<?> schema) {
    if (schema.getExtensions() == null) {
      return null;
    }
    Object resourceId = schema.getExtensions().get(RESOURCE_ID);
    if (resourceId == null) {
      return null;
    }
    return (String) resourceId;
  }

  public static boolean isListOfSubResourceSchema(Schema<?> schema) {
    if (schema instanceof ArraySchema) {
      return isSubResourceSchema(schema.getItems());
    }
    return false;
  }

  public static boolean isSubResourceSchema(Schema<?> schema) {
    if (schema instanceof ArraySchema) {
      return isSubResourceSchema(schema.getItems());
    }
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_SUB_RESOURCE) != null
        && (boolean) schema.getExtensions().get(IS_SUB_RESOURCE);
  }

  public static boolean isCompositeArrayRequestBody(Schema<?> schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_COMPOSITE_ARRAY_REQUEST_BODY) != null
        && (boolean) schema.getExtensions().get(IS_COMPOSITE_ARRAY_REQUEST_BODY);
  }

  public static boolean isReferenceSchema(Schema<?> schema) {
    return schema != null && schema.get$ref() != null;
  }

  public static String referredResourceName(Schema<?> schema) {
    String[] referenceTokens = schema.get$ref().split("/");
    return referenceTokens[referenceTokens.length - 1];
  }

  public static String subResourceName(Schema<?> schema) {
    if (schema instanceof ArraySchema) {
      return subResourceName(schema.getItems());
    }
    return (String) schema.getExtensions().get(SUB_RESOURCE_NAME);
  }

  public static String subResourceName(Resource subResource) {
    if (subResource.schema instanceof ArraySchema) {
      return subResourceName(subResource.schema.getItems());
    }
    return (String) subResource.schema.getExtensions().get(SUB_RESOURCE_NAME);
  }

  public static String subResourceName(String key, Schema<?> schema) {
    return schema instanceof ArraySchema
        ? CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, key)
        : (String) schema.getExtensions().get(SUB_RESOURCE_NAME);
  }

  public static boolean isGlobalResourceReference(Schema<?> schema) {
    if (schema instanceof ArraySchema) {
      return isGlobalResourceReference(schema.getItems());
    }
    if (schema.getExtensions() == null) return false;
    Object isGlobalResourceReference = schema.getExtensions().get(IS_GLOBAL_RESOURCE_REFERENCE);
    return isGlobalResourceReference != null && (boolean) isGlobalResourceReference;
  }

  public static String parentResourceName(Schema<?> schema) {
    if (schema instanceof ArraySchema) {
      return parentResourceName(schema.getItems());
    }
    return (String) schema.getExtensions().get(SUB_RESOURCE_PARENT_NAME);
  }

  public Resource enableForQa() {
    QAModeHandler.getInstance().setValue(true);
    return this;
  }

  public void setApiVersion(ApiVersion apiVersion) {
    ApiVersionHandler.getInstance().setValue(apiVersion);
  }

  public Optional<ProductCatalogVersion> productCatalogVersion() {
    Map<?, ?> extensions = schema.getExtensions();
    if (extensions == null) {
      return Optional.empty();
    }
    var pcVer = extensions.get(PRODUCT_CATALOG_VERSION);
    if (pcVer == null) {
      return Optional.empty();
    }
    return (int) pcVer == 1
        ? Optional.of(ProductCatalogVersion.PC1)
        : Optional.of(ProductCatalogVersion.PC2);
  }

  public List<Attribute> attributes() {
    var properties = schema.getProperties();
    if (properties == null) {
      return List.of();
    }
    Set<String> requiredProperties =
        schema.getRequired() == null ? Set.of() : new HashSet<>(schema.getRequired());
    return properties.entrySet().stream()
        .map(
            entry ->
                new Attribute(
                    entry.getKey(), entry.getValue(), requiredProperties.contains(entry.getKey())))
        .toList();
  }

  public List<Enum> enums() {
    return attributes().stream()
        .filter(Attribute::isNotHiddenAttribute)
        .filter(attr -> !attr.isGlobalEnumAttribute() && attr.isEnumAttribute())
        .map(attr -> new Enum(attr.name, attr.getSchema()))
        .toList();
  }

  public List<Enum> globalEnums() {
    return attributes().stream()
        .filter(Attribute::isNotHiddenAttribute)
        .filter(attr -> attr.isGlobalEnumAttribute() && attr.isEnumAttribute())
        .map(attr -> new Enum(attr.name, attr.getSchema()))
        .toList();
  }

  public List<Resource> subResources() {
    if (schema.getProperties() == null) {
      return List.of();
    }
    return schema.getProperties().entrySet().stream()
        .filter(e -> isSubResourceSchema(e.getValue()) && !isGlobalResourceReference(e.getValue()))
        .filter(e -> isNotHiddenFromSDKGeneration(e.getValue()))
        .collect(
            Collectors.toMap(
                e -> subResourceName(e.getKey(), e.getValue()),
                e ->
                    new Resource(
                        e.getKey(),
                        subResourceName(e.getKey(), e.getValue()),
                        e.getValue() instanceof ArraySchema
                            ? e.getValue().getItems()
                            : e.getValue(),
                        sortOrder(e.getValue())),
                (existing, duplicate) -> duplicate,
                LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }

  public List<Resource> dependentResources() {
    if (schema.getProperties() == null) {
      return List.of();
    }
    return schema.getProperties().entrySet().stream()
        .filter(e -> isSubResourceSchema(e.getValue()) && isGlobalResourceReference(e.getValue()))
        .map(
            e ->
                new Resource(
                    e.getKey(),
                    subResourceName(e.getValue()),
                    e.getValue() instanceof ArraySchema ? e.getValue().getItems() : e.getValue()))
        .toList();
  }

  public List<Resource> singularDependentResources() {
    if (schema.getProperties() == null) {
      return List.of();
    }
    return schema.getProperties().entrySet().stream()
        .filter(
            e ->
                isSubResourceSchema(e.getValue())
                    && isGlobalResourceReference(e.getValue())
                    && !(e.getValue() instanceof ArraySchema))
        .map(
            e ->
                new Resource(
                    e.getKey(),
                    subResourceName(e.getValue()),
                    e.getValue() instanceof ArraySchema ? e.getValue().getItems() : e.getValue()))
        .toList();
  }

  public List<Resource> listDependentResources() {
    if (schema.getProperties() == null) {
      return List.of();
    }
    return schema.getProperties().entrySet().stream()
        .filter(
            e ->
                isSubResourceSchema(e.getValue())
                    && isGlobalResourceReference(e.getValue())
                    && e.getValue() instanceof ArraySchema)
        .map(
            e ->
                new Resource(
                    e.getKey(),
                    subResourceName(e.getValue()),
                    e.getValue() instanceof ArraySchema ? e.getValue().getItems() : e.getValue()))
        .toList();
  }

  public Map<String, Object> templateParams() {
    return Map.of(
        "hasActions", !actions.isEmpty(),
        "name", name,
        "id", id);
  }

  public boolean hasBigDecimalAttributes(DataType lang) {
    return !subResources().stream()
            .filter(
                r ->
                    r.attributes().stream()
                        .filter(Attribute::isNotHiddenAttribute)
                        .anyMatch(a -> a.isDataTypeBigDecimal(lang)))
            .toList()
            .isEmpty()
        || attributes().stream()
            .filter(Attribute::isNotHiddenAttribute)
            .anyMatch(a -> a.isDataTypeBigDecimal(lang));
  }

  public Map<String, Object> templateParams(DataType lang) {
    var attributes =
        attributes().stream()
            .filter(Attribute::isNotHiddenAttribute)
            .sorted(Comparator.comparing(Attribute::sortOrder))
            .map(attr -> attr.templateParams(lang))
            .filter(m -> !m.isEmpty())
            .toList();
    var enums =
        attributes().stream()
            .filter(Attribute::isNotHiddenAttribute)
            .filter(attr -> !attr.isGlobalEnumAttribute() && attr.isEnumAttribute())
            .map(attr -> attr.templateParams(lang))
            .filter(m -> !m.isEmpty())
            .toList();
    var actionsTemplateParams =
        actions.stream()
            .filter(Action::isNotHiddenFromSDK)
            .filter(Action::isNotBulkOperation)
            .filter(Action::isNotInternalOperation)
            .sorted(Comparator.comparing(Action::sortOrder))
            .map(a -> a.templateParams(lang))
            .toList();
    var anyActionHasPathParam =
        actions.stream()
            .filter(Action::isNotHiddenFromSDK)
            .filter(Action::isNotBulkOperation)
            .filter(Action::isNotInternalOperation)
            .anyMatch(a -> !a.pathParameters().isEmpty());
    var subResourcesTemplateParams =
        subResources().stream()
            .sorted(Comparator.comparing(Resource::sortOrder))
            .map(r -> r.templateParams(lang))
            .toList();

    var templateParams =
        Map.ofEntries(
            new AbstractMap.SimpleEntry<String, Object>("id", id),
            new AbstractMap.SimpleEntry<String, Object>("name", name),
            new AbstractMap.SimpleEntry<String, Object>("pathName", pathName()),
            new AbstractMap.SimpleEntry<String, Object>("attributes", attributes),
            new AbstractMap.SimpleEntry<String, Object>("enums", enums),
            new AbstractMap.SimpleEntry<String, Object>(
                "hasEmptyActionsAndSubResources",
                actionsTemplateParams.isEmpty() && subResourcesTemplateParams.isEmpty()),
            new AbstractMap.SimpleEntry<String, Object>("actions", actionsTemplateParams),
            new AbstractMap.SimpleEntry<String, Object>(
                "anyActionHasPathParam", anyActionHasPathParam),
            new AbstractMap.SimpleEntry<String, Object>(
                "isAdditionalPropertiesSupported", isAdditionalPropertiesSupported()),
            new AbstractMap.SimpleEntry<String, Object>("subResources", subResourcesTemplateParams),
            new AbstractMap.SimpleEntry<String, Object>("isExport", isExport()),
            new AbstractMap.SimpleEntry<String, Object>("isTimeMachine", isTimeMachine()),
            new AbstractMap.SimpleEntry<String, Object>("isEvent", isEvent()),
            new AbstractMap.SimpleEntry<String, Object>("isHostedPage", isHostedPage()),
            new AbstractMap.SimpleEntry<String, Object>("isSession", isSession()),
            new AbstractMap.SimpleEntry<String, Object>(
                "hasDependentAttributes",
                !attributes().stream().filter(Attribute::isDependentAttribute).toList().isEmpty()),
            new AbstractMap.SimpleEntry<String, Object>(
                "dependentResources",
                dependentResources().stream().map(r -> r.templateParams(lang)).toList()),
            new AbstractMap.SimpleEntry<String, Object>(
                "singularDependentResources",
                singularDependentResources().stream().map(r -> r.templateParams(lang)).toList()),
            new AbstractMap.SimpleEntry<String, Object>(
                "listDependentResources",
                listDependentResources().stream().map(r -> r.templateParams(lang)).toList()),
            new AbstractMap.SimpleEntry<String, Object>(
                "hasBigDecimalTypeAttribute", hasBigDecimalAttributes(lang)),
            new AbstractMap.SimpleEntry<String, Object>("hasListOperations", hasListOperations()),
            new AbstractMap.SimpleEntry<String, Object>(
                "anyActionHasBodyOrQueryParams", anyActionHasBodyOrQueryParams()));

    return Streams.concat(
            templateParams.entrySet().stream(),
            lang.additionalTemplateParams(this).entrySet().stream())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public List<Action> getSortedAction() {
    return actions.stream()
        .filter(Action::isNotHiddenFromSDK)
        .filter(Action::isNotBulkOperation)
        .filter(Action::isNotInternalOperation)
        .sorted(Comparator.comparing(Action::sortOrder))
        .toList();
  }

  public List<Attribute> getSortedResourceAttributes() {
    return attributes().stream()
        .filter(Attribute::isNotHiddenAttribute)
        .sorted(Comparator.comparing(Attribute::sortOrder))
        .toList();
  }

  public boolean isNotHiddenFromSDKGeneration() {
    return isNotHiddenFromSDKGeneration(schema);
  }

  public boolean isNotHiddenFromSDKGeneration(Schema schema) {
    return schema.getExtensions() == null
        || schema.getExtensions().get(Extension.HIDDEN_FROM_CLIENT_SDK) == null
        || !((boolean) schema.getExtensions().get(Extension.HIDDEN_FROM_CLIENT_SDK))
        || QAModeHandler.getInstance().getValue();
  }

  public boolean isNotThirdPartyResource() {
    return schema.getExtensions() == null
        || schema.getExtensions().get(Extension.IS_THIRD_PARTY_RESOURCE) == null
        || !((boolean) schema.getExtensions().get(Extension.IS_THIRD_PARTY_RESOURCE))
        || QAModeHandler.getInstance().getValue();
  }

  public boolean isNotDependentResource() {
    return schema.getExtensions() == null
        || schema.getExtensions().get(Extension.IS_DEPENDENT_RESOURCE) == null
        || !((boolean) schema.getExtensions().get(Extension.IS_DEPENDENT_RESOURCE));
  }

  public int sortOrder() {
    return schema.getExtensions().get(SORT_ORDER) != null
        ? (int) schema.getExtensions().get(SORT_ORDER)
        : -1;
  }

  public int sortOrder(Schema schema) {
    return schema.getExtensions() != null && schema.getExtensions().get(SORT_ORDER) != null
        ? (int) schema.getExtensions().get(SORT_ORDER)
        : -1;
  }

  public String pathName() {
    return schema.getExtensions().get(RESOURCE_PATH_NAME) != null
        ? (String) schema.getExtensions().get(RESOURCE_PATH_NAME)
        : Inflector.pluralize(id);
  }

  public boolean isAdditionalPropertiesSupported() {
    return schema.getAdditionalProperties() != null && GenUtil.hasAdditionalProperties(schema);
  }

  // should handle HiddenFromSDK in calling function
  public List<Response> responseList() {
    return actions.stream()
        .sorted(Comparator.comparing(Action::sortOrder))
        .filter(Action::isNotHiddenFromSDK)
        .flatMap(action -> action.response().responseParameters().stream())
        .toList();
  }

  public boolean isExport() {
    return name.equals("Export");
  }

  public boolean isTimeMachine() {
    return name.equals("TimeMachine");
  }

  public boolean isEvent() {
    return name.equals("Event");
  }

  public boolean isHostedPage() {
    return name.equals("HostedPage");
  }

  public boolean isSession() {
    return name.equals("Session");
  }

  public boolean isCustomFieldSupported() {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_CUSTOM_FIELDS_SUPPORTED) != null
        && (boolean) schema.getExtensions().get(IS_CUSTOM_FIELDS_SUPPORTED);
  }

  public boolean hasListOperations() {
    return !actions.stream().filter(Action::isListResourceAction).toList().isEmpty();
  }

  public boolean hasAnyAction() {
    return !actions.isEmpty();
  }

  public boolean hasContentTypeJsonAction() {
    return !actions.stream().filter(Action::isContentTypeJsonAction).toList().isEmpty();
  }

  public boolean anyActionHasBodyOrQueryParams() {
    for (Action act : actions) {
      if (!act.requestBodyParameters().isEmpty() || !act.queryParameters().isEmpty()) {
        return true;
      }
    }
    return false;
  }
}
