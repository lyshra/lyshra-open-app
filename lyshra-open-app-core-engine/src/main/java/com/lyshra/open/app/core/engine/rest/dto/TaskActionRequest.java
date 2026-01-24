package com.lyshra.open.app.core.engine.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for task actions (claim, complete, approve, reject, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskActionRequest {

    /**
     * The user performing the action.
     */
    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * Optional reason or comment for the action.
     */
    private String reason;

    /**
     * Optional result data (for completion with form data).
     */
    private Map<String, Object> resultData;

    /**
     * Optional delegate target user ID (for delegation).
     */
    private String delegateToUserId;
}
