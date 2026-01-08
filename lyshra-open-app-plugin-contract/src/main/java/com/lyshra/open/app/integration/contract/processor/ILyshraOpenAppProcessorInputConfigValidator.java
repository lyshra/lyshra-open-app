package com.lyshra.open.app.integration.contract.processor;

import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;

@FunctionalInterface
public interface ILyshraOpenAppProcessorInputConfigValidator {
    void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) throws LyshraOpenAppProcessorRuntimeException;
}
