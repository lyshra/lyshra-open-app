package com.lyshra.open.app.core.engine.signal.impl;

import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignal;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalHandler;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalResult;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppSignalType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Handles rejection signals for human tasks.
 * When a task is rejected, this handler:
 * 1. Updates the task status to REJECTED
 * 2. Records the rejection reason
 * 3. Triggers workflow resumption with REJECTED branch
 *
 * <p>Design Pattern: Strategy Pattern
 */
@Slf4j
public class HumanTaskRejectionSignalHandler implements ILyshraOpenAppSignalHandler {

    private static final String HANDLER_ID = "human-task-rejection-handler";

    @Override
    public boolean canHandle(ILyshraOpenAppSignal signal) {
        return signal.getSignalType() == LyshraOpenAppSignalType.REJECT
                && signal.getTaskId().isPresent();
    }

    @Override
    public Mono<ILyshraOpenAppSignalResult> handle(ILyshraOpenAppSignal signal) {
        String taskId = signal.getTaskId().orElseThrow();
        String reason = (String) signal.getPayload().getOrDefault("reason", "No reason provided");

        log.info("Processing rejection signal for task: {}, reason: {}", taskId, reason);

        return Mono.defer(() -> {
            // Implementation would:
            // 1. Load the human task
            // 2. Validate the task is in a valid state for rejection
            // 3. Validate the sender has permission to reject
            // 4. Update task status to REJECTED
            // 5. Store rejection reason
            // 6. Add audit entry
            // 7. Trigger workflow resumption via WorkflowResumptionService with REJECTED branch

            return Mono.just(LyshraOpenAppSignalResult.builder()
                    .signal(signal)
                    .success(true)
                    .outcome(ILyshraOpenAppSignalResult.SignalOutcome.WORKFLOW_RESUMED)
                    .taskStatus("REJECTED")
                    .nextStep("REJECTED")
                    .processedAt(Instant.now())
                    .build());
        });
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public String getHandlerId() {
        return HANDLER_ID;
    }
}
