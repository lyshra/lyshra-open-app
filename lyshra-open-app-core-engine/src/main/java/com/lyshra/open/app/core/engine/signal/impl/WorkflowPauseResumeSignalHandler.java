package com.lyshra.open.app.core.engine.signal.impl;

import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignal;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalHandler;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalResult;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppSignalType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;

/**
 * Handles pause and resume signals for workflow instances.
 * This allows administrative control over workflow execution.
 *
 * <p>PAUSE: Suspends workflow execution at the current step (different from WAITING)
 * <p>RESUME: Continues a paused workflow from where it was suspended
 *
 * <p>Design Pattern: Strategy Pattern
 */
@Slf4j
public class WorkflowPauseResumeSignalHandler implements ILyshraOpenAppSignalHandler {

    private static final String HANDLER_ID = "workflow-pause-resume-handler";
    private static final Set<LyshraOpenAppSignalType> SUPPORTED_TYPES = Set.of(
            LyshraOpenAppSignalType.PAUSE,
            LyshraOpenAppSignalType.RESUME
    );

    @Override
    public boolean canHandle(ILyshraOpenAppSignal signal) {
        return SUPPORTED_TYPES.contains(signal.getSignalType())
                && signal.getWorkflowInstanceId() != null
                && !signal.getWorkflowInstanceId().isBlank();
    }

    @Override
    public Mono<ILyshraOpenAppSignalResult> handle(ILyshraOpenAppSignal signal) {
        String workflowInstanceId = signal.getWorkflowInstanceId();
        LyshraOpenAppSignalType signalType = signal.getSignalType();

        log.info("Processing {} signal for workflow instance: {}", signalType, workflowInstanceId);

        return Mono.defer(() -> {
            if (signalType == LyshraOpenAppSignalType.PAUSE) {
                return handlePause(signal, workflowInstanceId);
            } else {
                return handleResume(signal, workflowInstanceId);
            }
        });
    }

    private Mono<ILyshraOpenAppSignalResult> handlePause(ILyshraOpenAppSignal signal, String workflowInstanceId) {
        // Implementation would:
        // 1. Load the workflow instance
        // 2. Validate it's in RUNNING state
        // 3. Update state to PAUSED
        // 4. Add checkpoint
        // 5. Interrupt current step execution if possible

        return Mono.just(LyshraOpenAppSignalResult.builder()
                .signal(signal)
                .success(true)
                .outcome(ILyshraOpenAppSignalResult.SignalOutcome.ACCEPTED)
                .workflowState("PAUSED")
                .processedAt(Instant.now())
                .build());
    }

    private Mono<ILyshraOpenAppSignalResult> handleResume(ILyshraOpenAppSignal signal, String workflowInstanceId) {
        // Implementation would:
        // 1. Load the workflow instance
        // 2. Validate it's in PAUSED state (not WAITING - waiting requires task resolution)
        // 3. Update state to RUNNING
        // 4. Restart execution from current step
        // 5. Add checkpoint

        return Mono.just(LyshraOpenAppSignalResult.builder()
                .signal(signal)
                .success(true)
                .outcome(ILyshraOpenAppSignalResult.SignalOutcome.WORKFLOW_RESUMED)
                .workflowState("RUNNING")
                .processedAt(Instant.now())
                .build());
    }

    @Override
    public int getPriority() {
        return 5; // Higher priority than task handlers
    }

    @Override
    public String getHandlerId() {
        return HANDLER_ID;
    }
}
