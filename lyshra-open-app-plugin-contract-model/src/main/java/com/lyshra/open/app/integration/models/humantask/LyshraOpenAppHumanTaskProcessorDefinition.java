package com.lyshra.open.app.integration.models.humantask;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskEscalation;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskFormSchema;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorFunction;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfigValidator;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import lombok.Data;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Builder-based definition for human task processors.
 * Extends the base processor definition with human task specific configuration.
 *
 * <p>Design Pattern: Guided Builder Pattern
 * - Ensures all required fields are provided in order
 * - Provides compile-time safety for processor construction
 */
@Data
public class LyshraOpenAppHumanTaskProcessorDefinition implements ILyshraOpenAppHumanTaskProcessor, Serializable {

    private final String name;
    private final String humanReadableNameTemplate;
    private final String searchTagsCsvTemplate;
    private final Class<? extends ILyshraOpenAppErrorInfo> errorCodeEnum;
    private final Class<? extends ILyshraOpenAppProcessorInputConfig> inputConfigType;
    private final List<ILyshraOpenAppProcessorInputConfig> sampleInputConfigs;
    private final ILyshraOpenAppProcessorInputConfigValidator inputConfigValidator;
    private final ILyshraOpenAppProcessorFunction processorFunction;
    private final LyshraOpenAppHumanTaskType humanTaskType;
    private final Duration defaultTimeout;
    private final ILyshraOpenAppHumanTaskEscalation defaultEscalationConfig;
    private final List<String> validOutcomeBranches;
    private final boolean supportsClaming;
    private final boolean supportsDelegation;
    private final boolean requiresFormSubmission;
    private final ILyshraOpenAppHumanTaskFormSchema defaultFormSchema;

    // Guided Builder
    public static InitialStepBuilder builder() {
        return new Builder();
    }

    public interface InitialStepBuilder {
        HumanReadableNameStep name(String name);
    }

    public interface HumanReadableNameStep {
        SearchTagsStep humanReadableNameTemplate(String template);
    }

    public interface SearchTagsStep {
        ErrorCodeEnumStep searchTagsCsvTemplate(String template);
    }

    public interface ErrorCodeEnumStep {
        InputConfigTypeStep errorCodeEnum(Class<? extends ILyshraOpenAppErrorInfo> e);
    }

    public interface InputConfigTypeStep {
        SampleInputConfigsStep inputConfigType(Class<? extends ILyshraOpenAppProcessorInputConfig> i);
    }

    public interface SampleInputConfigsStep {
        InputValidatorStep sampleInputConfigs(List<ILyshraOpenAppProcessorInputConfig> s);
    }

    public interface InputValidatorStep {
        HumanTaskTypeStep validateInputConfig(ILyshraOpenAppProcessorInputConfigValidator v);
    }

    public interface HumanTaskTypeStep {
        OutcomeBranchesStep humanTaskType(LyshraOpenAppHumanTaskType type);
    }

    public interface OutcomeBranchesStep {
        ProcessorFunctionStep validOutcomeBranches(List<String> branches);
    }

    public interface ProcessorFunctionStep {
        BuildStep process(ILyshraOpenAppProcessorFunction f);
    }

    public interface BuildStep {
        BuildStep defaultTimeout(Duration timeout);

        BuildStep defaultEscalation(ILyshraOpenAppHumanTaskEscalation escalation);

        BuildStep supportsClaming(boolean supports);

        BuildStep supportsDelegation(boolean supports);

        BuildStep requiresFormSubmission(boolean requires);

        BuildStep defaultFormSchema(ILyshraOpenAppHumanTaskFormSchema schema);

        LyshraOpenAppHumanTaskProcessorDefinition build();
    }

