package com.lyshra.open.app.core.processors.plugin.processors.date;

import com.lyshra.open.app.core.processors.plugin.utils.ExceptionUtils;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.commons.LyshraOpenAppExpression;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType.GRAAALVM_JS;

/**
 * Processor for comparing dates with operations: BEFORE, AFTER, SAME.
 * Supports timezone-aware comparisons to ensure dates are compared in the same timezone.
 */
public class DateCompareProcessor {

    /**
     * JavaScript snippet for date comparison.
     *
     * Handles:
     * - ISO 8601 date strings (with and without timezone)
     * - Epoch milliseconds (13+ digits)
     * - Epoch seconds (10 digits or less)
     * - Timezone normalization for fair comparison
     * - Precision-based comparison for SAME operation
     */
    public static final String DATE_COMPARE_JS_SNIPPET = """
            var data = $data;
            var field = '%s';
            var compareWithValue = %s;
            var operation = '%s';
            var timezone = '%s';
            var precision = '%s';

            // Helper function to check if value is null/undefined/empty
            function isNullOrEmpty(v) {
                return v === null || v === undefined || v === '';
            }

            // Helper function to get value at a nested path (e.g., "abc.xyz")
            function getNestedValue(obj, path) {
                if (isNullOrEmpty(obj) || typeof obj !== 'object') return null;
                var keys = path.split('.');
                var current = obj;
                for (var i = 0; i < keys.length; i++) {
                    if (isNullOrEmpty(current) || typeof current !== 'object') return null;
                    current = current[keys[i]];
                }
                return current;
            }

            // Helper to parse date value to milliseconds
            function parseToMillis(value) {
                if (isNullOrEmpty(value)) return null;

                var valueType = typeof value;

                if (valueType === 'number') {
                    // Detect epoch seconds vs millis based on magnitude
                    if (Math.abs(value) < 100000000000) {
                        return value * 1000; // Epoch seconds to millis
                    }
                    return value; // Already epoch millis
                } else if (valueType === 'string') {
                    var str = value.trim();
                    var date = new Date(str);
                    if (isNaN(date.getTime())) {
                        throw new Error('Invalid date format: ' + value);
                    }
                    return date.getTime();
                } else if (valueType === 'object') {
                    // Check for polyglot null
                    var isPolyglotNull = false;
                    try {
                        if (!value) {
                            isPolyglotNull = true;
                        } else {
                            var keys = Object.keys(value);
                            var strVal = String(value);
                            isPolyglotNull = (keys.length === 0 && !Array.isArray(value)) || strVal === 'null';
                        }
                    } catch (e) {
                        isPolyglotNull = true;
                    }
                    if (isPolyglotNull) return null;
                    throw new Error('Unsupported date type: object');
                } else if (valueType === 'boolean') {
                    throw new Error('Unsupported date type: boolean');
                }

                throw new Error('Unsupported date type: ' + valueType);
            }

            // Helper to detect timezone from ISO string
            function detectTimezone(value) {
                if (typeof value !== 'string') return null;
                var str = value.trim();

                // Check for Z (UTC)
                if (str.endsWith('Z')) return 'UTC';

                // Check for offset like +05:30 or -08:00
                var offsetMatch = str.match(/([+-])(\\d{2}):(\\d{2})$/);
                if (offsetMatch) {
                    return 'OFFSET:' + offsetMatch[0];
                }

                // No timezone indicator - local time
                return null;
            }

            // Helper to truncate millis to precision
            function truncateToPrecision(millis, prec) {
                var date = new Date(millis);
                switch (prec.toUpperCase()) {
                    case 'YEAR':
                        return Date.UTC(date.getUTCFullYear(), 0, 1, 0, 0, 0, 0);
                    case 'MONTH':
                        return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), 1, 0, 0, 0, 0);
                    case 'DAY':
                        return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate(), 0, 0, 0, 0);
                    case 'HOUR':
                        return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate(), date.getUTCHours(), 0, 0, 0);
                    case 'MINUTE':
                        return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate(), date.getUTCHours(), date.getUTCMinutes(), 0, 0);
                    case 'SECOND':
                        return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate(), date.getUTCHours(), date.getUTCMinutes(), date.getUTCSeconds(), 0);
                    case 'MILLIS':
                    default:
                        return millis;
                }
            }

            // Get source value
            var sourceValue;
            if (field) {
                if (isNullOrEmpty(data) || typeof data !== 'object') {
                    sourceValue = null;
                } else {
                    sourceValue = getNestedValue(data, field);
                }
            } else {
                sourceValue = data;
            }

            // Parse dates
            var sourceMillis = parseToMillis(sourceValue);
            var compareMillis = parseToMillis(compareWithValue);

            // Handle null cases
            if (sourceMillis === null || compareMillis === null) {
                // Cannot compare if either date is null
                throw new Error('Cannot compare dates: one or both values are null');
            }

            // Detect timezones for validation
            var sourceTz = detectTimezone(sourceValue);
            var compareTz = detectTimezone(compareWithValue);

            // Validate timezone consistency if no normalizing timezone provided
            if (!timezone) {
                // If both have explicit timezones but they differ, warn/error
                if (sourceTz && compareTz && sourceTz !== compareTz) {
                    throw new Error('Timezone mismatch: source has ' + sourceTz + ' but compareWith has ' + compareTz + '. Provide a timezone parameter to normalize.');
                }
            }

            // Apply precision truncation for comparison
            var effectivePrecision = precision || 'MILLIS';
            var sourceCompare = truncateToPrecision(sourceMillis, effectivePrecision);
            var compareCompare = truncateToPrecision(compareMillis, effectivePrecision);

            // Perform comparison
            var comparisonResult;
            switch (operation.toUpperCase()) {
                case 'BEFORE':
                    comparisonResult = sourceCompare < compareCompare;
                    break;
                case 'AFTER':
                    comparisonResult = sourceCompare > compareCompare;
                    break;
                case 'SAME':
                    comparisonResult = sourceCompare === compareCompare;
                    break;
                case 'BEFORE_OR_SAME':
                    comparisonResult = sourceCompare <= compareCompare;
                    break;
                case 'AFTER_OR_SAME':
                    comparisonResult = sourceCompare >= compareCompare;
                    break;
                default:
                    throw new Error('Unsupported operation: ' + operation);
            }

            result = comparisonResult;
            """;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateCompareProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {
        /**
         * Optional field path within $data to get the source date.
         * If null/empty, operates on $data directly.
         */
        private String field;

        /**
         * The date value to compare against. Required.
         * Can be an ISO 8601 string, epoch millis, or epoch seconds.
         */
        @NotNull(message = "{date.compare.processor.input.compare.with.null}")
        private Object compareWith;

        /**
         * The comparison operation to perform.
         * Valid values: BEFORE, AFTER, SAME, BEFORE_OR_SAME, AFTER_OR_SAME
         */
        @NotNull(message = "{date.compare.processor.input.operation.null}")
        @Pattern(regexp = "^(?i)(BEFORE|AFTER|SAME|BEFORE_OR_SAME|AFTER_OR_SAME)$",
                message = "{date.compare.processor.input.operation.invalid}")
        private String operation;

        /**
         * Optional timezone ID for normalizing both dates before comparison.
         * E.g., "UTC", "America/New_York", "Asia/Kolkata".
         * If both dates have different explicit timezones and this is not provided,
         * the comparison will fail to prevent timezone mismatch issues.
         */
        private String timezone;

        /**
         * Optional precision for comparison.
         * Valid values: YEAR, MONTH, DAY, HOUR, MINUTE, SECOND, MILLIS
         * Default is MILLIS (exact comparison).
         */
        @Pattern(regexp = "^(?i)(YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|MILLIS)?$",
                message = "{date.compare.processor.input.precision.invalid}")
        private String precision;
    }

