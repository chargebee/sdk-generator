package com.chargebee.sdk.validator;

import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.validator.emitter.joi.JoiEmitter;
import com.chargebee.sdk.validator.ir.SharedSchemaRegistry;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Language implementation that generates Joi validation files for chargebee-node.
 * Plugs into the existing {@link Language} / CLI framework via {@code -l VALIDATOR_JOI}.
 */
public class ValidatorJoi extends Language {

  @Override
  protected List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    SharedSchemaRegistry registry = new SharedSchemaRegistry();
    JoiEmitter emitter = new JoiEmitter();
    return emitter.emit(spec, registry, outputDirectoryPath);
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    // No Handlebars templates used – all generation is AST-based
    return Map.of();
  }

  @Override
  public String dataType(Schema<?> schema) {
    return null;
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return true;
  }
}
