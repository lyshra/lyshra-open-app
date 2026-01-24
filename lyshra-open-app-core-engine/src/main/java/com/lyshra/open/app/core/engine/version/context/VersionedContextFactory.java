package com.lyshra.open.app.core.engine.version.context;

import com.lyshra.open.app.core.engine.version.util.WorkflowSchemaHashUtil;
import com.lyshra.open.app.core.models.LyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowInstanceMetadata;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.models.version.WorkflowInstanceMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Factory for creating workflow execution contexts with version metadata.
 *
 * <p>This factory ensures that every workflow execution context is properly
 * initialized with version information, enabling:</p>
 * <ul>
 *   <li>Version tracking throughout execution</li>
 *   <li>Audit trail of which version was used</li>
 *   <li>Support for version migration</li>
 *   <li>Distributed tracing via correlation IDs</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create context for a new execution
 * ILyshraOpenAppContext context = VersionedContextFactory
 *     .forWorkflow(workflowDefinition)
 *     .withCorrelationId("req-12345")
 *     .create();
 *
 * // Create context with initial data
 * ILyshraOpenAppContext context = VersionedContextFactory
 *     .forWorkflow(workflowDefinition)
 *     .withData(inputData)
 *     .create();
 * }</pre>
 */
@Slf4j
public class VersionedContextFactory {

    private final IVersionedWorkflow workflow;
    private String correlationId;
    private String parentInstanceId;
    private Object initialData;

    private VersionedContextFactory(IVersionedWorkflow workflow) {
        this.workflow = workflow;
    }

    /**
     * Creates a factory for the given workflow definition.
     *
     * @param workflow versioned workflow definition
     * @return factory instance
     */
    public static VersionedContextFactory forWorkflow(IVersionedWorkflow workflow) {
        return new VersionedContextFactory(workflow);
    }

    /**
     * Sets the correlation ID for distributed tracing.
     *
     * @param correlationId correlation ID
     * @return this factory
     */
    public VersionedContextFactory withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    /**
     * Sets the parent instance ID for nested workflow tracking.
     *
     * @param parentInstanceId parent instance ID
     * @return this factory
     */
    public VersionedContextFactory withParentInstanceId(String parentInstanceId) {
        this.parentInstanceId = parentInstanceId;
        return this;
    }

    /**
     * Sets the initial data for the context.
     *
     * @param data initial data
     * @return this factory
     */
    public VersionedContextFactory withData(Object data) {
        this.initialData = data;
        return this;
    }

    /**
     * Creates the execution context with version metadata.
     *
     * @return new context with version tracking
     */
    public ILyshraOpenAppContext create() {
        // Compute or use existing schema hash
        String schemaHash = workflow.getSchemaHash();
        if (schemaHash == null || schemaHash.isEmpty()) {
            schemaHash = WorkflowSchemaHashUtil.computeSchemaHash(workflow);
        }

        // Create instance metadata
        WorkflowInstanceMetadata.WorkflowInstanceMetadataBuilder metadataBuilder =
                WorkflowInstanceMetadata.builder()
                        .instanceId(UUID.randomUUID().toString())
                        .workflowId(workflow.getWorkflowId())
                        .originalVersion(workflow.getVersion())
                        .schemaHash(schemaHash);

        if (correlationId != null) {
            metadataBuilder.correlationId(correlationId);
        }

        if (parentInstanceId != null) {
            metadataBuilder.parentInstanceId(parentInstanceId);
        }

        IWorkflowInstanceMetadata metadata = metadataBuilder.build();

        // Create context
        LyshraOpenAppContext context = new LyshraOpenAppContext(metadata);

        if (initialData != null) {
            context.setData(initialData);
        }

        log.debug("Created versioned context: instanceId={}, workflowId={}, version={}",
                metadata.getInstanceId(),
                metadata.getWorkflowId(),
                metadata.getOriginalVersionString());

        return context;
    }

