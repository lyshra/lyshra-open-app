package com.lyshra.open.app.core.processors.plugin.processors.humantask;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import com.lyshra.open.app.integration.models.humantask.LyshraOpenAppHumanTaskProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Human task processor for approval workflows.
 * Creates an approval task that pauses workflow execution until
 * a human approves or rejects the request.
 *
 * <p>This processor demonstrates:
 * - Human-in-the-loop workflow execution
 * - Workflow suspension at human task step
 * - Branching based on approval outcome (APPROVED/REJECTED/TIMED_OUT)
 *
 * <p>Output branches:
 * - APPROVED: Human approved the task
 * - REJECTED: Human rejected the task
 * - TIMED_OUT: Task timed out without action (if timeout configured)
 */
public class ApprovalProcessor {

    public static final String PROCESSOR_NAME = "APPROVAL_PROCESSOR";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {

        @NotBlank(message = "{approval.processor.input.title.null}")
        @Size(min = 3, max = 200, message = "{approval.processor.input.title.invalid.length}")
        private String title;

        @Size(max = 2000, message = "{approval.processor.input.description.invalid.length}")
        private String description;

        /**
         * Priority level (1-10, higher = more urgent).
         */
        private int priority = 5;

        /**
         * Users who can approve this task.
         */
        private List<String> assignees = List.of();

        /**
         * Groups/roles who can approve this task.
         */
        private List<String> candidateGroups = List.of();

        /**
         * Timeout in ISO-8601 duration format (e.g., "PT24H" for 24 hours).
         * If null, task waits indefinitely.
         */
        private String timeout;

        /**
         * Whether to auto-approve on timeout.
         */
        private boolean autoApproveOnTimeout = false;

        /**
         * Whether to auto-reject on timeout.
         */
        private boolean autoRejectOnTimeout = false;

        /**
         * Custom data to display to the approver.
         * Uses SpEL expression to extract data from context.
         */
        private String dataExpression;
    }

    @AllArgsConstructor
    @Getter
    public enum ApprovalProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        INVALID_ASSIGNEES("APPROVAL_001", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "approval.processor.error.invalid.assignees",
                "approval.processor.error.invalid.assignees.resolution"),
        TIMEOUT_PARSE_ERROR("APPROVAL_002", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "approval.processor.error.timeout.parse",
                "approval.processor.error.timeout.parse.resolution");

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    /**
     * Builds the approval processor definition.
     * Note: Human task processors don't execute immediately - they suspend
     * the workflow and return when the task is resolved via external signal.
     */
    public static LyshraOpenAppHumanTaskProcessorDefinition.BuildStep build(
            LyshraOpenAppHumanTaskProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name(PROCESSOR_NAME)
                .humanReadableNameTemplate("approval.processor.name")
                .searchTagsCsvTemplate("approval.processor.search.tags")
                .errorCodeEnum(ApprovalProcessorErrorCodes.class)
                .inputConfigType(ApprovalProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new ApprovalProcessorInputConfig(
                                "Approve Purchase Order",
                                "Please review and approve PO #12345",
                                5,
                                List.of("manager@company.com"),
                                List.of("approvers"),
                                "PT24H",
                                false,
                                true,
                                "$data.orderDetails"
                        )
                ))
                .validateInputConfig(input -> {
                    ApprovalProcessorInputConfig config = (ApprovalProcessorInputConfig) input;
                    if (config.getAssignees().isEmpty() && config.getCandidateGroups().isEmpty()) {
                        throw new IllegalArgumentException("Either assignees or candidateGroups must be specified");
                    }
                })
                .humanTaskType(LyshraOpenAppHumanTaskType.APPROVAL)
                .validOutcomeBranches(List.of("APPROVED", "REJECTED", "TIMED_OUT"))
                .process((input, context, facade) -> {
                    // Human task processors return immediately after creating the task.
                    // The actual result will be returned when the task is resolved
                    // via external signal (approval, rejection, or timeout).
                    //
                    // This is a placeholder - the real implementation integrates with
                    // ILyshraOpenAppHumanTaskService to:
                    // 1. Create the human task
                    // 2. Suspend workflow execution
                    // 3. Schedule timeout if configured
                    // 4. Return Mono.never() to indicate suspension
                    //
                    // When the task is resolved, the WorkflowResumptionService will
                    // continue execution with the appropriate branch.

                    ApprovalProcessorInputConfig config = (ApprovalProcessorInputConfig) input;

                    // In a real implementation, this would call:
                    // humanTaskService.createTask(...)
                    //     .flatMap(task -> Mono.never())  // Suspend workflow

                    // For demonstration, return a pending status
                    // Real implementation would integrate with HumanTaskService
                    return Mono.just(LyshraOpenAppProcessorOutput.of("PENDING", null));
                })
                .defaultTimeout(Duration.ofHours(24));
    }
}
