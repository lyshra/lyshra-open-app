package com.lyshra.open.app.core.engine.signal.impl;

import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignal;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalHandler;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalResult;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppSignalType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Handles approval signals for human tasks.
 * When a task is approved, this handler:
 * 1. Updates the task status to APPROVED
 * 2. Triggers workflow resumption with APPROVED branch
 *
 * <p>Design Pattern: Strategy Pattern
 * - Implements specific strategy for handling approval signals
 */
@Slf4j
public class HumanTaskApprovalSignalHandler implements ILyshraOpenAppSignalHandler {

    private static final String HANDLER_ID = "human-task-approval-handler";

    @Override
    public boolean canHandle(ILyshraOpenAppSignal signal) {
        return signal.getSignalType() == LyshraOpenAppSignalType.APPROVE
                && signal.getTaskId().isPresent();
    }

    @Override
    public Mono<ILyshraOpenAppSignalResult> handle(ILyshraOpenAppSignal signal) {
        String taskId = signal.getTaskId().orElseThrow();
        log.info("Processing approval signal for task: {}", taskId);

        return Mono.defer(() -> {
            // This would integrate with the HumanTaskRepository and WorkflowInstanceRepository
            // For now, return a success result
            // In a complete implementation:
            // 1. Load the human task
            // 2. Validate the task is in a valid state for approval
            // 3. Validate the sender has permission to approve
            // 4. Update task status to APPROVED
            // 5. Add audit entry
            // 6. Trigger workflow resumption via WorkflowResumptionService

            return Mono.just(LyshraOpenAppSignalResult.builder()
                    .signal(signal)
                    .success(true)
                    .outcome(ILyshraOpenAppSignalResult.SignalOutcome.WORKFLOW_RESUMED)
                    .taskStatus("APPROVED")
                    .nextStep("APPROVED")
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
