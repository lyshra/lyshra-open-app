package com.lyshra.open.app.core.engine.node;

import com.lyshra.open.app.core.exception.node.LyshraOpenAppProcessorExecutionException;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface ILyshraOpenAppProcessorExecutor {

    /**
     * Responsible for executing a processor
     * It should return the:
     * 1. Branch: a branch which can be mapped to the next step (branch is not step itself, but maps to a step)
     * 2. Output: the output of the processor, which can be used by the next step.
     *      - The output must be non-primitive and should be extended from ILyshraOpenAppProcessorIO.
     *      - The output of this processor will be updated as context.$data
     *      - As the context is always available to all processors, it can be used to share data between processors.
     *      - Generally the output of processor 1 should be used as input for processor 2.
     */
    Mono<ILyshraOpenAppProcessorResult> execute(
            ILyshraOpenAppProcessorIdentifier identifier,
            Map<String, Object> inputConfig,
            ILyshraOpenAppContext context) throws LyshraOpenAppProcessorExecutionException;

}
