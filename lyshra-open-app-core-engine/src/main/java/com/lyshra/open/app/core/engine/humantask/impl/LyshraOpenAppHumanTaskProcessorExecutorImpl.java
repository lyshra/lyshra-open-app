package com.lyshra.open.app.core.engine.humantask.impl;

import com.lyshra.open.app.core.engine.humantask.ILyshraOpenAppHumanTaskProcessorExecutor;
import com.lyshra.open.app.core.engine.humantask.ILyshraOpenAppHumanTaskService;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTask;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor.ILyshraOpenAppHumanTaskConfig;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import com.lyshra.open.app.integration.models.humantask.LyshraOpenAppHumanTaskProcessorOutput;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of the human task processor executor.
 * Creates human tasks, suspends workflows, and returns suspension outputs.
 *
 * <p>Design Pattern: Singleton + Mediator
 * - Single instance coordinates all human task processor executions
 * - Mediates between HumanTaskService, processor config, and workflow state
 */
@Slf4j
public class LyshraOpenAppHumanTaskProcessorExecutorImpl implements ILyshraOpenAppHumanTaskProcessorExecutor {

    private final ILyshraOpenAppHumanTaskService humanTaskService;

    private LyshraOpenAppHumanTaskProcessorExecutorImpl() {
        this.humanTaskService = LyshraOpenAppHumanTaskServiceImpl.getInstance();
    }

    private static final class SingletonHelper {
        private static final ILyshraOpenAppHumanTaskProcessorExecutor INSTANCE =
                new LyshraOpenAppHumanTaskProcessorExecutorImpl();
    }

    public static ILyshraOpenAppHumanTaskProcessorExecutor getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public Mono<LyshraOpenAppHumanTaskProcessorOutput> execute(
            ILyshraOpenAppHumanTaskProcessor processor,
            ILyshraOpenAppProcessorInputConfig inputConfig,
            String workflowInstanceId,
            String workflowStepId,
            ILyshraOpenAppContext context) {

        return executeWithData(processor, inputConfig, workflowInstanceId, workflowStepId,
                Collections.emptyMap(), context);
    }

    @Override
    public Mono<LyshraOpenAppHumanTaskProcessorOutput> executeWithData(
            ILyshraOpenAppHumanTaskProcessor processor,
            ILyshraOpenAppProcessorInputConfig inputConfig,
            String workflowInstanceId,
            String workflowStepId,
            Map<String, Object> taskData,
            ILyshraOpenAppContext context) {

        return Mono.defer(() -> {
            log.info("Executing human task processor: workflowId={}, stepId={}, taskType={}",
                    workflowInstanceId, workflowStepId, processor.getHumanTaskType());

            // Build task configuration from processor input
            ILyshraOpenAppHumanTaskConfig taskConfig = buildTaskConfig(processor, inputConfig);

            // Prepare task data - merge context data with provided task data
            Map<String, Object> mergedTaskData = prepareTaskData(taskData, context);

            // Get the human task type
            LyshraOpenAppHumanTaskType taskType = processor.getHumanTaskType();

            // Create the human task and suspend workflow
            return humanTaskService.createTaskWithData(
                            workflowInstanceId,
                            workflowStepId,
                            taskType,
                            taskConfig,
                            mergedTaskData,
                            context
                    )
                    .map(task -> createSuspensionOutput(task, workflowInstanceId, workflowStepId, context))
                    .doOnSuccess(output -> log.info(
                            "Human task created and workflow suspended: taskId={}, workflowId={}, stepId={}",
                            output.getTaskId(), workflowInstanceId, workflowStepId))
                    .doOnError(error -> log.error(
                            "Failed to create human task: workflowId={}, stepId={}, error={}",
                            workflowInstanceId, workflowStepId, error.getMessage()));
        });
    }

    @Override
    public boolean isHumanTaskProcessor(Object processor) {
        return processor instanceof ILyshraOpenAppHumanTaskProcessor;
    }

