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

public class DateAddProcessor {

    /**
     * JavaScript snippet for date manipulation using native JavaScript Date API.
     *
     * This handles:
     * - ISO 8601 date strings (with and without timezone)
     * - Epoch milliseconds (13+ digits)
     * - Epoch seconds (10 digits or less)
     * - Calendar-based units (YEARS, MONTHS, WEEKS, DAYS)
     * - Time-based units (HOURS, MINUTES, SECONDS, MILLIS, MICROS, NANOS)
     * - Nanosecond precision through string parsing and arithmetic
     * - Timezone support for calendar-based operations
     */
    public static final String DATE_ADD_JS_SNIPPET = """
            var data = $data;
            var field = '%s';
            var amount = %d;
            var unit = '%s';
            var timezone = '%s';

            // Helper function to pad numbers with leading zeros
            function padZeros(num, size) {
                var s = String(num);
                while (s.length < size) s = '0' + s;
                return s;
            }

            // Helper function to parse ISO string with nanosecond precision
            function parseIsoWithNanos(str) {
                // Extract nanoseconds from string like "2024-01-15T10:30:00.123456789Z"
                var nanoMatch = str.match(/\\.([0-9]+)/);
                var nanos = 0;
                var millis = 0;
                if (nanoMatch) {
                    var fracStr = nanoMatch[1];
                    // Pad or truncate to 9 digits
                    while (fracStr.length < 9) fracStr = fracStr + '0';
                    fracStr = fracStr.substring(0, 9);
                    nanos = parseInt(fracStr, 10);
                    millis = Math.floor(nanos / 1000000);
                }
                // Parse the date without fractional seconds
                var baseStr = str.replace(/\\.([0-9]+)/, '');
                var date = new Date(baseStr);
                if (millis > 0) {
                    date.setMilliseconds(millis);
                }
                return { date: date, nanos: nanos %% 1000000 }; // nanos excluding millis portion
            }

            // Helper to format date back to ISO with nanoseconds
            function formatIsoWithNanos(date, extraNanos, hasTimezone, hasTime) {
                if (!hasTime) {
                    // Date only format
                    return date.getUTCFullYear() + '-' +
                           padZeros(date.getUTCMonth() + 1, 2) + '-' +
                           padZeros(date.getUTCDate(), 2);
                }

                var iso = date.toISOString();
                if (extraNanos > 0) {
                    // Replace milliseconds with full nanoseconds
                    var totalNanos = date.getUTCMilliseconds() * 1000000 + extraNanos;
                    var nanoStr = padZeros(totalNanos, 9);
                    // Remove trailing zeros but keep at least 3 digits
                    while (nanoStr.length > 3 && nanoStr.charAt(nanoStr.length - 1) === '0') {
                        nanoStr = nanoStr.substring(0, nanoStr.length - 1);
                    }
                    iso = iso.replace(/\\.\\d{3}Z$/, '.' + nanoStr + 'Z');
                }

                if (!hasTimezone) {
                    // Remove Z and return as local datetime
                    iso = iso.replace('Z', '');
                    // Convert from UTC representation
                }
                return iso;
            }

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

            // Helper function to set value at a nested path (e.g., "abc.xyz")
            function setNestedValue(obj, path, value) {
                var keys = path.split('.');
                var current = obj;
                for (var i = 0; i < keys.length - 1; i++) {
                    if (current[keys[i]] === undefined || current[keys[i]] === null) {
                        current[keys[i]] = {};
                    }
                    current = current[keys[i]];
                }
                current[keys[keys.length - 1]] = value;
            }

            // Get the value to manipulate
            var value;
            if (field) {
                // When field is specified, get the field from data (supports nested paths)
                if (isNullOrEmpty(data) || typeof data !== 'object') {
                    value = null;
                } else {
                    value = getNestedValue(data, field);
                }
            } else {
                value = data;
            }

            // Determine value type and handle accordingly
            var valueType = typeof value;

            // Check for null/undefined/empty first - return gracefully
            if (isNullOrEmpty(value)) {
                result = field ? data : null;
            } else if (valueType === 'boolean') {
                // Boolean is explicitly not supported - throw error
                throw new Error('Unsupported date type: boolean');
            } else if (valueType === 'object') {
                // Objects (including arrays) are not supported as date values
                // Note: GraalVM polyglot null also appears as 'object', need special handling
                var isPolyglotNull = false;
                try {
                    // Check multiple ways to detect polyglot null
                    if (!value) {
                        isPolyglotNull = true;
                    } else {
                        var keys = Object.keys(value);
                        var strVal = String(value);
                        // Polyglot null often: has no keys, or converts to 'null'
                        isPolyglotNull = (keys.length === 0 && !Array.isArray(value)) || strVal === 'null';
                    }
                } catch (e) {
                    // If accessing properties throws, it's likely polyglot null
                    isPolyglotNull = true;
                }

                if (isPolyglotNull) {
                    // Polyglot null or falsy object - treat as null
                    result = field ? data : null;
                } else if (Array.isArray(value)) {
                    throw new Error('Unsupported date type: array');
                } else {
                    throw new Error('Unsupported date type: object');
                }
            } else if (valueType !== 'string' && valueType !== 'number') {
                // Any other unsupported type
                throw new Error('Unsupported date type: ' + valueType);
            } else {
                var outputFormat = 'ISO_STRING';
                var hasTimezone = false;
                var hasTime = true;
                var extraNanos = 0;
                var date;

                if (typeof value === 'string') {
                    var str = value.trim();

                    // Detect format characteristics
                    hasTimezone = str.indexOf('Z') >= 0 || (str.indexOf('+') > 10) || (str.lastIndexOf('-') > 7);
                    hasTime = str.indexOf('T') >= 0;

                    if (!hasTime) {
                        // Date only - parse as date at midnight UTC
                        var parts = str.split('-');
                        date = new Date(Date.UTC(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2])));
                    } else {
                        var parsed = parseIsoWithNanos(str);
                        date = parsed.date;
                        extraNanos = parsed.nanos;
                    }
                } else if (typeof value === 'number') {
                    // Detect epoch seconds vs millis based on magnitude
                    if (Math.abs(value) < 100000000000) {
                        // Epoch seconds
                        date = new Date(value * 1000);
                        outputFormat = 'EPOCH_SECONDS';
                    } else {
                        // Epoch milliseconds
                        date = new Date(value);
                        outputFormat = 'EPOCH_MILLIS';
                    }
                } else {
                    throw new Error('Unsupported date type: ' + typeof value);
                }

                // Apply manipulation based on unit
                var upperUnit = unit.toUpperCase();

                switch (upperUnit) {
                    case 'YEARS':
                        date.setUTCFullYear(date.getUTCFullYear() + amount);
                        break;
                    case 'MONTHS':
                        date.setUTCMonth(date.getUTCMonth() + amount);
                        break;
                    case 'WEEKS':
                        date.setUTCDate(date.getUTCDate() + (amount * 7));
                        break;
                    case 'DAYS':
                        date.setUTCDate(date.getUTCDate() + amount);
                        break;
                    case 'HOURS':
                        date.setUTCHours(date.getUTCHours() + amount);
                        break;
                    case 'MINUTES':
                        date.setUTCMinutes(date.getUTCMinutes() + amount);
                        break;
                    case 'SECONDS':
                        date.setUTCSeconds(date.getUTCSeconds() + amount);
                        break;
                    case 'MILLIS':
                        date.setUTCMilliseconds(date.getUTCMilliseconds() + amount);
                        break;
                    case 'MICROS':
                        // Convert micros to millis and nanos
                        var totalMicros = extraNanos / 1000 + amount;
                        var addMillis = Math.floor(totalMicros / 1000);
                        extraNanos = (totalMicros %% 1000) * 1000;
                        if (extraNanos < 0) {
                            addMillis -= 1;
                            extraNanos += 1000000;
                        }
                        date.setUTCMilliseconds(date.getUTCMilliseconds() + addMillis);
                        break;
                    case 'NANOS':
                        // Handle nanoseconds
                        var totalNanos = extraNanos + amount;
                        var addMillisFromNanos = Math.floor(totalNanos / 1000000);
                        extraNanos = totalNanos %% 1000000;
                        if (extraNanos < 0) {
                            addMillisFromNanos -= 1;
                            extraNanos += 1000000;
                        }
                        date.setUTCMilliseconds(date.getUTCMilliseconds() + addMillisFromNanos);
                        break;
                    default:
                        throw new Error('Unsupported unit: ' + unit);
                }

                // Format output based on original format
                var outputValue;
                if (outputFormat === 'EPOCH_SECONDS') {
                    outputValue = Math.floor(date.getTime() / 1000);
                } else if (outputFormat === 'EPOCH_MILLIS') {
                    outputValue = date.getTime();
                } else {
                    outputValue = formatIsoWithNanos(date, extraNanos, hasTimezone, hasTime);
                }

                // Set result - if field is provided, update the field in data and return data
                // Otherwise, return the manipulated value directly
                if (field) {
                    setNestedValue(data, field, outputValue);
                    result = data;
                } else {
                    result = outputValue;
                }
            }
            """;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateAddProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {
        /**
         * Optional field path within $data to operate on.
         * If null/empty, operates on $data directly.
         */
        private String field;

        /**
         * Amount to add (positive) or subtract (negative).
         * Required.
         */
        @NotNull(message = "{date.add.processor.input.amount.null}")
        private Long amount;

        /**
         * Temporal unit for the operation.
         * Valid values: YEARS, MONTHS, WEEKS, DAYS, HOURS, MINUTES, SECONDS, MILLIS, MICROS, NANOS
         */
        @NotNull(message = "{date.add.processor.input.unit.null}")
        @Pattern(regexp = "^(?i)(YEARS|MONTHS|WEEKS|DAYS|HOURS|MINUTES|SECONDS|MILLIS|MICROS|NANOS)$",
                message = "{date.add.processor.input.unit.invalid}")
        private String unit;

        /**
         * Optional timezone ID (e.g., "UTC", "America/New_York", "Asia/Kolkata").
         * Note: JavaScript Date operates in UTC internally. This parameter is
         * reserved for future timezone-aware operations.
         */
        private String timezone;
    }

