package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Retry policy configuration for error handling.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryPolicy {

    private int maxAttempts;
    private long delayMs;
    private double backoffMultiplier;

    /**
     * Creates a default retry policy with 3 attempts, 1 second delay, and 2x backoff.
     *
     * @return the default retry policy
     */
    public static RetryPolicy defaultPolicy() {
        return RetryPolicy.builder()
                .maxAttempts(3)
                .delayMs(1000)
                .backoffMultiplier(2.0)
                .build();
    }
}
