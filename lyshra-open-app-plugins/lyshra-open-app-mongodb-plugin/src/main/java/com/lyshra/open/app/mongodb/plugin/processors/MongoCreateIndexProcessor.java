package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.mongodb.plugin.converter.BsonTypeConverter;
import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.mongodb.plugin.util.MongoOperationHelper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB Create Index Processor.
 * Creates indexes on collections for query optimization.
 */
public class MongoCreateIndexProcessor {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class MongoCreateIndexInputConfig extends BaseMongoProcessorInputConfig {

        /**
         * Index key specification.
         * Key: field name, Value: 1 (ascending), -1 (descending), "text", "2dsphere", "hashed"
         */
        @NotEmpty(message = "{mongodb.createindex.processor.input.indexKeys.empty}")
        private Map<String, Object> indexKeys;

        /**
         * Optional index name. Auto-generated if not provided.
         */
        private String indexName;

        /**
         * Whether the index enforces uniqueness.
         */
        @Builder.Default
        private Boolean unique = false;

        /**
         * Whether to skip documents without the indexed field.
         */
        @Builder.Default
        private Boolean sparse = false;

        /**
         * Whether to build index in background (deprecated in MongoDB 4.2+).
         */
        @Builder.Default
        private Boolean background = false;

        /**
         * For TTL indexes: seconds until document expiration.
         */
        @Min(value = 0, message = "{mongodb.createindex.processor.input.expireAfterSeconds.invalid}")
        private Integer expireAfterSeconds;

        /**
         * Partial filter expression for partial indexes.
         */
        private Map<String, Object> partialFilterExpression;

        /**
         * Collation options for string comparison.
         */
        private Map<String, Object> collation;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("MONGO_CREATE_INDEX_PROCESSOR")
                .humanReadableNameTemplate("mongodb.createindex.processor.name")
                .searchTagsCsvTemplate("mongodb.createindex.processor.search.tags")
                .errorCodeEnum(MongoProcessorErrorCodes.class)
                .inputConfigType(MongoCreateIndexInputConfig.class)
                .sampleInputConfigs(List.of(createSampleConfig()))
                .validateInputConfig(MongoCreateIndexProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    MongoCreateIndexInputConfig config = (MongoCreateIndexInputConfig) input;

                    try {
                        MongoCollection<Document> collection = MongoOperationHelper.getCollection(
                                config.getConnectionConfigKey(),
                                config.getDatabase(),
                                config.getCollection(),
                                context,
                                facade
                        );

                        // Build index keys document
                        Document indexKeys = new Document();
                        config.getIndexKeys().forEach((key, value) -> {
                            if (value instanceof String) {
                                indexKeys.append(key, value);
                            } else if (value instanceof Number) {
                                indexKeys.append(key, ((Number) value).intValue());
                            } else {
                                indexKeys.append(key, value);
                            }
                        });

                        // Build index options
                        IndexOptions options = new IndexOptions();

                        if (config.getIndexName() != null && !config.getIndexName().isBlank()) {
                            options.name(config.getIndexName());
                        }

                        options.unique(Boolean.TRUE.equals(config.getUnique()));
                        options.sparse(Boolean.TRUE.equals(config.getSparse()));
                        options.background(Boolean.TRUE.equals(config.getBackground()));

                        if (config.getExpireAfterSeconds() != null && config.getExpireAfterSeconds() >= 0) {
                            options.expireAfter((long) config.getExpireAfterSeconds(), TimeUnit.SECONDS);
                        }

                        if (config.getPartialFilterExpression() != null && !config.getPartialFilterExpression().isEmpty()) {
                            Bson partialFilter = BsonTypeConverter.toDocument(config.getPartialFilterExpression());
                            options.partialFilterExpression(partialFilter);
                        }

                        if (config.getCollation() != null && !config.getCollation().isEmpty()) {
                            com.mongodb.client.model.Collation.Builder collationBuilder =
                                    com.mongodb.client.model.Collation.builder();

                            Object locale = config.getCollation().get("locale");
                            if (locale != null) {
                                collationBuilder.locale(locale.toString());
                            }

                            // Add other collation options as needed
                            options.collation(collationBuilder.build());
                        }

                        return Mono.from(collection.createIndex(indexKeys, options))
                                .map(indexNameResult -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("indexName", indexNameResult);
                                    result.put("created", true);
                                    return LyshraOpenAppProcessorOutput.ofData(result);
                                })
                                .cast(com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult.class)
                                .onErrorMap(MongoCommandException.class, e -> {
                                    // Check for duplicate index
                                    if (e.getErrorCode() == 85 || e.getErrorCode() == 86) {
                                        return new LyshraOpenAppProcessorRuntimeException(
                                                MongoProcessorErrorCodes.DUPLICATE_INDEX,
                                                Map.of("indexName", config.getIndexName() != null ? config.getIndexName() : "auto"),
                                                e,
                                                null
                                        );
                                    }
                                    return new LyshraOpenAppProcessorRuntimeException(
                                            MongoProcessorErrorCodes.INDEX_CREATION_FAILED,
                                            Map.of("message", e.getMessage()),
                                            e,
                                            null
                                    );
                                })
                                .onErrorMap(MongoException.class, e ->
                                        new LyshraOpenAppProcessorRuntimeException(
                                                MongoProcessorErrorCodes.INDEX_CREATION_FAILED,
                                                Map.of("message", e.getMessage()),
                                                e,
                                                null
                                        )
                                );

                    } catch (LyshraOpenAppProcessorRuntimeException e) {
                        return Mono.error(e);
                    } catch (Exception e) {
                        return Mono.error(new LyshraOpenAppProcessorRuntimeException(
                                MongoProcessorErrorCodes.INDEX_CREATION_FAILED,
                                Map.of("message", e.getMessage()),
                                e,
                                null
                        ));
                    }
                });
    }

    private static void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) {
        MongoCreateIndexInputConfig config = (MongoCreateIndexInputConfig) input;

        if (config.getIndexKeys() == null || config.getIndexKeys().isEmpty()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    MongoProcessorErrorCodes.MISSING_REQUIRED_FIELD,
                    Map.of("field", "indexKeys")
            );
        }
    }

    private static MongoCreateIndexInputConfig createSampleConfig() {
        return MongoCreateIndexInputConfig.builder()
                .connectionConfigKey("mongodbConnection")
                .collection("users")
                .indexKeys(Map.of("email", 1))
                .indexName("email_unique_idx")
                .unique(true)
                .sparse(false)
                .build();
    }
}
