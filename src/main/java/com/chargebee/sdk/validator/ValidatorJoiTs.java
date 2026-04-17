package com.chargebee.sdk.validator;

import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.validator.emitter.joits.JoiTsEmitter;
import com.chargebee.sdk.validator.ir.SharedSchemaRegistry;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Language implementation that generates TypeScript Joi validation files.
 * Invoked via {@code -l VALIDATOR_JOI_TS}.
 */
public class ValidatorJoiTs extends Language {

  @Override
  protected List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) {
    SharedSchemaRegistry registry = new SharedSchemaRegistry();
    JoiTsEmitter emitter = new JoiTsEmitter();
    return emitter.emit(spec, registry, outputDirectoryPath);
  }

  @Override
  protected Map<String, String> templatesDefinition() {
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
