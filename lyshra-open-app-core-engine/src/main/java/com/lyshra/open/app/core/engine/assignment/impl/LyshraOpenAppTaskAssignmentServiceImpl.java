package com.lyshra.open.app.core.engine.assignment.impl;

import com.lyshra.open.app.core.engine.assignment.*;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Default implementation of the task assignment service.
 *
 * <p>This service uses the strategy registry to determine task assignments
 * based on configurable strategies.</p>
 */
@Slf4j
public class LyshraOpenAppTaskAssignmentServiceImpl implements ILyshraOpenAppTaskAssignmentService {

    private final LyshraOpenAppTaskAssignmentStrategyRegistry registry;

    private LyshraOpenAppTaskAssignmentServiceImpl() {
        this.registry = LyshraOpenAppTaskAssignmentStrategyRegistry.getInstance();
    }

    private static final class SingletonHelper {
        private static final ILyshraOpenAppTaskAssignmentService INSTANCE =
                new LyshraOpenAppTaskAssignmentServiceImpl();
    }

    public static ILyshraOpenAppTaskAssignmentService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * Creates a new instance with a custom registry.
     * Useful for testing or custom configurations.
     */
    public static ILyshraOpenAppTaskAssignmentService create(
            LyshraOpenAppTaskAssignmentStrategyRegistry registry) {
        return new LyshraOpenAppTaskAssignmentServiceImpl(registry);
    }

    private LyshraOpenAppTaskAssignmentServiceImpl(
            LyshraOpenAppTaskAssignmentStrategyRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<AssignmentResult> determineAssignment(AssignmentContext context) {
        log.debug("Determining assignment for task: type={}, workflow={}, step={}",
                context.getTaskType(),
                context.getWorkflowInstanceId(),
                context.getWorkflowStepId());

        return registry.executeAssignment(context)
                .doOnNext(result -> log.info(
                        "Assignment determined: resultType={}, primaryAssignee={}, strategy={}",
                        result.getResultType(),
                        result.getPrimaryAssignee(),
                        result.getStrategyName()))
                .doOnError(e -> log.error(
                        "Assignment failed for workflow={}, step={}",
                        context.getWorkflowInstanceId(),
                        context.getWorkflowStepId(), e));
    }

    @Override
    public Mono<AssignmentResult> determineAssignment(String strategyName, AssignmentContext context) {
        log.debug("Determining assignment with strategy: {} for workflow={}, step={}",
                strategyName,
                context.getWorkflowInstanceId(),
                context.getWorkflowStepId());

        return registry.getStrategy(strategyName)
                .map(strategy -> strategy.assign(context)
                        .map(result -> result.withStrategyName(strategy.getStrategyName())))
                .orElseGet(() -> {
                    log.warn("Strategy not found: {}, using default", strategyName);
                    return registry.executeAssignment(context);
                })
                .doOnNext(result -> log.info(
                        "Assignment determined: resultType={}, primaryAssignee={}, strategy={}",
                        result.getResultType(),
                        result.getPrimaryAssignee(),
                        result.getStrategyName()))
                .doOnError(e -> log.error(
                        "Assignment failed with strategy {} for workflow={}, step={}",
                        strategyName,
                        context.getWorkflowInstanceId(),
                        context.getWorkflowStepId(), e));
    }

    @Override
    public AssignmentContext buildContext(
            String workflowInstanceId,
            String workflowStepId,
            LyshraOpenAppHumanTaskType taskType,
            String taskTitle,
            List<String> candidateUsers,
            List<String> candidateGroups,
            ILyshraOpenAppContext workflowContext,
            AssignmentStrategyConfig strategyConfig) {

        // Extract workflow initiator if available
        String workflowInitiator = null;
        String tenantId = null;
        String businessKey = null;
        String workflowDefinitionId = null;

        if (workflowContext != null) {
            Object initiator = workflowContext.getVariable("_workflowInitiator");
            if (initiator != null) {
                workflowInitiator = initiator.toString();
            }

            Object tenant = workflowContext.getVariable("_tenantId");
            if (tenant != null) {
                tenantId = tenant.toString();
            }

            Object bKey = workflowContext.getVariable("_businessKey");
            if (bKey != null) {
                businessKey = bKey.toString();
            }

            Object wfDefId = workflowContext.getVariable("_workflowDefinitionId");
            if (wfDefId != null) {
                workflowDefinitionId = wfDefId.toString();
            }
        }

        // Build task data from context
        Map<String, Object> taskData = new HashMap<>();
        if (workflowContext != null && workflowContext.getVariables() != null) {
            // Copy relevant context variables to task data
            workflowContext.getVariables().keySet().stream()
                    .filter(name -> !name.startsWith("_")) // Exclude internal variables
                    .forEach(name -> {
                        Object value = workflowContext.getVariable(name);
                        if (value != null) {
                            taskData.put(name, value);
                        }
                    });
        }

        return AssignmentContext.builder()
                .workflowInstanceId(workflowInstanceId)
                .workflowStepId(workflowStepId)
                .workflowDefinitionId(workflowDefinitionId)
                .taskType(taskType)
                .taskTitle(taskTitle)
                .candidateUsers(candidateUsers != null ? candidateUsers : List.of())
                .candidateGroups(candidateGroups != null ? candidateGroups : List.of())
                .workflowContext(workflowContext)
                .strategyConfig(strategyConfig)
                .workflowInitiator(workflowInitiator)
                .tenantId(tenantId)
                .businessKey(businessKey)
                .taskData(taskData)
                .metadata(Map.of())
                .excludedUsers(List.of())
                .requiredCapabilities(List.of())
                .build();
    }

    @Override
    public void registerStrategy(ILyshraOpenAppTaskAssignmentStrategy strategy) {
        registry.register(strategy);
        log.info("Registered assignment strategy: {}", strategy.getStrategyName());
    }

    @Override
    public Set<String> getAvailableStrategies() {
        return registry.getStrategyNames();
    }

    @Override
    public Mono<ILyshraOpenAppTaskAssignmentStrategy.ValidationResult> validateConfig(
            AssignmentStrategyConfig config) {
        return registry.validateConfig(config);
    }

    @Override
    public Map<String, Object> getStrategyInfo(String strategyName) {
        return registry.getStrategy(strategyName)
                .map(strategy -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("name", strategy.getStrategyName());
                    info.put("description", strategy.getDescription());
                    info.put("priority", strategy.getPriority());
                    info.put("supportsReassignment", strategy.supportsReassignment());
                    info.put("requiresUserAvailability", strategy.requiresUserAvailability());
                    return info;
                })
                .orElse(Map.of());
    }
}
