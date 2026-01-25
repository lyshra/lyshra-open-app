package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Optional;

/**
 * Represents a connection between workflow steps.
 * Supports conditional branching based on processor output.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowConnection {

    private String id;
    private String sourceStepId;
    private String targetStepId;
    private String branchCondition;
    private String label;
    private ConnectionType type;
    private List<ConnectionPoint> points;

    /**
     * Gets the branch condition as Optional.
     *
     * @return Optional containing the branch condition
     */
    public Optional<String> getBranchConditionOptional() {
        return Optional.ofNullable(branchCondition);
    }

    /**
     * Checks if this is a default connection (no specific branch condition).
     *
     * @return true if this is a default connection
     */
    public boolean isDefaultConnection() {
        return branchCondition == null || branchCondition.isBlank() || "default".equalsIgnoreCase(branchCondition);
    }
}
