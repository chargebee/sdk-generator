package com.chargebee.sdk;

import com.chargebee.openapi.Resource;
import com.chargebee.openapi.parameter.Response;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;

public interface DataType {
  String dataType(Schema<?> schema);

  String listDataType(List<Response> responseParameters);

  Map<String, Object> additionalTemplateParams(Resource resource);
}
