package com.lyshra.open.app.integration.models.processors;

import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIO;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import lombok.Data;

@Data
public class LyshraOpenAppProcessorOutputResult<T extends ILyshraOpenAppProcessorIO> implements ILyshraOpenAppProcessorResult<T> {
    private final String branch;
    private final T output;

    public LyshraOpenAppProcessorOutputResult(String branch) {
        this(branch, null);
    }

    public LyshraOpenAppProcessorOutputResult(T output) {
        this(LyshraOpenAppConstants.DEFAULT_BRANCH, output);
    }

    public LyshraOpenAppProcessorOutputResult(String branch, T output) {
        this.branch = branch;
        this.output = output;
    }
}
