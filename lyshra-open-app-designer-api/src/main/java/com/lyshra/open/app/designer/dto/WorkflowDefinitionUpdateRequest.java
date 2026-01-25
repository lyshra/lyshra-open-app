package com.lyshra.open.app.designer.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for updating a workflow definition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionUpdateRequest {

    @Size(min = 1, max = 255, message = "Workflow name must be between 1 and 255 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private String category;

    private Map<String, String> tags;
}
