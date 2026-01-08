package com.lyshra.open.app.integration.models;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import lombok.Data;

import java.io.Serializable;

@Data
public class LyshraOpenAppPluginIdentifier implements ILyshraOpenAppPluginIdentifier, Serializable {
    private final String organization;
    private final String module;
    private final String version;

    // ---------------------------------------------------------
    // Guided Builder
    // ---------------------------------------------------------

    public static InitialStepBuilder builder() { return new Builder(); }

    public interface InitialStepBuilder { ModuleStep organization(String organization); }
    public interface ModuleStep { VersionStep module(String module); }
    public interface VersionStep { BuildStep version(String version); }
    public interface BuildStep { LyshraOpenAppPluginIdentifier build(); }

    private static class Builder implements InitialStepBuilder, ModuleStep, VersionStep, BuildStep {
        private String organization;
        private String module;
        private String version;

        @Override
        public ModuleStep organization(String organization) {
            this.organization = organization;
            return this;
        }

        @Override
        public VersionStep module(String module) {
            this.module = module;
            return this;
        }

        @Override
        public BuildStep version(String version) {
            this.version = version;
            return this;
        }

        @Override
        public LyshraOpenAppPluginIdentifier build() {
            return new LyshraOpenAppPluginIdentifier(organization, module, version);
        }
    }
}

