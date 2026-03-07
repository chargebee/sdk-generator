package com.chargebee.sdk.php.v4.generators;

import static com.chargebee.GenUtil.firstCharUpper;
import static com.chargebee.GenUtil.toCamelCase;
import static com.chargebee.sdk.php.v4.Constants.*;

import com.chargebee.openapi.Enum;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.php.v4.PHP_V4;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ClassBasedEnumFileGenerator implements FileGenerator {

  private final PHP_V4 phpGenerator;
  private final Template enumTemplate;

  public ClassBasedEnumFileGenerator(PHP_V4 phpGenerator) {
    this.phpGenerator = phpGenerator;
    this.enumTemplate = phpGenerator.getTemplateContent(CLASS_BASED_ENUM);
  }

  @Override
  public List<FileOp> generate(String outputPath, List<?> items) throws IOException {
    List<Enum> enums = (List<Enum>) items;
    List<FileOp> fileOps = new ArrayList<>();
    enums = enums.stream().sorted(Comparator.comparing(e -> e.name)).collect(Collectors.toList());
    for (Enum enumObj : enums) {
      Map<String, Object> enumMap = createEnumMap(enumObj, CHARGEBEE_CLASS_BASED_ENUMS);
      String content = enumTemplate.apply(enumMap);
      String fileName = enumObj.name + PHP_FILE_NAME_EXTENSION;
      fileOps.add(new FileOp.WriteString(outputPath, fileName, content));
    }

    return fileOps;
  }

  private Map<String, Object> createEnumMap(Enum enumObj, String namespace) {
    return Map.of(
        "name",
        firstCharUpper(toCamelCase(enumObj.name)),
        "possibleValues",
        enumObj.validValues().stream()
            .map(value -> Map.of("name", value))
            .collect(Collectors.toList()),
        "deprecatedEnums",
        !enumObj.deprecatedValues().isEmpty()
            ? enumObj.deprecatedValues().stream()
                .map(value -> Map.of("name", value))
                .collect(Collectors.toList())
            : new ArrayList<>(),
        "IsParamBlankOption",
        enumObj.isParamBlankOption(),
        "namespace",
        namespace);
  }
}
