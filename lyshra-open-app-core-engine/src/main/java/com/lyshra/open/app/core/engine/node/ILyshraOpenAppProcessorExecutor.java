package com.lyshra.open.app.core.engine.node;

import com.lyshra.open.app.core.exception.node.LyshraOpenAppProcessorExecutionException;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIO;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface ILyshraOpenAppProcessorExecutor {

    Mono<ILyshraOpenAppProcessorResult<? extends ILyshraOpenAppProcessorIO>> execute(
            ILyshraOpenAppProcessorIdentifier identifier,
            Map<String, Object> inputConfig,
            ILyshraOpenAppContext context) throws LyshraOpenAppProcessorExecutionException;

}
