package com.lyshra.open.app.integration.contract.processor;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ILyshraOpenAppProcessorFunction {
    Mono<ILyshraOpenAppProcessorResult<? extends ILyshraOpenAppProcessorIO>> process(
            ILyshraOpenAppProcessorInputConfig inputConfig,
            ILyshraOpenAppContext context,
            ILyshraOpenAppPluginFacade facade) throws LyshraOpenAppProcessorRuntimeException;
}
