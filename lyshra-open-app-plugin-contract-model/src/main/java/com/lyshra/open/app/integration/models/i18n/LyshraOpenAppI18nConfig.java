package com.lyshra.open.app.integration.models.i18n;

import com.lyshra.open.app.integration.contract.i18n.ILyshraOpenAppI18nConfig;
import lombok.Data;

import java.io.Serializable;

@Data
public class LyshraOpenAppI18nConfig implements ILyshraOpenAppI18nConfig, Serializable {
    private final String resourceBundleBasenamePath;

    // Guided builder
    public static InitialStepBuilder builder() { return new Builder(); }

    public interface InitialStepBuilder { BuildStep resourceBundleBasenamePath(String resourceBundleBasenamePath); }
    public interface BuildStep { LyshraOpenAppI18nConfig build(); }

    private static class Builder implements InitialStepBuilder, BuildStep {
        private String resourceBundleBasenamePath;

        @Override
        public BuildStep resourceBundleBasenamePath(String resourceBundleBasenamePath) {
            this.resourceBundleBasenamePath = resourceBundleBasenamePath;
            return this;
        }

        @Override
        public LyshraOpenAppI18nConfig build() {
            return new LyshraOpenAppI18nConfig(resourceBundleBasenamePath);
        }

    }
}
