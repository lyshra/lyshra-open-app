package com.lyshra.open.app.core.engine.assignment.strategies;

import com.lyshra.open.app.core.engine.assignment.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Load-balanced assignment strategy that assigns tasks to the user with the least workload.
 *
 * <p>This strategy queries the current task load of each eligible user and assigns
 * new tasks to minimize workload imbalance. It considers pending tasks and optionally
 * task priority weights.</p>
 *
 * <h2>Assignment Logic</h2>
 * <ol>
 *   <li>Get the pool of eligible users from config or context</li>
 *   <li>Filter out excluded users</li>
 *   <li>Query current task counts for each user via TaskLoadProvider</li>
 *   <li>Apply priority weighting if configured</li>
 *   <li>Select user(s) with lowest load below threshold</li>
 *   <li>Return selected user as primary assignee</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * AssignmentStrategyConfig.loadBalanced(List.of("user1", "user2", "user3"))
 * // With max tasks limit
 * AssignmentStrategyConfig.loadBalanced(List.of("user1", "user2"), 10)
 * }</pre>
 *
 * <h2>Load Calculation</h2>
 * <p>By default, load is calculated as the count of pending tasks. With priority
 * weighting enabled, each task contributes: 1 + (priority * priorityWeight).</p>
 */
public class LoadBalancedAssignmentStrategy implements ILyshraOpenAppTaskAssignmentStrategy {

    private static final String STRATEGY_NAME = "LOAD_BALANCED";
    private static final String DESCRIPTION = "Assigns tasks to user with least current workload";

    /**
     * Provider for querying user task loads.
     */
    private TaskLoadProvider taskLoadProvider;

    /**
     * Optional user availability checker.
     */
    private UserAvailabilityChecker availabilityChecker;

    /**
     * Interface for querying user task loads.
     */
    public interface TaskLoadProvider {
        /**
         * Gets the current task load for a user.
         *
         * @param userId the user ID
         * @param tenantId optional tenant ID for multi-tenant environments
         * @return the task load information
         */
        Mono<UserTaskLoad> getTaskLoad(String userId, String tenantId);

