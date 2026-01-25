package com.lyshra.open.app.designer.service.impl;

import com.lyshra.open.app.designer.domain.WorkflowConnection;
import com.lyshra.open.app.designer.domain.WorkflowStepDefinition;
import com.lyshra.open.app.designer.domain.WorkflowVersion;
import com.lyshra.open.app.designer.dto.ValidationError;
import com.lyshra.open.app.designer.dto.ValidationResult;
import com.lyshra.open.app.designer.dto.ValidationWarning;
import com.lyshra.open.app.designer.service.WorkflowValidationService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of WorkflowValidationService.
 * Validates workflow structure and configuration.
 */
@Service
public class WorkflowValidationServiceImpl implements WorkflowValidationService {

    @Override
    public Mono<ValidationResult> validateWorkflow(WorkflowVersion version) {
        return Mono.zip(
                        validateStartStep(version),
                        validateConnections(version),
                        validateStepConfigurations(version),
                        validateErrorHandling(version)
                )
                .map(tuple -> {
                    ValidationResult result = ValidationResult.valid();
                    result.merge(tuple.getT1());
                    result.merge(tuple.getT2());
                    result.merge(tuple.getT3());
                    result.merge(tuple.getT4());
                    return result;
                });
    }

    @Override
    public Mono<ValidationResult> validateConnections(WorkflowVersion version) {
        return Mono.fromCallable(() -> {
            ValidationResult result = ValidationResult.valid();
            List<WorkflowStepDefinition> steps = version.getSteps();
            Map<String, WorkflowConnection> connections = version.getConnections();

            if (steps == null || steps.isEmpty()) {
                return result;
            }

            Set<String> stepIds = steps.stream()
                    .map(WorkflowStepDefinition::getId)
                    .collect(Collectors.toSet());

            if (connections != null) {
                for (Map.Entry<String, WorkflowConnection> entry : connections.entrySet()) {
                    WorkflowConnection conn = entry.getValue();
                    if (conn.getSourceStepId() != null && !stepIds.contains(conn.getSourceStepId())) {
                        result.addError(ValidationError.builder()
                                .code("INVALID_SOURCE_STEP")
                                .message("Connection references non-existent source step: " +
                                        conn.getSourceStepId())
                                .field("connections." + entry.getKey() + ".sourceStepId")
                                .build());
                    }
                    if (conn.getTargetStepId() != null && !stepIds.contains(conn.getTargetStepId())) {
                        result.addError(ValidationError.builder()
                                .code("INVALID_TARGET_STEP")
                                .message("Connection references non-existent target step: " +
                                        conn.getTargetStepId())
                                .field("connections." + entry.getKey() + ".targetStepId")
                                .build());
                    }
                }
            }

            checkForOrphanedSteps(steps, connections, result);
            checkForCyclicConnections(steps, connections, result);

            return result;
        });
    }

    private void checkForOrphanedSteps(
            List<WorkflowStepDefinition> steps,
            Map<String, WorkflowConnection> connections,
            ValidationResult result) {
        if (steps == null || steps.size() <= 1) {
            return;
        }

        Set<String> connectedSteps = new HashSet<>();
        if (connections != null) {
            for (WorkflowConnection conn : connections.values()) {
                if (conn.getSourceStepId() != null) {
                    connectedSteps.add(conn.getSourceStepId());
                }
                if (conn.getTargetStepId() != null) {
                    connectedSteps.add(conn.getTargetStepId());
                }
            }
        }

        for (WorkflowStepDefinition step : steps) {
            if (!connectedSteps.contains(step.getId())) {
                result.addWarning(ValidationWarning.stepWarning(
                        "ORPHANED_STEP",
                        "Step is not connected to any other step",
                        step.getId(),
                        step.getName()
                ));
            }
        }
    }

    private void checkForCyclicConnections(
            List<WorkflowStepDefinition> steps,
            Map<String, WorkflowConnection> connections,
            ValidationResult result) {
        if (connections == null || connections.isEmpty()) {
            return;
        }

        Map<String, List<String>> adjacencyList = new HashMap<>();
        for (WorkflowConnection conn : connections.values()) {
            if (conn.getSourceStepId() != null && conn.getTargetStepId() != null) {
                adjacencyList.computeIfAbsent(conn.getSourceStepId(), k -> new ArrayList<>())
                        .add(conn.getTargetStepId());
            }
        }

        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (WorkflowStepDefinition step : steps) {
            if (hasCycle(step.getId(), adjacencyList, visited, recursionStack)) {
                result.addWarning(ValidationWarning.builder()
                        .code("CYCLIC_CONNECTION")
                        .message("Workflow contains cyclic connections which may cause infinite loops")
                        .build());
                break;
            }
        }
    }

