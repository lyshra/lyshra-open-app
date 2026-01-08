package com.lyshra.open.app.integration.contract.workflow;

import java.util.Map;

public interface ILyshraOpenAppWorkflowStepOnError {
    Map<String, ILyshraOpenAppWorkflowStepOnErrorConfig> getErrorConfigs();
}
