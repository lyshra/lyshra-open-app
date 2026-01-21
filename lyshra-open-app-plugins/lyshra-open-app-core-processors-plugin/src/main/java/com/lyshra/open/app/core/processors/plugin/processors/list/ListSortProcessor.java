package com.lyshra.open.app.core.processors.plugin.processors.list;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.commons.LyshraOpenAppExpression;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType.GRAAALVM_JS;

public class ListSortProcessor {

    public static final String LIST_SORT_JS_SNIPPET = """
            var listToSort = $data || [];
            var sortKey = '%s';
            var sortOrder = '%s';
            var sorted = listToSort.slice().sort((a, b) => {
                var valA = sortKey ? a[sortKey] : a;
                var valB = sortKey ? b[sortKey] : b;
                var comparison = 0;
                if (valA === null || valA === undefined) comparison = 1;
                else if (valB === null || valB === undefined) comparison = -1;
                else if (typeof valA === 'string' && typeof valB === 'string') {
                    comparison = valA.localeCompare(valB);
                } else if (typeof valA === 'number' && typeof valB === 'number') {
                    comparison = valA - valB;
                } else if (typeof valA === 'boolean' && typeof valB === 'boolean') {
                    comparison = (valA === valB) ? 0 : (valA ? 1 : -1);
                } else {
                    comparison = String(valA).localeCompare(String(valB));
                }
                return sortOrder === 'desc' ? -comparison : comparison;
            });
            result = sorted;
            """;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListSortProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {
        private String sortKey;

        @Pattern(regexp = "^(asc|desc)$", message = "{list.sort.processor.input.order.invalid}")
        private String order;
    }

    @AllArgsConstructor
    @Getter
    public enum ListSortProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        SORT_KEY_CANNOT_BE_BLANK("LIST_SORT_001", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "list.sort.processor.input.sort.key.blank",
                "list.sort.processor.input.sort.key.blank.resolution");

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("LIST_SORT_PROCESSOR")
                .humanReadableNameTemplate("list.sort.processor.name")
                .searchTagsCsvTemplate("list.sort.processor.search.tags")
                .errorCodeEnum(ListSortProcessorErrorCodes.class)
                .inputConfigType(ListSortProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new ListSortProcessorInputConfig(null, "asc"),
                        new ListSortProcessorInputConfig("name", "asc"),
                        new ListSortProcessorInputConfig("price", "desc")
                ))
                .validateInputConfig(input -> {
                    ListSortProcessorInputConfig config = (ListSortProcessorInputConfig) input;
                    if (config.getSortKey() != null && config.getSortKey().isBlank()) {
                        throw new LyshraOpenAppProcessorRuntimeException(ListSortProcessorErrorCodes.SORT_KEY_CANNOT_BE_BLANK);
                    }
                })
                .process((input, context, facade) -> {
                    ListSortProcessorInputConfig inputConfig = (ListSortProcessorInputConfig) input;
                    String sortKey = inputConfig.getSortKey() != null ? inputConfig.getSortKey() : "";
                    String order = inputConfig.getOrder() != null ? inputConfig.getOrder() : "asc";
                    String sortExpression = String.format(LIST_SORT_JS_SNIPPET, sortKey, order);

                    ILyshraOpenAppExpression expressionConfig = new LyshraOpenAppExpression(GRAAALVM_JS, sortExpression);
                    Object result = facade.getExpressionExecutor().evaluate(expressionConfig, context, facade);
                    return Mono.just(LyshraOpenAppProcessorOutput.ofData(result));
                });
    }
}
