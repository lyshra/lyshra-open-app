package com.lyshra.open.app.integration.models.workflows;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import lombok.Data;

@Data
public class LyshraOpenAppWorkflowIdentifier implements ILyshraOpenAppWorkflowIdentifier {
    private final String organization;
    private final String module;
    private final String version;
    private final String workflowName;

    public LyshraOpenAppWorkflowIdentifier(ILyshraOpenAppPluginIdentifier lyshraOpenAppPluginIdentifier, String workflowName) {
        this.organization = lyshraOpenAppPluginIdentifier.getOrganization();
        this.module = lyshraOpenAppPluginIdentifier.getModule();
        this.version = lyshraOpenAppPluginIdentifier.getVersion();
        this.workflowName = workflowName;
    }
}
