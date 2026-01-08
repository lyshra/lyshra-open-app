package com.lyshra.open.app.integration.models.workflows.step;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppRetryPolicy;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepOnErrorConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppOnErrorStrategy;
import com.lyshra.open.app.integration.models.commons.LyshraOpenAppRetryPolicy;
import lombok.Data;

import java.io.Serializable;
import java.util.function.Function;

@Data
public class LyshraOpenAppWorkflowStepOnErrorConfig implements ILyshraOpenAppWorkflowStepOnErrorConfig, Serializable {
    private final LyshraOpenAppOnErrorStrategy strategy; // CONTINUE | ABORT | FALLBACK
    private final ILyshraOpenAppRetryPolicy retryPolicy;
    private final String fallbackWorkflowStep; // organization:module:version:processorName

    public static InitialStepBuilder builder() { return new Builder(); }

    public interface InitialStepBuilder extends StrategyStep {
        StrategyStep retryPolicy(Function<LyshraOpenAppRetryPolicy.InitialStepBuilder, LyshraOpenAppRetryPolicy.BuildStep> builderFn);
    }

    public interface StrategyStep {
        BuildStep endWorkflow();
        BuildStep abortOnError();
        BuildStep fallbackOnError(String fallbackWorkflowStep);
    }

    public interface BuildStep {
        LyshraOpenAppWorkflowStepOnErrorConfig build();
    }

    private static class Builder implements InitialStepBuilder, StrategyStep, BuildStep {
        private LyshraOpenAppOnErrorStrategy strategy;
        private LyshraOpenAppRetryPolicy retryPolicy;
        private String fallbackWorkflowStep;

        @Override
        public StrategyStep retryPolicy(Function<LyshraOpenAppRetryPolicy.InitialStepBuilder, LyshraOpenAppRetryPolicy.BuildStep> builderFn) {
            if (builderFn != null) {
                this.retryPolicy = builderFn.apply(LyshraOpenAppRetryPolicy.builder()).build();
            }
            return this;
        }

        @Override
        public BuildStep endWorkflow() {
            this.strategy = LyshraOpenAppOnErrorStrategy.END_WORKFLOW;
            return this;
        }

        @Override
        public BuildStep abortOnError() {
            this.strategy = LyshraOpenAppOnErrorStrategy.ABORT_WORKFLOW;
            return this;
        }

        @Override
        public BuildStep fallbackOnError(String fallbackWorkflowStep) {
            this.strategy = LyshraOpenAppOnErrorStrategy.USE_FALLBACK_STEP;
            this.fallbackWorkflowStep = fallbackWorkflowStep;
            return this;
        }

        @Override
        public LyshraOpenAppWorkflowStepOnErrorConfig build() {
            return new LyshraOpenAppWorkflowStepOnErrorConfig(strategy, retryPolicy, fallbackWorkflowStep);
        }

    }
}
