package com.lyshra.open.app.integration.contract.processor;

public interface ILyshraOpenAppProcessorResult<T extends ILyshraOpenAppProcessorIO> {
    String getBranch();
    T getOutput();
}
