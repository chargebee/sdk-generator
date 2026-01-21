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
    public final String[] hiddenOverride = {"media"};
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
        return generators.get("changelog").generate(outputPath, oldVersion, newerVersion);
    }
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public boolean cleanDirectoryBeforeGenerate() {
        return false;
    }
}

