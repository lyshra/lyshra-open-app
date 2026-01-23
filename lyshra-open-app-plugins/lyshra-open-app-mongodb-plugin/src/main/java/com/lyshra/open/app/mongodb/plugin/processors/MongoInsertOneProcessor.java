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
import org.bson.types.ObjectId;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB Insert One Processor.
 * Inserts a single document into a MongoDB collection.
 */
public class MongoInsertOneProcessor {

    /**
     * Input configuration for the MongoDB Insert One Processor.
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoInsertOneInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * The document to insert.
         */
        @NotNull(message = "{mongodb.insertone.processor.input.document.null}")
        private Map<String, Object> document;

        /**
         * Whether to return the inserted _id in the result.
         */
        @Builder.Default
        private Boolean returnInsertedId = true;
    }

    /**
     * Build the processor definition.
     */
    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_INSERT_ONE_PROCESSOR")
                .humanReadableNameTemplate("mongodb.insertone.processor.name")
                .searchTagsCsvTemplate("mongodb.insertone.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoInsertOneInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoInsertOneProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoInsertOneInputConfig config = (MongoInsertOneInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        // Convert map to document
                        Document document = BsonTypeConverter.toDocument(config.getDocument());

                        // Insert the document
                        return Mono.from(collection.insertOne(document))
                                .map(insertResult -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("acknowledged", insertResult.wasAcknowledged());

                                    if (Boolean.TRUE.equals(config.getReturnInsertedId())) {
                                        BsonValue insertedId = insertResult.getInsertedId();
                                        if (insertedId != null) {
                                            result.put("insertedId", extractInsertedId(insertedId));
                                        }
                                    }

                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                })
                                .cast(com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult.class)
                                .onErrorMap(DuplicateKeyException.class, e -> {
                                    String field = extractDuplicateKeyField(e);
                                    return new LyshraOpenAppProcessorRuntimeException(
                                            MongoProcessorErrorCodes.DUPLICATE_KEY_ERROR,
                                            Map.of(
                                                    "field", field,
                                                    "value", extractDuplicateKeyValue(e)
                                            ),
                                            e,
                                            null
                                    );
                                })
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
        MongoInsertOneInputConfig config = (MongoInsertOneInputConfig) input;
        if (config.getDocument() == null || config.getDocument().isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "document")
            );
        }
    }

    private static Object extractInsertedId(BsonValue insertedId) {
        if (insertedId.isObjectId()) {
            return insertedId.asObjectId().getValue().toHexString();
        } else if (insertedId.isString()) {
            return insertedId.asString().getValue();
        } else if (insertedId.isInt32()) {
            return insertedId.asInt32().getValue();
        } else if (insertedId.isInt64()) {
            return insertedId.asInt64().getValue();
        }
        return insertedId.toString();
    }

    private static String extractDuplicateKeyField(DuplicateKeyException e) {
        String message = e.getMessage();
        // Try to extract field name from error message
        int startIdx = message.indexOf("index:");
        if (startIdx != -1) {
            int endIdx = message.indexOf(" ", startIdx + 7);
            if (endIdx != -1) {
                return message.substring(startIdx + 7, endIdx).trim();
            }
        }
        return "_id";
    }

    private static String extractDuplicateKeyValue(DuplicateKeyException e) {
        String message = e.getMessage();
        int startIdx = message.indexOf("dup key:");
        if (startIdx != -1) {
            return message.substring(startIdx + 9).trim();
        }
        return "unknown";
    }

    private static MongoInsertOneInputConfig createSampleConfig() {
        Map<String, Object> document = new HashMap<>();
        document.put("name", "John Doe");
        document.put("email", "john.doe@example.com");
        document.put("age", 30);
        document.put("active", true);

        return MongoInsertOneInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("users")
                .document(document)
                .returnInsertedId(true)
                .build();
    }
}
