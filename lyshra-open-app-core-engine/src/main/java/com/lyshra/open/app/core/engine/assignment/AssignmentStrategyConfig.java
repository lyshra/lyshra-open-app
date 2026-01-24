package com.lyshra.open.app.core.engine.assignment;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for task assignment strategies.
 *
 * <p>This class holds all the configuration parameters that can be used
 * by different assignment strategies to customize their behavior.</p>
 *
 * <h2>Configuration Options</h2>
 * <ul>
 *   <li><b>Strategy Selection</b> - Which strategy to use</li>
 *   <li><b>Candidate Configuration</b> - Users, groups, roles to consider</li>
 *   <li><b>Exclusion Rules</b> - Who should not be assigned</li>
 *   <li><b>Fallback Behavior</b> - What to do when primary strategy fails</li>
 *   <li><b>Load Balancing</b> - Parameters for load-based assignment</li>
 *   <li><b>Expressions</b> - Dynamic assignment expressions</li>
 * </ul>
 */
@Data
@Builder
public class AssignmentStrategyConfig {

    /**
     * Supported assignment strategy types.
     */
    public enum StrategyType {
        /** Assigns to explicitly specified users */
        DIRECT,
        /** Assigns based on user roles/groups */
        ROLE_BASED,
        /** Distributes tasks evenly across assignees */
        ROUND_ROBIN,
        /** Assigns to user with least pending tasks */
        LOAD_BALANCED,
        /** Uses dynamic expressions for complex rules */
        EXPRESSION,
        /** Assigns based on user skills/capabilities */
        CAPABILITY_BASED,
        /** Custom strategy implementation */
        CUSTOM
    }

    // ========================================================================
    // STRATEGY SELECTION
    // ========================================================================

    /**
     * The type of assignment strategy to use.
     */
    @Builder.Default
    private final StrategyType strategyType = StrategyType.DIRECT;

    /**
     * Custom strategy name (for CUSTOM type).
     */
    private final String customStrategyName;

    // ========================================================================
    // CANDIDATE CONFIGURATION
    // ========================================================================

    /**
     * Explicit list of users who can be assigned (for DIRECT strategy).
     */
    @Builder.Default
    private final List<String> assigneeUsers = List.of();

    /**
     * Roles/groups to consider for assignment (for ROLE_BASED strategy).
     */
    @Builder.Default
    private final List<String> assigneeRoles = List.of();

    /**
     * Required capabilities for assignment (for CAPABILITY_BASED strategy).
     */
    @Builder.Default
    private final List<String> requiredCapabilities = List.of();

    /**
     * Pool of users for round-robin or load-balanced assignment.
     */
    @Builder.Default
    private final List<String> assigneePool = List.of();

    // ========================================================================
    // EXCLUSION RULES
    // ========================================================================

    /**
     * Whether to exclude the workflow initiator from assignment.
     */
    @Builder.Default
    private final boolean excludeInitiator = false;

    /**
     * Users to explicitly exclude from assignment.
     */
    @Builder.Default
    private final List<String> excludedUsers = List.of();

    /**
     * Roles/groups to exclude from assignment.
     */
    @Builder.Default
    private final List<String> excludedRoles = List.of();

    // ========================================================================
    // FALLBACK BEHAVIOR
    // ========================================================================

    /**
     * Fallback strategy if primary strategy finds no assignees.
     */
    private final StrategyType fallbackStrategyType;

    /**
     * Fallback to candidate groups if no direct assignees found.
     */
    @Builder.Default
    private final boolean fallbackToGroups = true;

    /**
     * Groups to use as fallback if no assignees found.
     */
    @Builder.Default
    private final List<String> fallbackGroups = List.of();

    // ========================================================================
    // LOAD BALANCING CONFIGURATION
    // ========================================================================

    /**
     * Maximum tasks per user (for load balancing).
     */
    @Builder.Default
    private final int maxTasksPerUser = 10;

    /**
     * Whether to consider task priority in load balancing.
     */
    @Builder.Default
    private final boolean considerPriority = false;

    /**
     * Weight factor for task priority in load calculation.
     */
    @Builder.Default
    private final double priorityWeight = 1.0;

    /**
     * Whether to consider user availability.
     */
    @Builder.Default
    private final boolean considerAvailability = false;

    // ========================================================================
    // EXPRESSION CONFIGURATION
    // ========================================================================

    /**
     * Expression for dynamic assignment (for EXPRESSION strategy).
     * Supports SpEL or JavaScript expressions.
     */
    private final String assignmentExpression;

    /**
     * Expression language (spel, javascript, groovy).
     */
    @Builder.Default
    private final String expressionLanguage = "spel";

    // ========================================================================
    // ROUND ROBIN CONFIGURATION
    // ========================================================================

    /**
     * Key used to track round-robin state (e.g., workflow-id, step-id).
     */
    private final String roundRobinKey;

