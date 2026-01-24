package com.lyshra.open.app.integration.models.version.migration;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationAuditEntry;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationResult;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of migration result containing outcome and audit information.
 */
@Data
@Builder
public class MigrationResult implements IMigrationResult, Serializable {

    private static final long serialVersionUID = 1L;

    private final Status status;
    private final String executionId;
    private final IWorkflowVersion sourceVersion;
    private final IWorkflowVersion targetVersion;
    private final ILyshraOpenAppContext migratedContext;
    private final String targetStepName;
    private final Throwable error;
    private final String errorMessage;
    @Builder.Default
    private final List<String> warnings = new ArrayList<>();
    private final Duration duration;
    @Builder.Default
    private final Instant completedAt = Instant.now();
    @Builder.Default
    private final Map<String, Object> preMigrationSnapshot = new HashMap<>();
    @Builder.Default
    private final Map<String, Object> postMigrationSnapshot = new HashMap<>();
    @Builder.Default
    private final List<IMigrationAuditEntry> auditTrail = new ArrayList<>();

    @Override
    public Optional<ILyshraOpenAppContext> getMigratedContext() {
        return Optional.ofNullable(migratedContext);
    }

    @Override
    public Optional<String> getTargetStepName() {
        return Optional.ofNullable(targetStepName);
    }

    @Override
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    @Override
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    @Override
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    @Override
    public Map<String, Object> getPreMigrationSnapshot() {
        return Collections.unmodifiableMap(preMigrationSnapshot);
    }

    @Override
    public Map<String, Object> getPostMigrationSnapshot() {
        return Collections.unmodifiableMap(postMigrationSnapshot);
    }

    @Override
    public List<IMigrationAuditEntry> getAuditTrail() {
        return Collections.unmodifiableList(auditTrail);
    }

    /**
     * Creates a successful migration result.
     */
    public static MigrationResult success(
            String executionId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            ILyshraOpenAppContext migratedContext,
            String targetStepName,
            Duration duration,
            Map<String, Object> preMigrationSnapshot,
            Map<String, Object> postMigrationSnapshot,
            List<IMigrationAuditEntry> auditTrail) {

        return MigrationResult.builder()
                .status(Status.SUCCESS)
                .executionId(executionId)
                .sourceVersion(sourceVersion)
                .targetVersion(targetVersion)
                .migratedContext(migratedContext)
                .targetStepName(targetStepName)
                .duration(duration)
                .preMigrationSnapshot(new HashMap<>(preMigrationSnapshot))
                .postMigrationSnapshot(new HashMap<>(postMigrationSnapshot))
                .auditTrail(new ArrayList<>(auditTrail))
                .build();
    }

    /**
     * Creates a failed migration result.
     */
    public static MigrationResult failed(
            Status status,
            String executionId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            Throwable error,
            Duration duration,
            Map<String, Object> preMigrationSnapshot,
            List<IMigrationAuditEntry> auditTrail) {

        return MigrationResult.builder()
                .status(status)
                .executionId(executionId)
                .sourceVersion(sourceVersion)
                .targetVersion(targetVersion)
                .error(error)
                .errorMessage(error != null ? error.getMessage() : "Unknown error")
                .duration(duration)
                .preMigrationSnapshot(new HashMap<>(preMigrationSnapshot))
                .auditTrail(new ArrayList<>(auditTrail))
                .build();
    }

    /**
     * Creates a skipped migration result.
     */
    public static MigrationResult skipped(
            String executionId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            String reason) {

        return MigrationResult.builder()
                .status(Status.SKIPPED)
                .executionId(executionId)
                .sourceVersion(sourceVersion)
                .targetVersion(targetVersion)
                .errorMessage(reason)
                .duration(Duration.ZERO)
                .build();
    }

    /**
     * Creates a pending approval result.
     */
    public static MigrationResult pendingApproval(
            String executionId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            Map<String, Object> preMigrationSnapshot) {

        return MigrationResult.builder()
                .status(Status.PENDING_APPROVAL)
                .executionId(executionId)
                .sourceVersion(sourceVersion)
                .targetVersion(targetVersion)
                .preMigrationSnapshot(new HashMap<>(preMigrationSnapshot))
                .duration(Duration.ZERO)
                .build();
    }

    /**
     * Creates a dry-run complete result.
     */
    public static MigrationResult dryRunComplete(
            String executionId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            String targetStepName,
            Map<String, Object> preMigrationSnapshot,
            Map<String, Object> postMigrationSnapshot,
            List<String> warnings) {

        return MigrationResult.builder()
                .status(Status.DRY_RUN_COMPLETE)
                .executionId(executionId)
                .sourceVersion(sourceVersion)
                .targetVersion(targetVersion)
                .targetStepName(targetStepName)
                .preMigrationSnapshot(new HashMap<>(preMigrationSnapshot))
                .postMigrationSnapshot(new HashMap<>(postMigrationSnapshot))
                .warnings(new ArrayList<>(warnings))
                .duration(Duration.ZERO)
                .build();
    }
}
