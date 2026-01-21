package com.lyshra.open.app.core.processors.plugin.processors.date;

import com.lyshra.open.app.core.models.LyshraOpenAppContext;
import com.lyshra.open.app.core.processors.plugin.LyshraOpenAppCoreProcessorsPlugin;
import com.lyshra.open.app.core.processors.plugin.processors.AbstractPluginTest;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
class DateCompareProcessorTest extends AbstractPluginTest {

    private static LyshraOpenAppProcessorDefinition dateCompareProcessor;
    private static LyshraOpenAppProcessorIdentifier dateCompareProcessorIdentifier;

    @BeforeAll
    static void setupClass() {
        log.info("Setting up test class");
        facade.getPluginLoader().loadPlugin(new LyshraOpenAppCoreProcessorsPlugin());
        dateCompareProcessor = DateCompareProcessor.build(LyshraOpenAppProcessorDefinition.builder()).build();
        dateCompareProcessorIdentifier = new LyshraOpenAppProcessorIdentifier(
                LyshraOpenAppCoreProcessorsPlugin.getPluginIdentifier(),
                dateCompareProcessor.getName());
    }

    // ==================== BEFORE Operation Tests ====================

    @Nested
    @DisplayName("BEFORE Operation Tests")
    class BeforeOperationTests {

