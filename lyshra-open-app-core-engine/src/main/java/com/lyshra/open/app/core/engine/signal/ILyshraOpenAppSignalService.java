package com.lyshra.open.app.core.engine.signal;

import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignal;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalHandler;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalResult;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppSignalType;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Service interface for sending and processing signals.
 * Provides the main entry point for external systems to interact
 * with waiting workflows and human tasks.
 *
 * <p>Design Pattern: Facade Pattern
 * - Provides a simplified interface to the signal subsystem
 * - Hides complexity of handler selection and routing
 */
public interface ILyshraOpenAppSignalService {

    /**
     * Sends a signal to a workflow instance or human task.
     *
     * @param signal the signal to send
     * @return the result of signal processing
     */
    Mono<ILyshraOpenAppSignalResult> sendSignal(ILyshraOpenAppSignal signal);

    /**
     * Convenience method to approve a human task.
     *
     * @param taskId the task identifier
     * @param userId the user approving
     * @param payload optional additional data
     * @return the signal result
     */
    Mono<ILyshraOpenAppSignalResult> approve(String taskId, String userId, Map<String, Object> payload);

    /**
     * Convenience method to reject a human task.
     *
     * @param taskId the task identifier
     * @param userId the user rejecting
     * @param reason the rejection reason
     * @return the signal result
     */
    Mono<ILyshraOpenAppSignalResult> reject(String taskId, String userId, String reason);

    /**
     * Convenience method to complete a human task with data.
     *
     * @param taskId the task identifier
     * @param userId the user completing
     * @param formData the form submission data
     * @return the signal result
     */
    Mono<ILyshraOpenAppSignalResult> complete(String taskId, String userId, Map<String, Object> formData);

    /**
     * Convenience method to cancel a human task.
     *
     * @param taskId the task identifier
     * @param userId the user cancelling
     * @param reason the cancellation reason
     * @return the signal result
     */
    Mono<ILyshraOpenAppSignalResult> cancel(String taskId, String userId, String reason);

    /**
     * Sends a custom signal to a workflow instance.
     *
     * @param workflowInstanceId the workflow instance identifier
     * @param signalType the type of signal
     * @param senderId the sender identifier
     * @param payload the signal payload
     * @return the signal result
     */
    Mono<ILyshraOpenAppSignalResult> sendToWorkflow(
            String workflowInstanceId,
            LyshraOpenAppSignalType signalType,
            String senderId,
            Map<String, Object> payload);

    /**
     * Registers a signal handler.
     *
     * @param handler the handler to register
     */
    void registerHandler(ILyshraOpenAppSignalHandler handler);

    /**
     * Unregisters a signal handler.
     *
     * @param handlerId the handler identifier to unregister
     */
    void unregisterHandler(String handlerId);
}
