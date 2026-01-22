package com.lyshra.open.app.integration.models.apis;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthProfile;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import com.lyshra.open.app.integration.models.apis.auth.*;
import lombok.Data;

import java.io.Serializable;

/**
 * Implementation of API authentication profile.
 * Contains the profile name, auth type, and type-specific configuration.
 */
@Data
public class LyshraOpenAppApiAuthProfile implements ILyshraOpenAppApiAuthProfile, Serializable {
    private final String name;
    private final LyshraOpenAppApiAuthType type;
    private final ILyshraOpenAppApiAuthConfig config;

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
        TypeStep name(String n);
    }

    public interface TypeStep {
        /**
         * Sets the auth type to NO_AUTH with default NoAuthConfig.
         */
        BuildStep noAuth();

        /**
         * Sets the auth type to API_KEY with the provided configuration.
         */
        BuildStep apiKey(ApiKeyAuthConfig config);

        /**
         * Sets the auth type to BEARER_TOKEN with the provided configuration.
         */
        BuildStep bearerToken(BearerTokenAuthConfig config);

        /**
         * Sets the auth type to BASIC_AUTH with the provided configuration.
         */
        BuildStep basicAuth(BasicAuthConfig config);

        /**
         * Sets the auth type to DIGEST_AUTH with the provided configuration.
         */
        BuildStep digestAuth(DigestAuthConfig config);

        /**
         * Sets the auth type to OAUTH1 with the provided configuration.
         */
        BuildStep oauth1(OAuth1AuthConfig config);

        /**
         * Sets the auth type to OAUTH2 with the provided configuration.
         */
        BuildStep oauth2(OAuth2AuthConfig config);

        /**
         * Sets the auth type to HAWK with the provided configuration.
         */
        BuildStep hawk(HawkAuthConfig config);

        /**
         * Sets the auth type to AWS_SIGNATURE with the provided configuration.
         */
        BuildStep awsSignature(AwsSignatureAuthConfig config);

        /**
         * Sets the auth type to NTLM with the provided configuration.
         */
        BuildStep ntlm(NtlmAuthConfig config);

        /**
         * Sets the auth type to AKAMAI_EDGEGRID with the provided configuration.
         */
        BuildStep akamaiEdgeGrid(AkamaiEdgeGridAuthConfig config);

        /**
         * Generic method to set type and config.
         * @deprecated Use the type-specific methods instead for type safety.
         */
        @Deprecated
        BuildStep type(LyshraOpenAppApiAuthType t, ILyshraOpenAppApiAuthConfig config);
    }

    public interface BuildStep {
        LyshraOpenAppApiAuthProfile build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            TypeStep,
            BuildStep {

        private String name;
        private LyshraOpenAppApiAuthType type;
        private ILyshraOpenAppApiAuthConfig config;

        @Override
        public TypeStep name(String n) {
            this.name = n;
            return this;
        }

        @Override
        public BuildStep noAuth() {
            this.type = LyshraOpenAppApiAuthType.NO_AUTH;
            this.config = NoAuthConfig.builder().build();
            return this;
        }

        @Override
        public BuildStep apiKey(ApiKeyAuthConfig config) {
            this.type = LyshraOpenAppApiAuthType.API_KEY;
            this.config = config;
            return this;
        }

        @Override
        public BuildStep bearerToken(BearerTokenAuthConfig config) {
            this.type = LyshraOpenAppApiAuthType.BEARER_TOKEN;
            this.config = config;
            return this;
        }

        @Override
        public BuildStep basicAuth(BasicAuthConfig config) {
            this.type = LyshraOpenAppApiAuthType.BASIC_AUTH;
            this.config = config;
            return this;
        }

        @Override
        public BuildStep digestAuth(DigestAuthConfig config) {
            this.type = LyshraOpenAppApiAuthType.DIGEST_AUTH;
            this.config = config;
            return this;
        }

        @Override
        public BuildStep oauth1(OAuth1AuthConfig config) {
            this.type = LyshraOpenAppApiAuthType.OAUTH1;
            this.config = config;
            return this;
        }

        @Override
        public BuildStep oauth2(OAuth2AuthConfig config) {
            this.type = LyshraOpenAppApiAuthType.OAUTH2;
            this.config = config;
            return this;
        }

        @Override
        public BuildStep hawk(HawkAuthConfig config) {
            this.type = LyshraOpenAppApiAuthType.HAWK;
            this.config = config;
            return this;
        }

        @Override
        public BuildStep awsSignature(AwsSignatureAuthConfig config) {
            this.type = LyshraOpenAppApiAuthType.AWS_SIGNATURE;
            this.config = config;
            return this;
        }

        @Override
        public BuildStep ntlm(NtlmAuthConfig config) {
            this.type = LyshraOpenAppApiAuthType.NTLM;
            this.config = config;
            return this;
        }

        @Override
        public BuildStep akamaiEdgeGrid(AkamaiEdgeGridAuthConfig config) {
            this.type = LyshraOpenAppApiAuthType.AKAMAI_EDGEGRID;
            this.config = config;
            return this;
        }

        @Override
        @Deprecated
        public BuildStep type(LyshraOpenAppApiAuthType t, ILyshraOpenAppApiAuthConfig config) {
            this.type = t;
            this.config = config;
            return this;
        }

        @Override
        public LyshraOpenAppApiAuthProfile build() {
            return new LyshraOpenAppApiAuthProfile(
                    name,
                    type,
                    config
            );
        }
    }
}
