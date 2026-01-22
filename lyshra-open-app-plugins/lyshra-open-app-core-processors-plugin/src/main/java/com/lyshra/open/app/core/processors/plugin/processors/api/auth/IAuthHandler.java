package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Interface for authentication handlers.
 * Each authentication type has its own implementation that applies
 * the appropriate authentication mechanism to WebClient requests.
 */
public interface IAuthHandler {

    /**
     * Returns the authentication type this handler supports.
     *
     * @return the supported auth type
     */
    LyshraOpenAppApiAuthType getAuthType();

    /**
     * Validates the authentication configuration.
     *
     * @param config the auth configuration to validate
     * @throws com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException if validation fails
     */
    void validateConfig(ILyshraOpenAppApiAuthConfig config);

    /**
     * Applies authentication to the WebClient request spec.
     * This method modifies the request to include the necessary authentication headers,
     * query parameters, or other authentication mechanisms.
     *
     * @param requestSpec the WebClient request header spec to modify
     * @param config      the authentication configuration
     * @param context     the API request context containing additional information
     * @return the modified request spec
     */
    WebClient.RequestHeadersSpec<?> applyAuth(
            WebClient.RequestHeadersSpec<?> requestSpec,
            ILyshraOpenAppApiAuthConfig config,
            ApiRequestContext context
    );
}
