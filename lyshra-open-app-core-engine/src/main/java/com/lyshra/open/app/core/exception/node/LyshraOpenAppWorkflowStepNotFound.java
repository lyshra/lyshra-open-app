package com.lyshra.open.app.core.exception.node;

import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;
import lombok.Data;

@Data
public class LyshraOpenAppWorkflowStepNotFound extends RuntimeException {
    private final ILyshraOpenAppWorkflowStepIdentifier identifier;

    public LyshraOpenAppWorkflowStepNotFound(ILyshraOpenAppWorkflowStepIdentifier identifier) {
        super("Workflow Step Not Found. Identifier: ["+ identifier +"]");
        this.identifier = identifier;
    }

}
