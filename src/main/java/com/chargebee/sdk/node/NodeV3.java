package com.chargebee.sdk.node;

import com.chargebee.openapi.Resource;
import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.util.*;

public class NodeV3 extends Language {
  protected final String[] hiddenOverride = {"media"};

  @Override
  protected List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    var resources =
        spec.resources().stream()
            .filter(Resource::isNotDependentResource)
            .filter(resource -> !Arrays.stream(this.hiddenOverride).toList().contains(resource.id))
            .sorted(Comparator.comparing(Resource::sortOrder))
            .toList();
    return List.of(generateApiEndpointsFile(outputDirectoryPath, resources));
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of("api_endpoints", "/templates/node/api_endpoints.ts.hbs");
  }

  private FileOp generateApiEndpointsFile(String resourcesDirectoryPath, List<Resource> resources)
      throws IOException {
    List<Map<String, Object>> resourcesMap =
        resources.stream().map(resource -> resource.templateParams(this)).toList();
    Map templateParams = Map.of("resources", resourcesMap);
    Template resourceTemplate = getTemplateContent("api_endpoints");
    return new FileOp.WriteString(
        resourcesDirectoryPath, "api_endpoints.ts", resourceTemplate.apply(templateParams));
  }
}
