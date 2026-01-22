package com.lyshra.open.app.core.processors.plugin.processors.api.auth;

import com.lyshra.open.app.core.processors.plugin.processors.api.ApiProcessor;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.apis.auth.OAuth2AuthConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Auth handler for OAuth 2.0 authentication.
 * Supports Bearer token authentication. Token acquisition should be handled separately.
 */
public class OAuth2AuthHandler implements IAuthHandler {

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.OAUTH2;
    }

    @Override
    public void validateConfig(ILyshraOpenAppApiAuthConfig config) {
        // Basic field validation (grantType) is handled by Jakarta annotations
        if (!(config instanceof OAuth2AuthConfig oauth2Config)) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Invalid config type for OAuth 2.0 authentication")
            );
        }

        // Complex business validation: either accessToken OR proper grant type credentials are required
        // If access token is already provided, we can use it directly
        if (oauth2Config.getAccessToken() != null && !oauth2Config.getAccessToken().isBlank()) {
            return;
        }

        // Otherwise, validate based on grant type (multi-field conditional validation)
        if (oauth2Config.getGrantType() == null) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Either access token or grant type is required for OAuth 2.0")
            );
        }

        switch (oauth2Config.getGrantType()) {
            case CLIENT_CREDENTIALS:
                validateClientCredentials(oauth2Config);
                break;
            case PASSWORD:
                validatePasswordGrant(oauth2Config);
                break;
            case AUTHORIZATION_CODE:
                validateAuthorizationCode(oauth2Config);
                break;
            case REFRESH_TOKEN:
                validateRefreshToken(oauth2Config);
                break;
            case IMPLICIT:
                validateImplicitGrant(oauth2Config);
                break;
        }
    }

    private void validateClientCredentials(OAuth2AuthConfig config) {
        if (config.getAccessTokenUrl() == null || config.getAccessTokenUrl().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Token URL is required for client credentials grant")
            );
        }
        if (config.getClientId() == null || config.getClientId().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Client ID is required for client credentials grant")
            );
        }
        if (config.getClientSecret() == null || config.getClientSecret().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Client secret is required for client credentials grant")
            );
        }
    }

    private void validatePasswordGrant(OAuth2AuthConfig config) {
        validateClientCredentials(config);
        if (config.getUsername() == null || config.getUsername().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Username is required for password grant")
            );
        }
        if (config.getPassword() == null) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Password is required for password grant")
            );
        }
    }

    private void validateAuthorizationCode(OAuth2AuthConfig config) {
        if (config.getAuthUrl() == null || config.getAuthUrl().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Authorization URL is required for authorization code grant")
            );
        }
        if (config.getAccessTokenUrl() == null || config.getAccessTokenUrl().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Token URL is required for authorization code grant")
            );
        }
        if (config.getClientId() == null || config.getClientId().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Client ID is required for authorization code grant")
            );
        }
    }

    private void validateRefreshToken(OAuth2AuthConfig config) {
        if (config.getAccessTokenUrl() == null || config.getAccessTokenUrl().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Token URL is required for refresh token grant")
            );
        }
        if (config.getRefreshToken() == null || config.getRefreshToken().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Refresh token is required for refresh token grant")
            );
        }
    }

    private void validateImplicitGrant(OAuth2AuthConfig config) {
        if (config.getAuthUrl() == null || config.getAuthUrl().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Authorization URL is required for implicit grant")
            );
        }
    }

    @Override
    public WebClient.RequestHeadersSpec<?> applyAuth(
            WebClient.RequestHeadersSpec<?> requestSpec,
            ILyshraOpenAppApiAuthConfig config,
            ApiRequestContext context) {

        OAuth2AuthConfig oauth2Config = (OAuth2AuthConfig) config;

        // For now, we only support using an existing access token
        // Token acquisition flows (client credentials, password, etc.) should be
        // implemented as separate operations or done beforehand
        if (oauth2Config.getAccessToken() == null || oauth2Config.getAccessToken().isBlank()) {
            throw new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessor.ApiProcessorErrorCodes.API_VALIDATION_FAILED,
                    Map.of("message", "Access token must be provided. Token acquisition flows are not yet implemented in this handler.")
            );
        }

        String prefix = oauth2Config.getHeaderPrefix() != null ? oauth2Config.getHeaderPrefix() : "Bearer";

        if (oauth2Config.getAddTokenTo() == OAuth2AuthConfig.OAuth2TokenLocation.QUERY) {
            // Query parameter should be handled by the caller
            return requestSpec;
        }

        return requestSpec.header(HttpHeaders.AUTHORIZATION, prefix + " " + oauth2Config.getAccessToken());
    }
}
