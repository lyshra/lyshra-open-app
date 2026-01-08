package com.lyshra.open.app.integration.models.workflows.step;

import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;
import lombok.Data;

@Data
public class LyshraOpenAppWorkflowStepIdentifier implements ILyshraOpenAppWorkflowStepIdentifier {
    private final String organization;
    private final String module;
    private final String version;
    private final String workflowName;
    private final String workflowStepName;

    public LyshraOpenAppWorkflowStepIdentifier(ILyshraOpenAppWorkflowIdentifier lyshraOpenAppPluginWorkflowIdentifier, String workflowStepName) {
        this.organization = lyshraOpenAppPluginWorkflowIdentifier.getOrganization();
        this.module = lyshraOpenAppPluginWorkflowIdentifier.getModule();
        this.version = lyshraOpenAppPluginWorkflowIdentifier.getVersion();
        this.workflowName = lyshraOpenAppPluginWorkflowIdentifier.getWorkflowName();
        this.workflowStepName = workflowStepName;
    }
}
