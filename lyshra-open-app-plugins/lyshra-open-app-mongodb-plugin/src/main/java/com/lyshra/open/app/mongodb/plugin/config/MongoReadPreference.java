package com.lyshra.open.app.mongodb.plugin.config;

/**
 * MongoDB Read Preference enumeration.
 * Determines which members of a replica set to read from.
 */
public enum MongoReadPreference {
    /**
     * Read from the primary member only.
     */
    PRIMARY,

    /**
     * Read from the primary if available, otherwise from a secondary.
     */
    PRIMARY_PREFERRED,

    /**
     * Read from secondary members only.
     */
    SECONDARY,

    /**
     * Read from secondary if available, otherwise from primary.
     */
    SECONDARY_PREFERRED,

    /**
     * Read from the nearest member based on network latency.
     */
    NEAREST
}