        @ParameterizedTest(name = "BEFORE: {0}")
        @MethodSource("beforeOperationProvider")
        void testBeforeOperation(String description, Object sourceDate, Object compareWith, boolean expected) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(sourceDate);
            Map<String, Object> processorInput = Map.of("compareWith", compareWith, "operation", "BEFORE");

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("{} - Source: {}, CompareWith: {}, Result: {}", description, sourceDate, compareWith, output.getData());
                        Assertions.assertNotNull(output.getData());
                        Assertions.assertEquals(expected, output.getData());
                    })
                    .verifyComplete();
        }

        private static Stream<Arguments> beforeOperationProvider() {
            return Stream.of(
                    // ISO string comparisons - same timezone
                    Arguments.of("earlier date is BEFORE later date (UTC)",
                            "2024-01-10T10:30:00Z", "2024-01-15T10:30:00Z", true),
                    Arguments.of("later date is NOT BEFORE earlier date (UTC)",
                            "2024-01-20T10:30:00Z", "2024-01-15T10:30:00Z", false),
                    Arguments.of("same date is NOT BEFORE itself (UTC)",
                            "2024-01-15T10:30:00Z", "2024-01-15T10:30:00Z", false),

                    // Epoch millis comparisons
                    Arguments.of("earlier epoch millis is BEFORE later",
                            1705312200000L, 1705398600000L, true),
                    Arguments.of("later epoch millis is NOT BEFORE earlier",
                            1705398600000L, 1705312200000L, false),

                    // Epoch seconds comparisons
                    Arguments.of("earlier epoch seconds is BEFORE later",
                            1705312200L, 1705398600L, true),

                    // Mixed format comparisons (same timezone)
                    Arguments.of("ISO string BEFORE epoch millis",
                            "2024-01-15T10:30:00Z", 1705398600000L, true),
                    Arguments.of("epoch millis BEFORE ISO string",
                            1705312200000L, "2024-01-16T10:30:00Z", true)
            );
        }
    }

    // ==================== AFTER Operation Tests ====================

    @Nested
    @DisplayName("AFTER Operation Tests")
    class AfterOperationTests {

        @ParameterizedTest(name = "AFTER: {0}")
        @MethodSource("afterOperationProvider")
        void testAfterOperation(String description, Object sourceDate, Object compareWith, boolean expected) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(sourceDate);
            Map<String, Object> processorInput = Map.of("compareWith", compareWith, "operation", "AFTER");

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("{} - Source: {}, CompareWith: {}, Result: {}", description, sourceDate, compareWith, output.getData());
                        Assertions.assertNotNull(output.getData());
                        Assertions.assertEquals(expected, output.getData());
                    })
                    .verifyComplete();
        }

        private static Stream<Arguments> afterOperationProvider() {
            return Stream.of(
                    // ISO string comparisons
                    Arguments.of("later date is AFTER earlier date (UTC)",
                            "2024-01-20T10:30:00Z", "2024-01-15T10:30:00Z", true),
                    Arguments.of("earlier date is NOT AFTER later date (UTC)",
                            "2024-01-10T10:30:00Z", "2024-01-15T10:30:00Z", false),
                    Arguments.of("same date is NOT AFTER itself (UTC)",
                            "2024-01-15T10:30:00Z", "2024-01-15T10:30:00Z", false),

                    // Epoch millis comparisons
                    Arguments.of("later epoch millis is AFTER earlier",
                            1705398600000L, 1705312200000L, true),
                    Arguments.of("earlier epoch millis is NOT AFTER later",
                            1705312200000L, 1705398600000L, false),

                    // Epoch seconds comparisons
                    Arguments.of("later epoch seconds is AFTER earlier",
                            1705398600L, 1705312200L, true),

                    // Mixed format comparisons
                    Arguments.of("ISO string AFTER epoch millis (earlier)",
                            "2024-01-16T10:30:00Z", 1705312200000L, true)
            );
        }
    }

    // ==================== SAME Operation Tests ====================

    @Nested
    @DisplayName("SAME Operation Tests")
    class SameOperationTests {

        @ParameterizedTest(name = "SAME: {0}")
        @MethodSource("sameOperationProvider")
        void testSameOperation(String description, Object sourceDate, Object compareWith, boolean expected) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(sourceDate);
            Map<String, Object> processorInput = Map.of("compareWith", compareWith, "operation", "SAME");

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("{} - Source: {}, CompareWith: {}, Result: {}", description, sourceDate, compareWith, output.getData());
                        Assertions.assertNotNull(output.getData());
                        Assertions.assertEquals(expected, output.getData());
                    })
                    .verifyComplete();
        }

        private static Stream<Arguments> sameOperationProvider() {
            return Stream.of(
                    // Exact same dates
                    Arguments.of("same ISO string is SAME",
                            "2024-01-15T10:30:00Z", "2024-01-15T10:30:00Z", true),
                    Arguments.of("different dates are NOT SAME",
                            "2024-01-15T10:30:00Z", "2024-01-16T10:30:00Z", false),

                    // Same epoch millis
                    Arguments.of("same epoch millis is SAME",
                            1705312200000L, 1705312200000L, true),
                    Arguments.of("different epoch millis are NOT SAME",
                            1705312200000L, 1705398600000L, false),

                    // Same epoch seconds
                    Arguments.of("same epoch seconds is SAME",
                            1705312200L, 1705312200L, true)
            );
        }
    }

    // ==================== BEFORE_OR_SAME Operation Tests ====================

    @Nested
    @DisplayName("BEFORE_OR_SAME Operation Tests")
    class BeforeOrSameOperationTests {

        @ParameterizedTest(name = "BEFORE_OR_SAME: {0}")
        @MethodSource("beforeOrSameOperationProvider")
        void testBeforeOrSameOperation(String description, Object sourceDate, Object compareWith, boolean expected) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(sourceDate);
            Map<String, Object> processorInput = Map.of("compareWith", compareWith, "operation", "BEFORE_OR_SAME");

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("{} - Source: {}, CompareWith: {}, Result: {}", description, sourceDate, compareWith, output.getData());
                        Assertions.assertNotNull(output.getData());
                        Assertions.assertEquals(expected, output.getData());
                    })
                    .verifyComplete();
        }

        private static Stream<Arguments> beforeOrSameOperationProvider() {
            return Stream.of(
                    Arguments.of("earlier date is BEFORE_OR_SAME later date",
                            "2024-01-10T10:30:00Z", "2024-01-15T10:30:00Z", true),
                    Arguments.of("same date is BEFORE_OR_SAME itself",
                            "2024-01-15T10:30:00Z", "2024-01-15T10:30:00Z", true),
                    Arguments.of("later date is NOT BEFORE_OR_SAME earlier date",
                            "2024-01-20T10:30:00Z", "2024-01-15T10:30:00Z", false),

                    // Epoch millis
                    Arguments.of("earlier epoch millis is BEFORE_OR_SAME",
                            1705312200000L, 1705398600000L, true),
                    Arguments.of("same epoch millis is BEFORE_OR_SAME",
                            1705312200000L, 1705312200000L, true)
            );
        }
    }

    // ==================== AFTER_OR_SAME Operation Tests ====================

    @Nested
    @DisplayName("AFTER_OR_SAME Operation Tests")
    class AfterOrSameOperationTests {

        @ParameterizedTest(name = "AFTER_OR_SAME: {0}")
        @MethodSource("afterOrSameOperationProvider")
        void testAfterOrSameOperation(String description, Object sourceDate, Object compareWith, boolean expected) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(sourceDate);
            Map<String, Object> processorInput = Map.of("compareWith", compareWith, "operation", "AFTER_OR_SAME");

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("{} - Source: {}, CompareWith: {}, Result: {}", description, sourceDate, compareWith, output.getData());
                        Assertions.assertNotNull(output.getData());
                        Assertions.assertEquals(expected, output.getData());
                    })
                    .verifyComplete();
        }

        private static Stream<Arguments> afterOrSameOperationProvider() {
            return Stream.of(
                    Arguments.of("later date is AFTER_OR_SAME earlier date",
                            "2024-01-20T10:30:00Z", "2024-01-15T10:30:00Z", true),
                    Arguments.of("same date is AFTER_OR_SAME itself",
                            "2024-01-15T10:30:00Z", "2024-01-15T10:30:00Z", true),
                    Arguments.of("earlier date is NOT AFTER_OR_SAME later date",
                            "2024-01-10T10:30:00Z", "2024-01-15T10:30:00Z", false),

                    // Epoch millis
                    Arguments.of("later epoch millis is AFTER_OR_SAME",
                            1705398600000L, 1705312200000L, true),
                    Arguments.of("same epoch millis is AFTER_OR_SAME",
                            1705312200000L, 1705312200000L, true)
            );
        }
    }

    // ==================== Precision-based Comparison Tests ====================

    @Nested
    @DisplayName("Precision-based Comparison Tests")
    class PrecisionComparisonTests {

        @ParameterizedTest(name = "Precision {0}: {1}")
        @MethodSource("precisionComparisonProvider")
        void testPrecisionComparison(String precision, String description, Object sourceDate, Object compareWith,
                                      String operation, boolean expected) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(sourceDate);
            Map<String, Object> processorInput = new HashMap<>();
            processorInput.put("compareWith", compareWith);
            processorInput.put("operation", operation);
            processorInput.put("precision", precision);

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("Precision {}: {} - Source: {}, CompareWith: {}, Result: {}",
                                precision, description, sourceDate, compareWith, output.getData());
                        Assertions.assertNotNull(output.getData());
                        Assertions.assertEquals(expected, output.getData());
                    })
                    .verifyComplete();
        }

        private static Stream<Arguments> precisionComparisonProvider() {
            return Stream.of(
                    // YEAR precision
                    Arguments.of("YEAR", "same year different month are SAME at YEAR precision",
                            "2024-01-15T10:30:00Z", "2024-06-20T14:45:30Z", "SAME", true),
                    Arguments.of("YEAR", "different years are NOT SAME at YEAR precision",
                            "2024-01-15T10:30:00Z", "2025-01-15T10:30:00Z", "SAME", false),
                    Arguments.of("YEAR", "2024 is BEFORE 2025 at YEAR precision",
                            "2024-12-31T23:59:59Z", "2025-01-01T00:00:00Z", "BEFORE", true),

                    // MONTH precision
                    Arguments.of("MONTH", "same month different day are SAME at MONTH precision",
                            "2024-01-15T10:30:00Z", "2024-01-28T14:45:30Z", "SAME", true),
                    Arguments.of("MONTH", "different months are NOT SAME at MONTH precision",
                            "2024-01-15T10:30:00Z", "2024-02-15T10:30:00Z", "SAME", false),
                    Arguments.of("MONTH", "January is BEFORE February at MONTH precision",
                            "2024-01-31T23:59:59Z", "2024-02-01T00:00:00Z", "BEFORE", true),

                    // DAY precision
                    Arguments.of("DAY", "same day different hour are SAME at DAY precision",
                            "2024-01-15T10:30:00Z", "2024-01-15T22:45:30Z", "SAME", true),
                    Arguments.of("DAY", "different days are NOT SAME at DAY precision",
                            "2024-01-15T10:30:00Z", "2024-01-16T10:30:00Z", "SAME", false),
                    Arguments.of("DAY", "Jan 15 is BEFORE Jan 16 at DAY precision",
                            "2024-01-15T23:59:59Z", "2024-01-16T00:00:00Z", "BEFORE", true),

                    // HOUR precision
                    Arguments.of("HOUR", "same hour different minute are SAME at HOUR precision",
                            "2024-01-15T10:15:00Z", "2024-01-15T10:45:30Z", "SAME", true),
                    Arguments.of("HOUR", "different hours are NOT SAME at HOUR precision",
                            "2024-01-15T10:30:00Z", "2024-01-15T11:30:00Z", "SAME", false),
                    Arguments.of("HOUR", "10:00 is BEFORE 11:00 at HOUR precision",
                            "2024-01-15T10:59:59Z", "2024-01-15T11:00:00Z", "BEFORE", true),

                    // MINUTE precision
                    Arguments.of("MINUTE", "same minute different second are SAME at MINUTE precision",
                            "2024-01-15T10:30:15Z", "2024-01-15T10:30:45Z", "SAME", true),
                    Arguments.of("MINUTE", "different minutes are NOT SAME at MINUTE precision",
                            "2024-01-15T10:30:00Z", "2024-01-15T10:31:00Z", "SAME", false),

                    // SECOND precision
                    Arguments.of("SECOND", "same second different millis are SAME at SECOND precision",
                            "2024-01-15T10:30:00.100Z", "2024-01-15T10:30:00.999Z", "SAME", true),
                    Arguments.of("SECOND", "different seconds are NOT SAME at SECOND precision",
                            "2024-01-15T10:30:00Z", "2024-01-15T10:30:01Z", "SAME", false),

                    // MILLIS precision (default - exact comparison)
                    Arguments.of("MILLIS", "same millisecond are SAME at MILLIS precision",
                            "2024-01-15T10:30:00.500Z", "2024-01-15T10:30:00.500Z", "SAME", true),
                    Arguments.of("MILLIS", "different milliseconds are NOT SAME at MILLIS precision",
                            "2024-01-15T10:30:00.500Z", "2024-01-15T10:30:00.501Z", "SAME", false)
            );
        }

        @ParameterizedTest(name = "Precision case insensitivity: {0}")
        @ValueSource(strings = {"year", "YEAR", "Year", "YeAr", "month", "MONTH", "day", "DAY", "hour", "HOUR"})
        void testPrecisionCaseInsensitivity(String precision) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData("2024-01-15T10:30:00Z");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T22:45:30Z",
                    "operation", "SAME",
                    "precision", precision
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("Precision case insensitivity - precision: {}, result: {}", precision, output.getData());
                        Assertions.assertNotNull(output.getData());
                        // At DAY or higher precision, same day should be SAME
                    })
                    .verifyComplete();
        }
    }

    // ==================== Timezone Handling Tests ====================

    @Nested
    @DisplayName("Timezone Handling Tests")
    class TimezoneHandlingTests {

        @ParameterizedTest(name = "Same timezone comparison: {0}")
        @MethodSource("sameTimezoneProvider")
        void testSameTimezoneComparison(String description, Object sourceDate, Object compareWith,
                                         String operation, boolean expected) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(sourceDate);
            Map<String, Object> processorInput = Map.of("compareWith", compareWith, "operation", operation);

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("{} - Result: {}", description, output.getData());
                        Assertions.assertNotNull(output.getData());
                        Assertions.assertEquals(expected, output.getData());
                    })
                    .verifyComplete();
        }

        private static Stream<Arguments> sameTimezoneProvider() {
            return Stream.of(
                    // Both UTC
                    Arguments.of("both UTC - BEFORE", "2024-01-15T10:30:00Z", "2024-01-16T10:30:00Z", "BEFORE", true),
                    Arguments.of("both UTC - SAME", "2024-01-15T10:30:00Z", "2024-01-15T10:30:00Z", "SAME", true),

                    // Both with same offset
                    Arguments.of("both +05:30 - BEFORE", "2024-01-15T10:30:00+05:30", "2024-01-16T10:30:00+05:30", "BEFORE", true),
                    Arguments.of("both +05:30 - SAME", "2024-01-15T10:30:00+05:30", "2024-01-15T10:30:00+05:30", "SAME", true),

                    // Both no timezone (local time)
                    Arguments.of("both local time - BEFORE", "2024-01-15T10:30:00", "2024-01-16T10:30:00", "BEFORE", true),
                    Arguments.of("both local time - SAME", "2024-01-15T10:30:00", "2024-01-15T10:30:00", "SAME", true),

                    // Epoch millis (timezone agnostic)
                    Arguments.of("epoch millis comparison", 1705312200000L, 1705398600000L, "BEFORE", true)
            );
        }

        @ParameterizedTest(name = "Timezone mismatch should fail: {0}")
        @MethodSource("timezoneMismatchProvider")
        void testTimezoneMismatchShouldFail(String description, Object sourceDate, Object compareWith) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(sourceDate);
            Map<String, Object> processorInput = Map.of("compareWith", compareWith, "operation", "BEFORE");

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .expectError()
                    .verify();
        }

        private static Stream<Arguments> timezoneMismatchProvider() {
            return Stream.of(
                    // UTC vs offset timezone
                    Arguments.of("UTC vs +05:30 (Asia/Kolkata)",
                            "2024-01-15T10:30:00Z", "2024-01-15T16:00:00+05:30"),
                    Arguments.of("+05:30 vs Z (UTC)",
                            "2024-01-15T16:00:00+05:30", "2024-01-15T10:30:00Z"),

                    // Different offset timezones
                    Arguments.of("+05:30 (Asia/Kolkata) vs +01:00 (Africa/Lagos)",
                            "2024-01-15T16:00:00+05:30", "2024-01-15T11:30:00+01:00"),
                    Arguments.of("+08:00 vs -05:00",
                            "2024-01-15T18:30:00+08:00", "2024-01-15T05:30:00-05:00")
            );
        }

        @ParameterizedTest(name = "Timezone normalization: {0}")
        @MethodSource("timezoneNormalizationProvider")
        void testTimezoneNormalization(String description, Object sourceDate, Object compareWith,
                                        String normalizeTimezone, String operation, boolean expected) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(sourceDate);
            Map<String, Object> processorInput = new HashMap<>();
            processorInput.put("compareWith", compareWith);
            processorInput.put("operation", operation);
            processorInput.put("timezone", normalizeTimezone);

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("{} - Normalized to {}: Result: {}", description, normalizeTimezone, output.getData());
                        Assertions.assertNotNull(output.getData());
                        Assertions.assertEquals(expected, output.getData());
                    })
                    .verifyComplete();
        }

        private static Stream<Arguments> timezoneNormalizationProvider() {
            return Stream.of(
                    // Normalize to UTC
                    Arguments.of("normalize UTC vs +05:30 to UTC",
                            "2024-01-15T10:30:00Z", "2024-01-15T16:00:00+05:30", "UTC", "SAME", true),
                    Arguments.of("same instant different zones normalized to UTC",
                            "2024-01-15T10:30:00Z", "2024-01-15T05:30:00-05:00", "UTC", "SAME", true),

                    // Normalize to specific timezone
                    Arguments.of("normalize to America/New_York",
                            "2024-01-15T10:30:00Z", "2024-01-15T05:30:00-05:00", "America/New_York", "SAME", true),
                    Arguments.of("normalize to Asia/Kolkata",
                            "2024-01-15T10:30:00Z", "2024-01-15T16:00:00+05:30", "Asia/Kolkata", "SAME", true),

                    // Different actual times after normalization
                    Arguments.of("earlier time normalized to UTC",
                            "2024-01-15T05:30:00Z", "2024-01-15T16:00:00+05:30", "UTC", "BEFORE", true),
                    Arguments.of("later time normalized to UTC",
                            "2024-01-15T15:30:00Z", "2024-01-15T16:00:00+05:30", "UTC", "AFTER", true)
            );
        }

        @Test
        @DisplayName("Asia/Kolkata vs Africa/Lagos comparison fails without timezone normalization")
        void testAsiaKolkataVsAfricaLagosFailsWithoutNormalization() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            // Asia/Kolkata is UTC+5:30, Africa/Lagos is UTC+1
            context.setData("2024-01-15T16:00:00+05:30");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T11:30:00+01:00",
                    "operation", "SAME"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .expectError()
                    .verify();
        }

        @Test
        @DisplayName("Asia/Kolkata vs Africa/Lagos comparison succeeds with timezone normalization")
        void testAsiaKolkataVsAfricaLagosSucceedsWithNormalization() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            // Both represent the same instant: 2024-01-15T10:30:00Z
            context.setData("2024-01-15T16:00:00+05:30"); // Asia/Kolkata
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T11:30:00+01:00", // Africa/Lagos
                    "operation", "SAME",
                    "timezone", "UTC"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("Asia/Kolkata vs Africa/Lagos normalized to UTC: {}", output.getData());
                        Assertions.assertNotNull(output.getData());
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }
    }

    // ==================== Field-based Comparison Tests ====================

    @Nested
    @DisplayName("Field-based Comparison Tests")
    class FieldBasedComparisonTests {

        @ParameterizedTest(name = "Field comparison: {0}")
        @MethodSource("fieldComparisonProvider")
        void testFieldComparison(String description, Supplier<Map<String, Object>> dataSupplier,
                                  String field, Object compareWith, String operation, boolean expected) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(dataSupplier.get());
            Map<String, Object> processorInput = new HashMap<>();
            processorInput.put("field", field);
            processorInput.put("compareWith", compareWith);
            processorInput.put("operation", operation);

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("{} - Result: {}", description, output.getData());
                        Assertions.assertNotNull(output.getData());
                        Assertions.assertEquals(expected, output.getData());
                    })
                    .verifyComplete();
        }

        private static Stream<Arguments> fieldComparisonProvider() {
            return Stream.of(
                    // Simple field
                    Arguments.of("compare createdAt field BEFORE",
                            (Supplier<Map<String, Object>>) () -> Map.of("createdAt", "2024-01-15T10:30:00Z", "name", "Test"),
                            "createdAt", "2024-01-20T10:30:00Z", "BEFORE", true),
                    Arguments.of("compare timestamp field AFTER",
                            (Supplier<Map<String, Object>>) () -> Map.of("timestamp", 1705398600000L, "event", "Login"),
                            "timestamp", 1705312200000L, "AFTER", true),

                    // Nested field
                    Arguments.of("compare nested field SAME",
                            (Supplier<Map<String, Object>>) () -> {
                                Map<String, Object> meta = new HashMap<>();
                                meta.put("createdAt", "2024-01-15T10:30:00Z");
                                Map<String, Object> data = new HashMap<>();
                                data.put("metadata", meta);
                                return data;
                            },
                            "metadata.createdAt", "2024-01-15T10:30:00Z", "SAME", true),

                    // Deep nested field
                    Arguments.of("compare deep nested field BEFORE",
                            (Supplier<Map<String, Object>>) () -> {
                                Map<String, Object> ts = new HashMap<>();
                                ts.put("value", "2024-01-10T10:30:00Z");
                                Map<String, Object> audit = new HashMap<>();
                                audit.put("created", ts);
                                Map<String, Object> data = new HashMap<>();
                                data.put("audit", audit);
                                return data;
                            },
                            "audit.created.value", "2024-01-15T10:30:00Z", "BEFORE", true)
            );
        }

        @Test
        @DisplayName("Field comparison with epoch millis in nested object")
        void testFieldComparisonWithEpochMillisNested() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            Map<String, Object> times = new HashMap<>();
            times.put("start", 1705312200000L);
            Map<String, Object> data = new HashMap<>();
            data.put("times", times);
            data.put("name", "Event");
            context.setData(data);

            Map<String, Object> processorInput = Map.of(
                    "field", "times.start",
                    "compareWith", 1705398600000L,
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("Nested epoch millis comparison: {}", output.getData());
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }
    }

    // ==================== Operation Case Insensitivity Tests ====================

    @Nested
    @DisplayName("Operation Case Insensitivity Tests")
    class OperationCaseInsensitivityTests {

        @ParameterizedTest(name = "Operation case insensitivity: {0}")
        @ValueSource(strings = {"before", "BEFORE", "Before", "BeFoRe"})
        void testBeforeOperationCaseInsensitivity(String operation) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData("2024-01-10T10:30:00Z");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T10:30:00Z",
                    "operation", operation
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("Operation {} - Result: {}", operation, output.getData());
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }

        @ParameterizedTest(name = "All operations case insensitivity: {0}")
        @ValueSource(strings = {"after", "AFTER", "same", "SAME", "before_or_same", "BEFORE_OR_SAME", "after_or_same", "AFTER_OR_SAME"})
        void testAllOperationsCaseInsensitivity(String operation) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData("2024-01-15T10:30:00Z");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T10:30:00Z",
                    "operation", operation
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("Operation {} - Result: {}", operation, output.getData());
                        Assertions.assertNotNull(output.getData());
                    })
                    .verifyComplete();
        }
    }

    // ==================== Error Scenarios Tests ====================

    @Nested
    @DisplayName("Error Scenarios Tests")
    class ErrorScenariosTests {

        @Test
        @DisplayName("Null source date throws error")
        void testNullSourceDateThrowsError() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(null);
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T10:30:00Z",
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .expectError()
                    .verify();
        }

        @Test
        @DisplayName("Null field value throws error")
        void testNullFieldValueThrowsError() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            Map<String, Object> data = new HashMap<>();
            data.put("timestamp", null);
            context.setData(data);
            Map<String, Object> processorInput = Map.of(
                    "field", "timestamp",
                    "compareWith", "2024-01-15T10:30:00Z",
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .expectError()
                    .verify();
        }

        @ParameterizedTest(name = "Invalid date format: {0}")
        @ValueSource(strings = {"not-a-date", "abc123", "12345abc", ""})
        void testInvalidDateFormatThrowsError(String invalidDate) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(invalidDate);
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T10:30:00Z",
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .expectError()
                    .verify();
        }

        @ParameterizedTest(name = "Unsupported date type: {0}")
        @MethodSource("unsupportedDateTypeProvider")
        void testUnsupportedDateTypeThrowsError(String description, Object unsupportedValue) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(unsupportedValue);
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T10:30:00Z",
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .expectError()
                    .verify();
        }

        private static Stream<Arguments> unsupportedDateTypeProvider() {
            return Stream.of(
                    Arguments.of("boolean true", true),
                    Arguments.of("boolean false", false),
                    Arguments.of("array/list", java.util.List.of("2024-01-15")),
                    Arguments.of("empty list", java.util.List.of())
            );
        }

        @Test
        @DisplayName("Blank field throws validation error")
        void testBlankFieldThrowsValidationError() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(Map.of("date", "2024-01-15T10:30:00Z"));
            Map<String, Object> processorInput = Map.of(
                    "field", "   ",
                    "compareWith", "2024-01-15T10:30:00Z",
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .expectError()
                    .verify();
        }

        @Test
        @DisplayName("Invalid timezone throws validation error")
        void testInvalidTimezoneThrowsValidationError() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData("2024-01-15T10:30:00Z");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T10:30:00Z",
                    "operation", "BEFORE",
                    "timezone", "Invalid/Timezone"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .expectError()
                    .verify();
        }

        @ParameterizedTest(name = "Invalid operation: {0}")
        @ValueSource(strings = {"INVALID", "EQUALS", "GREATER", "LESS", "NOT_EQUAL"})
        void testInvalidOperationThrowsValidationError(String operation) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData("2024-01-15T10:30:00Z");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T10:30:00Z",
                    "operation", operation
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .expectError()
                    .verify();
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Compare dates at millisecond boundary")
        void testMillisecondBoundary() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData("2024-01-15T10:30:00.999Z");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T10:30:01.000Z",
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Compare dates at year boundary")
        void testYearBoundary() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData("2024-12-31T23:59:59Z");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2025-01-01T00:00:00Z",
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Compare leap year date Feb 29")
        void testLeapYearDate() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData("2024-02-29T10:30:00Z");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-03-01T10:30:00Z",
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Compare very large epoch millis")
        void testVeryLargeEpochMillis() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(4102444800000L); // 2100-01-01
            Map<String, Object> processorInput = Map.of(
                    "compareWith", 4102531200000L, // 2100-01-02
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Compare historic dates")
        void testHistoricDates() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData("1900-01-01T00:00:00Z");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2000-01-01T00:00:00Z",
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Compare date-only strings (no time component)")
        void testDateOnlyStrings() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData("2024-01-15");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-16",
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }

        @ParameterizedTest(name = "Epoch seconds vs millis detection: {0}")
        @MethodSource("epochSecondsVsMillisProvider")
        void testEpochSecondsVsMillisDetection(String description, Object source, Object compare, boolean expected) {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData(source);
            Map<String, Object> processorInput = Map.of(
                    "compareWith", compare,
                    "operation", "SAME"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("{} - Source: {}, Compare: {}, Result: {}", description, source, compare, output.getData());
                        Assertions.assertEquals(expected, output.getData());
                    })
                    .verifyComplete();
        }

        private static Stream<Arguments> epochSecondsVsMillisProvider() {
            return Stream.of(
                    // Epoch seconds vs millis - same moment when converted
                    // 1705312200 epoch seconds * 1000 = 1705312200000 epoch millis
                    Arguments.of("epoch seconds same as epoch millis when converted", 1705312200L, 1705312200000L, true),

                    // Compare same epoch millis values
                    Arguments.of("same epoch millis values are SAME", 1705312200000L, 1705312200000L, true),

                    // Compare same epoch seconds values
                    Arguments.of("same epoch seconds values are SAME", 1705312200L, 1705312200L, true)
            );
        }
    }

    // ==================== Combined Precision and Timezone Tests ====================

    @Nested
    @DisplayName("Combined Precision and Timezone Tests")
    class CombinedPrecisionTimezoneTests {

        @Test
        @DisplayName("Precision with timezone normalization")
        void testPrecisionWithTimezoneNormalization() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            // 2024-01-15 10:30 UTC = 2024-01-15 16:00 +05:30
            context.setData("2024-01-15T10:30:00Z");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T22:45:00+05:30", // Different time but same day in UTC
                    "operation", "SAME",
                    "timezone", "UTC",
                    "precision", "DAY"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("Precision DAY with UTC normalization: {}", output.getData());
                        // Both dates when normalized to UTC are on Jan 15, 2024
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Hour precision with timezone normalization")
        void testHourPrecisionWithTimezoneNormalization() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            context.setData("2024-01-15T10:15:00Z");
            Map<String, Object> processorInput = Map.of(
                    "compareWith", "2024-01-15T15:45:00+05:30", // Same hour in UTC
                    "operation", "SAME",
                    "timezone", "UTC",
                    "precision", "HOUR"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("Precision HOUR with UTC normalization: {}", output.getData());
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }
    }

    // ==================== Real-world Scenario Tests ====================

    @Nested
    @DisplayName("Real-world Scenario Tests")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("Check if subscription expired")
        void testSubscriptionExpired() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            Map<String, Object> subscription = new HashMap<>();
            subscription.put("expiryDate", "2024-01-10T23:59:59Z");
            subscription.put("userId", "user123");
            context.setData(subscription);

            Map<String, Object> processorInput = Map.of(
                    "field", "expiryDate",
                    "compareWith", "2024-01-15T10:30:00Z", // Current date
                    "operation", "BEFORE"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("Subscription expiry check: {}", output.getData());
                        // Expiry date is before current date = subscription expired
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Check if event is in the future")
        void testEventInFuture() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            Map<String, Object> event = new HashMap<>();
            event.put("startTime", "2024-06-15T10:00:00Z");
            event.put("name", "Conference");
            context.setData(event);

            Map<String, Object> processorInput = Map.of(
                    "field", "startTime",
                    "compareWith", "2024-01-15T10:30:00Z", // Current date
                    "operation", "AFTER"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("Event in future check: {}", output.getData());
                        // Event start time is after current date = event is in the future
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Check if order is within same business day")
        void testSameBusinessDay() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            Map<String, Object> order = new HashMap<>();
            order.put("orderTime", "2024-01-15T09:15:00Z");
            context.setData(order);

            Map<String, Object> processorInput = Map.of(
                    "field", "orderTime",
                    "compareWith", "2024-01-15T17:45:00Z",
                    "operation", "SAME",
                    "precision", "DAY"
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("Same business day check: {}", output.getData());
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Compare timestamps from different systems with different timezones")
        void testMultiSystemTimestampComparison() {
            ILyshraOpenAppContext context = new LyshraOpenAppContext();
            // System A in Asia/Kolkata
            context.setData("2024-01-15T16:00:00+05:30");

            Map<String, Object> processorInput = Map.of(
                    // System B in America/New_York
                    "compareWith", "2024-01-15T05:30:00-05:00",
                    "operation", "SAME",
                    "timezone", "UTC" // Normalize to compare
            );

            StepVerifier
                    .create(facade.getProcessorExecutor().execute(dateCompareProcessorIdentifier, processorInput, context))
                    .assertNext(output -> {
                        log.info("Multi-system timestamp comparison: {}", output.getData());
                        // Both represent the same instant
                        Assertions.assertEquals(true, output.getData());
                    })
                    .verifyComplete();
        }
    }
}
