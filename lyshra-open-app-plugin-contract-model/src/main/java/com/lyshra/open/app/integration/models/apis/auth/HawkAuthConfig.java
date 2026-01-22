package com.lyshra.open.app.integration.models.apis.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Configuration for Hawk Authentication.
 * A HTTP authentication scheme using a message authentication code (MAC).
 */
@Data
public class HawkAuthConfig implements ILyshraOpenAppApiAuthConfig, Serializable {

    // Mandatory fields
    @NotBlank(message = "{auth.hawk.authId.null}")
    @Size(min = 1, max = 500, message = "{auth.hawk.authId.invalid.length}")
    private final String hawkAuthId;

    @NotBlank(message = "{auth.hawk.authKey.null}")
    @Size(min = 1, max = 500, message = "{auth.hawk.authKey.invalid.length}")
    private final String hawkAuthKey;

    @NotNull(message = "{auth.hawk.algorithm.null}")
    private final HawkAlgorithm algorithm;

    // Optional fields
    @Size(max = 500, message = "{auth.hawk.user.invalid.length}")
    private final String user;

    @Size(max = 100, message = "{auth.hawk.nonce.invalid.length}")
    private final String nonce;

    @Size(max = 1000, message = "{auth.hawk.extraData.invalid.length}")
    private final String extraData;

    @Size(max = 200, message = "{auth.hawk.appId.invalid.length}")
    private final String appId;

    @Size(max = 200, message = "{auth.hawk.delegation.invalid.length}")
    private final String delegation;

    private final Long timestamp;

    private final boolean includePayloadHash;

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.HAWK;
    }

    public enum HawkAlgorithm {
        SHA256,
        SHA1
    }

    // ---------------------------------------------------------
    // Builder Entry
    // ---------------------------------------------------------
    public static InitialStepBuilder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------
    // Step Interfaces (Mandatory first: authId -> authKey -> algorithm, then optional)
    // ---------------------------------------------------------
    public interface InitialStepBuilder {
        AuthKeyStep hawkAuthId(String hawkAuthId);
    }

    public interface AuthKeyStep {
        AlgorithmStep hawkAuthKey(String hawkAuthKey);
    }

    public interface AlgorithmStep {
        OptionalStep algorithm(HawkAlgorithm algorithm);
    }

    public interface OptionalStep extends BuildStep {
        OptionalStep user(String user);
        OptionalStep nonce(String nonce);
        OptionalStep extraData(String extraData);
        OptionalStep appId(String appId);
        OptionalStep delegation(String delegation);
        OptionalStep timestamp(Long timestamp);
        OptionalStep includePayloadHash(boolean includePayloadHash);
    }

    public interface BuildStep {
        HawkAuthConfig build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            AuthKeyStep,
            AlgorithmStep,
            OptionalStep {

        private String hawkAuthId;
        private String hawkAuthKey;
        private HawkAlgorithm algorithm;
        private String user;
        private String nonce;
        private String extraData;
        private String appId;
        private String delegation;
        private Long timestamp;
        private boolean includePayloadHash = false;

        @Override
        public AuthKeyStep hawkAuthId(String hawkAuthId) {
            this.hawkAuthId = hawkAuthId;
            return this;
        }

        @Override
        public AlgorithmStep hawkAuthKey(String hawkAuthKey) {
            this.hawkAuthKey = hawkAuthKey;
            return this;
        }

        @Override
        public OptionalStep algorithm(HawkAlgorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        @Override
        public OptionalStep user(String user) {
            this.user = user;
            return this;
        }

        @Override
        public OptionalStep nonce(String nonce) {
            this.nonce = nonce;
            return this;
        }

        @Override
        public OptionalStep extraData(String extraData) {
            this.extraData = extraData;
            return this;
        }

        @Override
        public OptionalStep appId(String appId) {
            this.appId = appId;
            return this;
        }

        @Override
        public OptionalStep delegation(String delegation) {
            this.delegation = delegation;
            return this;
        }

        @Override
        public OptionalStep timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        @Override
        public OptionalStep includePayloadHash(boolean includePayloadHash) {
            this.includePayloadHash = includePayloadHash;
            return this;
        }

        @Override
        public HawkAuthConfig build() {
            return new HawkAuthConfig(
                    hawkAuthId, hawkAuthKey, algorithm, user,
                    nonce, extraData, appId, delegation,
                    timestamp, includePayloadHash
            );
        }
    }
}
