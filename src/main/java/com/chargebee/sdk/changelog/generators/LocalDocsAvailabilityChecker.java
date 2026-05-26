package com.chargebee.sdk.changelog.generators;

import com.chargebee.sdk.changelog.models.ChangeLogEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies whether a {@link ChangeLogEntry} refers to an entity that actually exists in the local
 * documentation repository (default: {@code ~/work/cb-api-documentation/cb-app/v2-pcv2}).
 *
 * <p>Layout assumptions, all relative to the docs root:
 *
 * <ul>
 *   <li>Resource directory: {@code <resourceId>/}
 *   <li>Resource attributes: {@code <resourceId>/resource.yaml} containing {@code slugId} entries
 *   <li>Action: {@code <resourceId>/<actionId>.yaml} containing {@code slugId} entries for params
 *   <li>Webhook event types: {@code event/event_types.yaml} containing {@code slugId} entries
 * </ul>
 *
 * Override the location with {@code CHANGELOG_DOCS_REPO_PATH=/abs/path/to/v2-pcv2}.
 *
 * <p>Set {@code CHANGELOG_DOCS_SKIP_VERIFY=true} to bypass checks entirely.
 */
public class LocalDocsAvailabilityChecker {

  static final String DEFAULT_DOCS_ROOT_PROPERTY = "user.home";
  static final String DEFAULT_DOCS_RELATIVE_PATH = "work/cb-api-documentation/cb-app/v2-pcv2";
  static final String RESOURCE_YAML = "resource.yaml";
  static final String EVENT_TYPES_RELATIVE_PATH = "event/event_types.yaml";
  static final String TOC_RELATIVE_PATH = "TOC.yaml";
  static final String DOCS_API_HREF_PREFIX = "/docs/api/";

  private static final Pattern SLUG_PATTERN =
      Pattern.compile("slugId:\\s*\"?([A-Za-z0-9._-]+)\"?");
  private static final Pattern TOC_HREF_PATTERN =
      Pattern.compile("href:\\s*\"?(/docs/api/[A-Za-z0-9_-]+)\"?");

  private final Path docsRoot;
  private final boolean skipVerification;
  private final Map<Path, Set<String>> slugCache = new ConcurrentHashMap<>();
  private volatile Set<String> publishedResourcePaths;

  public LocalDocsAvailabilityChecker() {
    this(resolveDefaultDocsRoot());
  }

  public LocalDocsAvailabilityChecker(Path docsRoot) {
    this.docsRoot = docsRoot;
    this.skipVerification = Boolean.parseBoolean(System.getenv("CHANGELOG_DOCS_SKIP_VERIFY"));
  }

  private static Path resolveDefaultDocsRoot() {
    String override = System.getenv("CHANGELOG_DOCS_REPO_PATH");
    if (override != null && !override.isBlank()) {
      return Paths.get(override);
    }
    return Paths.get(System.getProperty(DEFAULT_DOCS_ROOT_PROPERTY), DEFAULT_DOCS_RELATIVE_PATH);
  }

  public Path docsRoot() {
    return docsRoot;
  }

  /** Returns {@code true} when the entity referenced by the entry exists in the local docs. */
  public boolean isEntryAvailable(ChangeLogEntry entry) {
    if (skipVerification || entry == null || entry.getType() == null) {
      return true;
    }
    switch (entry.getType()) {
      case NEW_RESOURCE:
        return isResourcePublished(entry.getDocsResourcePath())
            && resourceDirExists(entry.getResourceId());
      case NEW_ACTION:
        return isResourcePublished(entry.getDocsResourcePath())
            && actionFileExists(entry.getResourceId(), entry.getActionId());
      case NEW_ATTRIBUTE:
        return isResourcePublished(entry.getDocsResourcePath())
            && slugExistsInResource(entry.getResourceId(), entry.getSlugPath());
      case NEW_PARAMETER:
      case PARAMETER_REQUIREMENT_CHANGE:
        return isResourcePublished(entry.getDocsResourcePath())
            && slugExistsInAction(
                entry.getResourceId(), entry.getActionId(), entry.getSlugPath());
      case NEW_EVENT_TYPE:
        return slugExistsInEventTypes(entry.getEventType());
      case NEW_ATTRIBUTE_ENUM_VALUE:
        return isResourcePublished(entry.getDocsResourcePath())
            && allEnumSlugsInResource(
                entry.getResourceId(), entry.getSlugPath(), entry.getEnumValues());
      case NEW_PARAMETER_ENUM_VALUE:
        return isResourcePublished(entry.getDocsResourcePath())
            && allEnumSlugsInAction(
                entry.getResourceId(),
                entry.getActionId(),
                entry.getSlugPath(),
                entry.getEnumValues());
      case DELETED_RESOURCE:
      case DELETED_EVENT_TYPE:
        // Deletion lines are not links; they are informational and always emitted.
        return true;
      case DELETED_ACTION:
      case DELETED_ATTRIBUTE:
      case DELETED_ATTRIBUTE_ENUM_VALUE:
        // Linked to the parent resource page; emit when the resource is still published.
        return entry.getDocsResourcePath() == null
            || isResourcePublished(entry.getDocsResourcePath());
      case DELETED_PARAMETER:
      case DELETED_PARAMETER_ENUM_VALUE:
        // Linked to an action page; emit when the action page still exists and is published.
        if (entry.getDocsResourcePath() == null
            || entry.getResourceId() == null
            || entry.getActionId() == null) {
          return true;
        }
        return isResourcePublished(entry.getDocsResourcePath())
            && actionFileExists(entry.getResourceId(), entry.getActionId());
      default:
        return true;
    }
  }

