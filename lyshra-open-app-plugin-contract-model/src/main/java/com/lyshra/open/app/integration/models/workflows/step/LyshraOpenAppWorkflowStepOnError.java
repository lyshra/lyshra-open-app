package com.lyshra.open.app.integration.models.workflows.step;

import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepOnError;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepOnErrorConfig;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Data
public class LyshraOpenAppWorkflowStepOnError implements ILyshraOpenAppWorkflowStepOnError, Serializable {

    private final Map<String, ILyshraOpenAppWorkflowStepOnErrorConfig> errorConfigs; // errorCode: config

    public static InitialStepBuilder builder() { return new Builder(); }

    public interface InitialStepBuilder extends BuildStep {
        InitialStepBuilder defaultErrorConfig(Function<LyshraOpenAppWorkflowStepOnErrorConfig.InitialStepBuilder, LyshraOpenAppWorkflowStepOnErrorConfig.BuildStep> fn);
        InitialStepBuilder timeoutErrorConfig(Function<LyshraOpenAppWorkflowStepOnErrorConfig.InitialStepBuilder, LyshraOpenAppWorkflowStepOnErrorConfig.BuildStep> fn);
        InitialStepBuilder customErrorConfigs(String errorCode, Function<LyshraOpenAppWorkflowStepOnErrorConfig.InitialStepBuilder, LyshraOpenAppWorkflowStepOnErrorConfig.BuildStep> fn);
    }

    public interface BuildStep { LyshraOpenAppWorkflowStepOnError build(); }

    private static class Builder implements InitialStepBuilder, BuildStep {
        private final Map<String, LyshraOpenAppWorkflowStepOnErrorConfig> errorConfigs = new LinkedHashMap<>();

        @Override
        public InitialStepBuilder defaultErrorConfig(Function<LyshraOpenAppWorkflowStepOnErrorConfig.InitialStepBuilder, LyshraOpenAppWorkflowStepOnErrorConfig.BuildStep> fn) {
            errorConfigs.put(LyshraOpenAppConstants.DEFAULT_ERROR_CONFIG_KEY, fn.apply(LyshraOpenAppWorkflowStepOnErrorConfig.builder()).build());
            return this;
        }

        @Override
        public InitialStepBuilder timeoutErrorConfig(Function<LyshraOpenAppWorkflowStepOnErrorConfig.InitialStepBuilder, LyshraOpenAppWorkflowStepOnErrorConfig.BuildStep> fn) {
            errorConfigs.put(LyshraOpenAppConstants.TIMEOUT_ERROR_CONFIG_KEY, fn.apply(LyshraOpenAppWorkflowStepOnErrorConfig.builder()).build());
            return this;
        }

        @Override
        public InitialStepBuilder customErrorConfigs(String errorCode, Function<LyshraOpenAppWorkflowStepOnErrorConfig.InitialStepBuilder, LyshraOpenAppWorkflowStepOnErrorConfig.BuildStep> fn) {
            this.errorConfigs.put(errorCode, fn.apply(LyshraOpenAppWorkflowStepOnErrorConfig.builder()).build());
            return this;
        }

        @Override
        public LyshraOpenAppWorkflowStepOnError build() {
            return new LyshraOpenAppWorkflowStepOnError(Collections.unmodifiableMap(errorConfigs));
        }

    }
}
