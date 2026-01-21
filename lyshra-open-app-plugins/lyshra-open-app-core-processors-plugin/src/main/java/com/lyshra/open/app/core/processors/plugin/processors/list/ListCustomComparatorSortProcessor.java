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

public class ListCustomComparatorSortProcessor {

    public static final String LIST_CUSTOM_COMPARATOR_SORT_JS_SNIPPET = """
            var listToSort = $data || [];
            var sorted = listToSort.slice().sort((a, b) => {
                var $a = a;
                var $b = b;
                var result = %s;
                return result;
            });
            result = sorted;
            """;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListCustomComparatorSortProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {
        @NotBlank(message = "{list.custom.comparator.sort.processor.input.expression.null}")
        @Size(min = 3, max = 999, message = "{list.custom.comparator.sort.processor.input.expression.invalid.length}")
        private String expression;
    }

    @AllArgsConstructor
    @Getter
    public enum ListCustomComparatorSortProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        ;

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("LIST_CUSTOM_COMPARATOR_SORT_PROCESSOR")
                .humanReadableNameTemplate("list.custom.comparator.sort.processor.name")
                .searchTagsCsvTemplate("list.custom.comparator.sort.processor.search.tags")
                .errorCodeEnum(ListCustomComparatorSortProcessorErrorCodes.class)
                .inputConfigType(ListCustomComparatorSortProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new ListCustomComparatorSortProcessorInputConfig("$a - $b"),
                        new ListCustomComparatorSortProcessorInputConfig("$b - $a"),
                        new ListCustomComparatorSortProcessorInputConfig("$a.name.localeCompare($b.name)"),
                        new ListCustomComparatorSortProcessorInputConfig("$a.age - $b.age || $a.name.localeCompare($b.name)")
                ))
                .validateInputConfig(input -> {})
                .process((input, context, facade) -> {
                    ListCustomComparatorSortProcessorInputConfig inputConfig = (ListCustomComparatorSortProcessorInputConfig) input;
                    String expression = inputConfig.getExpression();
                    String sortExpression = String.format(LIST_CUSTOM_COMPARATOR_SORT_JS_SNIPPET, expression);

                    ILyshraOpenAppExpression expressionConfig = new LyshraOpenAppExpression(GRAAALVM_JS, sortExpression);
                    Object result = facade.getExpressionExecutor().evaluate(expressionConfig, context, facade);
                    return Mono.just(LyshraOpenAppProcessorOutput.ofData(result));
                });
    }
}