    @AllArgsConstructor
    @Getter
    public enum DateCompareProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        FIELD_CANNOT_BE_BLANK("DATE_COMPARE_001", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "date.compare.processor.input.field.blank",
                "date.compare.processor.input.field.blank.resolution"),
        INVALID_TIMEZONE("DATE_COMPARE_002", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "date.compare.processor.input.timezone.invalid",
                "date.compare.processor.input.timezone.invalid.resolution"),
        TIMEZONE_MISMATCH("DATE_COMPARE_003", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "date.compare.processor.timezone.mismatch",
                "date.compare.processor.timezone.mismatch.resolution"),
        INVALID_DATE_FORMAT("DATE_COMPARE_004", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "date.compare.processor.invalid.date.format",
                "date.compare.processor.invalid.date.format.resolution"),
        NULL_DATE_VALUE("DATE_COMPARE_005", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "date.compare.processor.null.date.value",
                "date.compare.processor.null.date.value.resolution"),
        COMPARISON_ERROR("DATE_COMPARE_006", LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
                "date.compare.processor.comparison.error",
                "date.compare.processor.comparison.error.resolution");

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("DATE_COMPARE_PROCESSOR")
                .humanReadableNameTemplate("date.compare.processor.name")
                .searchTagsCsvTemplate("date.compare.processor.search.tags")
                .errorCodeEnum(DateCompareProcessorErrorCodes.class)
                .inputConfigType(DateCompareProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new DateCompareProcessorInputConfig(null, "2024-01-15T10:30:00Z", "BEFORE", null, null),
                        new DateCompareProcessorInputConfig(null, "2024-01-15T10:30:00Z", "AFTER", "UTC", null),
                        new DateCompareProcessorInputConfig("createdAt", "2024-01-15T10:30:00Z", "SAME", null, "DAY"),
                        new DateCompareProcessorInputConfig("timestamp", 1705312200000L, "BEFORE_OR_SAME", "America/New_York", null),
                        new DateCompareProcessorInputConfig(null, "2024-01-15", "SAME", null, "MONTH")
                ))
                .validateInputConfig(input -> {
                    DateCompareProcessorInputConfig config = (DateCompareProcessorInputConfig) input;

                    // Validate field if provided
                    if (config.getField() != null && config.getField().isBlank()) {
                        throw new LyshraOpenAppProcessorRuntimeException(DateCompareProcessorErrorCodes.FIELD_CANNOT_BE_BLANK);
                    }

                    // Validate timezone if provided
                    if (config.getTimezone() != null && !config.getTimezone().isBlank()) {
                        try {
                            java.time.ZoneId.of(config.getTimezone());
                        } catch (Exception e) {
                            throw new LyshraOpenAppProcessorRuntimeException(DateCompareProcessorErrorCodes.INVALID_TIMEZONE);
                        }
                    }
                })
                .process((input, context, facade) -> {
                    DateCompareProcessorInputConfig inputConfig = (DateCompareProcessorInputConfig) input;

                    String field = inputConfig.getField() != null ? inputConfig.getField() : "";
                    Object compareWith = inputConfig.getCompareWith();
                    String operation = inputConfig.getOperation().toUpperCase();
                    String timezone = inputConfig.getTimezone() != null ? inputConfig.getTimezone() : "";
                    String precision = inputConfig.getPrecision() != null ? inputConfig.getPrecision() : "";

                    // Format compareWith value for JS
                    String compareWithJs;
                    if (compareWith instanceof String) {
                        compareWithJs = "'" + compareWith + "'";
                    } else if (compareWith instanceof Number) {
                        compareWithJs = compareWith.toString();
                    } else {
                        compareWithJs = "null";
                    }

                    String expression = String.format(DATE_COMPARE_JS_SNIPPET,
                            field, compareWithJs, operation, timezone, precision);

                    ILyshraOpenAppExpression expressionConfig = new LyshraOpenAppExpression(GRAAALVM_JS, expression);

                    try {
                        Object result = facade.getExpressionExecutor().evaluate(expressionConfig, context, facade);
                        return Mono.just(LyshraOpenAppProcessorOutput.ofData(result));
                    } catch (Exception e) {
                        // Check for specific error types in the entire exception chain
                        if (ExceptionUtils.messageContainsInCauseChain(e, "Timezone mismatch")) {
                            throw new LyshraOpenAppProcessorRuntimeException(
                                    DateCompareProcessorErrorCodes.TIMEZONE_MISMATCH, e);
                        } else if (ExceptionUtils.messageContainsInCauseChain(e, "Invalid date format")) {
                            throw new LyshraOpenAppProcessorRuntimeException(
                                    DateCompareProcessorErrorCodes.INVALID_DATE_FORMAT, e);
                        } else if (ExceptionUtils.messageContainsInCauseChain(e, "Cannot compare dates") ||
                                   ExceptionUtils.messageContainsInCauseChain(e, "null")) {
                            throw new LyshraOpenAppProcessorRuntimeException(
                                    DateCompareProcessorErrorCodes.NULL_DATE_VALUE, e);
                        } else {
                            throw new LyshraOpenAppProcessorRuntimeException(
                                    DateCompareProcessorErrorCodes.COMPARISON_ERROR, e);
                        }
                    }
                });
    }
}
