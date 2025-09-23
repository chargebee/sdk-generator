package com.chargebee.sdk.java.javanext.builder;

import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.java.javanext.JavaFormatter;
import com.chargebee.sdk.java.javanext.core.EnumFields;
import com.chargebee.sdk.java.javanext.core.Field;
import com.chargebee.sdk.java.javanext.core.Model;
import com.chargebee.sdk.java.javanext.core.TypeMapper;
import com.chargebee.sdk.java.javanext.datatype.FieldType;
import com.chargebee.sdk.java.javanext.datatype.ListType;
import com.chargebee.sdk.java.javanext.datatype.ObjectType;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

public class ModelBuilder {

  private Template template;
  private String outputDirectoryPath;
  private OpenAPI openApi;

  private final List<FileOp> fileOps = new ArrayList<>();

  public ModelBuilder withOutputDirectoryPath(String outputDirectoryPath) {
    this.outputDirectoryPath = outputDirectoryPath + "/core/models";
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  public ModelBuilder withTemplate(Template template) {
    this.template = template;
    return this;
  }

  public List<FileOp> build(OpenAPI openApi) {
    this.openApi = openApi;
    generateModels();
    return fileOps;
  }

  private void generateModels() {
    try {
      var models = getModels();
      for (var entry : models.entrySet()) {
        Model model = new Model();
        model.setPackageName(entry.getKey());
        model.setName(entry.getKey());
        model.setFields(getFields(entry.getValue()));
        model.setImports(getImports(entry.getValue()));
        model.setEnumFields(getEnumFields(entry.getValue()));
        model.setSubModels(getSubModels(entry.getValue()));
        var content = template.apply(model);
        var formattedContent = JavaFormatter.formatSafely(content);
        String packageDirName = toLowerCamel(entry.getKey());
        fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, packageDirName));
        fileOps.add(
            new FileOp.WriteString(
                this.outputDirectoryPath + "/" + packageDirName,
                model.getName() + ".java",
                formattedContent));
      }
    } catch (IOException e) {
      System.err.println("Error generating models: " + e.getMessage());
    }
  }

  private static String toLowerCamel(String name) {
    if (name == null || name.isEmpty()) return name;
    if (name.indexOf('_') >= 0) {
      return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name);
    }
    return Character.isUpperCase(name.charAt(0))
        ? CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, name)
        : name;
  }

  private List<Model> getSubModels(Schema schema) {
    var subModels = new ArrayList<Model>();
    if (schema.getProperties() == null) {
      return subModels;
    }
    for (var fieldName : schema.getProperties().keySet()) {
      Schema schemaDefn = (Schema) schema.getProperties().get(fieldName);
      FieldType fieldType = TypeMapper.getJavaType(fieldName.toString(), schemaDefn);
      if (fieldType instanceof ObjectType) {
        if (schemaDefn.getProperties() == null || schemaDefn.getProperties().isEmpty()) {
          continue;
        }
        subModels.add(createSubModel(fieldName.toString(), schemaDefn));
      } else if (fieldType instanceof ListType) {
        var listSchema = schemaDefn.getItems();
        if (listSchema == null
            || ((Schema<?>) listSchema).getProperties() == null
            || ((Schema) listSchema).getProperties().isEmpty()) {
          continue;
        }
        subModels.add(createSubModel(fieldName.toString(), listSchema));
      }
    }
    return subModels;
  }

  private List<String> getImports(Schema<?> schema) {
    Set<String> fqns = new HashSet<>();
    collectImports(schema, fqns);
    return new ArrayList<>(fqns);
  }

  private void collectImports(Schema<?> schema, Set<String> fqns) {
    if (schema == null) return;
    if (schema.get$ref() != null) {
      String ref = schema.get$ref();
      String refName = ref.substring(ref.lastIndexOf('/') + 1);
      String refPkg = toLowerCamel(refName);
      fqns.add("com.chargebee.core.models." + refPkg + "." + refName);
      return;
    }
    // Arrays
    if (schema instanceof io.swagger.v3.oas.models.media.ArraySchema arr) {
      collectImports(arr.getItems(), fqns);
    }
    // Objects
    if ("object".equals(schema.getType()) && schema.getProperties() != null) {
      for (var entry : schema.getProperties().entrySet()) {
        collectImports((Schema<?>) entry.getValue(), fqns);
      }
    }
    // Composed
    if (schema instanceof io.swagger.v3.oas.models.media.ComposedSchema comp) {
      if (comp.getAllOf() != null) comp.getAllOf().forEach(s -> collectImports(s, fqns));
      if (comp.getAnyOf() != null) comp.getAnyOf().forEach(s -> collectImports(s, fqns));
      if (comp.getOneOf() != null) comp.getOneOf().forEach(s -> collectImports(s, fqns));
    }
    // Map types
    Object additional = schema.getAdditionalProperties();
    if (additional instanceof Schema) {
      collectImports((Schema<?>) additional, fqns);
    }
  }

  private Model createSubModel(String fieldName, Schema schema) {
    var subModel = new Model();
    subModel.setPackageName(fieldName);
    subModel.setName(fieldName);
    subModel.setFields(getFields(schema));
    subModel.setEnumFields(getEnumFields(schema));
    subModel.setSubModels(getSubModels(schema));
    return subModel;
  }

  private List<Field> getFields(Schema schema) {
    var fields = new ArrayList<Field>();
    if (schema.getProperties() == null) {
      return fields;
    }
    for (var fieldName : schema.getProperties().keySet()) {
      Schema schemaDefn = (Schema) schema.getProperties().get(fieldName);
      var field = new Field();
      field.setName(fieldName.toString());
      field.setType(TypeMapper.getJavaType(fieldName.toString(), schemaDefn));
      field.setDeprecated(schemaDefn.getDeprecated() != null && schemaDefn.getDeprecated());
      fields.add(field);
    }
    return fields;
  }

  private List<EnumFields> getEnumFields(Schema schema) {
    var enumFields = new ArrayList<EnumFields>();
    if (schema.getProperties() == null) {
      return enumFields;
    }
    for (var fieldName : schema.getProperties().keySet()) {
      Schema schemaDefn = (Schema) schema.getProperties().get(fieldName);
      if (schemaDefn.getEnum() != null) {
        var enumField = new EnumFields();
        enumField.setName(fieldName.toString());
        enumField.setEnums(schemaDefn.getEnum());
        enumFields.add(enumField);
      }
    }
    return enumFields;
  }

  private Map<String, Schema> getModels() {
    var components = openApi.getComponents();
    var schemas = components.getSchemas();
    return schemas.entrySet().stream()
        .filter(entry -> entry.getValue().getProperties() != null)
        .filter(entry -> !entry.getKey().startsWith("4") && !entry.getKey().startsWith("5"))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
