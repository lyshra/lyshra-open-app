package com.lyshra.open.app.core.processors.plugin.processors.list;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import com.lyshra.open.app.integration.models.commons.LyshraOpenAppExpression;
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

import static com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType.GRAAALVM_JS;

public class ListFilterProcessor {

    public static final String LIST_FILTER_JS_SNIPPET = """
                var listToFilter = $data || [];
                var filtered = listToFilter.filter((item, index) => {
                    var $item = item;
                    var $index = index;
                    result = %s;
                    return result === true;
               });
                result = filtered;
            """;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListFilterProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {
        @NotBlank(message = "{list.filter.processor.input.expression.null}")
        @Size(min = 3, max = 999, message = "{list.filter.processor.input.expression.invalid.length}")
        private String expression;
    }

    @AllArgsConstructor
    @Getter
    public enum ListFilterProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        ;

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("LIST_FILTER_PROCESSOR")
                .humanReadableNameTemplate("list.filter.processor.name")
                .searchTagsCsvTemplate("list.filter.processor.search.tags")
                .errorCodeEnum(ListFilterProcessorErrorCodes.class)
                .inputConfigType(ListFilterProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new ListFilterProcessorInputConfig("$item > 5")
                ))
                .validateInputConfig(input -> {})
                .process((input, context, facade) -> {
                    ListFilterProcessorInputConfig inputConfig = (ListFilterProcessorInputConfig) input;
                    String expression = inputConfig.getExpression();
                    String filterExpression = String.format(LIST_FILTER_JS_SNIPPET, expression);

                    ILyshraOpenAppExpression expressionConfig = new LyshraOpenAppExpression(GRAAALVM_JS, filterExpression);
                    Object result = facade.getExpressionExecutor().evaluate(expressionConfig, context, facade);
                    return Mono.just(LyshraOpenAppProcessorOutput.ofData(result));
                });
    }
}
