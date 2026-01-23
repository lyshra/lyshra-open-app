package com.lyshra.open.app.mongodb.plugin.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * MongoDB Connection Pool Settings.
 * Configures the connection pool behavior for MongoDB client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MongoConnectionPoolSettings implements Serializable {

    /**
     * Maximum number of connections in the pool.
     * Default: 100
     */
    @Builder.Default
    private Integer maxPoolSize = 100;

    /**
     * Minimum number of connections in the pool.
     * Default: 0
     */
    @Builder.Default
    private Integer minPoolSize = 0;

    /**
     * Maximum time (in milliseconds) to wait for a connection from the pool.
     * Default: 120000 (2 minutes)
     */
    @Builder.Default
    private Integer maxWaitTimeMs = 120000;

    /**
     * Maximum time (in milliseconds) a connection can remain idle before being closed.
     * Default: 0 (no limit)
     */
    @Builder.Default
    private Integer maxConnectionIdleTimeMs = 0;

    /**
     * Maximum time (in milliseconds) a connection can exist before being closed.
     * Default: 0 (no limit)
     */
    @Builder.Default
    private Integer maxConnectionLifeTimeMs = 0;
}