    /**
     * Builds the task configuration from processor definition and input config.
     */
    private ILyshraOpenAppHumanTaskConfig buildTaskConfig(
            ILyshraOpenAppHumanTaskProcessor processor,
            ILyshraOpenAppProcessorInputConfig inputConfig) {

        // Get the task config builder from the processor
        ILyshraOpenAppHumanTaskProcessor.ILyshraOpenAppHumanTaskConfigBuilder builder =
                processor.getTaskConfigBuilder();

        // Extract configuration from input - implementation depends on input config type
        // Here we use reflection or duck typing to extract common fields
        HumanTaskInputConfigExtractor extractor = new HumanTaskInputConfigExtractor(inputConfig);

        // Build configuration with extracted values and processor defaults
        builder.title(extractor.getTitle().orElse("Human Task"));
        builder.description(extractor.getDescription().orElse(null));
        builder.priority(extractor.getPriority().orElse(5));

        extractor.getAssignees().ifPresent(builder::assignees);
        extractor.getCandidateGroups().ifPresent(builder::candidateGroups);

        // Use input timeout or processor default
        Duration timeout = extractor.getTimeout()
                .orElseGet(() -> processor.getDefaultTimeout().orElse(null));
        if (timeout != null) {
            builder.timeout(timeout);
        }

        // Set escalation config if available
        processor.getDefaultEscalationConfig().ifPresent(builder::escalation);

        // Set form schema if available
        processor.getDefaultFormSchema().ifPresent(builder::formSchema);

        return builder.build();
    }

    /**
     * Prepares the task data by merging context data with provided task data.
     */
    private Map<String, Object> prepareTaskData(Map<String, Object> providedData, ILyshraOpenAppContext context) {
        Map<String, Object> taskData = new HashMap<>();

        // Include relevant context data
        if (context.getData() != null) {
            taskData.put("contextData", context.getData());
        }

        // Include context variables
        if (context.getVariables() != null && !context.getVariables().isEmpty()) {
            taskData.put("variables", new HashMap<>(context.getVariables()));
        }

        // Merge with provided task data (provided data takes precedence)
        if (providedData != null && !providedData.isEmpty()) {
            taskData.putAll(providedData);
        }

        return taskData;
    }

    /**
     * Creates the suspension output after task creation.
     */
    private LyshraOpenAppHumanTaskProcessorOutput createSuspensionOutput(
            ILyshraOpenAppHumanTask task,
            String workflowInstanceId,
            String workflowStepId,
            ILyshraOpenAppContext context) {

        // Preserve context for later resumption
        Map<String, Object> preservedContext = new HashMap<>();
        if (context.getData() != null) {
            preservedContext.put("data", context.getData());
        }
        if (context.getVariables() != null) {
            preservedContext.put("variables", new HashMap<>(context.getVariables()));
        }

        return LyshraOpenAppHumanTaskProcessorOutput.ofSuspendedWithContext(
                task.getTaskId(),
                workflowInstanceId,
                workflowStepId,
                preservedContext
        );
    }

    /**
     * Helper class to extract human task configuration from various input config types.
     * Uses reflection to handle different processor input configurations.
     */
    private static class HumanTaskInputConfigExtractor {
        private final ILyshraOpenAppProcessorInputConfig inputConfig;

        public HumanTaskInputConfigExtractor(ILyshraOpenAppProcessorInputConfig inputConfig) {
            this.inputConfig = inputConfig;
        }

        public Optional<String> getTitle() {
            return getFieldValue("title", String.class);
        }

        public Optional<String> getDescription() {
            return getFieldValue("description", String.class);
        }

        public Optional<Integer> getPriority() {
            return getFieldValue("priority", Integer.class);
        }

        @SuppressWarnings("unchecked")
        public Optional<List<String>> getAssignees() {
            return getFieldValue("assignees", List.class)
                    .map(list -> (List<String>) list);
        }

        @SuppressWarnings("unchecked")
        public Optional<List<String>> getCandidateGroups() {
            return getFieldValue("candidateGroups", List.class)
                    .map(list -> (List<String>) list);
        }

        public Optional<Duration> getTimeout() {
            // Try to get timeout as Duration first
            Optional<Duration> durationTimeout = getFieldValue("timeout", Duration.class);
            if (durationTimeout.isPresent()) {
                return durationTimeout;
            }

            // Try to get timeout as String and parse
            return getFieldValue("timeout", String.class)
                    .filter(s -> s != null && !s.isEmpty())
                    .map(Duration::parse);
        }

        @SuppressWarnings("unchecked")
        private <T> Optional<T> getFieldValue(String fieldName, Class<T> type) {
            if (inputConfig == null) {
                return Optional.empty();
            }

            try {
                // Try to get the field directly
                java.lang.reflect.Field field = inputConfig.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(inputConfig);
                if (value != null && type.isInstance(value)) {
                    return Optional.of((T) value);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // Field not found or not accessible, try getter method
                try {
                    String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                    java.lang.reflect.Method getter = inputConfig.getClass().getMethod(getterName);
                    Object value = getter.invoke(inputConfig);
                    if (value != null && type.isInstance(value)) {
                        return Optional.of((T) value);
                    }
                } catch (Exception ex) {
                    // Getter not found or failed
                    log.trace("Could not extract field '{}' from input config: {}", fieldName, ex.getMessage());
                }
            }

            return Optional.empty();
        }
    }
}