    @AllArgsConstructor
    @Getter
    public enum DateAddProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        FIELD_CANNOT_BE_BLANK("DATE_ADD_001", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "date.add.processor.input.field.blank",
                "date.add.processor.input.field.blank.resolution"),
        INVALID_TIMEZONE("DATE_ADD_002", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "date.add.processor.input.timezone.invalid",
                "date.add.processor.input.timezone.invalid.resolution"),
        UNSUPPORTED_DATE_TYPE("DATE_ADD_003", LyshraOpenAppHttpStatus.BAD_REQUEST,
                "date.add.processor.unsupported.date.type",
                "date.add.processor.unsupported.date.type.resolution"),
        DATE_PROCESSING_ERROR("DATE_ADD_004", LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
                "date.add.processor.processing.error",
                "date.add.processor.processing.error.resolution");

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("DATE_ADD_PROCESSOR")
                .humanReadableNameTemplate("date.add.processor.name")
                .searchTagsCsvTemplate("date.add.processor.search.tags")
                .errorCodeEnum(DateAddProcessorErrorCodes.class)
                .inputConfigType(DateAddProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        new DateAddProcessorInputConfig(null, 1L, "DAYS", null),
                        new DateAddProcessorInputConfig(null, -1L, "MONTHS", "UTC"),
                        new DateAddProcessorInputConfig("createdAt", 2L, "HOURS", "America/New_York"),
                        new DateAddProcessorInputConfig("timestamp", -30L, "MINUTES", null),
                        new DateAddProcessorInputConfig(null, 500L, "MILLIS", null),
                        new DateAddProcessorInputConfig(null, 1000000L, "NANOS", null)
                ))
                .validateInputConfig(input -> {
                    DateAddProcessorInputConfig config = (DateAddProcessorInputConfig) input;

                    // Validate field if provided
                    if (config.getField() != null && config.getField().isBlank()) {
                        throw new LyshraOpenAppProcessorRuntimeException(DateAddProcessorErrorCodes.FIELD_CANNOT_BE_BLANK);
                    }

                    // Validate timezone if provided (basic validation - just check it's not blank if set)
                    if (config.getTimezone() != null && !config.getTimezone().isBlank()) {
                        try {
                            java.time.ZoneId.of(config.getTimezone());
                        } catch (Exception e) {
                            throw new LyshraOpenAppProcessorRuntimeException(DateAddProcessorErrorCodes.INVALID_TIMEZONE);
                        }
                    }
                })
                .process((input, context, facade) -> {
                    DateAddProcessorInputConfig inputConfig = (DateAddProcessorInputConfig) input;

                    String field = inputConfig.getField() != null ? inputConfig.getField() : "";
                    long amount = inputConfig.getAmount();
                    String unit = inputConfig.getUnit().toUpperCase();
                    String timezone = inputConfig.getTimezone() != null ? inputConfig.getTimezone() : "";

                    String expression = String.format(DATE_ADD_JS_SNIPPET, field, amount, unit, timezone);

                    ILyshraOpenAppExpression expressionConfig = new LyshraOpenAppExpression(GRAAALVM_JS, expression);

                    try {
                        Object result = facade.getExpressionExecutor().evaluate(expressionConfig, context, facade);
                        return Mono.just(LyshraOpenAppProcessorOutput.ofData(result));
                    } catch (Exception e) {
                        // Check for specific error types in the entire exception chain
                        if (ExceptionUtils.messageContainsInCauseChain(e, "Unsupported date type")) {
                            throw new LyshraOpenAppProcessorRuntimeException(
                                    DateAddProcessorErrorCodes.UNSUPPORTED_DATE_TYPE, e);
                        } else {
                            throw new LyshraOpenAppProcessorRuntimeException(
                                    DateAddProcessorErrorCodes.DATE_PROCESSING_ERROR, e);
                        }
                    }
                });
    }
}