  /** Builds a human-readable description of what is missing for a given entry. */
  public String describeMissing(ChangeLogEntry entry) {
    if (entry == null || entry.getType() == null) {
      return "unknown entry";
    }
    // When the resource isn't published in TOC at all, surface that first—nothing else can match.
    if (requiresPublishedResource(entry.getType())
        && entry.getDocsResourcePath() != null
        && !isResourcePublished(entry.getDocsResourcePath())) {
      return "resource '"
          + entry.getDocsResourcePath()
          + "' not listed in '"
          + TOC_RELATIVE_PATH
          + "' (no published "
          + DOCS_API_HREF_PREFIX
          + entry.getDocsResourcePath()
          + " page)";
    }
    Path relative;
    switch (entry.getType()) {
      case NEW_RESOURCE:
      case DELETED_ACTION:
      case DELETED_ATTRIBUTE:
      case DELETED_ATTRIBUTE_ENUM_VALUE:
        relative = Paths.get(nullToEmpty(entry.getResourceId()));
        return "resource dir '" + relative + "'";
      case NEW_ACTION:
      case DELETED_PARAMETER:
      case DELETED_PARAMETER_ENUM_VALUE:
        relative =
            Paths.get(nullToEmpty(entry.getResourceId()), nullToEmpty(entry.getActionId()) + ".yaml");
        return "action file '" + relative + "'";
      case NEW_ATTRIBUTE:
        relative = Paths.get(nullToEmpty(entry.getResourceId()), RESOURCE_YAML);
        return "attribute slug '" + entry.getSlugPath() + "' in '" + relative + "'";
      case NEW_PARAMETER:
      case PARAMETER_REQUIREMENT_CHANGE:
        relative =
            Paths.get(nullToEmpty(entry.getResourceId()), nullToEmpty(entry.getActionId()) + ".yaml");
        return "parameter slug '" + entry.getSlugPath() + "' in '" + relative + "'";
      case NEW_EVENT_TYPE:
        return "event type '" + entry.getEventType() + "' in '" + EVENT_TYPES_RELATIVE_PATH + "'";
      case NEW_ATTRIBUTE_ENUM_VALUE:
        relative = Paths.get(nullToEmpty(entry.getResourceId()), RESOURCE_YAML);
        return "enum values "
            + entry.getEnumValues()
            + " for slug '"
            + entry.getSlugPath()
            + "' in '"
            + relative
            + "'";
      case NEW_PARAMETER_ENUM_VALUE:
        relative =
            Paths.get(nullToEmpty(entry.getResourceId()), nullToEmpty(entry.getActionId()) + ".yaml");
        return "enum values "
            + entry.getEnumValues()
            + " for slug '"
            + entry.getSlugPath()
            + "' in '"
            + relative
            + "'";
      default:
        return "entry of type " + entry.getType();
    }
  }

  private boolean requiresPublishedResource(ChangeLogEntry.EntryType type) {
    switch (type) {
      case NEW_RESOURCE:
      case NEW_ACTION:
      case NEW_ATTRIBUTE:
      case NEW_PARAMETER:
      case NEW_ATTRIBUTE_ENUM_VALUE:
      case NEW_PARAMETER_ENUM_VALUE:
      case PARAMETER_REQUIREMENT_CHANGE:
      case DELETED_ACTION:
      case DELETED_ATTRIBUTE:
      case DELETED_ATTRIBUTE_ENUM_VALUE:
      case DELETED_PARAMETER:
      case DELETED_PARAMETER_ENUM_VALUE:
        return true;
      default:
        return false;
    }
  }

  private boolean isResourcePublished(String docsResourcePath) {
    if (docsResourcePath == null || docsResourcePath.isBlank()) {
      return true;
    }
    return loadPublishedResourcePaths().contains(docsResourcePath);
  }

