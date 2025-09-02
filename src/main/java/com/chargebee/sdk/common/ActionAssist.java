package com.chargebee.sdk.common;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Assistant for processing and organizing Action attributes.
 * This class provides a fluent API for configuring attribute processing options
 * and retrieving filtered, sorted attribute collections.
 *
 * <p>Key features:
 * <ul>
 *   <li>Immutable configuration through builder pattern</li>
 *   <li>Functional programming approach with streams</li>
 *   <li>Type-safe operations without reflection</li>
 *   <li>Efficient schema operations without serialization</li>
 * </ul>
 *
 * @author Chargebee DX Team
 * @version 1.0
 */
public class ActionAssist {

  // Configuration fields - mutable for backwards compatibility with tests
  private Action action;
  private AttributeProcessingConfig config;

  // Default constructor for backwards compatibility
  public ActionAssist() {
    this.action = null;
    this.config = AttributeProcessingConfig.defaults();
  }

  // Static factory methods for creating instances
  public static ActionAssist of(Action action) {
    return new ActionAssist(action, AttributeProcessingConfig.defaults());
  }

  public static ActionAssist of(Action action, AttributeProcessingConfig config) {
    return new ActionAssist(action, config);
  }

  private ActionAssist(Action action, AttributeProcessingConfig config) {
    this.action = action;
    this.config = Objects.requireNonNull(config, "Configuration cannot be null");
  }

  // Fluent configuration methods that return new instances
  public ActionAssist withFlatMultiAttribute(boolean flatMultiAttribute) {
    return new ActionAssist(this.action, config.withFlatMultiAttribute(flatMultiAttribute));
  }

  public ActionAssist withFlatSingleAttribute(boolean flatSingleAttribute) {
    return new ActionAssist(this.action, config.withFlatSingleAttribute(flatSingleAttribute));
  }

  public ActionAssist withFilterSubResource(boolean includeFilterSubResource) {
    return new ActionAssist(
        this.action, config.withIncludeFilterSubResource(includeFilterSubResource));
  }

  public ActionAssist withPagination(boolean includePagination) {
    return new ActionAssist(this.action, config.withIncludePagination(includePagination));
  }

  public ActionAssist withOnlyPagination(boolean acceptOnlyPagination) {
    return new ActionAssist(this.action, config.withAcceptOnlyPagination(acceptOnlyPagination));
  }

  public ActionAssist withSortBy(boolean includeSortBy) {
    return new ActionAssist(this.action, config.withIncludeSortBy(includeSortBy));
  }

  public ActionAssist withFlatOuterEntries(boolean flatOuterEntries) {
    return new ActionAssist(this.action, config.withFlatOuterEntries(flatOuterEntries));
  }

  // Legacy API compatibility methods - modify current instance for test compatibility
  public ActionAssist setAction(Action action) {
    this.action = action;
    return this;
  }

  public ActionAssist setFlatMultiAttribute(boolean flatMultiAttribute) {
    this.config = config.withFlatMultiAttribute(flatMultiAttribute);
    return this;
  }

  public ActionAssist includeFilterSubResource() {
    this.config = config.withIncludeFilterSubResource(true);
    return this;
  }

  public ActionAssist includeSortBy() {
    this.config = config.withIncludeSortBy(true);
    return this;
  }

  public ActionAssist includePagination() {
    this.config = config.withIncludePagination(true);
    return this;
  }

  public ActionAssist setAcceptOnlyPagination() {
    this.config = config.withAcceptOnlyPagination(true);
    return this;
  }

  public ActionAssist setFlatOuterEntries(boolean flatOuterEntries) {
    this.config = config.withFlatOuterEntries(flatOuterEntries);
    return this;
  }

  public ActionAssist setFlatSingleAttribute(boolean flatSingleAttribute) {
    this.config = config.withFlatSingleAttribute(flatSingleAttribute);
    return this;
  }

  /**
   * Retrieves all attributes from both request body and query parameters,
   * applying configured filters and sorting.
   *
   * @return immutable list of attributes sorted by their sort order
   */
  public List<Attribute> getAllAttribute() {
    if (action == null) {
      return Collections.emptyList();
    }

    List<Attribute> attributes =
        Stream.concat(getRequestBodyAttributes().stream(), getQueryAttributes().stream())
            .filter(Attribute::isNotHiddenAttribute)
            .collect(Collectors.toList());

    // Apply filters based on configuration
    attributes = applySubResourceFilter(attributes);
    attributes = applyPaginationFilter(attributes);

    // Apply sorting strategy based on configuration
    return applySortingStrategy(attributes);
  }

