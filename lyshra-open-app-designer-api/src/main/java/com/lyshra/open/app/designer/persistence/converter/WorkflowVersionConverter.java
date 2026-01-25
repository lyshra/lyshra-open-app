package com.lyshra.open.app.designer.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lyshra.open.app.designer.domain.*;
import com.lyshra.open.app.designer.persistence.entity.WorkflowVersionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Converter between WorkflowVersion domain object and WorkflowVersionEntity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowVersionConverter {

    private final ObjectMapper objectMapper;

    /**
     * Convert domain object to entity.
     */
    public WorkflowVersionEntity toEntity(WorkflowVersion domain) {
        if (domain == null) {
            return null;
        }

        String stepsJson = null;
        String connectionsJson = null;
        String contextRetentionJson = null;
        String designerMetadataJson = null;

        try {
            if (domain.getSteps() != null) {
                stepsJson = objectMapper.writeValueAsString(domain.getSteps());
            }
            if (domain.getConnections() != null) {
                connectionsJson = objectMapper.writeValueAsString(domain.getConnections());
            }
            if (domain.getContextRetention() != null) {
                contextRetentionJson = objectMapper.writeValueAsString(domain.getContextRetention());
            }
            if (domain.getDesignerMetadata() != null) {
                designerMetadataJson = objectMapper.writeValueAsString(domain.getDesignerMetadata());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize workflow version data", e);
        }

        return WorkflowVersionEntity.builder()
                .id(domain.getId())
                .workflowDefinitionId(domain.getWorkflowDefinitionId())
                .versionNumber(domain.getVersionNumber())
                .description(domain.getDescription())
                .startStepId(domain.getStartStepId())
                .stepsJson(stepsJson)
                .connectionsJson(connectionsJson)
                .contextRetentionJson(contextRetentionJson)
                .designerMetadataJson(designerMetadataJson)
                .state(domain.getState())
                .createdBy(domain.getCreatedBy())
                .createdAt(domain.getCreatedAt())
                .activatedBy(domain.getActivatedBy())
                .activatedAt(domain.getActivatedAt())
                .deprecatedBy(domain.getDeprecatedBy())
                .deprecatedAt(domain.getDeprecatedAt())
                .build();
    }

    /**
     * Convert entity to domain object.
     */
    public WorkflowVersion toDomain(WorkflowVersionEntity entity) {
        if (entity == null) {
            return null;
        }

        List<WorkflowStepDefinition> steps = Collections.emptyList();
        Map<String, WorkflowConnection> connections = Collections.emptyMap();
        WorkflowContextRetention contextRetention = null;
        DesignerMetadata designerMetadata = null;

        try {
            if (entity.getStepsJson() != null && !entity.getStepsJson().isBlank()) {
                steps = objectMapper.readValue(entity.getStepsJson(),
                        new TypeReference<List<WorkflowStepDefinition>>() {});
            }
            if (entity.getConnectionsJson() != null && !entity.getConnectionsJson().isBlank()) {
                connections = objectMapper.readValue(entity.getConnectionsJson(),
                        new TypeReference<Map<String, WorkflowConnection>>() {});
            }
            if (entity.getContextRetentionJson() != null && !entity.getContextRetentionJson().isBlank()) {
                contextRetention = objectMapper.readValue(entity.getContextRetentionJson(),
                        WorkflowContextRetention.class);
            }
            if (entity.getDesignerMetadataJson() != null && !entity.getDesignerMetadataJson().isBlank()) {
                designerMetadata = objectMapper.readValue(entity.getDesignerMetadataJson(),
                        DesignerMetadata.class);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize workflow version data", e);
        }

        return WorkflowVersion.builder()
                .id(entity.getId())
                .workflowDefinitionId(entity.getWorkflowDefinitionId())
                .versionNumber(entity.getVersionNumber())
                .description(entity.getDescription())
                .startStepId(entity.getStartStepId())
                .steps(steps)
                .connections(connections)
                .contextRetention(contextRetention)
                .designerMetadata(designerMetadata)
                .state(entity.getState())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .activatedBy(entity.getActivatedBy())
                .activatedAt(entity.getActivatedAt())
                .deprecatedBy(entity.getDeprecatedBy())
                .deprecatedAt(entity.getDeprecatedAt())
                .build();
    }

    /**
     * Convert Optional entity to Optional domain object.
     */
    public Optional<WorkflowVersion> toDomainOptional(WorkflowVersionEntity entity) {
        return Optional.ofNullable(toDomain(entity));
    }
}
