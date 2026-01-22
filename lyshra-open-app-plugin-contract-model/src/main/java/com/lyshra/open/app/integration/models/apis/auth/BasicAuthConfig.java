package com.lyshra.open.app.integration.models.apis.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Configuration for Basic Authentication.
 * Username and password are base64 encoded and sent in the Authorization header.
 */
@Data
public class BasicAuthConfig implements ILyshraOpenAppApiAuthConfig, Serializable {

    /**
     * The username for basic authentication.
     */
    @NotBlank(message = "{auth.basic.username.null}")
    @Size(min = 1, max = 500, message = "{auth.basic.username.invalid.length}")
    private final String username;

    /**
     * The password for basic authentication.
     */
    @NotNull(message = "{auth.basic.password.null}")
    @Size(max = 2000, message = "{auth.basic.password.invalid.length}")
    private final String password;

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.BASIC_AUTH;
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
        PasswordStep username(String username);
    }

    public interface PasswordStep {
        BuildStep password(String password);
    }

    public interface BuildStep {
        BasicAuthConfig build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            PasswordStep,
            BuildStep {

        private String username;
        private String password;

        @Override
        public PasswordStep username(String username) {
            this.username = username;
            return this;
        }

        @Override
        public BuildStep password(String password) {
            this.password = password;
            return this;
        }

        @Override
        public BasicAuthConfig build() {
            return new BasicAuthConfig(username, password);
        }
    }
}
