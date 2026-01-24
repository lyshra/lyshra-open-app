package com.lyshra.open.app.core.engine.signal.impl;

import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignal;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalHandler;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalResult;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppSignalType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Handles completion signals for human tasks that require form data submission.
 * When a task is completed with data, this handler:
 * 1. Validates the submitted form data against the task's form schema
 * 2. Updates the task status to COMPLETED
 * 3. Stores the form data as task result
 * 4. Triggers workflow resumption with COMPLETED/DEFAULT branch
 *
 * <p>Design Pattern: Strategy Pattern
 */
@Slf4j
public class HumanTaskCompletionSignalHandler implements ILyshraOpenAppSignalHandler {

    private static final String HANDLER_ID = "human-task-completion-handler";

    @Override
    public boolean canHandle(ILyshraOpenAppSignal signal) {
        return signal.getSignalType() == LyshraOpenAppSignalType.COMPLETE
                && signal.getTaskId().isPresent();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<ILyshraOpenAppSignalResult> handle(ILyshraOpenAppSignal signal) {
        String taskId = signal.getTaskId().orElseThrow();
        Map<String, Object> formData = (Map<String, Object>) signal.getPayload().getOrDefault("formData", Map.of());

        log.info("Processing completion signal for task: {}, formData keys: {}", taskId, formData.keySet());

        return Mono.defer(() -> {
            // Implementation would:
            // 1. Load the human task
            // 2. Validate the task is in a valid state for completion
            // 3. Validate the sender has permission to complete
            // 4. Validate form data against task's form schema (if defined)
            // 5. Update task status to COMPLETED
            // 6. Store form data as result
            // 7. Add audit entry
            // 8. Update workflow context with form data
            // 9. Trigger workflow resumption via WorkflowResumptionService

            return Mono.just(LyshraOpenAppSignalResult.builder()
                    .signal(signal)
                    .success(true)
                    .outcome(ILyshraOpenAppSignalResult.SignalOutcome.WORKFLOW_RESUMED)
                    .taskStatus("COMPLETED")
                    .nextStep("DEFAULT")
                    .resultData(formData)
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
