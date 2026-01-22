package com.lyshra.open.app.integration.models.apis.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import lombok.Data;

import java.io.Serializable;

/**
 * Configuration for No Authentication.
 * This is a marker configuration indicating no authentication is required.
 */
@Data
public class NoAuthConfig implements ILyshraOpenAppApiAuthConfig, Serializable {

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.NO_AUTH;
    }

    // ---------------------------------------------------------
    // Builder Entry
    // ---------------------------------------------------------
    public static BuildStep builder() {
        return new Builder();
    }

    // ---------------------------------------------------------
    // Step Interfaces
    // ---------------------------------------------------------
    public interface BuildStep {
        NoAuthConfig build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements BuildStep {

        @Override
        public NoAuthConfig build() {
            return new NoAuthConfig();
        }
    }
}
