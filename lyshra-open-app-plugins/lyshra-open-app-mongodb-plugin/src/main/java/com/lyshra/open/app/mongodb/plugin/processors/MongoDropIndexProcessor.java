package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.reactivestreams.client.MongoCollection;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.bson.Document;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB Drop Index Processor.
 * Drops indexes from collections.
 */
public class MongoDropIndexProcessor {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoDropIndexInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * Index name to drop.
         * Use "*" to drop all indexes (except _id).
         */
        @NotBlank(message = "{mongodb.dropindex.processor.input.indexName.null}")
        private String indexName;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_DROP_INDEX_PROCESSOR")
                .humanReadableNameTemplate("mongodb.dropindex.processor.name")
                .searchTagsCsvTemplate("mongodb.dropindex.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoDropIndexInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoDropIndexProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoDropIndexInputConfig config = (MongoDropIndexInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        Mono<Void> dropMono;
                        if ("*".equals(config.getIndexName())) {
                            dropMono = Mono.from(collection.dropIndexes());
                        } else {
                            dropMono = Mono.from(collection.dropIndex(config.getIndexName()));
                        }

                        return dropMono
                                .then(Mono.fromSupplier(() -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("dropped", true);
                                    result.put("indexName", config.getIndexName());
                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                }))
                                .cast(com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult.class)
                                .onErrorMap(MongoCommandException.class, e -> {
                                    // Error code 27 = IndexNotFound
                                    if (e.getErrorCode() == 27) {
                                        return new LyshraOpenAppProcessorRuntimeException(
                                                MongoProcessorErrorCodes.INDEX_NOT_FOUND,
                                                Map.of("indexName", config.getIndexName()),
                                                e,
                                                null
                                        );
                                    }
                                    return new LyshraOpenAppProcessorRuntimeException(
                                            MongoProcessorErrorCodes.INDEX_DROP_FAILED,
                                            Map.of("message", e.getMessage()),
                                            e,
                                            null
                                    );
                                })
                                .onErrorMap(MongoException.class, e ->
                                        new LyshraOpenAppProcessorRuntimeException(
                                                MongoProcessorErrorCodes.INDEX_DROP_FAILED,
                                                Map.of("message", e.getMessage()),
                                                e,
                                                null
                                        )
                                );

                    } catch (LyshraOpenAppProcessorRuntimeException e) {
                        return Mono.error(e);
                    } catch (Exception e) {
                        return Mono.error(new LyshraOpenAppProcessorRuntimeException(
                                MongoProcessorErrorCodes.INDEX_DROP_FAILED,
                                Map.of("message", e.getMessage()),
                                e,
                                null
                        ));
                    }
                });
    }

    private static void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) {
        MongoDropIndexInputConfig config = (MongoDropIndexInputConfig) input;
        if (config.getIndexName() == null || config.getIndexName().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "indexName")
            );
        }
    }

    private static MongoDropIndexInputConfig createSampleConfig() {
        return MongoDropIndexInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("users")
                .indexName("email_unique_idx")
                .build();
    }
}