        /**
         * Gets task loads for multiple users.
         *
         * @param userIds the user IDs
         * @param tenantId optional tenant ID
         * @return map of user ID to task load
         */
        default Mono<Map<String, UserTaskLoad>> getTaskLoads(List<String> userIds, String tenantId) {
            return Flux.fromIterable(userIds)
                    .flatMap(userId -> getTaskLoad(userId, tenantId)
                            .map(load -> Map.entry(userId, load)))
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue);
        }
    }

    /**
     * Represents a user's current task load.
     */
    public record UserTaskLoad(
            String userId,
            int pendingTaskCount,
            int inProgressTaskCount,
            double weightedLoad,
            boolean available
    ) {
        public static UserTaskLoad empty(String userId) {
            return new UserTaskLoad(userId, 0, 0, 0.0, true);
        }

        public int totalActiveTaskCount() {
            return pendingTaskCount + inProgressTaskCount;
        }
    }

    /**
     * Interface for checking user availability.
     */
    @FunctionalInterface
    public interface UserAvailabilityChecker {
        Mono<Boolean> isAvailable(String userId, Map<String, Object> context);
    }

    public LoadBalancedAssignmentStrategy() {
        // Default provider returns 0 load for all users
        this.taskLoadProvider = (userId, tenantId) -> Mono.just(UserTaskLoad.empty(userId));
    }

    public LoadBalancedAssignmentStrategy(TaskLoadProvider provider) {
        this.taskLoadProvider = provider != null ? provider :
                (userId, tenantId) -> Mono.just(UserTaskLoad.empty(userId));
    }

    /**
     * Sets the task load provider.
     */
    public LoadBalancedAssignmentStrategy withTaskLoadProvider(TaskLoadProvider provider) {
        this.taskLoadProvider = provider;
        return this;
    }

    /**
     * Sets the user availability checker.
     */
    public LoadBalancedAssignmentStrategy withAvailabilityChecker(UserAvailabilityChecker checker) {
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
            return Mono.just(AssignmentResult.noAssignees("No users in load balancing pool"));
        }

        // Filter excluded users
        List<String> eligibleUsers = filterExcludedUsers(userPool, context);

        if (eligibleUsers.isEmpty()) {
            return Mono.just(AssignmentResult.noAssignees("All users in pool are excluded"));
        }

        // Get configuration
        AssignmentStrategyConfig config = context.getStrategyConfig();
        int maxTasksPerUser = config != null ? config.getMaxTasksPerUser() : 10;
        boolean considerPriority = config != null && config.isConsiderPriority();
        double priorityWeight = config != null ? config.getPriorityWeight() : 1.0;
        boolean considerAvailability = config != null && config.isConsiderAvailability();

        String tenantId = context.getTenantId();

        // Get task loads for all eligible users
        return taskLoadProvider.getTaskLoads(eligibleUsers, tenantId)
                .flatMap(loadMap -> {
                    // Filter by availability if needed
                    if (considerAvailability && availabilityChecker != null) {
                        return filterByAvailability(loadMap, context)
                                .map(filteredMap -> selectLeastLoadedUser(
                                        filteredMap, maxTasksPerUser, considerPriority, priorityWeight, context));
                    }

                    return Mono.just(selectLeastLoadedUser(
                            loadMap, maxTasksPerUser, considerPriority, priorityWeight, context));
                });
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

    private Mono<Map<String, UserTaskLoad>> filterByAvailability(
            Map<String, UserTaskLoad> loadMap, AssignmentContext context) {

        Map<String, Object> checkContext = Map.of(
                "workflowInstanceId", context.getWorkflowInstanceId() != null ? context.getWorkflowInstanceId() : "",
                "tenantId", context.getTenantId() != null ? context.getTenantId() : ""
        );

        return Flux.fromIterable(loadMap.entrySet())
                .flatMap(entry -> availabilityChecker.isAvailable(entry.getKey(), checkContext)
                        .map(available -> available ? entry : null))
                .filter(Objects::nonNull)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private AssignmentResult selectLeastLoadedUser(
            Map<String, UserTaskLoad> loadMap,
            int maxTasksPerUser,
            boolean considerPriority,
            double priorityWeight,
            AssignmentContext context) {

        if (loadMap.isEmpty()) {
            return AssignmentResult.noAssignees("No available users found");
        }

        // Calculate effective load for each user
        List<Map.Entry<String, Double>> userLoads = loadMap.entrySet().stream()
                .map(entry -> {
                    UserTaskLoad load = entry.getValue();
                    double effectiveLoad = considerPriority ?
                            load.weightedLoad() : load.totalActiveTaskCount();
                    return Map.entry(entry.getKey(), effectiveLoad);
                })
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toList());

        // Filter users below threshold
        List<String> eligibleByLoad = userLoads.stream()
                .filter(entry -> {
                    UserTaskLoad load = loadMap.get(entry.getKey());
                    return load.totalActiveTaskCount() < maxTasksPerUser;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (eligibleByLoad.isEmpty()) {
            // All users at capacity
            AssignmentStrategyConfig config = context.getStrategyConfig();
            if (config != null && config.isAllowDeferred()) {
                return AssignmentResult.deferred(
                        "All users at maximum task capacity",
                        java.time.Instant.now().plus(config.getDeferredRetryInterval())
                );
            }

            // Fall back to user with least load even if above threshold
            String leastLoaded = userLoads.get(0).getKey();
            return AssignmentResult.builder()
                    .resultType(AssignmentResult.ResultType.ASSIGNED)
                    .assignees(List.of(leastLoaded))
                    .primaryAssignee(leastLoaded)
                    .strategyName(STRATEGY_NAME)
                    .reason("Assigned to least loaded user (all users above threshold)")
                    .metadata(Map.of(
                            "currentLoad", loadMap.get(leastLoaded).totalActiveTaskCount(),
                            "maxTasksPerUser", maxTasksPerUser,
                            "aboveThreshold", true
                    ))
                    .build();
        }

        // Select user with least load
        String selectedUser = eligibleByLoad.get(0);
        UserTaskLoad selectedLoad = loadMap.get(selectedUser);

        return AssignmentResult.builder()
                .resultType(AssignmentResult.ResultType.ASSIGNED)
                .assignees(eligibleByLoad)
                .primaryAssignee(selectedUser)
                .strategyName(STRATEGY_NAME)
                .reason("Load-balanced assignment to user with least workload")
                .metadata(Map.of(
                        "selectedUserLoad", selectedLoad.totalActiveTaskCount(),
                        "maxTasksPerUser", maxTasksPerUser,
                        "eligibleUserCount", eligibleByLoad.size(),
                        "considerPriority", considerPriority
                ))
                .build();
    }

    @Override
    public Mono<ValidationResult> validateConfig(AssignmentStrategyConfig config) {
        if (config == null) {
            return Mono.just(ValidationResult.valid());
        }

        if (config.getStrategyType() != null &&
            config.getStrategyType() != AssignmentStrategyConfig.StrategyType.LOAD_BALANCED) {
            return Mono.just(ValidationResult.invalid(
                    "Config strategy type mismatch: expected LOAD_BALANCED, got " + config.getStrategyType()));
        }

        // Validate max tasks per user
        if (config.getMaxTasksPerUser() <= 0) {
            return Mono.just(ValidationResult.invalid("maxTasksPerUser must be positive"));
        }

        // Validate priority weight
        if (config.isConsiderPriority() && config.getPriorityWeight() < 0) {
            return Mono.just(ValidationResult.invalid("priorityWeight cannot be negative"));
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
        return 50;
    }
}
