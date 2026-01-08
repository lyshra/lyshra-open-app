package com.lyshra.open.app.integration.models.workflows;

import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflow;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflows;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Collection of workflow definitions with guided builder similar to API definitions.
 */
@Data
public class LyshraOpenAppWorkflowDefinitions implements ILyshraOpenAppWorkflows, Serializable {
    private final Map<String, ILyshraOpenAppWorkflow> workflows;

    // Entry
    public static InitialStepBuilder builder() { return new Builder(); }

    // Steps
    public interface InitialStepBuilder extends BuildStep {
        InitialStepBuilder workflow(Function<LyshraOpenAppWorkflowDefinition.InitialStepBuilder, LyshraOpenAppWorkflowDefinition.BuildStep> fn);
        InitialStepBuilder workflow(ILyshraOpenAppWorkflow def);
    }
    public interface BuildStep { LyshraOpenAppWorkflowDefinitions build(); }

    // Main builder acts as collection
    private static class Builder implements InitialStepBuilder, BuildStep {
        private final Map<String, ILyshraOpenAppWorkflow> map = new LinkedHashMap<>();

        @Override
        public InitialStepBuilder workflow(Function<LyshraOpenAppWorkflowDefinition.InitialStepBuilder, LyshraOpenAppWorkflowDefinition.BuildStep> fn) {
            LyshraOpenAppWorkflowDefinition def = fn.apply(LyshraOpenAppWorkflowDefinition.builder()).build();
            map.put(def.getName(), def);
            return this;
        }

        @Override
        public InitialStepBuilder workflow(ILyshraOpenAppWorkflow def) {
            map.put(def.getName(), def);
            return this;
        }

        @Override
        public LyshraOpenAppWorkflowDefinitions build() {
            return new LyshraOpenAppWorkflowDefinitions(Collections.unmodifiableMap(map));
        }
    }
}
