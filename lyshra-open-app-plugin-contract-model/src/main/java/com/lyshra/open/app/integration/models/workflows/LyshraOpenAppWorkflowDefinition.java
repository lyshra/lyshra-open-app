package com.lyshra.open.app.integration.models.workflows;

import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflow;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStep;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowContextRetention;
import com.lyshra.open.app.integration.models.workflows.step.LyshraOpenAppWorkflowStep;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Data
public class LyshraOpenAppWorkflowDefinition implements ILyshraOpenAppWorkflow, Serializable {
    private final String name;
    private final String startStep;
    private final LyshraOpenAppWorkflowContextRetention contextRetention; // FULL | LATEST
    private final Map<String, ILyshraOpenAppWorkflowStep> steps;

    public static InitialStepBuilder builder() { return new Builder(); }

    public interface InitialStepBuilder { StartStep name(String name); }
    public interface StartStep { ContextRetentionStep startStep(String startStep); }
    public interface ContextRetentionStep { StepsStep contextRetention(LyshraOpenAppWorkflowContextRetention contextRetention); }
    public interface StepsStep { BuildStep steps(Function<StepsBuilder, StepsBuilder> f); }
    public interface BuildStep { LyshraOpenAppWorkflowDefinition build(); }

    public static class StepsBuilder {
        private final Map<String, ILyshraOpenAppWorkflowStep> map = new LinkedHashMap<>();

        public StepsBuilder step(Function<LyshraOpenAppWorkflowStep.InitialStepBuilder, LyshraOpenAppWorkflowStep.BuildStep> fn) {
            LyshraOpenAppWorkflowStep step = fn.apply(LyshraOpenAppWorkflowStep.builder()).build();
            map.put(step.getName(), step);
            return this;
        }

        public StepsBuilder step(ILyshraOpenAppWorkflowStep step) {
            map.put(step.getName(), step);
            return this;
        }

        Map<String, ILyshraOpenAppWorkflowStep> build() { return map; }
    }

    private static class Builder implements InitialStepBuilder, StartStep, ContextRetentionStep, StepsStep, BuildStep {
        private String name;
        private String startStep;
        private LyshraOpenAppWorkflowContextRetention contextRetention;
        private Map<String, ILyshraOpenAppWorkflowStep> steps;

        @Override
        public StartStep name(String name) { this.name = name; return this; }

        @Override
        public ContextRetentionStep startStep(String startStep) { this.startStep = startStep; return this; }

        @Override
        public StepsStep contextRetention(LyshraOpenAppWorkflowContextRetention contextRetention) {
            this.contextRetention = contextRetention;
            return this;
        }

        @Override
        public BuildStep steps(Function<StepsBuilder, StepsBuilder> f) {
            StepsBuilder sb = f.apply(new StepsBuilder());
            this.steps = sb.build();
            return this;
        }

        @Override
        public LyshraOpenAppWorkflowDefinition build() {
            return new LyshraOpenAppWorkflowDefinition(
                    name,
                    startStep,
                    contextRetention,
                    Collections.unmodifiableMap(steps)
            );
        }
    }
}
