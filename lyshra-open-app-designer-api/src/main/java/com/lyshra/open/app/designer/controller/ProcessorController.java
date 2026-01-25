package com.lyshra.open.app.designer.controller;

import com.lyshra.open.app.designer.domain.ProcessorMetadata;
import com.lyshra.open.app.designer.service.ProcessorMetadataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for processor metadata operations.
 */
@RestController
@RequestMapping("/api/v1/processors")
public class ProcessorController {

    private final ProcessorMetadataService processorMetadataService;

    public ProcessorController(ProcessorMetadataService processorMetadataService) {
        this.processorMetadataService = processorMetadataService;
    }

    @GetMapping
    public Flux<ProcessorMetadata> getAllProcessors(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        if (search != null && !search.isBlank()) {
            return processorMetadataService.searchProcessors(search);
        }
        if (category != null && !category.isBlank()) {
            return processorMetadataService.getProcessorsByCategory(category);
        }
        return processorMetadataService.getAllProcessors();
    }

    @GetMapping("/plugins/{organization}/{module}/{version}")
    public Flux<ProcessorMetadata> getProcessorsByPlugin(
            @PathVariable String organization,
            @PathVariable String module,
            @PathVariable String version) {
        return processorMetadataService.getProcessorsByPlugin(organization, module, version);
    }

    @GetMapping("/{identifier}")
    public Mono<ResponseEntity<ProcessorMetadata>> getProcessor(@PathVariable String identifier) {
        return processorMetadataService.getProcessor(identifier)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/categories")
    public Flux<String> getCategories() {
        return processorMetadataService.getCategories();
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<Void>> refreshCache() {
        return processorMetadataService.refreshCache()
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }
}
