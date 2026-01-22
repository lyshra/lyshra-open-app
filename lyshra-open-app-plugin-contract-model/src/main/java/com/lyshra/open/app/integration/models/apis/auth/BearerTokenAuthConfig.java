package com.lyshra.open.app.integration.models.apis.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Configuration for Bearer Token authentication.
 * The token is sent in the Authorization header as "Bearer {token}".
 */
@Data
public class BearerTokenAuthConfig implements ILyshraOpenAppApiAuthConfig, Serializable {

    /**
     * The bearer token value.
     */
    @NotBlank(message = "{auth.bearer.token.null}")
    @Size(min = 1, max = 10000, message = "{auth.bearer.token.invalid.length}")
    private final String token;

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.BEARER_TOKEN;
    }

    // ---------------------------------------------------------
    // Builder Entry
    // ---------------------------------------------------------
    public static InitialStepBuilder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------
    // Step Interfaces
    // ---------------------------------------------------------
    public interface InitialStepBuilder {
        BuildStep token(String token);
    }

    public interface BuildStep {
        BearerTokenAuthConfig build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            BuildStep {

        private String token;

        @Override
        public BuildStep token(String token) {
            this.token = token;
            return this;
        }

        @Override
        public BearerTokenAuthConfig build() {
            return new BearerTokenAuthConfig(token);
        }
    }
}
