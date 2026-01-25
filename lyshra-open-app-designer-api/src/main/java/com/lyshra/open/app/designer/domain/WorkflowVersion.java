package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a specific version of a workflow definition.
 * Each version contains the complete workflow configuration including steps and their connections.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersion {

    private String id;
    private String workflowDefinitionId;
    private String versionNumber;
    private String description;
    private String startStepId;
    private List<WorkflowStepDefinition> steps;
    private Map<String, WorkflowConnection> connections;
    private WorkflowContextRetention contextRetention;
    private WorkflowVersionState state;
    private String createdBy;
    private Instant createdAt;
    private String activatedBy;
    private Instant activatedAt;
    private String deprecatedBy;
    private Instant deprecatedAt;
    private DesignerMetadata designerMetadata;

    /**
     * Gets the start step ID as Optional.
     *
     * @return Optional containing the start step ID
     */
    public Optional<String> getStartStepIdOptional() {
        return Optional.ofNullable(startStepId);
    }

    /**
     * Checks if this version is currently active.
     *
     * @return true if the version is active
     */
    public boolean isActive() {
        return state == WorkflowVersionState.ACTIVE;
    }

    /**
     * Checks if this version can be activated.
     *
     * @return true if the version can be activated
     */
    public boolean canBeActivated() {
        return state == WorkflowVersionState.DRAFT || state == WorkflowVersionState.DEPRECATED;
    }
}
