package com.lyshra.open.app.integration.contract.workflow;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppRetryPolicy;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppOnErrorStrategy;

public interface ILyshraOpenAppWorkflowStepOnErrorConfig {
    LyshraOpenAppOnErrorStrategy getStrategy();
    ILyshraOpenAppRetryPolicy getRetryPolicy();
    String getFallbackWorkflowStep();
}
