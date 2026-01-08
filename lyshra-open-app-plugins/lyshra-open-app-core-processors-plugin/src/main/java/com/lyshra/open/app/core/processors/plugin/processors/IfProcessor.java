package com.lyshra.open.app.core.processors.plugin.processors;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
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

        private final LyshraOpenAppExpressionType expressionType = LyshraOpenAppExpressionType.GRAAALVM_JS;

        @NotBlank(message = "{if.processor.input.expression.null}")
        @Size(min = 3, max = 999, message = "{if.processor.input.expression.invalid.length}")
        private String expression;
    }

    @AllArgsConstructor
    @Getter
    public enum IfProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
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
                .humanReadableNameTemplate("if.processor.name")
                .searchTagsCsvTemplate("if.processor.search.tags")
                .errorCodeEnum(IfProcessorErrorCodes.class)
                .inputConfigType(IfProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new IfProcessorInputConfig("result = $data.field1.subField2.subField3.size() == 4")
                ))
                .validateInputConfig(input -> {})
                .process((input, context, facade) -> {
                    IfProcessorInputConfig ifProcessorInputConfig = (IfProcessorInputConfig) input;
                    Boolean value = facade.getExpressionExecutor().evaluateBoolean(ifProcessorInputConfig, context, facade);
                    return Mono.just(LyshraOpenAppProcessorOutput.ofBranch(value));
                });
    }

}
