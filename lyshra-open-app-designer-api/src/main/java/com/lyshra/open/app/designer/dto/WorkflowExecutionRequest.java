package com.lyshra.open.app.designer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for starting a workflow execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecutionRequest {

    private Map<String, Object> inputData;

    private Map<String, Object> variables;

    private String correlationId;

    private Map<String, String> metadata;

    private String versionId;
}
