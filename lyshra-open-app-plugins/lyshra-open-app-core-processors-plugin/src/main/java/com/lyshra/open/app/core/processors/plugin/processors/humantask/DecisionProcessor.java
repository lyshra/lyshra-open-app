package com.lyshra.open.app.core.processors.plugin.processors.humantask;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import com.lyshra.open.app.integration.models.humantask.LyshraOpenAppHumanTaskProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Human task processor for multi-option decisions.
 * Creates a task that presents the user with multiple options
 * and routes the workflow based on their selection.
 *
 * <p>This processor is useful for:
 * - Routing workflows based on human judgment
 * - Triaging or categorizing items
 * - Selecting from predefined options
 *
 * <p>Output branches:
 * - Dynamic based on configured options (option values become branch names)
 * - TIMED_OUT: Task timed out without action (if timeout configured)
 */
public class DecisionProcessor {

    public static final String PROCESSOR_NAME = "DECISION_PROCESSOR";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecisionProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {

        @NotBlank(message = "{decision.processor.input.title.null}")
        @Size(min = 3, max = 200, message = "{decision.processor.input.title.invalid.length}")
        private String title;

        @Size(max = 2000, message = "{decision.processor.input.description.invalid.length}")
        private String description;

        /**
         * The question or decision prompt to display.
         */
        @NotBlank(message = "{decision.processor.input.question.null}")
        private String question;

        /**
         * Priority level (1-10, higher = more urgent).
         */
        private int priority = 5;

        /**
         * Users who can make this decision.
         */
        private List<String> assignees = List.of();

        /**
         * Groups/roles who can make this decision.
         */
        private List<String> candidateGroups = List.of();

        /**
         * Timeout in ISO-8601 duration format.
         */
        private String timeout;

        /**
         * Decision options. Each option has a value (branch name) and label.
         */
        @NotEmpty(message = "{decision.processor.input.options.empty}")
        private List<DecisionOption> options;

        /**
         * Whether to allow additional comments with the decision.
         */
        private boolean allowComments = true;

        /**
         * Default option to select on timeout (if any).
         */
        private String defaultOnTimeout;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecisionOption {
        /**
         * The value/branch name for this option.
         */
        @NotBlank
        private String value;

        /**
         * Display label for this option.
         */
        @NotBlank
        private String label;

        /**
         * Optional description/help text for this option.
         */
        private String description;

        /**
         * Optional color/style hint for UI rendering.
         */
        private String style;
    }

    @AllArgsConstructor
    @Getter
    public enum DecisionProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        NO_OPTIONS("DECISION_001", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "decision.processor.error.no.options",
                "decision.processor.error.no.options.resolution"),
        DUPLICATE_VALUES("DECISION_002", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "decision.processor.error.duplicate.values",
                "decision.processor.error.duplicate.values.resolution");

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    /**
     * Builds the decision processor definition.
     */
    public static LyshraOpenAppHumanTaskProcessorDefinition.BuildStep build(
            LyshraOpenAppHumanTaskProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name(PROCESSOR_NAME)
                .humanReadableNameTemplate("decision.processor.name")
                .searchTagsCsvTemplate("decision.processor.search.tags")
                .errorCodeEnum(DecisionProcessorErrorCodes.class)
                .inputConfigType(DecisionProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new DecisionProcessorInputConfig(
                                "Triage Support Ticket",
                                "Review the ticket and assign appropriate priority and category",
                                "How should this support ticket be handled?",
                                7,
                                List.of(),
                                List.of("support-team"),
                                "PT2H",
                                List.of(
                                        new DecisionOption("ESCALATE", "Escalate to Engineering",
                                                "Technical issue requiring engineering involvement", "danger"),
                                        new DecisionOption("SUPPORT", "Handle in Support",
                                                "Can be resolved by support team", "primary"),
                                        new DecisionOption("SELF_SERVICE", "Direct to Self-Service",
                                                "Customer can resolve using documentation", "secondary"),
                                        new DecisionOption("CLOSE", "Close - No Action Needed",
                                                "Not a valid support request", "default")
                                ),
                                true,
                                "SUPPORT"
                        )
                ))
                .validateInputConfig(input -> {
                    DecisionProcessorInputConfig config = (DecisionProcessorInputConfig) input;

                    // Validate options
                    if (config.getOptions() == null || config.getOptions().isEmpty()) {
                        throw new IllegalArgumentException("At least one decision option must be defined");
                    }

                    // Validate unique values
                    long uniqueValues = config.getOptions().stream()
                            .map(DecisionOption::getValue)
                            .distinct()
                            .count();
                    if (uniqueValues != config.getOptions().size()) {
                        throw new IllegalArgumentException("Decision option values must be unique");
                    }

                    // Validate defaultOnTimeout if set
                    if (config.getDefaultOnTimeout() != null) {
                        boolean hasDefault = config.getOptions().stream()
                                .anyMatch(opt -> opt.getValue().equals(config.getDefaultOnTimeout()));
                        if (!hasDefault) {
                            throw new IllegalArgumentException("defaultOnTimeout must match one of the option values");
                        }
                    }
                })
                .humanTaskType(LyshraOpenAppHumanTaskType.DECISION)
                // Dynamic branches based on options - will be populated at runtime
                .validOutcomeBranches(List.of("TIMED_OUT"))
                .process((input, context, facade) -> {
                    DecisionProcessorInputConfig config = (DecisionProcessorInputConfig) input;

                    // The valid branches for this instance are the option values plus TIMED_OUT
                    List<String> validBranches = config.getOptions().stream()
                            .map(DecisionOption::getValue)
                            .collect(Collectors.toList());
                    validBranches.add("TIMED_OUT");

                    // Placeholder - real implementation integrates with HumanTaskService
                    return Mono.just(LyshraOpenAppProcessorOutput.of("PENDING", null));
                })
                .defaultTimeout(Duration.ofHours(8));
    }
}
