package com.lyshra.open.app.core.processors.plugin.processors;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import com.lyshra.open.app.integration.models.commons.LyshraOpenAppExpression;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutputResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.List;

@RequiredArgsConstructor
public class SwitchProcessor {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SwitchProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {
        @NotBlank(message = "{switch.processor.input.expression.null}")
        @Size(min = 3, max = 999, message = "{switch.processor.input.expression.invalid.length}")
        private LyshraOpenAppExpression expression;
    }

    @AllArgsConstructor
    @Getter
    public enum SwitchProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        ;
        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("SWITCH_PROCESSOR")
                .errorCodeEnum(SwitchProcessorErrorCodes.class)
                .inputConfigType(SwitchProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new SwitchProcessorInputConfig(new LyshraOpenAppExpression(LyshraOpenAppExpressionType.SPEL, "$context.field1.subField2.subField3.size()"))
                ))
                .validateInputConfig(input -> {})
                .process((input, context, facade) -> {
                    SwitchProcessorInputConfig switchProcessorInputConfig = (SwitchProcessorInputConfig) input;
                    String value = facade.getExpressionExecutor().evaluateString(switchProcessorInputConfig.getExpression(), context, facade);
                    return Mono.just(new LyshraOpenAppProcessorOutputResult<>(value));
                });
    }

}
