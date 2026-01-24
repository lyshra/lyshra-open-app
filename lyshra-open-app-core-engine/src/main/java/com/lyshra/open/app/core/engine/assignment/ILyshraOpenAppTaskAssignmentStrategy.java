package com.lyshra.open.app.core.engine.assignment;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Strategy interface for automatic task assignment.
 *
 * <p>Implementations of this interface determine how human tasks are assigned
 * to users or groups based on configurable rules. Different strategies can be
 * used for different workflows, steps, or task types.</p>
 *
 * <h2>Available Strategies</h2>
 * <ul>
 *   <li><b>DIRECT</b> - Assigns to explicitly specified users</li>
 *   <li><b>ROLE_BASED</b> - Assigns based on user roles/groups</li>
 *   <li><b>ROUND_ROBIN</b> - Distributes tasks evenly across assignees</li>
 *   <li><b>LOAD_BALANCED</b> - Assigns to user with least pending tasks</li>
 *   <li><b>EXPRESSION</b> - Uses dynamic expressions for complex rules</li>
 *   <li><b>CAPABILITY</b> - Assigns based on user skills/capabilities</li>
 * </ul>
 *
 * <h2>Strategy Selection</h2>
 * <p>Strategies can be configured at multiple levels:</p>
 * <ol>
 *   <li>Workflow definition level (default for all steps)</li>
 *   <li>Step level (overrides workflow default)</li>
 *   <li>Task type level (specific to task types)</li>
 *   <li>Runtime level (dynamic selection based on context)</li>
 * </ol>
 *
 * <h2>Implementation Example</h2>
 * <pre>{@code
 * public class MyCustomStrategy implements ILyshraOpenAppTaskAssignmentStrategy {
 *     @Override
 *     public String getStrategyName() {
 *         return "MY_CUSTOM";
 *     }
 *
 *     @Override
 *     public Mono<AssignmentResult> assign(AssignmentContext context) {
 *         // Custom assignment logic
 *         return Mono.just(AssignmentResult.assignTo(List.of("user1", "user2")));
 *     }
 * }
 * }</pre>
 *
 * @see AssignmentContext
 * @see AssignmentResult
 */
public interface ILyshraOpenAppTaskAssignmentStrategy {

    /**
     * Returns the unique name of this strategy.
     * <p>Used for configuration and strategy selection.</p>
     *
     * @return the strategy name (e.g., "DIRECT", "ROUND_ROBIN", "LOAD_BALANCED")
     */
    String getStrategyName();

    /**
     * Returns a description of this strategy.
     *
     * @return human-readable description
     */
    String getDescription();

    /**
     * Determines the assignment for a task based on the given context.
     *
     * @param context the assignment context containing task and configuration info
     * @return the assignment result with assignees and/or candidate groups
     */
    Mono<AssignmentResult> assign(AssignmentContext context);

    /**
     * Validates the configuration for this strategy.
     *
     * @param config the strategy configuration
     * @return validation result with any errors
     */
    default Mono<ValidationResult> validateConfig(AssignmentStrategyConfig config) {
        return Mono.just(ValidationResult.valid());
    }

    /**
     * Indicates whether this strategy supports dynamic reassignment.
     * <p>If true, the strategy can reassign tasks when conditions change.</p>
     *
     * @return true if reassignment is supported
     */
    default boolean supportsReassignment() {
        return false;
    }

    /**
     * Indicates whether this strategy requires user availability information.
     *
     * @return true if user availability is needed
     */
    default boolean requiresUserAvailability() {
        return false;
    }

    /**
     * Returns the priority of this strategy when multiple strategies apply.
     * <p>Higher values indicate higher priority.</p>
     *
     * @return priority value (default 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Validation result for strategy configuration.
     */
    record ValidationResult(boolean isValid, List<String> errors) {
        public static ValidationResult valid() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, List.of(error));
        }

        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }
}
