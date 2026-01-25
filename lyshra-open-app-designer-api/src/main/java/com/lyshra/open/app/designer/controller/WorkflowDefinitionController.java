package com.lyshra.open.app.designer.controller;

import com.lyshra.open.app.designer.domain.WorkflowDefinition;
import com.lyshra.open.app.designer.domain.WorkflowLifecycleState;
import com.lyshra.open.app.designer.domain.WorkflowVersion;
import com.lyshra.open.app.designer.dto.WorkflowDefinitionCreateRequest;
import com.lyshra.open.app.designer.dto.WorkflowDefinitionUpdateRequest;
import com.lyshra.open.app.designer.dto.WorkflowVersionCreateRequest;
import com.lyshra.open.app.designer.service.WorkflowDesignerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for workflow definition operations.
 */
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowDefinitionController {

    private final WorkflowDesignerService workflowDesignerService;

    public WorkflowDefinitionController(WorkflowDesignerService workflowDesignerService) {
        this.workflowDesignerService = workflowDesignerService;
    }

    @PostMapping
    public Mono<ResponseEntity<WorkflowDefinition>> createWorkflow(
            @Valid @RequestBody WorkflowDefinitionCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserId(userDetails);
        return workflowDesignerService.createWorkflowDefinition(request, userId)
                .map(definition -> ResponseEntity.status(HttpStatus.CREATED).body(definition));
    }

    @GetMapping
    public Flux<WorkflowDefinition> getAllWorkflows(
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) WorkflowLifecycleState state,
            @RequestParam(required = false) String search) {
        if (search != null && !search.isBlank()) {
            return workflowDesignerService.searchWorkflowDefinitions(search);
        }
        if (organization != null && !organization.isBlank()) {
            return workflowDesignerService.getWorkflowDefinitionsByOrganization(organization);
        }
        if (state != null) {
            return workflowDesignerService.getWorkflowDefinitionsByState(state);
        }
        return workflowDesignerService.getAllWorkflowDefinitions();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<WorkflowDefinition>> getWorkflow(@PathVariable String id) {
        return workflowDesignerService.getWorkflowDefinition(id)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<WorkflowDefinition>> updateWorkflow(
            @PathVariable String id,
            @Valid @RequestBody WorkflowDefinitionUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserId(userDetails);
        return workflowDesignerService.updateWorkflowDefinition(id, request, userId)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteWorkflow(@PathVariable String id) {
        return workflowDesignerService.deleteWorkflowDefinition(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @PutMapping("/{id}/state")
    public Mono<ResponseEntity<WorkflowDefinition>> changeLifecycleState(
            @PathVariable String id,
            @RequestParam WorkflowLifecycleState state,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserId(userDetails);
        return workflowDesignerService.changeLifecycleState(id, state, userId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{id}/duplicate")
    public Mono<ResponseEntity<WorkflowDefinition>> duplicateWorkflow(
            @PathVariable String id,
            @RequestParam String newName,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserId(userDetails);
        return workflowDesignerService.duplicateWorkflowDefinition(id, newName, userId)
                .map(definition -> ResponseEntity.status(HttpStatus.CREATED).body(definition));
    }

    @PostMapping("/{workflowId}/versions")
    public Mono<ResponseEntity<WorkflowVersion>> createVersion(
            @PathVariable String workflowId,
            @Valid @RequestBody WorkflowVersionCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserId(userDetails);
        return workflowDesignerService.createWorkflowVersion(workflowId, request, userId)
                .map(version -> ResponseEntity.status(HttpStatus.CREATED).body(version));
    }

    @GetMapping("/{workflowId}/versions")
    public Flux<WorkflowVersion> getVersions(@PathVariable String workflowId) {
        return workflowDesignerService.getWorkflowVersions(workflowId);
    }

    @GetMapping("/{workflowId}/versions/active")
    public Mono<ResponseEntity<WorkflowVersion>> getActiveVersion(@PathVariable String workflowId) {
        return workflowDesignerService.getActiveVersion(workflowId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/versions/{versionId}")
    public Mono<ResponseEntity<WorkflowVersion>> getVersion(@PathVariable String versionId) {
        return workflowDesignerService.getWorkflowVersion(versionId)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/versions/{versionId}/activate")
    public Mono<ResponseEntity<WorkflowVersion>> activateVersion(
            @PathVariable String versionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserId(userDetails);
        return workflowDesignerService.activateVersion(versionId, userId)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/versions/{versionId}/deprecate")
    public Mono<ResponseEntity<WorkflowVersion>> deprecateVersion(
            @PathVariable String versionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserId(userDetails);
        return workflowDesignerService.deprecateVersion(versionId, userId)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{workflowId}/rollback")
    public Mono<ResponseEntity<WorkflowVersion>> rollbackToVersion(
            @PathVariable String workflowId,
            @RequestParam String targetVersionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserId(userDetails);
        return workflowDesignerService.rollbackToVersion(workflowId, targetVersionId, userId)
                .map(ResponseEntity::ok);
    }

    private String getUserId(UserDetails userDetails) {
        return userDetails != null ? userDetails.getUsername() : "anonymous";
    }
}
