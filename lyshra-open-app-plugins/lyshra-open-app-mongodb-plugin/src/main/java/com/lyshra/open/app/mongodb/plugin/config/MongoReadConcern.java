package com.lyshra.open.app.mongodb.plugin.config;

/**
 * MongoDB Read Concern enumeration.
 * Controls the consistency and isolation properties of read operations.
 */
public enum MongoReadConcern {
    /**
     * Returns data from the instance with no guarantee of durability.
     */
    LOCAL,

    /**
     * Returns data acknowledged by a majority of replica set members.
     */
    MAJORITY,

    /**
     * Returns data that has been acknowledged by a majority and is durable.
     */
    LINEARIZABLE,

    /**
     * Returns data from a consistent snapshot (for transactions).
     */
    SNAPSHOT,

    /**
     * Returns data regardless of durability, useful for sharded clusters.
     */
    AVAILABLE
}
