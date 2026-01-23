package com.lyshra.open.app.mongodb.plugin.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * MongoDB Connection Configuration.
 * Comprehensive configuration for MongoDB client connections supporting multi-tenancy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MongoConnectionConfig implements Serializable {

    /**
     * MongoDB connection URI string.
     * Example: "mongodb://localhost:27017" or "mongodb+srv://cluster.mongodb.net"
     */
    @NotBlank(message = "{mongodb.config.connectionString.null}")
    private String connectionString;

    /**
     * Default database name to use for operations.
     */
    @NotBlank(message = "{mongodb.config.database.null}")
    private String database;

    /**
     * Optional username for authentication.
     * Can also be specified in the connection string.
     */
    private String username;

    /**
     * Optional password for authentication.
     * Can also be specified in the connection string.
     */
    private String password;

    /**
     * Connection pool settings.
     */
    @Valid
    @Builder.Default
    private MongoConnectionPoolSettings connectionPoolSettings = new MongoConnectionPoolSettings();

    /**
     * Server settings for monitoring and selection.
     */
    @Valid
    @Builder.Default
    private MongoServerSettings serverSettings = new MongoServerSettings();

    /**
     * Read preference for replica set members.
     * Default: PRIMARY
     */
    @Builder.Default
    private MongoReadPreference readPreference = MongoReadPreference.PRIMARY;

    /**
     * Read concern for consistency guarantees.
     * Default: LOCAL
     */
    @Builder.Default
    private MongoReadConcern readConcern = MongoReadConcern.LOCAL;

    /**
     * Write concern specification.
     * Examples: "w:1", "w:majority", "w:2"
     * Default: "w:1"
     */
    @Builder.Default
    private String writeConcern = "w:1";

    /**
     * Enable retry for write operations.
     * Default: true
     */
    @Builder.Default
    private Boolean retryWrites = true;

    /**
     * Enable retry for read operations.
     * Default: true
     */
    @Builder.Default
    private Boolean retryReads = true;

    /**
     * Enable SSL/TLS encryption.
     * Default: false (can also be set in connection string)
     */
    @Builder.Default
    private Boolean sslEnabled = false;

    /**
     * Authentication database name.
     * Default: "admin"
     */
    @Builder.Default
    private String authDatabase = "admin";

    /**
     * Additional properties for advanced configuration.
     * These will be passed to the MongoDB client settings.
     */
    private Map<String, Object> additionalProperties;
}
