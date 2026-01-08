package com.lyshra.open.app.core.engine.node;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import reactor.core.publisher.Mono;

public interface ILyshraOpenAppWorkflowExecutor {
    Mono<ILyshraOpenAppContext> execute(ILyshraOpenAppWorkflowIdentifier identifier, ILyshraOpenAppContext context);
}
