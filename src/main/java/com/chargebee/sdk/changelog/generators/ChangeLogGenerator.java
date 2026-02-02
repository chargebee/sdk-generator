package com.chargebee.sdk.changelog.generators;

import static com.chargebee.GenUtil.pluralize;
import static com.chargebee.GenUtil.singularize;
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
    changeLogSchema.setNewParams(
        Stream.concat(queryParamLines.stream(), requestBodyParamLines.stream())
            .collect(Collectors.toList()));

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
    changeLogSchema.setDeletedParams(
        Stream.concat(deletedQueryParamLines.stream(), deletedRequestBodyParamLines.stream())
            .collect(Collectors.toList()));

    changeLogSchema.setDeletedEventType(
        generateDeletedEventLine(
            oldVersion.extractWebhookInfo(false), newerVersion.extractWebhookInfo(false)));

    changeLogSchema.setNewEnumValues(
        generateEnumValueLines(oldResources, newResources, oldVersion, newerVersion));
    changeLogSchema.setDeletedEnumValues(
        generateDeletedEnumValueLines(oldResources, newResources, oldVersion, newerVersion));

    changeLogSchema.setParameterRequirementChangesValues(
        generateParameterRequirementChanges(oldResources, newResources));

    String content =
        changeLogTemplate.apply(
            changeLogGenerator.getObjectMapper().convertValue(changeLogSchema, Map.class));
    content = content.replaceAll("(?m)^[ \t]*\r?\n([ \t]*\r?\n)+", "\n\n");
    content = content.replaceAll("^\\s+", "");
    return new FileOp.WriteString("./", output + "CHANGELOG.md", content);
  }

  private List<Resource> filterResources(List<Resource> resources) {
    return resources.stream()
        .filter(resource -> !List.of(this.changeLogGenerator.hiddenOverride).contains(resource.id))
        .collect(Collectors.toList());
  }

  private List<String> generateEventLine(
      List<Map<String, String>> oldEvents, List<Map<String, String>> newEvents) {
    Set<String> oldEventTypes =
        oldEvents.stream().map(event -> event.get("type")).collect(Collectors.toSet());

    return newEvents.stream()
        .filter(event -> !oldEventTypes.contains(event.get("type")))
        .map(
            event -> {
              String type = event.get("type");
              return String.format("- [`%s`](%s) has been added.", type, getDocsUrlForEvent(type));
            })
        .distinct()
        .collect(Collectors.toList());
  }

  private List<String> generateResourceLine(
      List<Resource> oldResources, List<Resource> newResources) {
    Set<String> oldResourceIds =
        oldResources.stream().map(resource -> resource.id).collect(Collectors.toSet());

    return newResources.stream()
        .filter(r -> !oldResourceIds.contains(r.id))
        .map(r -> String.format("- [`%s`](%s) has been added.", r.name, getDocsUrlForResource(r)))
        .distinct()
        .collect(Collectors.toList());
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
          newActionLines.add(
              String.format(
                  "- [`%s`](%s) has been added to [`%s`](%s).",
                  action.id,
                  getDocsUrlForActions(newResource, action),
                  newResource.name,
                  getDocsUrlForResource(newResource)));
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
                    .map(
                        attribute ->
                            String.format(
                                "- [`%s`](%s) has been added to [`%s`](%s).",
                                attribute.name,
                                getDocsUrlForAttribute(resource, attribute),
                                resource.name,
                                getDocsUrlForResource(resource))))
        .distinct()
        .collect(Collectors.toList());
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
                                            .map(Parameter::getName)
                                            .collect(Collectors.toSet())))));

    Set<String> newParamLines = new LinkedHashSet<>();
    for (Resource newResource : newResources) {
      Map<String, Set<String>> oldActionsMap =
          oldParamsByResourceAndAction.getOrDefault(newResource.id, Collections.emptyMap());
      for (Action action : newResource.actions) {
        Set<String> oldParams = oldActionsMap.getOrDefault(action.id, Collections.emptySet());
        for (Parameter param : parameterExtractor.apply(action)) {
          if (!oldParams.contains(param.getName())) {
            newParamLines.add(
                String.format(
                    "- [`%s`](%s) has been added as %s to [`%s`](%s) in [`%s`](%s).",
                    param.getName(),
                    getDocsUrlForParameter(newResource, action, param),
                    parameterType,
                    action.id,
                    getDocsUrlForActions(newResource, action),
                    newResource.name,
                    getDocsUrlForResource(newResource)));
          }
        }
      }
    }
    return new ArrayList<>(newParamLines);
  }

  private List<String> generateDeletedResourceLine(
      List<Resource> oldResources, List<Resource> newResources) {
    Set<String> newResourceIds =
        newResources.stream().map(resource -> resource.id).collect(Collectors.toSet());

    return oldResources.stream()
        .filter(r -> !newResourceIds.contains(r.id))
        .map(r -> String.format("- %s has been removed.", r.name))
        .distinct()
        .collect(Collectors.toList());
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
    if (hyperlinkResource) {
      return String.format(
          "- `%s` has been removed from [`%s`](%s).",
          action.id, resource.name, getDocsUrlForResource(resource));
    }
    return String.format("- `%s` has been removed from `%s`.", action.id, resource.name);
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
    if (hyperlinkResource) {
      return String.format(
          "- `%s` has been removed from [`%s`](%s).",
          attribute.name, resource.name, getDocsUrlForResource(resource));
    }
    return String.format("- `%s` has been removed from `%s`.", attribute.name, resource.name);
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
                    .map(Parameter::getName)
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
    if (hyperlinkResource && hyperlinkAction) {
      return String.format(
          "- `%s` has been removed as %s from [`%s`](%s) in [`%s`](%s).",
          param.getName(),
          parameterType,
          action.id,
          getDocsUrlForActions(resource, action),
          resource.name,
          getDocsUrlForResource(resource));
    } else if (hyperlinkResource) {
      return String.format(
          "- `%s` has been removed as %s from `%s` in [`%s`](%s).",
          param.getName(),
          parameterType,
          action.id,
          resource.name,
          getDocsUrlForResource(resource));
    }
    return String.format(
        "- `%s` has been removed as %s from `%s` in `%s`.",
        param.getName(), parameterType, action.id, resource.name);
  }

  private List<String> generateDeletedEventLine(
      List<Map<String, String>> oldEvents, List<Map<String, String>> newEvents) {
    Set<String> newEventTypes =
        newEvents.stream().map(event -> event.get("type")).collect(Collectors.toSet());

    return oldEvents.stream()
        .filter(event -> !newEventTypes.contains(event.get("type")))
        .map(event -> String.format("- `%s` has been removed.", event.get("type")))
        .distinct()
        .collect(Collectors.toList());
  }

  private List<String> generateEnumValueLines(
      List<Resource> oldResources, List<Resource> newResources, Spec oldSpec, Spec newSpec) {
    Set<String> enumLines = new LinkedHashSet<>();
    enumLines.addAll(generateGlobalEnumLines(oldSpec.globalEnums(), newSpec.globalEnums()));
    enumLines.addAll(generateAttributeEnumLines(oldResources, newResources));
    enumLines.addAll(generateParameterEnumLines(oldResources, newResources));
    return new ArrayList<>(enumLines);
  }

  private List<String> generateGlobalEnumLines(
      List<Enum> oldGlobalEnums, List<Enum> newGlobalEnums) {
    List<String> lines = new ArrayList<>();
    Map<String, Set<String>> oldEnumValuesMap =
        oldGlobalEnums.stream()
            .collect(Collectors.toMap(e -> e.name, e -> new HashSet<>(e.values())));

    for (Enum newEnum : newGlobalEnums) {
      Set<String> oldValues = oldEnumValuesMap.getOrDefault(newEnum.name, Collections.emptySet());
      List<String> newValues =
          newEnum.values().stream()
              .filter(value -> !oldValues.contains(value))
              .collect(Collectors.toList());

      if (!newValues.isEmpty()) {
        lines.add(
            String.format("- %s to enum `%s`.", formatValuesList(newValues, true), newEnum.name));
      }
    }
    return lines;
  }

  private List<String> generateAttributeEnumLines(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Map<String, Set<String>>> oldEnumsByResource = new HashMap<>();
    for (Resource oldResource : oldResources) {
      Map<String, Set<String>> attributeEnums = new HashMap<>();
      for (Attribute attr : oldResource.attributes()) {
        if (attr.isEnumAttribute() && !attr.isGlobalEnumAttribute()) {
          attributeEnums.put(attr.name, new HashSet<>(attr.getEnum().values()));
        }
      }
      oldEnumsByResource.put(oldResource.id, attributeEnums);
    }

    List<String> lines = new ArrayList<>();
    for (Resource newResource : newResources) {
      Map<String, Set<String>> oldAttributeEnums =
          oldEnumsByResource.getOrDefault(newResource.id, Collections.emptyMap());
      for (Attribute newAttr : newResource.attributes()) {
        if (newAttr.isEnumAttribute() && !newAttr.isGlobalEnumAttribute()) {
          Set<String> oldValues =
              oldAttributeEnums.getOrDefault(newAttr.name, Collections.emptySet());
          List<String> addedValues =
              newAttr.getEnum().values().stream()
                  .filter(value -> !oldValues.contains(value))
                  .collect(Collectors.toList());

          if (!addedValues.isEmpty()) {
            lines.add(
                String.format(
                    "- %s to enum attribute [`%s`](%s) in [`%s`](%s).",
                    formatValuesList(addedValues, true),
                    newAttr.name,
                    getDocsUrlForAttribute(newResource, newAttr),
                    newResource.name,
                    getDocsUrlForResource(newResource)));
          }
        }
      }
    }
    return lines;
  }

  private List<String> generateParameterEnumLines(
      List<Resource> oldResources, List<Resource> newResources) {
    List<String> lines = new ArrayList<>();
    Map<String, Map<String, Map<String, Set<String>>>> oldParamEnumsByResource =
        collectParameterEnums(oldResources);

    for (Resource newResource : newResources) {
      Map<String, Map<String, Set<String>>> oldActionParams =
          oldParamEnumsByResource.getOrDefault(newResource.id, Collections.emptyMap());

      for (Action action : newResource.actions) {
        Map<String, Set<String>> oldParamEnums =
            oldActionParams.getOrDefault(action.id, Collections.emptyMap());
        processNewParameterEnums(
            newResource, action, action.queryParameters(), "query", oldParamEnums, lines);
        processNewParameterEnums(
            newResource, action, action.requestBodyParameters(), "body", oldParamEnums, lines);
      }
    }
    return lines;
  }

  private void processNewParameterEnums(
      Resource resource,
      Action action,
      List<Parameter> parameters,
      String prefix,
      Map<String, Set<String>> oldParamEnums,
      List<String> lines) {
    for (Parameter param : parameters) {
      if (param.isEnum()
          && !(param.isGlobalEnum() || param.isExternalEnum() || param.isGenSeperate())) {
        List<String> addedValues =
            getNewEnumValues(
                param.getEnumValues(),
                oldParamEnums.getOrDefault(prefix + ":" + param.getName(), Collections.emptySet()));
        if (!addedValues.isEmpty()) {
          addParamEnumLine(
              lines,
              addedValues,
              resource,
              action,
              param,
              prefix.equals("query") ? "query parameter" : "request body parameter");
        }
      }
      if (param.schema.getProperties() != null) {
        param
            .schema
            .getProperties()
            .forEach(
                (key, value) -> {
                  if (isEnumSchema(value)
                      && !(isGlobalEnumSchema(value)
                          || isExternalEnumSchema(value)
                          || isGenSeperatSchema(value))) {
                    List<String> addedValues =
                        getNewEnumValues(
                            new ArrayList<>(getEnumValues(value)),
                            oldParamEnums.getOrDefault(
                                prefix + ":" + param.getName() + "." + key,
                                Collections.emptySet()));
                    if (!addedValues.isEmpty()) {
                      addParamEnumLine(
                          lines,
                          addedValues,
                          resource,
                          action,
                          new Parameter(param.getName() + "." + key, value),
                          prefix.equals("query") ? "query parameter" : "request body parameter");
                    }
                  }
                });
      }
    }
  }

  private void addParamEnumLine(
      List<String> lines,
      List<String> values,
      Resource resource,
      Action action,
      Parameter param,
      String type) {
    lines.add(
        String.format(
            "- %s to enum %s `%s` in [`%s`](%s) of [`%s`](%s).",
            formatValuesList(values, true),
            type,
            param.getName(),
            action.id,
            getDocsUrlForActions(resource, action),
            resource.name,
            getDocsUrlForResource(resource)));
  }

  private List<String> getNewEnumValues(List<String> current, Set<String> old) {
    return current.stream().filter(v -> !old.contains(v)).collect(Collectors.toList());
  }

  private List<String> generateDeletedEnumValueLines(
      List<Resource> oldResources, List<Resource> newResources, Spec oldSpec, Spec newSpec) {
    Set<String> enumLines = new LinkedHashSet<>();
    enumLines.addAll(generateDeletedGlobalEnumLines(oldSpec.globalEnums(), newSpec.globalEnums()));
    enumLines.addAll(generateDeletedAttributeEnumLines(oldResources, newResources));
    enumLines.addAll(generateDeletedParameterEnumLines(oldResources, newResources));
    return new ArrayList<>(enumLines);
  }

  private List<String> generateDeletedGlobalEnumLines(
      List<Enum> oldGlobalEnums, List<Enum> newGlobalEnums) {
    List<String> lines = new ArrayList<>();
    Map<String, Set<String>> newEnumValuesMap =
        newGlobalEnums.stream()
            .collect(Collectors.toMap(e -> e.name, e -> new HashSet<>(e.values())));

    for (Enum oldEnum : oldGlobalEnums) {
      Set<String> newValues = newEnumValuesMap.getOrDefault(oldEnum.name, Collections.emptySet());
      List<String> deletedValues =
          oldEnum.values().stream()
              .filter(value -> !newValues.contains(value))
              .collect(Collectors.toList());

      if (!deletedValues.isEmpty()) {
        lines.add(
            String.format(
                "- %s from global enum `%s`.",
                formatValuesList(deletedValues, false), oldEnum.name));
      }
    }
    return lines;
  }

  private List<String> generateDeletedAttributeEnumLines(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Map<String, Set<String>>> newEnumsByResource = new HashMap<>();
    for (Resource newResource : newResources) {
      Map<String, Set<String>> attributeEnums = new HashMap<>();
      for (Attribute attr : newResource.attributes()) {
        if (attr.isEnumAttribute() && !attr.isGlobalEnumAttribute()) {
          attributeEnums.put(attr.name, new HashSet<>(attr.getEnum().values()));
        }
      }
      newEnumsByResource.put(newResource.id, attributeEnums);
    }

    List<String> lines = new ArrayList<>();
    for (Resource oldResource : oldResources) {
      Map<String, Set<String>> newAttributeEnums =
          newEnumsByResource.getOrDefault(oldResource.id, Collections.emptyMap());
      for (Attribute oldAttr : oldResource.attributes()) {
        if (oldAttr.isEnumAttribute() && !oldAttr.isGlobalEnumAttribute()) {
          Set<String> newValues =
              newAttributeEnums.getOrDefault(oldAttr.name, Collections.emptySet());
          List<String> deletedValues =
              oldAttr.getEnum().values().stream()
                  .filter(value -> !newValues.contains(value))
                  .collect(Collectors.toList());

          if (!deletedValues.isEmpty()) {
            lines.add(
                String.format(
                    "- %s from enum attribute [`%s`](%s) in [`%s`](%s).",
                    formatValuesList(deletedValues, false),
                    oldAttr.name,
                    getDocsUrlForAttribute(oldResource, oldAttr),
                    oldResource.name,
                    getDocsUrlForResource(oldResource)));
          }
        }
      }
    }
    return lines;
  }

  private List<String> generateDeletedParameterEnumLines(
      List<Resource> oldResources, List<Resource> newResources) {
    List<String> lines = new ArrayList<>();
    Map<String, Map<String, Map<String, Set<String>>>> newParamEnumsByResource =
        collectParameterEnums(newResources);

    for (Resource oldResource : oldResources) {
      Map<String, Map<String, Set<String>>> newActionParams =
          newParamEnumsByResource.getOrDefault(oldResource.id, Collections.emptyMap());

      for (Action action : oldResource.actions) {
        Map<String, Set<String>> newParamEnums =
            newActionParams.getOrDefault(action.id, Collections.emptyMap());
        processDeletedParameterEnums(
            oldResource, action, action.queryParameters(), "query", newParamEnums, lines);
        processDeletedParameterEnums(
            oldResource, action, action.requestBodyParameters(), "body", newParamEnums, lines);
      }
    }
    return lines;
  }

  private void processDeletedParameterEnums(
      Resource resource,
      Action action,
      List<Parameter> parameters,
      String prefix,
      Map<String, Set<String>> newParamEnums,
      List<String> lines) {
    for (Parameter param : parameters) {
      if (param.isEnum()
          && !(param.isGlobalEnum() || param.isExternalEnum() || param.isGenSeperate())) {
        List<String> deletedValues =
            getNewEnumValues(
                param.getEnumValues(),
                newParamEnums.getOrDefault(prefix + ":" + param.getName(), Collections.emptySet()));
        if (!deletedValues.isEmpty()) {
          addDeletedParamEnumLine(
              lines,
              deletedValues,
              resource,
              action,
              param,
              prefix.equals("query") ? "query parameter" : "request body parameter");
        }
      }
    }
  }

  private void addDeletedParamEnumLine(
      List<String> lines,
      List<String> values,
      Resource resource,
      Action action,
      Parameter param,
      String type) {
    lines.add(
        String.format(
            "- %s from enum %s [`%s`](%s) in [`%s`](%s) of [`%s`](%s).",
            formatValuesList(values, false),
            type,
            param.getName(),
            getDocsUrlForParameter(resource, action, param),
            action.id,
            getDocsUrlForActions(resource, action),
            resource.name,
            getDocsUrlForResource(resource)));
  }

  private Map<String, Map<String, Map<String, Set<String>>>> collectParameterEnums(
      List<Resource> resources) {
    Map<String, Map<String, Map<String, Set<String>>>> resourceMap = new HashMap<>();
    for (Resource resource : resources) {
      Map<String, Map<String, Set<String>>> actionMap = new HashMap<>();
      for (Action action : resource.actions) {
        Map<String, Set<String>> paramEnums = new HashMap<>();
        collectEnumsFromParams(action.queryParameters(), "query", paramEnums);
        collectEnumsFromParams(action.requestBodyParameters(), "body", paramEnums);
        actionMap.put(action.id, paramEnums);
      }
      resourceMap.put(resource.id, actionMap);
    }
    return resourceMap;
  }

  private void collectEnumsFromParams(
      List<Parameter> params, String prefix, Map<String, Set<String>> paramEnums) {
    for (Parameter param : params) {
      if (param.isEnum()
          && !(param.isGlobalEnum() || param.isExternalEnum() || param.isGenSeperate())) {
        paramEnums.put(prefix + ":" + param.getName(), new HashSet<>(param.getEnumValues()));
      }
      if (param.schema.getProperties() != null) {
        param
            .schema
            .getProperties()
            .forEach(
                (key, value) -> {
                  if (isEnumSchema(value)
                      && !(isGlobalEnumSchema(value)
                          || isExternalEnumSchema(value)
                          || isGenSeperatSchema(value))) {
                    paramEnums.put(
                        prefix + ":" + param.getName() + "." + key, getEnumValues(value));
                  }
                });
      }
    }
  }

  private String formatValuesList(List<String> values, boolean isAdded) {
    if (values.isEmpty()) return "";
    String action = isAdded ? "added as a new value" : "removed";
    String actionPlural = isAdded ? "added as new values" : "removed";
    String val0 = values.get(0);

    if (values.size() == 1) {
      return String.format("`%s` has been %s", val0, action);
    } else if (values.size() == 2) {
      return String.format("`%s` and `%s` have been %s", val0, values.get(1), actionPlural);
    } else {
      String lastValue = values.get(values.size() - 1);
      String otherValues =
          values.subList(0, values.size() - 1).stream()
              .map(v -> String.format("`%s`", v))
              .collect(Collectors.joining(", "));
      return String.format("%s, and `%s` have been %s", otherValues, lastValue, actionPlural);
    }
  }

  private String getDocsUrlForEvent(String eventType) {
    return String.format("https://apidocs.chargebee.com/docs/api/events/webhook/%s", eventType);
  }

  private String getDocsUrlForResource(Resource resource) {
    return String.format("https://apidocs.chargebee.com/docs/api/%s", pluralize(resource.id));
  }

  private String getDocsUrlForActions(Resource resource, Action action) {
    return String.format(
        "https://apidocs.chargebee.com/docs/api/%s/%s",
        pluralize(resource.id), toHyphenCase(action.id));
  }

  private String getDocsUrlForAttribute(Resource resource, Attribute attribute) {
    return String.format(
        "https://apidocs.chargebee.com/docs/api/%s/%s-object#%s",
        pluralize(resource.id), toHyphenCase(singularize(resource.id)), attribute.name);
  }

  private String getDocsUrlForParameter(Resource resource, Action action, Parameter param) {
    return String.format(
        "https://apidocs.chargebee.com/docs/api/%s/%s#%s",
        pluralize(resource.id), toHyphenCase(action.id), param.getName());
  }

  private String toHyphenCase(String s) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, s);
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

  public boolean isExternalEnumSchema(Schema schema) {
    return schema instanceof ArraySchema
        ? isExternalEnumAttribute(schema.getItems())
        : isExternalEnumAttribute(schema);
  }

  private boolean isExternalEnumAttribute(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_EXTERNAL_ENUM) != null
        && (boolean) schema.getExtensions().get(IS_EXTERNAL_ENUM);
  }

  public Set<String> getEnumValues(Schema schema) {
    if (isGlobalEnumSchema(schema)
        || isExternalEnumSchema(schema)
        || !isEnumSchema(schema)
        || isGenSeperatSchema(schema)) {
      return Collections.emptySet();
    }
    return new HashSet<>(new Enum(schema).values());
  }

  private List<String> generateParameterRequirementChanges(
      List<Resource> oldResources, List<Resource> newResources) {

    List<String> lines = new ArrayList<>();

    // Build map of old parameters with their requirement status
    Map<String, Map<String, Map<String, Boolean>>> oldParamRequirements = new HashMap<>();

    for (Resource oldResource : oldResources) {
      Map<String, Map<String, Boolean>> actionParams = new HashMap<>();

      for (Action action : oldResource.actions) {
        Map<String, Boolean> paramRequirements = new HashMap<>();

        // Collect query parameters
        for (Parameter param : action.queryParameters()) {
          paramRequirements.put("query:" + param.getName(), param.isRequired);
        }

        // Collect request body parameters
        for (Parameter param : action.requestBodyParameters()) {
          paramRequirements.put("body:" + param.getName(), param.isRequired);
        }

        actionParams.put(action.id, paramRequirements);
      }

      oldParamRequirements.put(oldResource.id, actionParams);
    }

    // Check for requirement changes in new parameters
    for (Resource newResource : newResources) {
      Map<String, Map<String, Boolean>> oldActionParams =
          oldParamRequirements.getOrDefault(newResource.id, Collections.emptyMap());

      for (Action action : newResource.actions) {
        Map<String, Boolean> oldParamReqs =
            oldActionParams.getOrDefault(action.id, Collections.emptyMap());

        // Check query parameters
        for (Parameter param : action.queryParameters()) {
          String key = "query:" + param.getName();
          if (oldParamReqs.containsKey(key)) {
            boolean wasRequired = oldParamReqs.get(key);
            boolean isNowRequired = param.isRequired;

            if (wasRequired && !isNowRequired) {
              lines.add(
                  String.format(
                      "- [`%s`](%s) has been changed from required to optional in [`%s`](%s) of"
                          + " [`%s`](%s).",
                      param.getName(),
                      getDocsUrlForParameter(newResource, action, param),
                      action.id,
                      getDocsUrlForActions(newResource, action),
                      newResource.name,
                      getDocsUrlForResource(newResource)));
            } else if (!wasRequired && isNowRequired) {
              lines.add(
                  String.format(
                      "- [`%s`](%s) has been changed from optional to required in [`%s`](%s) of"
                          + " [`%s`](%s).",
                      param.getName(),
                      getDocsUrlForParameter(newResource, action, param),
                      action.id,
                      getDocsUrlForActions(newResource, action),
                      newResource.name,
                      getDocsUrlForResource(newResource)));
            }
          }
        }

        // Check request body parameters
        for (Parameter param : action.requestBodyParameters()) {
          String key = "body:" + param.getName();
          if (oldParamReqs.containsKey(key)) {
            boolean wasRequired = oldParamReqs.get(key);
            boolean isNowRequired = param.isRequired;

            if (wasRequired && !isNowRequired) {
              lines.add(
                  String.format(
                      "- [`%s`](%s) has been changed from required to optional in [`%s`](%s) of"
                          + " [`%s`](%s).",
                      param.getName(),
                      getDocsUrlForParameter(newResource, action, param),
                      action.id,
                      getDocsUrlForActions(newResource, action),
                      newResource.name,
                      getDocsUrlForResource(newResource)));
            } else if (!wasRequired && isNowRequired) {
              lines.add(
                  String.format(
                      "- [`%s`](%s) has been changed from optional to required in [`%s`](%s) of"
                          + " [`%s`](%s).",
                      param.getName(),
                      getDocsUrlForParameter(newResource, action, param),
                      action.id,
                      getDocsUrlForActions(newResource, action),
                      newResource.name,
                      getDocsUrlForResource(newResource)));
            }
          }
        }
      }
    }

    return lines;
  }

  public boolean isGenSeperatSchema(Schema schema) {
    return schema instanceof ArraySchema
        ? isGenSeperateAttributeSchema(schema.getItems())
        : isGenSeperateAttributeSchema(schema);
  }

  private boolean isGenSeperateAttributeSchema(Schema schema) {
    return schema.getExtensions() != null
        && schema.getExtensions().get(IS_GEN_SEPARATE) != null
        && (boolean) schema.getExtensions().get((IS_GEN_SEPARATE));
  }
}
