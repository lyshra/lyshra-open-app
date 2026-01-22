package com.lyshra.open.app.integration.contract.api;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;

/**
 * Marker interface for API authentication configurations.
 * Each authentication type has its own implementation with type-specific fields.
 */
public interface ILyshraOpenAppApiAuthConfig {

    /**
     * Returns the authentication type this configuration is for.
     *
     * @return the authentication type
     */
    LyshraOpenAppApiAuthType getAuthType();
}
