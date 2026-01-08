package com.lyshra.open.app.core.exception.node;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import lombok.Data;

@Data
public class LyshraOpenAppWorkflowNotFound extends RuntimeException {
    private final ILyshraOpenAppPluginIdentifier identifier;

    public LyshraOpenAppWorkflowNotFound(ILyshraOpenAppWorkflowIdentifier identifier) {
        super("Workflow Not Found. Identifier: ["+ identifier +"]");
        this.identifier = identifier;
    }
}
