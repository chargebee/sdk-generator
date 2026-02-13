package com.chargebee.sdk.changelog.generators;

import static com.chargebee.GenUtil.pluralize;
import static com.chargebee.openapi.Extension.*;
import static com.chargebee.sdk.changelog.Constants.CHANGELOG;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Enum;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.openapi.parameter.Parameter;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.changelog.ChangeLog;
import com.chargebee.sdk.changelog.models.ChangeLogSchema;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChangeLogGenerator implements FileGenerator {

  private static final String QUERY_PREFIX = "query";
  private static final String BODY_PREFIX = "body";
  private static final String QUERY_PARAMETER_TYPE = "query parameter";
  private static final String REQUEST_BODY_PARAMETER_TYPE = "request body parameter";

  private static final String ADDED_VALUE_SINGULAR = "added as a new value";
  private static final String ADDED_VALUE_PLURAL = "added as new values";
  private static final String REMOVED_VALUE = "removed";

  private final ChangeLog changeLogGenerator;
  private final Template changeLogTemplate;

  public ChangeLogGenerator(ChangeLog changeLogGenerator) {
    this.changeLogGenerator = changeLogGenerator;
    this.changeLogTemplate = changeLogGenerator.getTemplateContent(CHANGELOG);
  }

  @Override
  public FileOp generate(String output, Spec oldVersion, Spec newerVersion) throws IOException {
    List<Resource> oldResources = filterResources(oldVersion.resources());
    List<Resource> newResources = filterResources(newerVersion.resources());

    ChangeLogSchema schema =
        buildChangeLogSchema(oldVersion, newerVersion, oldResources, newResources);
    String content = renderTemplate(schema);

    return new FileOp.WriteString("./", output + "CHANGELOG.md", content);
  }

  private ChangeLogSchema buildChangeLogSchema(
      Spec oldVersion,
      Spec newerVersion,
      List<Resource> oldResources,
      List<Resource> newResources) {
    ChangeLogSchema schema = new ChangeLogSchema();

    schema.setNewResource(generateNewResources(oldResources, newResources));
    schema.setNewActions(generateNewActions(oldResources, newResources));
    schema.setNewResourceAttribute(generateNewAttributes(oldResources, newResources));
    schema.setNewParams(generateNewParameters(oldResources, newResources));
    schema.setNewEventType(generateNewEvents(oldVersion, newerVersion));

    schema.setDeletedResource(generateDeletedResources(oldResources, newResources));
    schema.setDeletedActions(generateDeletedActions(oldResources, newResources));
    schema.setDeletedResourceAttribute(generateDeletedAttributes(oldResources, newResources));
    schema.setDeletedParams(generateDeletedParameters(oldResources, newResources));
    schema.setDeletedEventType(generateDeletedEvents(oldVersion, newerVersion));

    schema.setNewEnumValues(
        generateNewEnumValues(oldResources, newResources, oldVersion, newerVersion));
    schema.setDeletedEnumValues(
        generateDeletedEnumValues(oldResources, newResources, oldVersion, newerVersion));
    schema.setParameterRequirementChangesValues(
        generateParameterRequirementChanges(oldResources, newResources));

    return schema;
  }

  private String renderTemplate(ChangeLogSchema schema) throws IOException {
    Map<String, Object> schemaMap =
        changeLogGenerator.getObjectMapper().convertValue(schema, Map.class);
    String content = changeLogTemplate.apply(schemaMap);
    return content.replaceAll("(?m)^[ \t]*\r?\n([ \t]*\r?\n)+", "\n\n").replaceAll("^\\s+", "");
  }

  private List<Resource> filterResources(List<Resource> resources) {
    List<String> hiddenResourceIds = List.of(changeLogGenerator.hiddenOverride);
    return resources.stream()
        .filter(resource -> !hiddenResourceIds.contains(resource.id))
        .collect(Collectors.toList());
  }

  private List<String> generateNewResources(
      List<Resource> oldResources, List<Resource> newResources) {
    Set<String> existingResourceIds = extractResourceIds(oldResources);

    return newResources.stream()
        .filter(resource -> !existingResourceIds.contains(resource.id))
        .map(this::formatNewResourceLine)
        .distinct()
        .collect(Collectors.toList());
  }

  private String formatNewResourceLine(Resource resource) {
    return String.format(
        "- [`%s`](%s) has been added.", resource.name, getDocsUrlForResourceList(resource));
  }

  private List<String> generateNewActions(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Set<String>> oldActionsByResource = buildActionMap(oldResources);
    Set<String> lines = new LinkedHashSet<>();

    for (Resource newResource : newResources) {
      Set<String> existingActions =
          oldActionsByResource.getOrDefault(newResource.id, Collections.emptySet());

      for (Action action : newResource.actions) {
        if (!existingActions.contains(action.name)) {
          lines.add(formatNewActionLine(newResource, action));
        }
      }
    }

    return new ArrayList<>(lines);
  }

  private String formatNewActionLine(Resource resource, Action action) {
    return String.format(
        "- [`%s`](%s) has been added to [`%s`](%s).",
        action.id,
        getDocsUrlForActions(resource, action),
        resource.name,
        getDocsUrlForResourceList(resource));
  }

  private List<String> generateNewAttributes(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Set<String>> oldAttributesByResource = buildAttributeMap(oldResources);

    return newResources.stream()
        .flatMap(resource -> findNewAttributes(resource, oldAttributesByResource))
        .distinct()
        .collect(Collectors.toList());
  }

  private Stream<String> findNewAttributes(
      Resource resource, Map<String, Set<String>> oldAttributesByResource) {
    Set<String> existingAttributes =
        oldAttributesByResource.getOrDefault(resource.id, Collections.emptySet());

    return resource.attributes().stream()
        .filter(attribute -> !existingAttributes.contains(attribute.name))
        .map(attribute -> formatNewAttributeLine(resource, attribute));
  }

  private String formatNewAttributeLine(Resource resource, Attribute attribute) {
    return String.format(
        "- [`%s`](%s) has been added to [`%s`](%s).",
        attribute.name,
        getDocsUrlForAttribute(resource, attribute),
        resource.name,
        getDocsUrlForResourceList(resource));
  }

  private List<String> generateNewParameters(
      List<Resource> oldResources, List<Resource> newResources) {
    List<String> queryParameterLines =
        generateParameterLines(
            oldResources, newResources, Action::queryParameters, QUERY_PARAMETER_TYPE);
    List<String> bodyParameterLines =
        generateParameterLines(
            oldResources, newResources, Action::requestBodyParameters, REQUEST_BODY_PARAMETER_TYPE);

    return Stream.concat(queryParameterLines.stream(), bodyParameterLines.stream())
        .collect(Collectors.toList());
  }

  private List<String> generateParameterLines(
      List<Resource> oldResources,
      List<Resource> newResources,
      Function<Action, List<Parameter>> parameterExtractor,
      String parameterType) {
    Map<String, Map<String, Set<String>>> oldParametersByResource =
        buildParameterMap(oldResources, parameterExtractor);
    Set<String> lines = new LinkedHashSet<>();

    for (Resource newResource : newResources) {
      Map<String, Set<String>> oldActionParameters =
          oldParametersByResource.getOrDefault(newResource.id, Collections.emptyMap());

      for (Action action : newResource.actions) {
        Set<String> existingParameters =
            oldActionParameters.getOrDefault(action.id, Collections.emptySet());
        findAndAddNewParameters(
            newResource,
            action,
            parameterExtractor.apply(action),
            existingParameters,
            parameterType,
            lines);
      }
    }

    return new ArrayList<>(lines);
  }

  private void findAndAddNewParameters(
      Resource resource,
      Action action,
      List<Parameter> parameters,
      Set<String> existingParameters,
      String parameterType,
      Set<String> lines) {
    for (Parameter parameter : parameters) {
      if (!existingParameters.contains(parameter.getName())) {
        lines.add(formatParameterLine(resource, action, parameter, parameterType, true));
      } else if (parameter.schema.getProperties() != null) {
        processNestedParameters(
            parameter.schema,
            parameter.getName(),
            existingParameters,
            resource,
            action,
            parameterType,
            lines,
            true);
      }
    }
  }

  private void processNestedParameters(
      Schema schema,
      String path,
      Set<String> existingParameters,
      Resource resource,
      Action action,
      String parameterType,
      Set<String> lines,
      boolean isNew) {
    if (schema.getProperties() == null) {
      return;
    }

    schema
        .getProperties()
        .forEach(
            (key, value) -> {
              String nestedPath = path + "." + key;

              if (!existingParameters.contains(nestedPath)) {
                Parameter nestedParameter = new Parameter(nestedPath, (Schema) value);
                lines.add(
                    formatParameterLine(resource, action, nestedParameter, parameterType, isNew));
              } else {
                processNestedParameters(
                    (Schema) value,
                    nestedPath,
                    existingParameters,
                    resource,
                    action,
                    parameterType,
                    lines,
                    isNew);
              }
            });
  }

  private String formatParameterLine(
      Resource resource,
      Action action,
      Parameter parameter,
      String parameterType,
      boolean isAdded) {
    String actionVerb = isAdded ? "added" : "removed";
    return String.format(
        "- [`%s`](%s) has been %s as %s to [`%s`](%s) in [`%s`](%s).",
        parameter.getName(),
        getDocsUrlForParameter(resource, action, parameter),
        actionVerb,
        parameterType,
        action.id,
        getDocsUrlForActions(resource, action),
        resource.name,
        getDocsUrlForResourceList(resource));
  }

  private List<String> generateNewEvents(Spec oldVersion, Spec newerVersion) {
    List<Map<String, String>> oldEvents = oldVersion.extractWebhookInfo(false);
    List<Map<String, String>> newEvents = newerVersion.extractWebhookInfo(false);

    Set<String> existingEventTypes =
        oldEvents.stream().map(event -> event.get("type")).collect(Collectors.toSet());

    return newEvents.stream()
        .filter(event -> !existingEventTypes.contains(event.get("type")))
        .map(this::formatNewEventLine)
        .distinct()
        .collect(Collectors.toList());
  }

  private String formatNewEventLine(Map<String, String> event) {
    String eventType = event.get("type");
    return String.format("- [`%s`](%s) has been added.", eventType, getDocsUrlForEvent(eventType));
  }

  private List<String> generateDeletedResources(
      List<Resource> oldResources, List<Resource> newResources) {
    Set<String> currentResourceIds = extractResourceIds(newResources);

    return oldResources.stream()
        .filter(resource -> !currentResourceIds.contains(resource.id))
        .map(resource -> String.format("- %s has been removed.", resource.name))
        .distinct()
        .collect(Collectors.toList());
  }

  private List<String> generateDeletedActions(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Resource> newResourceMap =
        newResources.stream()
            .collect(Collectors.toMap(resource -> resource.id, resource -> resource));
    Set<String> lines = new LinkedHashSet<>();

    for (Resource oldResource : oldResources) {
      Resource correspondingNewResource = newResourceMap.get(oldResource.id);

      if (correspondingNewResource != null) {
        addDeletedActionsForExistingResource(oldResource, correspondingNewResource, lines);
      } else {
        addDeletedActionsForRemovedResource(oldResource, lines);
      }
    }

    return new ArrayList<>(lines);
  }

  private void addDeletedActionsForExistingResource(
      Resource oldResource, Resource newResource, Set<String> lines) {
    Set<String> currentActions =
        newResource.actions.stream().map(action -> action.name).collect(Collectors.toSet());

    for (Action oldAction : oldResource.actions) {
      if (!currentActions.contains(oldAction.name)) {
        lines.add(formatDeletedAction(newResource, oldAction, true));
      }
    }
  }

  private void addDeletedActionsForRemovedResource(Resource oldResource, Set<String> lines) {
    for (Action action : oldResource.actions) {
      lines.add(formatDeletedAction(oldResource, action, false));
    }
  }

  private String formatDeletedAction(Resource resource, Action action, boolean includeHyperlink) {
    if (includeHyperlink) {
      return String.format(
          "- `%s` has been removed from [`%s`](%s).",
          action.id, resource.name, getDocsUrlForResourceList(resource));
    } else {
      return String.format("- `%s` has been removed from `%s`.", action.id, resource.name);
    }
  }

  private List<String> generateDeletedAttributes(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Resource> newResourceMap =
        newResources.stream()
            .collect(Collectors.toMap(resource -> resource.id, resource -> resource));
    Set<String> lines = new LinkedHashSet<>();

    for (Resource oldResource : oldResources) {
      Resource correspondingNewResource = newResourceMap.get(oldResource.id);

      if (correspondingNewResource != null) {
        addDeletedAttributesForExistingResource(oldResource, correspondingNewResource, lines);
      } else {
        addDeletedAttributesForRemovedResource(oldResource, lines);
      }
    }

    return new ArrayList<>(lines);
  }

  private void addDeletedAttributesForExistingResource(
      Resource oldResource, Resource newResource, Set<String> lines) {
    Set<String> currentAttributes =
        newResource.attributes().stream()
            .map(attribute -> attribute.name)
            .collect(Collectors.toSet());

    for (Attribute oldAttribute : oldResource.attributes()) {
      if (!currentAttributes.contains(oldAttribute.name)) {
        lines.add(formatDeletedAttribute(newResource, oldAttribute, true));
      }
    }
  }

  private void addDeletedAttributesForRemovedResource(Resource oldResource, Set<String> lines) {
    for (Attribute attribute : oldResource.attributes()) {
      lines.add(formatDeletedAttribute(oldResource, attribute, false));
    }
  }

  private String formatDeletedAttribute(
      Resource resource, Attribute attribute, boolean includeHyperlink) {
    if (includeHyperlink) {
      return String.format(
          "- `%s` has been removed from [`%s`](%s).",
          attribute.name, resource.name, getDocsUrlForResourceList(resource));
    } else {
      return String.format("- `%s` has been removed from `%s`.", attribute.name, resource.name);
    }
  }

  private List<String> generateDeletedParameters(
      List<Resource> oldResources, List<Resource> newResources) {
    List<String> deletedQueryParameters =
        generateDeletedParameterLines(
            oldResources, newResources, Action::queryParameters, QUERY_PARAMETER_TYPE);
    List<String> deletedBodyParameters =
        generateDeletedParameterLines(
            oldResources, newResources, Action::requestBodyParameters, REQUEST_BODY_PARAMETER_TYPE);

    return Stream.concat(deletedQueryParameters.stream(), deletedBodyParameters.stream())
        .collect(Collectors.toList());
  }

  private List<String> generateDeletedParameterLines(
      List<Resource> oldResources,
      List<Resource> newResources,
      Function<Action, List<Parameter>> parameterExtractor,
      String parameterType) {
    Map<String, Resource> newResourceMap =
        newResources.stream()
            .collect(Collectors.toMap(resource -> resource.id, resource -> resource));
    Set<String> lines = new LinkedHashSet<>();

    for (Resource oldResource : oldResources) {
      Resource correspondingNewResource = newResourceMap.get(oldResource.id);

      if (correspondingNewResource != null) {
        processDeletedParametersForExistingResource(
            oldResource, correspondingNewResource, parameterExtractor, parameterType, lines);
      } else {
        processDeletedParametersForRemovedResource(
            oldResource, parameterExtractor, parameterType, lines);
      }
    }

    return new ArrayList<>(lines);
  }

  private void processDeletedParametersForExistingResource(
      Resource oldResource,
      Resource newResource,
      Function<Action, List<Parameter>> parameterExtractor,
      String parameterType,
      Set<String> lines) {
    Map<String, Action> newActionMap =
        newResource.actions.stream()
            .collect(Collectors.toMap(action -> action.id, action -> action));

    for (Action oldAction : oldResource.actions) {
      Action correspondingNewAction = newActionMap.get(oldAction.id);

      if (correspondingNewAction != null) {
        findAndAddDeletedParameters(
            oldResource,
            oldAction,
            newResource,
            correspondingNewAction,
            parameterExtractor,
            parameterType,
            lines,
            true,
            true);
      } else {
        findAndAddDeletedParameters(
            oldResource,
            oldAction,
            newResource,
            oldAction,
            parameterExtractor,
            parameterType,
            lines,
            true,
            false);
      }
    }
  }

  private void processDeletedParametersForRemovedResource(
      Resource oldResource,
      Function<Action, List<Parameter>> parameterExtractor,
      String parameterType,
      Set<String> lines) {
    for (Action action : oldResource.actions) {
      for (Parameter parameter : parameterExtractor.apply(action)) {
        lines.add(
            formatDeletedParameter(oldResource, action, parameter, parameterType, false, false));
      }
    }
  }

  private void findAndAddDeletedParameters(
      Resource oldResource,
      Action oldAction,
      Resource newResource,
      Action newAction,
      Function<Action, List<Parameter>> parameterExtractor,
      String parameterType,
      Set<String> lines,
      boolean includeResourceHyperlink,
      boolean includeActionHyperlink) {
    Set<String> currentParameters = collectParameterNames(parameterExtractor.apply(newAction));

    for (Parameter oldParameter : parameterExtractor.apply(oldAction)) {
      if (!currentParameters.contains(oldParameter.getName())) {
        lines.add(
            formatDeletedParameter(
                newResource,
                newAction,
                oldParameter,
                parameterType,
                includeResourceHyperlink,
                includeActionHyperlink));
      } else if (oldParameter.schema.getProperties() != null) {
        processDeletedNestedParameters(
            oldParameter.schema,
            oldParameter.getName(),
            currentParameters,
            newResource,
            newAction,
            parameterType,
            lines);
      }
    }
  }

  private void processDeletedNestedParameters(
      Schema schema,
      String path,
      Set<String> currentParameters,
      Resource resource,
      Action action,
      String parameterType,
      Set<String> lines) {
    if (schema.getProperties() == null) {
      return;
    }

    schema
        .getProperties()
        .forEach(
            (key, value) -> {
              String nestedPath = path + "." + key;

              if (!currentParameters.contains(nestedPath)) {
                Parameter nestedParameter = new Parameter(nestedPath, (Schema) value);
                lines.add(
                    formatDeletedParameter(
                        resource, action, nestedParameter, parameterType, true, true));
              } else {
                processDeletedNestedParameters(
                    (Schema) value,
                    nestedPath,
                    currentParameters,
                    resource,
                    action,
                    parameterType,
                    lines);
              }
            });
  }

  private String formatDeletedParameter(
      Resource resource,
      Action action,
      Parameter parameter,
      String parameterType,
      boolean includeResourceHyperlink,
      boolean includeActionHyperlink) {
    if (includeResourceHyperlink && includeActionHyperlink) {
      return String.format(
          "- `%s` has been removed as %s from [`%s`](%s) in [`%s`](%s).",
          parameter.getName(),
          parameterType,
          action.id,
          getDocsUrlForActions(resource, action),
          resource.name,
          getDocsUrlForResourceList(resource));
    } else if (includeResourceHyperlink) {
      return String.format(
          "- `%s` has been removed as %s from `%s` in [`%s`](%s).",
          parameter.getName(),
          parameterType,
          action.id,
          resource.name,
          getDocsUrlForResourceList(resource));
    } else {
      return String.format(
          "- `%s` has been removed as %s from `%s` in `%s`.",
          parameter.getName(), parameterType, action.id, resource.name);
    }
  }

  private List<String> generateDeletedEvents(Spec oldVersion, Spec newerVersion) {
    List<Map<String, String>> oldEvents = oldVersion.extractWebhookInfo(false);
    List<Map<String, String>> newEvents = newerVersion.extractWebhookInfo(false);

    Set<String> currentEventTypes =
        newEvents.stream().map(event -> event.get("type")).collect(Collectors.toSet());

    return oldEvents.stream()
        .filter(event -> !currentEventTypes.contains(event.get("type")))
        .map(event -> String.format("- `%s` has been removed.", event.get("type")))
        .distinct()
        .collect(Collectors.toList());
  }

  private List<String> generateNewEnumValues(
      List<Resource> oldResources, List<Resource> newResources, Spec oldSpec, Spec newSpec) {
    Set<String> lines = new LinkedHashSet<>();

    lines.addAll(generateGlobalEnumLines(oldSpec.globalEnums(), newSpec.globalEnums(), true));
    lines.addAll(generateAttributeEnumLines(oldResources, newResources, true));
    lines.addAll(generateParameterEnumLines(oldResources, newResources, true));

    return new ArrayList<>(lines);
  }

  private List<String> generateDeletedEnumValues(
      List<Resource> oldResources, List<Resource> newResources, Spec oldSpec, Spec newSpec) {
    Set<String> lines = new LinkedHashSet<>();

    lines.addAll(generateGlobalEnumLines(oldSpec.globalEnums(), newSpec.globalEnums(), false));
    lines.addAll(generateAttributeEnumLines(oldResources, newResources, false));
    lines.addAll(generateParameterEnumLines(oldResources, newResources, false));

    return new ArrayList<>(lines);
  }

  private List<String> generateGlobalEnumLines(
      List<Enum> oldEnums, List<Enum> newEnums, boolean isAdded) {
    List<String> lines = new ArrayList<>();
    Map<String, Set<String>> enumValuesMap =
        oldEnums.stream().collect(Collectors.toMap(e -> e.name, e -> new HashSet<>(e.values())));

    for (Enum currentEnum : newEnums) {
      Set<String> comparisonValues =
          enumValuesMap.getOrDefault(currentEnum.name, Collections.emptySet());
      List<String> changedValues =
          findChangedEnumValues(currentEnum.values(), comparisonValues, isAdded);

      if (!changedValues.isEmpty()) {
        String prefix = isAdded ? "" : "from global ";
        lines.add(
            String.format(
                "- %s %senum `%s`.",
                formatValuesList(changedValues, isAdded), prefix, currentEnum.name));
      }
    }

    return lines;
  }

  private List<String> generateAttributeEnumLines(
      List<Resource> oldResources, List<Resource> newResources, boolean isAdded) {
    Map<String, Map<String, Set<String>>> oldEnumMap = buildAttributeEnumMap(oldResources);
    Map<String, Map<String, Set<String>>> newEnumMap = buildAttributeEnumMap(newResources);

    List<String> lines = new ArrayList<>();
    List<Resource> sourceResources = isAdded ? newResources : oldResources;
    Map<String, Map<String, Set<String>>> comparisonMap = isAdded ? oldEnumMap : newEnumMap;

    for (Resource resource : sourceResources) {
      Map<String, Set<String>> comparisonEnums =
          comparisonMap.getOrDefault(resource.id, Collections.emptyMap());
      processAttributeEnumChanges(
          resource, resource.attributes(), "", comparisonEnums, lines, isAdded);
    }

    return lines;
  }

  private void processAttributeEnumChanges(
      Resource resource,
      List<Attribute> attributes,
      String path,
      Map<String, Set<String>> comparisonEnums,
      List<String> lines,
      boolean isAdded) {
    for (Attribute attribute : attributes) {
      String currentPath = buildAttributePath(path, attribute.name);
      String anchorId = buildAttributeAnchor(path, attribute.name);

      if (attribute.isEnumAttribute() && !attribute.isGlobalEnumAttribute()) {
        processEnumAttributeChange(
            resource, attribute, currentPath, anchorId, comparisonEnums, lines, isAdded);
      }

      if (attribute.getSubAttributes() != null) {
        processAttributeEnumChanges(
            resource, attribute.getSubAttributes(), currentPath, comparisonEnums, lines, isAdded);
      }
    }
  }

  private void processEnumAttributeChange(
      Resource resource,
      Attribute attribute,
      String path,
      String anchorId,
      Map<String, Set<String>> comparisonEnums,
      List<String> lines,
      boolean isAdded) {
    Set<String> comparisonValues = comparisonEnums.getOrDefault(path, Collections.emptySet());
    List<String> changedValues =
        findChangedEnumValues(attribute.getEnum().values(), comparisonValues, isAdded);

    if (!changedValues.isEmpty()) {
      String actionVerb = isAdded ? "to" : "from";
      lines.add(
          String.format(
              "- %s %s enum attribute [`%s`](%s#%s) in [`%s`](%s).",
              formatValuesList(changedValues, isAdded),
              actionVerb,
              path,
              getDocsUrlForResourceObject(resource),
              anchorId,
              resource.name,
              getDocsUrlForResourceList(resource)));
    }
  }

  private List<String> generateParameterEnumLines(
      List<Resource> oldResources, List<Resource> newResources, boolean isAdded) {
    List<String> lines = new ArrayList<>();
    Map<String, Map<String, Map<String, Set<String>>>> oldEnumMap =
        collectParameterEnums(oldResources);
    Map<String, Map<String, Map<String, Set<String>>>> newEnumMap =
        collectParameterEnums(newResources);

    List<Resource> sourceResources = isAdded ? newResources : oldResources;
    Map<String, Map<String, Map<String, Set<String>>>> comparisonMap =
        isAdded ? oldEnumMap : newEnumMap;

    for (Resource resource : sourceResources) {
      Map<String, Map<String, Set<String>>> comparisonActions =
          comparisonMap.getOrDefault(resource.id, Collections.emptyMap());

      for (Action action : resource.actions) {
        Map<String, Set<String>> comparisonParameters =
            comparisonActions.getOrDefault(action.id, Collections.emptyMap());

        processParameterEnumChanges(
            resource,
            action,
            action.queryParameters(),
            QUERY_PREFIX,
            comparisonParameters,
            lines,
            isAdded);
        processParameterEnumChanges(
            resource,
            action,
            action.requestBodyParameters(),
            BODY_PREFIX,
            comparisonParameters,
            lines,
            isAdded);
      }
    }

    return lines;
  }

  private void processParameterEnumChanges(
      Resource resource,
      Action action,
      List<Parameter> parameters,
      String prefix,
      Map<String, Set<String>> comparisonParameters,
      List<String> lines,
      boolean isAdded) {
    for (Parameter parameter : parameters) {
      if (shouldProcessParameterEnum(parameter)) {
        processDirectParameterEnum(
            resource, action, parameter, prefix, comparisonParameters, lines, isAdded);
      }

      if (parameter.schema.getProperties() != null) {
        processNestedParameterEnums(
            resource, action, parameter, prefix, comparisonParameters, lines, isAdded);
      }
    }
  }

  private void processDirectParameterEnum(
      Resource resource,
      Action action,
      Parameter parameter,
      String prefix,
      Map<String, Set<String>> comparisonParameters,
      List<String> lines,
      boolean isAdded) {
    String parameterKey = prefix + ":" + parameter.getName();
    Set<String> comparisonValues =
        comparisonParameters.getOrDefault(parameterKey, Collections.emptySet());
    List<String> changedValues =
        findChangedEnumValues(parameter.getEnumValues(), comparisonValues, isAdded);

    if (!changedValues.isEmpty()) {
      String parameterType =
          prefix.equals(QUERY_PREFIX) ? QUERY_PARAMETER_TYPE : REQUEST_BODY_PARAMETER_TYPE;
      addParameterEnumLine(
          lines, changedValues, resource, action, parameter, parameterType, isAdded);
    }
  }

  private void processNestedParameterEnums(
      Resource resource,
      Action action,
      Parameter parameter,
      String prefix,
      Map<String, Set<String>> comparisonParameters,
      List<String> lines,
      boolean isAdded) {
    parameter
        .schema
        .getProperties()
        .forEach(
            (key, schema) -> {
              Schema propertySchema = (Schema) schema;

              if (shouldProcessSchemaEnum(propertySchema)) {
                String nestedParameterName = parameter.getName() + "." + key;
                String parameterKey = prefix + ":" + nestedParameterName;
                Set<String> comparisonValues =
                    comparisonParameters.getOrDefault(parameterKey, Collections.emptySet());
                List<String> changedValues =
                    findChangedEnumValues(
                        new ArrayList<>(getEnumValues(propertySchema)), comparisonValues, isAdded);

                if (!changedValues.isEmpty()) {
                  String parameterType =
                      prefix.equals(QUERY_PREFIX)
                          ? QUERY_PARAMETER_TYPE
                          : REQUEST_BODY_PARAMETER_TYPE;
                  Parameter nestedParameter = new Parameter(nestedParameterName, propertySchema);
                  addParameterEnumLine(
                      lines,
                      changedValues,
                      resource,
                      action,
                      nestedParameter,
                      parameterType,
                      isAdded);
                }
              }
            });
  }

  private void addParameterEnumLine(
      List<String> lines,
      List<String> values,
      Resource resource,
      Action action,
      Parameter parameter,
      String parameterType,
      boolean isAdded) {
    String actionVerb = isAdded ? "to" : "from";
    lines.add(
        String.format(
            "- %s %s enum %s `%s` in [`%s`](%s) of [`%s`](%s).",
            formatValuesList(values, isAdded),
            actionVerb,
            parameterType,
            parameter.getName(),
            action.id,
            getDocsUrlForActions(resource, action),
            resource.name,
            getDocsUrlForResourceList(resource)));
  }

  private List<String> generateParameterRequirementChanges(
      List<Resource> oldResources, List<Resource> newResources) {
    List<String> lines = new ArrayList<>();
    Map<String, Map<String, Map<String, Boolean>>> oldRequirementMap =
        buildParameterRequirementMap(oldResources);

    for (Resource newResource : newResources) {
      Map<String, Map<String, Boolean>> oldActionRequirements =
          oldRequirementMap.getOrDefault(newResource.id, Collections.emptyMap());

      for (Action newAction : newResource.actions) {
        Map<String, Boolean> oldParameterRequirements =
            oldActionRequirements.getOrDefault(newAction.id, Collections.emptyMap());

        checkRequirementChanges(
            newResource,
            newAction,
            newAction.queryParameters(),
            QUERY_PREFIX,
            oldParameterRequirements,
            lines);
        checkRequirementChanges(
            newResource,
            newAction,
            newAction.requestBodyParameters(),
            BODY_PREFIX,
            oldParameterRequirements,
            lines);
      }
    }

    return lines;
  }

  private void checkRequirementChanges(
      Resource resource,
      Action action,
      List<Parameter> parameters,
      String prefix,
      Map<String, Boolean> oldRequirements,
      List<String> lines) {
    for (Parameter parameter : parameters) {
      String parameterKey = prefix + ":" + parameter.getName();

      if (oldRequirements.containsKey(parameterKey)
          && oldRequirements.get(parameterKey) != parameter.isRequired) {
        lines.add(formatRequirementChangeLine(resource, action, parameter));
      }
    }
  }

  private String formatRequirementChangeLine(
      Resource resource, Action action, Parameter parameter) {
    String changeDescription =
        parameter.isRequired ? "optional to required" : "required to optional";
    return String.format(
        "- [`%s`](%s) has been changed from %s in [`%s`](%s) of [`%s`](%s).",
        parameter.getName(),
        getDocsUrlForParameter(resource, action, parameter),
        changeDescription,
        action.id,
        getDocsUrlForActions(resource, action),
        resource.name,
        getDocsUrlForResourceList(resource));
  }

  private Map<String, Set<String>> buildActionMap(List<Resource> resources) {
    return resources.stream()
        .collect(
            Collectors.toMap(
                resource -> resource.id,
                resource ->
                    resource.actions.stream()
                        .map(action -> action.name)
                        .collect(Collectors.toSet())));
  }

  private Map<String, Set<String>> buildAttributeMap(List<Resource> resources) {
    return resources.stream()
        .collect(
            Collectors.toMap(
                resource -> resource.id,
                resource ->
                    resource.attributes().stream()
                        .map(attribute -> attribute.name)
                        .collect(Collectors.toSet())));
  }

  private Map<String, Map<String, Set<String>>> buildParameterMap(
      List<Resource> resources, Function<Action, List<Parameter>> parameterExtractor) {
    Map<String, Map<String, Set<String>>> resourceMap = new HashMap<>();

    for (Resource resource : resources) {
      Map<String, Set<String>> actionMap = new HashMap<>();

      for (Action action : resource.actions) {
        Set<String> parameterNames = collectParameterNames(parameterExtractor.apply(action));
        actionMap.put(action.id, parameterNames);
      }

      resourceMap.put(resource.id, actionMap);
    }

    return resourceMap;
  }

  private Set<String> collectParameterNames(List<Parameter> parameters) {
    Set<String> names = new HashSet<>();

    for (Parameter parameter : parameters) {
      names.add(parameter.getName());
      collectNestedParameterNames(parameter.schema, parameter.getName(), names);
    }

    return names;
  }

  private void collectNestedParameterNames(Schema schema, String path, Set<String> names) {
    if (schema.getProperties() == null) {
      return;
    }

    schema
        .getProperties()
        .forEach(
            (key, value) -> {
              String nestedPath = path + "." + key;
              names.add(nestedPath);
              collectNestedParameterNames((Schema) value, nestedPath, names);
            });
  }

  private Map<String, Map<String, Set<String>>> buildAttributeEnumMap(List<Resource> resources) {
    Map<String, Map<String, Set<String>>> resourceMap = new HashMap<>();

    for (Resource resource : resources) {
      Map<String, Set<String>> enumMap = new HashMap<>();
      collectAttributeEnumsRecursive(resource.attributes(), "", enumMap);
      resourceMap.put(resource.id, enumMap);
    }

    return resourceMap;
  }

  private void collectAttributeEnumsRecursive(
      List<Attribute> attributes, String path, Map<String, Set<String>> enumMap) {
    for (Attribute attribute : attributes) {
      String currentPath = buildAttributePath(path, attribute.name);

      if (attribute.isEnumAttribute() && !attribute.isGlobalEnumAttribute()) {
        enumMap.put(currentPath, new HashSet<>(attribute.getEnum().values()));
      }

      if (attribute.getSubAttributes() != null) {
        collectAttributeEnumsRecursive(attribute.getSubAttributes(), currentPath, enumMap);
      }
    }
  }

  private Map<String, Map<String, Map<String, Set<String>>>> collectParameterEnums(
      List<Resource> resources) {
    Map<String, Map<String, Map<String, Set<String>>>> resourceMap = new HashMap<>();

    for (Resource resource : resources) {
      Map<String, Map<String, Set<String>>> actionMap = new HashMap<>();

      for (Action action : resource.actions) {
        Map<String, Set<String>> enumMap = new HashMap<>();
        collectEnumsFromParameters(action.queryParameters(), QUERY_PREFIX, enumMap);
        collectEnumsFromParameters(action.requestBodyParameters(), BODY_PREFIX, enumMap);
        actionMap.put(action.id, enumMap);
      }

      resourceMap.put(resource.id, actionMap);
    }

    return resourceMap;
  }

  private void collectEnumsFromParameters(
      List<Parameter> parameters, String prefix, Map<String, Set<String>> enumMap) {
    for (Parameter parameter : parameters) {
      if (shouldProcessParameterEnum(parameter)) {
        String key = prefix + ":" + parameter.getName();
        enumMap.put(key, new HashSet<>(parameter.getEnumValues()));
      }

      if (parameter.schema.getProperties() != null) {
        collectEnumsFromNestedParameters(parameter, prefix, enumMap);
      }
    }
  }

  private void collectEnumsFromNestedParameters(
      Parameter parameter, String prefix, Map<String, Set<String>> enumMap) {
    parameter
        .schema
        .getProperties()
        .forEach(
            (key, schema) -> {
              Schema propertySchema = (Schema) schema;

              if (shouldProcessSchemaEnum(propertySchema)) {
                String enumKey = prefix + ":" + parameter.getName() + "." + key;
                enumMap.put(enumKey, getEnumValues(propertySchema));
              }
            });
  }

  private Map<String, Map<String, Map<String, Boolean>>> buildParameterRequirementMap(
      List<Resource> resources) {
    Map<String, Map<String, Map<String, Boolean>>> resourceMap = new HashMap<>();

    for (Resource resource : resources) {
      Map<String, Map<String, Boolean>> actionMap = new HashMap<>();

      for (Action action : resource.actions) {
        Map<String, Boolean> requirementMap = new HashMap<>();
        collectParameterRequirements(action.queryParameters(), QUERY_PREFIX, requirementMap);
        collectParameterRequirements(action.requestBodyParameters(), BODY_PREFIX, requirementMap);
        actionMap.put(action.id, requirementMap);
      }

      resourceMap.put(resource.id, actionMap);
    }

    return resourceMap;
  }

  private void collectParameterRequirements(
      List<Parameter> parameters, String prefix, Map<String, Boolean> requirementMap) {
    for (Parameter parameter : parameters) {
      String key = prefix + ":" + parameter.getName();
      requirementMap.put(key, parameter.isRequired);
    }
  }

  private List<String> findChangedEnumValues(
      List<String> currentValues, Set<String> comparisonValues, boolean isAdded) {
    if (isAdded) {
      return currentValues.stream()
          .filter(value -> !comparisonValues.contains(value))
          .collect(Collectors.toList());
    } else {
      return currentValues.stream()
          .filter(comparisonValues::contains)
          .filter(value -> !currentValues.contains(value))
          .collect(Collectors.toList());
    }
  }

  private Set<String> extractResourceIds(List<Resource> resources) {
    return resources.stream().map(resource -> resource.id).collect(Collectors.toSet());
  }

  private String buildAttributePath(String basePath, String attributeName) {
    return basePath.isEmpty() ? attributeName : basePath + "." + attributeName;
  }

  private String buildAttributeAnchor(String basePath, String attributeName) {
    return basePath.isEmpty() ? attributeName : basePath.replace(".", "_") + "_" + attributeName;
  }

  private String formatValuesList(List<String> values, boolean isAdded) {
    if (values.isEmpty()) {
      return "";
    }

    String singularAction = isAdded ? ADDED_VALUE_SINGULAR : REMOVED_VALUE;
    String pluralAction = isAdded ? ADDED_VALUE_PLURAL : REMOVED_VALUE;

    if (values.size() == 1) {
      return String.format("`%s` has been %s", values.get(0), singularAction);
    }

    if (values.size() == 2) {
      return String.format(
          "`%s` and `%s` have been %s", values.get(0), values.get(1), pluralAction);
    }

    String allButLast =
        values.subList(0, values.size() - 1).stream()
            .map(value -> "`" + value + "`")
            .collect(Collectors.joining(", "));
    String lastValue = values.get(values.size() - 1);

    return String.format("%s, and `%s` have been %s", allButLast, lastValue, pluralAction);
  }

  private boolean shouldProcessParameterEnum(Parameter parameter) {
    return parameter.isEnum()
        && !parameter.isGlobalEnum()
        && !parameter.isExternalEnum()
        && !parameter.isGenSeperate();
  }

  private boolean shouldProcessSchemaEnum(Schema schema) {
    return isEnumSchema(schema)
        && !isGlobalEnumSchema(schema)
        && !isExternalEnumSchema(schema)
        && !isGenSeparateSchema(schema);
  }

  private boolean isEnumSchema(Schema schema) {
    if (schema instanceof ArraySchema) {
      return schema.getItems().getEnum() != null && !schema.getItems().getEnum().isEmpty();
    }
    return schema.getEnum() != null && !schema.getEnum().isEmpty();
  }

  private boolean isGlobalEnumSchema(Schema schema) {
    Schema targetSchema = schema instanceof ArraySchema ? schema.getItems() : schema;
    return hasExtension(targetSchema, IS_GLOBAL_ENUM);
  }

  private boolean isExternalEnumSchema(Schema schema) {
    Schema targetSchema = schema instanceof ArraySchema ? schema.getItems() : schema;
    return hasExtension(targetSchema, IS_EXTERNAL_ENUM);
  }

  private boolean isGenSeparateSchema(Schema schema) {
    Schema targetSchema = schema instanceof ArraySchema ? schema.getItems() : schema;
    return hasExtension(targetSchema, IS_GEN_SEPARATE);
  }

  private boolean hasExtension(Schema schema, String extensionKey) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(extensionKey) != null
        && (boolean) schema.getExtensions().get(extensionKey);
  }

  private Set<String> getEnumValues(Schema schema) {
    if (isGlobalEnumSchema(schema)
        || isExternalEnumSchema(schema)
        || !isEnumSchema(schema)
        || isGenSeparateSchema(schema)) {
      return Collections.emptySet();
    }
    return new HashSet<>(new Enum(schema).values());
  }

  private String toHyphenCase(String text) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, text);
  }

  private String getDocsUrlForEvent(String eventType) {
    return String.format("https://apidocs.chargebee.com/docs/api/events/webhook/%s", eventType);
  }

  private String getDocsUrlForResourceList(Resource resource) {
    return String.format("https://apidocs.chargebee.com/docs/api/%s", pluralize(resource.id));
  }

  private String getDocsUrlForResourceObject(Resource resource) {
    String hyphenatedResourceId = resource.id.replace("_", "-");
    return String.format(
        "https://apidocs.chargebee.com/docs/api/%s/%s-object",
        pluralize(resource.id), hyphenatedResourceId);
  }

  private String getDocsUrlForActions(Resource resource, Action action) {
    return String.format(
        "https://apidocs.chargebee.com/docs/api/%s/%s",
        pluralize(resource.id), toHyphenCase(action.id));
  }

  private String getDocsUrlForAttribute(Resource resource, Attribute attribute) {
    return String.format("%s#%s", getDocsUrlForResourceObject(resource), attribute.name);
  }

  private String getDocsUrlForParameter(Resource resource, Action action, Parameter parameter) {
    String anchorId = parameter.getName().replace(".", "_");
    return String.format(
        "https://apidocs.chargebee.com/docs/api/%s/%s#%s",
        pluralize(resource.id), toHyphenCase(action.id), anchorId);
  }
}
