package com.lyshra.open.app.core.processors.plugin.processors;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutputResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.List;

public class IfProcessor {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IfProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig, ILyshraOpenAppExpression {
        @NotBlank(message = "{if.processor.input.expression.null}")
        @Size(min = 3, max = 999, message = "{if.processor.input.expression.invalid.length}")
        private String expression;
    }

    @AllArgsConstructor
    @Getter
    public enum IfProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        IF_PROCESSOR_INPUT_IS_NULL("ERR001", LyshraOpenAppHttpStatus.BAD_REQUEST, "IF_PROCESSOR_INPUT_IS_NULL", "PROVIDE_IF_PROCESSOR_INPUT"),
        IF_PROCESSOR_INPUT_EXPRESSION_CONFIG_IS_NULL("ERR002", LyshraOpenAppHttpStatus.BAD_REQUEST, "IF_PROCESSOR_INPUT_EXPRESSION_CONFIG_IS_NULL", "PROVIDE_IF_PROCESSOR_INPUT_EXPRESSION_CONFIG"),
        IF_PROCESSOR_INPUT_EXPRESSION_TYPE_NULL("ERR003", LyshraOpenAppHttpStatus.BAD_REQUEST, "IF_PROCESSOR_INPUT_EXPRESSION_TYPE_NULL", "PROVIDE_IF_PROCESSOR_INPUT_EXPRESSION_TYPE"),
        IF_PROCESSOR_INPUT_EXPRESSION_IS_NULL("ERRO04", LyshraOpenAppHttpStatus.BAD_REQUEST, "IF_PROCESSOR_INPUT_EXPRESSION_IS_NULL", "PROVIDE_IF_PROCESSOR_INPUT_EXPRESSION"),
        ;

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("IF_PROCESSOR")
                .errorCodeEnum(IfProcessorErrorCodes.class)
                .inputConfigType(IfProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new IfProcessorInputConfig("$context.field1.subField2.subField3.size() == 4")
                ))
                .validateInputConfig(input -> {
                    IfProcessorInputConfig ifProcessorInputConfig = (IfProcessorInputConfig) input;
                    if (ifProcessorInputConfig == null) {
                        throw new LyshraOpenAppProcessorRuntimeException(IfProcessorErrorCodes.IF_PROCESSOR_INPUT_IS_NULL);
                    } else if (ifProcessorInputConfig.getExpression() == null) {
                        throw new LyshraOpenAppProcessorRuntimeException(IfProcessorErrorCodes.IF_PROCESSOR_INPUT_EXPRESSION_CONFIG_IS_NULL);
                    }
                })
                .process((input, context, facade) -> {
                    IfProcessorInputConfig ifProcessorInputConfig = (IfProcessorInputConfig) input;
                    Boolean value = facade.getExpressionExecutor().evaluateBoolean(ifProcessorInputConfig, context, facade);
                    return Mono.just(new LyshraOpenAppProcessorOutputResult<>(value.toString()));
                });
    }

}
