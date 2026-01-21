package com.chargebee.sdk.changelog;


import com.chargebee.openapi.*;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.Language;
import com.chargebee.sdk.changelog.generators.ChangeLogGenerator;
import com.chargebee.sdk.changelog.generators.FileGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.chargebee.sdk.changelog.Constants.*;

public class ChangeLog extends Language {
    protected final String[] hiddenOverride = {"media"};
    private final ObjectMapper objectMapper;
    private Map<String, FileGenerator> generators;

    public ChangeLog() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected Map<String, String> templatesDefinition() {
        return Map.of(
                CHANGELOG,
                "/templates/changelog/changelog.md.hbs");
    }

    private Map<String, FileGenerator> initializeGenerators() {
        return Map.of(
                "changelog",
                new ChangeLogGenerator(this));
    }

    @Override
    protected List<FileOp> generateSDK(String outputPath, Spec spec) throws IOException {
        return null;
    }

    protected FileOp generateChangeLog(
            String outputPath,
            Spec oldVersion,
            Spec newerVersion
    ) throws IOException {
        this.generators = initializeGenerators();
        List <Resource> newVersionResource = filterResources(newerVersion.resources());
        List <Resource> olderVersionResource = filterResources(oldVersion.resources());
        return generators.get("changelog").generate(outputPath, olderVersionResource, newVersionResource);
    }

    private List<com.chargebee.openapi.Resource> filterResources(
            List<com.chargebee.openapi.Resource> resources) {
        return resources.stream()
                .filter(resource -> !List.of(this.hiddenOverride).contains(resource.id))
                .collect(Collectors.toList());
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public boolean cleanDirectoryBeforeGenerate() {
        return false;
    }
}

