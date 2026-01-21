package com.chargebee.sdk.changelog.generators;

import com.chargebee.openapi.Action;
import com.chargebee.openapi.Attribute;
import com.chargebee.openapi.Resource;
import com.chargebee.sdk.FileOp;
import com.chargebee.sdk.changelog.ChangeLog;
import com.chargebee.sdk.changelog.models.ChangeLogSchema;
import com.github.jknack.handlebars.Template;
import com.google.common.base.CaseFormat;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.chargebee.GenUtil.pluralize;
import static com.chargebee.GenUtil.singularize;
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
        changeLogSchema.setNewActions(generateActionLine(oldResources, newResources));
        changeLogSchema.setNewResourceAttribute(generateAttributeLine(oldResources, newResources));

        String content = changeLogTemplate.apply(changeLogGenerator.getObjectMapper().convertValue(changeLogSchema, Map.class));
        return new FileOp.WriteString("./", output + "CHANGELOG.md", content);
    }

    private String convertNewResourceToResourceLine(Resource r){
        String resourceName = r.name;
        String message = String.format("- [%s](%s) has been added.", resourceName, getDocsUrlForResource(r));
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

    private List<String> generateActionLine(List<Resource> oldResources, List<Resource> newResources) {
        Map<String, Set<String>> oldActionsByResourceId = oldResources.stream()
                .collect(Collectors.toMap(
                        resource -> resource.id,
                        resource -> resource.actions.stream()
                                .map(action -> action.name)
                                .collect(Collectors.toSet())
                ));

        Set<String> newActionLines = new LinkedHashSet<>();

        for (Resource newResource : newResources) {
            Set<String> oldActions = oldActionsByResourceId.getOrDefault(newResource.id, Collections.emptySet());
            for (Action action : newResource.actions) {
                if (!oldActions.contains(action.name)) {
                    newActionLines.add(convertNewActionToActionLine(newResource, action));
                }
            }
        }
        return new ArrayList<>(newActionLines);
    }

    private List<String> generateAttributeLine(List<Resource> oldResources, List<Resource> newResources) {
        Map<String, Set<String>> oldAttributesByResourceId = oldResources.stream()
                .collect(Collectors.toMap(
                        resource -> resource.id,
                        resource -> resource.attributes().stream()
                                .map(attribute -> attribute.name)
                                .collect(Collectors.toSet())
                ));

        return newResources.stream()
                .flatMap(resource -> resource.attributes().stream()
                        .filter(attribute -> !oldAttributesByResourceId
                                .getOrDefault(resource.id, Collections.emptySet())
                                .contains(attribute.name))
                        .map(attribute -> convertNewAttributeToAttributeLine(resource, attribute)))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private String convertNewActionToActionLine(Resource resource, Action action) {
        String actionName = action.name;
        String resourceName = resource.name;

        return String.format("- [%s](%s) has been added to [%s](%s).", actionName, getDocsUrlForActions(resource, action), resourceName, getDocsUrlForResource(resource));
    }

    private String getDocsUrlForResource(Resource resource){
        return String.format("https://apidocs.chargebee.com/docs/api/%s", pluralize(resource.id));
    }

    private String getDocsUrlForActions(Resource resource, Action action){
        String resourcePath = pluralize(resource.id);
        String actionPath = toHyphenCase(action.id);
        return String.format("https://apidocs.chargebee.com/docs/api/%s/%s", resourcePath, actionPath);
    }

    private String convertNewAttributeToAttributeLine(Resource resource, Attribute attribute) {
        String attributeName = attribute.name;
        String resourceName = resource.name;

        return String.format("- [%s](%s) has been added to [%s](%s).", attributeName, getDocsUrlForAttribute(resource, attribute), resourceName, getDocsUrlForResource(resource));
    }

    private String getDocsUrlForAttribute(Resource resource, Attribute attribute) {
        String resourcePath = pluralize(resource.id);
        String attributePath = attribute.name;
        return String.format("https://apidocs.chargebee.com/docs/api/%s/%s-object#%s", resourcePath, toHyphenCase(singularize(resource.id)), attributePath);
    }

    private  String toHyphenCase(String s){
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, s);
    }
}
