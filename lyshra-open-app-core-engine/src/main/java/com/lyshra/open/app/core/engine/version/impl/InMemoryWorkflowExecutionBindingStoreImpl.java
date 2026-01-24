package com.lyshra.open.app.core.engine.version.impl;

import com.lyshra.open.app.core.engine.version.IWorkflowExecutionBindingStore;
import com.lyshra.open.app.integration.contract.version.IWorkflowExecutionBinding;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of execution binding store.
 * Suitable for development and testing.
 * Production deployments should use a persistent implementation.
 *
 * <p>Design Pattern: Repository pattern with Singleton lifecycle.</p>
 */
@Slf4j
public class InMemoryWorkflowExecutionBindingStoreImpl implements IWorkflowExecutionBindingStore {

    private final Map<String, IWorkflowExecutionBinding> bindings;

    private InMemoryWorkflowExecutionBindingStoreImpl() {
        this.bindings = new ConcurrentHashMap<>();
    }

    private static final class SingletonHelper {
        private static final InMemoryWorkflowExecutionBindingStoreImpl INSTANCE =
                new InMemoryWorkflowExecutionBindingStoreImpl();
    }

    public static IWorkflowExecutionBindingStore getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public void save(IWorkflowExecutionBinding binding) {
        if (bindings.containsKey(binding.getExecutionId())) {
            throw new IllegalStateException(
                    "Execution binding already exists: " + binding.getExecutionId());
        }
        bindings.put(binding.getExecutionId(), binding);
        log.debug("Saved execution binding [{}] for workflow [{}] version [{}]",
                binding.getExecutionId(),
                binding.getWorkflowIdentifier().getWorkflowName(),
                binding.getBoundVersion().toVersionString());
    }

    @Override
    public void update(IWorkflowExecutionBinding binding) {
        if (!bindings.containsKey(binding.getExecutionId())) {
            throw new IllegalStateException(
                    "Execution binding not found: " + binding.getExecutionId());
        }
        bindings.put(binding.getExecutionId(), binding);
        log.debug("Updated execution binding [{}] state [{}] step [{}]",
                binding.getExecutionId(),
                binding.getState(),
                binding.getCurrentStepName());
    }

    @Override
    public Optional<IWorkflowExecutionBinding> findById(String executionId) {
        return Optional.ofNullable(bindings.get(executionId));
    }

    @Override
    public Collection<IWorkflowExecutionBinding> findByWorkflowId(String workflowId) {
        return bindings.values().stream()
                .filter(b -> workflowId.equals(b.getWorkflowIdentifier().getWorkflowName()))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<IWorkflowExecutionBinding> findByWorkflowIdAndVersion(String workflowId, IWorkflowVersion version) {
        return bindings.values().stream()
                .filter(b -> workflowId.equals(b.getWorkflowIdentifier().getWorkflowName()))
                .filter(b -> version.equals(b.getBoundVersion()))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<IWorkflowExecutionBinding> findActiveByWorkflowId(String workflowId) {
        return bindings.values().stream()
                .filter(b -> workflowId.equals(b.getWorkflowIdentifier().getWorkflowName()))
                .filter(b -> !b.isTerminal())
                .collect(Collectors.toList());
    }

    @Override
    public Collection<IWorkflowExecutionBinding> findActiveByWorkflowIdAndVersion(String workflowId, IWorkflowVersion version) {
        return bindings.values().stream()
                .filter(b -> workflowId.equals(b.getWorkflowIdentifier().getWorkflowName()))
                .filter(b -> version.equals(b.getBoundVersion()))
                .filter(b -> !b.isTerminal())
                .collect(Collectors.toList());
    }

    @Override
    public Collection<IWorkflowExecutionBinding> findByState(IWorkflowExecutionBinding.ExecutionState state) {
        return bindings.values().stream()
                .filter(b -> state == b.getState())
                .collect(Collectors.toList());
    }

    @Override
    public boolean delete(String executionId) {
        IWorkflowExecutionBinding removed = bindings.remove(executionId);
        if (removed != null) {
            log.debug("Deleted execution binding [{}]", executionId);
            return true;
        }
        return false;
    }

    @Override
    public long countActiveByVersion(String workflowId, IWorkflowVersion version) {
        return bindings.values().stream()
                .filter(b -> workflowId.equals(b.getWorkflowIdentifier().getWorkflowName()))
                .filter(b -> version.equals(b.getBoundVersion()))
                .filter(b -> !b.isTerminal())
                .count();
    }

    @Override
    public boolean hasExecutionsBoundToVersion(String workflowId, IWorkflowVersion version) {
        return bindings.values().stream()
                .filter(b -> workflowId.equals(b.getWorkflowIdentifier().getWorkflowName()))
                .anyMatch(b -> version.equals(b.getBoundVersion()));
    }
}
