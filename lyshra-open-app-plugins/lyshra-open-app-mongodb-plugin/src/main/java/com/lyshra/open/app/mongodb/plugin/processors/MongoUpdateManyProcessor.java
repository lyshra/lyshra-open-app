package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.converter.BsonTypeConverter;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB Update Many Processor.
 * Updates all documents matching the filter criteria.
 */
public class MongoUpdateManyProcessor {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoUpdateManyInputConfig extends BaseMongoProcessorInputConfig {

        @NotNull(message = "{mongodb.updatemany.processor.input.filter.null}")
        private Map<String, Object> filter;

        @NotNull(message = "{mongodb.updatemany.processor.input.update.null}")
        private Map<String, Object> update;

        @Builder.Default
        private Boolean upsert = false;

        private List<Map<String, Object>> arrayFilters;

        private Object hint;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_UPDATE_MANY_PROCESSOR")
                .humanReadableNameTemplate("mongodb.updatemany.processor.name")
                .searchTagsCsvTemplate("mongodb.updatemany.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoUpdateManyInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoUpdateManyProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoUpdateManyInputConfig config = (MongoUpdateManyInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        Bson filter = MongoOperationHelper.toFilter(config.getFilter());
                        Bson update = MongoOperationHelper.toUpdate(config.getUpdate());

                        UpdateOptions options = new UpdateOptions()
                                .upsert(Boolean.TRUE.equals(config.getUpsert()));

                        if (config.getArrayFilters() != null && !config.getArrayFilters().isEmpty()) {
                            List<Bson> arrayFilters = config.getArrayFilters().stream()
                                    .map(BsonTypeConverter::toDocument)
                                    .map(doc -> (Bson) doc)
                                    .toList();
                            options.arrayFilters(arrayFilters);
                        }

                        Bson hint = MongoOperationHelper.toHint(config.getHint());
                        if (hint != null) {
                            options.hint(hint);
                        }

                        return Mono.from(collection.updateMany(filter, update, options))
                                .map(updateResult -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("acknowledged", updateResult.wasAcknowledged());
                                    result.put("matchedCount", updateResult.getMatchedCount());
                                    result.put("modifiedCount", updateResult.getModifiedCount());

                                    BsonValue upsertedId = updateResult.getUpsertedId();
                                    if (upsertedId != null) {
                                        result.put("upsertedId", extractBsonValue(upsertedId));
                                    }

                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                })
                                .cast(com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult.class)
                                .onErrorMap(MongoWriteException.class, e ->
                                        new LyshraOpenAppProcessorRuntimeException(
                                                MongoProcessorErrorCodes.WRITE_FAILED,
                                                Map.of("message", e.getMessage()),
                                                e,
                                                null
                                        )
                                )
                                .onErrorMap(MongoException.class, e ->
                                        new LyshraOpenAppProcessorRuntimeException(
                                                MongoProcessorErrorCodes.WRITE_FAILED,
                                                Map.of("message", e.getMessage()),
                                                e,
                                                null
                                        )
                                );

                    } catch (LyshraOpenAppProcessorRuntimeException e) {
                        return Mono.error(e);
                    } catch (Exception e) {
                        return Mono.error(new LyshraOpenAppProcessorRuntimeException(
                                MongoProcessorErrorCodes.WRITE_FAILED,
                                Map.of("message", e.getMessage()),
                                e,
                                null
                        ));
                    }
                });
    }

    private static void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) {
        MongoUpdateManyInputConfig config = (MongoUpdateManyInputConfig) input;

        if (config.getFilter() == null) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "filter")
            );
        }

        if (config.getUpdate() == null || config.getUpdate().isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "update")
            );
        }

        boolean hasUpdateOperators = config.getUpdate().keySet().stream()
                .anyMatch(key -> key.startsWith("$"));
        if (!hasUpdateOperators) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_UPDATE_OPERATION,
                    Map.of("message", "Update must use update operators ($set, $inc, etc.)")
            );
        }
    }

    private static Object extractBsonValue(BsonValue value) {
        if (value.isObjectId()) {
            return value.asObjectId().getValue().toHexString();
        } else if (value.isString()) {
            return value.asString().getValue();
        } else if (value.isInt32()) {
            return value.asInt32().getValue();
        } else if (value.isInt64()) {
            return value.asInt64().getValue();
        }
        return value.toString();
    }

    private static MongoUpdateManyInputConfig createSampleConfig() {
        return MongoUpdateManyInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("orders")
                .filter(Map.of("status", "pending"))
                .update(Map.of(
                        "$set", Map.of("status", "processing"),
                        "$currentDate", Map.of("updatedAt", true)
                ))
                .upsert(false)
                .build();
    }
}
