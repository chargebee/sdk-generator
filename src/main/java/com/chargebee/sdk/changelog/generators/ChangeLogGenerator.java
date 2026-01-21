package com.chargebee.sdk.changelog.generators;

import com.chargebee.openapi.Resource;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.changelog.ChangeLog;
import com.chargebee.sdk.changelog.models.ChangeLogSchema;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.chargebee.GenUtil.pluralize;
import static com.chargebee.sdk.changelog.Constants.*;

public class ChangeLogGenerator implements FileGenerator {
    private final ChangeLog changeLogGenerator;
    private final Template changeLogTemplate;

    public ChangeLogGenerator(ChangeLog changeLogGenerator){
        this.changeLogGenerator = changeLogGenerator;
        this.changeLogTemplate = changeLogGenerator.getTemplateContent(CHANGELOG);
    }

    @Override
    public FileOp generate(String output, List<?> oldItems, List<?> newItems) throws IOException {
        List<Resource> oldResources = (List<Resource>) oldItems;
        List<Resource> newResources = (List<Resource>) newItems;


        ChangeLogSchema changeLogSchema = new ChangeLogSchema();
        changeLogSchema.setNewResource(generateResourceLine(oldResources, newResources));

        String content = changeLogTemplate.apply(changeLogGenerator.getObjectMapper().convertValue(changeLogSchema, Map.class));
        return new FileOp.WriteString("./", output + "CHANGELOG.md", content);
    }

    private String convertNewResourceToResourceLine(Resource r){
        String resourceName = r.name;
        String resourceId = r.id;

        String message = String.format("- [%s](https://apidocs.chargebee.com/docs/api/%s) has been generated.", resourceName, toHyphenCase(pluralize(resourceId)));
        return message;
    }

    private List<String> generateResourceLine( List<Resource> oldResources, List<Resource> newResources){
        Set<String> oldResourceIds = oldResources.stream()
                .map(resource -> resource.id)
                .collect(Collectors.toSet());

        Set<String> newResource = newResources.stream()
                .filter(r -> !oldResourceIds.contains(r.id))
                .map(r -> convertNewResourceToResourceLine(r))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return newResource.stream().toList();
    }

    private  String toHyphenCase(String s){
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, s);
    }
}
