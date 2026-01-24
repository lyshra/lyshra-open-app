package com.lyshra.open.app.core.engine.signal.impl;

import com.lyshra.open.app.core.engine.signal.ILyshraOpenAppSignalService;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignal;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalHandler;
import com.lyshra.open.app.integration.contract.signal.ILyshraOpenAppSignalResult;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppSignalType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of the signal service.
 * Routes signals to appropriate handlers and manages handler registration.
 *
 * <p>Design Pattern: Chain of Responsibility
 * - Handlers are tried in priority order until one can handle the signal
 */
@Slf4j
public class LyshraOpenAppSignalServiceImpl implements ILyshraOpenAppSignalService {

    private final Map<String, ILyshraOpenAppSignalHandler> handlers = new ConcurrentHashMap<>();
    private volatile List<ILyshraOpenAppSignalHandler> sortedHandlers = Collections.emptyList();

    private LyshraOpenAppSignalServiceImpl() {
        registerDefaultHandlers();
    }

    private static final class SingletonHelper {
        private static final ILyshraOpenAppSignalService INSTANCE = new LyshraOpenAppSignalServiceImpl();
    }

    public static ILyshraOpenAppSignalService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    private void registerDefaultHandlers() {
        registerHandler(new HumanTaskApprovalSignalHandler());
        registerHandler(new HumanTaskRejectionSignalHandler());
        registerHandler(new HumanTaskCompletionSignalHandler());
        registerHandler(new WorkflowPauseResumeSignalHandler());
        registerHandler(new TimeoutSignalHandler());
    }

    @Override
    public Mono<ILyshraOpenAppSignalResult> sendSignal(ILyshraOpenAppSignal signal) {
        log.info("Processing signal: type={}, workflowInstanceId={}, taskId={}",
                signal.getSignalType(),
                signal.getWorkflowInstanceId(),
                signal.getTaskId().orElse("N/A"));

        return Mono.defer(() -> {
            // Find handler that can process this signal
            for (ILyshraOpenAppSignalHandler handler : sortedHandlers) {
                if (handler.canHandle(signal)) {
                    log.debug("Signal handled by: {}", handler.getHandlerId());
                    return handler.handle(signal);
                }
            }

            // No handler found
            log.warn("No handler found for signal type: {}", signal.getSignalType());
            return Mono.just(createRejectedResult(signal, "No handler found for signal type"));
        });
    }

    @Override
    public Mono<ILyshraOpenAppSignalResult> approve(String taskId, String userId, Map<String, Object> payload) {
        ILyshraOpenAppSignal signal = createSignal(
                LyshraOpenAppSignalType.APPROVE,
                null,
                taskId,
                userId,
                payload != null ? payload : Collections.emptyMap()
        );
        return sendSignal(signal);
    }

    @Override
    public Mono<ILyshraOpenAppSignalResult> reject(String taskId, String userId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);

        ILyshraOpenAppSignal signal = createSignal(
                LyshraOpenAppSignalType.REJECT,
                null,
                taskId,
                userId,
                payload
        );
        return sendSignal(signal);
    }

    @Override
    public Mono<ILyshraOpenAppSignalResult> complete(String taskId, String userId, Map<String, Object> formData) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("formData", formData);

        ILyshraOpenAppSignal signal = createSignal(
                LyshraOpenAppSignalType.COMPLETE,
                null,
                taskId,
                userId,
                payload
        );
        return sendSignal(signal);
    }

    @Override
    public Mono<ILyshraOpenAppSignalResult> cancel(String taskId, String userId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);

        ILyshraOpenAppSignal signal = createSignal(
                LyshraOpenAppSignalType.CANCEL,
                null,
                taskId,
                userId,
                payload
        );
        return sendSignal(signal);
    }

    @Override
    public Mono<ILyshraOpenAppSignalResult> sendToWorkflow(
            String workflowInstanceId,
            LyshraOpenAppSignalType signalType,
            String senderId,
            Map<String, Object> payload) {

        ILyshraOpenAppSignal signal = createSignal(
                signalType,
                workflowInstanceId,
                null,
                senderId,
                payload
        );
        return sendSignal(signal);
    }

    @Override
    public synchronized void registerHandler(ILyshraOpenAppSignalHandler handler) {
        handlers.put(handler.getHandlerId(), handler);
        rebuildSortedHandlers();
        log.info("Registered signal handler: {}", handler.getHandlerId());
    }

    @Override
    public synchronized void unregisterHandler(String handlerId) {
        handlers.remove(handlerId);
        rebuildSortedHandlers();
        log.info("Unregistered signal handler: {}", handlerId);
    }

    private void rebuildSortedHandlers() {
        List<ILyshraOpenAppSignalHandler> sorted = new ArrayList<>(handlers.values());
        sorted.sort(Comparator.comparingInt(ILyshraOpenAppSignalHandler::getPriority));
        this.sortedHandlers = Collections.unmodifiableList(sorted);
    }

    private ILyshraOpenAppSignal createSignal(
            LyshraOpenAppSignalType type,
            String workflowInstanceId,
            String taskId,
            String senderId,
            Map<String, Object> payload) {

        return new LyshraOpenAppSignal(
                UUID.randomUUID().toString(),
                type,
                workflowInstanceId,
                taskId,
                payload,
                senderId,
                ILyshraOpenAppSignal.SenderType.USER,
                Instant.now()
        );
    }

    private ILyshraOpenAppSignalResult createRejectedResult(ILyshraOpenAppSignal signal, String reason) {
        return new LyshraOpenAppSignalResult(
                signal,
                false,
                ILyshraOpenAppSignalResult.SignalOutcome.REJECTED_VALIDATION_FAILED,
                reason,
                null,
                Instant.now()
        );
    }
}
