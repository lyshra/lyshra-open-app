package com.lyshra.open.app.designer.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lyshra.open.app.designer.domain.WorkflowDefinition;
import com.lyshra.open.app.designer.persistence.entity.WorkflowDefinitionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Converter between WorkflowDefinition domain object and WorkflowDefinitionEntity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowDefinitionConverter {

    private final ObjectMapper objectMapper;

    /**
     * Convert domain object to entity.
     */
    public WorkflowDefinitionEntity toEntity(WorkflowDefinition domain) {
        if (domain == null) {
            return null;
        }

        String tagsJson = null;
        if (domain.getTags() != null && !domain.getTags().isEmpty()) {
            try {
                tagsJson = objectMapper.writeValueAsString(domain.getTags());
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize tags", e);
            }
        }

        return WorkflowDefinitionEntity.builder()
                .id(domain.getId())
                .name(domain.getName())
                .description(domain.getDescription())
                .organization(domain.getOrganization())
                .module(domain.getModule())
                .category(domain.getCategory())
                .tags(tagsJson)
                .createdBy(domain.getCreatedBy())
                .createdAt(domain.getCreatedAt())
                .updatedBy(domain.getUpdatedBy())
                .updatedAt(domain.getUpdatedAt())
                .activeVersionId(domain.getActiveVersionId())
                .lifecycleState(domain.getLifecycleState())
                .build();
    }

    /**
     * Convert entity to domain object.
     */
    public WorkflowDefinition toDomain(WorkflowDefinitionEntity entity) {
        if (entity == null) {
            return null;
        }

        Map<String, String> tags = Collections.emptyMap();
        if (entity.getTags() != null && !entity.getTags().isBlank()) {
            try {
                tags = objectMapper.readValue(entity.getTags(), new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize tags", e);
            }
        }

        return WorkflowDefinition.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .organization(entity.getOrganization())
                .module(entity.getModule())
                .category(entity.getCategory())
                .tags(tags)
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .activeVersionId(entity.getActiveVersionId())
                .lifecycleState(entity.getLifecycleState())
                .build();
    }

    /**
     * Convert Optional entity to Optional domain object.
     */
    public Optional<WorkflowDefinition> toDomainOptional(WorkflowDefinitionEntity entity) {
        return Optional.ofNullable(toDomain(entity));
    }
}
