package com.lyshra.open.app.integration.models.apis;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiResponseStatusIdentifier;
import com.lyshra.open.app.integration.models.commons.LyshraOpenAppExpression;
import lombok.Data;

import java.io.Serializable;

@Data
public class LyshraOpenAppApiResponseStatusIdentifier implements ILyshraOpenAppApiResponseStatusIdentifier, Serializable {
    private final ILyshraOpenAppExpression success;
    private final ILyshraOpenAppExpression failure;
    private final ILyshraOpenAppExpression retry;

    // ---------------------------------------------------------
    // Builder Entry
    // ---------------------------------------------------------
    public static InitialStepBuilder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------
    // Step Interfaces
    // ---------------------------------------------------------
    public interface InitialStepBuilder {
        FailureStep success(LyshraOpenAppExpression s);
    }

    public interface FailureStep {
        RetryStep failure(LyshraOpenAppExpression f);
    }

    public interface RetryStep {
        BuildStep retry(LyshraOpenAppExpression r);
    }

    public interface BuildStep {
        LyshraOpenAppApiResponseStatusIdentifier build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            FailureStep,
            RetryStep,
            BuildStep {

        private LyshraOpenAppExpression success;
        private LyshraOpenAppExpression failure;
        private LyshraOpenAppExpression retry;

        @Override
        public FailureStep success(LyshraOpenAppExpression s) {
            this.success = s;
            return this;
        }

        @Override
        public RetryStep failure(LyshraOpenAppExpression f) {
            this.failure = f;
            return this;
        }

        @Override
        public BuildStep retry(LyshraOpenAppExpression r) {
            this.retry = r;
            return this;
        }

        @Override
        public LyshraOpenAppApiResponseStatusIdentifier build() {
            return new LyshraOpenAppApiResponseStatusIdentifier(
                    success,
                    failure,
                    retry
            );
        }
    }
}