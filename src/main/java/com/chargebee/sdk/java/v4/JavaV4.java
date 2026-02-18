package com.chargebee.sdk.java.v4;

import com.chargebee.openapi.Spec;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.java.v4.builder.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JavaV4 extends Language {

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

    // Generate error enums and exception classes
    List<FileOp> errorEnumFiles =
        new ErrorEnumBuilder()
            .withOutputDirectoryPath(outputDirectoryPath)
            .withTemplate(getTemplateContent("error.enum"))
            .withInterfaceTemplate(getTemplateContent("api.error.code.interface"))
            .build(spec.openAPI());
    List<FileOp> exceptionFiles =
        new ExceptionBuilder()
            .withOutputDirectoryPath(outputDirectoryPath)
            .withExceptionTemplate(getTemplateContent("exception"))
            .withBaseExceptionTemplate(getTemplateContent("api.exception"))
            .withHttpStatusHandlerTemplate(getTemplateContent("http.status.handler"))
            .build(spec.openAPI());

    List<List<FileOp>> allFileOps = new ArrayList<>();
    allFileOps.add(coreModelFiles);
    allFileOps.add(paramsBuilderFiles);
    allFileOps.add(getParamsBuilderFiles);
    allFileOps.add(getResponseFiles);
    allFileOps.add(postResponseFiles);
    allFileOps.add(serviceFiles);
    allFileOps.add(clientMethodsFiles);
    allFileOps.add(serviceRegistryFiles);
    allFileOps.add(errorEnumFiles);
    allFileOps.add(exceptionFiles);

    return allFileOps.stream().flatMap(List::stream).collect(Collectors.toList());
  }

  @Override
  protected Map<String, String> templatesDefinition() {
    // Map.of() only supports up to 10 entries, use Map.ofEntries for more
    return Map.ofEntries(
        Map.entry("core.models", "/templates/java/next/core.models.hbs"),
        Map.entry("core.post.params.builder", "/templates/java/next/core.post.params.builder.hbs"),
        Map.entry("core.get.params.builder", "/templates/java/next/core.get.params.builder.hbs"),
        Map.entry("core.get.response", "/templates/java/next/core.get.response.hbs"),
        Map.entry("core.get.response.list", "/templates/java/next/core.get.response.list.hbs"),
        Map.entry("core.post.response", "/templates/java/next/core.post.response.hbs"),
        Map.entry("core.base.response", "/templates/java/next/core.base.response.hbs"),
        Map.entry("core.services", "/templates/java/next/core.services.hbs"),
        Map.entry("core.service.registry", "/templates/java/next/core.service.registry.hbs"),
        Map.entry("client.methods", "/templates/java/next/client.methods.hbs"),
        Map.entry("client.methods.impl", "/templates/java/next/client.methods.impl.hbs"),
        // Error enum and exception templates
        Map.entry("error.enum", "/templates/java/next/error.enum.hbs"),
        Map.entry("api.error.code.interface", "/templates/java/next/api.error.code.interface.hbs"),
        Map.entry("exception", "/templates/java/next/exception.hbs"),
        Map.entry("api.exception", "/templates/java/next/api.exception.hbs"),
        Map.entry("http.status.handler", "/templates/java/next/http.status.handler.hbs"));
  }

  @Override
  public boolean cleanDirectoryBeforeGenerate() {
    return false;
  }
}
