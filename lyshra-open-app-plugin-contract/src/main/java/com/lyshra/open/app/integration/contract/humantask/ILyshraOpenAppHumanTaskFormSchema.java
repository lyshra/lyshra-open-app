package com.lyshra.open.app.integration.contract.humantask;

import java.util.List;
import java.util.Map;

/**
 * Defines the form schema for capturing user input in human tasks.
 * The schema describes the fields, their types, validations, and layout.
 */
public interface ILyshraOpenAppHumanTaskFormSchema {

    /**
     * Unique identifier for this form schema.
     */
    String getSchemaId();

    /**
     * Version of the schema for evolution support.
     */
    String getSchemaVersion();

    /**
     * List of field definitions in the form.
     */
    List<ILyshraOpenAppHumanTaskFormField> getFields();

    /**
     * Layout configuration for rendering the form.
     */
    Map<String, Object> getLayout();

    /**
     * Validation rules that span multiple fields.
     */
    List<ILyshraOpenAppHumanTaskFormValidation> getValidations();

    /**
     * Interface for form field definition.
     */
    interface ILyshraOpenAppHumanTaskFormField {

        /**
         * Field identifier (used as key in result data).
         */
        String getFieldId();

        /**
         * Display label for the field.
         */
        String getLabel();

        /**
         * Field type (text, number, date, select, checkbox, etc.).
         */
        String getFieldType();

        /**
         * Whether the field is required.
         */
        boolean isRequired();

        /**
         * Default value for the field.
         */
        Object getDefaultValue();

        /**
         * Placeholder text.
         */
        String getPlaceholder();

        /**
         * Help text for the field.
         */
        String getHelpText();

        /**
         * Options for select/radio/checkbox fields.
         */
        List<Map<String, Object>> getOptions();

        /**
         * Field-level validation rules.
         */
        Map<String, Object> getValidation();

        /**
         * Whether the field is read-only.
         */
        boolean isReadOnly();

        /**
         * Conditional visibility rules.
         */
        Map<String, Object> getVisibilityCondition();
    }

    /**
     * Interface for cross-field validation rules.
     */
    interface ILyshraOpenAppHumanTaskFormValidation {

        /**
         * Validation rule identifier.
         */
        String getRuleId();

        /**
         * Fields involved in this validation.
         */
        List<String> getFields();

        /**
         * Validation expression (SpEL or JavaScript).
         */
        String getExpression();

        /**
         * Error message when validation fails.
         */
        String getErrorMessage();
    }
}
