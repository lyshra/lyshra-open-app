package com.lyshra.open.app.designer.controller;

import com.lyshra.open.app.designer.domain.WorkflowVersion;
import com.lyshra.open.app.designer.dto.ValidationResult;
import com.lyshra.open.app.designer.service.WorkflowValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller for workflow validation operations.
 */
@RestController
@RequestMapping("/api/v1/validation")
public class ValidationController {

    private final WorkflowValidationService validationService;

    public ValidationController(WorkflowValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping("/workflow")
    public Mono<ResponseEntity<ValidationResult>> validateWorkflow(@RequestBody WorkflowVersion version) {
        return validationService.validateWorkflow(version)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/connections")
    public Mono<ResponseEntity<ValidationResult>> validateConnections(@RequestBody WorkflowVersion version) {
        return validationService.validateConnections(version)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/steps")
    public Mono<ResponseEntity<ValidationResult>> validateStepConfigurations(@RequestBody WorkflowVersion version) {
        return validationService.validateStepConfigurations(version)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/start-step")
    public Mono<ResponseEntity<ValidationResult>> validateStartStep(@RequestBody WorkflowVersion version) {
        return validationService.validateStartStep(version)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/error-handling")
    public Mono<ResponseEntity<ValidationResult>> validateErrorHandling(@RequestBody WorkflowVersion version) {
        return validationService.validateErrorHandling(version)
                .map(ResponseEntity::ok);
    }
}
