package com.lyshra.open.app.designer.persistence.entity;

import com.lyshra.open.app.designer.domain.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Database entity for workflow executions.
 * Tracks the execution state and results of workflow runs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("workflow_executions")
public class WorkflowExecutionEntity {

    @Id
    private String id;

    @Column("workflow_definition_id")
    private String workflowDefinitionId;

    @Column("workflow_version_id")
    private String workflowVersionId;

    @Column("workflow_name")
    private String workflowName;

    @Column("version_number")
    private String versionNumber;

    @Column("status")
    private ExecutionStatus status;

    @Column("correlation_id")
    private String correlationId;

    @Column("triggered_by")
    private String triggeredBy;

    /**
     * Input data stored as JSON.
     */
    @Column("input_data_json")
    private String inputDataJson;

    /**
     * Output data stored as JSON.
     */
    @Column("output_data_json")
    private String outputDataJson;

    /**
     * Execution context stored as JSON.
     */
    @Column("context_data_json")
    private String contextDataJson;

    @Column("current_step_id")
    private String currentStepId;

    @Column("current_step_name")
    private String currentStepName;

    @Column("error_code")
    private String errorCode;

    @Column("error_message")
    private String errorMessage;

    /**
     * Step execution logs stored as JSON array.
     */
    @Column("step_logs_json")
    private String stepLogsJson;

    @Column("retry_count")
    private Integer retryCount;

    @Column("parent_execution_id")
    private String parentExecutionId;

    @Column("started_at")
    private Instant startedAt;

    @Column("completed_at")
    private Instant completedAt;

    /**
     * Optimistic locking version.
     */
    @Version
    @Column("version")
    private Long entityVersion;
}
