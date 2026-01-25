package com.lyshra.open.app.designer.service;

import com.lyshra.open.app.designer.domain.ProcessorMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface for processor metadata operations.
 * Provides information about available processors for the designer UI.
 */
public interface ProcessorMetadataService {

    /**
     * Gets all available processors.
     *
     * @return Flux of processor metadata
     */
    Flux<ProcessorMetadata> getAllProcessors();

    /**
     * Gets processors by plugin identifier.
     *
     * @param pluginOrganization the plugin organization
     * @param pluginModule the plugin module
     * @param pluginVersion the plugin version
     * @return Flux of processor metadata
     */
    Flux<ProcessorMetadata> getProcessorsByPlugin(String pluginOrganization, String pluginModule, String pluginVersion);

    /**
     * Gets processors by category.
     *
     * @param category the category
     * @return Flux of processor metadata
     */
    Flux<ProcessorMetadata> getProcessorsByCategory(String category);

    /**
     * Gets a specific processor by identifier.
     *
     * @param identifier the processor identifier
     * @return Mono containing the processor metadata
     */
    Mono<ProcessorMetadata> getProcessor(String identifier);

    /**
     * Searches processors by name.
     *
     * @param namePattern the name pattern to search for
     * @return Flux of matching processor metadata
     */
    Flux<ProcessorMetadata> searchProcessors(String namePattern);

    /**
     * Gets all available processor categories.
     *
     * @return Flux of category names
     */
    Flux<String> getCategories();

    /**
     * Refreshes the processor metadata cache.
     *
     * @return Mono completing when refresh is done
     */
    Mono<Void> refreshCache();
}
