package com.lyshra.open.app.designer.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lyshra.open.app.designer.domain.StepExecutionLog;
import com.lyshra.open.app.designer.domain.WorkflowExecution;
import com.lyshra.open.app.designer.persistence.entity.WorkflowExecutionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Converter between WorkflowExecution domain object and WorkflowExecutionEntity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowExecutionConverter {

    private final ObjectMapper objectMapper;

    /**
     * Convert domain object to entity.
     */
    public WorkflowExecutionEntity toEntity(WorkflowExecution domain) {
        if (domain == null) {
            return null;
        }

        String inputDataJson = null;
        String outputDataJson = null;
        String contextDataJson = null;
        String stepLogsJson = null;

        try {
            if (domain.getInputData() != null) {
                inputDataJson = objectMapper.writeValueAsString(domain.getInputData());
            }
            if (domain.getOutputData() != null) {
                outputDataJson = objectMapper.writeValueAsString(domain.getOutputData());
            }
            if (domain.getVariables() != null) {
                contextDataJson = objectMapper.writeValueAsString(domain.getVariables());
            }
            if (domain.getStepLogs() != null) {
                stepLogsJson = objectMapper.writeValueAsString(domain.getStepLogs());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize execution data", e);
        }

        return WorkflowExecutionEntity.builder()
                .id(domain.getId())
                .workflowDefinitionId(domain.getWorkflowDefinitionId())
                .workflowVersionId(domain.getWorkflowVersionId())
                .workflowName(domain.getWorkflowName())
                .versionNumber(domain.getVersionNumber())
                .status(domain.getStatus())
                .correlationId(domain.getCorrelationId())
                .triggeredBy(domain.getTriggeredBy())
                .inputDataJson(inputDataJson)
                .outputDataJson(outputDataJson)
                .contextDataJson(contextDataJson)
                .currentStepId(domain.getCurrentStepId())
                .currentStepName(domain.getCurrentStepName())
                .errorCode(domain.getErrorCode())
                .errorMessage(domain.getErrorMessage())
                .stepLogsJson(stepLogsJson)
                .startedAt(domain.getStartedAt())
                .completedAt(domain.getCompletedAt())
                .build();
    }

    /**
     * Convert entity to domain object.
     */
    public WorkflowExecution toDomain(WorkflowExecutionEntity entity) {
        if (entity == null) {
            return null;
        }

        Map<String, Object> inputData = Collections.emptyMap();
        Map<String, Object> outputData = null;
        Map<String, Object> variables = Collections.emptyMap();
        List<StepExecutionLog> stepLogs = Collections.emptyList();

        try {
            if (entity.getInputDataJson() != null && !entity.getInputDataJson().isBlank()) {
                inputData = objectMapper.readValue(entity.getInputDataJson(),
                        new TypeReference<Map<String, Object>>() {});
            }
            if (entity.getOutputDataJson() != null && !entity.getOutputDataJson().isBlank()) {
                outputData = objectMapper.readValue(entity.getOutputDataJson(),
                        new TypeReference<Map<String, Object>>() {});
            }
            if (entity.getContextDataJson() != null && !entity.getContextDataJson().isBlank()) {
                variables = objectMapper.readValue(entity.getContextDataJson(),
                        new TypeReference<Map<String, Object>>() {});
            }
            if (entity.getStepLogsJson() != null && !entity.getStepLogsJson().isBlank()) {
                stepLogs = objectMapper.readValue(entity.getStepLogsJson(),
                        new TypeReference<List<StepExecutionLog>>() {});
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize execution data", e);
        }

        return WorkflowExecution.builder()
                .id(entity.getId())
                .workflowDefinitionId(entity.getWorkflowDefinitionId())
                .workflowVersionId(entity.getWorkflowVersionId())
                .workflowName(entity.getWorkflowName())
                .versionNumber(entity.getVersionNumber())
                .status(entity.getStatus())
                .correlationId(entity.getCorrelationId())
                .triggeredBy(entity.getTriggeredBy())
                .inputData(inputData)
                .outputData(outputData)
                .variables(variables)
                .currentStepId(entity.getCurrentStepId())
                .currentStepName(entity.getCurrentStepName())
                .errorCode(entity.getErrorCode())
                .errorMessage(entity.getErrorMessage())
                .stepLogs(stepLogs)
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }

    /**
     * Convert Optional entity to Optional domain object.
     */
    public Optional<WorkflowExecution> toDomainOptional(WorkflowExecutionEntity entity) {
        return Optional.ofNullable(toDomain(entity));
    }
}
