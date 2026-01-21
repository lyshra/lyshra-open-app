package com.lyshra.open.app.core.models;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Slf4j
@Data
public class LyshraOpenAppContext implements ILyshraOpenAppContext {
    private final Map<String, Object> history;
    private Object data;
    private Map<String, Object> variables;

    public LyshraOpenAppContext() {
        this.history = new ConcurrentSkipListMap<>();
        this.data = new Object();
        this.variables = new ConcurrentHashMap<>();
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

}
