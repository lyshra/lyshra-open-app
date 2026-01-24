package com.lyshra.open.app.core.engine.assignment.strategies;

import com.lyshra.open.app.core.engine.assignment.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Round-robin assignment strategy that distributes tasks evenly across assignees.
 *
 * <p>This strategy maintains a rotation state to ensure fair distribution of tasks
 * among all eligible users. The rotation state can be tracked globally or per
 * workflow/step using a configurable key.</p>
 *
 * <h2>Assignment Logic</h2>
 * <ol>
 *   <li>Get the pool of eligible users from config or context</li>
 *   <li>Filter out excluded and optionally unavailable users</li>
 *   <li>Get next user in rotation based on tracking key</li>
 *   <li>Increment rotation counter</li>
 *   <li>Return selected user as primary assignee</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * AssignmentStrategyConfig.roundRobin(List.of("user1", "user2", "user3"))
 * // With tracking key
 * AssignmentStrategyConfig.roundRobin(List.of("user1", "user2"), "workflow-123")
 * }</pre>
 *
 * <h2>State Management</h2>
 * <p>The strategy maintains rotation state in-memory by default. For distributed
 * environments, implement {@link RoundRobinStateStore} for external state management.</p>
 */
public class RoundRobinAssignmentStrategy implements ILyshraOpenAppTaskAssignmentStrategy {

    private static final String STRATEGY_NAME = "ROUND_ROBIN";
    private static final String DESCRIPTION = "Distributes tasks evenly across assignees using round-robin rotation";
    private static final String DEFAULT_KEY = "global";

    /**
     * In-memory state store for rotation indices.
     */
    private final Map<String, AtomicInteger> rotationState = new ConcurrentHashMap<>();

    /**
     * Optional external state store for distributed environments.
     */
    private RoundRobinStateStore externalStateStore;

    /**
     * Optional user availability checker.
     */
    private UserAvailabilityChecker availabilityChecker;

    /**
     * Interface for external round-robin state management.
     */
    public interface RoundRobinStateStore {
        /**
         * Gets and increments the rotation index for a key.
         *
         * @param key the rotation tracking key
         * @param poolSize the size of the user pool
         * @return the next index to use (0 to poolSize-1)
         */
        Mono<Integer> getAndIncrementIndex(String key, int poolSize);
    }

    /**
     * Interface for checking user availability.
     */
    @FunctionalInterface
    public interface UserAvailabilityChecker {
        /**
         * Checks if a user is available for assignment.
         *
         * @param userId the user ID
         * @param context optional context for the check
         * @return true if the user is available
         */
        Mono<Boolean> isAvailable(String userId, Map<String, Object> context);
    }

    public RoundRobinAssignmentStrategy() {
    }

    /**
     * Sets an external state store for distributed environments.
     */
    public RoundRobinAssignmentStrategy withStateStore(RoundRobinStateStore stateStore) {
        this.externalStateStore = stateStore;
        return this;
    }

