package com.lyshra.open.app.core.engine.assignment.strategies;

import com.lyshra.open.app.core.engine.assignment.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Role-based assignment strategy that assigns tasks based on user roles/groups.
 *
 * <p>This strategy determines assignees by matching required roles against
 * candidate groups. It can either assign directly to role members or
 * make the task available to candidate groups for claiming.</p>
 *
 * <h2>Assignment Logic</h2>
 * <ol>
 *   <li>Get required roles from configuration</li>
 *   <li>If role resolver is available, resolve role members</li>
 *   <li>Otherwise, assign to candidate groups matching the roles</li>
 *   <li>Filter out excluded users/roles</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * AssignmentStrategyConfig.roleBased(List.of("APPROVERS", "MANAGERS"))
 * }</pre>
 */
public class RoleBasedAssignmentStrategy implements ILyshraOpenAppTaskAssignmentStrategy {

    private static final String STRATEGY_NAME = "ROLE_BASED";
    private static final String DESCRIPTION = "Assigns tasks based on user roles and groups";

    /**
     * Optional role resolver for looking up role members.
     * If not set, tasks are assigned to candidate groups.
     */
    private RoleMemberResolver roleMemberResolver;

    /**
     * Interface for resolving members of a role/group.
     */
    @FunctionalInterface
    public interface RoleMemberResolver {
        /**
         * Resolves the members of a role/group.
         *
         * @param role the role/group name
         * @param context optional context for resolution
         * @return list of user IDs belonging to the role
         */
        Mono<List<String>> resolveMembers(String role, Map<String, Object> context);
    }

    public RoleBasedAssignmentStrategy() {
    }

    public RoleBasedAssignmentStrategy(RoleMemberResolver resolver) {
        this.roleMemberResolver = resolver;
    }

    /**
     * Sets the role member resolver.
     */
    public RoleBasedAssignmentStrategy withRoleMemberResolver(RoleMemberResolver resolver) {
        this.roleMemberResolver = resolver;
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
        List<String> requiredRoles = getRequiredRoles(context);

        if (requiredRoles.isEmpty()) {
            // No roles specified, fall back to context candidate groups
            return Mono.just(handleNoRolesSpecified(context));
        }

        // Filter out excluded roles
        List<String> eligibleRoles = filterExcludedRoles(requiredRoles, context);

        if (eligibleRoles.isEmpty()) {
            return Mono.just(AssignmentResult.noAssignees("All specified roles are excluded"));
        }

        // If we have a role resolver, try to get specific users
        if (roleMemberResolver != null) {
            return resolveRoleMembers(eligibleRoles, context);
        }

        // No resolver - assign to candidate groups
        return Mono.just(assignToCandidateGroups(eligibleRoles, context));
    }

    private List<String> getRequiredRoles(AssignmentContext context) {
        AssignmentStrategyConfig config = context.getStrategyConfig();

        List<String> roles = new ArrayList<>();

        // Get roles from config
        if (config != null && config.getAssigneeRoles() != null) {
            roles.addAll(config.getAssigneeRoles());
        }

        // Add candidate groups from context if no config roles
        if (roles.isEmpty()) {
            roles.addAll(context.getCandidateGroups());
        }

        return roles;
    }

    private List<String> filterExcludedRoles(List<String> roles, AssignmentContext context) {
        AssignmentStrategyConfig config = context.getStrategyConfig();
        if (config == null || config.getExcludedRoles() == null) {
            return roles;
        }

        List<String> excludedRoles = config.getExcludedRoles();
        List<String> filtered = new ArrayList<>();
        for (String role : roles) {
            if (!excludedRoles.contains(role)) {
                filtered.add(role);
            }
        }
        return filtered;
    }

    private AssignmentResult handleNoRolesSpecified(AssignmentContext context) {
        List<String> candidateGroups = context.getCandidateGroups();

        if (candidateGroups.isEmpty()) {
            return AssignmentResult.noAssignees("No roles or candidate groups specified");
        }

        return AssignmentResult.builder()
                .resultType(AssignmentResult.ResultType.CANDIDATE_GROUPS)
                .candidateGroups(candidateGroups)
                .strategyName(STRATEGY_NAME)
                .reason("Assigned to context candidate groups (no specific roles configured)")
                .build();
    }

    private Mono<AssignmentResult> resolveRoleMembers(List<String> roles, AssignmentContext context) {
        // Resolve all roles and collect members
        List<Mono<List<String>>> memberMonos = new ArrayList<>();

        Map<String, Object> resolverContext = Map.of(
                "workflowInstanceId", Optional.ofNullable(context.getWorkflowInstanceId()).orElse(""),
                "tenantId", Optional.ofNullable(context.getTenantId()).orElse(""),
                "department", Optional.ofNullable(context.getDepartment()).orElse(""),
                "location", Optional.ofNullable(context.getLocation()).orElse("")
        );

        for (String role : roles) {
            memberMonos.add(roleMemberResolver.resolveMembers(role, resolverContext));
        }

        return Mono.zip(memberMonos, results -> {
            List<String> allMembers = new ArrayList<>();
            for (Object result : results) {
                @SuppressWarnings("unchecked")
                List<String> members = (List<String>) result;
                for (String member : members) {
                    if (!allMembers.contains(member) && !context.isExcluded(member)) {
                        allMembers.add(member);
                    }
                }
            }
            return allMembers;
        }).map(members -> {
            if (members.isEmpty()) {
                // No members resolved, fall back to groups
                return assignToCandidateGroups(roles, context);
            }

            return AssignmentResult.builder()
                    .resultType(AssignmentResult.ResultType.MIXED)
                    .assignees(members)
                    .candidateGroups(roles)
                    .primaryAssignee(members.get(0))
                    .strategyName(STRATEGY_NAME)
                    .reason("Assigned to role members: " + String.join(", ", roles))
                    .build();
        }).onErrorResume(e -> {
            // If resolution fails, fall back to candidate groups
            return Mono.just(assignToCandidateGroups(roles, context));
        });
    }

    private AssignmentResult assignToCandidateGroups(List<String> roles, AssignmentContext context) {
        return AssignmentResult.builder()
                .resultType(AssignmentResult.ResultType.CANDIDATE_GROUPS)
                .candidateGroups(roles)
                .strategyName(STRATEGY_NAME)
                .reason("Assigned to candidate groups: " + String.join(", ", roles))
                .build();
    }

    @Override
    public Mono<ValidationResult> validateConfig(AssignmentStrategyConfig config) {
        if (config == null) {
            return Mono.just(ValidationResult.valid());
        }

        if (config.getStrategyType() != null &&
            config.getStrategyType() != AssignmentStrategyConfig.StrategyType.ROLE_BASED) {
            return Mono.just(ValidationResult.invalid(
                    "Config strategy type mismatch: expected ROLE_BASED, got " + config.getStrategyType()));
        }

        // Warn if no roles specified (but still valid as context may provide groups)
        return Mono.just(ValidationResult.valid());
    }

    @Override
    public int getPriority() {
        return 80; // Lower than direct assignment
    }
}
