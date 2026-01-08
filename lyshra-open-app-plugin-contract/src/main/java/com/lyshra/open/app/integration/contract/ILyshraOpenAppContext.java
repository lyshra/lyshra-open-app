package com.lyshra.open.app.integration.contract;

import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIO;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;

import java.util.Map;

public interface ILyshraOpenAppContext {
    void captureWorkflowStart(ILyshraOpenAppWorkflowIdentifier identifier);
    void captureWorkflowEnd(ILyshraOpenAppWorkflowIdentifier identifier);
    void captureWorkflowEnd(ILyshraOpenAppWorkflowIdentifier identifier, Throwable throwable);

    void captureWorkflowStepStart(ILyshraOpenAppWorkflowStepIdentifier identifier);
    void captureWorkflowStepEnd(ILyshraOpenAppWorkflowStepIdentifier identifier, Throwable throwable);
    void captureWorkflowStepEnd(ILyshraOpenAppWorkflowStepIdentifier identifier, String next);

    void captureProcessorStart(ILyshraOpenAppProcessorIdentifier identifier, Map<String, Object> rawInput);
    void captureProcessorEnd(ILyshraOpenAppProcessorIdentifier identifier, ILyshraOpenAppProcessorResult<? extends ILyshraOpenAppProcessorIO> res);
    void captureProcessorEnd(ILyshraOpenAppProcessorIdentifier identifier, Throwable throwable);

}
