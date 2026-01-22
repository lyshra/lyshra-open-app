package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.core.processors.plugin.processors.api.ApiProcessor;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.apis.auth.ApiKeyAuthConfig;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Auth handler for API Key authentication.
 * The API key can be sent as a header or query parameter.
 */
public class ApiKeyAuthHandler implements IAuthHandler {

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.API_KEY;
    }

    @Override
    public void validateConfig(ILyshraOpenAppApiAuthConfig config) {
        // Basic field validations (key, value, addTo) are handled by Jakarta annotations
        if (!(config instanceof ApiKeyAuthConfig)) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Invalid config type for API Key authentication")
            );
        }
    }

    @Override
    public WebClient.RequestHeadersSpec<?> applyAuth(
            WebClient.RequestHeadersSpec<?> requestSpec,
            ILyshraOpenAppApiAuthConfig config,
            ApiRequestContext context) {

        ApiKeyAuthConfig apiKeyConfig = (ApiKeyAuthConfig) config;

        if (apiKeyConfig.getAddTo() == ApiKeyAuthConfig.ApiKeyLocation.HEADER) {
            return requestSpec.header(apiKeyConfig.getKey(), apiKeyConfig.getValue());
        }
        // For QUERY, the query parameter should be added when building the URL
        // The caller should handle this before calling applyAuth
        return requestSpec;
    }
}
