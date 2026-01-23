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
import com.mongodb.client.model.*;
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
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MongoDB Bulk Write Processor.
 * Executes multiple write operations in a single batch for improved performance.
 */
public class MongoBulkWriteProcessor {

    public enum BulkOperationType {
        INSERT_ONE,
        UPDATE_ONE,
        UPDATE_MANY,
        REPLACE_ONE,
        DELETE_ONE,
        DELETE_MANY
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoBulkWriteInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * List of operations to execute.
         * Each operation should specify operationType, filter, document, update, or replacement.
         */
        @NotEmpty(message = "{mongodb.bulkwrite.processor.input.operations.empty}")
        private List<BulkOperation> operations;

        /**
         * Whether to stop on first error (ordered) or continue with remaining operations.
         */
        @Builder.Default
        private Boolean ordered = true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkOperation {
        private BulkOperationType operationType;
        private Map<String, Object> filter;
        private Map<String, Object> document;
        private Map<String, Object> update;
        private Map<String, Object> replacement;
        @Builder.Default
        private Boolean upsert = false;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_BULK_WRITE_PROCESSOR")
                .humanReadableNameTemplate("mongodb.bulkwrite.processor.name")
                .searchTagsCsvTemplate("mongodb.bulkwrite.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoBulkWriteInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoBulkWriteProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoBulkWriteInputConfig config = (MongoBulkWriteInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        // Convert operations to WriteModel list
                        List<WriteModel<Document>> writeModels = config.getOperations().stream()
                                .map(MongoBulkWriteProcessor::toWriteModel)
                                .toList();

                        BulkWriteOptions options = new BulkWriteOptions()
                                .ordered(Boolean.TRUE.equals(config.getOrdered()));

                        return Mono.from(collection.bulkWrite(writeModels, options))
                                .map(bulkWriteResult -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("acknowledged", bulkWriteResult.wasAcknowledged());
                                    result.put("insertedCount", bulkWriteResult.getInsertedCount());
                                    result.put("matchedCount", bulkWriteResult.getMatchedCount());
                                    result.put("modifiedCount", bulkWriteResult.getModifiedCount());
                                    result.put("deletedCount", bulkWriteResult.getDeletedCount());

                                    // Extract upserted IDs
                                    List<Object> upsertedIds = bulkWriteResult.getUpserts().stream()
                                            .map(upsert -> extractBsonValue(upsert.getId()))
                                            .collect(Collectors.toList());
                                    if (!upsertedIds.isEmpty()) {
                                        result.put("upsertedIds", upsertedIds);
                                        result.put("upsertedCount", upsertedIds.size());
                                    }

                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                })
                                .cast(com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult.class)
                                .onErrorMap(MongoBulkWriteException.class, e -> {
                                    int successCount = e.getWriteResult().getInsertedCount() +
                                            e.getWriteResult().getModifiedCount() +
                                            e.getWriteResult().getDeletedCount();
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

    private static WriteModel<Document> toWriteModel(BulkOperation operation) {
        if (operation.getOperationType() == null) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.INVALID_BULK_OPERATION,
                    Map.of("operationType", "null")
            );
        }

        return switch (operation.getOperationType()) {
            case INSERT_ONE -> {
                if (operation.getDocument() == null) {
                    throw new LyshraOpenAppProcessorRuntimeException(
                            MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                            Map.of("field", "document for INSERT_ONE")
                    );
                }
                yield new InsertOneModel<>(BsonTypeConverter.toDocument(operation.getDocument()));
            }
            case UPDATE_ONE -> {
                Bson filter = MongoOperationHelper.toFilter(operation.getFilter());
                Bson update = MongoOperationHelper.toUpdate(operation.getUpdate());
                UpdateOptions options = new UpdateOptions().upsert(Boolean.TRUE.equals(operation.getUpsert()));
                yield new UpdateOneModel<>(filter, update, options);
            }
            case UPDATE_MANY -> {
                Bson filter = MongoOperationHelper.toFilter(operation.getFilter());
                Bson update = MongoOperationHelper.toUpdate(operation.getUpdate());
                UpdateOptions options = new UpdateOptions().upsert(Boolean.TRUE.equals(operation.getUpsert()));
                yield new UpdateManyModel<>(filter, update, options);
            }
            case REPLACE_ONE -> {
                Bson filter = MongoOperationHelper.toFilter(operation.getFilter());
                Document replacement = BsonTypeConverter.toDocument(operation.getReplacement());
                ReplaceOptions options = new ReplaceOptions().upsert(Boolean.TRUE.equals(operation.getUpsert()));
                yield new ReplaceOneModel<>(filter, replacement, options);
            }
            case DELETE_ONE -> {
                Bson filter = MongoOperationHelper.toFilter(operation.getFilter());
                yield new DeleteOneModel<>(filter);
            }
            case DELETE_MANY -> {
                Bson filter = MongoOperationHelper.toFilter(operation.getFilter());
                yield new DeleteManyModel<>(filter);
            }
        };
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

    private static void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) {
        MongoBulkWriteInputConfig config = (MongoBulkWriteInputConfig) input;

        if (config.getOperations() == null || config.getOperations().isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "operations")
            );
        }

        for (int i = 0; i < config.getOperations().size(); i++) {
            BulkOperation op = config.getOperations().get(i);
            if (op.getOperationType() == null) {
                throw new LyshraOpenAppProcessorRuntimeException(
                        MongoProcessorErrorCodes.INVALID_BULK_OPERATION,
                        Map.of("operationType", "operation " + i + " has no operationType")
                );
            }
        }
    }

    private static MongoBulkWriteInputConfig createSampleConfig() {
        List<BulkOperation> operations = new ArrayList<>();

        operations.add(BulkOperation.builder()
                .operationType(BulkOperationType.INSERT_ONE)
                .document(Map.of("name", "New User", "email", "new@example.com"))
                .build());

        operations.add(BulkOperation.builder()
                .operationType(BulkOperationType.UPDATE_ONE)
                .filter(Map.of("email", "existing@example.com"))
                .update(Map.of("$set", Map.of("status", "active")))
                .build());

        operations.add(BulkOperation.builder()
                .operationType(BulkOperationType.DELETE_ONE)
                .filter(Map.of("status", "deleted"))
                .build());

        return MongoBulkWriteInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("users")
                .operations(operations)
                .ordered(true)
                .build();
    }
}
