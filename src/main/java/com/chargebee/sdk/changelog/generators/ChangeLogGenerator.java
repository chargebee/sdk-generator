package com.chargebee.sdk.changelog.generators;

import static com.chargebee.GenUtil.pluralize;
import static com.chargebee.GenUtil.singularize;
import static com.chargebee.sdk.changelog.Constants.*;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.openapi.parameter.Parameter;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.changelog.ChangeLog;
import com.chargebee.sdk.changelog.models.ChangeLogSchema;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
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
}
