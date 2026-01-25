package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reference to another workflow for workflow call steps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowReference {

    private String organization;
    private String module;
    private String version;
    private String workflowName;

    /**
     * Creates a workflow identifier string in the format "organization/module/version:workflowName".
     *
     * @return the full workflow identifier
     */
    public String toIdentifier() {
        return String.format("%s/%s/%s:%s", organization, module, version, workflowName);
    }

    /**
     * Creates a WorkflowReference from an identifier string.
     *
     * @param identifier the identifier string in format "organization/module/version:workflowName"
     * @return the WorkflowReference
     * @throws IllegalArgumentException if the identifier format is invalid
     */
    public static WorkflowReference fromIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Workflow identifier cannot be null or blank");
        }

        String[] parts = identifier.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid workflow identifier format: " + identifier);
        }

        String[] pluginParts = parts[0].split("/");
        if (pluginParts.length != 3) {
            throw new IllegalArgumentException("Invalid plugin identifier format: " + parts[0]);
        }

        return WorkflowReference.builder()
                .organization(pluginParts[0])
                .module(pluginParts[1])
                .version(pluginParts[2])
                .workflowName(parts[1])
                .build();
    }
}
