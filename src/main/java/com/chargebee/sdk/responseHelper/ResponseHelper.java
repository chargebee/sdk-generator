package com.chargebee.sdk.responseHelper;

import com.chargebee.openapi.Resource;
import com.chargebee.openapi.parameter.Response;
import com.chargebee.sdk.DataType;
import io.swagger.v3.oas.models.media.ArraySchema;
import java.util.*;
import java.util.stream.Collectors;

public class ResponseHelper {
  private final List<Resource> resourceList;

  public ResponseHelper(List<Resource> resourceList) {
    this.resourceList = resourceList;
  }

  public List<Response> listResponses() {
    return resourceList.stream()
        .flatMap(
            resource ->
                resource.responseList().stream()
                    .filter(response -> response.schema instanceof ArraySchema))
        .toList();
  }

  public static boolean isTypeDefined(Map<String, Object> response) {
    return !response.get("type").toString().contains("unknown");
  }

  public Set<String> listResponseSet(List<Map<String, Object>> listResponses) {
    return listResponses.stream()
        .map(map -> map.get("referredResourceName").toString())
        .collect(Collectors.toSet());
  }

  public boolean isListResponse(List<Map<String, Object>> listResponses, Map<String, Object> r) {
    return listResponseSet(listResponses).contains(r.get("name"));
  }

  public List<String> jsonResponse(DataType lang) {
    Set<String> uniqueFields = new HashSet<>();
    for (var resource : resourceList) {
      for (var response : resource.responseList()) {
        if (response.templateParams(lang).get("type").equals("unknown[]")
            || response.templateParams(lang).get("type").equals("unknown")) {
          uniqueFields.add((String) response.templateParams(lang).get("name"));
        }
      }
    }
    return new ArrayList<>(uniqueFields);
  }
}
