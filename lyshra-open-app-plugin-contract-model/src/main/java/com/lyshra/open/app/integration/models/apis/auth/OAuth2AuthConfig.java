package com.lyshra.open.app.integration.models.apis.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Configuration for OAuth 2.0 authentication.
 * Supports various grant types: Authorization Code, Client Credentials,
 * Password Credentials, and Implicit.
 */
@Data
public class OAuth2AuthConfig implements ILyshraOpenAppApiAuthConfig, Serializable {

    // Mandatory field
    @NotNull(message = "{auth.oauth2.grantType.null}")
    private final OAuth2GrantType grantType;

    // Optional fields - availability depends on grant type
    @Size(max = 10000, message = "{auth.oauth2.accessToken.invalid.length}")
    private final String accessToken;

    private final OAuth2TokenLocation addTokenTo;

    @Size(max = 50, message = "{auth.oauth2.headerPrefix.invalid.length}")
    private final String headerPrefix;

    // Authorization Code / Implicit Grant fields
    @Size(max = 2000, message = "{auth.oauth2.authUrl.invalid.length}")
    private final String authUrl;

    @Size(max = 2000, message = "{auth.oauth2.callbackUrl.invalid.length}")
    private final String callbackUrl;

    // Token exchange fields
    @Size(max = 2000, message = "{auth.oauth2.accessTokenUrl.invalid.length}")
    private final String accessTokenUrl;

    @Size(max = 500, message = "{auth.oauth2.clientId.invalid.length}")
    private final String clientId;

    @Size(max = 1000, message = "{auth.oauth2.clientSecret.invalid.length}")
    private final String clientSecret;

    @Size(max = 1000, message = "{auth.oauth2.scope.invalid.length}")
    private final String scope;

    @Size(max = 500, message = "{auth.oauth2.state.invalid.length}")
    private final String state;

    // Password Grant fields
    @Size(max = 500, message = "{auth.oauth2.username.invalid.length}")
    private final String username;

    @Size(max = 2000, message = "{auth.oauth2.password.invalid.length}")
    private final String password;

    // Refresh Token fields
    @Size(max = 10000, message = "{auth.oauth2.refreshToken.invalid.length}")
    private final String refreshToken;

    private final OAuth2ClientAuth clientAuthentication;

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.OAUTH2;
    }

    public enum OAuth2GrantType {
        AUTHORIZATION_CODE,
        CLIENT_CREDENTIALS,
        PASSWORD,
        IMPLICIT,
        REFRESH_TOKEN
    }

    public enum OAuth2TokenLocation {
        HEADER,
        QUERY
    }

    public enum OAuth2ClientAuth {
        BODY,
        HEADER
    }

    // ---------------------------------------------------------
    // Builder Entry
    // ---------------------------------------------------------
    public static InitialStepBuilder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------
    // Step Interfaces (Grant type first, then optional, then build)
    // ---------------------------------------------------------
    public interface InitialStepBuilder {
        OptionalStep grantType(OAuth2GrantType grantType);
    }

    public interface OptionalStep extends BuildStep {
        OptionalStep accessToken(String accessToken);
        OptionalStep addTokenTo(OAuth2TokenLocation addTokenTo);
        OptionalStep headerPrefix(String headerPrefix);
        OptionalStep authUrl(String authUrl);
        OptionalStep callbackUrl(String callbackUrl);
        OptionalStep accessTokenUrl(String accessTokenUrl);
        OptionalStep clientId(String clientId);
        OptionalStep clientSecret(String clientSecret);
        OptionalStep scope(String scope);
        OptionalStep state(String state);
        OptionalStep username(String username);
        OptionalStep password(String password);
        OptionalStep refreshToken(String refreshToken);
        OptionalStep clientAuthentication(OAuth2ClientAuth clientAuthentication);
    }

    public interface BuildStep {
        OAuth2AuthConfig build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            OptionalStep {

        private OAuth2GrantType grantType;
        private String accessToken;
        private OAuth2TokenLocation addTokenTo = OAuth2TokenLocation.HEADER;
        private String headerPrefix = "Bearer";
        private String authUrl;
        private String callbackUrl;
        private String accessTokenUrl;
        private String clientId;
        private String clientSecret;
        private String scope;
        private String state;
        private String username;
        private String password;
        private String refreshToken;
        private OAuth2ClientAuth clientAuthentication = OAuth2ClientAuth.HEADER;

        @Override
        public OptionalStep grantType(OAuth2GrantType grantType) {
            this.grantType = grantType;
            return this;
        }

        @Override
        public OptionalStep accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        @Override
        public OptionalStep addTokenTo(OAuth2TokenLocation addTokenTo) {
            this.addTokenTo = addTokenTo;
            return this;
        }

        @Override
        public OptionalStep headerPrefix(String headerPrefix) {
            this.headerPrefix = headerPrefix;
            return this;
        }

        @Override
        public OptionalStep authUrl(String authUrl) {
            this.authUrl = authUrl;
            return this;
        }

        @Override
        public OptionalStep callbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        @Override
        public OptionalStep accessTokenUrl(String accessTokenUrl) {
            this.accessTokenUrl = accessTokenUrl;
            return this;
        }

        @Override
        public OptionalStep clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        @Override
        public OptionalStep clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        @Override
        public OptionalStep scope(String scope) {
            this.scope = scope;
            return this;
        }

        @Override
        public OptionalStep state(String state) {
            this.state = state;
            return this;
        }

        @Override
        public OptionalStep username(String username) {
            this.username = username;
            return this;
        }

        @Override
        public OptionalStep password(String password) {
            this.password = password;
            return this;
        }

        @Override
        public OptionalStep refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        @Override
        public OptionalStep clientAuthentication(OAuth2ClientAuth clientAuthentication) {
            this.clientAuthentication = clientAuthentication;
            return this;
        }

        @Override
        public OAuth2AuthConfig build() {
            return new OAuth2AuthConfig(
                    grantType, accessToken, addTokenTo, headerPrefix,
                    authUrl, callbackUrl, accessTokenUrl, clientId,
                    clientSecret, scope, state, username, password,
                    refreshToken, clientAuthentication
            );
        }
    }
}
