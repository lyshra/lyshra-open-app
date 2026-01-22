package com.lyshra.open.app.integration.contract.api;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;

/**
 * Interface for API authentication profiles.
 * An auth profile defines the authentication mechanism and its configuration.
 */
public interface ILyshraOpenAppApiAuthProfile {

    /**
     * Returns the unique name of this authentication profile.
     *
     * @return the profile name
     */
    String getName();

    /**
     * Returns the authentication type.
     *
     * @return the auth type
     */
    LyshraOpenAppApiAuthType getType();

    /**
     * Returns the authentication configuration specific to the auth type.
     *
     * @return the auth configuration
     */
    ILyshraOpenAppApiAuthConfig getConfig();
}
