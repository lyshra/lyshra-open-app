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
    private final Class<? extends ILyshraOpenAppErrorInfo> errorCodeEnum;
    private final Class<? extends ILyshraOpenAppProcessorInputConfig> inputConfigType;
    private final List<ILyshraOpenAppProcessorInputConfig> sampleInputConfigs;
    private final ILyshraOpenAppProcessorInputConfigValidator inputConfigValidator;
    private final ILyshraOpenAppProcessorFunction processorFunction;

    // Guided builder
    public static InitialStepBuilder builder() { return new Builder(); }

    public interface InitialStepBuilder { ErrorCodeEnumStep name(String name); }
    public interface ErrorCodeEnumStep { InputConfigTypeStep errorCodeEnum(Class<? extends ILyshraOpenAppErrorInfo> e); }
    public interface InputConfigTypeStep { SampleInputConfigsStep inputConfigType(Class<? extends ILyshraOpenAppProcessorInputConfig> i); }
    public interface SampleInputConfigsStep { InputValidatorStep sampleInputConfigs(List<ILyshraOpenAppProcessorInputConfig> s); }
    public interface InputValidatorStep { ProcessorFunctionStep validateInputConfig(ILyshraOpenAppProcessorInputConfigValidator v); }
    public interface ProcessorFunctionStep { BuildStep process(ILyshraOpenAppProcessorFunction f); }
    public interface BuildStep { LyshraOpenAppProcessorDefinition build(); }

    private static class Builder implements InitialStepBuilder, ErrorCodeEnumStep, InputConfigTypeStep, SampleInputConfigsStep, InputValidatorStep, ProcessorFunctionStep, BuildStep {
        private String name;
        private Class<? extends ILyshraOpenAppErrorInfo> errorCodeEnum;
        private Class<? extends ILyshraOpenAppProcessorInputConfig> inputConfigType;
        private List<ILyshraOpenAppProcessorInputConfig> sampleInputConfigs;
        private ILyshraOpenAppProcessorInputConfigValidator inputValidator;
        private ILyshraOpenAppProcessorFunction processorFunction;

        @Override
        public ErrorCodeEnumStep name(String name) { this.name = name; return this; }
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
                    errorCodeEnum,
                    inputConfigType,
                    Collections.unmodifiableList(sampleInputConfigs),
                    inputValidator,
                    processorFunction
            );
        }
    }
}
