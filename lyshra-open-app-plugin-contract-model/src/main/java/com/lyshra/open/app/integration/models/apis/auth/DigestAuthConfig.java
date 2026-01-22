package com.lyshra.open.app.integration.models.apis.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Configuration for Digest Authentication.
 * A more secure alternative to Basic Auth that doesn't send passwords in plain text.
 */
@Data
public class DigestAuthConfig implements ILyshraOpenAppApiAuthConfig, Serializable {

    // Mandatory fields
    @NotBlank(message = "{auth.digest.username.null}")
    @Size(min = 1, max = 500, message = "{auth.digest.username.invalid.length}")
    private final String username;

    @NotNull(message = "{auth.digest.password.null}")
    @Size(max = 2000, message = "{auth.digest.password.invalid.length}")
    private final String password;

    // Optional fields (usually provided by server in 401 response)
    private final String realm;
    private final String nonce;
    private final String algorithm;
    private final String qop;
    private final String nonceCount;
    private final String clientNonce;
    private final String opaque;

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.DIGEST_AUTH;
    }

    // ---------------------------------------------------------
    // Builder Entry
    // ---------------------------------------------------------
    public static InitialStepBuilder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------
    // Step Interfaces (Mandatory first, then optional, then build)
    // ---------------------------------------------------------
    public interface InitialStepBuilder {
        PasswordStep username(String username);
    }

    public interface PasswordStep {
        OptionalStep password(String password);
    }

    public interface OptionalStep extends BuildStep {
        OptionalStep realm(String realm);
        OptionalStep nonce(String nonce);
        OptionalStep algorithm(String algorithm);
        OptionalStep qop(String qop);
        OptionalStep nonceCount(String nonceCount);
        OptionalStep clientNonce(String clientNonce);
        OptionalStep opaque(String opaque);
    }

    public interface BuildStep {
        DigestAuthConfig build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            PasswordStep,
            OptionalStep {

        private String username;
        private String password;
        private String realm;
        private String nonce;
        private String algorithm;
        private String qop;
        private String nonceCount;
        private String clientNonce;
        private String opaque;

        @Override
        public PasswordStep username(String username) {
            this.username = username;
            return this;
        }

        @Override
        public OptionalStep password(String password) {
            this.password = password;
            return this;
        }

        @Override
        public OptionalStep realm(String realm) {
            this.realm = realm;
            return this;
        }

        @Override
        public OptionalStep nonce(String nonce) {
            this.nonce = nonce;
            return this;
        }

        @Override
        public OptionalStep algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        @Override
        public OptionalStep qop(String qop) {
            this.qop = qop;
            return this;
        }

        @Override
        public OptionalStep nonceCount(String nonceCount) {
            this.nonceCount = nonceCount;
            return this;
        }

        @Override
        public OptionalStep clientNonce(String clientNonce) {
            this.clientNonce = clientNonce;
            return this;
        }

        @Override
        public OptionalStep opaque(String opaque) {
            this.opaque = opaque;
            return this;
        }

        @Override
        public DigestAuthConfig build() {
            return new DigestAuthConfig(
                    username, password, realm, nonce, algorithm,
                    qop, nonceCount, clientNonce, opaque
            );
        }
    }
}
