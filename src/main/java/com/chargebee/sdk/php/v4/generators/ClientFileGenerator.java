package com.chargebee.sdk.php.v4.generators;

import static com.chargebee.GenUtil.toCamelCase;
import static com.chargebee.sdk.php.v4.Constants.*;

import com.chargebee.openapi.Resource;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.php.v4.PHP_V4;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClientFileGenerator implements FileGenerator {
  public static final String ACTIONS = "Actions";
  private final PHP_V4 phpGenerator;
  private final Template clientTemplate;

  public ClientFileGenerator(PHP_V4 phpGenerator) {
    this.phpGenerator = phpGenerator;
    this.clientTemplate = phpGenerator.getTemplateContent(CHARGEBEE_CLIENT);
  }

  @Override
  public List<FileOp> generate(String outputPath, List<?> items) {
    throw new UnsupportedOperationException("Use generateSingle instead");
  }

  @Override
  public FileOp generateSingle(String outputPath, List<?> items) throws IOException {
    List<Resource> resources = (List<Resource>) items;
    List<String> resourceNames =
        resources.stream()
            .filter(r -> !r.actions.isEmpty())
            .map(r -> r.id)
            .collect(Collectors.toList());

    List<String> imports =
        resources.stream()
            .filter(r -> !r.actions.isEmpty())
            .flatMap(
                r ->
                    Stream.of(
                        ACTIONS_NAMESPACE
                            + BACK_SLASH
                            + CONTRACTS
                            + BACK_SLASH
                            + toCamelCase(r.name)
                            + ACTIONS
                            + INTERFACE
                            + SEMICOLON,
                        ACTIONS_NAMESPACE + BACK_SLASH + toCamelCase(r.name) + ACTIONS + SEMICOLON))
            .collect(Collectors.toList());
    Map<String, Object> contents =
        Map.of(
            RESOURCES_NAME_LIST, resourceNames,
            IMPORT_LIST, imports);
    String content = clientTemplate.apply(contents);

    return new FileOp.WriteString(outputPath, CHARGEBEE_CLIENT_PHP, content);
  }
}
