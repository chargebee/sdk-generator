package com.chargebee.sdk.changelog.generators;

import static com.chargebee.GenUtil.pluralize;
import static com.chargebee.GenUtil.singularize;
import static com.chargebee.openapi.Extension.IS_GLOBAL_ENUM;
import static com.chargebee.sdk.changelog.Constants.*;

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
  private final ChangeLog changeLogGenerator;
  private final Template changeLogTemplate;

  public ChangeLogGenerator(ChangeLog changeLogGenerator) {
    this.changeLogGenerator = changeLogGenerator;
    this.changeLogTemplate = changeLogGenerator.getTemplateContent(CHANGELOG);
  }

  @Override
  public FileOp generate(String output, Spec oldVersion, Spec newerVersion) throws IOException {
    List<Resource> newResources = filterResources(newerVersion.resources());
    List<Resource> oldResources = filterResources(oldVersion.resources());

    ChangeLogSchema changeLogSchema = new ChangeLogSchema();
    changeLogSchema.setNewResource(generateResourceLine(oldResources, newResources));
    changeLogSchema.setNewActions(generateActionLine(oldResources, newResources));
    changeLogSchema.setNewResourceAttribute(generateAttributeLine(oldResources, newResources));

    List<String> queryParamLines =
        generateParameterLine(
            oldResources, newResources, Action::queryParameters, "query parameter");
    List<String> requestBodyParamLines =
        generateParameterLine(
            oldResources, newResources, Action::requestBodyParameters, "request body parameter");
    List<String> allParamLines =
        Stream.concat(queryParamLines.stream(), requestBodyParamLines.stream())
            .collect(Collectors.toList());
    changeLogSchema.setNewParams(allParamLines);

    changeLogSchema.setNewEventType(
        generateEventLine(
            oldVersion.extractWebhookInfo(false), newerVersion.extractWebhookInfo(false)));
    changeLogSchema.setDeletedResource(generateDeletedResourceLine(oldResources, newResources));
    changeLogSchema.setDeletedActions(generateDeletedActionLine(oldResources, newResources));
    changeLogSchema.setDeletedResourceAttribute(
        generateDeletedAttributeLine(oldResources, newResources));

    List<String> deletedQueryParamLines =
        generateDeletedParameterLine(
            oldResources, newResources, Action::queryParameters, "query parameter");
    List<String> deletedRequestBodyParamLines =
        generateDeletedParameterLine(
            oldResources, newResources, Action::requestBodyParameters, "request body parameter");
    List<String> allDeletedParamLines =
        Stream.concat(deletedQueryParamLines.stream(), deletedRequestBodyParamLines.stream())
            .collect(Collectors.toList());
    changeLogSchema.setDeletedParams(allDeletedParamLines);

    changeLogSchema.setDeletedEventType(
        generateDeletedEventLine(
            oldVersion.extractWebhookInfo(false), newerVersion.extractWebhookInfo(false)));

    changeLogSchema.setNewEnumValues(
        generateEnumValueLines(oldResources, newResources, oldVersion, newerVersion));

    changeLogSchema.setDeletedEnumValues(
        generateDeletedEnumValueLines(oldResources, newResources, oldVersion, newerVersion));

    String content =
        changeLogTemplate.apply(
            changeLogGenerator.getObjectMapper().convertValue(changeLogSchema, Map.class));
    content = content.replaceAll("(?m)^[ \t]*\r?\n([ \t]*\r?\n)+", "\n\n");
    content = content.replaceAll("^\\s+", "");
    return new FileOp.WriteString("./", output + "CHANGELOG.md", content);
  }

  private List<String> generateEventLine(
      List<Map<String, String>> oldEvents, List<Map<String, String>> newEvents) {
    Set<String> oldEventTypes =
        oldEvents.stream().map(event -> event.get("type")).collect(Collectors.toSet());

    return newEvents.stream()
        .filter(event -> !oldEventTypes.contains(event.get("type")))
        .map(event -> convertNewEventToEventLine(event))
        .collect(Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .toList();
  }

  private String convertNewEventToEventLine(Map<String, String> event) {
    String eventType = event.get("type");
    String resourceSchemaName = event.get("resource_schema_name");

    return String.format("- [`%s`](%s) has been added.", eventType, getDocsUrlForEvent(eventType));
  }

  private String getDocsUrlForEvent(String eventType) {
    return String.format("https://apidocs.chargebee.com/docs/api/events/webhook/%s", eventType);
  }

  private String convertNewResourceToResourceLine(Resource r) {
    String resourceName = r.name;
    String message =
        String.format("- [`%s`](%s) has been added.", resourceName, getDocsUrlForResource(r));
    return message;
  }

  private List<String> generateResourceLine(
      List<Resource> oldResources, List<Resource> newResources) {
    Set<String> oldResourceIds =
        oldResources.stream().map(resource -> resource.id).collect(Collectors.toSet());

    Set<String> newResource =
        newResources.stream()
            .filter(r -> !oldResourceIds.contains(r.id))
            .map(r -> convertNewResourceToResourceLine(r))
            .collect(Collectors.toCollection(LinkedHashSet::new));

    return newResource.stream().toList();
  }

  private List<String> generateActionLine(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Set<String>> oldActionsByResourceId =
        oldResources.stream()
            .collect(
                Collectors.toMap(
                    resource -> resource.id,
                    resource ->
                        resource.actions.stream()
                            .map(action -> action.name)
                            .collect(Collectors.toSet())));

    Set<String> newActionLines = new LinkedHashSet<>();

    for (Resource newResource : newResources) {
      Set<String> oldActions =
          oldActionsByResourceId.getOrDefault(newResource.id, Collections.emptySet());
      for (Action action : newResource.actions) {
        if (!oldActions.contains(action.name)) {
          newActionLines.add(convertNewActionToActionLine(newResource, action));
        }
      }
    }
    return new ArrayList<>(newActionLines);
  }

  private List<String> generateAttributeLine(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Set<String>> oldAttributesByResourceId =
        oldResources.stream()
            .collect(
                Collectors.toMap(
                    resource -> resource.id,
                    resource ->
                        resource.attributes().stream()
                            .map(attribute -> attribute.name)
                            .collect(Collectors.toSet())));

    return newResources.stream()
        .flatMap(
            resource ->
                resource.attributes().stream()
                    .filter(
                        attribute ->
                            !oldAttributesByResourceId
                                .getOrDefault(resource.id, Collections.emptySet())
                                .contains(attribute.name))
                    .map(attribute -> convertNewAttributeToAttributeLine(resource, attribute)))
        .collect(Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .toList();
  }

  private String convertNewActionToActionLine(Resource resource, Action action) {
    String resourceName = resource.name;

    return String.format(
        "- [`%s`](%s) has been added to [`%s`](%s).",
        action.id,
        getDocsUrlForActions(resource, action),
        resourceName,
        getDocsUrlForResource(resource));
  }

  private String getDocsUrlForResource(Resource resource) {
    return String.format("https://apidocs.chargebee.com/docs/api/%s", pluralize(resource.id));
  }

  private String getDocsUrlForActions(Resource resource, Action action) {
    String resourcePath = pluralize(resource.id);
    String actionPath = toHyphenCase(action.id);
    return String.format("https://apidocs.chargebee.com/docs/api/%s/%s", resourcePath, actionPath);
  }

  private String convertNewAttributeToAttributeLine(Resource resource, Attribute attribute) {
    String attributeName = attribute.name;
    String resourceName = resource.name;

    return String.format(
        "- [`%s`](%s) has been added to [`%s`](%s).",
        attributeName,
        getDocsUrlForAttribute(resource, attribute),
        resourceName,
        getDocsUrlForResource(resource));
  }

  private String getDocsUrlForAttribute(Resource resource, Attribute attribute) {
    String resourcePath = pluralize(resource.id);
    String attributePath = attribute.name;
    return String.format(
        "https://apidocs.chargebee.com/docs/api/%s/%s-object#%s",
        resourcePath, toHyphenCase(singularize(resource.id)), attributePath);
  }

  private String toHyphenCase(String s) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, s);
  }

  private List<String> generateParameterLine(
      List<Resource> oldResources,
      List<Resource> newResources,
      Function<Action, List<Parameter>> parameterExtractor,
      String parameterType) {

    Map<String, Map<String, Set<String>>> oldParamsByResourceAndAction =
        oldResources.stream()
            .collect(
                Collectors.toMap(
                    resource -> resource.id,
                    resource ->
                        resource.actions.stream()
                            .collect(
                                Collectors.toMap(
                                    action -> action.id,
                                    action ->
                                        parameterExtractor.apply(action).stream()
                                            .map(param -> param.getName())
                                            .collect(Collectors.toSet())))));

    Set<String> newParamLines = new LinkedHashSet<>();

    for (Resource newResource : newResources) {
      Map<String, Set<String>> oldActionsMap =
          oldParamsByResourceAndAction.getOrDefault(newResource.id, Collections.emptyMap());

      for (Action action : newResource.actions) {
        Set<String> oldParams = oldActionsMap.getOrDefault(action.id, Collections.emptySet());

        for (Parameter param : parameterExtractor.apply(action)) {
          if (!oldParams.contains(param.getName())) {
            newParamLines.add(convertNewParameterToLine(newResource, action, param, parameterType));
          }
        }
      }
    }

    return new ArrayList<>(newParamLines);
  }

  private String convertNewParameterToLine(
      Resource resource, Action action, Parameter param, String parameterType) {
    String paramName = param.getName();
    String resourceName = resource.name;

    return String.format(
        "- [`%s`](%s) has been added as %s to [`%s`](%s) in [`%s`](%s).",
        paramName,
        getDocsUrlForParameter(resource, action, param),
        parameterType,
        action.id,
        getDocsUrlForActions(resource, action),
        resourceName,
        getDocsUrlForResource(resource));
  }

  private String getDocsUrlForParameter(Resource resource, Action action, Parameter param) {
    String resourcePath = pluralize(resource.id);
    String actionPath = toHyphenCase(action.id);
    String paramPath = param.getName();
    return String.format(
        "https://apidocs.chargebee.com/docs/api/%s/%s#%s", resourcePath, actionPath, paramPath);
  }

  private List<com.chargebee.openapi.Resource> filterResources(
      List<com.chargebee.openapi.Resource> resources) {
    return resources.stream()
        .filter(resource -> !List.of(this.changeLogGenerator.hiddenOverride).contains(resource.id))
        .collect(Collectors.toList());
  }

  private List<String> generateDeletedResourceLine(
      List<Resource> oldResources, List<Resource> newResources) {
    Set<String> newResourceIds =
        newResources.stream().map(resource -> resource.id).collect(Collectors.toSet());

    Set<String> deletedResource =
        oldResources.stream()
            .filter(r -> !newResourceIds.contains(r.id))
            .map(r -> convertDeletedResourceToResourceLine(r))
            .collect(Collectors.toCollection(LinkedHashSet::new));

    return deletedResource.stream().toList();
  }

  private String convertDeletedResourceToResourceLine(Resource r) {
    String resourceName = r.name;
    return String.format("- %s has been removed.", resourceName);
  }

  private List<String> generateDeletedActionLine(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Resource> newResourcesMap =
        newResources.stream()
            .collect(Collectors.toMap(resource -> resource.id, resource -> resource));

    Set<String> deletedActionLines = new LinkedHashSet<>();

    for (Resource oldResource : oldResources) {
      Resource newResource = newResourcesMap.get(oldResource.id);

      if (newResource != null) {
        Set<String> newActions =
            newResource.actions.stream().map(action -> action.name).collect(Collectors.toSet());

        for (Action action : oldResource.actions) {
          if (!newActions.contains(action.name)) {
            deletedActionLines.add(convertDeletedActionToActionLine(newResource, action, true));
          }
        }
      } else {
        for (Action action : oldResource.actions) {
          deletedActionLines.add(convertDeletedActionToActionLine(oldResource, action, false));
        }
      }
    }
    return new ArrayList<>(deletedActionLines);
  }

  private String convertDeletedActionToActionLine(
      Resource resource, Action action, boolean hyperlinkResource) {
    String resourceName = resource.name;

    if (hyperlinkResource) {
      return String.format(
          "- `%s` has been removed from [`%s`](%s).",
          action.id, resourceName, getDocsUrlForResource(resource));
    } else {
      return String.format("- `%s` has been removed from `%s`.", action.id, resourceName);
    }
  }

  private List<String> generateDeletedAttributeLine(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Resource> newResourcesMap =
        newResources.stream()
            .collect(Collectors.toMap(resource -> resource.id, resource -> resource));

    Set<String> deletedAttributeLines = new LinkedHashSet<>();

    for (Resource oldResource : oldResources) {
      Resource newResource = newResourcesMap.get(oldResource.id);

      if (newResource != null) {
        Set<String> newAttributes =
            newResource.attributes().stream()
                .map(attribute -> attribute.name)
                .collect(Collectors.toSet());

        for (Attribute attribute : oldResource.attributes()) {
          if (!newAttributes.contains(attribute.name)) {
            deletedAttributeLines.add(
                convertDeletedAttributeToAttributeLine(newResource, attribute, true));
          }
        }
      } else {
        for (Attribute attribute : oldResource.attributes()) {
          deletedAttributeLines.add(
              convertDeletedAttributeToAttributeLine(oldResource, attribute, false));
        }
      }
    }

    return new ArrayList<>(deletedAttributeLines);
  }

  private String convertDeletedAttributeToAttributeLine(
      Resource resource, Attribute attribute, boolean hyperlinkResource) {
    String attributeName = attribute.name;
    String resourceName = resource.name;

    if (hyperlinkResource) {
      return String.format(
          "- `%s` has been removed from [`%s`](%s).",
          attributeName, resourceName, getDocsUrlForResource(resource));
    } else {
      return String.format("- `%s` has been removed from `%s`.", attributeName, resourceName);
    }
  }

  private List<String> generateDeletedParameterLine(
      List<Resource> oldResources,
      List<Resource> newResources,
      Function<Action, List<Parameter>> parameterExtractor,
      String parameterType) {

    Map<String, Resource> newResourcesMap =
        newResources.stream()
            .collect(Collectors.toMap(resource -> resource.id, resource -> resource));

    Set<String> deletedParamLines = new LinkedHashSet<>();

    for (Resource oldResource : oldResources) {
      Resource newResource = newResourcesMap.get(oldResource.id);

      if (newResource != null) {
        Map<String, Action> newActionsMap =
            newResource.actions.stream()
                .collect(Collectors.toMap(action -> action.id, action -> action));

        for (Action oldAction : oldResource.actions) {
          Action newAction = newActionsMap.get(oldAction.id);

          if (newAction != null) {
            Set<String> newParams =
                parameterExtractor.apply(newAction).stream()
                    .map(param -> param.getName())
                    .collect(Collectors.toSet());

            for (Parameter param : parameterExtractor.apply(oldAction)) {
              if (!newParams.contains(param.getName())) {
                deletedParamLines.add(
                    convertDeletedParameterToLine(
                        newResource, newAction, param, parameterType, true, true));
              }
            }
          } else {
            for (Parameter param : parameterExtractor.apply(oldAction)) {
              deletedParamLines.add(
                  convertDeletedParameterToLine(
                      newResource, oldAction, param, parameterType, true, false));
            }
          }
        }
      } else {
        for (Action action : oldResource.actions) {
          for (Parameter param : parameterExtractor.apply(action)) {
            deletedParamLines.add(
                convertDeletedParameterToLine(
                    oldResource, action, param, parameterType, false, false));
          }
        }
      }
    }

    return new ArrayList<>(deletedParamLines);
  }

  private String convertDeletedParameterToLine(
      Resource resource,
      Action action,
      Parameter param,
      String parameterType,
      boolean hyperlinkResource,
      boolean hyperlinkAction) {
    String paramName = param.getName();
    String resourceName = resource.name;

    if (hyperlinkResource && hyperlinkAction) {
      return String.format(
          "- `%s` has been removed as %s from [`%s`](%s) in [`%s`](%s).",
          paramName,
          parameterType,
          action.id,
          getDocsUrlForActions(resource, action),
          resourceName,
          getDocsUrlForResource(resource));
    } else if (hyperlinkResource && !hyperlinkAction) {
      return String.format(
          "- `%s` has been removed as %s from `%s` in [`%s`](%s).",
          paramName, parameterType, action.id, resourceName, getDocsUrlForResource(resource));
    } else {
      return String.format(
          "- `%s` has been removed as %s from `%s` in `%s`.",
          paramName, parameterType, action.id, resourceName);
    }
  }

  private List<String> generateDeletedEventLine(
      List<Map<String, String>> oldEvents, List<Map<String, String>> newEvents) {
    Set<String> newEventTypes =
        newEvents.stream().map(event -> event.get("type")).collect(Collectors.toSet());

    return oldEvents.stream()
        .filter(event -> !newEventTypes.contains(event.get("type")))
        .map(event -> convertDeletedEventToEventLine(event))
        .collect(Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .toList();
  }

  private String convertDeletedEventToEventLine(Map<String, String> event) {
    String eventType = event.get("type");
    return String.format("- `%s` has been removed.", eventType);
  }

  /**
   * Generate lines for new enum values (both global and attribute-level enums)
   */
  private List<String> generateEnumValueLines(
      List<Resource> oldResources, List<Resource> newResources, Spec oldSpec, Spec newSpec) {

    Set<String> enumLines = new LinkedHashSet<>();

    // Handle global enums
    enumLines.addAll(generateGlobalEnumLines(oldSpec.globalEnums(), newSpec.globalEnums()));

    // Handle attribute-level enums (excluding global enums)
    enumLines.addAll(generateAttributeEnumLines(oldResources, newResources));

    // Handle parameter enums (both query and request body)
    enumLines.addAll(generateParameterEnumLines(oldResources, newResources));

    return new ArrayList<>(enumLines);
  }

  /**
   * Generate lines for global enum changes (grouped by enum name)
   */
  private List<String> generateGlobalEnumLines(
      List<Enum> oldGlobalEnums, List<Enum> newGlobalEnums) {

    List<String> lines = new ArrayList<>();

    // Build map of old enum values
    Map<String, Set<String>> oldEnumValuesMap =
        oldGlobalEnums.stream()
            .collect(Collectors.toMap(e -> e.name, e -> new HashSet<>(e.values())));

    // Find new values in each global enum and group them
    for (Enum newEnum : newGlobalEnums) {
      Set<String> oldValues = oldEnumValuesMap.getOrDefault(newEnum.name, Collections.emptySet());

      List<String> newValues = new ArrayList<>();
      for (String value : newEnum.values()) {
        if (!oldValues.contains(value)) {
          newValues.add(value);
        }
      }

      if (!newValues.isEmpty()) {
        lines.add(formatEnumValuesLine(newValues, newEnum.name, true));
      }
    }

    return lines;
  }

  /**
   * Format enum values into a single line
   */
  private String formatEnumValuesLine(List<String> values, String enumName, boolean isGlobal) {
    if (values.isEmpty()) {
      return "";
    }

    String valuesStr;
    if (values.size() == 1) {
      valuesStr = String.format("`%s` has been added as a new value", values.get(0));
    } else if (values.size() == 2) {
      valuesStr =
          String.format(
              "`%s` and `%s` have been added as new values", values.get(0), values.get(1));
    } else {
      String lastValue = values.get(values.size() - 1);
      String otherValues =
          values.subList(0, values.size() - 1).stream()
              .map(v -> String.format("`%s`", v))
              .collect(Collectors.joining(", "));
      valuesStr =
          String.format("%s, and `%s` have been added as new values", otherValues, lastValue);
    }

    return String.format("- %s to global enum `%s`.", valuesStr, enumName);
  }

  /**
   * Generate lines for attribute-level enum changes (excluding global enums)
   * Grouped by resource and attribute
   */
  private List<String> generateAttributeEnumLines(
      List<Resource> oldResources, List<Resource> newResources) {

    // Build map of old resource attributes and their enum values
    Map<String, Map<String, Set<String>>> oldEnumsByResource = new HashMap<>();

    for (Resource oldResource : oldResources) {
      Map<String, Set<String>> attributeEnums = new HashMap<>();
      for (Attribute attr : oldResource.attributes()) {
        // Only process non-global enum attributes
        if (attr.isEnumAttribute() && !attr.isGlobalEnumAttribute()) {
          attributeEnums.put(attr.name, new HashSet<>(attr.getEnum().values()));
        }
      }
      oldEnumsByResource.put(oldResource.id, attributeEnums);
    }

    // Check for new enum values in new resources
    List<String> lines = new ArrayList<>();

    for (Resource newResource : newResources) {
      Map<String, Set<String>> oldAttributeEnums =
          oldEnumsByResource.getOrDefault(newResource.id, Collections.emptyMap());

      for (Attribute newAttr : newResource.attributes()) {
        // Only process non-global enum attributes
        if (newAttr.isEnumAttribute() && !newAttr.isGlobalEnumAttribute()) {
          Set<String> oldValues =
              oldAttributeEnums.getOrDefault(newAttr.name, Collections.emptySet());
          List<String> newEnumValues = newAttr.getEnum().values();

          // Find new enum values
          List<String> addedValues = new ArrayList<>();
          for (String value : newEnumValues) {
            if (!oldValues.contains(value)) {
              addedValues.add(value);
            }
          }

          if (!addedValues.isEmpty()) {
            lines.add(formatAttributeEnumValuesLine(addedValues, newResource, newAttr));
          }
        }
      }
    }

    return lines;
  }

  /**
   * Format attribute enum values into a single line
   */
  private String formatAttributeEnumValuesLine(
      List<String> values, Resource resource, Attribute attribute) {

    if (values.isEmpty()) {
      return "";
    }

    String valuesStr;
    if (values.size() == 1) {
      valuesStr = String.format("`%s` has been added as a new value", values.get(0));
    } else if (values.size() == 2) {
      valuesStr =
          String.format(
              "`%s` and `%s` have been added as new values", values.get(0), values.get(1));
    } else {
      String lastValue = values.get(values.size() - 1);
      String otherValues =
          values.subList(0, values.size() - 1).stream()
              .map(v -> String.format("`%s`", v))
              .collect(Collectors.joining(", "));
      valuesStr =
          String.format("%s, and `%s` have been added as new values", otherValues, lastValue);
    }

    return String.format(
        "- %s to enum attribute [`%s`](%s) in [`%s`](%s).",
        valuesStr,
        attribute.name,
        getDocsUrlForAttribute(resource, attribute),
        resource.name,
        getDocsUrlForResource(resource));
  }

  /**
   * Generate lines for parameter enum changes
   * Handles both query parameters and request body parameters
   */
  private List<String> generateParameterEnumLines(
      List<Resource> oldResources, List<Resource> newResources) {

    List<String> lines = new ArrayList<>();

    // Build map of old parameters and their enum values
    Map<String, Map<String, Map<String, Set<String>>>> oldParamEnumsByResource = new HashMap<>();

    for (Resource oldResource : oldResources) {
      Map<String, Map<String, Set<String>>> actionParams = new HashMap<>();

      for (Action action : oldResource.actions) {
        Map<String, Set<String>> paramEnums = new HashMap<>();

        // Process query parameters
        for (Parameter param : action.queryParameters()) {
          if (param.isEnum() && !param.isGlobalEnum()) {
            paramEnums.put("query:" + param.getName(), new HashSet<>(param.getEnumValues()));
          }
          if (param.schema.getProperties() != null) {
            param
                .schema
                .getProperties()
                .forEach(
                    (key, value) -> {
                      if (isEnumSchema(value) && !isGlobalEnumSchema(value)) {
                        paramEnums.put(
                            "query:" + param.getName() + "." + key, getEnumValues(value));
                      }
                    });
          }
        }

        // Process request body parameters
        for (Parameter param : action.requestBodyParameters()) {
          if (param.isEnum() && !param.isGlobalEnum()) {
            paramEnums.put("body:" + param.getName(), new HashSet<>(param.getEnumValues()));
          }
          if (param.schema.getProperties() != null) {
            param
                .schema
                .getProperties()
                .forEach(
                    (key, value) -> {
                      if (isEnumSchema(value) && !isGlobalEnumSchema(value)) {
                        paramEnums.put("body:" + param.getName() + "." + key, getEnumValues(value));
                      }
                    });
          }
        }

        actionParams.put(action.id, paramEnums);
      }

      oldParamEnumsByResource.put(oldResource.id, actionParams);
    }

    // Check for new enum values in parameters
    for (Resource newResource : newResources) {
      Map<String, Map<String, Set<String>>> oldActionParams =
          oldParamEnumsByResource.getOrDefault(newResource.id, Collections.emptyMap());

      for (Action action : newResource.actions) {
        Map<String, Set<String>> oldParamEnums =
            oldActionParams.getOrDefault(action.id, Collections.emptyMap());

        // Process query parameters
        for (Parameter param : action.queryParameters()) {
          Set<String> addedValues = new HashSet<>();

          if (param.isEnum() && !param.isGlobalEnum()) {
            Set<String> oldValues =
                oldParamEnums.getOrDefault("query:" + param.getName(), Collections.emptySet());
            for (String value : param.getEnumValues()) {
              if (!oldValues.contains(value)) {
                addedValues.add(value);
              }
            }

            if (!addedValues.isEmpty()) {
              lines.add(
                  formatParameterEnumValuesLine(
                      addedValues.stream().toList(),
                      newResource,
                      action,
                      param,
                      "query parameter"));
            }
          }

          // Process nested properties separately
          if (param.schema.getProperties() != null) {
            param
                .schema
                .getProperties()
                .forEach(
                    (key, value) -> {
                      if (isEnumSchema(value) && !isGlobalEnumSchema(value)) {
                        Set<String> nestedAddedValues = new HashSet<>();
                        Set<String> oldValues =
                            oldParamEnums.getOrDefault(
                                "query:" + param.getName() + "." + key, Collections.emptySet());
                        for (String enumValue : getEnumValues(value)) {
                          if (!oldValues.contains(enumValue)) {
                            nestedAddedValues.add(enumValue);
                          }
                        }

                        if (!nestedAddedValues.isEmpty()) {
                          // Create a parameter with the full nested path
                          Parameter nestedParam = new Parameter(param.getName() + "." + key, value);
                          lines.add(
                              formatParameterEnumValuesLine(
                                  nestedAddedValues.stream().toList(),
                                  newResource,
                                  action,
                                  nestedParam,
                                  "query parameter"));
                        }
                      }
                    });
          }
        }

        // Process request body parameters
        for (Parameter param : action.requestBodyParameters()) {
          Set<String> addedValues = new HashSet<>();

          if (param.isEnum() && !param.isGlobalEnum()) {
            Set<String> oldValues =
                oldParamEnums.getOrDefault("body:" + param.getName(), Collections.emptySet());
            for (String value : param.getEnumValues()) {
              if (!oldValues.contains(value)) {
                addedValues.add(value);
              }
            }

            if (!addedValues.isEmpty()) {
              lines.add(
                  formatParameterEnumValuesLine(
                      addedValues.stream().toList(),
                      newResource,
                      action,
                      param,
                      "request body parameter"));
            }
          }

          // Process nested properties separately
          if (param.schema.getProperties() != null) {
            param
                .schema
                .getProperties()
                .forEach(
                    (key, value) -> {
                      if (isEnumSchema(value) && !isGlobalEnumSchema(value)) {
                        Set<String> nestedAddedValues = new HashSet<>();
                        Set<String> oldValues =
                            oldParamEnums.getOrDefault(
                                "body:" + param.getName() + "." + key, Collections.emptySet());
                        for (String enumValue : getEnumValues(value)) {
                          if (!oldValues.contains(enumValue)) {
                            nestedAddedValues.add(enumValue);
                          }
                        }

                        if (!nestedAddedValues.isEmpty()) {
                          // Create a parameter with the full nested path
                          Parameter nestedParam = new Parameter(param.getName() + "." + key, value);
                          lines.add(
                              formatParameterEnumValuesLine(
                                  nestedAddedValues.stream().toList(),
                                  newResource,
                                  action,
                                  nestedParam,
                                  "request body parameter"));
                        }
                      }
                    });
          }
        }
      }
    }

    return lines;
  }

  /**
   * Format parameter enum values into a single line
   */
  private String formatParameterEnumValuesLine(
      List<String> values,
      Resource resource,
      Action action,
      Parameter param,
      String parameterType) {

    if (values.isEmpty()) {
      return "";
    }

    String valuesStr;
    if (values.size() == 1) {
      valuesStr = String.format("`%s` has been added as a new value", values.get(0));
    } else if (values.size() == 2) {
      valuesStr =
          String.format(
              "`%s` and `%s` have been added as new values", values.get(0), values.get(1));
    } else {
      String lastValue = values.get(values.size() - 1);
      String otherValues =
          values.subList(0, values.size() - 1).stream()
              .map(v -> String.format("`%s`", v))
              .collect(Collectors.joining(", "));
      valuesStr =
          String.format("%s, and `%s` have been added as new values", otherValues, lastValue);
    }

    return String.format(
        "- %s to enum %s `%s` in [`%s`](%s) of [`%s`](%s).",
        valuesStr,
        parameterType,
        param.getName(),
        action.id,
        getDocsUrlForActions(resource, action),
        resource.name,
        getDocsUrlForResource(resource));
  }

  /**
   * Generate lines for deleted enum values
   */
  private List<String> generateDeletedEnumValueLines(
      List<Resource> oldResources, List<Resource> newResources, Spec oldSpec, Spec newSpec) {

    Set<String> enumLines = new LinkedHashSet<>();

    // Handle global enums
    enumLines.addAll(generateDeletedGlobalEnumLines(oldSpec.globalEnums(), newSpec.globalEnums()));

    // Handle attribute-level enums (excluding global enums)
    enumLines.addAll(generateDeletedAttributeEnumLines(oldResources, newResources));

    // Handle parameter enums
    enumLines.addAll(generateDeletedParameterEnumLines(oldResources, newResources));

    return new ArrayList<>(enumLines);
  }

  /**
   * Generate lines for deleted global enum values (grouped)
   */
  private List<String> generateDeletedGlobalEnumLines(
      List<Enum> oldGlobalEnums, List<Enum> newGlobalEnums) {

    List<String> lines = new ArrayList<>();

    // Build map of new enum values
    Map<String, Set<String>> newEnumValuesMap =
        newGlobalEnums.stream()
            .collect(Collectors.toMap(e -> e.name, e -> new HashSet<>(e.values())));

    // Find deleted values in each global enum and group them
    for (Enum oldEnum : oldGlobalEnums) {
      Set<String> newValues = newEnumValuesMap.getOrDefault(oldEnum.name, Collections.emptySet());

      List<String> deletedValues = new ArrayList<>();
      for (String value : oldEnum.values()) {
        if (!newValues.contains(value)) {
          deletedValues.add(value);
        }
      }

      if (!deletedValues.isEmpty()) {
        lines.add(formatDeletedEnumValuesLine(deletedValues, oldEnum.name));
      }
    }

    return lines;
  }

  /**
   * Format deleted enum values into a single line
   */
  private String formatDeletedEnumValuesLine(List<String> values, String enumName) {
    if (values.isEmpty()) {
      return "";
    }

    String valuesStr;
    if (values.size() == 1) {
      valuesStr = String.format("`%s` has been removed", values.get(0));
    } else if (values.size() == 2) {
      valuesStr = String.format("`%s` and `%s` have been removed", values.get(0), values.get(1));
    } else {
      String lastValue = values.get(values.size() - 1);
      String otherValues =
          values.subList(0, values.size() - 1).stream()
              .map(v -> String.format("`%s`", v))
              .collect(Collectors.joining(", "));
      valuesStr = String.format("%s, and `%s` have been removed", otherValues, lastValue);
    }

    return String.format("- %s from global enum `%s`.", valuesStr, enumName);
  }

  /**
   * Generate lines for deleted attribute-level enum values (grouped, excluding global enums)
   */
  private List<String> generateDeletedAttributeEnumLines(
      List<Resource> oldResources, List<Resource> newResources) {

    // Build map of new resource attributes and their enum values
    Map<String, Map<String, Set<String>>> newEnumsByResource = new HashMap<>();

    for (Resource newResource : newResources) {
      Map<String, Set<String>> attributeEnums = new HashMap<>();
      for (Attribute attr : newResource.attributes()) {
        // Only process non-global enum attributes
        if (attr.isEnumAttribute() && !attr.isGlobalEnumAttribute()) {
          attributeEnums.put(attr.name, new HashSet<>(attr.getEnum().values()));
        }
      }
      newEnumsByResource.put(newResource.id, attributeEnums);
    }

    // Check for deleted enum values
    List<String> lines = new ArrayList<>();

    for (Resource oldResource : oldResources) {
      Map<String, Set<String>> newAttributeEnums =
          newEnumsByResource.getOrDefault(oldResource.id, Collections.emptyMap());

      for (Attribute oldAttr : oldResource.attributes()) {
        // Only process non-global enum attributes
        if (oldAttr.isEnumAttribute() && !oldAttr.isGlobalEnumAttribute()) {
          Set<String> newValues =
              newAttributeEnums.getOrDefault(oldAttr.name, Collections.emptySet());
          List<String> oldEnumValues = oldAttr.getEnum().values();

          // Find deleted enum values
          List<String> deletedValues = new ArrayList<>();
          for (String value : oldEnumValues) {
            if (!newValues.contains(value)) {
              deletedValues.add(value);
            }
          }

          if (!deletedValues.isEmpty()) {
            lines.add(formatDeletedAttributeEnumValuesLine(deletedValues, oldResource, oldAttr));
          }
        }
      }
    }

    return lines;
  }

  /**
   * Format deleted attribute enum values into a single line
   */
  private String formatDeletedAttributeEnumValuesLine(
      List<String> values, Resource resource, Attribute attribute) {

    if (values.isEmpty()) {
      return "";
    }

    String valuesStr;
    if (values.size() == 1) {
      valuesStr = String.format("`%s` has been removed", values.get(0));
    } else if (values.size() == 2) {
      valuesStr = String.format("`%s` and `%s` have been removed", values.get(0), values.get(1));
    } else {
      String lastValue = values.get(values.size() - 1);
      String otherValues =
          values.subList(0, values.size() - 1).stream()
              .map(v -> String.format("`%s`", v))
              .collect(Collectors.joining(", "));
      valuesStr = String.format("%s, and `%s` have been removed", otherValues, lastValue);
    }

    return String.format(
        "- %s from enum attribute [`%s`](%s) in [`%s`](%s).",
        valuesStr,
        attribute.name,
        getDocsUrlForAttribute(resource, attribute),
        resource.name,
        getDocsUrlForResource(resource));
  }

  /**
   * Generate lines for deleted parameter enum values
   */
  private List<String> generateDeletedParameterEnumLines(
      List<Resource> oldResources, List<Resource> newResources) {

    List<String> lines = new ArrayList<>();

    // Build map of new parameters and their enum values
    Map<String, Map<String, Map<String, Set<String>>>> newParamEnumsByResource = new HashMap<>();

    for (Resource newResource : newResources) {
      Map<String, Map<String, Set<String>>> actionParams = new HashMap<>();

      for (Action action : newResource.actions) {
        Map<String, Set<String>> paramEnums = new HashMap<>();

        // Process query parameters
        for (Parameter param : action.queryParameters()) {
          if (param.isEnum() && !param.isGlobalEnum()) {
            paramEnums.put("query:" + param.getName(), new HashSet<>(param.getEnumValues()));
          }
        }

        // Process request body parameters
        for (Parameter param : action.requestBodyParameters()) {
          if (param.isEnum() && !param.isGlobalEnum()) {
            paramEnums.put("body:" + param.getName(), new HashSet<>(param.getEnumValues()));
          }
        }

        actionParams.put(action.id, paramEnums);
      }

      newParamEnumsByResource.put(newResource.id, actionParams);
    }

    // Check for deleted enum values in parameters
    for (Resource oldResource : oldResources) {
      Map<String, Map<String, Set<String>>> newActionParams =
          newParamEnumsByResource.getOrDefault(oldResource.id, Collections.emptyMap());

      for (Action action : oldResource.actions) {
        Map<String, Set<String>> newParamEnums =
            newActionParams.getOrDefault(action.id, Collections.emptyMap());

        // Process query parameters
        for (Parameter param : action.queryParameters()) {
          if (param.isEnum() && !param.isGlobalEnum()) {
            Set<String> newValues =
                newParamEnums.getOrDefault("query:" + param.getName(), Collections.emptySet());

            List<String> deletedValues = new ArrayList<>();
            for (String value : param.getEnumValues()) {
              if (!newValues.contains(value)) {
                deletedValues.add(value);
              }
            }

            if (!deletedValues.isEmpty()) {
              lines.add(
                  formatDeletedParameterEnumValuesLine(
                      deletedValues, oldResource, action, param, "query parameter"));
            }
          }
        }

        // Process request body parameters
        for (Parameter param : action.requestBodyParameters()) {
          if (param.isEnum() && !param.isGlobalEnum()) {
            Set<String> newValues =
                newParamEnums.getOrDefault("body:" + param.getName(), Collections.emptySet());

            List<String> deletedValues = new ArrayList<>();
            for (String value : param.getEnumValues()) {
              if (!newValues.contains(value)) {
                deletedValues.add(value);
              }
            }

            if (!deletedValues.isEmpty()) {
              lines.add(
                  formatDeletedParameterEnumValuesLine(
                      deletedValues, oldResource, action, param, "request body parameter"));
            }
          }
        }
      }
    }

    return lines;
  }

  /**
   * Format deleted parameter enum values into a single line
   */
  private String formatDeletedParameterEnumValuesLine(
      List<String> values,
      Resource resource,
      Action action,
      Parameter param,
      String parameterType) {

    if (values.isEmpty()) {
      return "";
    }

    String valuesStr;
    if (values.size() == 1) {
      valuesStr = String.format("`%s` has been removed", values.get(0));
    } else if (values.size() == 2) {
      valuesStr = String.format("`%s` and `%s` have been removed", values.get(0), values.get(1));
    } else {
      String lastValue = values.get(values.size() - 1);
      String otherValues =
          values.subList(0, values.size() - 1).stream()
              .map(v -> String.format("`%s`", v))
              .collect(Collectors.joining(", "));
      valuesStr = String.format("%s, and `%s` have been removed", otherValues, lastValue);
    }

    return String.format(
        "- %s from enum %s [`%s`](%s) in [`%s`](%s) of [`%s`](%s).",
        valuesStr,
        parameterType,
        param.getName(),
        getDocsUrlForParameter(resource, action, param),
        action.id,
        getDocsUrlForActions(resource, action),
        resource.name,
        getDocsUrlForResource(resource));
  }

  public boolean isEnumSchema(Schema schema) {
    return schema instanceof ArraySchema
        ? schema.getItems().getEnum() != null && !schema.getItems().getEnum().isEmpty()
        : schema.getEnum() != null && !schema.getEnum().isEmpty();
  }

  public boolean isGlobalEnumSchema(Schema schema) {
    return schema instanceof ArraySchema
        ? isGlobalEnumAttribute(schema.getItems())
        : isGlobalEnumAttribute(schema);
  }

  private boolean isGlobalEnumAttribute(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_GLOBAL_ENUM) != null
        && (boolean) schema.getExtensions().get(IS_GLOBAL_ENUM);
  }

  public Set<String> getEnumValues(Schema schema) {
    if (isGlobalEnumSchema(schema)) {
      return Collections.emptySet();
    }
    if (!isEnumSchema(schema)) {
      return Collections.emptySet();
    }
    return new HashSet<>(new Enum(schema).values());
  }
}
