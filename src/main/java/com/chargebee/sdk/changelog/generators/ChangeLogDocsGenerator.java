package com.chargebee.sdk.changelog.generators;

import static com.chargebee.GenUtil.pluralize;
import static com.chargebee.openapi.Extension.*;
import static com.chargebee.sdk.changelog.Constants.CHANGELOG_DOCS;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Enum;
import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.openapi.parameter.Parameter;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.changelog.ChangeLogDocs;
import com.chargebee.sdk.changelog.models.ChangeLogDocsSchema;
import com.chargebee.sdk.changelog.models.ChangeLogEntry;
import com.chargebee.sdk.changelog.models.ChangeLogEntry.EntryType;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChangeLogDocsGenerator implements FileGenerator {

  private static final String QUERY_PREFIX = "query";
  private static final String BODY_PREFIX = "body";

  private final ChangeLogDocs changeLogDocs;
  private final Template changeLogDocsTemplate;

  public ChangeLogDocsGenerator(ChangeLogDocs changeLogDocs) {
    this.changeLogDocs = changeLogDocs;
    this.changeLogDocsTemplate = changeLogDocs.getTemplateContent(CHANGELOG_DOCS);
  }

  @Override
  public FileOp generate(String output, Spec oldVersion, Spec newerVersion) throws IOException {
    List<Resource> oldResources = filterResources(oldVersion.resources());
    List<Resource> newResources = filterResources(newerVersion.resources());

    ChangeLogDocsSchema schema =
        buildChangeLogSchema(oldVersion, newerVersion, oldResources, newResources);

    LocalDocsAvailabilityChecker checker = new LocalDocsAvailabilityChecker();

    ChangeLogDocsSchema availableSchema = new ChangeLogDocsSchema();
    ChangeLogDocsSchema missingSchema = new ChangeLogDocsSchema();
    partitionSchemaByDocsAvailability(schema, availableSchema, missingSchema, checker);

    String sectionDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    List<FileOp> ops = new ArrayList<>();

    if (hasAnyEntries(availableSchema)) {
      String availableContent = renderTemplate(availableSchema);
      String sectionContent = "[section " + sectionDate + "]\n" + availableContent + "\n\n";
      ops.add(new FileOp.PrependString(output, "index.txt", sectionContent));
    }

    if (hasAnyEntries(missingSchema)) {
      String missingContent = renderMissingDocsReport(missingSchema, checker, sectionDate);
      ops.add(new FileOp.PrependString(output, "MISSING_DOCS.txt", missingContent));
    }

    return new FileOp.Composite(ops);
  }

  private List<ChangeLogEntry> collectAllEntries(ChangeLogDocsSchema schema) {
    List<ChangeLogEntry> all = new ArrayList<>();
    addAllNullable(all, schema.getNewResource());
    addAllNullable(all, schema.getNewActions());
    addAllNullable(all, schema.getNewResourceAttribute());
    addAllNullable(all, schema.getNewParams());
    addAllNullable(all, schema.getNewEventType());
    addAllNullable(all, schema.getDeletedResource());
    addAllNullable(all, schema.getDeletedActions());
    addAllNullable(all, schema.getDeletedResourceAttribute());
    addAllNullable(all, schema.getDeletedParams());
    addAllNullable(all, schema.getDeletedEventType());
    addAllNullable(all, schema.getNewEnumValues());
    addAllNullable(all, schema.getDeletedEnumValues());
    addAllNullable(all, schema.getParameterRequirementChangesValues());
    return all;
  }

  private <T> void addAllNullable(List<T> target, List<T> source) {
    if (source != null) {
      target.addAll(source);
    }
  }

  private void partitionSchemaByDocsAvailability(
      ChangeLogDocsSchema source,
      ChangeLogDocsSchema available,
      ChangeLogDocsSchema missing,
      LocalDocsAvailabilityChecker checker) {
    available.setNewResource(partition(source.getNewResource(), checker, missing::setNewResource));
    available.setNewActions(partition(source.getNewActions(), checker, missing::setNewActions));
    available.setNewResourceAttribute(
        partition(source.getNewResourceAttribute(), checker, missing::setNewResourceAttribute));
    available.setNewParams(partition(source.getNewParams(), checker, missing::setNewParams));
    available.setNewEventType(
        partition(source.getNewEventType(), checker, missing::setNewEventType));
    available.setDeletedResource(
        partition(source.getDeletedResource(), checker, missing::setDeletedResource));
    available.setDeletedActions(
        partition(source.getDeletedActions(), checker, missing::setDeletedActions));
    available.setDeletedResourceAttribute(
        partition(
            source.getDeletedResourceAttribute(), checker, missing::setDeletedResourceAttribute));
    available.setDeletedParams(
        partition(source.getDeletedParams(), checker, missing::setDeletedParams));
    available.setDeletedEventType(
        partition(source.getDeletedEventType(), checker, missing::setDeletedEventType));
    available.setNewEnumValues(
        partition(source.getNewEnumValues(), checker, missing::setNewEnumValues));
    available.setDeletedEnumValues(
        partition(source.getDeletedEnumValues(), checker, missing::setDeletedEnumValues));
    available.setParameterRequirementChangesValues(
        partition(
            source.getParameterRequirementChangesValues(),
            checker,
            missing::setParameterRequirementChangesValues));
  }

  private List<ChangeLogEntry> partition(
      List<ChangeLogEntry> source,
      LocalDocsAvailabilityChecker checker,
      Consumer<List<ChangeLogEntry>> setMissing) {
    if (source == null) {
      setMissing.accept(new ArrayList<>());
      return new ArrayList<>();
    }
    List<ChangeLogEntry> kept = new ArrayList<>();
    List<ChangeLogEntry> missing = new ArrayList<>();
    for (ChangeLogEntry entry : source) {
      if (checker.isEntryAvailable(entry)) {
        kept.add(entry);
      } else {
        missing.add(entry);
      }
    }
    setMissing.accept(missing);
    return kept;
  }

  private boolean hasAnyEntries(ChangeLogDocsSchema schema) {
    return isNotEmpty(schema.getNewResource())
        || isNotEmpty(schema.getNewActions())
        || isNotEmpty(schema.getNewResourceAttribute())
        || isNotEmpty(schema.getNewParams())
        || isNotEmpty(schema.getNewEventType())
        || isNotEmpty(schema.getDeletedResource())
        || isNotEmpty(schema.getDeletedActions())
        || isNotEmpty(schema.getDeletedResourceAttribute())
        || isNotEmpty(schema.getDeletedParams())
        || isNotEmpty(schema.getDeletedEventType())
        || isNotEmpty(schema.getNewEnumValues())
        || isNotEmpty(schema.getDeletedEnumValues())
        || isNotEmpty(schema.getParameterRequirementChangesValues());
  }

  private boolean isNotEmpty(List<?> list) {
    return list != null && !list.isEmpty();
  }

  private String renderMissingDocsReport(
      ChangeLogDocsSchema missingSchema, LocalDocsAvailabilityChecker checker, String sectionDate)
      throws IOException {
    Set<String> missingDescriptions = checker.describeMissingDocs(collectAllEntries(missingSchema));

    StringBuilder header = new StringBuilder();
    header.append("[section ").append(sectionDate).append("]\n");
    header
        .append("# The following changelog entries were skipped from index.txt because their\n")
        .append("# corresponding docs were not found in the local docs repo at:\n")
        .append("#   ")
        .append(checker.docsRoot())
        .append("\n# Add the missing docs and re-run.\n");
    if (!missingDescriptions.isEmpty()) {
      header.append("#\n# Missing docs entities:\n");
      for (String description : missingDescriptions) {
        header.append("#   - ").append(description).append("\n");
      }
    }
    header.append("\n");

    return header + renderTemplate(missingSchema) + "\n\n";
  }

  private ChangeLogDocsSchema buildChangeLogSchema(
      Spec oldVersion,
      Spec newerVersion,
      List<Resource> oldResources,
      List<Resource> newResources) {
    ChangeLogDocsSchema schema = new ChangeLogDocsSchema();

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

  private String renderTemplate(ChangeLogDocsSchema schema) throws IOException {
    Map<String, Object> schemaMap =
        changeLogDocs.getObjectMapper().convertValue(schema, Map.class);
    String content = changeLogDocsTemplate.apply(schemaMap);
    return content.replaceAll("(?m)^[ \t]*\r?\n([ \t]*\r?\n)+", "\n\n").replaceAll("^\\s+", "");
  }

  private List<Resource> filterResources(List<Resource> resources) {
    List<String> hiddenResourceIds = List.of(changeLogDocs.hiddenOverride);
    return resources.stream()
        .filter(resource -> !hiddenResourceIds.contains(resource.id))
        .collect(Collectors.toList());
  }

  // --- New Resources ---

  private List<ChangeLogEntry> generateNewResources(
      List<Resource> oldResources, List<Resource> newResources) {
    Set<String> existingResourceIds = extractResourceIds(oldResources);

    return distinctByLine(
        newResources.stream()
            .filter(resource -> !existingResourceIds.contains(resource.id))
            .map(this::formatNewResourceEntry));
  }

  private ChangeLogEntry formatNewResourceEntry(Resource resource) {
    String resourcePath = getDocsLinkForResourceList(resource);
    String line =
        String.format(
            "[list]New resource added: [link_api %s][code %s][].[]", resourcePath, resourcePath);
    return ChangeLogEntry.builder()
        .line(line)
        .type(EntryType.NEW_RESOURCE)
        .resourceId(resource.id)
        .docsResourcePath(resourcePath)
        .build();
  }

  // --- New Actions ---

  private List<ChangeLogEntry> generateNewActions(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Set<String>> oldActionsByResource = buildActionMap(oldResources);
    Set<String> oldResourceIds = extractResourceIds(oldResources);
    Map<String, ChangeLogEntry> entries = new LinkedHashMap<>();

    for (Resource newResource : newResources) {
      if (!oldResourceIds.contains(newResource.id)) {
        continue;
      }

      Set<String> existingActions =
          oldActionsByResource.getOrDefault(newResource.id, Collections.emptySet());

      for (Action action : newResource.actions) {
        if (!existingActions.contains(action.name)) {
          ChangeLogEntry entry = formatNewActionEntry(newResource, action);
          entries.putIfAbsent(entry.getLine(), entry);
        }
      }
    }

    return new ArrayList<>(entries.values());
  }

  private ChangeLogEntry formatNewActionEntry(Resource resource, Action action) {
    String resourcePath = getDocsLinkForResourceList(resource);
    String line =
        String.format(
            "[list]New endpoint added: [link_api %s#%s][code %s%s][].[]",
            resourcePath,
            action.id,
            action.httpRequestType.name(),
            action.getUrl());
    return ChangeLogEntry.builder()
        .line(line)
        .type(EntryType.NEW_ACTION)
        .resourceId(resource.id)
        .docsResourcePath(resourcePath)
        .actionId(action.id)
        .build();
  }

  // --- New Attributes ---

  private List<ChangeLogEntry> generateNewAttributes(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Set<String>> oldAttributesByResource = buildAttributeMap(oldResources);
    Set<String> oldResourceIds = extractResourceIds(oldResources);

    return distinctByLine(
        newResources.stream()
            .filter(resource -> oldResourceIds.contains(resource.id))
            .flatMap(resource -> findNewAttributes(resource, oldAttributesByResource)));
  }

  private Stream<ChangeLogEntry> findNewAttributes(
      Resource resource, Map<String, Set<String>> oldAttributesByResource) {
    Set<String> existingAttributes =
        oldAttributesByResource.getOrDefault(resource.id, Collections.emptySet());

    return resource.attributes().stream()
        .filter(attribute -> !existingAttributes.contains(attribute.name))
        .map(attribute -> formatNewAttributeEntry(resource, attribute));
  }

  private ChangeLogEntry formatNewAttributeEntry(Resource resource, Attribute attribute) {
    String resourcePath = getDocsLinkForResourceList(resource);
    String line =
        String.format(
            "[list]New attribute [code %s] added to the resource [link_api %s#%s][code %s][].[]",
            attribute.name,
            getDocsLinkForResourceObject(resource),
            attribute.name,
            resourcePath);
    return ChangeLogEntry.builder()
        .line(line)
        .type(EntryType.NEW_ATTRIBUTE)
        .resourceId(resource.id)
        .docsResourcePath(resourcePath)
        .slugPath(attribute.name)
        .build();
  }

  // --- New Parameters ---

  private List<ChangeLogEntry> generateNewParameters(
      List<Resource> oldResources, List<Resource> newResources) {
    List<ChangeLogEntry> queryParameterEntries =
        generateParameterEntries(oldResources, newResources, Action::queryParameters);
    List<ChangeLogEntry> bodyParameterEntries =
        generateParameterEntries(oldResources, newResources, Action::requestBodyParameters);

    return Stream.concat(queryParameterEntries.stream(), bodyParameterEntries.stream())
        .collect(Collectors.toList());
  }

  private List<ChangeLogEntry> generateParameterEntries(
      List<Resource> oldResources,
      List<Resource> newResources,
      Function<Action, List<Parameter>> parameterExtractor) {
    Map<String, Map<String, Set<String>>> oldParametersByResource =
        buildParameterMap(oldResources, parameterExtractor);
    Set<String> oldResourceIds = extractResourceIds(oldResources);
    Map<String, ChangeLogEntry> entries = new LinkedHashMap<>();

    for (Resource newResource : newResources) {
      if (!oldResourceIds.contains(newResource.id)) {
        continue;
      }

      Map<String, Set<String>> oldActionParameters =
          oldParametersByResource.getOrDefault(newResource.id, Collections.emptyMap());

      for (Action action : newResource.actions) {
        Set<String> existingParameters =
            oldActionParameters.getOrDefault(action.id, Collections.emptySet());
        findAndAddNewParameters(
            newResource, action, parameterExtractor.apply(action), existingParameters, entries);
      }
    }

    return new ArrayList<>(entries.values());
  }

  private void findAndAddNewParameters(
      Resource resource,
      Action action,
      List<Parameter> parameters,
      Set<String> existingParameters,
      Map<String, ChangeLogEntry> entries) {
    for (Parameter parameter : parameters) {
      if (!existingParameters.contains(parameter.getName())) {
        ChangeLogEntry entry = formatNewParameterEntry(resource, action, parameter);
        entries.putIfAbsent(entry.getLine(), entry);
      } else if (parameter.schema.getProperties() != null) {
        processNestedParameters(
            parameter.schema, parameter.getName(), existingParameters, resource, action, entries,
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
      Map<String, ChangeLogEntry> entries,
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
                ChangeLogEntry entry =
                    isNew
                        ? formatNewParameterEntry(resource, action, nestedParameter)
                        : formatDeletedParameterEntry(resource, action, nestedParameter, true);
                entries.putIfAbsent(entry.getLine(), entry);
              } else {
                processNestedParameters(
                    (Schema) value, nestedPath, existingParameters, resource, action, entries,
                    isNew);
              }
            });
  }

  private ChangeLogEntry formatNewParameterEntry(
      Resource resource, Action action, Parameter parameter) {
    String paramAnchor = parameter.getName().replace(".", "_");
    String resourcePath = getDocsLinkForResourceList(resource);
    String actionPath = toHyphenCase(action.id);
    String line =
        String.format(
            "[list]New input parameter [code %s] added to the endpoint "
                + "[link_api %s/%s#%s][code %s/%s][].[]",
            toBracketNotation(parameter.getName()),
            resourcePath,
            actionPath,
            paramAnchor,
            resourcePath,
            actionPath);
    return ChangeLogEntry.builder()
        .line(line)
        .type(EntryType.NEW_PARAMETER)
        .resourceId(resource.id)
        .docsResourcePath(resourcePath)
        .actionId(action.id)
        .slugPath(parameter.getName())
        .build();
  }

  // --- New Events ---

  private List<ChangeLogEntry> generateNewEvents(Spec oldVersion, Spec newerVersion) {
    List<Map<String, String>> oldEvents = oldVersion.extractWebhookInfo(false);
    List<Map<String, String>> newEvents = newerVersion.extractWebhookInfo(false);

    Set<String> existingEventTypes =
        oldEvents.stream().map(event -> event.get("type")).collect(Collectors.toSet());

    return distinctByLine(
        newEvents.stream()
            .filter(event -> !existingEventTypes.contains(event.get("type")))
            .map(this::formatNewEventEntry));
  }

  private ChangeLogEntry formatNewEventEntry(Map<String, String> event) {
    String eventType = event.get("type");
    String line =
        String.format(
            "[list]Enum value added: [code %s] to the enum"
                + " [link_api events/webhook/%s][code event_type][].[]",
            eventType, eventType);
    return ChangeLogEntry.builder()
        .line(line)
        .type(EntryType.NEW_EVENT_TYPE)
        .eventType(eventType)
        .build();
  }

  // --- Deleted Resources ---

  private List<ChangeLogEntry> generateDeletedResources(
      List<Resource> oldResources, List<Resource> newResources) {
    Set<String> currentResourceIds = extractResourceIds(newResources);

    return distinctByLine(
        oldResources.stream()
            .filter(resource -> !currentResourceIds.contains(resource.id))
            .map(
                resource -> {
                  String resourcePath = getDocsLinkForResourceList(resource);
                  return ChangeLogEntry.builder()
                      .line(
                          String.format(
                              "[list]Resource [code %s] has been removed.[]", resourcePath))
                      .type(EntryType.DELETED_RESOURCE)
                      .resourceId(resource.id)
                      .docsResourcePath(resourcePath)
                      .build();
                }));
  }

  // --- Deleted Actions ---

  private List<ChangeLogEntry> generateDeletedActions(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Resource> newResourceMap =
        newResources.stream()
            .collect(Collectors.toMap(resource -> resource.id, resource -> resource));
    Map<String, ChangeLogEntry> entries = new LinkedHashMap<>();

    for (Resource oldResource : oldResources) {
      Resource correspondingNewResource = newResourceMap.get(oldResource.id);

      if (correspondingNewResource != null) {
        Set<String> currentActions =
            correspondingNewResource.actions.stream()
                .map(action -> action.name)
                .collect(Collectors.toSet());

        for (Action oldAction : oldResource.actions) {
          if (!currentActions.contains(oldAction.name)) {
            String resourcePath = getDocsLinkForResourceList(correspondingNewResource);
            String line =
                String.format(
                    "[list]Endpoint [code %s] removed from [link_api %s][code %s][].[]",
                    oldAction.id, resourcePath, resourcePath);
            entries.putIfAbsent(
                line,
                ChangeLogEntry.builder()
                    .line(line)
                    .type(EntryType.DELETED_ACTION)
                    .resourceId(correspondingNewResource.id)
                    .docsResourcePath(resourcePath)
                    .actionId(oldAction.id)
                    .build());
          }
        }
      } else {
        for (Action action : oldResource.actions) {
          String line =
              String.format(
                  "[list]Endpoint [code %s] removed from [code %s].[]",
                  action.id, getDocsLinkForResourceList(oldResource));
          entries.putIfAbsent(
              line,
              ChangeLogEntry.builder()
                  .line(line)
                  .type(EntryType.DELETED_ACTION)
                  // Resource is gone; nothing to verify against.
                  .build());
        }
      }
    }

    return new ArrayList<>(entries.values());
  }

  // --- Deleted Attributes ---

  private List<ChangeLogEntry> generateDeletedAttributes(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Resource> newResourceMap =
        newResources.stream()
            .collect(Collectors.toMap(resource -> resource.id, resource -> resource));
    Map<String, ChangeLogEntry> entries = new LinkedHashMap<>();

    for (Resource oldResource : oldResources) {
      Resource correspondingNewResource = newResourceMap.get(oldResource.id);

      if (correspondingNewResource != null) {
        Set<String> currentAttributes =
            correspondingNewResource.attributes().stream()
                .map(attribute -> attribute.name)
                .collect(Collectors.toSet());

        String resourcePath = getDocsLinkForResourceList(correspondingNewResource);
        for (Attribute oldAttribute : oldResource.attributes()) {
          if (!currentAttributes.contains(oldAttribute.name)) {
            String anchor = oldResource.id + "_" + oldAttribute.name;
            String line =
                String.format(
                    "[list]Attribute [code %s] removed from the resource "
                        + "[link_api %s#%s][code %s][].[]",
                    oldAttribute.name,
                    resourcePath,
                    anchor,
                    resourcePath);
            entries.putIfAbsent(
                line,
                ChangeLogEntry.builder()
                    .line(line)
                    .type(EntryType.DELETED_ATTRIBUTE)
                    .resourceId(correspondingNewResource.id)
                    .docsResourcePath(resourcePath)
                    .slugPath(oldAttribute.name)
                    .build());
          }
        }
      } else {
        for (Attribute attribute : oldResource.attributes()) {
          String line =
              String.format(
                  "[list]Attribute [code %s] removed from the resource [code %s].[]",
                  attribute.name, getDocsLinkForResourceList(oldResource));
          entries.putIfAbsent(
              line,
              ChangeLogEntry.builder()
                  .line(line)
                  .type(EntryType.DELETED_ATTRIBUTE)
                  .slugPath(attribute.name)
                  .build());
        }
      }
    }

    return new ArrayList<>(entries.values());
  }

  // --- Deleted Parameters ---

  private List<ChangeLogEntry> generateDeletedParameters(
      List<Resource> oldResources, List<Resource> newResources) {
    List<ChangeLogEntry> deletedQueryParameters =
        generateDeletedParameterEntries(oldResources, newResources, Action::queryParameters);
    List<ChangeLogEntry> deletedBodyParameters =
        generateDeletedParameterEntries(oldResources, newResources, Action::requestBodyParameters);

    return Stream.concat(deletedQueryParameters.stream(), deletedBodyParameters.stream())
        .collect(Collectors.toList());
  }

  private List<ChangeLogEntry> generateDeletedParameterEntries(
      List<Resource> oldResources,
      List<Resource> newResources,
      Function<Action, List<Parameter>> parameterExtractor) {
    Map<String, Resource> newResourceMap =
        newResources.stream()
            .collect(Collectors.toMap(resource -> resource.id, resource -> resource));
    Map<String, ChangeLogEntry> entries = new LinkedHashMap<>();

    for (Resource oldResource : oldResources) {
      Resource correspondingNewResource = newResourceMap.get(oldResource.id);

      if (correspondingNewResource != null) {
        processDeletedParametersForExistingResource(
            oldResource, correspondingNewResource, parameterExtractor, entries);
      } else {
        processDeletedParametersForRemovedResource(oldResource, parameterExtractor, entries);
      }
    }

    return new ArrayList<>(entries.values());
  }

  private void processDeletedParametersForExistingResource(
      Resource oldResource,
      Resource newResource,
      Function<Action, List<Parameter>> parameterExtractor,
      Map<String, ChangeLogEntry> entries) {
    Map<String, Action> newActionMap =
        newResource.actions.stream()
            .collect(Collectors.toMap(action -> action.id, action -> action));

    for (Action oldAction : oldResource.actions) {
      Action correspondingNewAction = newActionMap.get(oldAction.id);

      if (correspondingNewAction != null) {
        Set<String> currentParameters =
            collectParameterNames(parameterExtractor.apply(correspondingNewAction));

        for (Parameter oldParameter : parameterExtractor.apply(oldAction)) {
          if (!currentParameters.contains(oldParameter.getName())) {
            ChangeLogEntry entry =
                formatDeletedParameterEntry(newResource, correspondingNewAction, oldParameter,
                    true);
            entries.putIfAbsent(entry.getLine(), entry);
          } else if (oldParameter.schema.getProperties() != null) {
            processNestedParameters(
                oldParameter.schema,
                oldParameter.getName(),
                currentParameters,
                newResource,
                correspondingNewAction,
                entries,
                false);
          }
        }
      } else {
        for (Parameter parameter : parameterExtractor.apply(oldAction)) {
          ChangeLogEntry entry =
              formatDeletedParameterEntry(newResource, oldAction, parameter, false);
          entries.putIfAbsent(entry.getLine(), entry);
        }
      }
    }
  }

  private void processDeletedParametersForRemovedResource(
      Resource oldResource,
      Function<Action, List<Parameter>> parameterExtractor,
      Map<String, ChangeLogEntry> entries) {
    String resourcePath = getDocsLinkForResourceList(oldResource);
    for (Action action : oldResource.actions) {
      for (Parameter parameter : parameterExtractor.apply(action)) {
        String line =
            String.format(
                "[list]Input parameter [code %s] removed from the endpoint [code %s] in [code %s].[]",
                toBracketNotation(parameter.getName()), toHyphenCase(action.id), resourcePath);
        entries.putIfAbsent(
            line,
            ChangeLogEntry.builder()
                .line(line)
                .type(EntryType.DELETED_PARAMETER)
                .slugPath(parameter.getName())
                .build());
      }
    }
  }

  private ChangeLogEntry formatDeletedParameterEntry(
      Resource resource, Action action, Parameter parameter, boolean includeLink) {
    String paramAnchor = parameter.getName().replace(".", "_");
    String resourcePath = getDocsLinkForResourceList(resource);
    String actionPath = toHyphenCase(action.id);
    String line;
    if (includeLink) {
      line =
          String.format(
              "[list]Input parameter [code %s] removed from the endpoint "
                  + "[link_api %s/%s#%s][code %s/%s][].[]",
              toBracketNotation(parameter.getName()),
              resourcePath,
              actionPath,
              paramAnchor,
              resourcePath,
              actionPath);
    } else {
      line =
          String.format(
              "[list]Input parameter [code %s] removed from the endpoint [code %s] in [code %s].[]",
              toBracketNotation(parameter.getName()), actionPath, resourcePath);
    }
    return ChangeLogEntry.builder()
        .line(line)
        .type(EntryType.DELETED_PARAMETER)
        .resourceId(resource.id)
        .docsResourcePath(resourcePath)
        .actionId(action.id)
        .slugPath(parameter.getName())
        .build();
  }

  // --- Deleted Events ---

  private List<ChangeLogEntry> generateDeletedEvents(Spec oldVersion, Spec newerVersion) {
    List<Map<String, String>> oldEvents = oldVersion.extractWebhookInfo(false);
    List<Map<String, String>> newEvents = newerVersion.extractWebhookInfo(false);

    Set<String> currentEventTypes =
        newEvents.stream().map(event -> event.get("type")).collect(Collectors.toSet());

    return distinctByLine(
        oldEvents.stream()
            .filter(event -> !currentEventTypes.contains(event.get("type")))
            .map(
                event -> {
                  String eventType = event.get("type");
                  String line =
                      String.format(
                          "[list]Enum value removed: [code %s] from the enum [code event_type].[]",
                          eventType);
                  return ChangeLogEntry.builder()
                      .line(line)
                      .type(EntryType.DELETED_EVENT_TYPE)
                      .eventType(eventType)
                      .build();
                }));
  }

  // --- New Enum Values ---

  private List<ChangeLogEntry> generateNewEnumValues(
      List<Resource> oldResources, List<Resource> newResources, Spec oldSpec, Spec newSpec) {
    Map<String, ChangeLogEntry> entries = new LinkedHashMap<>();

    for (ChangeLogEntry entry : generateAttributeEnumEntries(oldResources, newResources, true)) {
      entries.putIfAbsent(entry.getLine(), entry);
    }
    for (ChangeLogEntry entry : generateParameterEnumEntries(oldResources, newResources, true)) {
      entries.putIfAbsent(entry.getLine(), entry);
    }

    return new ArrayList<>(entries.values());
  }

  // --- Deleted Enum Values ---

  private List<ChangeLogEntry> generateDeletedEnumValues(
      List<Resource> oldResources, List<Resource> newResources, Spec oldSpec, Spec newSpec) {
    Map<String, ChangeLogEntry> entries = new LinkedHashMap<>();

    for (ChangeLogEntry entry : generateAttributeEnumEntries(oldResources, newResources, false)) {
      entries.putIfAbsent(entry.getLine(), entry);
    }
    for (ChangeLogEntry entry : generateParameterEnumEntries(oldResources, newResources, false)) {
      entries.putIfAbsent(entry.getLine(), entry);
    }

    return new ArrayList<>(entries.values());
  }

  private List<ChangeLogEntry> generateAttributeEnumEntries(
      List<Resource> oldResources, List<Resource> newResources, boolean isAdded) {
    Map<String, Map<String, Set<String>>> oldEnumMap = buildAttributeEnumMap(oldResources);
    Map<String, Map<String, Set<String>>> newEnumMap = buildAttributeEnumMap(newResources);

    List<ChangeLogEntry> entries = new ArrayList<>();
    List<Resource> sourceResources = isAdded ? newResources : oldResources;
    Map<String, Map<String, Set<String>>> comparisonMap = isAdded ? oldEnumMap : newEnumMap;

    for (Resource resource : sourceResources) {
      Map<String, Set<String>> comparisonEnums =
          comparisonMap.getOrDefault(resource.id, Collections.emptyMap());
      processAttributeEnumChanges(
          resource, resource.attributes(), "", comparisonEnums, entries, isAdded);
    }

    return entries;
  }

  private void processAttributeEnumChanges(
      Resource resource,
      List<Attribute> attributes,
      String path,
      Map<String, Set<String>> comparisonEnums,
      List<ChangeLogEntry> entries,
      boolean isAdded) {
    for (Attribute attribute : attributes) {
      String currentPath = buildAttributePath(path, attribute.name);
      String anchorId = buildAttributeAnchor(path, attribute.name);

      if (attribute.isEnumAttribute()) {
        Set<String> comparisonValues =
            comparisonEnums.getOrDefault(currentPath, Collections.emptySet());
        List<String> changedValues =
            findChangedEnumValues(attribute.getEnum().values(), comparisonValues, isAdded);

        if (!changedValues.isEmpty()) {
          String verb = isAdded ? "added" : "removed";
          String preposition = isAdded ? "to" : "from";
          String line =
              String.format(
                  "[list]Enum value %s: %s %s the enum "
                      + "[link_api %s#%s][code %s.%s][].[]",
                  verb,
                  formatEnumCodeValues(changedValues),
                  preposition,
                  getDocsLinkForResourceObject(resource),
                  anchorId,
                  resource.id,
                  currentPath);
          entries.add(
              ChangeLogEntry.builder()
                  .line(line)
                  .type(
                      isAdded
                          ? EntryType.NEW_ATTRIBUTE_ENUM_VALUE
                          : EntryType.DELETED_ATTRIBUTE_ENUM_VALUE)
                  .resourceId(resource.id)
                  .docsResourcePath(getDocsLinkForResourceList(resource))
                  .slugPath(currentPath)
                  .enumValues(new ArrayList<>(changedValues))
                  .build());
        }
      }

      if (attribute.getSubAttributes() != null) {
        processAttributeEnumChanges(
            resource, attribute.getSubAttributes(), currentPath, comparisonEnums, entries, isAdded);
      }
    }
  }

  private List<ChangeLogEntry> generateParameterEnumEntries(
      List<Resource> oldResources, List<Resource> newResources, boolean isAdded) {
    List<ChangeLogEntry> entries = new ArrayList<>();
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
            resource, action, action.queryParameters(), QUERY_PREFIX, comparisonParameters, entries,
            isAdded);
        processParameterEnumChanges(
            resource, action, action.requestBodyParameters(), BODY_PREFIX, comparisonParameters,
            entries, isAdded);
      }
    }

    return entries;
  }

  private void processParameterEnumChanges(
      Resource resource,
      Action action,
      List<Parameter> parameters,
      String prefix,
      Map<String, Set<String>> comparisonParameters,
      List<ChangeLogEntry> entries,
      boolean isAdded) {
    for (Parameter parameter : parameters) {
      if (shouldProcessParameterEnum(parameter)) {
        String parameterKey = prefix + ":" + parameter.getName();
        Set<String> comparisonValues =
            comparisonParameters.getOrDefault(parameterKey, Collections.emptySet());
        List<String> changedValues =
            findChangedEnumValues(parameter.getEnumValues(), comparisonValues, isAdded);

        if (!changedValues.isEmpty()) {
          addParameterEnumEntry(entries, changedValues, resource, action, parameter, isAdded);
        }
      }

      if (parameter.schema.getProperties() != null) {
        processNestedParameterEnums(
            resource, action, parameter, prefix, comparisonParameters, entries, isAdded);
      }
    }
  }

  private void processNestedParameterEnums(
      Resource resource,
      Action action,
      Parameter parameter,
      String prefix,
      Map<String, Set<String>> comparisonParameters,
      List<ChangeLogEntry> entries,
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
                  Parameter nestedParameter = new Parameter(nestedParameterName, propertySchema);
                  addParameterEnumEntry(
                      entries, changedValues, resource, action, nestedParameter, isAdded);
                }
              }
            });
  }

  private void addParameterEnumEntry(
      List<ChangeLogEntry> entries,
      List<String> values,
      Resource resource,
      Action action,
      Parameter parameter,
      boolean isAdded) {
    String verb = isAdded ? "added" : "removed";
    String preposition = isAdded ? "to" : "from";
    String paramAnchor = parameter.getName().replace(".", "_");
    String resourcePath = getDocsLinkForResourceList(resource);
    String actionPath = toHyphenCase(action.id);
    String line =
        String.format(
            "[list]Enum value %s: %s %s the enum "
                + "[link_api %s/%s#%s][code %s/%s][].[]",
            verb,
            formatEnumCodeValues(values),
            preposition,
            resourcePath,
            actionPath,
            paramAnchor,
            resourcePath,
            actionPath);
    entries.add(
        ChangeLogEntry.builder()
            .line(line)
            .type(
                isAdded
                    ? EntryType.NEW_PARAMETER_ENUM_VALUE
                    : EntryType.DELETED_PARAMETER_ENUM_VALUE)
            .resourceId(resource.id)
            .docsResourcePath(resourcePath)
            .actionId(action.id)
            .slugPath(parameter.getName())
            .enumValues(new ArrayList<>(values))
            .build());
  }

  // --- Parameter Requirement Changes ---

  private List<ChangeLogEntry> generateParameterRequirementChanges(
      List<Resource> oldResources, List<Resource> newResources) {
    List<ChangeLogEntry> entries = new ArrayList<>();
    Map<String, Map<String, Map<String, Boolean>>> oldRequirementMap =
        buildParameterRequirementMap(oldResources);

    for (Resource newResource : newResources) {
      Map<String, Map<String, Boolean>> oldActionRequirements =
          oldRequirementMap.getOrDefault(newResource.id, Collections.emptyMap());

      for (Action newAction : newResource.actions) {
        Map<String, Boolean> oldParameterRequirements =
            oldActionRequirements.getOrDefault(newAction.id, Collections.emptyMap());

        checkRequirementChanges(
            newResource, newAction, newAction.queryParameters(), QUERY_PREFIX,
            oldParameterRequirements, entries);
        checkRequirementChanges(
            newResource, newAction, newAction.requestBodyParameters(), BODY_PREFIX,
            oldParameterRequirements, entries);
      }
    }

    return entries;
  }

  private void checkRequirementChanges(
      Resource resource,
      Action action,
      List<Parameter> parameters,
      String prefix,
      Map<String, Boolean> oldRequirements,
      List<ChangeLogEntry> entries) {
    for (Parameter parameter : parameters) {
      String parameterKey = prefix + ":" + parameter.getName();

      if (oldRequirements.containsKey(parameterKey)
          && oldRequirements.get(parameterKey) != parameter.isRequired) {
        entries.add(formatRequirementChangeEntry(resource, action, parameter));
      }
    }
  }

  private ChangeLogEntry formatRequirementChangeEntry(
      Resource resource, Action action, Parameter parameter) {
    String changeDescription =
        parameter.isRequired ? "`optional` to `required`" : "`required` to `optional`";
    String paramAnchor = parameter.getName().replace(".", "_");
    String resourcePath = getDocsLinkForResourceList(resource);
    String actionPath = toHyphenCase(action.id);
    String line =
        String.format(
            "[list]Input parameter [code %s] has been changed from %s in the endpoint "
                + "[link_api %s/%s#%s][code %s/%s][].[]",
            toBracketNotation(parameter.getName()),
            changeDescription,
            resourcePath,
            actionPath,
            paramAnchor,
            resourcePath,
            actionPath);
    return ChangeLogEntry.builder()
        .line(line)
        .type(EntryType.PARAMETER_REQUIREMENT_CHANGE)
        .resourceId(resource.id)
        .docsResourcePath(resourcePath)
        .actionId(action.id)
        .slugPath(parameter.getName())
        .build();
  }

  // --- Utility: Maps & Sets ---

  private List<ChangeLogEntry> distinctByLine(Stream<ChangeLogEntry> stream) {
    Map<String, ChangeLogEntry> seen = new LinkedHashMap<>();
    stream.forEach(entry -> seen.putIfAbsent(entry.getLine(), entry));
    return new ArrayList<>(seen.values());
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

      if (attribute.isEnumAttribute()) {
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

  // --- Utility: Enum helpers ---

  private List<String> findChangedEnumValues(
      List<String> currentValues, Set<String> comparisonValues, boolean isAdded) {
    return currentValues.stream()
        .filter(value -> !comparisonValues.contains(value))
        .collect(Collectors.toList());
  }

  private String formatEnumCodeValues(List<String> values) {
    if (values.isEmpty()) {
      return "";
    }

    if (values.size() == 1) {
      return String.format("[code %s]", values.get(0));
    }

    if (values.size() == 2) {
      return String.format("[code %s] and [code %s]", values.get(0), values.get(1));
    }

    String allButLast =
        values.subList(0, values.size() - 1).stream()
            .map(value -> "[code " + value + "]")
            .collect(Collectors.joining(", "));
    String lastValue = values.get(values.size() - 1);

    return String.format("%s, and [code %s]", allButLast, lastValue);
  }

  // --- Utility: Schema checks ---

  private boolean shouldProcessParameterEnum(Parameter parameter) {
    return parameter.isEnum()
        && !parameter.isExternalEnum()
        && !parameter.isGenSeperate();
  }

  private boolean shouldProcessSchemaEnum(Schema schema) {
    return isEnumSchema(schema)
        && !isExternalEnumSchema(schema)
        && !isGenSeparateSchema(schema);
  }

  private boolean isEnumSchema(Schema schema) {
    if (schema instanceof ArraySchema) {
      return schema.getItems().getEnum() != null && !schema.getItems().getEnum().isEmpty();
    }
    return schema.getEnum() != null && !schema.getEnum().isEmpty();
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
    if (isExternalEnumSchema(schema) || !isEnumSchema(schema) || isGenSeparateSchema(schema)) {
      return Collections.emptySet();
    }
    return new HashSet<>(new Enum(schema).values());
  }

  // --- Utility: ID & Name helpers ---

  private Set<String> extractResourceIds(List<Resource> resources) {
    return resources.stream().map(resource -> resource.id).collect(Collectors.toSet());
  }

  private String buildAttributePath(String basePath, String attributeName) {
    return basePath.isEmpty() ? attributeName : basePath + "." + attributeName;
  }

  private String buildAttributeAnchor(String basePath, String attributeName) {
    return basePath.isEmpty() ? attributeName : basePath.replace(".", "_") + "_" + attributeName;
  }

  private String toHyphenCase(String text) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, text);
  }

  private String toBracketNotation(String paramName) {
    if (!paramName.contains(".")) {
      return paramName;
    }
    String[] parts = paramName.split("\\.");
    StringBuilder sb = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      sb.append("[").append(parts[i]).append("]");
    }
    return sb.toString();
  }

  // --- Utility: Docs link builders ---

  private String getDocsLinkForResourceList(Resource resource) {
    return pluralize(resource.id);
  }

  private String getDocsLinkForResourceObject(Resource resource) {
    String hyphenatedResourceId = resource.id.replace("_", "-");
    return String.format("%s/%s-object", pluralize(resource.id), hyphenatedResourceId);
  }
}
