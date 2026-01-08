package com.lyshra.open.app.integration.models.workflows.step;

import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStep;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepNext;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepOnError;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowContextRetention;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowStepType;
import lombok.Data;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Data
public class LyshraOpenAppWorkflowStep implements ILyshraOpenAppWorkflowStep, Serializable {
    private final String name;
    private final String description;
    private final LyshraOpenAppWorkflowContextRetention contextRetention; // FULL | LATEST
    private final LyshraOpenAppWorkflowStepType type; // PROCESSOR | WORKFLOW
    private final ILyshraOpenAppProcessorIdentifier processor; // organization:module:version:processorName
    private final ILyshraOpenAppWorkflowIdentifier workflowCall; // organization:module:version:workflowName
    private final List<Integer> coordinate; // for UI purpose
    private final Map<String, Object> inputConfig;
    private final Duration timeout;
    private final ILyshraOpenAppWorkflowStepNext next;
    private final ILyshraOpenAppWorkflowStepOnError onError;

    // Guided builder matching SampleIntegration order
    public static InitialStepBuilder builder() { return new Builder(); }

    public interface InitialStepBuilder { DescriptionStep name(String name); }
    public interface DescriptionStep { ContextRetentionStep description(String description); }
    public interface ContextRetentionStep extends ProcessorOrWorkflowStep { ProcessorOrWorkflowStep contextRetention(LyshraOpenAppWorkflowContextRetention contextRetention); }
    public interface ProcessorOrWorkflowStep {
        InputStep processor(ILyshraOpenAppProcessorIdentifier processor);
        InputStep workflowCall(ILyshraOpenAppWorkflowIdentifier workflowCall);
    }
    public interface InputStep { TimeoutStep inputConfig(Map<String, Object> input); }
    public interface TimeoutStep {
        NextStep timeout(Duration timeout);
        NextStep noTimeout();
    }
    public interface NextStep {
        OnErrorStep next(Function<LyshraOpenAppWorkflowStepNext.InitialStepBuilder, LyshraOpenAppWorkflowStepNext.BuildStep> builderFn);
        OnErrorStep next(LyshraOpenAppWorkflowStepNext next);
    }
    public interface OnErrorStep {
        BuildStep onError(Function<LyshraOpenAppWorkflowStepOnError.InitialStepBuilder, LyshraOpenAppWorkflowStepOnError.BuildStep> builderFn);
        BuildStep onError(LyshraOpenAppWorkflowStepOnError onError);
    }
    public interface BuildStep { LyshraOpenAppWorkflowStep build(); }

    private static class Builder implements InitialStepBuilder, ContextRetentionStep, DescriptionStep, ProcessorOrWorkflowStep, InputStep, TimeoutStep, NextStep, OnErrorStep, BuildStep {
        private String name;
        private String description;
        private LyshraOpenAppWorkflowContextRetention contextRetention;
        private LyshraOpenAppWorkflowStepType type;
        private ILyshraOpenAppProcessorIdentifier processor;
        private ILyshraOpenAppWorkflowIdentifier workflowCall;
        private Map<String, Object> inputConfig;
        private Duration timeout;
        private LyshraOpenAppWorkflowStepNext next;
        private LyshraOpenAppWorkflowStepOnError onError;

        @Override
        public DescriptionStep name(String name) { this.name = name; return this; }

        @Override
        public ContextRetentionStep description(String description) { this.description = description; return this; }

        @Override
        public ProcessorOrWorkflowStep contextRetention(LyshraOpenAppWorkflowContextRetention contextRetention) {
            this.contextRetention = contextRetention;
            return this;
        }

        @Override
        public InputStep processor(ILyshraOpenAppProcessorIdentifier processor) {
            this.type = LyshraOpenAppWorkflowStepType.PROCESSOR;
            this.processor = processor;
            return this;
        }

        @Override
        public InputStep workflowCall(ILyshraOpenAppWorkflowIdentifier workflowCall) {
            this.type = LyshraOpenAppWorkflowStepType.WORKFLOW;
            this.workflowCall = workflowCall;
            return this;
        }

        @Override
        public TimeoutStep inputConfig(Map<String, Object> input) { this.inputConfig = input; return this; }

        @Override
        public NextStep timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        @Override
        public NextStep noTimeout() {
            this.timeout = Duration.ZERO;
            return this;
        }

        @Override
        public OnErrorStep next(Function<LyshraOpenAppWorkflowStepNext.InitialStepBuilder, LyshraOpenAppWorkflowStepNext.BuildStep> builderFn) {
            this.next = builderFn.apply(LyshraOpenAppWorkflowStepNext.builder()).build();
            return this;
        }

        @Override
        public OnErrorStep next(LyshraOpenAppWorkflowStepNext next) {
            this.next = next;
            return this;
        }

        @Override
        public BuildStep onError(Function<LyshraOpenAppWorkflowStepOnError.InitialStepBuilder, LyshraOpenAppWorkflowStepOnError.BuildStep> builderFn) {
            this.onError = builderFn.apply(LyshraOpenAppWorkflowStepOnError.builder()).build();
            return this;
        }

        @Override
        public BuildStep onError(LyshraOpenAppWorkflowStepOnError onError) {
            this.onError = onError;
            return this;
        }

        @Override
        public LyshraOpenAppWorkflowStep build() {
            return new LyshraOpenAppWorkflowStep(
                    name,
                    description,
                    contextRetention,
                    type,
                    processor,
                    workflowCall,
                    null,
                    Collections.unmodifiableMap(inputConfig),
                    timeout,
                    next,
                    onError);
        }
    }
}
