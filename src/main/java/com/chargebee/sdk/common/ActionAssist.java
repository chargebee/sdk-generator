package com.chargebee.sdk.common;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.*;
import java.util.function.Predicate;
import lombok.SneakyThrows;

public class ActionAssist {
  private boolean isFlatMultiAttribute = true;
  private boolean isFlatSingleAttribute = false;
  private boolean includeFilterSubResource = false;
  private boolean includePagination = false;

  private boolean canAcceptOnlyPagination = false;
  private Action action;
  private boolean includeSortBy = false;
  private boolean isFlatOuterEntries = false;

  public ActionAssist setFlatMultiAttribute(boolean flatMultiAttribute) {
    isFlatMultiAttribute = flatMultiAttribute;
    return this;
  }

  public ActionAssist setAction(Action action) {
    this.action = action;
    return this;
  }

  public ActionAssist includeFilterSubResource() {
    this.includeFilterSubResource = true;
    return this;
  }

  public ActionAssist includeSortBy() {
    this.includeSortBy = true;
    return this;
  }

  public ActionAssist includePagination() {
    this.includePagination = true;
    return this;
  }

  public ActionAssist setAcceptOnlyPagination() {
    this.canAcceptOnlyPagination = true;
    return this;
  }

  public List<Attribute> getAllAttribute() {
    List<Attribute> attributes = new ArrayList<>();
    attributes.addAll(
        action.requestBodyParameters().stream()
            .map(p -> new Attribute(p.getName(), p.schema, p.isRequired))
            .filter(Attribute::isNotHiddenAttribute)
            .toList());
    attributes.addAll(
        action.queryParameters().stream()
            .map(p -> new Attribute(p.getName(), p.schema, p.isRequired))
            .filter(Attribute::isNotHiddenAttribute)
            .toList());
    if (!includeFilterSubResource) {
      attributes =
          attributes.stream().filter(a -> !a.isSubResource() || a.isFilterAttribute()).toList();
    }
    if (!includePagination || (!canAcceptOnlyPagination && attributes.size() == 2)) {
      attributes =
          attributes.stream().filter(Predicate.not(Attribute::isPaginationProperty)).toList();
    }
    if (isFlatOuterEntries) {
      return singleLevelSort(attributes);
    }
    if (isFlatSingleAttribute) {
      return genericSort(attributes);
    }
    return attributes.stream().sorted(Comparator.comparing(Attribute::sortOrder)).toList();
  }

  @SneakyThrows
  private List<Attribute> genericSort(List<Attribute> attributes) {
    List<Attribute> consolidatedAttributes = new ArrayList<>();
    List<Integer> sortOrder =
        new ArrayList<>(attributes.stream().map(Attribute::sortOrder).filter(i -> i > -1).toList());

    for (Integer order : sortOrder.stream().sorted().toList()) {
      for (Attribute attribute : attributes) {
        if (!attribute.attributes().isEmpty()
            && !attribute.isFilterAttribute()
            && !attribute.name.equals(Constant.SORT_BY)) continue;
        if (attribute.sortOrder() == order) {
          consolidatedAttributes.add(attribute);
        }
      }
    }

    attributes.stream()
        .filter(attribute -> attribute.sortOrder() == -1)
        .forEach(a -> sortOrder.addAll(a.attributes().stream().map(Attribute::sortOrder).toList()));
    for (Integer order : sortOrder.stream().sorted().toList()) {
      for (Attribute attribute : attributes) {
        for (Attribute subAttribute : attribute.attributes()) {
          if (subAttribute.sortOrder() == order) {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
            Schema updatedSchema =
                objectMapper.readValue(
                    objectMapper.writeValueAsString(attribute.schema), Schema.class);
            Map<String, Schema> updatedPropertiesMap = new HashMap<>();
            updatedPropertiesMap.put(
                subAttribute.name, attribute.schema.getProperties().get(subAttribute.name));
            updatedSchema.setProperties(updatedPropertiesMap);
            consolidatedAttributes.add(
                new Attribute(attribute.name, updatedSchema, attribute.isRequired));
          }
        }
      }
    }
    return consolidatedAttributes;
  }

  public List<Attribute> requestBody() {
    return action.requestBodyParameters().stream()
        .map(p -> new Attribute(p.getName(), p.schema, p.isRequired))
        .filter(Attribute::isNotHiddenAttribute)
        .filter(Predicate.not(Attribute::isCompositeArrayRequestBody))
        .filter(Predicate.not(Attribute::isFilterAttribute))
        .sorted(Comparator.comparing(Attribute::sortOrder))
        .toList();
  }

