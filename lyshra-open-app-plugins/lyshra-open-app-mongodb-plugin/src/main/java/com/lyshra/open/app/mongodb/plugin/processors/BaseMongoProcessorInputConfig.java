package com.lyshra.open.app.mongodb.plugin.processors;

import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Base input configuration for all MongoDB processors.
 * Contains common fields required by most MongoDB operations.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseMongoProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {

    /**
     * Key to retrieve MongoDB connection configuration from context.
     * The configuration should contain connection string, database, and pool settings.
     */
    @NotBlank(message = "{mongodb.processor.input.connectionConfigKey.null}")
    @Size(min = 1, max = 200, message = "{mongodb.processor.input.connectionConfigKey.invalid.length}")
    private String connectionConfigKey;

    /**
     * Database name. Optional - uses the default from connection config if not provided.
     */
    @Size(max = 200, message = "{mongodb.processor.input.database.invalid.length}")
    private String database;

    /**
     * Collection name. Required for most operations.
     */
    @NotBlank(message = "{mongodb.processor.input.collection.null}")
    @Size(min = 1, max = 200, message = "{mongodb.processor.input.collection.invalid.length}")
    private String collection;
}