  /**
   * Retrieves filtered request body parameters.
   *
   * @return immutable list of request body attributes
   */
  public List<Attribute> requestBody() {
    if (action == null) {
      return Collections.emptyList();
    }

    return getRequestBodyAttributes().stream()
        .filter(Attribute::isNotHiddenAttribute)
        .filter(Predicate.not(Attribute::isCompositeArrayRequestBody))
        .filter(Predicate.not(Attribute::isFilterAttribute))
        .sorted(Comparator.comparing(Attribute::sortOrder))
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * Retrieves filtered query parameters and relevant request body filters.
   *
   * @return immutable list of query attributes
   */
  public List<Attribute> query() {
    if (action == null) {
      return Collections.emptyList();
    }

    List<Attribute> queryAttributes = new ArrayList<>(getQueryAttributes());

    // Add sort attributes if configured
    if (config.includeSortBy()) {
      queryAttributes.addAll(getSortByAttributes());
    }

    // Add filter attributes from request body
    queryAttributes.addAll(getFilterAttributesFromRequestBody());

    // Apply pagination filter
    if (shouldExcludePagination(queryAttributes)) {
      queryAttributes =
          queryAttributes.stream()
              .filter(Predicate.not(Attribute::isPaginationProperty))
              .collect(Collectors.toList());
    }

    return queryAttributes.stream()
        .filter(Attribute::isNotHiddenAttribute)
        .sorted(Comparator.comparing(Attribute::sortOrder))
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * Retrieves singular sub-attributes (non-array object attributes).
   *
   * @return immutable list of singular sub-attributes
   */
  public List<Attribute> singularSubAttributes() {
    if (action == null) {
      return Collections.emptyList();
    }

    List<Attribute> result = new ArrayList<>();

    // Add qualifying query attributes
    result.addAll(
        query().stream()
            .filter(this::isSingularObjectAttribute)
            .filter(attr -> attr.isFilterAttribute() || attr.isSubResource())
            .collect(Collectors.toList()));

    // Add qualifying request body attributes
    result.addAll(
        requestBody().stream()
            .filter(this::shouldIncludeInSingularSubAttributes)
            .collect(Collectors.toList()));

    return result.stream()
        .filter(attr -> config.includeSortBy() || !attr.name.equals(Constant.SORT_BY))
        .filter(attr -> attr.attributes().stream().anyMatch(Attribute::isSubResource))
        .sorted(Comparator.comparing(Attribute::sortOrder))
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * Retrieves multi sub-attributes (composite arrays and complex attributes).
   *
   * @return immutable list of multi sub-attributes
   */
  public List<Attribute> multiSubAttributes() {
    if (action == null) {
      return Collections.emptyList();
    }

    List<Attribute> allAttributes =
        Stream.concat(getQueryAttributes().stream(), getRequestBodyAttributes().stream())
            .filter(Attribute::isNotHiddenAttribute)
            .collect(Collectors.toList());

    List<Attribute> consolidatedList =
        new ArrayList<>(
            allAttributes.stream()
                .filter(Attribute::isCompositeArrayRequestBody)
                .collect(Collectors.toList()));

    // Add sort by attributes with sub-attributes if configured
    if (config.includeSortBy()) {
      consolidatedList.addAll(
          allAttributes.stream()
              .filter(attr -> attr.name.equals(Constant.SORT_BY) && !attr.attributes().isEmpty())
              .collect(Collectors.toList()));
    }

    // Apply flattening if configured
    if (config.flatMultiAttribute()) {
      consolidatedList = flattenAttributeList(consolidatedList);
    }

    return consolidatedList.stream()
        .sorted(Comparator.comparing(Attribute::sortOrder))
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * Retrieves consolidated sub-parameters in the correct order.
   *
   * @return immutable list of consolidated sub-parameters
   */
  public List<Attribute> consolidatedSubParams() {
    if (action == null) {
      return Collections.emptyList();
    }

    List<Attribute> subResourceAttributes =
        getAllAttribute().stream()
            .filter(
                attr ->
                    attr.isSubResource()
                        || attr.attributes().stream().anyMatch(Attribute::isSubResource))
            .collect(Collectors.toList());

    List<String> attributeOrder = determineMultiAttributeOrder(subResourceAttributes);

    return attributeOrder.stream()
        .map(name -> findAttributeByName(subResourceAttributes, name))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toUnmodifiableList());
  }

  // Private helper methods

  private List<Attribute> getRequestBodyAttributes() {
    return action.requestBodyParameters().stream()
        .map(p -> new Attribute(p.getName(), p.schema, p.isRequired))
        .collect(Collectors.toList());
  }

  private List<Attribute> getQueryAttributes() {
    return action.queryParameters().stream()
        .map(p -> new Attribute(p.getName(), p.schema, p.isRequired))
        .collect(Collectors.toList());
  }

  private List<Attribute> getSortByAttributes() {
    return getQueryAttributes().stream()
        .filter(attr -> attr.name.equals(Constant.SORT_BY))
        .filter(Attribute::isNotHiddenAttribute)
        .collect(Collectors.toList());
  }

  private List<Attribute> getFilterAttributesFromRequestBody() {
    return getRequestBodyAttributes().stream()
        .filter(Attribute::isNotHiddenAttribute)
        .filter(Attribute::isFilterAttribute)
        .collect(Collectors.toList());
  }

  private List<Attribute> applySubResourceFilter(List<Attribute> attributes) {
    if (config.includeFilterSubResource()) {
      return attributes; // Include all
    }
    return attributes.stream()
        .filter(attr -> !attr.isSubResource() || attr.isFilterAttribute())
        .collect(Collectors.toList());
  }

  private List<Attribute> applyPaginationFilter(List<Attribute> attributes) {
    if (shouldExcludePagination(attributes)) {
      return attributes.stream()
          .filter(Predicate.not(Attribute::isPaginationProperty))
          .collect(Collectors.toList());
    }
    return attributes;
  }

  private boolean shouldExcludePagination(List<Attribute> attributes) {
    return !config.includePagination()
        || (!config.acceptOnlyPagination() && attributes.size() == 2);
  }

  private List<Attribute> applySortingStrategy(List<Attribute> attributes) {
    if (config.flatOuterEntries()) {
      return applySingleLevelSort(attributes);
    }
    if (config.flatSingleAttribute()) {
      return applyGenericSort(attributes);
    }
    return attributes.stream()
        .sorted(Comparator.comparing(Attribute::sortOrder))
        .collect(Collectors.toUnmodifiableList());
  }

  private List<Attribute> applyGenericSort(List<Attribute> attributes) {
    List<Attribute> result = new ArrayList<>();

    // Add simple attributes first (sorted by order)
    List<Integer> validSortOrders =
        attributes.stream()
            .map(Attribute::sortOrder)
            .filter(order -> order > -1)
            .distinct()
            .sorted()
            .collect(Collectors.toList());

    for (Integer order : validSortOrders) {
      attributes.stream()
          .filter(attr -> attr.sortOrder() == order)
          .filter(
              attr ->
                  attr.attributes().isEmpty()
                      || attr.isFilterAttribute()
                      || attr.name.equals(Constant.SORT_BY))
          .forEach(result::add);
    }

    // Add flattened sub-attributes
    Set<Integer> subAttributeOrders =
        attributes.stream()
            .filter(attr -> attr.sortOrder() == -1)
            .flatMap(attr -> attr.attributes().stream())
            .map(Attribute::sortOrder)
            .collect(Collectors.toSet());

    validSortOrders.addAll(subAttributeOrders);

    for (Integer order : validSortOrders.stream().sorted().collect(Collectors.toList())) {
      for (Attribute attribute : attributes) {
        for (Attribute subAttribute : attribute.attributes()) {
          if (subAttribute.sortOrder() == order) {
            Schema<?> flattenedSchema = createFlattenedSchema(attribute.schema, subAttribute.name);
            result.add(new Attribute(attribute.name, flattenedSchema, attribute.isRequired));
          }
        }
      }
    }

    return Collections.unmodifiableList(result);
  }

  private List<Attribute> applySingleLevelSort(List<Attribute> attributes) {
    List<Attribute> result = new ArrayList<>();
    Set<String> addedNames = new HashSet<>();

    // Collect all sort orders
    Set<Integer> allSortOrders = new TreeSet<>();
    allSortOrders.addAll(attributes.stream().map(Attribute::sortOrder).collect(Collectors.toSet()));
    allSortOrders.addAll(
        attributes.stream()
            .filter(attr -> !attr.isFilterAttribute() && !attr.isSortAttribute())
            .flatMap(attr -> attr.attributes().stream())
            .map(Attribute::sortOrder)
            .collect(Collectors.toSet()));

    for (Integer sortOrder : allSortOrders) {
      // Add direct attributes
      for (Attribute attribute : attributes) {
        if (shouldAddDirectAttribute(attribute, sortOrder, addedNames)) {
          result.add(attribute);
          addedNames.add(attribute.name);
        }
      }

      // Add flattened sub-attributes
      for (Attribute attribute : attributes) {
        if (shouldSkipForSubAttribute(attribute)) continue;

        for (Attribute subAttribute : attribute.attributes()) {
          if (subAttribute.sortOrder() == sortOrder) {
            Schema<?> flattenedSchema = createFlattenedSchema(attribute.schema, subAttribute.name);
            result.add(new Attribute(attribute.name, flattenedSchema, attribute.isRequired));
            break; // Only add one sub-attribute per parent per sort order
          }
        }
      }
    }

    return Collections.unmodifiableList(result);
  }

  private boolean shouldAddDirectAttribute(
      Attribute attribute, Integer sortOrder, Set<String> addedNames) {
    return (attribute.attributes().isEmpty() && attribute.sortOrder() == sortOrder)
        || (attribute.isSortAttribute() && attribute.sortOrder() == sortOrder)
        || (attribute.isFilterAttribute() && attribute.sortOrder() == sortOrder)
            && !addedNames.contains(attribute.name);
  }

  private boolean shouldSkipForSubAttribute(Attribute attribute) {
    return attribute.isFilterAttribute()
        || attribute.isPaginationProperty()
        || attribute.isSortAttribute();
  }

  private List<Attribute> flattenAttributeList(List<Attribute> attributes) {
    List<Attribute> result = new ArrayList<>();

    Set<Integer> subAttributeOrders =
        attributes.stream()
            .flatMap(attr -> attr.attributes().stream())
            .map(Attribute::sortOrder)
            .collect(Collectors.toSet());

    for (Integer sortOrder : subAttributeOrders.stream().sorted().collect(Collectors.toList())) {
      for (Attribute attribute : attributes) {
        for (Attribute subAttribute : attribute.attributes()) {
          if (subAttribute.sortOrder() == sortOrder) {
            Schema<?> flattenedSchema = createFlattenedSchema(attribute.schema, subAttribute.name);
            result.add(new Attribute(attribute.name, flattenedSchema, attribute.isRequired));
          }
        }
      }
    }

    return result;
  }

  /**
   * Creates a flattened schema containing only the specified property.
   * This replaces the ObjectMapper deep copy approach with a type-safe method.
   */
  private Schema<?> createFlattenedSchema(Schema<?> originalSchema, String propertyName) {
    if (originalSchema.getProperties() == null
        || !originalSchema.getProperties().containsKey(propertyName)) {
      return originalSchema;
    }

    ObjectSchema flattenedSchema = new ObjectSchema();

    // Copy essential metadata
    flattenedSchema.setType(originalSchema.getType());
    flattenedSchema.setDescription(originalSchema.getDescription());
    flattenedSchema.setExtensions(originalSchema.getExtensions());

    // Add only the specific property
    Map<String, Schema> singleProperty = new HashMap<>();
    singleProperty.put(propertyName, originalSchema.getProperties().get(propertyName));
    flattenedSchema.setProperties(singleProperty);

    // Set required if the original property was required
    if (originalSchema.getRequired() != null
        && originalSchema.getRequired().contains(propertyName)) {
      flattenedSchema.setRequired(List.of(propertyName));
    }

    return flattenedSchema;
  }

  private boolean isSingularObjectAttribute(Attribute attribute) {
    return (attribute.schema instanceof ObjectSchema)
        && attribute.schema.getProperties() != null
        && attribute.schema.getItems() == null
        && !attribute.isCompositeArrayRequestBody();
  }

  private boolean shouldIncludeInSingularSubAttributes(Attribute attribute) {
    if (config.includeFilterSubResource()) {
      return attribute.isSubResource()
          || attribute.attributes().stream().anyMatch(Attribute::isSubResource);
    }
    return !attribute.isFilterAttribute();
  }

  private List<String> determineMultiAttributeOrder(List<Attribute> attributes) {
    Map<String, Integer> attributeToMinOrder = new HashMap<>();

    for (Attribute attribute : attributes) {
      int minOrder =
          attribute.attributes().stream()
              .mapToInt(Attribute::sortOrder)
              .min()
              .orElse(Integer.MAX_VALUE);
      attributeToMinOrder.put(attribute.name, minOrder);
    }

    return attributeToMinOrder.entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }

  private Optional<Attribute> findAttributeByName(List<Attribute> attributes, String name) {
    return attributes.stream().filter(attr -> attr.name.equals(name)).findFirst();
  }

  /**
   * Immutable configuration class for ActionAssist behavior.
   * Uses builder pattern for type-safe configuration.
   */
  public static final class AttributeProcessingConfig {
    private final boolean flatMultiAttribute;
    private final boolean flatSingleAttribute;
    private final boolean includeFilterSubResource;
    private final boolean includePagination;
    private final boolean acceptOnlyPagination;
    private final boolean includeSortBy;
    private final boolean flatOuterEntries;

    private AttributeProcessingConfig(Builder builder) {
      this.flatMultiAttribute = builder.flatMultiAttribute;
      this.flatSingleAttribute = builder.flatSingleAttribute;
      this.includeFilterSubResource = builder.includeFilterSubResource;
      this.includePagination = builder.includePagination;
      this.acceptOnlyPagination = builder.acceptOnlyPagination;
      this.includeSortBy = builder.includeSortBy;
      this.flatOuterEntries = builder.flatOuterEntries;
    }

    public static AttributeProcessingConfig defaults() {
      return new Builder().build();
    }

    public static Builder builder() {
      return new Builder();
    }

    // Immutable update methods
    public AttributeProcessingConfig withFlatMultiAttribute(boolean flatMultiAttribute) {
      return new Builder(this).flatMultiAttribute(flatMultiAttribute).build();
    }

    public AttributeProcessingConfig withFlatSingleAttribute(boolean flatSingleAttribute) {
      return new Builder(this).flatSingleAttribute(flatSingleAttribute).build();
    }

    public AttributeProcessingConfig withIncludeFilterSubResource(
        boolean includeFilterSubResource) {
      return new Builder(this).includeFilterSubResource(includeFilterSubResource).build();
    }

    public AttributeProcessingConfig withIncludePagination(boolean includePagination) {
      return new Builder(this).includePagination(includePagination).build();
    }

    public AttributeProcessingConfig withAcceptOnlyPagination(boolean acceptOnlyPagination) {
      return new Builder(this).acceptOnlyPagination(acceptOnlyPagination).build();
    }

    public AttributeProcessingConfig withIncludeSortBy(boolean includeSortBy) {
      return new Builder(this).includeSortBy(includeSortBy).build();
    }

    public AttributeProcessingConfig withFlatOuterEntries(boolean flatOuterEntries) {
      return new Builder(this).flatOuterEntries(flatOuterEntries).build();
    }

    // Getters
    public boolean flatMultiAttribute() {
      return flatMultiAttribute;
    }

    public boolean flatSingleAttribute() {
      return flatSingleAttribute;
    }

    public boolean includeFilterSubResource() {
      return includeFilterSubResource;
    }

    public boolean includePagination() {
      return includePagination;
    }

    public boolean acceptOnlyPagination() {
      return acceptOnlyPagination;
    }

    public boolean includeSortBy() {
      return includeSortBy;
    }

    public boolean flatOuterEntries() {
      return flatOuterEntries;
    }

    public static final class Builder {
      private boolean flatMultiAttribute = true;
      private boolean flatSingleAttribute = false;
      private boolean includeFilterSubResource = false;
      private boolean includePagination = false;
      private boolean acceptOnlyPagination = false;
      private boolean includeSortBy = false;
      private boolean flatOuterEntries = false;

      private Builder() {}

      private Builder(AttributeProcessingConfig config) {
        this.flatMultiAttribute = config.flatMultiAttribute;
        this.flatSingleAttribute = config.flatSingleAttribute;
        this.includeFilterSubResource = config.includeFilterSubResource;
        this.includePagination = config.includePagination;
        this.acceptOnlyPagination = config.acceptOnlyPagination;
        this.includeSortBy = config.includeSortBy;
        this.flatOuterEntries = config.flatOuterEntries;
      }

      public Builder flatMultiAttribute(boolean flatMultiAttribute) {
        this.flatMultiAttribute = flatMultiAttribute;
        return this;
      }

      public Builder flatSingleAttribute(boolean flatSingleAttribute) {
        this.flatSingleAttribute = flatSingleAttribute;
        return this;
      }

      public Builder includeFilterSubResource(boolean includeFilterSubResource) {
        this.includeFilterSubResource = includeFilterSubResource;
        return this;
      }

      public Builder includePagination(boolean includePagination) {
        this.includePagination = includePagination;
        return this;
      }

      public Builder acceptOnlyPagination(boolean acceptOnlyPagination) {
        this.acceptOnlyPagination = acceptOnlyPagination;
        return this;
      }

      public Builder includeSortBy(boolean includeSortBy) {
        this.includeSortBy = includeSortBy;
        return this;
      }

      public Builder flatOuterEntries(boolean flatOuterEntries) {
        this.flatOuterEntries = flatOuterEntries;
        return this;
      }

      public AttributeProcessingConfig build() {
        return new AttributeProcessingConfig(this);
      }
    }
  }
}
