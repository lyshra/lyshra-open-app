package com.lyshra.open.app.integration.contract.workflow;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowContextRetention;

import java.util.Map;

public interface ILyshraOpenAppWorkflow {
    String getName();
    String getStartStep();
    LyshraOpenAppWorkflowContextRetention getContextRetention();
    Map<String, ILyshraOpenAppWorkflowStep> getSteps();
}
