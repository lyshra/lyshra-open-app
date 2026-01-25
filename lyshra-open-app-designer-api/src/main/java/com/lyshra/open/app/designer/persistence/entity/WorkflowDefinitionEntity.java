package com.lyshra.open.app.designer.persistence.entity;

import com.lyshra.open.app.designer.domain.WorkflowLifecycleState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Database entity for workflow definitions.
 * Uses R2DBC annotations for reactive database access.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("workflow_definitions")
public class WorkflowDefinitionEntity {

    @Id
    private String id;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("organization")
    private String organization;

    @Column("module")
    private String module;

    @Column("category")
    private String category;

    /**
     * Tags stored as JSON string.
     */
    @Column("tags")
    private String tags;

    @Column("created_by")
    private String createdBy;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    @Column("updated_by")
    private String updatedBy;

    @LastModifiedDate
    @Column("updated_at")
    private Instant updatedAt;

    @Column("active_version_id")
    private String activeVersionId;

    @Column("lifecycle_state")
    private WorkflowLifecycleState lifecycleState;

    /**
     * Optimistic locking version.
     */
    @Version
    @Column("version")
    private Long entityVersion;
}
