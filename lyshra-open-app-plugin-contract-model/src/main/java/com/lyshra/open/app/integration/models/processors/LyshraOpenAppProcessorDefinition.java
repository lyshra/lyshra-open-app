package com.lyshra.open.app.integration.models.processors;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessor;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorFunction;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfigValidator;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
public class LyshraOpenAppProcessorDefinition implements ILyshraOpenAppProcessor, Serializable {
    private final String name;
    private final String humanReadableNameTemplate;
    private final String searchTagsCsvTemplate;
    private final Class<? extends ILyshraOpenAppErrorInfo> errorCodeEnum;
    private final Class<? extends ILyshraOpenAppProcessorInputConfig> inputConfigType;
    private final List<ILyshraOpenAppProcessorInputConfig> sampleInputConfigs;
    private final ILyshraOpenAppProcessorInputConfigValidator inputConfigValidator;
    private final ILyshraOpenAppProcessorFunction processorFunction;

    // Guided builder
    public static InitialStepBuilder builder() { return new Builder(); }

    public interface InitialStepBuilder { HumanReadableNameStep name(String name); }
    public interface HumanReadableNameStep { SearchTagsStep humanReadableNameTemplate(String template); }
    public interface SearchTagsStep { ErrorCodeEnumStep searchTagsCsvTemplate(String template); }
    public interface ErrorCodeEnumStep { InputConfigTypeStep errorCodeEnum(Class<? extends ILyshraOpenAppErrorInfo> e); }
    public interface InputConfigTypeStep { SampleInputConfigsStep inputConfigType(Class<? extends ILyshraOpenAppProcessorInputConfig> i); }
    public interface SampleInputConfigsStep { InputValidatorStep sampleInputConfigs(List<ILyshraOpenAppProcessorInputConfig> s); }
    public interface InputValidatorStep { ProcessorFunctionStep validateInputConfig(ILyshraOpenAppProcessorInputConfigValidator v); }
    public interface ProcessorFunctionStep { BuildStep process(ILyshraOpenAppProcessorFunction f); }
    public interface BuildStep { LyshraOpenAppProcessorDefinition build(); }

    private static class Builder implements InitialStepBuilder, HumanReadableNameStep, SearchTagsStep, ErrorCodeEnumStep, InputConfigTypeStep, SampleInputConfigsStep, InputValidatorStep, ProcessorFunctionStep, BuildStep {
        private String name;
        private String humanReadableNameTemplate;
        private String searchTagsCsvTemplate;
        private Class<? extends ILyshraOpenAppErrorInfo> errorCodeEnum;
        private Class<? extends ILyshraOpenAppProcessorInputConfig> inputConfigType;
        private List<ILyshraOpenAppProcessorInputConfig> sampleInputConfigs;
        private ILyshraOpenAppProcessorInputConfigValidator inputValidator;
        private ILyshraOpenAppProcessorFunction processorFunction;

        @Override
        public HumanReadableNameStep name(String name) { this.name = name; return this; }
        
        @Override
        public SearchTagsStep humanReadableNameTemplate(String template) { this.humanReadableNameTemplate = template; return this; }
        
        @Override
        public ErrorCodeEnumStep searchTagsCsvTemplate(String template) { this.searchTagsCsvTemplate = template; return this; }
        @Override
        public InputConfigTypeStep errorCodeEnum(Class<? extends ILyshraOpenAppErrorInfo> e) { this.errorCodeEnum = e; return this; }
        @Override
        public SampleInputConfigsStep inputConfigType(Class<? extends ILyshraOpenAppProcessorInputConfig> i) { this.inputConfigType = i; return this; }
        @Override
        public InputValidatorStep sampleInputConfigs(List<ILyshraOpenAppProcessorInputConfig> s) { this.sampleInputConfigs = s; return this; }
        @Override
        public ProcessorFunctionStep validateInputConfig(ILyshraOpenAppProcessorInputConfigValidator v) { this.inputValidator = v; return this; }
        @Override
        public BuildStep process(ILyshraOpenAppProcessorFunction f) { this.processorFunction = f; return this; }
        @Override
        public LyshraOpenAppProcessorDefinition build() {
            return new LyshraOpenAppProcessorDefinition(
                    name,
                    humanReadableNameTemplate,
                    searchTagsCsvTemplate,
                    errorCodeEnum,
                    inputConfigType,
                    Collections.unmodifiableList(sampleInputConfigs),
                    inputValidator,
                    processorFunction
            );
        }
    }
}