    /**
     * Sets a user availability checker.
     */
    public RoundRobinAssignmentStrategy withAvailabilityChecker(UserAvailabilityChecker checker) {
        this.availabilityChecker = checker;
        return this;
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Mono<AssignmentResult> assign(AssignmentContext context) {
        // Get user pool
        List<String> userPool = getUserPool(context);

        if (userPool.isEmpty()) {
            return Mono.just(AssignmentResult.noAssignees("No users in rotation pool"));
        }

        // Filter excluded users
        List<String> eligibleUsers = filterExcludedUsers(userPool, context);

        if (eligibleUsers.isEmpty()) {
            return Mono.just(AssignmentResult.noAssignees("All users in pool are excluded"));
        }

        // Check availability if configured
        AssignmentStrategyConfig config = context.getStrategyConfig();
        boolean skipUnavailable = config != null && config.isSkipUnavailableUsers();

        if (skipUnavailable && availabilityChecker != null) {
            return filterAvailableUsers(eligibleUsers, context)
                    .flatMap(availableUsers -> selectNextUser(availableUsers, context));
        }

        return selectNextUser(eligibleUsers, context);
    }

    private List<String> getUserPool(AssignmentContext context) {
        AssignmentStrategyConfig config = context.getStrategyConfig();

        // Check config for assignee pool
        if (config != null && config.getAssigneePool() != null && !config.getAssigneePool().isEmpty()) {
            return new ArrayList<>(config.getAssigneePool());
        }

        // Check config for assignee users
        if (config != null && config.getAssigneeUsers() != null && !config.getAssigneeUsers().isEmpty()) {
            return new ArrayList<>(config.getAssigneeUsers());
        }

        // Fall back to context candidate users
        return new ArrayList<>(context.getCandidateUsers());
    }

    private List<String> filterExcludedUsers(List<String> users, AssignmentContext context) {
        return users.stream()
                .filter(user -> !context.isExcluded(user))
                .collect(Collectors.toList());
    }

    private Mono<List<String>> filterAvailableUsers(List<String> users, AssignmentContext context) {
        if (availabilityChecker == null) {
            return Mono.just(users);
        }

        Map<String, Object> checkContext = Map.of(
                "workflowInstanceId", context.getWorkflowInstanceId() != null ? context.getWorkflowInstanceId() : "",
                "tenantId", context.getTenantId() != null ? context.getTenantId() : ""
        );

        List<Mono<Boolean>> availabilityChecks = users.stream()
                .map(user -> availabilityChecker.isAvailable(user, checkContext))
                .collect(Collectors.toList());

        return Mono.zip(availabilityChecks, results -> {
            List<String> available = new ArrayList<>();
            for (int i = 0; i < results.length; i++) {
                if ((Boolean) results[i]) {
                    available.add(users.get(i));
                }
            }
            return available;
        });
    }

    private Mono<AssignmentResult> selectNextUser(List<String> eligibleUsers, AssignmentContext context) {
        if (eligibleUsers.isEmpty()) {
            return Mono.just(AssignmentResult.noAssignees("No available users in rotation pool"));
        }

        String trackingKey = getTrackingKey(context);

        return getNextIndex(trackingKey, eligibleUsers.size())
                .map(index -> {
                    String selectedUser = eligibleUsers.get(index);

                    return AssignmentResult.builder()
                            .resultType(AssignmentResult.ResultType.ASSIGNED)
                            .assignees(eligibleUsers) // Include all in pool
                            .primaryAssignee(selectedUser)
                            .strategyName(STRATEGY_NAME)
                            .reason("Round-robin assignment, selected user at index " + index)
                            .metadata(Map.of(
                                    "rotationKey", trackingKey,
                                    "selectedIndex", index,
                                    "poolSize", eligibleUsers.size()
                            ))
                            .build();
                });
    }

    private String getTrackingKey(AssignmentContext context) {
        AssignmentStrategyConfig config = context.getStrategyConfig();

        // Use config key if specified
        if (config != null && config.getRoundRobinKey() != null) {
            return config.getRoundRobinKey();
        }

        // Build key from context
        StringBuilder keyBuilder = new StringBuilder();

        if (context.getWorkflowDefinitionId() != null) {
            keyBuilder.append(context.getWorkflowDefinitionId());
        }

        if (context.getWorkflowStepId() != null) {
            keyBuilder.append(":").append(context.getWorkflowStepId());
        }

        if (context.getTenantId() != null) {
            keyBuilder.append(":").append(context.getTenantId());
        }

        return keyBuilder.length() > 0 ? keyBuilder.toString() : DEFAULT_KEY;
    }

    private Mono<Integer> getNextIndex(String key, int poolSize) {
        // Use external store if available
        if (externalStateStore != null) {
            return externalStateStore.getAndIncrementIndex(key, poolSize);
        }

        // Use in-memory state
        AtomicInteger counter = rotationState.computeIfAbsent(key, k -> new AtomicInteger(0));
        int index = counter.getAndIncrement() % poolSize;

        // Reset counter if it gets too large to avoid overflow
        if (counter.get() > 1_000_000) {
            counter.set(index + 1);
        }

        return Mono.just(index);
    }

    /**
     * Resets the rotation state for a specific key.
     */
    public void resetRotation(String key) {
        rotationState.remove(key);
    }

    /**
     * Resets all rotation state.
     */
    public void resetAllRotations() {
        rotationState.clear();
    }

    /**
     * Gets the current rotation index for a key (for testing/debugging).
     */
    public int getCurrentIndex(String key) {
        AtomicInteger counter = rotationState.get(key);
        return counter != null ? counter.get() : 0;
    }

    @Override
    public Mono<ValidationResult> validateConfig(AssignmentStrategyConfig config) {
        if (config == null) {
            return Mono.just(ValidationResult.valid());
        }

        if (config.getStrategyType() != null &&
            config.getStrategyType() != AssignmentStrategyConfig.StrategyType.ROUND_ROBIN) {
            return Mono.just(ValidationResult.invalid(
                    "Config strategy type mismatch: expected ROUND_ROBIN, got " + config.getStrategyType()));
        }

        // Validate that we have a user pool
        boolean hasPool = (config.getAssigneePool() != null && !config.getAssigneePool().isEmpty()) ||
                          (config.getAssigneeUsers() != null && !config.getAssigneeUsers().isEmpty());

        if (!hasPool) {
            return Mono.just(ValidationResult.invalid(
                    "Round-robin strategy requires assigneePool or assigneeUsers to be specified"));
        }

        return Mono.just(ValidationResult.valid());
    }

    @Override
    public boolean supportsReassignment() {
        return true;
    }

    @Override
    public boolean requiresUserAvailability() {
        return true;
    }

    @Override
    public int getPriority() {
        return 60;
    }
}
