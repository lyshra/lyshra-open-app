package com.lyshra.open.app.integration.contract.processor;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;

import java.util.List;

public interface ILyshraOpenAppProcessor {
    String getName();
    Class<? extends ILyshraOpenAppErrorInfo> getErrorCodeEnum();
    Class<? extends ILyshraOpenAppProcessorInputConfig> getInputConfigType();
    List<? extends ILyshraOpenAppProcessorInputConfig> getSampleInputConfigs();
    ILyshraOpenAppProcessorInputConfigValidator getInputConfigValidator();
    ILyshraOpenAppProcessorFunction getProcessorFunction();
}