  Set<String> loadPublishedResourcePaths() {
    Set<String> cached = publishedResourcePaths;
    if (cached != null) {
      return cached;
    }
    synchronized (this) {
      if (publishedResourcePaths != null) {
        return publishedResourcePaths;
      }
      Set<String> paths = new HashSet<>();
      Path toc = docsRoot.resolve(TOC_RELATIVE_PATH);
      if (Files.isRegularFile(toc)) {
        try {
          for (String line : Files.readAllLines(toc)) {
            Matcher matcher = TOC_HREF_PATTERN.matcher(line);
            if (matcher.find()) {
              String href = matcher.group(1);
              paths.add(href.substring(DOCS_API_HREF_PREFIX.length()));
            }
          }
        } catch (IOException ignored) {
          // Treat unreadable TOC as empty (everything will be marked missing-by-toc).
        }
      }
      publishedResourcePaths = Set.copyOf(paths);
      return publishedResourcePaths;
    }
  }

  private boolean resourceDirExists(String resourceId) {
    if (resourceId == null || resourceId.isBlank()) {
      return true;
    }
    return Files.isDirectory(docsRoot.resolve(resourceId));
  }

  private boolean actionFileExists(String resourceId, String actionId) {
    if (resourceId == null || actionId == null) {
      return true;
    }
    Path yaml = docsRoot.resolve(resourceId).resolve(actionId + ".yaml");
    return Files.isRegularFile(yaml);
  }

  private boolean slugExistsInResource(String resourceId, String slug) {
    if (resourceId == null || slug == null) {
      return true;
    }
    return loadSlugs(docsRoot.resolve(resourceId).resolve(RESOURCE_YAML)).contains(slug);
  }

  private boolean slugExistsInAction(String resourceId, String actionId, String slug) {
    if (resourceId == null || actionId == null || slug == null) {
      return true;
    }
    Path yaml = docsRoot.resolve(resourceId).resolve(actionId + ".yaml");
    return loadSlugs(yaml).contains(slug);
  }

  private boolean slugExistsInEventTypes(String eventType) {
    if (eventType == null) {
      return true;
    }
    return loadSlugs(docsRoot.resolve(EVENT_TYPES_RELATIVE_PATH)).contains(eventType);
  }

  private boolean allEnumSlugsInResource(String resourceId, String slug, List<String> values) {
    if (resourceId == null || slug == null || values == null || values.isEmpty()) {
      return true;
    }
    Set<String> slugs = loadSlugs(docsRoot.resolve(resourceId).resolve(RESOURCE_YAML));
    if (!slugs.contains(slug)) {
      return false;
    }
    for (String value : values) {
      if (!slugs.contains(slug + ".enum." + value)) {
        return false;
      }
    }
    return true;
  }

  private boolean allEnumSlugsInAction(
      String resourceId, String actionId, String slug, List<String> values) {
    if (resourceId == null || actionId == null || slug == null || values == null || values.isEmpty()) {
      return true;
    }
    Set<String> slugs = loadSlugs(docsRoot.resolve(resourceId).resolve(actionId + ".yaml"));
    if (!slugs.contains(slug)) {
      return false;
    }
    for (String value : values) {
      if (!slugs.contains(slug + ".enum." + value)) {
        return false;
      }
    }
    return true;
  }

  Set<String> loadSlugs(Path file) {
    return slugCache.computeIfAbsent(
        file,
        path -> {
          if (!Files.isRegularFile(path)) {
            return Collections.emptySet();
          }
          try {
            Set<String> slugs = new LinkedHashSet<>();
            for (String line : Files.readAllLines(path)) {
              Matcher matcher = SLUG_PATTERN.matcher(line);
              if (matcher.find()) {
                slugs.add(matcher.group(1));
              }
            }
            return slugs;
          } catch (IOException e) {
            return Collections.emptySet();
          }
        });
  }

  /** Returns missing-entry descriptions for a collection of entries, preserving insertion order. */
  Set<String> describeMissingDocs(Iterable<ChangeLogEntry> entries) {
    Set<String> descriptions = new LinkedHashSet<>();
    if (entries == null) {
      return descriptions;
    }
    for (ChangeLogEntry entry : entries) {
      if (!isEntryAvailable(entry)) {
        descriptions.add(describeMissing(entry));
      }
    }
    return descriptions;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  // visible for tests
  Map<Path, Set<String>> cacheSnapshot() {
    return Map.copyOf(slugCache);
  }

  // visible for tests
  static boolean hasMetadata(ChangeLogEntry entry) {
    return Objects.nonNull(entry) && Objects.nonNull(entry.getType());
  }

  // visible for tests
  Set<String> distinctSlugsAcross(Iterable<Path> files) {
    Set<String> all = new HashSet<>();
    if (files == null) {
      return all;
    }
    for (Path path : files) {
      all.addAll(loadSlugs(path));
    }
    return all;
  }
}
