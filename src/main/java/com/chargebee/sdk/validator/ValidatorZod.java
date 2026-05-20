package com.chargebee.sdk.validator;

import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.validator.emitter.zod.ZodTsEmitter;
import com.chargebee.sdk.validator.ir.SharedSchemaRegistry;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Language implementation that generates Zod validation files for chargebee-node.
 * Invoked via {@code -l VALIDATOR_ZOD}.
 */
public class ValidatorZod extends Language {

  @Override
  protected List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) {
    SharedSchemaRegistry registry = new SharedSchemaRegistry();
    return new ZodTsEmitter().emit(spec, registry, outputDirectoryPath);
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
