package com.lyshra.open.app.core.processors.plugin.processors.list;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.commons.LyshraOpenAppExpression;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType.GRAAALVM_JS;

public class ListSummarizeProcessor {

    public static final String LIST_SUMMARIZE_JS_SNIPPET = """
            var listToSummarize = $data || [];
            var field = '%s';
            var result = {
                count: listToSummarize.length,
                nullCount: 0,
                first: null,
                last: null,
                sum: null,
                avg: null,
                min: null,
                max: null,
                minLength: null,
                maxLength: null,
                trueCount: null,
                falseCount: null,
                type: null
            };

            if (listToSummarize.length === 0) {
                result = result;
            } else {
                // Extract values
                var values = [];
                var nullCount = 0;
                for (var i = 0; i < listToSummarize.length; i++) {
                    var item = listToSummarize[i];
                    var val = field ? (item !== null && item !== undefined ? item[field] : null) : item;
                    if (val === null || val === undefined) {
                        nullCount++;
                    } else {
                        values.push(val);
                    }
                }
                result.nullCount = nullCount;

                if (values.length > 0) {
                    result.first = values[0];
                    result.last = values[values.length - 1];

                    var firstValue = values[0];
                    var valueType = typeof firstValue;
                    result.type = valueType;

                    if (valueType === 'number') {
                        var sum = 0;
                        var min = values[0];
                        var max = values[0];
                        for (var i = 0; i < values.length; i++) {
                            var v = values[i];
                            sum += v;
                            if (v < min) min = v;
                            if (v > max) max = v;
                        }
                        result.sum = sum;
                        result.min = min;
                        result.max = max;
                        result.avg = sum / values.length;
                    } else if (valueType === 'string') {
                        var minStr = values[0];
                        var maxStr = values[0];
                        var minLen = values[0].length;
                        var maxLen = values[0].length;
                        for (var i = 1; i < values.length; i++) {
                            var v = values[i];
                            if (v.localeCompare(minStr) < 0) minStr = v;
                            if (v.localeCompare(maxStr) > 0) maxStr = v;
                            if (v.length < minLen) minLen = v.length;
                            if (v.length > maxLen) maxLen = v.length;
                        }
                        result.min = minStr;
                        result.max = maxStr;
                        result.minLength = minLen;
                        result.maxLength = maxLen;
                    } else if (valueType === 'boolean') {
                        var trueCount = 0;
                        var falseCount = 0;
                        for (var i = 0; i < values.length; i++) {
                            if (values[i]) trueCount++;
                            else falseCount++;
                        }
                        result.trueCount = trueCount;
                        result.falseCount = falseCount;
                    }
                }
            }
            result = result;
            """;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListSummarizeProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {
        private String field;
    }

    @AllArgsConstructor
    @Getter
    public enum ListSummarizeProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        FIELD_CANNOT_BE_BLANK("LIST_SUMMARIZE_001", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "list.summarize.processor.input.field.blank",
                "list.summarize.processor.input.field.blank.resolution");

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("LIST_SUMMARIZE_PROCESSOR")
                .humanReadableNameTemplate("list.summarize.processor.name")
                .searchTagsCsvTemplate("list.summarize.processor.search.tags")
                .errorCodeEnum(ListSummarizeProcessorErrorCodes.class)
                .inputConfigType(ListSummarizeProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new ListSummarizeProcessorInputConfig(null),
                        new ListSummarizeProcessorInputConfig("price"),
                        new ListSummarizeProcessorInputConfig("name"),
                        new ListSummarizeProcessorInputConfig("active")
                ))
                .validateInputConfig(input -> {
                    ListSummarizeProcessorInputConfig config = (ListSummarizeProcessorInputConfig) input;
                    if (config.getField() != null && config.getField().isBlank()) {
                        throw new LyshraOpenAppProcessorRuntimeException(ListSummarizeProcessorErrorCodes.FIELD_CANNOT_BE_BLANK);
                    }
                })
                .process((input, context, facade) -> {
                    ListSummarizeProcessorInputConfig inputConfig = (ListSummarizeProcessorInputConfig) input;
                    String field = inputConfig.getField() != null ? inputConfig.getField() : "";
                    String summarizeExpression = String.format(LIST_SUMMARIZE_JS_SNIPPET, field);

                    ILyshraOpenAppExpression expressionConfig = new LyshraOpenAppExpression(GRAAALVM_JS, summarizeExpression);
                    Object result = facade.getExpressionExecutor().evaluate(expressionConfig, context, facade);
                    return Mono.just(LyshraOpenAppProcessorOutput.ofData(result));
                });
    }
}
