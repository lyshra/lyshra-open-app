package com.lyshra.open.app.integration.models.apis;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthProfile;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import lombok.Data;

import java.io.Serializable;

@Data
public class LyshraOpenAppApiAuthProfile implements ILyshraOpenAppApiAuthProfile, Serializable {
    private final String name;
    private final LyshraOpenAppApiAuthType type;

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
        TypeStep name(String n);
    }

    public interface TypeStep {
        BuildStep type(LyshraOpenAppApiAuthType t);
    }

    public interface BuildStep {
        LyshraOpenAppApiAuthProfile build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            TypeStep,
            BuildStep {

        private String name;
        private LyshraOpenAppApiAuthType type;

        @Override
        public TypeStep name(String n) {
            this.name = n;
            return this;
        }

        @Override
        public BuildStep type(LyshraOpenAppApiAuthType t) {
            this.type = t;
            return this;
        }

        @Override
        public LyshraOpenAppApiAuthProfile build() {
            return new LyshraOpenAppApiAuthProfile(
                    name,
                    type
            );
        }
    }
}