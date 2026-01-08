package com.lyshra.open.app.integration.models;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPlugin;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApis;
import com.lyshra.open.app.integration.contract.i18n.ILyshraOpenAppI18nConfig;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessors;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflows;
import com.lyshra.open.app.integration.models.apis.LyshraOpenAppApiDefinitions;
import com.lyshra.open.app.integration.models.i18n.LyshraOpenAppI18nConfig;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinitions;
import com.lyshra.open.app.integration.models.workflows.LyshraOpenAppWorkflowDefinitions;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.function.Function;

@Getter
@AllArgsConstructor
@ToString
public class LyshraOpenAppPlugin implements ILyshraOpenAppPlugin, Serializable {
    private ILyshraOpenAppPluginIdentifier identifier;
    private String documentationResourcePath;
    private ILyshraOpenAppApis apis;
    private ILyshraOpenAppProcessors processors;
    private ILyshraOpenAppWorkflows workflows;
    private ILyshraOpenAppI18nConfig i18n;

    // ---------------------------------------------------------
    // Guided Builder
    // ---------------------------------------------------------

    public static LyshraOpenAppPluginIdentifierBuilderStep builder() { return new Builder(); }

    public interface LyshraOpenAppPluginIdentifierBuilderStep {
        DocumentationStep identifier(ILyshraOpenAppPluginIdentifier organization);
        DocumentationStep identifier(Function<LyshraOpenAppPluginIdentifier.InitialStepBuilder, LyshraOpenAppPluginIdentifier.BuildStep> builderFn);
    }

    public interface DocumentationStep {
        ApiStep documentationResourcePath(String documentationResourcePath);
    }

    public interface ApiStep extends ProcessorStep {
        ProcessorStep apis(Function<LyshraOpenAppApiDefinitions.InitialStepBuilder, LyshraOpenAppApiDefinitions.BuildStep> builderFn);
    }

    public interface ProcessorStep extends WorkflowStep {
        WorkflowStep processors(Function<LyshraOpenAppProcessorDefinitions.InitialStepBuilder, LyshraOpenAppProcessorDefinitions.BuildStep> builderFn);
    }

    public interface WorkflowStep extends I18nStep {
        I18nStep workflows(Function<LyshraOpenAppWorkflowDefinitions.InitialStepBuilder, LyshraOpenAppWorkflowDefinitions.BuildStep> builderFn);
    }

    public interface I18nStep {
        BuildStep i18n(Function<com.lyshra.open.app.integration.models.i18n.LyshraOpenAppI18nConfig.InitialStepBuilder, com.lyshra.open.app.integration.models.i18n.LyshraOpenAppI18nConfig.BuildStep> builderFn);
    }

    public interface BuildStep { LyshraOpenAppPlugin build(); }

    private static class Builder implements LyshraOpenAppPluginIdentifierBuilderStep, DocumentationStep, ApiStep, ProcessorStep, WorkflowStep, I18nStep, BuildStep {
        private ILyshraOpenAppPluginIdentifier identifier;
        private String documentationResourcePath;
        private LyshraOpenAppApiDefinitions apis;
        private LyshraOpenAppProcessorDefinitions processors;
        private LyshraOpenAppWorkflowDefinitions workflows;
        private LyshraOpenAppI18nConfig i18n;

        @Override
        public DocumentationStep identifier(ILyshraOpenAppPluginIdentifier identifier) {
            this.identifier = identifier;
            return this;
        }

        @Override
        public DocumentationStep identifier(Function<LyshraOpenAppPluginIdentifier.InitialStepBuilder, LyshraOpenAppPluginIdentifier.BuildStep> builderFn) {
            this.identifier = builderFn.apply(LyshraOpenAppPluginIdentifier.builder()).build();
            return this;
        }

        @Override
        public ApiStep documentationResourcePath(String documentationResourcePath) {
            this.documentationResourcePath = documentationResourcePath;
            return this;
        }

        @Override
        public ProcessorStep apis(Function<LyshraOpenAppApiDefinitions.InitialStepBuilder, LyshraOpenAppApiDefinitions.BuildStep> builderFn) {
            this.apis = builderFn.apply(LyshraOpenAppApiDefinitions.builder()).build();
            return this;
        }

        @Override
        public WorkflowStep processors(Function<LyshraOpenAppProcessorDefinitions.InitialStepBuilder, LyshraOpenAppProcessorDefinitions.BuildStep> builderFn) {
            this.processors = builderFn.apply(LyshraOpenAppProcessorDefinitions.builder()).build();
            return this;
        }

        @Override
        public I18nStep workflows(Function<LyshraOpenAppWorkflowDefinitions.InitialStepBuilder, LyshraOpenAppWorkflowDefinitions.BuildStep> builderFn) {
            this.workflows = builderFn.apply(LyshraOpenAppWorkflowDefinitions.builder()).build();
            return this;
        }

        @Override
        public BuildStep i18n(Function<LyshraOpenAppI18nConfig.InitialStepBuilder, LyshraOpenAppI18nConfig.BuildStep> builderFn) {
            this.i18n = builderFn.apply(LyshraOpenAppI18nConfig.builder()).build();
            return this;
        }

        @Override
        public LyshraOpenAppPlugin build() {
            return new LyshraOpenAppPlugin(identifier, documentationResourcePath, apis, processors, workflows, i18n);
        }
    }
}
