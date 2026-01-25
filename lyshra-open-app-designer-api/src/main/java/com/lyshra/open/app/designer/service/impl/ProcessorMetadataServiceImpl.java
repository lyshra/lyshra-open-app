package com.lyshra.open.app.designer.service.impl;

import com.lyshra.open.app.designer.domain.FieldOption;
import com.lyshra.open.app.designer.domain.ProcessorFieldType;
import com.lyshra.open.app.designer.domain.ProcessorInputField;
import com.lyshra.open.app.designer.domain.ProcessorMetadata;
import com.lyshra.open.app.designer.exception.ResourceNotFoundException;
import com.lyshra.open.app.designer.service.ProcessorMetadataService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of ProcessorMetadataService.
 * Provides metadata about available processors for the designer UI.
 */
@Service
public class ProcessorMetadataServiceImpl implements ProcessorMetadataService {

    private final Map<String, ProcessorMetadata> processorCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadCoreProcessors();
    }

    private void loadCoreProcessors() {
        addProcessor(createIfProcessor());
        addProcessor(createSwitchProcessor());
        addProcessor(createJavaScriptProcessor());
        addProcessor(createApiProcessor());
        addProcessor(createListFilterProcessor());
        addProcessor(createListSortProcessor());
        addProcessor(createDateAddProcessor());
        addProcessor(createDateCompareProcessor());
    }

    private void addProcessor(ProcessorMetadata processor) {
        processorCache.put(processor.getIdentifier(), processor);
    }

    private ProcessorMetadata createIfProcessor() {
        return ProcessorMetadata.builder()
                .pluginOrganization("com.lyshra.open.app")
                .pluginModule("core")
                .pluginVersion("1.0.0")
                .processorName("IF_PROCESSOR")
                .displayName("If Condition")
                .description("Evaluates a boolean expression and branches execution")
                .category("Control Flow")
                .icon("code-branch")
                .inputFields(List.of(
                        ProcessorInputField.builder()
                                .name("expression")
                                .displayName("Expression")
                                .description("JavaScript boolean expression to evaluate")
                                .type(ProcessorFieldType.CODE)
                                .required(true)
                                .expressionSupport("GRAALVM_JS")
                                .build()
                ))
                .possibleBranches(List.of("true", "false"))
                .sampleConfig(Map.of("expression", "$data.amount > 100"))
                .build();
    }

    private ProcessorMetadata createSwitchProcessor() {
        return ProcessorMetadata.builder()
                .pluginOrganization("com.lyshra.open.app")
                .pluginModule("core")
                .pluginVersion("1.0.0")
                .processorName("SWITCH_PROCESSOR")
                .displayName("Switch")
                .description("Multi-way branching based on expression evaluation")
                .category("Control Flow")
                .icon("random")
                .inputFields(List.of(
                        ProcessorInputField.builder()
                                .name("expression")
                                .displayName("Expression")
                                .description("JavaScript expression to evaluate")
                                .type(ProcessorFieldType.CODE)
                                .required(true)
                                .expressionSupport("GRAALVM_JS")
                                .build(),
                        ProcessorInputField.builder()
                                .name("cases")
                                .displayName("Cases")
                                .description("Map of case values to branch names")
                                .type(ProcessorFieldType.MAP)
                                .required(true)
                                .build()
                ))
                .possibleBranches(List.of("case1", "case2", "default"))
                .sampleConfig(Map.of(
                        "expression", "$data.status",
                        "cases", Map.of("ACTIVE", "active", "INACTIVE", "inactive")
                ))
                .build();
    }

    private ProcessorMetadata createJavaScriptProcessor() {
        return ProcessorMetadata.builder()
                .pluginOrganization("com.lyshra.open.app")
                .pluginModule("core")
                .pluginVersion("1.0.0")
                .processorName("JAVASCRIPT_PROCESSOR")
                .displayName("JavaScript")
                .description("Execute custom JavaScript code")
                .category("Scripting")
                .icon("js")
                .inputFields(List.of(
                        ProcessorInputField.builder()
                                .name("script")
                                .displayName("Script")
                                .description("JavaScript code to execute")
                                .type(ProcessorFieldType.CODE)
                                .required(true)
                                .expressionSupport("GRAALVM_JS")
                                .build()
                ))
                .possibleBranches(List.of("default"))
                .sampleConfig(Map.of("script", "return { result: $data.value * 2 };"))
                .build();
    }

    private ProcessorMetadata createApiProcessor() {
        return ProcessorMetadata.builder()
                .pluginOrganization("com.lyshra.open.app")
                .pluginModule("core")
                .pluginVersion("1.0.0")
                .processorName("API_PROCESSOR")
                .displayName("API Call")
                .description("Make HTTP API calls to external services")
                .category("Integration")
                .icon("globe")
                .inputFields(List.of(
                        ProcessorInputField.builder()
                                .name("serviceName")
                                .displayName("Service Name")
                                .description("Name of the API service")
                                .type(ProcessorFieldType.STRING)
                                .required(true)
                                .build(),
                        ProcessorInputField.builder()
                                .name("endpointName")
                                .displayName("Endpoint Name")
                                .description("Name of the API endpoint")
                                .type(ProcessorFieldType.STRING)
                                .required(true)
                                .build(),
                        ProcessorInputField.builder()
                                .name("method")
                                .displayName("HTTP Method")
                                .description("HTTP method to use")
                                .type(ProcessorFieldType.SELECT)
                                .required(true)
                                .defaultValue("GET")
                                .options(List.of(
                                        FieldOption.builder().value("GET").label("GET").build(),
                                        FieldOption.builder().value("POST").label("POST").build(),
                                        FieldOption.builder().value("PUT").label("PUT").build(),
                                        FieldOption.builder().value("DELETE").label("DELETE").build(),
                                        FieldOption.builder().value("PATCH").label("PATCH").build()
                                ))
                                .build(),
                        ProcessorInputField.builder()
                                .name("body")
                                .displayName("Request Body")
                                .description("Request body for POST/PUT/PATCH")
                                .type(ProcessorFieldType.JSON)
                                .required(false)
                                .build()
                ))
                .possibleBranches(List.of("success", "error"))
                .sampleConfig(Map.of(
                        "serviceName", "userService",
                        "endpointName", "getUser",
                        "method", "GET"
                ))
                .build();
    }

    private ProcessorMetadata createListFilterProcessor() {
        return ProcessorMetadata.builder()
                .pluginOrganization("com.lyshra.open.app")
                .pluginModule("core")
                .pluginVersion("1.0.0")
                .processorName("LIST_FILTER_PROCESSOR")
                .displayName("List Filter")
                .description("Filter items in a list based on a condition")
                .category("Data Operations")
                .icon("filter")
                .inputFields(List.of(
                        ProcessorInputField.builder()
                                .name("listPath")
                                .displayName("List Path")
                                .description("Path to the list in context data")
                                .type(ProcessorFieldType.STRING)
                                .required(true)
                                .build(),
                        ProcessorInputField.builder()
                                .name("filterExpression")
                                .displayName("Filter Expression")
                                .description("JavaScript expression to filter items")
                                .type(ProcessorFieldType.CODE)
                                .required(true)
                                .expressionSupport("GRAALVM_JS")
                                .build()
                ))
                .possibleBranches(List.of("default"))
                .sampleConfig(Map.of(
                        "listPath", "$data.items",
                        "filterExpression", "item.active === true"
                ))
                .build();
    }

    private ProcessorMetadata createListSortProcessor() {
        return ProcessorMetadata.builder()
                .pluginOrganization("com.lyshra.open.app")
                .pluginModule("core")
                .pluginVersion("1.0.0")
                .processorName("LIST_SORT_PROCESSOR")
                .displayName("List Sort")
                .description("Sort items in a list")
                .category("Data Operations")
                .icon("sort")
                .inputFields(List.of(
                        ProcessorInputField.builder()
                                .name("listPath")
                                .displayName("List Path")
                                .description("Path to the list in context data")
                                .type(ProcessorFieldType.STRING)
                                .required(true)
                                .build(),
                        ProcessorInputField.builder()
                                .name("sortBy")
                                .displayName("Sort By")
                                .description("Field to sort by")
                                .type(ProcessorFieldType.STRING)
                                .required(true)
                                .build(),
                        ProcessorInputField.builder()
                                .name("ascending")
                                .displayName("Ascending")
                                .description("Sort in ascending order")
                                .type(ProcessorFieldType.BOOLEAN)
                                .required(false)
                                .defaultValue(true)
                                .build()
                ))
                .possibleBranches(List.of("default"))
                .sampleConfig(Map.of(
                        "listPath", "$data.items",
                        "sortBy", "name",
                        "ascending", true
                ))
                .build();
    }

    private ProcessorMetadata createDateAddProcessor() {
        return ProcessorMetadata.builder()
                .pluginOrganization("com.lyshra.open.app")
                .pluginModule("core")
                .pluginVersion("1.0.0")
                .processorName("DATE_ADD_PROCESSOR")
                .displayName("Date Add")
                .description("Add time to a date")
                .category("Date Operations")
                .icon("calendar-plus")
                .inputFields(List.of(
                        ProcessorInputField.builder()
                                .name("datePath")
                                .displayName("Date Path")
                                .description("Path to the date in context data")
                                .type(ProcessorFieldType.STRING)
                                .required(true)
                                .build(),
                        ProcessorInputField.builder()
                                .name("amount")
                                .displayName("Amount")
                                .description("Amount to add")
                                .type(ProcessorFieldType.NUMBER)
                                .required(true)
                                .build(),
                        ProcessorInputField.builder()
                                .name("unit")
                                .displayName("Unit")
                                .description("Time unit")
                                .type(ProcessorFieldType.SELECT)
                                .required(true)
                                .options(List.of(
                                        FieldOption.builder().value("DAYS").label("Days").build(),
                                        FieldOption.builder().value("HOURS").label("Hours").build(),
                                        FieldOption.builder().value("MINUTES").label("Minutes").build(),
                                        FieldOption.builder().value("SECONDS").label("Seconds").build()
                                ))
                                .build()
                ))
                .possibleBranches(List.of("default"))
                .sampleConfig(Map.of(
                        "datePath", "$data.createdAt",
                        "amount", 7,
                        "unit", "DAYS"
                ))
                .build();
    }

    private ProcessorMetadata createDateCompareProcessor() {
        return ProcessorMetadata.builder()
                .pluginOrganization("com.lyshra.open.app")
                .pluginModule("core")
                .pluginVersion("1.0.0")
                .processorName("DATE_COMPARE_PROCESSOR")
                .displayName("Date Compare")
                .description("Compare two dates")
                .category("Date Operations")
                .icon("calendar-check")
                .inputFields(List.of(
                        ProcessorInputField.builder()
                                .name("date1Path")
                                .displayName("First Date Path")
                                .description("Path to the first date")
                                .type(ProcessorFieldType.STRING)
                                .required(true)
                                .build(),
                        ProcessorInputField.builder()
                                .name("date2Path")
                                .displayName("Second Date Path")
                                .description("Path to the second date")
                                .type(ProcessorFieldType.STRING)
                                .required(true)
                                .build()
                ))
                .possibleBranches(List.of("before", "after", "equal"))
                .sampleConfig(Map.of(
                        "date1Path", "$data.startDate",
                        "date2Path", "$data.endDate"
                ))
                .build();
    }

    @Override
    public Flux<ProcessorMetadata> getAllProcessors() {
        return Flux.fromIterable(processorCache.values());
    }

    @Override
    public Flux<ProcessorMetadata> getProcessorsByPlugin(
            String pluginOrganization, String pluginModule, String pluginVersion) {
        return Flux.fromIterable(processorCache.values())
                .filter(p -> pluginOrganization.equals(p.getPluginOrganization()) &&
                        pluginModule.equals(p.getPluginModule()) &&
                        pluginVersion.equals(p.getPluginVersion()));
    }

    @Override
    public Flux<ProcessorMetadata> getProcessorsByCategory(String category) {
        return Flux.fromIterable(processorCache.values())
                .filter(p -> category.equals(p.getCategory()));
    }

    @Override
    public Mono<ProcessorMetadata> getProcessor(String identifier) {
        ProcessorMetadata processor = processorCache.get(identifier);
        if (processor == null) {
            return Mono.error(new ResourceNotFoundException("Processor", identifier));
        }
        return Mono.just(processor);
    }

    @Override
    public Flux<ProcessorMetadata> searchProcessors(String namePattern) {
        String lowerPattern = namePattern.toLowerCase();
        return Flux.fromIterable(processorCache.values())
                .filter(p -> p.getDisplayName().toLowerCase().contains(lowerPattern) ||
                        p.getProcessorName().toLowerCase().contains(lowerPattern) ||
                        (p.getDescription() != null &&
                                p.getDescription().toLowerCase().contains(lowerPattern)));
    }

    @Override
    public Flux<String> getCategories() {
        return Flux.fromIterable(processorCache.values())
                .map(ProcessorMetadata::getCategory)
                .distinct();
    }

    @Override
    public Mono<Void> refreshCache() {
        return Mono.fromRunnable(() -> {
            processorCache.clear();
            loadCoreProcessors();
        });
    }
}
