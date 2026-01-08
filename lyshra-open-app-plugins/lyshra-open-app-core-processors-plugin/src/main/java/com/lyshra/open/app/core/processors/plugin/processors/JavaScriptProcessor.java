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
import java.util.Map;

public class JavaScriptProcessor {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JavaScriptProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig, ILyshraOpenAppExpression {

        private final LyshraOpenAppExpressionType expressionType = LyshraOpenAppExpressionType.GRAAALVM_JS;

        @NotBlank(message = "{js.processor.input.expression.null}")
        @Size(min = 3, max = 999, message = "{js.processor.input.expression.invalid.length}")
        private String expression;
    }

    @AllArgsConstructor
    @Getter
    public enum JavaScriptProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        ;

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("JS_PROCESSOR")
                .humanReadableNameTemplate("js.processor.name")
                .searchTagsCsvTemplate("js.processor.search.tags")
                .errorCodeEnum(JavaScriptProcessorErrorCodes.class)
                .inputConfigType(JavaScriptProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new JavaScriptProcessorInputConfig("$data and $variables are available to operate on")
                ))
                .validateInputConfig(input -> {})
                .process((input, context, facade) -> {
                    JavaScriptProcessorInputConfig javaScriptProcessorInputConfig = (JavaScriptProcessorInputConfig) input;
                    String expression = String.format("%s;const result = {'$data': $data, '$variables': $variables}", javaScriptProcessorInputConfig.getExpression());
                    javaScriptProcessorInputConfig.setExpression(expression);
                    Object result = facade.getExpressionExecutor().evaluate(javaScriptProcessorInputConfig, context, facade);

                    Map value = (Map) result;

                    // Set the variables
                    context.setVariables((Map) value.get("$variables"));

                    // Return the data
                    Map data = (Map) value.get("$data");
                    return Mono.just(LyshraOpenAppProcessorOutput.ofData(data));
                });
    }

}
