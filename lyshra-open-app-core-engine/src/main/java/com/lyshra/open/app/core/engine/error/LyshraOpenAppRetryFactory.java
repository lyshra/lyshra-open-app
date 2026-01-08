package com.lyshra.open.app.core.engine.error;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppRetryPolicy;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Predicate;

public final class LyshraOpenAppRetryFactory {

    public static Retry buildRetry(ILyshraOpenAppRetryPolicy policy, Predicate<Throwable> retryPredicate) {
        if (policy == null || policy.getMaxAttempts() <= 0) {
            return Retry.max(0);
        }

        return Retry
                .backoff(policy.getMaxAttempts(), Duration.ofMillis(policy.getInitialIntervalMs()))
                .maxBackoff(Duration.ofMillis(policy.getMaxIntervalMs()))
                .multiplier(policy.getMultiplier())
                .jitter(0.2)
                .filter(retryPredicate)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }
}
