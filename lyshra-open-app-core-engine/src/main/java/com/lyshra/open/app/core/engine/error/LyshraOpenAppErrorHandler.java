package com.lyshra.open.app.core.engine.error;

import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepOnError;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepOnErrorConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppOnErrorStrategy;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.Map;
import java.util.Optional;

@Slf4j
public final class LyshraOpenAppErrorHandler {

    public static Mono<String> applyErrorHandling(
            Mono<String> source,
            ILyshraOpenAppWorkflowStepOnError workflowStepOnError) {

        Map<String, ILyshraOpenAppWorkflowStepOnErrorConfig> errorConfigMap = Optional
                .ofNullable(workflowStepOnError)
                .map(ILyshraOpenAppWorkflowStepOnError::getErrorConfigs)
                .orElse(Map.of());

        if (errorConfigMap.isEmpty()) {
            return source;
        }

        return source

                // handle retries
                .retryWhen(createRetryConfig(errorConfigMap))

                // handle other error strategies
                .onErrorResume(error -> LyshraOpenAppErrorHandler.handleError(error, errorConfigMap));
    }

    private static @NotNull Retry createRetryConfig(Map<String, ILyshraOpenAppWorkflowStepOnErrorConfig> errorConfigMap) {
        return Retry
                .from(retrySignals -> retrySignals
                        .flatMap(rs -> {
                            Throwable error = rs.failure();
                            String key = LyshraOpenAppErrorResolver.resolveKey(error, errorConfigMap);
                            ILyshraOpenAppWorkflowStepOnErrorConfig config = errorConfigMap.get(key);

                            if (config == null || config.getRetryPolicy() == null) {
                                log.info("No retry config found for error key: [{}]", key);
                                return Mono.error(error);
                            }

                            if (rs.totalRetries() < config.getRetryPolicy().getMaxAttempts()) {
                                log.info("Performing retry: [{}/{}], Using retry config: [{}]",
                                        (rs.totalRetries() + 1),
                                        config.getRetryPolicy().getMaxAttempts(),
                                        config.getRetryPolicy());
                            } else {
                                log.info("Maximum retries [{}/{}] performed! " +
                                        "Falling back to error handling strategy: [{}] ",
                                        rs.totalRetries(),
                                        config.getRetryPolicy().getMaxAttempts(),
                                        config.getStrategy()
                                );
                            }

                            // retry already filtered by key
                            Retry retry = LyshraOpenAppRetryFactory.buildRetry(config.getRetryPolicy(), err -> true);
                            return retry.generateCompanion(Flux.just(rs));
                        })
                );
    }

    private static Mono<String> handleError(
            Throwable error,
            Map<String, ILyshraOpenAppWorkflowStepOnErrorConfig> errorConfigMap) {

        String key = LyshraOpenAppErrorResolver.resolveKey(error, errorConfigMap);
        ILyshraOpenAppWorkflowStepOnErrorConfig config = errorConfigMap.get(key);

        if (config == null) {
            return Mono.error(error);
        }

        LyshraOpenAppOnErrorStrategy strategy = config.getStrategy();
        if (config.getStrategy() == LyshraOpenAppOnErrorStrategy.USE_FALLBACK_STEP) {
            log.info("Error handling strategy: [{}], Fallback workflow Step: [{}]", config.getStrategy(), config.getFallbackWorkflowStep());
        } else {
            log.info("Error handling strategy: [{}]", config.getStrategy());
        }
        return switch (strategy) {
            case END_WORKFLOW -> Mono.empty();
            case USE_FALLBACK_STEP -> Mono.just(config.getFallbackWorkflowStep());
            case ABORT_WORKFLOW -> Mono.error(error);
        };
    }

}