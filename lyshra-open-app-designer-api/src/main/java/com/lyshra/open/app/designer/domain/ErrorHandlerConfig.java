package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * Configuration for handling specific error types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorHandlerConfig {

    private String errorKey;
    private ErrorStrategy strategy;
    private String fallbackStepId;
    private RetryPolicy retryPolicy;

    /**
     * Gets the fallback step ID as Optional.
     *
     * @return Optional containing the fallback step ID
     */
    public Optional<String> getFallbackStepIdOptional() {
        return Optional.ofNullable(fallbackStepId);
    }

    /**
     * Gets the retry policy as Optional.
     *
     * @return Optional containing the retry policy
     */
    public Optional<RetryPolicy> getRetryPolicyOptional() {
        return Optional.ofNullable(retryPolicy);
    }
}
