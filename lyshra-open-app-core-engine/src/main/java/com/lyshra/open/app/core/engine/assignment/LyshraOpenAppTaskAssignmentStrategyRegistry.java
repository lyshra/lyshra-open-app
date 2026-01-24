package com.lyshra.open.app.core.engine.assignment;

import com.lyshra.open.app.core.engine.assignment.strategies.*;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for task assignment strategies.
 *
 * <p>This registry maintains all available assignment strategies and provides
 * methods to select the appropriate strategy based on configuration.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Get the singleton instance
 * LyshraOpenAppTaskAssignmentStrategyRegistry registry =
 *     LyshraOpenAppTaskAssignmentStrategyRegistry.getInstance();
 *
 * // Register a custom strategy
 * registry.register(new MyCustomStrategy());
 *
 * // Get strategy by name
 * ILyshraOpenAppTaskAssignmentStrategy strategy = registry.getStrategy("MY_CUSTOM");
 *
 * // Get strategy for config
 * ILyshraOpenAppTaskAssignmentStrategy strategy = registry.getStrategyForConfig(config);
 * }</pre>
 *
 * <h2>Default Strategies</h2>
 * <ul>
 *   <li>DIRECT - Direct assignee strategy</li>
 *   <li>ROLE_BASED - Role-based assignment</li>
 *   <li>ROUND_ROBIN - Round-robin distribution</li>
 *   <li>LOAD_BALANCED - Load-balanced assignment</li>
 * </ul>
 */
public class LyshraOpenAppTaskAssignmentStrategyRegistry {

    private static volatile LyshraOpenAppTaskAssignmentStrategyRegistry instance;

    private final Map<String, ILyshraOpenAppTaskAssignmentStrategy> strategies = new ConcurrentHashMap<>();
    private final ILyshraOpenAppTaskAssignmentStrategy defaultStrategy;

    /**
     * Gets the singleton instance of the registry.
     */
    public static LyshraOpenAppTaskAssignmentStrategyRegistry getInstance() {
        if (instance == null) {
            synchronized (LyshraOpenAppTaskAssignmentStrategyRegistry.class) {
                if (instance == null) {
                    instance = new LyshraOpenAppTaskAssignmentStrategyRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * Creates a new registry with default strategies.
     */
    public LyshraOpenAppTaskAssignmentStrategyRegistry() {
        // Register default strategies
        DirectAssigneeStrategy directStrategy = new DirectAssigneeStrategy();
        register(directStrategy);
        register(new RoleBasedAssignmentStrategy());
        register(new RoundRobinAssignmentStrategy());
        register(new LoadBalancedAssignmentStrategy());

        this.defaultStrategy = directStrategy;
    }

    /**
     * Registers a strategy.
     *
     * @param strategy the strategy to register
     * @return this registry for chaining
     */
    public LyshraOpenAppTaskAssignmentStrategyRegistry register(ILyshraOpenAppTaskAssignmentStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy cannot be null");
        }
        strategies.put(strategy.getStrategyName().toUpperCase(), strategy);
        return this;
    }

    /**
     * Unregisters a strategy.
     *
     * @param strategyName the name of the strategy to unregister
     * @return the removed strategy, or null if not found
     */
    public ILyshraOpenAppTaskAssignmentStrategy unregister(String strategyName) {
        if (strategyName == null) {
            return null;
        }
        return strategies.remove(strategyName.toUpperCase());
    }

    /**
     * Gets a strategy by name.
     *
     * @param strategyName the strategy name
     * @return the strategy, or empty if not found
     */
    public Optional<ILyshraOpenAppTaskAssignmentStrategy> getStrategy(String strategyName) {
        if (strategyName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(strategies.get(strategyName.toUpperCase()));
    }

    /**
     * Gets a strategy by name, falling back to default if not found.
     *
     * @param strategyName the strategy name
     * @return the strategy or the default strategy
     */
    public ILyshraOpenAppTaskAssignmentStrategy getStrategyOrDefault(String strategyName) {
        return getStrategy(strategyName).orElse(defaultStrategy);
    }

    /**
     * Gets the appropriate strategy for a configuration.
     *
     * @param config the assignment configuration
     * @return the matching strategy
     */
    public ILyshraOpenAppTaskAssignmentStrategy getStrategyForConfig(AssignmentStrategyConfig config) {
        if (config == null) {
            return defaultStrategy;
        }

        // Check for custom strategy name
        if (config.getStrategyType() == AssignmentStrategyConfig.StrategyType.CUSTOM &&
            config.getCustomStrategyName() != null) {
            return getStrategyOrDefault(config.getCustomStrategyName());
        }

        // Map strategy type to strategy name
        String strategyName = mapStrategyTypeToName(config.getStrategyType());
        return getStrategyOrDefault(strategyName);
    }

    private String mapStrategyTypeToName(AssignmentStrategyConfig.StrategyType type) {
        if (type == null) {
            return "DIRECT";
        }

        return switch (type) {
            case DIRECT -> "DIRECT";
            case ROLE_BASED -> "ROLE_BASED";
            case ROUND_ROBIN -> "ROUND_ROBIN";
            case LOAD_BALANCED -> "LOAD_BALANCED";
            case EXPRESSION -> "EXPRESSION";
            case CAPABILITY_BASED -> "CAPABILITY_BASED";
            case CUSTOM -> "DIRECT"; // Fallback for unspecified custom
        };
    }

    /**
     * Gets the default strategy.
     *
     * @return the default strategy
     */
    public ILyshraOpenAppTaskAssignmentStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    /**
     * Gets all registered strategies.
     *
     * @return unmodifiable map of strategy name to strategy
     */
    public Map<String, ILyshraOpenAppTaskAssignmentStrategy> getAllStrategies() {
        return Collections.unmodifiableMap(strategies);
    }

    /**
     * Gets names of all registered strategies.
     *
     * @return set of strategy names
     */
    public Set<String> getStrategyNames() {
        return Collections.unmodifiableSet(strategies.keySet());
    }

    /**
     * Checks if a strategy is registered.
     *
     * @param strategyName the strategy name
     * @return true if registered
     */
    public boolean hasStrategy(String strategyName) {
        return strategyName != null && strategies.containsKey(strategyName.toUpperCase());
    }

    /**
     * Executes assignment using the appropriate strategy for the context.
     *
     * @param context the assignment context
     * @return the assignment result
     */
    public Mono<AssignmentResult> executeAssignment(AssignmentContext context) {
        ILyshraOpenAppTaskAssignmentStrategy strategy = getStrategyForConfig(context.getStrategyConfig());
        return strategy.assign(context)
                .map(result -> result.withStrategyName(strategy.getStrategyName()));
    }

    /**
     * Validates configuration for the appropriate strategy.
     *
     * @param config the configuration to validate
     * @return validation result
     */
    public Mono<ILyshraOpenAppTaskAssignmentStrategy.ValidationResult> validateConfig(
            AssignmentStrategyConfig config) {
        ILyshraOpenAppTaskAssignmentStrategy strategy = getStrategyForConfig(config);
        return strategy.validateConfig(config);
    }

    /**
     * Resets the singleton instance (for testing).
     */
    public static synchronized void resetInstance() {
        instance = null;
    }
}