    private static class Builder implements
            InitialStepBuilder, HumanReadableNameStep, SearchTagsStep, ErrorCodeEnumStep,
            InputConfigTypeStep, SampleInputConfigsStep, InputValidatorStep, HumanTaskTypeStep,
            OutcomeBranchesStep, ProcessorFunctionStep, BuildStep {

        private String name;
        private String humanReadableNameTemplate;
        private String searchTagsCsvTemplate;
        private Class<? extends ILyshraOpenAppErrorInfo> errorCodeEnum;
        private Class<? extends ILyshraOpenAppProcessorInputConfig> inputConfigType;
        private List<ILyshraOpenAppProcessorInputConfig> sampleInputConfigs;
        private ILyshraOpenAppProcessorInputConfigValidator inputValidator;
        private ILyshraOpenAppProcessorFunction processorFunction;
        private LyshraOpenAppHumanTaskType humanTaskType;
        private List<String> validOutcomeBranches;
        private Duration defaultTimeout;
        private ILyshraOpenAppHumanTaskEscalation defaultEscalation;
        private boolean supportsClaming = true;
        private boolean supportsDelegation = true;
        private boolean requiresFormSubmission = false;
        private ILyshraOpenAppHumanTaskFormSchema defaultFormSchema;

        @Override
        public HumanReadableNameStep name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public SearchTagsStep humanReadableNameTemplate(String template) {
            this.humanReadableNameTemplate = template;
            return this;
        }

        @Override
        public ErrorCodeEnumStep searchTagsCsvTemplate(String template) {
            this.searchTagsCsvTemplate = template;
            return this;
        }

        @Override
        public InputConfigTypeStep errorCodeEnum(Class<? extends ILyshraOpenAppErrorInfo> e) {
            this.errorCodeEnum = e;
            return this;
        }

        @Override
        public SampleInputConfigsStep inputConfigType(Class<? extends ILyshraOpenAppProcessorInputConfig> i) {
            this.inputConfigType = i;
            return this;
        }

        @Override
        public InputValidatorStep sampleInputConfigs(List<ILyshraOpenAppProcessorInputConfig> s) {
            this.sampleInputConfigs = s;
            return this;
        }

        @Override
        public HumanTaskTypeStep validateInputConfig(ILyshraOpenAppProcessorInputConfigValidator v) {
            this.inputValidator = v;
            return this;
        }

        @Override
        public OutcomeBranchesStep humanTaskType(LyshraOpenAppHumanTaskType type) {
            this.humanTaskType = type;
            return this;
        }

        @Override
        public ProcessorFunctionStep validOutcomeBranches(List<String> branches) {
            this.validOutcomeBranches = branches;
            return this;
        }

        @Override
        public BuildStep process(ILyshraOpenAppProcessorFunction f) {
            this.processorFunction = f;
            return this;
        }

        @Override
        public BuildStep defaultTimeout(Duration timeout) {
            this.defaultTimeout = timeout;
            return this;
        }

        @Override
        public BuildStep defaultEscalation(ILyshraOpenAppHumanTaskEscalation escalation) {
            this.defaultEscalation = escalation;
            return this;
        }

        @Override
        public BuildStep supportsClaming(boolean supports) {
            this.supportsClaming = supports;
            return this;
        }

        @Override
        public BuildStep supportsDelegation(boolean supports) {
            this.supportsDelegation = supports;
            return this;
        }

        @Override
        public BuildStep requiresFormSubmission(boolean requires) {
            this.requiresFormSubmission = requires;
            return this;
        }

        @Override
        public BuildStep defaultFormSchema(ILyshraOpenAppHumanTaskFormSchema schema) {
            this.defaultFormSchema = schema;
            return this;
        }

        @Override
        public LyshraOpenAppHumanTaskProcessorDefinition build() {
            return new LyshraOpenAppHumanTaskProcessorDefinition(
                    name,
                    humanReadableNameTemplate,
                    searchTagsCsvTemplate,
                    errorCodeEnum,
                    inputConfigType,
                    Collections.unmodifiableList(sampleInputConfigs),
                    inputValidator,
                    processorFunction,
                    humanTaskType,
                    defaultTimeout,
                    defaultEscalation,
                    Collections.unmodifiableList(validOutcomeBranches),
                    supportsClaming,
                    supportsDelegation,
                    requiresFormSubmission,
                    defaultFormSchema
            );
        }
    }

    // ILyshraOpenAppHumanTaskProcessor implementation

    @Override
    public Optional<Duration> getDefaultTimeout() {
        return Optional.ofNullable(defaultTimeout);
    }

    @Override
    public Optional<ILyshraOpenAppHumanTaskEscalation> getDefaultEscalationConfig() {
        return Optional.ofNullable(defaultEscalationConfig);
    }

    @Override
    public ILyshraOpenAppHumanTaskConfigBuilder getTaskConfigBuilder() {
        return new DefaultHumanTaskConfigBuilder();
    }

    @Override
    public boolean supportsClaming() {
        return supportsClaming;
    }

    @Override
    public boolean supportsDelegation() {
        return supportsDelegation;
    }

    @Override
    public boolean requiresFormSubmission() {
        return requiresFormSubmission;
    }

    @Override
    public Optional<ILyshraOpenAppHumanTaskFormSchema> getDefaultFormSchema() {
        return Optional.ofNullable(defaultFormSchema);
    }
}
