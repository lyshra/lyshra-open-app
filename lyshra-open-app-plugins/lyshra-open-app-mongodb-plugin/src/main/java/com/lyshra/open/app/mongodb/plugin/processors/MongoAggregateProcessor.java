package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.converter.BsonTypeConverter;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MongoDB Aggregate Processor.
 * Executes aggregation pipelines on MongoDB collections.
 * Supports all MongoDB aggregation stages ($match, $group, $project, $sort, $lookup, etc.)
 */
public class MongoAggregateProcessor {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoAggregateInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * The aggregation pipeline stages.
         * Each stage is a map representing an aggregation operation.
         */
        @NotEmpty(message = "{mongodb.aggregate.processor.input.pipeline.empty}")
        private List<Map<String, Object>> pipeline;

        /**
         * Allow disk use for large aggregations that exceed memory limit.
         */
        private Boolean allowDiskUse;

        /**
         * Batch size for cursor retrieval.
         */
        @Min(value = 1, message = "{mongodb.aggregate.processor.input.batchSize.invalid}")
        private Integer batchSize;

        /**
         * Maximum time (in milliseconds) for aggregation execution.
         */
        @Min(value = 1, message = "{mongodb.aggregate.processor.input.maxTimeMs.invalid}")
        private Integer maxTimeMs;

        /**
         * Index hint for query optimization.
         */
        private Object hint;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_AGGREGATE_PROCESSOR")
                .humanReadableNameTemplate("mongodb.aggregate.processor.name")
                .searchTagsCsvTemplate("mongodb.aggregate.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoAggregateInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoAggregateProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoAggregateInputConfig config = (MongoAggregateInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        // Convert pipeline stages to BSON
                        List<Bson> pipelineStages = config.getPipeline().stream()
                                .map(BsonTypeConverter::toDocument)
                                .collect(Collectors.toList());

                        // Create aggregate publisher
                        AggregatePublisher<Document> aggregatePublisher = collection.aggregate(pipelineStages);

                        // Apply options
                        if (Boolean.TRUE.equals(config.getAllowDiskUse())) {
                            aggregatePublisher = aggregatePublisher.allowDiskUse(true);
                        }

                        if (config.getBatchSize() != null && config.getBatchSize() > 0) {
                            aggregatePublisher = aggregatePublisher.batchSize(config.getBatchSize());
                        }

                        if (config.getMaxTimeMs() != null && config.getMaxTimeMs() > 0) {
                            aggregatePublisher = aggregatePublisher.maxTime(config.getMaxTimeMs(), TimeUnit.MILLISECONDS);
                        }

                        Bson hint = MongoOperationHelper.toHint(config.getHint());
                        if (hint != null) {
                            aggregatePublisher = aggregatePublisher.hint(hint);
                        }

                        // Execute and collect results
                        return Flux.from(aggregatePublisher)
                                .map(BsonTypeConverter::fromDocument)
                                .collectList()
                                .map(results -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("results", results);
                                    result.put("count", results.size());
                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                })
                                .cast(com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult.class)
                                .onErrorMap(MongoExecutionTimeoutException.class, e ->
                                        new LyshraOpenAppProcessorRuntimeException(
                                                MongoProcessorErrorCodes.AGGREGATION_TIMEOUT,
                                                Map.of("maxTimeMs", String.valueOf(config.getMaxTimeMs())),
                                                e,
                                                null
                                        )
                                )
                                .onErrorMap(MongoException.class, e ->
                                        new LyshraOpenAppProcessorRuntimeException(
                                                MongoProcessorErrorCodes.AGGREGATION_EXECUTION_FAILED,
                                                Map.of("message", e.getMessage()),
                                                e,
                                                null
                                        )
                                );

                    } catch (LyshraOpenAppProcessorRuntimeException e) {
                        return Mono.error(e);
                    } catch (Exception e) {
                        return Mono.error(new LyshraOpenAppProcessorRuntimeException(
                                MongoProcessorErrorCodes.AGGREGATION_EXECUTION_FAILED,
                                Map.of("message", e.getMessage()),
                                e,
                                null
                        ));
                    }
                });
    }

    private static void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) {
        MongoAggregateInputConfig config = (MongoAggregateInputConfig) input;

        if (config.getPipeline() == null || config.getPipeline().isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "pipeline")
            );
        }

        // Validate each stage has exactly one operator
        for (int i = 0; i < config.getPipeline().size(); i++) {
            Map<String, Object> stage = config.getPipeline().get(i);
            if (stage.isEmpty()) {
                throw new LyshraOpenAppProcessorRuntimeException(
                        MongoProcessorErrorCodes.INVALID_AGGREGATION_STAGE,
                        Map.of("stage", "stage " + i + " is empty")
                );
            }

            // Each stage should have one key that is an aggregation operator
            boolean hasValidOperator = stage.keySet().stream()
                    .anyMatch(key -> key.startsWith("$"));
            if (!hasValidOperator) {
                throw new LyshraOpenAppProcessorRuntimeException(
                        MongoProcessorErrorCodes.INVALID_AGGREGATION_STAGE,
                        Map.of("stage", "stage " + i + " must use an aggregation operator ($match, $group, etc.)")
                );
            }
        }
    }

    private static MongoAggregateInputConfig createSampleConfig() {
        return MongoAggregateInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("orders")
                .pipeline(List.of(
                        Map.of("$match", Map.of("status", "completed")),
                        Map.of("$group", Map.of(
                                "_id", "$region",
                                "totalSales", Map.of("$sum", "$amount"),
                                "orderCount", Map.of("$sum", 1)
                        )),
                        Map.of("$sort", Map.of("totalSales", -1)),
                        Map.of("$limit", 10)
                ))
                .allowDiskUse(true)
                .maxTimeMs(60000)
                .build();
    }
}
