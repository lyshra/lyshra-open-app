package com.lyshra.open.app.integration.models.apis.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Configuration for OAuth 1.0 authentication.
 * Uses consumer key, consumer secret, access token, and token secret.
 */
@Data
public class OAuth1AuthConfig implements ILyshraOpenAppApiAuthConfig, Serializable {

    // Mandatory fields
    @NotBlank(message = "{auth.oauth1.consumerKey.null}")
    @Size(min = 1, max = 500, message = "{auth.oauth1.consumerKey.invalid.length}")
    private final String consumerKey;

    @NotBlank(message = "{auth.oauth1.consumerSecret.null}")
    @Size(min = 1, max = 500, message = "{auth.oauth1.consumerSecret.invalid.length}")
    private final String consumerSecret;

    // Optional fields
    private final String accessToken;
    private final String tokenSecret;
    private final OAuth1SignatureMethod signatureMethod;
    private final OAuth1ParamLocation addParamsTo;
    private final String realm;
    private final String version;
    private final boolean includeBodyHash;
    private final String timestamp;
    private final String nonce;

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.OAUTH1;
    }

    public enum OAuth1SignatureMethod {
        HMAC_SHA1,
        HMAC_SHA256,
        RSA_SHA1,
        PLAINTEXT
    }

    public enum OAuth1ParamLocation {
        HEADER,
        QUERY
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
        ConsumerSecretStep consumerKey(String consumerKey);
    }

    public interface ConsumerSecretStep {
        OptionalStep consumerSecret(String consumerSecret);
    }

    public interface OptionalStep extends BuildStep {
        OptionalStep accessToken(String accessToken);
        OptionalStep tokenSecret(String tokenSecret);
        OptionalStep signatureMethod(OAuth1SignatureMethod signatureMethod);
        OptionalStep addParamsTo(OAuth1ParamLocation addParamsTo);
        OptionalStep realm(String realm);
        OptionalStep version(String version);
        OptionalStep includeBodyHash(boolean includeBodyHash);
        OptionalStep timestamp(String timestamp);
        OptionalStep nonce(String nonce);
    }

    public interface BuildStep {
        OAuth1AuthConfig build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            ConsumerSecretStep,
            OptionalStep {

        private String consumerKey;
        private String consumerSecret;
        private String accessToken;
        private String tokenSecret;
        private OAuth1SignatureMethod signatureMethod = OAuth1SignatureMethod.HMAC_SHA1;
        private OAuth1ParamLocation addParamsTo = OAuth1ParamLocation.HEADER;
        private String realm;
        private String version = "1.0";
        private boolean includeBodyHash = false;
        private String timestamp;
        private String nonce;

        @Override
        public ConsumerSecretStep consumerKey(String consumerKey) {
            this.consumerKey = consumerKey;
            return this;
        }

        @Override
        public OptionalStep consumerSecret(String consumerSecret) {
            this.consumerSecret = consumerSecret;
            return this;
        }

        @Override
        public OptionalStep accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        @Override
        public OptionalStep tokenSecret(String tokenSecret) {
            this.tokenSecret = tokenSecret;
            return this;
        }

        @Override
        public OptionalStep signatureMethod(OAuth1SignatureMethod signatureMethod) {
            this.signatureMethod = signatureMethod;
            return this;
        }

        @Override
        public OptionalStep addParamsTo(OAuth1ParamLocation addParamsTo) {
            this.addParamsTo = addParamsTo;
            return this;
        }

        @Override
        public OptionalStep realm(String realm) {
            this.realm = realm;
            return this;
        }

        @Override
        public OptionalStep version(String version) {
            this.version = version;
            return this;
        }

        @Override
        public OptionalStep includeBodyHash(boolean includeBodyHash) {
            this.includeBodyHash = includeBodyHash;
            return this;
        }

        @Override
        public OptionalStep timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        @Override
        public OptionalStep nonce(String nonce) {
            this.nonce = nonce;
            return this;
        }

        @Override
        public OAuth1AuthConfig build() {
            return new OAuth1AuthConfig(
                    consumerKey, consumerSecret, accessToken, tokenSecret,
                    signatureMethod, addParamsTo, realm, version,
                    includeBodyHash, timestamp, nonce
            );
        }
    }
}
