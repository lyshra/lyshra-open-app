package com.lyshra.open.app.designer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for creating a workflow definition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinitionCreateRequest {

    @NotBlank(message = "Workflow name is required")
    @Size(min = 1, max = 255, message = "Workflow name must be between 1 and 255 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @NotBlank(message = "Organization is required")
    private String organization;

    @NotBlank(message = "Module is required")
    private String module;

    private String category;

    private Map<String, String> tags;
}
