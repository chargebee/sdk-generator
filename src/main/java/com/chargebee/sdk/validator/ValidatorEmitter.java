package com.chargebee.sdk.validator;

import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.validator.ir.SharedSchemaRegistry;
import java.util.List;

/**
 * Contract for all language-specific validator emitters.
 * An emitter walks the spec (and its pre-built IR/SharedSchemaRegistry) and
 * produces a list of {@link FileOp} instances representing files to write.
 */
public interface ValidatorEmitter {
  List<FileOp> emit(Spec spec, SharedSchemaRegistry registry, String outputDir);
}
