package com.lyshra.open.app.core.engine.assignment;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Service interface for task assignment operations.
 *
 * <p>This service provides high-level methods for determining task assignments
 * based on configurable strategies. It integrates with the HumanTaskService
 * to apply assignments during task creation.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ILyshraOpenAppTaskAssignmentService assignmentService =
 *     LyshraOpenAppTaskAssignmentServiceImpl.getInstance();
 *
 * // Build assignment context
 * AssignmentContext context = AssignmentContext.builder()
 *     .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
 *     .taskTitle("Approve Purchase Order")
 *     .workflowInstanceId("wf-123")
 *     .candidateUsers(List.of("user1", "user2"))
 *     .candidateGroups(List.of("approvers"))
 *     .strategyConfig(AssignmentStrategyConfig.roundRobin(List.of("user1", "user2")))
 *     .build();
 *
 * // Execute assignment
 * AssignmentResult result = assignmentService.determineAssignment(context).block();
 * }</pre>
 *
 * @see ILyshraOpenAppTaskAssignmentStrategy
 * @see AssignmentContext
 * @see AssignmentResult
 */
public interface ILyshraOpenAppTaskAssignmentService {

    /**
     * Determines the assignment for a task based on the provided context.
     *
     * @param context the assignment context containing task info and configuration
     * @return the assignment result
     */
    Mono<AssignmentResult> determineAssignment(AssignmentContext context);

    /**
     * Determines the assignment using a specific strategy.
     *
     * @param strategyName the name of the strategy to use
     * @param context the assignment context
     * @return the assignment result
     */
    Mono<AssignmentResult> determineAssignment(String strategyName, AssignmentContext context);

    /**
     * Creates an assignment context from workflow information.
     *
     * @param workflowInstanceId the workflow instance ID
     * @param workflowStepId the current step ID
     * @param taskType the type of task
     * @param taskTitle the task title
     * @param candidateUsers candidate users for assignment
     * @param candidateGroups candidate groups for assignment
     * @param workflowContext the current workflow context
     * @param strategyConfig the strategy configuration
     * @return the built assignment context
     */
    AssignmentContext buildContext(
            String workflowInstanceId,
            String workflowStepId,
            LyshraOpenAppHumanTaskType taskType,
            String taskTitle,
            List<String> candidateUsers,
            List<String> candidateGroups,
            ILyshraOpenAppContext workflowContext,
            AssignmentStrategyConfig strategyConfig);

    /**
     * Registers a custom assignment strategy.
     *
     * @param strategy the strategy to register
     */
    void registerStrategy(ILyshraOpenAppTaskAssignmentStrategy strategy);

    /**
     * Gets the list of available strategy names.
     *
     * @return set of strategy names
     */
    java.util.Set<String> getAvailableStrategies();

    /**
     * Validates a strategy configuration.
     *
     * @param config the configuration to validate
     * @return validation result
     */
    Mono<ILyshraOpenAppTaskAssignmentStrategy.ValidationResult> validateConfig(AssignmentStrategyConfig config);

    /**
     * Gets strategy information for a given strategy name.
     *
     * @param strategyName the strategy name
     * @return strategy info map with name, description, priority
     */
    Map<String, Object> getStrategyInfo(String strategyName);
}
