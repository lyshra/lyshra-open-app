package com.lyshra.open.app.core.models;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIO;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Data
@Slf4j
public class LyshraOpenAppContext implements ILyshraOpenAppContext {
    // injected by framework
    private final Map<String, Object> $config;

    // captures workflow execution history with their input and output
    private final Map<String, Object> $history;

    // data to operate on
    private final Map<String, Object> $data;

    // variables to capture intermediate results
    private final Map<String, Object> $variables;

    public LyshraOpenAppContext(Map<String, Object> $config) {
        this.$config = $config;
        this.$history = new ConcurrentSkipListMap<>();
        this.$data = new LinkedHashMap<>();
        this.$variables = new ConcurrentHashMap<>();
    }

    public Map<String, Object> getConfig() {
        return $config;
    }

    public Map<String, Object> getHistory() {
        return $history;
    }

    public Map<String, Object> getData() {
        return $data;
    }

    public Map<String, Object> getVariables() {
        return $variables;
    }

    @Override
    public void captureWorkflowStart(ILyshraOpenAppWorkflowIdentifier identifier) {
        log.info("Starting workflow: [{}]", identifier);
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

        log.info("Starting processor: [{}], Raw Input: [{}]", identifier, rawInput);
    }

    @Override
    public void captureProcessorEnd(
            ILyshraOpenAppProcessorIdentifier identifier,
            ILyshraOpenAppProcessorResult<? extends ILyshraOpenAppProcessorIO> res) {

        log.info("Processor completed: [{}], Result: [{}]", identifier, res);
    }

    @Override
    public void captureProcessorEnd(ILyshraOpenAppProcessorIdentifier identifier, Throwable throwable) {
        log.error("Processor Failed, Error: [{}]", throwable.getMessage());
    }

}

