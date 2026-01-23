package com.lyshra.open.app.mongodb.plugin;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPlugin;
import com.lyshra.open.app.integration.models.LyshraOpenAppPluginIdentifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for MongoDB Plugin.
 */
class LyshraOpenAppMongoDbPluginTest {

    @Test
    void testPluginIdentifier() {
        LyshraOpenAppPluginIdentifier identifier = LyshraOpenAppMongoDbPlugin.getPluginIdentifier();

        assertNotNull(identifier);
        assertEquals("com.lyshra.open.app", identifier.getOrganization());
        assertEquals("mongodb", identifier.getModule());
        assertEquals("1.0.0", identifier.getVersion());
    }

    @Test
    void testPluginCreation() {
        LyshraOpenAppMongoDbPlugin provider = new LyshraOpenAppMongoDbPlugin();

        // Plugin should be created (facade can be null for basic structure test)
        ILyshraOpenAppPlugin plugin = provider.create(null);

        assertNotNull(plugin);
        assertNotNull(plugin.getIdentifier());
        assertNotNull(plugin.getProcessors());
        assertEquals(20, plugin.getProcessors().getProcessors().size(), "Should have 20 MongoDB processors");
    }

    @Test
    void testProcessorNames() {
        LyshraOpenAppMongoDbPlugin provider = new LyshraOpenAppMongoDbPlugin();
        ILyshraOpenAppPlugin plugin = provider.create(null);

        var processors = plugin.getProcessors().getProcessors();

        // Verify key processor names exist
        assertTrue(processors.containsKey("MONGO_FIND_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_FIND_ONE_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_INSERT_ONE_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_INSERT_MANY_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_UPDATE_ONE_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_UPDATE_MANY_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_DELETE_ONE_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_DELETE_MANY_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_AGGREGATE_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_COUNT_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_DISTINCT_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_REPLACE_ONE_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_FIND_ONE_AND_UPDATE_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_FIND_ONE_AND_REPLACE_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_FIND_ONE_AND_DELETE_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_BULK_WRITE_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_CREATE_INDEX_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_DROP_INDEX_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_LIST_INDEXES_PROCESSOR"));
        assertTrue(processors.containsKey("MONGO_TRANSACTION_PROCESSOR"));
    }
}
