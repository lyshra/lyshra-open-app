package com.lyshra.open.app.integration.models.processors;

import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import lombok.Data;

@Data
public class LyshraOpenAppProcessorOutput implements ILyshraOpenAppProcessorResult {
    private final String branch;
    private final Object data;

    private LyshraOpenAppProcessorOutput(String branch, Object data) {
        this.branch = branch;
        this.data = data;
    }

    public static LyshraOpenAppProcessorOutput of(String branch, Object data) {
        return new LyshraOpenAppProcessorOutput(branch, data);
    }

    public static LyshraOpenAppProcessorOutput ofDefaultBranch() {
        return new LyshraOpenAppProcessorOutput(LyshraOpenAppConstants.DEFAULT_BRANCH, null);
    }

    public static LyshraOpenAppProcessorOutput ofBranch(String branch) {
        return new LyshraOpenAppProcessorOutput(branch, null);
    }

    public static LyshraOpenAppProcessorOutput ofBranch(Boolean branch) {
        return new LyshraOpenAppProcessorOutput(branch.toString(), null);
    }

    public static LyshraOpenAppProcessorOutput ofData(Object data) {
        return new LyshraOpenAppProcessorOutput(LyshraOpenAppConstants.DEFAULT_BRANCH, data);
    }

}
