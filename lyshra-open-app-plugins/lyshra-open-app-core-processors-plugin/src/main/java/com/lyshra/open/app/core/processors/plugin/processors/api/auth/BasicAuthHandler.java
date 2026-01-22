package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.core.processors.plugin.processors.api.ApiProcessor;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.apis.auth.BasicAuthConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Auth handler for Basic Authentication.
 * Username and password are base64 encoded and sent in the Authorization header.
 */
public class BasicAuthHandler implements IAuthHandler {

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.BASIC_AUTH;
    }

    @Override
    public void validateConfig(ILyshraOpenAppApiAuthConfig config) {
        // Basic field validations (username, password) are handled by Jakarta annotations
        if (!(config instanceof BasicAuthConfig)) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Invalid config type for Basic authentication")
            );
        }
    }

    @Override
    public WebClient.RequestHeadersSpec<?> applyAuth(
            WebClient.RequestHeadersSpec<?> requestSpec,
            ILyshraOpenAppApiAuthConfig config,
            ApiRequestContext context) {

        BasicAuthConfig basicConfig = (BasicAuthConfig) config;

        String credentials = basicConfig.getUsername() + ":" + basicConfig.getPassword();
        String encodedCredentials = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)
        );

        return requestSpec.header(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials);
    }
}
