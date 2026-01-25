package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Optional;

/**
 * Error handling configuration for a workflow step.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepErrorHandling {

    private ErrorStrategy defaultStrategy;
    private String fallbackStepId;
    private RetryPolicy retryPolicy;
    private Map<String, ErrorHandlerConfig> errorHandlers;

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
