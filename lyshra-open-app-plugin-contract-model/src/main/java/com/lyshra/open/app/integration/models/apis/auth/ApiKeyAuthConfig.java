package com.lyshra.open.app.integration.models.apis.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Configuration for API Key authentication.
 * The API key can be sent as a header or query parameter.
 */
@Data
public class ApiKeyAuthConfig implements ILyshraOpenAppApiAuthConfig, Serializable {

    /**
     * The name of the header or query parameter for the API key.
     * Common values: "X-API-Key", "api_key", "apikey"
     */
    @NotBlank(message = "{auth.apikey.key.null}")
    @Size(min = 1, max = 200, message = "{auth.apikey.key.invalid.length}")
    private final String key;

    /**
     * The API key value.
     */
    @NotBlank(message = "{auth.apikey.value.null}")
    @Size(min = 1, max = 2000, message = "{auth.apikey.value.invalid.length}")
    private final String value;

    /**
     * Where to add the API key: "header" or "query".
     */
    @NotNull(message = "{auth.apikey.addTo.null}")
    private final ApiKeyLocation addTo;

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.API_KEY;
    }

    public enum ApiKeyLocation {
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
    // Step Interfaces (Mandatory fields first, then build)
    // ---------------------------------------------------------
    public interface InitialStepBuilder {
        ValueStep key(String key);
    }

    public interface ValueStep {
        AddToStep value(String value);
    }

    public interface AddToStep {
        BuildStep addTo(ApiKeyLocation addTo);
    }

    public interface BuildStep {
        ApiKeyAuthConfig build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            ValueStep,
            AddToStep,
            BuildStep {

        private String key;
        private String value;
        private ApiKeyLocation addTo;

        @Override
        public ValueStep key(String key) {
            this.key = key;
            return this;
        }

        @Override
        public AddToStep value(String value) {
            this.value = value;
            return this;
        }

        @Override
        public BuildStep addTo(ApiKeyLocation addTo) {
            this.addTo = addTo;
            return this;
        }

        @Override
        public ApiKeyAuthConfig build() {
            return new ApiKeyAuthConfig(key, value, addTo);
        }
    }
}
