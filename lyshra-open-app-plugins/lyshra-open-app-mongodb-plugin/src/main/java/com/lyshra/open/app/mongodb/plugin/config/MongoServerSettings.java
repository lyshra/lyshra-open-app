package com.lyshra.open.app.mongodb.plugin.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * MongoDB Server Settings.
 * Configures server monitoring and selection behavior.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MongoServerSettings implements Serializable {

    /**
     * Frequency (in milliseconds) at which the driver sends heartbeats to servers.
     * Default: 10000 (10 seconds)
     */
    @Builder.Default
    private Integer heartbeatFrequencyMs = 10000;

    /**
     * Maximum time (in milliseconds) to wait for a server to become available.
     * Default: 30000 (30 seconds)
     */
    @Builder.Default
    private Integer serverSelectionTimeoutMs = 30000;
}
