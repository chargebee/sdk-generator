package com.chargebee.sdk.java.javanext;

import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.java.javanext.builder.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JavaNext extends Language {

  @Override
  public List<FileOp> generateSDK(String outputDirectoryPath, Spec spec) throws IOException {
    List<FileOp> coreModelFiles =
        new ModelBuilder()
            .withOutputDirectoryPath(outputDirectoryPath)
            .withTemplate(getTemplateContent("core.models"))
            .build(spec.openAPI());
    List<FileOp> paramsBuilderFiles =
        new PostRequestParamsBuilder()
            .withOutputDirectoryPath(outputDirectoryPath)
            .withTemplate(getTemplateContent("core.post.params.builder"))
            .build(spec.openAPI());
    List<FileOp> getParamsBuilderFiles =
        new GetRequestParamsBuilder()
            .withOutputDirectoryPath(outputDirectoryPath)
            .withTemplate(getTemplateContent("core.get.params.builder"))
            .build(spec.openAPI());
    List<FileOp> getResponseFiles =
        new GetResponseBuilder()
            .withOutputDirectoryPath(outputDirectoryPath)
            .withSimpleTemplate(getTemplateContent("core.get.response"))
            .withListTemplate(getTemplateContent("core.get.response.list"))
            .build(spec.openAPI());
    List<FileOp> postResponseFiles =
        new PostResponseBuilder()
            .withOutputDirectoryPath(outputDirectoryPath)
            .withTemplate(getTemplateContent("core.post.response"))
            .withBaseResponseTemplate(getTemplateContent("core.base.response"))
            .build(spec.openAPI());
    List<FileOp> serviceFiles =
        new ServiceBuilder()
            .withOutputDirectoryPath(outputDirectoryPath)
            .withTemplate(getTemplateContent("core.services"))
            .build(spec.openAPI());
    List<FileOp> clientMethodsFiles =
        new ClientMethodsBuilder()
            .withOutputDirectoryPath(outputDirectoryPath)
            .withClientMethodsTemplate(getTemplateContent("client.methods"))
            .withClientMethodsImplTemplate(getTemplateContent("client.methods.impl"))
            .build(spec.openAPI());
    List<FileOp> serviceRegistryFiles =
        new ServiceRegistryBuilder()
            .withOutputDirectoryPath(outputDirectoryPath)
            .withTemplate(getTemplateContent("core.service.registry"))
            .build(spec.openAPI());
    return List.of(
            coreModelFiles,
            paramsBuilderFiles,
            getParamsBuilderFiles,
            getResponseFiles,
            postResponseFiles,
            serviceFiles,
            clientMethodsFiles,
            serviceRegistryFiles)
        .stream()
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    return Map.of(
        "core.models",
        "/templates/java/next/core.models.hbs",
        "core.post.params.builder",
        "/templates/java/next/core.post.params.builder.hbs",
        "core.get.params.builder",
        "/templates/java/next/core.get.params.builder.hbs",
        "core.get.response",
        "/templates/java/next/core.get.response.hbs",
        "core.get.response.list",
        "/templates/java/next/core.get.response.list.hbs",
        "core.post.response",
        "/templates/java/next/core.post.response.hbs",
        "core.services",
        "/templates/java/next/core.services.hbs",
        "core.service.registry",
        "/templates/java/next/core.service.registry.hbs",
        "client.methods",
        "/templates/java/next/client.methods.hbs",
        "client.methods.impl",
        "/templates/java/next/client.methods.impl.hbs");
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