  public List<Attribute> query() {
    List<Attribute> queryAttributes =
        new java.util.ArrayList<>(
            action.queryParameters().stream()
                .map(p -> new Attribute(p.getName(), p.schema, p.isRequired))
                .filter(Attribute::isNotHiddenAttribute)
                .toList());
    if (includeSortBy) {
      List<Attribute> sortAttributes =
          action.queryParameters().stream()
              .map(p -> new Attribute(p.getName(), p.schema, p.isRequired))
              .filter(Attribute::isNotHiddenAttribute)
              .filter(attribute -> attribute.name.equals(Constant.SORT_BY))
              .toList();
      queryAttributes.addAll(sortAttributes);
    }
    queryAttributes.addAll(
        action.requestBodyParameters().stream()
            .map(p -> new Attribute(p.getName(), p.schema, p.isRequired))
            .filter(Attribute::isNotHiddenAttribute)
            .filter(Attribute::isFilterAttribute)
            .toList());
    if (!includePagination || queryAttributes.size() == 2) {
      queryAttributes =
          queryAttributes.stream().filter(Predicate.not(Attribute::isPaginationProperty)).toList();
    }
    return queryAttributes.stream().sorted(Comparator.comparing(Attribute::sortOrder)).toList();
  }

  public List<Attribute> singularSubAttributes() {
    List<Attribute> consolidatedList = new ArrayList<>();
    consolidatedList.addAll(
        query().stream()
            .filter(
                attribute ->
                    ((attribute.schema instanceof ObjectSchema
                            || attribute.schema instanceof MapSchema)
                        && attribute.schema.getProperties() != null
                        && attribute.schema.getItems() == null
                        && !attribute.isCompositeArrayRequestBody()))
            .filter(a -> a.isFilterAttribute() || a.isSubResource())
            .toList());
    consolidatedList.addAll(
        requestBody().stream()
            .filter(
                a ->
                    (includeFilterSubResource
                            && (a.isSubResource()
                                || a.attributes().stream().anyMatch(Attribute::isSubResource)))
                        || (!includeFilterSubResource && !a.isFilterAttribute()))
            .toList());
    consolidatedList =
        consolidatedList.stream()
            .filter(attribute -> includeSortBy || !attribute.name.equals(Constant.SORT_BY))
            .sorted(Comparator.comparing(Attribute::sortOrder))
            .toList();

    return consolidatedList.stream()
        .filter(attribute -> attribute.attributes().stream().anyMatch(Attribute::isSubResource))
        .toList();
  }

  public List<Attribute> multiSubAttributes() {
    List<Attribute> multiAttributes =
        new java.util.ArrayList<>(
            action.queryParameters().stream()
                .map(p -> new Attribute(p.getName(), p.schema, p.isRequired))
                .filter(Attribute::isNotHiddenAttribute)
                .toList());
    multiAttributes.addAll(
        action.requestBodyParameters().stream()
            .map(p -> new Attribute(p.getName(), p.schema, p.isRequired))
            .filter(Attribute::isNotHiddenAttribute)
            .toList());
    List<Attribute> consolidatedList =
        new ArrayList<>(
            multiAttributes.stream().filter(Attribute::isCompositeArrayRequestBody).toList());
    consolidatedList.addAll(
        multiAttributes.stream()
            .filter(
                attribute ->
                    includeSortBy
                        && attribute.name.equals(Constant.SORT_BY)
                        && !attribute.attributes().isEmpty())
            .toList());
    if (isFlatMultiAttribute) {
      consolidatedList = modifyToFlatList(consolidatedList);
    }
    return consolidatedList.stream().sorted(Comparator.comparing(Attribute::sortOrder)).toList();
  }

