package com.chargebee.sdk.changelog.generators;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies whether the docs page referenced by a generated changelog line actually exists. Results
 * are cached per path so each unique URL is hit at most once.
 *
 * <p>Set the env var {@code CHANGELOG_DOCS_SKIP_VERIFY=true} to bypass network checks (every line
 * will be treated as available).
 */
public class DocsAvailabilityChecker {

  static final String BASE_URL = "https://apidocs.chargebee.com/docs/api/";
  private static final Pattern LINK_API_PATTERN = Pattern.compile("\\[link_api ([^\\]]+)\\]");
  private static final int WARMUP_PARALLELISM = 16;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private final HttpClient httpClient;
  private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
  private final boolean skipVerification;

  public DocsAvailabilityChecker() {
    this.skipVerification = Boolean.parseBoolean(System.getenv("CHANGELOG_DOCS_SKIP_VERIFY"));
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  /** Returns the docs path referenced by the first {@code [link_api ...]} token, or {@code null}. */
  public static String extractDocsPath(String line) {
    Matcher matcher = LINK_API_PATTERN.matcher(line);
    if (!matcher.find()) {
      return null;
    }
    String path = matcher.group(1);
    int hashIdx = path.indexOf('#');
    return hashIdx >= 0 ? path.substring(0, hashIdx) : path;
  }

  /**
   * Pre-checks all distinct docs paths referenced by the given lines in parallel so subsequent
   * {@link #isLineDocsAvailable(String)} calls hit the cache.
   */
  public void warmUp(Collection<String> lines) {
    if (skipVerification) {
      return;
    }
    Set<String> paths = new LinkedHashSet<>();
    for (String line : lines) {
      String path = extractDocsPath(line);
      if (path != null && !cache.containsKey(path)) {
        paths.add(path);
      }
    }
    if (paths.isEmpty()) {
      return;
    }
    ExecutorService pool =
        Executors.newFixedThreadPool(Math.min(WARMUP_PARALLELISM, paths.size()));
    try {
      CompletableFuture.allOf(
              paths.stream()
                  .map(path -> CompletableFuture.runAsync(() -> isPathAvailable(path), pool))
                  .toArray(CompletableFuture[]::new))
          .join();
    } finally {
      pool.shutdown();
    }
  }

  public boolean isLineDocsAvailable(String line) {
    if (skipVerification) {
      return true;
    }
    String path = extractDocsPath(line);
    if (path == null) {
      return true;
    }
    return isPathAvailable(path);
  }

  public String resolveDocsUrl(String line) {
    String path = extractDocsPath(line);
    return path == null ? null : BASE_URL + path;
  }

  boolean isPathAvailable(String path) {
    return cache.computeIfAbsent(path, this::fetchAvailability);
  }

  private boolean fetchAvailability(String path) {
    String url = BASE_URL + path;
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(REQUEST_TIMEOUT)
              .method("HEAD", HttpRequest.BodyPublishers.noBody())
              .build();
      HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      int code = response.statusCode();
      return code >= 200 && code < 400;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return true;
    }
  }

  Set<String> missingPathsFor(Collection<String> lines) {
    Set<String> missing = new LinkedHashSet<>();
    for (String line : lines) {
      String path = extractDocsPath(line);
      if (path != null && !isPathAvailable(path)) {
        missing.add(path);
      }
    }
    return missing;
  }

  // visible for tests
  Map<String, Boolean> cacheSnapshot() {
    return Map.copyOf(cache);
  }

  // visible for tests
  static boolean isCacheableLine(String line) {
    return Objects.nonNull(extractDocsPath(line));
  }
}