    /**
     * Whether to skip unavailable users in round-robin.
     */
    @Builder.Default
    private final boolean skipUnavailableUsers = true;

    // ========================================================================
    // TIMING CONFIGURATION
    // ========================================================================

    /**
     * Timeout for assignment decision.
     */
    @Builder.Default
    private final Duration assignmentTimeout = Duration.ofSeconds(30);

    /**
     * Whether to defer assignment if no suitable assignee found.
     */
    @Builder.Default
    private final boolean allowDeferred = false;

    /**
     * Duration to wait before retrying deferred assignment.
     */
    @Builder.Default
    private final Duration deferredRetryInterval = Duration.ofMinutes(5);

    // ========================================================================
    // ADDITIONAL PARAMETERS
    // ========================================================================

    /**
     * Additional strategy-specific parameters.
     */
    @Builder.Default
    private final Map<String, Object> additionalParams = Map.of();

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Gets an additional parameter value.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getAdditionalParam(String key) {
        return Optional.ofNullable((T) additionalParams.get(key));
    }

    /**
     * Gets an additional parameter with a default value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAdditionalParamOrDefault(String key, T defaultValue) {
        Object value = additionalParams.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Checks if this config has candidate users defined.
     */
    public boolean hasCandidateUsers() {
        return !assigneeUsers.isEmpty() || !assigneePool.isEmpty();
    }

    /**
     * Checks if this config has candidate roles defined.
     */
    public boolean hasCandidateRoles() {
        return !assigneeRoles.isEmpty();
    }

    /**
     * Checks if this config has fallback options.
     */
    public boolean hasFallback() {
        return fallbackStrategyType != null || fallbackToGroups || !fallbackGroups.isEmpty();
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Creates a direct assignment config with specific users.
     */
    public static AssignmentStrategyConfig directTo(List<String> users) {
        return AssignmentStrategyConfig.builder()
                .strategyType(StrategyType.DIRECT)
                .assigneeUsers(users)
                .build();
    }

    /**
     * Creates a direct assignment config with a single user.
     */
    public static AssignmentStrategyConfig directTo(String user) {
        return directTo(List.of(user));
    }

    /**
     * Creates a role-based assignment config.
     */
    public static AssignmentStrategyConfig roleBased(List<String> roles) {
        return AssignmentStrategyConfig.builder()
                .strategyType(StrategyType.ROLE_BASED)
                .assigneeRoles(roles)
                .build();
    }

    /**
     * Creates a round-robin assignment config.
     */
    public static AssignmentStrategyConfig roundRobin(List<String> userPool) {
        return AssignmentStrategyConfig.builder()
                .strategyType(StrategyType.ROUND_ROBIN)
                .assigneePool(userPool)
                .build();
    }

    /**
     * Creates a round-robin assignment config with a tracking key.
     */
    public static AssignmentStrategyConfig roundRobin(List<String> userPool, String key) {
        return AssignmentStrategyConfig.builder()
                .strategyType(StrategyType.ROUND_ROBIN)
                .assigneePool(userPool)
                .roundRobinKey(key)
                .build();
    }

    /**
     * Creates a load-balanced assignment config.
     */
    public static AssignmentStrategyConfig loadBalanced(List<String> userPool) {
        return AssignmentStrategyConfig.builder()
                .strategyType(StrategyType.LOAD_BALANCED)
                .assigneePool(userPool)
                .build();
    }

    /**
     * Creates a load-balanced assignment config with max tasks limit.
     */
    public static AssignmentStrategyConfig loadBalanced(List<String> userPool, int maxTasksPerUser) {
        return AssignmentStrategyConfig.builder()
                .strategyType(StrategyType.LOAD_BALANCED)
                .assigneePool(userPool)
                .maxTasksPerUser(maxTasksPerUser)
                .build();
    }

    /**
     * Creates an expression-based assignment config.
     */
    public static AssignmentStrategyConfig expression(String expression) {
        return AssignmentStrategyConfig.builder()
                .strategyType(StrategyType.EXPRESSION)
                .assignmentExpression(expression)
                .build();
    }

    /**
     * Creates a capability-based assignment config.
     */
    public static AssignmentStrategyConfig capabilityBased(List<String> requiredCapabilities) {
        return AssignmentStrategyConfig.builder()
                .strategyType(StrategyType.CAPABILITY_BASED)
                .requiredCapabilities(requiredCapabilities)
                .build();
    }

    /**
     * Creates a default builder with common settings pre-populated.
     */
    public static AssignmentStrategyConfigBuilder withDefaults() {
        return AssignmentStrategyConfig.builder()
                .strategyType(StrategyType.DIRECT)
                .assigneeUsers(List.of())
                .assigneeRoles(List.of())
                .assigneePool(List.of())
                .excludedUsers(List.of())
                .excludedRoles(List.of())
                .requiredCapabilities(List.of())
                .fallbackGroups(List.of())
                .additionalParams(Map.of());
    }
}
