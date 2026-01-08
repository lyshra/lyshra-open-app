package com.lyshra.open.app.integration.models.commons;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppRetryPolicy;
import lombok.Data;

import java.io.Serializable;

@Data
public class LyshraOpenAppRetryPolicy implements ILyshraOpenAppRetryPolicy, Serializable {
    private final int maxAttempts;
    private final long initialIntervalMs;
    private final double multiplier;
    private final long maxIntervalMs;

    // ---------------------------------------------------------
    // Builder entry
    // ---------------------------------------------------------
    public static InitialStepBuilder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------
    // Step Interfaces
    // ---------------------------------------------------------
    public interface InitialStepBuilder {
        InitialIntervalStep maxAttempts(int m);
    }

    public interface InitialIntervalStep {
        MultiplierStep initialIntervalMs(long i);
    }

    public interface MultiplierStep {
        MaxIntervalStep multiplier(double mult);
    }

    public interface MaxIntervalStep {
        BuildStep maxIntervalMs(long max);
    }

    public interface BuildStep {
        LyshraOpenAppRetryPolicy build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            InitialIntervalStep,
            MultiplierStep,
            MaxIntervalStep,
            BuildStep {

        private int maxAttempts;
        private long initialIntervalMs;
        private double multiplier;
        private long maxIntervalMs;

        @Override
        public InitialIntervalStep maxAttempts(int m) {
            this.maxAttempts = m;
            return this;
        }

        @Override
        public MultiplierStep initialIntervalMs(long i) {
            this.initialIntervalMs = i;
            return this;
        }

        @Override
        public MaxIntervalStep multiplier(double mult) {
            this.multiplier = mult;
            return this;
        }

        @Override
        public BuildStep maxIntervalMs(long max) {
            this.maxIntervalMs = max;
            return this;
        }

        @Override
        public LyshraOpenAppRetryPolicy build() {
            return new LyshraOpenAppRetryPolicy(
                    maxAttempts,
                    initialIntervalMs,
                    multiplier,
                    maxIntervalMs
            );
        }
    }
}