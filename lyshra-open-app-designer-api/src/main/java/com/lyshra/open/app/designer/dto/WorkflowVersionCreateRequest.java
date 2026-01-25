package com.lyshra.open.app.designer.dto;

import com.lyshra.open.app.designer.domain.DesignerMetadata;
import com.lyshra.open.app.designer.domain.WorkflowConnection;
import com.lyshra.open.app.designer.domain.WorkflowContextRetention;
import com.lyshra.open.app.designer.domain.WorkflowStepDefinition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating a workflow version.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersionCreateRequest {

    @NotBlank(message = "Version number is required")
    @Size(max = 50, message = "Version number must not exceed 50 characters")
    private String versionNumber;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private String startStepId;

    private List<WorkflowStepDefinition> steps;

    private Map<String, WorkflowConnection> connections;

    private WorkflowContextRetention contextRetention;

    private DesignerMetadata designerMetadata;
}
