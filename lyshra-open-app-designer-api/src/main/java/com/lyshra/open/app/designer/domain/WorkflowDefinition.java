package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a workflow definition in the designer.
 * A workflow definition can have multiple versions, with one active version at a time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinition {

    private String id;
    private String name;
    private String description;
    private String organization;
    private String module;
    private String category;
    private Map<String, String> tags;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private String activeVersionId;
    private WorkflowLifecycleState lifecycleState;

    /**
     * Gets the active version ID if present.
     *
     * @return Optional containing the active version ID
     */
    public Optional<String> getActiveVersionIdOptional() {
        return Optional.ofNullable(activeVersionId);
    }

    /**
     * Checks if the workflow has an active version.
     *
     * @return true if an active version exists
     */
    public boolean hasActiveVersion() {
        return activeVersionId != null && !activeVersionId.isBlank();
    }
}
