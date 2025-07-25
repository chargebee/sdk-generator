package com.chargebee.openapi;

import com.chargebee.QAModeHandler;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.*;
import java.util.stream.Collectors;

public class Spec {
  private final OpenAPI openAPI;
  private final Version version;

  public Spec(OpenAPI openAPI) {
    this.openAPI = openAPI;
    this.version = Version.get(openAPI.getInfo());
  }

  private static Map<String, List<Action>> getAllActions(OpenAPI openAPI) {
    if (openAPI.getPaths() == null) {
      return Map.of();
    }
    return openAPI.getPaths().entrySet().stream()
        .map(
            pathItem -> {
              var actions = new ArrayList<Action>();
              if (pathItem.getValue().getGet() != null) {
                actions.add(
                    new Action(
                        HttpRequestType.GET, pathItem.getValue().getGet(), pathItem.getKey()));
              }
              if (pathItem.getValue().getPost() != null) {
                actions.add(
                    new Action(
                        HttpRequestType.POST, pathItem.getValue().getPost(), pathItem.getKey()));
              }
              return actions;
            })
        .flatMap(Collection::stream)
        .collect(Collectors.groupingBy(Action::resourceId));
  }

  public void enableForQa() {
    QAModeHandler.getInstance().setValue(true);
  }

  public List<Resource> resources() {
    if (openAPI.getComponents() == null) {
      return List.of();
    }
    if (openAPI.getComponents().getSchemas() == null) {
      return List.of();
    }
    Map<String, List<Action>> actions = getAllActions(openAPI);
    return openAPI.getComponents().getSchemas().entrySet().stream()
        .filter(entry -> Resource.resourceId(entry.getValue()) != null)
        .map(
            entry -> {
              var resourceActions = actions.get(Resource.resourceId(entry.getValue()));
              if (resourceActions == null) {
                resourceActions = List.of();
              }
              if (QAModeHandler.getInstance().getValue()) {
                return new Resource(entry.getKey(), entry.getValue(), resourceActions)
                    .enableForQa();
              }
              return new Resource(entry.getKey(), entry.getValue(), resourceActions);
            })
        .filter(Resource::isNotHiddenFromSDKGeneration)
        .filter(Resource::isNotThirdPartyResource)
        .sorted(Comparator.comparing(resource -> resource.name))
        .toList();
  }

  public List<Resource> pcAwareResources() {
    return resources().stream()
        .filter(
            r ->
                r.productCatalogVersion().isEmpty()
                    || r.productCatalogVersion().get() == version.productCatalogVersion)
        .filter(r -> r.isNotHiddenFromSDKGeneration())
        .toList();
  }

  public List<Enum> globalEnums() {
    return Enum.globalEnums(openAPI);
  }
}
