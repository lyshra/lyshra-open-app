package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auth handler for requests that don't require authentication.
 */
public class NoAuthHandler implements IAuthHandler {

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.NO_AUTH;
    }

    @Override
    public void validateConfig(ILyshraOpenAppApiAuthConfig config) {
        // No validation needed for no auth
    }

    @Override
    public WebClient.RequestHeadersSpec<?> applyAuth(
            WebClient.RequestHeadersSpec<?> requestSpec,
            ILyshraOpenAppApiAuthConfig config,
            ApiRequestContext context) {
        // No authentication to apply
        return requestSpec;
    }
}