    private boolean hasCycle(
            String stepId,
            Map<String, List<String>> adjacencyList,
            Set<String> visited,
            Set<String> recursionStack) {
        if (recursionStack.contains(stepId)) {
            return true;
        }
        if (visited.contains(stepId)) {
            return false;
        }

        visited.add(stepId);
        recursionStack.add(stepId);

        List<String> neighbors = adjacencyList.getOrDefault(stepId, Collections.emptyList());
        for (String neighbor : neighbors) {
            if (hasCycle(neighbor, adjacencyList, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(stepId);
        return false;
    }

    @Override
    public Mono<ValidationResult> validateStepConfigurations(WorkflowVersion version) {
        return Mono.fromCallable(() -> {
            ValidationResult result = ValidationResult.valid();
            List<WorkflowStepDefinition> steps = version.getSteps();

            if (steps == null || steps.isEmpty()) {
                return result;
            }

            for (WorkflowStepDefinition step : steps) {
                validateStep(step, result);
            }

            return result;
        });
    }

    private void validateStep(WorkflowStepDefinition step, ValidationResult result) {
        if (step.getId() == null || step.getId().isBlank()) {
            result.addError(ValidationError.builder()
                    .code("MISSING_STEP_ID")
                    .message("Step ID is required")
                    .stepName(step.getName())
                    .build());
        }

        if (step.getName() == null || step.getName().isBlank()) {
            result.addError(ValidationError.builder()
                    .code("MISSING_STEP_NAME")
                    .message("Step name is required")
                    .stepId(step.getId())
                    .build());
        }

        if (step.getType() == null) {
            result.addError(ValidationError.stepError(
                    "MISSING_STEP_TYPE",
                    "Step type is required",
                    step.getId(),
                    step.getName()
            ));
            return;
        }

        switch (step.getType()) {
            case PROCESSOR -> validateProcessorStep(step, result);
            case WORKFLOW -> validateWorkflowCallStep(step, result);
        }
    }

    private void validateProcessorStep(WorkflowStepDefinition step, ValidationResult result) {
        if (step.getProcessor() == null) {
            result.addError(ValidationError.stepError(
                    "MISSING_PROCESSOR_REFERENCE",
                    "Processor reference is required for processor steps",
                    step.getId(),
                    step.getName()
            ));
        } else {
            if (step.getProcessor().getProcessorName() == null ||
                    step.getProcessor().getProcessorName().isBlank()) {
                result.addError(ValidationError.stepError(
                        "MISSING_PROCESSOR_NAME",
                        "Processor name is required",
                        step.getId(),
                        step.getName()
                ));
            }
        }
    }

    private void validateWorkflowCallStep(WorkflowStepDefinition step, ValidationResult result) {
        if (step.getWorkflowCall() == null) {
            result.addError(ValidationError.stepError(
                    "MISSING_WORKFLOW_REFERENCE",
                    "Workflow reference is required for workflow call steps",
                    step.getId(),
                    step.getName()
            ));
        } else {
            if (step.getWorkflowCall().getWorkflowName() == null ||
                    step.getWorkflowCall().getWorkflowName().isBlank()) {
                result.addError(ValidationError.stepError(
                        "MISSING_WORKFLOW_NAME",
                        "Workflow name is required",
                        step.getId(),
                        step.getName()
                ));
            }
        }
    }

    @Override
    public Mono<ValidationResult> validateStartStep(WorkflowVersion version) {
        return Mono.fromCallable(() -> {
            ValidationResult result = ValidationResult.valid();
            List<WorkflowStepDefinition> steps = version.getSteps();

            if (steps == null || steps.isEmpty()) {
                result.addWarning(ValidationWarning.builder()
                        .code("NO_STEPS")
                        .message("Workflow has no steps defined")
                        .build());
                return result;
            }

            String startStepId = version.getStartStepId();
            if (startStepId == null || startStepId.isBlank()) {
                result.addError(ValidationError.fieldError(
                        "MISSING_START_STEP",
                        "Start step ID is required",
                        "startStepId"
                ));
                return result;
            }

            boolean startStepExists = steps.stream()
                    .anyMatch(step -> startStepId.equals(step.getId()));

            if (!startStepExists) {
                result.addError(ValidationError.fieldError(
                        "INVALID_START_STEP",
                        "Start step references non-existent step: " + startStepId,
                        "startStepId"
                ));
            }

            return result;
        });
    }

    @Override
    public Mono<ValidationResult> validateErrorHandling(WorkflowVersion version) {
        return Mono.fromCallable(() -> {
            ValidationResult result = ValidationResult.valid();
            List<WorkflowStepDefinition> steps = version.getSteps();

            if (steps == null || steps.isEmpty()) {
                return result;
            }

            Set<String> stepIds = steps.stream()
                    .map(WorkflowStepDefinition::getId)
                    .collect(Collectors.toSet());

            for (WorkflowStepDefinition step : steps) {
                if (step.getErrorHandling() != null) {
                    String fallbackStepId = step.getErrorHandling().getFallbackStepId();
                    if (fallbackStepId != null && !stepIds.contains(fallbackStepId)) {
                        result.addError(ValidationError.stepError(
                                "INVALID_FALLBACK_STEP",
                                "Fallback step references non-existent step: " + fallbackStepId,
                                step.getId(),
                                step.getName()
                        ));
                    }
                }
            }

            return result;
        });
    }
}
