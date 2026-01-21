package com.lyshra.open.app.core.processors.plugin.processors.list;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import com.lyshra.open.app.integration.models.commons.LyshraOpenAppExpression;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import lombok.AllArgsConstructor;
import lombok.Getter;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType.GRAAALVM_JS;

public class ListRemoveDuplicatesProcessor {

    public static class ListRemoveDuplicatesProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {
    }

    public static final String REMOVE_DUPLICATES_JS_SNIPPET = """
                var listToProcess = $data || [];
                jlog("List to process:", listToProcess);
                var seen = new Set();
                var result = [];
                for (var i = 0; i < listToProcess.length; i++) {
                    var item = listToProcess[i];
                    var itemKey = JSON.stringify(item);
                    jlog("Item key:", item, itemKey);
                    if (!seen.has(itemKey)) {
                        seen.add(itemKey);
                        result.push(item);
                    }
                }
                result = result;
            """;

    @AllArgsConstructor
    @Getter
    public enum ListRemoveDuplicatesProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        ;

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("LIST_REMOVE_DUPLICATES_PROCESSOR")
                .humanReadableNameTemplate("list.remove.duplicates.processor.name")
                .searchTagsCsvTemplate("list.remove.duplicates.processor.search.tags")
                .errorCodeEnum(ListRemoveDuplicatesProcessorErrorCodes.class)
                .inputConfigType(ListRemoveDuplicatesProcessorInputConfig.class)
                .sampleInputConfigs(List.of(new ListRemoveDuplicatesProcessorInputConfig()))
                .validateInputConfig(input -> {})
                .process((input, context, facade) -> {
                    ILyshraOpenAppExpression expressionConfig = new LyshraOpenAppExpression(GRAAALVM_JS, REMOVE_DUPLICATES_JS_SNIPPET);
                    Object result = facade.getExpressionExecutor().evaluate(expressionConfig, context, facade);
                    return Mono.just(LyshraOpenAppProcessorOutput.ofData(result));
                });
    }
}
