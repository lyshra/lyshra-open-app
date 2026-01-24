package com.lyshra.open.app.core.models;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import com.lyshra.open.app.integration.contract.version.IWorkflowInstanceMetadata;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Implementation of workflow execution context with version tracking.
 *
 * <p>This context carries:</p>
 * <ul>
 *   <li>Data - the main payload being processed</li>
 *   <li>Variables - workflow-scoped variables</li>
 *   <li>Instance Metadata - version and execution tracking</li>
 *   <li>History - execution audit trail</li>
 * </ul>
 */
@Slf4j
@Data
public class LyshraOpenAppContext implements ILyshraOpenAppContext {
    private final Map<String, Object> history;
    private Object data;
    private Map<String, Object> variables;
    private IWorkflowInstanceMetadata instanceMetadata;

    public LyshraOpenAppContext() {
        this.history = new ConcurrentSkipListMap<>();
        this.data = new Object();
        this.variables = new ConcurrentHashMap<>();
        this.instanceMetadata = null;
    }

    /**
     * Creates a context with instance metadata for version tracking.
     *
     * @param instanceMetadata metadata with version info
     */
    public LyshraOpenAppContext(IWorkflowInstanceMetadata instanceMetadata) {
        this();
        this.instanceMetadata = instanceMetadata;
    }

    @Override
    public void addVariable(String key, Object value) {
        variables.put(key, value);
    }

    @Override
    public void updateVariable(String key, Object value) {
        variables.put(key, value);
    }

    @Override
    public void removeVariable(String key) {
        variables.remove(key);
    }

    @Override
    public Object getVariable(String key) {
        return variables.get(key);
    }

    @Override
    public boolean hasVariable(String key) {
        return variables.containsKey(key);
    }

    @Override
    public void clearVariables() {
        variables.clear();
    }

    @Override
    public void captureWorkflowStart(ILyshraOpenAppWorkflowIdentifier identifier) {
        String versionInfo = getWorkflowVersionString().map(v -> ", version=" + v).orElse("");
        String instanceInfo = getInstanceId().map(id -> ", instanceId=" + id).orElse("");
        log.info("Starting workflow: [{}]{}{}", identifier, versionInfo, instanceInfo);
    }

    @Override
    public void captureWorkflowEnd(ILyshraOpenAppWorkflowIdentifier identifier) {
        log.error("Workflow Completed: [{}]", identifier);
    }

    @Override
    public void captureWorkflowEnd(ILyshraOpenAppWorkflowIdentifier identifier, Throwable throwable) {
        log.error("Workflow Failed: [{}], Error: [{}]", identifier, throwable.getMessage());
    }

    @Override
    public void captureWorkflowStepStart(ILyshraOpenAppWorkflowStepIdentifier identifier) {
        log.info("Starting workflow step: [{}]", identifier);
    }

    @Override
    public void captureWorkflowStepEnd(ILyshraOpenAppWorkflowStepIdentifier identifier, Throwable throwable) {
        log.error("Workflow Step Failed: [{}], Error: [{}]", identifier, throwable.getMessage());
    }

    @Override
    public void captureWorkflowStepEnd(ILyshraOpenAppWorkflowStepIdentifier identifier, String next) {
        log.error("Workflow Step Completed: [{}], Next: [{}]", identifier, next);
    }

    @Override
    public void captureProcessorStart(
            ILyshraOpenAppProcessorIdentifier identifier,
            Map<String, Object> rawInput) {

        log.info("Starting processor: [{}], Raw Input: [{}], Data: [{}], Variables: [{}]", identifier, rawInput, data, variables);
    }

    @Override
    public void captureProcessorEnd(
            ILyshraOpenAppProcessorIdentifier identifier,
            ILyshraOpenAppProcessorResult res) {

        log.info("Processor completed: [{}], Result: [{}]", identifier, res);
    }

    @Override
    public void captureProcessorEnd(ILyshraOpenAppProcessorIdentifier identifier, Throwable throwable) {
        log.error("Processor Failed, Error: [{}]", throwable.getMessage());
    }

    @Override
    public Optional<IWorkflowInstanceMetadata> getInstanceMetadata() {
        return Optional.ofNullable(instanceMetadata);
    }

    @Override
    public void setInstanceMetadata(IWorkflowInstanceMetadata metadata) {
        this.instanceMetadata = metadata;
        if (metadata != null) {
            log.debug("Set instance metadata: instanceId={}, workflowId={}, version={}",
                    metadata.getInstanceId(),
                    metadata.getWorkflowId(),
                    metadata.getCurrentVersionString());
        }
    }

    /**
     * Creates a copy of this context for nested workflow execution.
     *
     * @param nestedMetadata metadata for the nested workflow
     * @return new context with nested metadata
     */
    public LyshraOpenAppContext createNestedContext(IWorkflowInstanceMetadata nestedMetadata) {
        LyshraOpenAppContext nested = new LyshraOpenAppContext(nestedMetadata);
        nested.setData(this.data);
        nested.setVariables(new ConcurrentHashMap<>(this.variables));
        return nested;
    }
}
