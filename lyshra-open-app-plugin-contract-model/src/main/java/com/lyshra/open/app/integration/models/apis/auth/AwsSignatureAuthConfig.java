package com.lyshra.open.app.integration.models.apis.auth;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Configuration for AWS Signature authentication.
 * Used for authenticating requests to AWS services.
 * Supports both AWS Signature Version 4.
 */
@Data
public class AwsSignatureAuthConfig implements ILyshraOpenAppApiAuthConfig, Serializable {

    // Mandatory fields
    @NotBlank(message = "{auth.aws.accessKey.null}")
    @Size(min = 1, max = 200, message = "{auth.aws.accessKey.invalid.length}")
    private final String accessKey;

    @NotBlank(message = "{auth.aws.secretKey.null}")
    @Size(min = 1, max = 500, message = "{auth.aws.secretKey.invalid.length}")
    private final String secretKey;

    @NotBlank(message = "{auth.aws.region.null}")
    @Size(min = 1, max = 100, message = "{auth.aws.region.invalid.length}")
    private final String region;

    @NotBlank(message = "{auth.aws.serviceName.null}")
    @Size(min = 1, max = 100, message = "{auth.aws.serviceName.invalid.length}")
    private final String serviceName;

    // Optional fields
    @Size(max = 1000, message = "{auth.aws.sessionToken.invalid.length}")
    private final String sessionToken;

    private final boolean addAuthDataToHeaders;

    private final boolean addAuthDataToQuery;

    @Override
    public LyshraOpenAppApiAuthType getAuthType() {
        return LyshraOpenAppApiAuthType.AWS_SIGNATURE;
    }

    // ---------------------------------------------------------
    // Builder Entry
    // ---------------------------------------------------------
    public static InitialStepBuilder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------
    // Step Interfaces (Mandatory: accessKey -> secretKey -> region -> serviceName, then optional)
    // ---------------------------------------------------------
    public interface InitialStepBuilder {
        SecretKeyStep accessKey(String accessKey);
    }

    public interface SecretKeyStep {
        RegionStep secretKey(String secretKey);
    }

    public interface RegionStep {
        ServiceNameStep region(String region);
    }

    public interface ServiceNameStep {
        OptionalStep serviceName(String serviceName);
    }

    public interface OptionalStep extends BuildStep {
        OptionalStep sessionToken(String sessionToken);
        OptionalStep addAuthDataToHeaders(boolean addAuthDataToHeaders);
        OptionalStep addAuthDataToQuery(boolean addAuthDataToQuery);
    }

    public interface BuildStep {
        AwsSignatureAuthConfig build();
    }

    // ---------------------------------------------------------
    // Builder Implementation
    // ---------------------------------------------------------
    private static class Builder implements
            InitialStepBuilder,
            SecretKeyStep,
            RegionStep,
            ServiceNameStep,
            OptionalStep {

        private String accessKey;
        private String secretKey;
        private String region;
        private String serviceName;
        private String sessionToken;
        private boolean addAuthDataToHeaders = true;
        private boolean addAuthDataToQuery = false;

        @Override
        public SecretKeyStep accessKey(String accessKey) {
            this.accessKey = accessKey;
            return this;
        }

        @Override
        public RegionStep secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        @Override
        public ServiceNameStep region(String region) {
            this.region = region;
            return this;
        }

        @Override
        public OptionalStep serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        @Override
        public OptionalStep sessionToken(String sessionToken) {
            this.sessionToken = sessionToken;
            return this;
        }

        @Override
        public OptionalStep addAuthDataToHeaders(boolean addAuthDataToHeaders) {
            this.addAuthDataToHeaders = addAuthDataToHeaders;
            return this;
        }

        @Override
        public OptionalStep addAuthDataToQuery(boolean addAuthDataToQuery) {
            this.addAuthDataToQuery = addAuthDataToQuery;
            return this;
        }

        @Override
        public AwsSignatureAuthConfig build() {
            return new AwsSignatureAuthConfig(
                    accessKey, secretKey, region, serviceName,
                    sessionToken, addAuthDataToHeaders, addAuthDataToQuery
            );
        }
    }
}
