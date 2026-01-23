package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.converter.BsonTypeConverter;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.DuplicateKeyException;
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
 * MongoDB Update One Processor.
 * Updates a single document matching the filter criteria.
 * Supports all MongoDB update operators ($set, $inc, $push, $pull, etc.)
 */
public class MongoUpdateOneProcessor {

    /**
     * Input configuration for the MongoDB Update One Processor.
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoUpdateOneInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * Query filter to select the document to update.
         */
        @NotNull(message = "{mongodb.updateone.processor.input.filter.null}")
        private Map<String, Object> filter;

        /**
         * Update operations to apply.
         * Must use MongoDB update operators like $set, $inc, $push, etc.
         */
        @NotNull(message = "{mongodb.updateone.processor.input.update.null}")
        private Map<String, Object> update;

        /**
         * Whether to insert a new document if no document matches the filter.
         */
        @Builder.Default
        private Boolean upsert = false;

        /**
         * Array filters for updating specific array elements.
         */
        private List<Map<String, Object>> arrayFilters;

        /**
         * Index hint for query optimization.
         */
        private Object hint;
    }

    /**
     * Build the processor definition.
     */
    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_UPDATE_ONE_PROCESSOR")
                .humanReadableNameTemplate("mongodb.updateone.processor.name")
                .searchTagsCsvTemplate("mongodb.updateone.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoUpdateOneInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoUpdateOneProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoUpdateOneInputConfig config = (MongoUpdateOneInputConfig) input;

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

                        // Configure update options
                        UpdateOptions options = new UpdateOptions()
                                .upsert(Boolean.TRUE.equals(config.getUpsert()));

                        // Add array filters if provided
                        if (config.getArrayFilters() != null && !config.getArrayFilters().isEmpty()) {
                            List<Bson> arrayFilters = config.getArrayFilters().stream()
                                    .map(BsonTypeConverter::toDocument)
                                    .map(doc -> (Bson) doc)
                                    .toList();
                            options.arrayFilters(arrayFilters);
                        }

                        // Add hint if provided
                        Bson hint = MongoOperationHelper.toHint(config.getHint());
                        if (hint != null) {
                            options.hint(hint);
                        }

                        return Mono.from(collection.updateOne(filter, update, options))
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
                                .onErrorMap(DuplicateKeyException.class, e ->
                                        new LyshraOpenAppProcessorRuntimeException(
                                                MongoProcessorErrorCodes.DUPLICATE_KEY_ERROR,
                                                Map.of("field", "_id", "value", "duplicate"),
                                                e,
                                                null
                                        )
                                )
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
        MongoUpdateOneInputConfig config = (MongoUpdateOneInputConfig) input;

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

        // Validate update uses operators or is a replacement document
        validateUpdateDocument(config.getUpdate());
    }

    private static void validateUpdateDocument(Map<String, Object> update) {
        // Check if it uses update operators
        boolean hasUpdateOperators = update.keySet().stream()
                .anyMatch(key -> key.startsWith("$"));

        // If not using operators, warn (could be replacement for findOneAndReplace)
        if (!hasUpdateOperators) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_UPDATE_OPERATION,
                    Map.of("message", "Update must use update operators ($set, $inc, etc.) for updateOne. " +
                            "Use MONGO_REPLACE_ONE_PROCESSOR for document replacement.")
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

    private static MongoUpdateOneInputConfig createSampleConfig() {
        return MongoUpdateOneInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("users")
                .filter(Map.of("email", "john@example.com"))
                .update(Map.of(
                        "$set", Map.of("name", "John Updated", "updatedAt", Map.of("$date", System.currentTimeMillis())),
                        "$inc", Map.of("loginCount", 1)
                ))
                .upsert(false)
                .build();
    }
}
