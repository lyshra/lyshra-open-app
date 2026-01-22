package com.lyshra.open.app.integration.models.apis.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Configuration for Akamai EdgeGrid authentication.
 * Used for authenticating requests to Akamai APIs.
 */
@Data
public class AkamaiEdgeGridAuthConfig implements ILyshraOpenAppApiAuthConfig, Serializable {

    // Mandatory fields
    @NotBlank(message = "{auth.akamai.clientToken.null}")
    @Size(min = 1, max = 500, message = "{auth.akamai.clientToken.invalid.length}")
    private final String clientToken;

    @NotBlank(message = "{auth.akamai.clientSecret.null}")
    @Size(min = 1, max = 500, message = "{auth.akamai.clientSecret.invalid.length}")
    private final String clientSecret;

    @NotBlank(message = "{auth.akamai.accessToken.null}")
    @Size(min = 1, max = 500, message = "{auth.akamai.accessToken.invalid.length}")
    private final String accessToken;

    // Optional fields
    @Size(max = 500, message = "{auth.akamai.baseUrl.invalid.length}")
    private final String baseUrl;

    @Size(max = 100, message = "{auth.akamai.nonce.invalid.length}")
    private final String nonce;

    @Size(max = 50, message = "{auth.akamai.timestamp.invalid.length}")
    private final String timestamp;

    private final int maxBodySize;

    @Size(max = 500, message = "{auth.akamai.headersToSign.invalid.length}")
    private final String headersToSign;

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.AKAMAI_EDGEGRID;
    }

    // ---------------------------------------------------------
    // Builder Entry
    // ---------------------------------------------------------
    public static InitialStepBuilder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------
    // Step Interfaces (Mandatory: clientToken -> clientSecret -> accessToken, then optional)
    // ---------------------------------------------------------
    public interface InitialStepBuilder {
        ClientSecretStep clientToken(String clientToken);
    }

    public interface ClientSecretStep {
        AccessTokenStep clientSecret(String clientSecret);
    }

    public interface AccessTokenStep {
        OptionalStep accessToken(String accessToken);
    }

    public interface OptionalStep extends BuildStep {
        OptionalStep baseUrl(String baseUrl);
        OptionalStep nonce(String nonce);
        OptionalStep timestamp(String timestamp);
        OptionalStep maxBodySize(int maxBodySize);
        OptionalStep headersToSign(String headersToSign);
    }

    public interface BuildStep {
        AkamaiEdgeGridAuthConfig build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            ClientSecretStep,
            AccessTokenStep,
            OptionalStep {

        private String clientToken;
        private String clientSecret;
        private String accessToken;
        private String baseUrl;
        private String nonce;
        private String timestamp;
        private int maxBodySize = 131072;
        private String headersToSign;

        @Override
        public ClientSecretStep clientToken(String clientToken) {
            this.clientToken = clientToken;
            return this;
        }

        @Override
        public AccessTokenStep clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        @Override
        public OptionalStep accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        @Override
        public OptionalStep baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        @Override
        public OptionalStep nonce(String nonce) {
            this.nonce = nonce;
            return this;
        }

        @Override
        public OptionalStep timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        @Override
        public OptionalStep maxBodySize(int maxBodySize) {
            this.maxBodySize = maxBodySize;
            return this;
        }

        @Override
        public OptionalStep headersToSign(String headersToSign) {
            this.headersToSign = headersToSign;
            return this;
        }

        @Override
        public AkamaiEdgeGridAuthConfig build() {
            return new AkamaiEdgeGridAuthConfig(
                    clientToken, clientSecret, accessToken, baseUrl,
                    nonce, timestamp, maxBodySize, headersToSign
            );
        }
    }
}
