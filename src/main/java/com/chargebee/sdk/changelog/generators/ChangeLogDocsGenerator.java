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
import com.chargebee.sdk.changelog.models.ChangeLogSchema;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChangeLogDocsGenerator implements FileGenerator {

  private static final String QUERY_PREFIX = "query";
  private static final String BODY_PREFIX = "body";

  private static final String ADDED_VALUE_SINGULAR = "added as a new value";
  private static final String ADDED_VALUE_PLURAL = "added as new values";
  private static final String REMOVED_VALUE = "removed";

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

    ChangeLogSchema schema =
        buildChangeLogSchema(oldVersion, newerVersion, oldResources, newResources);

    DocsAvailabilityChecker checker = new DocsAvailabilityChecker();
    checker.warmUp(collectAllLines(schema));

    ChangeLogSchema availableSchema = new ChangeLogSchema();
    ChangeLogSchema missingSchema = new ChangeLogSchema();
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

  private List<String> collectAllLines(ChangeLogSchema schema) {
    List<String> all = new ArrayList<>();
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

  private void addAllNullable(List<String> target, List<String> source) {
    if (source != null) {
      target.addAll(source);
    }
  }

  private void partitionSchemaByDocsAvailability(
      ChangeLogSchema source,
      ChangeLogSchema available,
      ChangeLogSchema missing,
      DocsAvailabilityChecker checker) {
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

  private List<String> partition(
      List<String> source,
      DocsAvailabilityChecker checker,
      java.util.function.Consumer<List<String>> setMissing) {
    if (source == null) {
      setMissing.accept(new ArrayList<>());
      return new ArrayList<>();
    }
    List<String> kept = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    for (String line : source) {
      if (checker.isLineDocsAvailable(line)) {
        kept.add(line);
      } else {
        missing.add(line);
      }
    }
    setMissing.accept(missing);
    return kept;
  }

  private boolean hasAnyEntries(ChangeLogSchema schema) {
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

  private boolean isNotEmpty(List<String> list) {
    return list != null && !list.isEmpty();
  }

  private String renderMissingDocsReport(
      ChangeLogSchema missingSchema, DocsAvailabilityChecker checker, String sectionDate)
      throws IOException {
    Set<String> missingPaths = new LinkedHashSet<>();
    for (String line : collectAllLines(missingSchema)) {
      String path = DocsAvailabilityChecker.extractDocsPath(line);
      if (path != null) {
        missingPaths.add(path);
      }
    }

    StringBuilder header = new StringBuilder();
    header.append("[section ").append(sectionDate).append("]\n");
    header.append(
        "# The following changelog entries were skipped from index.txt because their docs\n");
    header.append("# pages return a non-200 status. Add the missing docs and re-run.\n");
    if (!missingPaths.isEmpty()) {
      header.append("#\n# Missing docs pages:\n");
      for (String path : missingPaths) {
        header.append("#   - ").append(DocsAvailabilityChecker.BASE_URL).append(path).append("\n");
      }
    }
    header.append("\n");

    return header + renderTemplate(missingSchema) + "\n\n";
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
        "[list]New resource added: [link_api %s][code %s][].[]",
        getDocsLinkForResourceList(resource), resource.name);
  }

  // --- New Actions ---

  private List<String> generateNewActions(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Set<String>> oldActionsByResource = buildActionMap(oldResources);
    Set<String> oldResourceIds = extractResourceIds(oldResources);
    Set<String> lines = new LinkedHashSet<>();

    for (Resource newResource : newResources) {
      if (!oldResourceIds.contains(newResource.id)) {
        continue;
      }

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
        "[list]New endpoint added: [link_api %s#%s][code %s%s][].[]",
        getDocsLinkForResourceList(resource),
        action.id,
        action.httpRequestType.name(),
        action.getUrl());
  }

  // --- New Attributes ---

  private List<String> generateNewAttributes(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Set<String>> oldAttributesByResource = buildAttributeMap(oldResources);
    Set<String> oldResourceIds = extractResourceIds(oldResources);

    return newResources.stream()
        .filter(resource -> oldResourceIds.contains(resource.id))
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
        "[list]New attribute [code %s] added to the resource [link_api %s#%s][code %s][].[]",
        attribute.name,
        getDocsLinkForResourceObject(resource),
        attribute.name,
        resource.name);
  }

  // --- New Parameters ---

  private List<String> generateNewParameters(
      List<Resource> oldResources, List<Resource> newResources) {
    List<String> queryParameterLines =
        generateParameterLines(oldResources, newResources, Action::queryParameters);
    List<String> bodyParameterLines =
        generateParameterLines(oldResources, newResources, Action::requestBodyParameters);

    return Stream.concat(queryParameterLines.stream(), bodyParameterLines.stream())
        .collect(Collectors.toList());
  }

  private List<String> generateParameterLines(
      List<Resource> oldResources,
      List<Resource> newResources,
      Function<Action, List<Parameter>> parameterExtractor) {
    Map<String, Map<String, Set<String>>> oldParametersByResource =
        buildParameterMap(oldResources, parameterExtractor);
    Set<String> oldResourceIds = extractResourceIds(oldResources);
    Set<String> lines = new LinkedHashSet<>();

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
            newResource, action, parameterExtractor.apply(action), existingParameters, lines);
      }
    }

    return new ArrayList<>(lines);
  }

  private void findAndAddNewParameters(
      Resource resource,
      Action action,
      List<Parameter> parameters,
      Set<String> existingParameters,
      Set<String> lines) {
    for (Parameter parameter : parameters) {
      if (!existingParameters.contains(parameter.getName())) {
        lines.add(formatNewParameterLine(resource, action, parameter));
      } else if (parameter.schema.getProperties() != null) {
        processNestedParameters(
            parameter.schema, parameter.getName(), existingParameters, resource, action, lines,
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
                if (isNew) {
                  lines.add(formatNewParameterLine(resource, action, nestedParameter));
                } else {
                  lines.add(formatDeletedParameterLine(resource, action, nestedParameter, true));
                }
              } else {
                processNestedParameters(
                    (Schema) value, nestedPath, existingParameters, resource, action, lines, isNew);
              }
            });
  }

  private String formatNewParameterLine(Resource resource, Action action, Parameter parameter) {
    String paramAnchor = parameter.getName().replace(".", "_");
    return String.format(
        "[list]New input parameter [code %s] added to the endpoint "
            + "[link_api %s/%s#%s][code %s.%s][].[]",
        toBracketNotation(parameter.getName()),
        getDocsLinkForResourceList(resource),
        toHyphenCase(action.id),
        paramAnchor,
        resource.name,
        toHyphenCase(action.id));
  }

  // --- New Events ---

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
    return String.format(
        "[list]New webhook added: [link_api events/webhook/%s][code %s][].[]",
        eventType, eventType);
  }

  // --- Deleted Resources ---

  private List<String> generateDeletedResources(
      List<Resource> oldResources, List<Resource> newResources) {
    Set<String> currentResourceIds = extractResourceIds(newResources);

    return oldResources.stream()
        .filter(resource -> !currentResourceIds.contains(resource.id))
        .map(resource -> String.format("[list]Resource [code %s] has been removed.[]", resource.name))
        .distinct()
        .collect(Collectors.toList());
  }

  // --- Deleted Actions ---

  private List<String> generateDeletedActions(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Resource> newResourceMap =
        newResources.stream()
            .collect(Collectors.toMap(resource -> resource.id, resource -> resource));
    Set<String> lines = new LinkedHashSet<>();

    for (Resource oldResource : oldResources) {
      Resource correspondingNewResource = newResourceMap.get(oldResource.id);

      if (correspondingNewResource != null) {
        Set<String> currentActions =
            correspondingNewResource.actions.stream()
                .map(action -> action.name)
                .collect(Collectors.toSet());

        for (Action oldAction : oldResource.actions) {
          if (!currentActions.contains(oldAction.name)) {
            lines.add(
                String.format(
                    "[list]Endpoint [code %s] removed from [link_api %s][code %s][].[]",
                    oldAction.id,
                    getDocsLinkForResourceList(correspondingNewResource),
                    correspondingNewResource.name));
          }
        }
      } else {
        for (Action action : oldResource.actions) {
          lines.add(
              String.format(
                  "[list]Endpoint [code %s] removed from [code %s].[]",
                  action.id, oldResource.name));
        }
      }
    }

    return new ArrayList<>(lines);
  }

  // --- Deleted Attributes ---

  private List<String> generateDeletedAttributes(
      List<Resource> oldResources, List<Resource> newResources) {
    Map<String, Resource> newResourceMap =
        newResources.stream()
            .collect(Collectors.toMap(resource -> resource.id, resource -> resource));
    Set<String> lines = new LinkedHashSet<>();

    for (Resource oldResource : oldResources) {
      Resource correspondingNewResource = newResourceMap.get(oldResource.id);

      if (correspondingNewResource != null) {
        Set<String> currentAttributes =
            correspondingNewResource.attributes().stream()
                .map(attribute -> attribute.name)
                .collect(Collectors.toSet());

        for (Attribute oldAttribute : oldResource.attributes()) {
          if (!currentAttributes.contains(oldAttribute.name)) {
            String anchor = oldResource.id + "_" + oldAttribute.name;
            lines.add(
                String.format(
                    "[list]Attribute [code %s] removed from the resource "
                        + "[link_api %s#%s][code %s][].[]",
                    oldAttribute.name,
                    getDocsLinkForResourceList(correspondingNewResource),
                    anchor,
                    correspondingNewResource.name));
          }
        }
      } else {
        for (Attribute attribute : oldResource.attributes()) {
          lines.add(
              String.format(
                  "[list]Attribute [code %s] removed from the resource [code %s].[]",
                  attribute.name, oldResource.name));
        }
      }
    }

    return new ArrayList<>(lines);
  }

  // --- Deleted Parameters ---

  private List<String> generateDeletedParameters(
      List<Resource> oldResources, List<Resource> newResources) {
    List<String> deletedQueryParameters =
        generateDeletedParameterLines(oldResources, newResources, Action::queryParameters);
    List<String> deletedBodyParameters =
        generateDeletedParameterLines(oldResources, newResources, Action::requestBodyParameters);

    return Stream.concat(deletedQueryParameters.stream(), deletedBodyParameters.stream())
        .collect(Collectors.toList());
  }

  private List<String> generateDeletedParameterLines(
      List<Resource> oldResources,
      List<Resource> newResources,
      Function<Action, List<Parameter>> parameterExtractor) {
    Map<String, Resource> newResourceMap =
        newResources.stream()
            .collect(Collectors.toMap(resource -> resource.id, resource -> resource));
    Set<String> lines = new LinkedHashSet<>();

    for (Resource oldResource : oldResources) {
      Resource correspondingNewResource = newResourceMap.get(oldResource.id);

      if (correspondingNewResource != null) {
        processDeletedParametersForExistingResource(
            oldResource, correspondingNewResource, parameterExtractor, lines);
      } else {
        processDeletedParametersForRemovedResource(oldResource, parameterExtractor, lines);
      }
    }

    return new ArrayList<>(lines);
  }

  private void processDeletedParametersForExistingResource(
      Resource oldResource,
      Resource newResource,
      Function<Action, List<Parameter>> parameterExtractor,
      Set<String> lines) {
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
            lines.add(
                formatDeletedParameterLine(newResource, correspondingNewAction, oldParameter, true));
          } else if (oldParameter.schema.getProperties() != null) {
            processNestedParameters(
                oldParameter.schema,
                oldParameter.getName(),
                currentParameters,
                newResource,
                correspondingNewAction,
                lines,
                false);
          }
        }
      } else {
        for (Parameter parameter : parameterExtractor.apply(oldAction)) {
          lines.add(formatDeletedParameterLine(newResource, oldAction, parameter, false));
        }
      }
    }
  }

  private void processDeletedParametersForRemovedResource(
      Resource oldResource,
      Function<Action, List<Parameter>> parameterExtractor,
      Set<String> lines) {
    for (Action action : oldResource.actions) {
      for (Parameter parameter : parameterExtractor.apply(action)) {
        lines.add(
            String.format(
                "[list]Input parameter [code %s] removed from the endpoint [code %s] in [code %s].[]",
                toBracketNotation(parameter.getName()), action.id, oldResource.name));
      }
    }
  }

  private String formatDeletedParameterLine(
      Resource resource, Action action, Parameter parameter, boolean includeLink) {
    String paramAnchor = parameter.getName().replace(".", "_");
    if (includeLink) {
      return String.format(
          "[list]Input parameter [code %s] removed from the endpoint "
              + "[link_api %s/%s#%s][code %s.%s][].[]",
          toBracketNotation(parameter.getName()),
          getDocsLinkForResourceList(resource),
          toHyphenCase(action.id),
          paramAnchor,
          resource.name,
          toHyphenCase(action.id));
    } else {
      return String.format(
          "[list]Input parameter [code %s] removed from the endpoint [code %s] in [code %s].[]",
          toBracketNotation(parameter.getName()), action.id, resource.name);
    }
  }

  // --- Deleted Events ---

  private List<String> generateDeletedEvents(Spec oldVersion, Spec newerVersion) {
    List<Map<String, String>> oldEvents = oldVersion.extractWebhookInfo(false);
    List<Map<String, String>> newEvents = newerVersion.extractWebhookInfo(false);

    Set<String> currentEventTypes =
        newEvents.stream().map(event -> event.get("type")).collect(Collectors.toSet());

    return oldEvents.stream()
        .filter(event -> !currentEventTypes.contains(event.get("type")))
        .map(
            event ->
                String.format(
                    "[list]Webhook [code %s] has been removed.[]", event.get("type")))
        .distinct()
        .collect(Collectors.toList());
  }

  // --- New Enum Values ---

  private List<String> generateNewEnumValues(
      List<Resource> oldResources, List<Resource> newResources, Spec oldSpec, Spec newSpec) {
    Set<String> lines = new LinkedHashSet<>();

    lines.addAll(generateAttributeEnumLines(oldResources, newResources, true));
    lines.addAll(generateParameterEnumLines(oldResources, newResources, true));

    return new ArrayList<>(lines);
  }

  // --- Deleted Enum Values ---

  private List<String> generateDeletedEnumValues(
      List<Resource> oldResources, List<Resource> newResources, Spec oldSpec, Spec newSpec) {
    Set<String> lines = new LinkedHashSet<>();

    lines.addAll(generateAttributeEnumLines(oldResources, newResources, false));
    lines.addAll(generateParameterEnumLines(oldResources, newResources, false));

    return new ArrayList<>(lines);
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

      if (attribute.isEnumAttribute()) {
        Set<String> comparisonValues =
            comparisonEnums.getOrDefault(currentPath, Collections.emptySet());
        List<String> changedValues =
            findChangedEnumValues(attribute.getEnum().values(), comparisonValues, isAdded);

        if (!changedValues.isEmpty()) {
          String verb = isAdded ? "added" : "removed";
          String preposition = isAdded ? "to" : "from";
          lines.add(
              String.format(
                  "[list]Enum value %s: %s %s the enum "
                      + "[link_api %s#%s][code %s.%s][].[]",
                  verb,
                  formatEnumCodeValues(changedValues),
                  preposition,
                  getDocsLinkForResourceObject(resource),
                  anchorId,
                  resource.id,
                  currentPath));
        }
      }

      if (attribute.getSubAttributes() != null) {
        processAttributeEnumChanges(
            resource, attribute.getSubAttributes(), currentPath, comparisonEnums, lines, isAdded);
      }
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
            resource, action, action.queryParameters(), QUERY_PREFIX, comparisonParameters, lines,
            isAdded);
        processParameterEnumChanges(
            resource, action, action.requestBodyParameters(), BODY_PREFIX, comparisonParameters,
            lines, isAdded);
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
        String parameterKey = prefix + ":" + parameter.getName();
        Set<String> comparisonValues =
            comparisonParameters.getOrDefault(parameterKey, Collections.emptySet());
        List<String> changedValues =
            findChangedEnumValues(parameter.getEnumValues(), comparisonValues, isAdded);

        if (!changedValues.isEmpty()) {
          addParameterEnumLine(lines, changedValues, resource, action, parameter, isAdded);
        }
      }

      if (parameter.schema.getProperties() != null) {
        processNestedParameterEnums(
            resource, action, parameter, prefix, comparisonParameters, lines, isAdded);
      }
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
                  Parameter nestedParameter = new Parameter(nestedParameterName, propertySchema);
                  addParameterEnumLine(
                      lines, changedValues, resource, action, nestedParameter, isAdded);
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
      boolean isAdded) {
    String verb = isAdded ? "added" : "removed";
    String preposition = isAdded ? "to" : "from";
    String paramAnchor = parameter.getName().replace(".", "_");
    lines.add(
        String.format(
            "[list]Enum value %s: %s %s the enum "
                + "[link_api %s/%s#%s][code %s.%s][].[]",
            verb,
            formatEnumCodeValues(values),
            preposition,
            getDocsLinkForResourceList(resource),
            toHyphenCase(action.id),
            paramAnchor,
            resource.name,
            toBracketNotation(parameter.getName())));
  }

  // --- Parameter Requirement Changes ---

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
            newResource, newAction, newAction.queryParameters(), QUERY_PREFIX,
            oldParameterRequirements, lines);
        checkRequirementChanges(
            newResource, newAction, newAction.requestBodyParameters(), BODY_PREFIX,
            oldParameterRequirements, lines);
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
        parameter.isRequired ? "`optional` to `required`" : "`required` to `optional`";
    String paramAnchor = parameter.getName().replace(".", "_");
    return String.format(
        "[list]Input parameter [code %s] has been changed from %s in the endpoint "
            + "[link_api %s/%s#%s][code %s][].[]",
        toBracketNotation(parameter.getName()),
        changeDescription,
        getDocsLinkForResourceList(resource),
        toHyphenCase(action.id),
        paramAnchor,
        toHyphenCase(action.id));
  }

  // --- Utility: Maps & Sets ---

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
