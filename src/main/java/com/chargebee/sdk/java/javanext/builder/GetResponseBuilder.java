package com.chargebee.sdk.java.javanext.builder;

import com.chargebee.sdk.FileOp;
import com.github.jknack.handlebars.Template;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.ArrayList;
import java.util.List;

public class GetResponseBuilder {

  private Template listTemplate;
  private Template simpleTemplate;
  private String outputDirectoryPath;

  private final List<FileOp> fileOps = new ArrayList<>();

  public GetResponseBuilder withOutputDirectoryPath(String outputDirectoryPath) {
    this.outputDirectoryPath = outputDirectoryPath + "/com/chargebee/v4/models";
    fileOps.add(new FileOp.CreateDirectory(this.outputDirectoryPath, ""));
    return this;
  }

  public GetResponseBuilder withListTemplate(Template listTemplate) {
    this.listTemplate = listTemplate;
    return this;
  }

  public GetResponseBuilder withSimpleTemplate(Template simpleTemplate) {
    this.simpleTemplate = simpleTemplate;
    return this;
  }

  public List<FileOp> build(OpenAPI openApi) {
    // Delegate to specialized builders
    var listBuilder =
        new ListResponseBuilder()
            .withOutputDirectoryPath(outputDirectoryPath)
            .withTemplate(listTemplate);
    fileOps.addAll(listBuilder.build(openApi));

    var simpleBuilder =
        new SimpleGetResponseBuilder()
            .withOutputDirectoryPath(outputDirectoryPath)
            .withTemplate(simpleTemplate);
    fileOps.addAll(simpleBuilder.build(openApi));

    return fileOps;
  }
}
