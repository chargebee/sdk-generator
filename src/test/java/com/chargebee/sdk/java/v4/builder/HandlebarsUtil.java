package com.chargebee.sdk.java.v4.builder;

import com.chargebee.handlebar.*;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.helper.StringHelpers;

public class HandlebarsUtil {

  public static void registerAllHelpers(Handlebars handlebars) {
    handlebars.registerHelper("tokenBasedSplitTextToLines", new TokenBasedSplitTextToLinesHelper());
    handlebars.registerHelper("neq", ConditionalHelpers.neq);
    handlebars.registerHelper("eq", ConditionalHelpers.eq);
    handlebars.registerHelper("upper", StringHelpers.upper);
    handlebars.registerHelper("lower", StringHelpers.lower);
    handlebars.registerHelper("and", ConditionalHelpers.and);
    handlebars.registerHelper("or", ConditionalHelpers.or);
    handlebars.registerHelper("not", ConditionalHelpers.not);
    handlebars.registerHelper("camelCaseToPascalCase", NameFormatHelpers.CAMEL_CASE_TO_PASCAL_CASE);
    handlebars.registerHelper("camelCaseToSnakeCase", NameFormatHelpers.CAMEL_CASE_TO_SNAKE_CASE);
    handlebars.registerHelper("snakeCaseToCamelCase", NameFormatHelpers.SNAKE_CASE_TO_CAMEL_CASE);
    handlebars.registerHelper("pascalCaseToSnakeCase", NameFormatHelpers.PASCAL_CASE_TO_SNAKE_CASE);
    handlebars.registerHelper("pascalCaseToCamelCase", NameFormatHelpers.PASCAL_CASE_TO_CAMEL_CASE);
    handlebars.registerHelper("snakeCaseToPascalCase", NameFormatHelpers.SNAKE_CASE_TO_PASCAL_CASE);
    handlebars.registerHelper("golangCase", NameFormatHelpers.PASCAL_CASE_TO_GO_CASE);
    handlebars.registerHelper("toUpperCase", NameFormatHelpers.TO_UPPER_CASE);
    handlebars.registerHelper("camelCase", NameFormatHelpers.CAMEL_CASE);
    handlebars.registerHelper("pascalCase", NameFormatHelpers.TO_PASCAL);
    handlebars.registerHelper(
        "operationNameToPascalCase", NameFormatHelpers.OPERATION_NAME_TO_PASCAL_CASE);
    handlebars.registerHelper(
        "snakeCaseToPascalCaseAndSingularize",
        NameFormatHelpers.SNAKE_CASE_TO_PASCAL_CASE_AND_SINGULARIZE);
    handlebars.registerHelper(
        "snakeCaseToCamelCaseAndSingularize",
        NameFormatHelpers.SNAKE_CASE_TO_CAMEL_CASE_AND_SINGULARIZE);
    handlebars.registerHelper(
        "pascalCaseToCamelCaseAndPluralize",
        NameFormatHelpers.PASCAL_CASE_TO_CAMEL_CASE_AND_PLURALIZE);
    handlebars.registerHelper(
        "pascalCaseToSnakeCaseAndPluralize",
        NameFormatHelpers.PASCAL_CASE_TO_SNAKE_CASE_AND_PLURALIZE);
    handlebars.registerHelper("pluralize", NameFormatHelpers.PLURALIZE);
    handlebars.registerHelper("singularize", NameFormatHelpers.SINGULARIZE);
    handlebars.registerHelper("in", ArrayHelpers.IN);
    handlebars.registerHelper("curly", SpecialCharacters.CURLY_BRACKETS);
    handlebars.registerHelper("backslash", SpecialCharacters.BACK_SLASH);
    handlebars.registerHelper("includeFile", IncludeFileHelpers.INCLUDE_FILE);
  }
}
