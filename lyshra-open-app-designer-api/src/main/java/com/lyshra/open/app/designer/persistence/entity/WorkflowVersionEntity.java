package com.lyshra.open.app.designer.persistence.entity;

import com.lyshra.open.app.designer.domain.WorkflowVersionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Database entity for workflow versions.
 * Steps and connections are stored as JSON to preserve the complex structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("workflow_versions")
public class WorkflowVersionEntity {

    @Id
    private String id;

    @Column("workflow_definition_id")
    private String workflowDefinitionId;

    @Column("version_number")
    private String versionNumber;

    @Column("description")
    private String description;

    @Column("start_step_id")
    private String startStepId;

    /**
     * Workflow steps stored as JSON.
     * Contains the full step definitions including processor configs.
     */
    @Column("steps_json")
    private String stepsJson;

    /**
     * Workflow connections stored as JSON.
     * Contains the connection graph between steps.
     */
    @Column("connections_json")
    private String connectionsJson;

    /**
     * Context retention configuration stored as JSON.
     */
    @Column("context_retention_json")
    private String contextRetentionJson;

    /**
     * Designer metadata (canvas position, zoom, etc.) stored as JSON.
     */
    @Column("designer_metadata_json")
    private String designerMetadataJson;

    @Column("state")
    private WorkflowVersionState state;

    @Column("created_by")
    private String createdBy;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    @Column("activated_by")
    private String activatedBy;

    @Column("activated_at")
    private Instant activatedAt;

    @Column("deprecated_by")
    private String deprecatedBy;

    @Column("deprecated_at")
    private Instant deprecatedAt;

    /**
     * Optimistic locking version.
     */
    @Version
    @Column("version")
    private Long entityVersion;
}
