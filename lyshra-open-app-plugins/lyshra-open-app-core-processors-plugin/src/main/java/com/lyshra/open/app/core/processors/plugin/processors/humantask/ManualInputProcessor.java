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
import java.util.Map;

/**
 * Human task processor for collecting manual data input.
 * Creates a task that pauses workflow execution until a human
 * provides the required data via a form.
 *
 * <p>This processor demonstrates:
 * - Form-based data collection in workflows
 * - Dynamic form schema definition
 * - Data validation before workflow continuation
 *
 * <p>Output branches:
 * - DEFAULT: Human submitted the form successfully
 * - CANCELLED: Human cancelled the task
 * - TIMED_OUT: Task timed out without action (if timeout configured)
 *
 * <p>The submitted form data is merged into the workflow context.
 */
public class ManualInputProcessor {

    public static final String PROCESSOR_NAME = "MANUAL_INPUT_PROCESSOR";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualInputProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {

        @NotBlank(message = "{manualinput.processor.input.title.null}")
        @Size(min = 3, max = 200, message = "{manualinput.processor.input.title.invalid.length}")
        private String title;

        @Size(max = 2000, message = "{manualinput.processor.input.description.invalid.length}")
        private String description;

        /**
         * Priority level (1-10, higher = more urgent).
         */
        private int priority = 5;

        /**
         * Users who can complete this task.
         */
        private List<String> assignees = List.of();

        /**
         * Groups/roles who can complete this task.
         */
        private List<String> candidateGroups = List.of();

        /**
         * Timeout in ISO-8601 duration format.
         */
        private String timeout;

        /**
         * Form field definitions.
         * Each field defines the input required from the user.
         */
        private List<FormFieldConfig> formFields = List.of();

        /**
         * Key in context where form data will be stored.
         */
        private String outputKey = "formData";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormFieldConfig {
        /**
         * Field identifier (used as key in result).
         */
        private String fieldId;

        /**
         * Display label.
         */
        private String label;

        /**
         * Field type: text, number, date, select, checkbox, textarea, email, phone.
         */
        private String fieldType = "text";

        /**
         * Whether the field is required.
         */
        private boolean required = false;

        /**
         * Default value.
         */
        private Object defaultValue;

        /**
         * Placeholder text.
         */
        private String placeholder;

        /**
         * Help text for the field.
         */
        private String helpText;

        /**
         * Options for select fields (list of {value, label} maps).
         */
        private List<Map<String, Object>> options;

        /**
         * Validation rules (min, max, pattern, etc.).
         */
        private Map<String, Object> validation;
    }

    @AllArgsConstructor
    @Getter
    public enum ManualInputProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        INVALID_FORM_SCHEMA("MANUAL_INPUT_001", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "manualinput.processor.error.invalid.schema",
                "manualinput.processor.error.invalid.schema.resolution"),
        VALIDATION_FAILED("MANUAL_INPUT_002", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "manualinput.processor.error.validation.failed",
                "manualinput.processor.error.validation.failed.resolution");

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    /**
     * Builds the manual input processor definition.
     */
    public static LyshraOpenAppHumanTaskProcessorDefinition.BuildStep build(
            LyshraOpenAppHumanTaskProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name(PROCESSOR_NAME)
                .humanReadableNameTemplate("manualinput.processor.name")
                .searchTagsCsvTemplate("manualinput.processor.search.tags")
                .errorCodeEnum(ManualInputProcessorErrorCodes.class)
                .inputConfigType(ManualInputProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new ManualInputProcessorInputConfig(
                                "Enter Customer Details",
                                "Please provide the customer information for this order",
                                5,
                                List.of(),
                                List.of("data-entry"),
                                "PT4H",
                                List.of(
                                        new FormFieldConfig("customerName", "Customer Name", "text",
                                                true, null, "Enter full name", null, null, null),
                                        new FormFieldConfig("email", "Email Address", "email",
                                                true, null, "Enter email", null, null, null),
                                        new FormFieldConfig("phone", "Phone Number", "phone",
                                                false, null, "Enter phone", null, null, null),
                                        new FormFieldConfig("priority", "Priority", "select",
                                                true, "normal", null, "Select priority",
                                                List.of(
                                                        Map.of("value", "low", "label", "Low"),
                                                        Map.of("value", "normal", "label", "Normal"),
                                                        Map.of("value", "high", "label", "High")
                                                ), null)
                                ),
                                "customerDetails"
                        )
                ))
                .validateInputConfig(input -> {
                    ManualInputProcessorInputConfig config = (ManualInputProcessorInputConfig) input;
                    if (config.getFormFields().isEmpty()) {
                        throw new IllegalArgumentException("At least one form field must be defined");
                    }
                    // Validate field IDs are unique
                    long uniqueIds = config.getFormFields().stream()
                            .map(FormFieldConfig::getFieldId)
                            .distinct()
                            .count();
                    if (uniqueIds != config.getFormFields().size()) {
                        throw new IllegalArgumentException("Form field IDs must be unique");
                    }
                })
                .humanTaskType(LyshraOpenAppHumanTaskType.MANUAL_INPUT)
                .validOutcomeBranches(List.of("DEFAULT", "CANCELLED", "TIMED_OUT"))
                .process((input, context, facade) -> {
                    // Similar to ApprovalProcessor, this creates a human task
                    // and suspends workflow execution.
                    //
                    // When the user submits the form:
                    // 1. Form data is validated against the schema
                    // 2. Data is merged into workflow context under outputKey
                    // 3. Workflow resumes with DEFAULT branch
                    //
                    // The form data becomes available in subsequent steps as:
                    // $data.formData.customerName (if outputKey is "formData")

                    ManualInputProcessorInputConfig config = (ManualInputProcessorInputConfig) input;

                    // Placeholder - real implementation integrates with HumanTaskService
                    return Mono.just(LyshraOpenAppProcessorOutput.of("PENDING", null));
                })
                .defaultTimeout(Duration.ofHours(4))
                .requiresFormSubmission(true);
    }
}
