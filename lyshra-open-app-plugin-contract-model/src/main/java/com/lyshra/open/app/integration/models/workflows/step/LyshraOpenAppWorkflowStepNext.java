package com.lyshra.open.app.integration.models.workflows.step;

import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepNext;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Data
public class LyshraOpenAppWorkflowStepNext implements ILyshraOpenAppWorkflowStepNext, Serializable {
    private final Map<String, String> branches; // branchName: stepName

    public static InitialStepBuilder builder() { return new Builder(); }

    public interface InitialStepBuilder {
        BuildStep terminate();
        BuildStep defaultBranch(String defaultStepName);
        BuildStep booleanBranch(String trueStepName, String falseStepName);
        BuildStep branches(Map<String, String> branches);
    }
    public interface BuildStep { LyshraOpenAppWorkflowStepNext build(); }

    private static class Builder implements InitialStepBuilder, BuildStep {
        private final Map<String, String> branches = new HashMap<>();

        @Override
        public BuildStep terminate() {
            branches.put(LyshraOpenAppConstants.DEFAULT_BRANCH, LyshraOpenAppConstants.NOOP_PROCESSOR);
            return this;
        }

        @Override
        public BuildStep defaultBranch(String defaultStepName) {
            branches.put(LyshraOpenAppConstants.DEFAULT_BRANCH, defaultStepName);
            return this;
        }

        @Override
        public BuildStep booleanBranch(String trueStepName, String falseStepName) {
            branches.put(Boolean.TRUE.toString(), trueStepName);
            branches.put(Boolean.FALSE.toString(), falseStepName);
            return this;
        }

        @Override
        public BuildStep branches(Map<String, String> branches) {
            if (branches != null) {
                this.branches.putAll(branches);
            }
            return this;
        }

        @Override
        public LyshraOpenAppWorkflowStepNext build() {
            return new LyshraOpenAppWorkflowStepNext(Collections.unmodifiableMap(branches));
        }
    }
}
