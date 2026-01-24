package com.lyshra.open.app.core.engine.assignment.strategies;

import com.lyshra.open.app.core.engine.assignment.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Direct assignment strategy that assigns tasks to explicitly specified users.
 *
 * <p>This is the simplest assignment strategy where tasks are assigned directly
 * to users specified in the configuration or context.</p>
 *
 * <h2>Assignment Logic</h2>
 * <ol>
 *   <li>Check for explicitly configured assignees in strategy config</li>
 *   <li>Fall back to candidate users from context if no config assignees</li>
 *   <li>Filter out excluded users</li>
 *   <li>Return first available user as primary assignee</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * AssignmentStrategyConfig.directTo(List.of("user1", "user2"))
 * }</pre>
 */
public class DirectAssigneeStrategy implements ILyshraOpenAppTaskAssignmentStrategy {

    private static final String STRATEGY_NAME = "DIRECT";
    private static final String DESCRIPTION = "Assigns tasks directly to specified users";

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
        return Mono.fromCallable(() -> performAssignment(context));
    }

    private AssignmentResult performAssignment(AssignmentContext context) {
        // Get configured assignees
        List<String> configuredAssignees = getConfiguredAssignees(context);

        // Get candidate users from context as fallback
        List<String> candidateUsers = context.getCandidateUsers();

        // Merge and deduplicate
        List<String> allCandidates = new ArrayList<>(configuredAssignees);
        for (String candidate : candidateUsers) {
            if (!allCandidates.contains(candidate)) {
                allCandidates.add(candidate);
            }
        }

        // Filter out excluded users
        List<String> eligibleAssignees = filterExcludedUsers(allCandidates, context);

        if (eligibleAssignees.isEmpty()) {
            // Check if we should fall back to groups
            if (shouldFallbackToGroups(context)) {
                return handleGroupFallback(context);
            }
            return AssignmentResult.noAssignees("No eligible assignees found after filtering");
        }

        // Create result with all eligible assignees
        return AssignmentResult.builder()
                .resultType(AssignmentResult.ResultType.ASSIGNED)
                .assignees(eligibleAssignees)
                .primaryAssignee(eligibleAssignees.get(0))
                .strategyName(STRATEGY_NAME)
                .reason("Directly assigned to specified users")
                .build();
    }

    private List<String> getConfiguredAssignees(AssignmentContext context) {
        AssignmentStrategyConfig config = context.getStrategyConfig();
        if (config == null) {
            return List.of();
        }

        List<String> assignees = config.getAssigneeUsers();
        if (assignees == null || assignees.isEmpty()) {
            // Try assignee pool as fallback
            return config.getAssigneePool() != null ? config.getAssigneePool() : List.of();
        }
        return assignees;
    }

    private List<String> filterExcludedUsers(List<String> candidates, AssignmentContext context) {
        return candidates.stream()
                .filter(user -> !context.isExcluded(user))
                .collect(Collectors.toList());
    }

    private boolean shouldFallbackToGroups(AssignmentContext context) {
        AssignmentStrategyConfig config = context.getStrategyConfig();
        if (config == null) {
            return !context.getCandidateGroups().isEmpty();
        }
        return config.isFallbackToGroups() &&
               (!context.getCandidateGroups().isEmpty() || !config.getFallbackGroups().isEmpty());
    }

    private AssignmentResult handleGroupFallback(AssignmentContext context) {
        AssignmentStrategyConfig config = context.getStrategyConfig();

        List<String> groups = new ArrayList<>();

        // Add context candidate groups
        groups.addAll(context.getCandidateGroups());

        // Add fallback groups from config
        if (config != null && config.getFallbackGroups() != null) {
            for (String group : config.getFallbackGroups()) {
                if (!groups.contains(group)) {
                    groups.add(group);
                }
            }
        }

        if (groups.isEmpty()) {
            return AssignmentResult.noAssignees("No assignees or candidate groups found");
        }

        return AssignmentResult.builder()
                .resultType(AssignmentResult.ResultType.CANDIDATE_GROUPS)
                .candidateGroups(groups)
                .strategyName(STRATEGY_NAME)
                .reason("Fell back to candidate groups as no direct assignees available")
                .build();
    }

    @Override
    public Mono<ValidationResult> validateConfig(AssignmentStrategyConfig config) {
        if (config == null) {
            return Mono.just(ValidationResult.valid());
        }

        // For direct strategy, we need either assignee users or candidate users in context
        // Validation is lenient since context may provide candidates
        if (config.getStrategyType() != null &&
            config.getStrategyType() != AssignmentStrategyConfig.StrategyType.DIRECT) {
            return Mono.just(ValidationResult.invalid(
                    "Config strategy type mismatch: expected DIRECT, got " + config.getStrategyType()));
        }

        return Mono.just(ValidationResult.valid());
    }

    @Override
    public int getPriority() {
        return 100; // High priority - explicit assignment takes precedence
    }
}
