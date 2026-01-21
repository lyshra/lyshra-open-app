package com.lyshra.open.app.integration.contract;

import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;

import java.util.Map;

public interface ILyshraOpenAppContext {

    // data helper methods
    Object getData();
    void setData(Object data);

    // variables helper methods
    Map<String, Object> getVariables();
    void setVariables(Map<String, Object> variables);
    void addVariable(String key, Object value);
    void updateVariable(String key, Object value);
    void removeVariable(String key);
    Object getVariable(String key);
    boolean hasVariable(String key);
    void clearVariables();

    void captureWorkflowStart(ILyshraOpenAppWorkflowIdentifier identifier);
    void captureWorkflowEnd(ILyshraOpenAppWorkflowIdentifier identifier);
    void captureWorkflowEnd(ILyshraOpenAppWorkflowIdentifier identifier, Throwable throwable);

    void captureWorkflowStepStart(ILyshraOpenAppWorkflowStepIdentifier identifier);
    void captureWorkflowStepEnd(ILyshraOpenAppWorkflowStepIdentifier identifier, Throwable throwable);
    void captureWorkflowStepEnd(ILyshraOpenAppWorkflowStepIdentifier identifier, String next);

    void captureProcessorStart(ILyshraOpenAppProcessorIdentifier identifier, Map<String, Object> rawInput);
    void captureProcessorEnd(ILyshraOpenAppProcessorIdentifier identifier, ILyshraOpenAppProcessorResult res);
    void captureProcessorEnd(ILyshraOpenAppProcessorIdentifier identifier, Throwable throwable);

}
