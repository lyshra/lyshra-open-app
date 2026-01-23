package com.lyshra.open.app.mongodb.plugin;

import com.lyshra.open.app.integration.ILyshraOpenAppPluginProvider;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPlugin;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.models.LyshraOpenAppPlugin;
import com.lyshra.open.app.integration.models.LyshraOpenAppPluginIdentifier;
import com.lyshra.open.app.mongodb.plugin.processors.*;

/**
 * Lyshra OpenApp MongoDB Plugin Provider.
 * <p>
 * This plugin provides comprehensive MongoDB integration for workflow processing with:
 * - Full CRUD operations (find, insert, update, delete)
 * - Aggregation pipeline support
 * - Index management
 * - Transaction support (ACID)
 * - Bulk write operations
 * - Connection pooling with multi-tenancy support
 * <p>
 * Plugin Identifier: com.lyshra.open.app:mongodb:1.0.0
 */
public class LyshraOpenAppMongoDbPlugin implements ILyshraOpenAppPluginProvider {

    /**
     * Returns the plugin identifier.
     *
     * @return The plugin identifier with groupId, artifactId, and version
     */
    public static LyshraOpenAppPluginIdentifier getPluginIdentifier() {
        return new LyshraOpenAppPluginIdentifier("com.lyshra.open.app", "mongodb", "1.0.0");
    }

    @Override
    public ILyshraOpenAppPlugin create(ILyshraOpenAppPluginFacade lyshraOpenAppFacade) {
        return LyshraOpenAppPlugin
                .builder()
                .identifier(getPluginIdentifier())
                .documentationResourcePath("documentation.md")
                .processors(builder -> builder
                        // Query Processors
                        .processor(MongoFindProcessor::build)
                        .processor(MongoFindOneProcessor::build)
                        .processor(MongoCountProcessor::build)
                        .processor(MongoDistinctProcessor::build)
                        .processor(MongoAggregateProcessor::build)

                        // Insert Processors
                        .processor(MongoInsertOneProcessor::build)
                        .processor(MongoInsertManyProcessor::build)

                        // Update Processors
                        .processor(MongoUpdateOneProcessor::build)
                        .processor(MongoUpdateManyProcessor::build)
                        .processor(MongoReplaceOneProcessor::build)

                        // Delete Processors
                        .processor(MongoDeleteOneProcessor::build)
                        .processor(MongoDeleteManyProcessor::build)

                        // Atomic Operations (Find-and-Modify)
                        .processor(MongoFindOneAndUpdateProcessor::build)
                        .processor(MongoFindOneAndReplaceProcessor::build)
                        .processor(MongoFindOneAndDeleteProcessor::build)

                        // Bulk Operations
                        .processor(MongoBulkWriteProcessor::build)

                        // Index Management
                        .processor(MongoCreateIndexProcessor::build)
                        .processor(MongoDropIndexProcessor::build)
                        .processor(MongoListIndexesProcessor::build)

                        // Transaction Support
                        .processor(MongoTransactionProcessor::build)
                )
                .i18n(builder -> builder
                        .resourceBundleBasenamePath("i18n/messages")
                )
                .build();
    }
}
