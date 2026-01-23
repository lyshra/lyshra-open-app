package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.converter.BsonTypeConverter;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoException;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.bson.BsonValue;
import org.bson.Document;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MongoDB Insert Many Processor.
 * Inserts multiple documents into a MongoDB collection in a single batch operation.
 */
public class MongoInsertManyProcessor {

    /**
     * Input configuration for the MongoDB Insert Many Processor.
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoInsertManyInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * The list of documents to insert.
         */
        @NotEmpty(message = "{mongodb.insertmany.processor.input.documents.empty}")
        private List<Map<String, Object>> documents;

        /**
         * Whether to stop on first error (ordered) or continue with remaining docs (unordered).
         * Default: true (ordered)
         */
        @Builder.Default
        private Boolean ordered = true;

        /**
         * Whether to return the inserted _ids in the result.
         */
        @Builder.Default
        private Boolean returnInsertedIds = true;
    }

    /**
     * Build the processor definition.
     */
    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_INSERT_MANY_PROCESSOR")
                .humanReadableNameTemplate("mongodb.insertmany.processor.name")
                .searchTagsCsvTemplate("mongodb.insertmany.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoInsertManyInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoInsertManyProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoInsertManyInputConfig config = (MongoInsertManyInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        // Convert maps to documents
                        List<Document> documents = BsonTypeConverter.toDocuments(config.getDocuments());

                        // Configure insert options
                        InsertManyOptions options = new InsertManyOptions()
                                .ordered(Boolean.TRUE.equals(config.getOrdered()));

                        // Insert documents
                        return Mono.from(collection.insertMany(documents, options))
                                .map(insertResult -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("acknowledged", insertResult.wasAcknowledged());
                                    result.put("insertedCount", insertResult.getInsertedIds().size());

                                    if (Boolean.TRUE.equals(config.getReturnInsertedIds())) {
                                        List<Object> insertedIds = insertResult.getInsertedIds().values()
                                                .stream()
                                                .map(MongoInsertManyProcessor::extractInsertedId)
                                                .collect(Collectors.toList());
                                        result.put("insertedIds", insertedIds);
                                    }

                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                })
                                .cast(com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult.class)
                                .onErrorMap(MongoBulkWriteException.class, e -> {
                                    int successCount = e.getWriteResult().getInsertedCount();
                                    int failCount = e.getWriteErrors().size();
                                    return new LyshraOpenAppProcessorRuntimeException(
                                            MongoProcessorErrorCodes.BULK_WRITE_FAILED,
                                            Map.of(
                                                    "successCount", String.valueOf(successCount),
                                                    "failCount", String.valueOf(failCount)
                                            ),
                                            e,
                                            null
                                    );
                                })
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
        MongoInsertManyInputConfig config = (MongoInsertManyInputConfig) input;
        if (config.getDocuments() == null || config.getDocuments().isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "documents")
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

    private static MongoInsertManyInputConfig createSampleConfig() {
        List<Map<String, Object>> documents = new ArrayList<>();

        Map<String, Object> doc1 = new HashMap<>();
        doc1.put("name", "Alice");
        doc1.put("email", "alice@example.com");
        documents.add(doc1);

        Map<String, Object> doc2 = new HashMap<>();
        doc2.put("name", "Bob");
        doc2.put("email", "bob@example.com");
        documents.add(doc2);

        return MongoInsertManyInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("users")
                .documents(documents)
                .ordered(true)
                .returnInsertedIds(true)
                .build();
    }
}
