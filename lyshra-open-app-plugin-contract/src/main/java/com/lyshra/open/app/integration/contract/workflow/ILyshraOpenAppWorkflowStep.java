package com.lyshra.open.app.integration.contract.workflow;

import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowContextRetention;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowStepType;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface ILyshraOpenAppWorkflowStep {
    String getName();
    String getDescription();
    LyshraOpenAppWorkflowContextRetention getContextRetention();
    LyshraOpenAppWorkflowStepType getType();
    ILyshraOpenAppProcessorIdentifier getProcessor();
    ILyshraOpenAppWorkflowIdentifier getWorkflowCall();
    List<Integer> getCoordinate();
    Map<String, Object> getInputConfig();
    Duration getTimeout();
    ILyshraOpenAppWorkflowStepNext getNext();
    ILyshraOpenAppWorkflowStepOnError getOnError();
}