    /**
     * Creates a context with started state.
     *
     * @return new context in RUNNING state
     */
    public ILyshraOpenAppContext createAndStart() {
        ILyshraOpenAppContext context = create();

        // Update metadata to started state
        context.getInstanceMetadata().ifPresent(meta -> {
            if (meta instanceof WorkflowInstanceMetadata wim) {
                context.setInstanceMetadata(wim.start());
            }
        });

        return context;
    }

    /**
     * Creates a context for simple workflow ID and version (without full definition).
     *
     * @param workflowId workflow ID
     * @param version workflow version
     * @return context factory
     */
    public static SimpleContextBuilder forSimple(String workflowId, IWorkflowVersion version) {
        return new SimpleContextBuilder(workflowId, version);
    }

    /**
     * Builder for creating contexts without a full workflow definition.
     */
    public static class SimpleContextBuilder {
        private final String workflowId;
        private final IWorkflowVersion version;
        private String schemaHash;
        private String correlationId;
        private String parentInstanceId;
        private Object initialData;

        private SimpleContextBuilder(String workflowId, IWorkflowVersion version) {
            this.workflowId = workflowId;
            this.version = version;
        }

        public SimpleContextBuilder withSchemaHash(String schemaHash) {
            this.schemaHash = schemaHash;
            return this;
        }

        public SimpleContextBuilder withCorrelationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public SimpleContextBuilder withParentInstanceId(String parentInstanceId) {
            this.parentInstanceId = parentInstanceId;
            return this;
        }

        public SimpleContextBuilder withData(Object data) {
            this.initialData = data;
            return this;
        }

        public ILyshraOpenAppContext create() {
            WorkflowInstanceMetadata metadata = WorkflowInstanceMetadata.builder()
                    .instanceId(UUID.randomUUID().toString())
                    .workflowId(workflowId)
                    .originalVersion(version)
                    .schemaHash(schemaHash)
                    .correlationId(correlationId)
                    .parentInstanceId(parentInstanceId)
                    .build();

            LyshraOpenAppContext context = new LyshraOpenAppContext(metadata);

            if (initialData != null) {
                context.setData(initialData);
            }

            return context;
        }
    }

    /**
     * Updates the context with a new version after migration.
     *
     * @param context context to update
     * @param newVersion new version after migration
     */
    public static void migrateContext(ILyshraOpenAppContext context, IWorkflowVersion newVersion) {
        context.getInstanceMetadata().ifPresent(meta -> {
            if (meta instanceof WorkflowInstanceMetadata wim) {
                WorkflowInstanceMetadata migratedMetadata = wim.migratedTo(newVersion);
                context.setInstanceMetadata(migratedMetadata);
                log.info("Migrated context {} from {} to {}",
                        meta.getInstanceId(),
                        meta.getCurrentVersionString(),
                        newVersion.toVersionString());
            }
        });
    }

    /**
     * Updates the context's current step.
     *
     * @param context context to update
     * @param stepName current step name
     */
    public static void updateCurrentStep(ILyshraOpenAppContext context, String stepName) {
        context.getInstanceMetadata().ifPresent(meta -> {
            if (meta instanceof WorkflowInstanceMetadata wim) {
                context.setInstanceMetadata(wim.withCurrentStep(stepName));
            }
        });
    }

    /**
     * Marks the context as completed.
     *
     * @param context context to update
     */
    public static void markCompleted(ILyshraOpenAppContext context) {
        context.getInstanceMetadata().ifPresent(meta -> {
            if (meta instanceof WorkflowInstanceMetadata wim) {
                context.setInstanceMetadata(wim.complete());
            }
        });
    }

    /**
     * Marks the context as failed.
     *
     * @param context context to update
     */
    public static void markFailed(ILyshraOpenAppContext context) {
        context.getInstanceMetadata().ifPresent(meta -> {
            if (meta instanceof WorkflowInstanceMetadata wim) {
                context.setInstanceMetadata(wim.fail());
            }
        });
    }
}