  @SneakyThrows
  private List<Attribute> singleLevelSort(List<Attribute> attributes) {
    boolean levelSkip = false;
    List<Attribute> consolidatedList = new ArrayList<>();
    List<Integer> sortOrders =
        new ArrayList<>(
            attributes.stream()
                .filter(a -> !a.isFilterAttribute())
                .filter(a -> !a.isSortAttribute())
                .flatMap(a -> a.attributes().stream())
                .map(Attribute::sortOrder)
                .toList());
    sortOrders.addAll(attributes.stream().map(Attribute::sortOrder).toList());
    for (Integer sortOrder : sortOrders.stream().sorted().toList()) {
      for (Attribute attribute : attributes) {
        levelSkip = false;
        if ((attribute.attributes().isEmpty() && attribute.sortOrder() == sortOrder)
            || (attribute.isSortAttribute() && attribute.sortOrder() == sortOrder)
            || (attribute.isFilterAttribute() && attribute.sortOrder() == sortOrder)) {
          if (consolidatedList.stream().noneMatch(a -> a.name.equals(attribute.name)))
            consolidatedList.add(attribute);
        } else {
          for (Attribute subAttribute : attribute.attributes()) {
            if (levelSkip) continue;
            if (attribute.isFilterAttribute()) continue;
            if (attribute.isPaginationProperty()) continue;
            if (attribute.isSortAttribute()) continue;
            if (subAttribute.sortOrder() == sortOrder) {
              ObjectMapper objectMapper = new ObjectMapper();
              objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
              Schema updatedSchema =
                  objectMapper.readValue(
                      objectMapper.writeValueAsString(attribute.schema), Schema.class);
              Map<String, Schema> updatedPropertiesMap = new HashMap<>();
              updatedPropertiesMap.put(
                  subAttribute.name, attribute.schema.getProperties().get(subAttribute.name));
              updatedSchema.setProperties(updatedPropertiesMap);
              consolidatedList.add(
                  new Attribute(attribute.name, updatedSchema, attribute.isRequired));
              levelSkip = true;
            }
          }
        }
      }
    }
    return consolidatedList;
  }

  @SneakyThrows
  private List<Attribute> modifyToFlatList(List<Attribute> attributes) {
    List<Attribute> consolidatedList = new ArrayList<>();
    List<Integer> sortOrders =
        attributes.stream()
            .flatMap(a -> a.attributes().stream())
            .map(Attribute::sortOrder)
            .toList();
    for (Integer sortOrder : sortOrders.stream().sorted().toList()) {
      for (Attribute attribute : attributes) {
        for (Attribute subAttribute : attribute.attributes()) {
          if (subAttribute.sortOrder() == sortOrder) {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
            Schema updatedSchema =
                objectMapper.readValue(
                    objectMapper.writeValueAsString(attribute.schema), Schema.class);
            Map<String, Schema> updatedPropertiesMap = new HashMap<>();
            updatedPropertiesMap.put(
                subAttribute.name, attribute.schema.getProperties().get(subAttribute.name));
            updatedSchema.setProperties(updatedPropertiesMap);
            consolidatedList.add(
                new Attribute(attribute.name, updatedSchema, attribute.isRequired));
          }
        }
      }
    }
    return consolidatedList;
  }

  @SneakyThrows
  private List<String> multiAttributeOrder(List<Attribute> attributes) {
    List<String> consolidatedOrder = new ArrayList<>();
    List<Integer> sortOrders =
        attributes.stream()
            .flatMap(a -> a.attributes().stream())
            .map(Attribute::sortOrder)
            .toList();
    for (Integer sortOrder : sortOrders.stream().sorted().toList()) {
      for (Attribute attribute : attributes) {
        for (Attribute subAttribute : attribute.attributes()) {
          if (subAttribute.sortOrder() == sortOrder
              && !consolidatedOrder.contains(attribute.name)) {
            consolidatedOrder.add(attribute.name);
          }
        }
      }
    }
    return consolidatedOrder.stream().toList();
  }

  public ActionAssist setFlatOuterEntries(boolean flatOuterEntries) {
    isFlatOuterEntries = flatOuterEntries;
    return this;
  }

  public List<Attribute> consolidatedSubParams() {
    List<Attribute> consoldiatedList = getAllAttribute();
    consoldiatedList =
        consoldiatedList.stream()
            .filter(
                a ->
                    a.isSubResource() || a.attributes().stream().anyMatch(Attribute::isSubResource))
            .toList();
    List<String> multiAttributeOrder = multiAttributeOrder(consoldiatedList);
    List<Attribute> orderedMultiAttributes = new ArrayList<>();
    for (String order : multiAttributeOrder) {
      for (Attribute subAttribute : consoldiatedList) {
        if (subAttribute.name.equals(order)) orderedMultiAttributes.add(subAttribute);
      }
    }
    return orderedMultiAttributes;
  }

  public ActionAssist setFlatSingleAttribute(boolean flatSingleAttribute) {
    isFlatSingleAttribute = flatSingleAttribute;
    return this;
  }
}
