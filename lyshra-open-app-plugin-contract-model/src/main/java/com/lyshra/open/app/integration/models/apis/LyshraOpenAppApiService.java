package com.lyshra.open.app.integration.models.apis;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiService;
import lombok.Data;

import java.io.Serializable;

@Data
public class LyshraOpenAppApiService implements ILyshraOpenAppApiService, Serializable {
    private final String name;
    private final String baseUrl;
    private final String authProfile; // refers to authProfile.name

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
        BaseUrlStep name(String n);
    }

    public interface BaseUrlStep {
        AuthProfileStep baseUrl(String url);
    }

    public interface AuthProfileStep {
        BuildStep authProfile(String ap);
    }

    public interface BuildStep {
        LyshraOpenAppApiService build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            BaseUrlStep,
            AuthProfileStep,
            BuildStep {

        private String name;
        private String baseUrl;
        private String authProfile;

        @Override
        public BaseUrlStep name(String n) {
            this.name = n;
            return this;
        }

        @Override
        public AuthProfileStep baseUrl(String url) {
            this.baseUrl = url;
            return this;
        }

        @Override
        public BuildStep authProfile(String ap) {
            this.authProfile = ap;
            return this;
        }

        @Override
        public LyshraOpenAppApiService build() {
            return new LyshraOpenAppApiService(
                    name,
                    baseUrl,
                    authProfile
            );
        }
    }
}