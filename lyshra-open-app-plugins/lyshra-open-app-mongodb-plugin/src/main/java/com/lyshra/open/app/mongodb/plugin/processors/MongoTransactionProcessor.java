package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.connection.MongoClientManager;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.MongoException;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.TransactionBody;
import com.lyshra.open.app.mongodb.plugin.config.MongoConnectionConfig;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB Transaction Processor.
 * Executes multiple operations within a single ACID transaction.
 * Supports MongoDB 4.0+ replica sets and sharded clusters.
 *
 * Note: This processor uses the sync MongoDB driver for transactions
 * as the reactive driver has limited transaction support with callbacks.
 */
public class MongoTransactionProcessor {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoTransactionInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * List of operations to execute within the transaction.
         * Each operation specifies a processor name and its input config.
         */
        @NotEmpty(message = "{mongodb.transaction.processor.input.operations.empty}")
        private List<TransactionOperation> operations;

        /**
         * Maximum time in milliseconds for the transaction to complete.
         */
        @Min(value = 1000, message = "{mongodb.transaction.processor.input.maxCommitTimeMs.invalid}")
        @Builder.Default
        private Integer maxCommitTimeMs = 30000;

        /**
         * Read concern for the transaction.
         */
        @Builder.Default
        private String readConcern = "snapshot";

        /**
         * Write concern for the transaction.
         */
        @Builder.Default
        private String writeConcern = "majority";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionOperation {
        /**
         * The operation type (e.g., INSERT_ONE, UPDATE_ONE, DELETE_ONE, etc.)
         */
        private TransactionOperationType operationType;

        /**
         * Collection name for this operation.
         */
        private String collection;

        /**
         * Filter for update/delete operations.
         */
        private Map<String, Object> filter;

        /**
         * Document for insert operations.
         */
        private Map<String, Object> document;

        /**
         * Update specification for update operations.
         */
        private Map<String, Object> update;

        /**
         * Replacement document for replace operations.
         */
        private Map<String, Object> replacement;

        /**
         * Upsert option for update/replace operations.
         */
        @Builder.Default
        private Boolean upsert = false;
    }

