package com.lyshra.open.app.integration.models.apis.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Configuration for NTLM Authentication.
 * Windows NT LAN Manager authentication protocol.
 */
@Data
public class NtlmAuthConfig implements ILyshraOpenAppApiAuthConfig, Serializable {

    // Mandatory fields
    @NotBlank(message = "{auth.ntlm.username.null}")
    @Size(min = 1, max = 500, message = "{auth.ntlm.username.invalid.length}")
    private final String username;

    @NotNull(message = "{auth.ntlm.password.null}")
    @Size(max = 2000, message = "{auth.ntlm.password.invalid.length}")
    private final String password;

    // Optional fields
    @Size(max = 200, message = "{auth.ntlm.domain.invalid.length}")
    private final String domain;

    @Size(max = 200, message = "{auth.ntlm.workstation.invalid.length}")
    private final String workstation;

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.NTLM;
    }

    // ---------------------------------------------------------
    // Builder Entry
    // ---------------------------------------------------------
    public static InitialStepBuilder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------
    // Step Interfaces (Mandatory: username -> password, then optional)
    // ---------------------------------------------------------
    public interface InitialStepBuilder {
        PasswordStep username(String username);
    }

    public interface PasswordStep {
        OptionalStep password(String password);
    }

    public interface OptionalStep extends BuildStep {
        OptionalStep domain(String domain);
        OptionalStep workstation(String workstation);
    }

    public interface BuildStep {
        NtlmAuthConfig build();
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
        private String domain;
        private String workstation;

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
        public OptionalStep domain(String domain) {
            this.domain = domain;
            return this;
        }

        @Override
        public OptionalStep workstation(String workstation) {
            this.workstation = workstation;
            return this;
        }

        @Override
        public NtlmAuthConfig build() {
            return new NtlmAuthConfig(username, password, domain, workstation);
        }
    }
}
