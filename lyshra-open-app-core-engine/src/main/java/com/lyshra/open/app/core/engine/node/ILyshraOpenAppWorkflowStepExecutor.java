package com.lyshra.open.app.core.engine.node;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;
import reactor.core.publisher.Mono;

public interface ILyshraOpenAppWorkflowStepExecutor {
    Mono<String> execute(ILyshraOpenAppWorkflowStepIdentifier identifier, ILyshraOpenAppContext context);
}