    public enum TransactionOperationType {
        INSERT_ONE,
        UPDATE_ONE,
        UPDATE_MANY,
        REPLACE_ONE,
        DELETE_ONE,
        DELETE_MANY
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_TRANSACTION_PROCESSOR")
                .humanReadableNameTemplate("mongodb.transaction.processor.name")
                .searchTagsCsvTemplate("mongodb.transaction.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoTransactionInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoTransactionProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoTransactionInputConfig config = (MongoTransactionInputConfig) input;

                    return Mono.fromCallable(() -> {
                        try {
                            // Register config from context
                            MongoClientManager manager = MongoClientManager.getInstance();
                            MongoOperationHelper.registerConfigFromContext(
                                    config.getConnectionConfigKey(),
                                    context,
                                    facade,
                                    manager
                            );

                            // Get connection config
                            MongoConnectionConfig connConfig = manager.getConfig(config.getConnectionConfigKey())
                                    .orElseThrow(() -> new LyshraOpenAppProcessorRuntimeException(
                                            MongoProcessorErrorCodes.CONNECTION_CONFIG_NOT_FOUND,
                                            Map.of("configKey", config.getConnectionConfigKey())
                                    ));

                            // Create sync client for transaction support
                            // Note: Using sync driver because reactive transactions have limitations
                            try (MongoClient syncClient = MongoClients.create(connConfig.getConnectionString())) {

                                // Configure transaction options
                                TransactionOptions txnOptions = TransactionOptions.builder()
                                        .readConcern(parseReadConcern(config.getReadConcern()))
                                        .writeConcern(parseWriteConcern(config.getWriteConcern()))
                                        .maxCommitTime((long) config.getMaxCommitTimeMs(), TimeUnit.MILLISECONDS)
                                        .build();

                                // Execute transaction
                                try (ClientSession session = syncClient.startSession()) {
                                    List<Map<String, Object>> results = new ArrayList<>();

                                    TransactionBody<List<Map<String, Object>>> txnBody = () -> {
                                        List<Map<String, Object>> opResults = new ArrayList<>();

                                        String dbName = config.getDatabase() != null
                                                ? config.getDatabase()
                                                : connConfig.getDatabase();

                                        for (TransactionOperation op : config.getOperations()) {
                                            String collName = op.getCollection() != null
                                                    ? op.getCollection()
                                                    : config.getCollection();

                                            var collection = syncClient.getDatabase(dbName)
                                                    .getCollection(collName);

                                            Map<String, Object> opResult = executeOperation(session, collection, op);
                                            opResults.add(opResult);
                                        }

                                        return opResults;
                                    };

                                    results = session.withTransaction(txnBody, txnOptions);

                                    Map<String, Object> result = new HashMap<>();
                                    result.put("committed", true);
                                    result.put("operationResults", results);
                                    result.put("operationCount", results.size());

                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                }
                            }

                        } catch (LyshraOpenAppProcessorRuntimeException e) {
                            throw e;
                        } catch (MongoException e) {
                            if (e.getMessage() != null && e.getMessage().contains("Transaction")) {
                                throw new LyshraOpenAppProcessorRuntimeException(
                                        MongoProcessorErrorCodes.TRANSACTION_FAILED,
                                        Map.of("message", e.getMessage()),
                                        e,
                                        null
                                );
                            }
                            throw new LyshraOpenAppProcessorRuntimeException(
                                    MongoProcessorErrorCodes.WRITE_FAILED,
                                    Map.of("message", e.getMessage()),
                                    e,
                                    null
                            );
                        } catch (Exception e) {
                            throw new LyshraOpenAppProcessorRuntimeException(
                                    MongoProcessorErrorCodes.TRANSACTION_FAILED,
                                    Map.of("message", e.getMessage()),
                                    e,
                                    null
                            );
                        }
                    }).cast(com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult.class);
                });
    }

    private static Map<String, Object> executeOperation(
            ClientSession session,
            com.mongodb.client.MongoCollection<org.bson.Document> collection,
            TransactionOperation op) {

        Map<String, Object> result = new HashMap<>();
        result.put("operationType", op.getOperationType().name());

        switch (op.getOperationType()) {
            case INSERT_ONE -> {
                org.bson.Document doc = new org.bson.Document(op.getDocument());
                collection.insertOne(session, doc);
                result.put("acknowledged", true);
                if (doc.containsKey("_id")) {
                    result.put("insertedId", doc.get("_id").toString());
                }
            }
            case UPDATE_ONE -> {
                org.bson.Document filter = new org.bson.Document(op.getFilter());
                org.bson.Document update = new org.bson.Document(op.getUpdate());
                var updateResult = collection.updateOne(session, filter, update);
                result.put("matchedCount", updateResult.getMatchedCount());
                result.put("modifiedCount", updateResult.getModifiedCount());
            }
            case UPDATE_MANY -> {
                org.bson.Document filter = new org.bson.Document(op.getFilter());
                org.bson.Document update = new org.bson.Document(op.getUpdate());
                var updateResult = collection.updateMany(session, filter, update);
                result.put("matchedCount", updateResult.getMatchedCount());
                result.put("modifiedCount", updateResult.getModifiedCount());
            }
            case REPLACE_ONE -> {
                org.bson.Document filter = new org.bson.Document(op.getFilter());
                org.bson.Document replacement = new org.bson.Document(op.getReplacement());
                var updateResult = collection.replaceOne(session, filter, replacement);
                result.put("matchedCount", updateResult.getMatchedCount());
                result.put("modifiedCount", updateResult.getModifiedCount());
            }
            case DELETE_ONE -> {
                org.bson.Document filter = new org.bson.Document(op.getFilter());
                var deleteResult = collection.deleteOne(session, filter);
                result.put("deletedCount", deleteResult.getDeletedCount());
            }
            case DELETE_MANY -> {
                org.bson.Document filter = new org.bson.Document(op.getFilter());
                var deleteResult = collection.deleteMany(session, filter);
                result.put("deletedCount", deleteResult.getDeletedCount());
            }
        }

        return result;
    }

    private static ReadConcern parseReadConcern(String readConcern) {
        if (readConcern == null) {
            return ReadConcern.SNAPSHOT;
        }
        return switch (readConcern.toLowerCase()) {
            case "local" -> ReadConcern.LOCAL;
            case "majority" -> ReadConcern.MAJORITY;
            case "linearizable" -> ReadConcern.LINEARIZABLE;
            case "available" -> ReadConcern.AVAILABLE;
            default -> ReadConcern.SNAPSHOT;
        };
    }

    private static WriteConcern parseWriteConcern(String writeConcern) {
        if (writeConcern == null) {
            return WriteConcern.MAJORITY;
        }
        return switch (writeConcern.toLowerCase()) {
            case "w1", "1" -> WriteConcern.W1;
            case "w2", "2" -> WriteConcern.W2;
            case "w3", "3" -> WriteConcern.W3;
            case "acknowledged" -> WriteConcern.ACKNOWLEDGED;
            case "unacknowledged" -> WriteConcern.UNACKNOWLEDGED;
            case "journaled" -> WriteConcern.JOURNALED;
            default -> WriteConcern.MAJORITY;
        };
    }

    private static void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) {
        MongoTransactionInputConfig config = (MongoTransactionInputConfig) input;

        if (config.getOperations() == null || config.getOperations().isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "operations")
            );
        }

        for (int i = 0; i < config.getOperations().size(); i++) {
            TransactionOperation op = config.getOperations().get(i);
            if (op.getOperationType() == null) {
                throw new LyshraOpenAppProcessorRuntimeException(
                        MongoProcessorErrorCodes.INVALID_BULK_OPERATION,
                        Map.of("operationType", "operation " + i + " has no operationType")
                );
            }
        }
    }

    private static MongoTransactionInputConfig createSampleConfig() {
        List<TransactionOperation> operations = new ArrayList<>();

        // Create order
        Map<String, Object> orderDoc = new HashMap<>();
        orderDoc.put("orderId", "ORD-001");
        orderDoc.put("amount", 150.00);
        orderDoc.put("status", "created");

        operations.add(TransactionOperation.builder()
                .operationType(TransactionOperationType.INSERT_ONE)
                .collection("orders")
                .document(orderDoc)
                .build());

        // Update inventory
        operations.add(TransactionOperation.builder()
                .operationType(TransactionOperationType.UPDATE_ONE)
                .collection("inventory")
                .filter(Map.of("productId", "PROD-001"))
                .update(Map.of("$inc", Map.of("quantity", -1)))
                .build());

        return MongoTransactionInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .database("ecommerce")
                .collection("orders")
                .operations(operations)
                .maxCommitTimeMs(30000)
                .readConcern("snapshot")
                .writeConcern("majority")
                .build();
    }
}
