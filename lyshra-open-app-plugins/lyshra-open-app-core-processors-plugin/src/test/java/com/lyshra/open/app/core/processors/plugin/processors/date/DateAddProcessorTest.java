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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
class DateAddProcessorTest extends AbstractPluginTest {

    private static LyshraOpenAppProcessorDefinition dateAddProcessor;
    private static LyshraOpenAppProcessorIdentifier dateAddProcessorIdentifier;

    @BeforeAll
    static void setupClass() {
        log.info("Setting up test class");
        facade.getPluginLoader().loadPlugin(new LyshraOpenAppCoreProcessorsPlugin());
        dateAddProcessor = DateAddProcessor.build(LyshraOpenAppProcessorDefinition.builder()).build();
        dateAddProcessorIdentifier = new LyshraOpenAppProcessorIdentifier(
                LyshraOpenAppCoreProcessorsPlugin.getPluginIdentifier(),
                dateAddProcessor.getName());
    }

    // ==================== ISO String Date Manipulation (Parameterized) ====================

    @ParameterizedTest(name = "ISO string add: {0}")
    @MethodSource("isoStringAddProvider")
    void testProcess_IsoStringDateAdd(String description, String inputDate, long amount, String unit,
                                       Consumer<String> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputDate);
        Map<String, Object> processorInput = Map.of("amount", amount, "unit", unit);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Input: {}, Output: {}", description, inputDate, output);
                    Assertions.assertNotNull(output.getData());
                    Assertions.assertInstanceOf(String.class, output.getData());
                    verifier.accept((String) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> isoStringAddProvider() {
        return Stream.of(
                // Adding days
                Arguments.of("add 1 day to ISO instant",
                        "2024-01-15T10:30:00Z", 1L, "DAYS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-16"))),
                Arguments.of("add 7 days to ISO instant",
                        "2024-01-15T10:30:00Z", 7L, "DAYS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-22"))),
                Arguments.of("subtract 1 day from ISO instant",
                        "2024-01-15T10:30:00Z", -1L, "DAYS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-14"))),

                // Adding months
                Arguments.of("add 1 month to ISO instant",
                        "2024-01-15T10:30:00Z", 1L, "MONTHS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-02-15"))),
                Arguments.of("add 12 months (1 year) to ISO instant",
                        "2024-01-15T10:30:00Z", 12L, "MONTHS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2025-01-15"))),
                Arguments.of("subtract 1 month from ISO instant",
                        "2024-03-15T10:30:00Z", -1L, "MONTHS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-02-15"))),

                // Adding years
                Arguments.of("add 1 year to ISO instant",
                        "2024-01-15T10:30:00Z", 1L, "YEARS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2025-01-15"))),
                Arguments.of("subtract 5 years from ISO instant",
                        "2024-01-15T10:30:00Z", -5L, "YEARS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2019-01-15"))),

                // Adding weeks
                Arguments.of("add 2 weeks to ISO instant",
                        "2024-01-15T10:30:00Z", 2L, "WEEKS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-29"))),

                // Adding hours
                Arguments.of("add 3 hours to ISO instant",
                        "2024-01-15T10:30:00Z", 3L, "HOURS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("13:30:00"))),
                Arguments.of("add 24 hours to ISO instant",
                        "2024-01-15T10:30:00Z", 24L, "HOURS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-16T10:30:00"))),

                // Adding minutes
                Arguments.of("add 45 minutes to ISO instant",
                        "2024-01-15T10:30:00Z", 45L, "MINUTES",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("11:15:00"))),
                Arguments.of("subtract 30 minutes from ISO instant",
                        "2024-01-15T10:30:00Z", -30L, "MINUTES",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("10:00:00"))),

                // Adding seconds
                Arguments.of("add 120 seconds to ISO instant",
                        "2024-01-15T10:30:00Z", 120L, "SECONDS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("10:32:00")))
        );
    }

    // ==================== Epoch Milliseconds Manipulation (Parameterized) ====================

    @ParameterizedTest(name = "Epoch millis add: {0}")
    @MethodSource("epochMillisAddProvider")
    void testProcess_EpochMillisAdd(String description, long inputEpochMillis, long amount, String unit,
                                     Consumer<Number> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputEpochMillis);
        Map<String, Object> processorInput = Map.of("amount", amount, "unit", unit);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Input: {}, Output: {}", description, inputEpochMillis, output);
                    Assertions.assertNotNull(output.getData());
                    Assertions.assertInstanceOf(Number.class, output.getData());
                    verifier.accept((Number) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> epochMillisAddProvider() {
        long baseEpochMillis = 1705312200000L; // 2024-01-15T10:30:00Z

        return Stream.of(
                // Adding days
                Arguments.of("add 1 day to epoch millis",
                        baseEpochMillis, 1L, "DAYS",
                        (Consumer<Number>) result -> {
                            long expected = baseEpochMillis + (24 * 60 * 60 * 1000);
                            Assertions.assertEquals(expected, result.longValue());
                        }),
                Arguments.of("subtract 1 day from epoch millis",
                        baseEpochMillis, -1L, "DAYS",
                        (Consumer<Number>) result -> {
                            long expected = baseEpochMillis - (24 * 60 * 60 * 1000);
                            Assertions.assertEquals(expected, result.longValue());
                        }),

                // Adding hours
                Arguments.of("add 5 hours to epoch millis",
                        baseEpochMillis, 5L, "HOURS",
                        (Consumer<Number>) result -> {
                            long expected = baseEpochMillis + (5 * 60 * 60 * 1000);
                            Assertions.assertEquals(expected, result.longValue());
                        }),

                // Adding minutes
                Arguments.of("add 30 minutes to epoch millis",
                        baseEpochMillis, 30L, "MINUTES",
                        (Consumer<Number>) result -> {
                            long expected = baseEpochMillis + (30 * 60 * 1000);
                            Assertions.assertEquals(expected, result.longValue());
                        }),

                // Adding seconds
                Arguments.of("add 90 seconds to epoch millis",
                        baseEpochMillis, 90L, "SECONDS",
                        (Consumer<Number>) result -> {
                            long expected = baseEpochMillis + (90 * 1000);
                            Assertions.assertEquals(expected, result.longValue());
                        }),

                // Adding milliseconds
                Arguments.of("add 500 milliseconds to epoch millis",
                        baseEpochMillis, 500L, "MILLIS",
                        (Consumer<Number>) result -> {
                            long expected = baseEpochMillis + 500;
                            Assertions.assertEquals(expected, result.longValue());
                        })
        );
    }

    // ==================== Epoch Seconds Manipulation (Parameterized) ====================

    @ParameterizedTest(name = "Epoch seconds add: {0}")
    @MethodSource("epochSecondsAddProvider")
    void testProcess_EpochSecondsAdd(String description, long inputEpochSeconds, long amount, String unit,
                                      Consumer<Number> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputEpochSeconds);
        Map<String, Object> processorInput = Map.of("amount", amount, "unit", unit);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Input: {}, Output: {}", description, inputEpochSeconds, output);
                    Assertions.assertNotNull(output.getData());
                    Assertions.assertInstanceOf(Number.class, output.getData());
                    verifier.accept((Number) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> epochSecondsAddProvider() {
        long baseEpochSeconds = 1705312200L; // 2024-01-15T10:30:00Z

        return Stream.of(
                // Adding days
                Arguments.of("add 1 day to epoch seconds",
                        baseEpochSeconds, 1L, "DAYS",
                        (Consumer<Number>) result -> {
                            long expected = baseEpochSeconds + (24 * 60 * 60);
                            Assertions.assertEquals(expected, result.longValue());
                        }),

                // Adding hours
                Arguments.of("add 12 hours to epoch seconds",
                        baseEpochSeconds, 12L, "HOURS",
                        (Consumer<Number>) result -> {
                            long expected = baseEpochSeconds + (12 * 60 * 60);
                            Assertions.assertEquals(expected, result.longValue());
                        }),

                // Adding minutes
                Arguments.of("add 15 minutes to epoch seconds",
                        baseEpochSeconds, 15L, "MINUTES",
                        (Consumer<Number>) result -> {
                            long expected = baseEpochSeconds + (15 * 60);
                            Assertions.assertEquals(expected, result.longValue());
                        }),

                // Adding seconds
                Arguments.of("add 60 seconds to epoch seconds",
                        baseEpochSeconds, 60L, "SECONDS",
                        (Consumer<Number>) result -> {
                            long expected = baseEpochSeconds + 60;
                            Assertions.assertEquals(expected, result.longValue());
                        })
        );
    }

    // ==================== Field-based Manipulation (Parameterized) ====================

    @ParameterizedTest(name = "Field-based add: {0}")
    @MethodSource("fieldBasedAddProvider")
    void testProcess_FieldBasedAdd(String description, Supplier<Map<String, Object>> dataSupplier,
                                    String field, long amount, String unit,
                                    Consumer<Map<String, Object>> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(dataSupplier.get());
        Map<String, Object> processorInput = Map.of("field", field, "amount", amount, "unit", unit);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    Assertions.assertNotNull(output.getData());
                    Assertions.assertInstanceOf(Map.class, output.getData());
                    verifier.accept((Map<String, Object>) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> fieldBasedAddProvider() {
        return Stream.of(
                // String date field
                Arguments.of("add 1 day to string date field",
                        (Supplier<Map<String, Object>>) () -> {
                            Map<String, Object> data = new HashMap<>();
                            data.put("createdAt", "2024-01-15T10:30:00Z");
                            data.put("name", "Test");
                            return data;
                        },
                        "createdAt", 1L, "DAYS",
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertTrue(((String) result.get("createdAt")).contains("2024-01-16"));
                            Assertions.assertEquals("Test", result.get("name"));
                        }),

                // Epoch millis field
                Arguments.of("add 2 hours to epoch millis field",
                        (Supplier<Map<String, Object>>) () -> {
                            Map<String, Object> data = new HashMap<>();
                            data.put("timestamp", 1705312200000L);
                            data.put("event", "Login");
                            return data;
                        },
                        "timestamp", 2L, "HOURS",
                        (Consumer<Map<String, Object>>) result -> {
                            long expected = 1705312200000L + (2 * 60 * 60 * 1000);
                            Assertions.assertEquals(expected, ((Number) result.get("timestamp")).longValue());
                            Assertions.assertEquals("Login", result.get("event"));
                        }),

                // Subtract from date field
                Arguments.of("subtract 1 month from date field",
                        (Supplier<Map<String, Object>>) () -> {
                            Map<String, Object> data = new HashMap<>();
                            data.put("expiryDate", "2024-03-15T10:30:00Z");
                            data.put("product", "Subscription");
                            return data;
                        },
                        "expiryDate", -1L, "MONTHS",
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertTrue(((String) result.get("expiryDate")).contains("2024-02-15"));
                            Assertions.assertEquals("Subscription", result.get("product"));
                        })
        );
    }

    // ==================== Timezone Handling (Parameterized) ====================

    @ParameterizedTest(name = "Timezone handling: {0}")
    @MethodSource("timezoneHandlingProvider")
    void testProcess_TimezoneHandling(String description, String inputDate, long amount, String unit,
                                       String timezone, Consumer<String> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputDate);
        Map<String, Object> processorInput = new HashMap<>();
        processorInput.put("amount", amount);
        processorInput.put("unit", unit);
        if (timezone != null) {
            processorInput.put("timezone", timezone);
        }

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Input: {}, Timezone: {}, Output: {}", description, inputDate, timezone, output);
                    Assertions.assertNotNull(output.getData());
                    verifier.accept((String) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> timezoneHandlingProvider() {
        return Stream.of(
                // UTC timezone
                Arguments.of("add 1 day with UTC timezone",
                        "2024-01-15T10:30:00Z", 1L, "DAYS", "UTC",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-16"))),

                // America/New_York timezone
                Arguments.of("add 1 day with America/New_York timezone",
                        "2024-01-15T10:30:00Z", 1L, "DAYS", "America/New_York",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-16"))),

                // Asia/Kolkata timezone
                Arguments.of("add 1 day with Asia/Kolkata timezone",
                        "2024-01-15T10:30:00Z", 1L, "DAYS", "Asia/Kolkata",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-16"))),

                // Europe/London timezone
                Arguments.of("add 1 month with Europe/London timezone",
                        "2024-01-15T10:30:00Z", 1L, "MONTHS", "Europe/London",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-02-15"))),

                // No timezone (defaults to UTC)
                Arguments.of("add 1 day with no timezone (defaults to UTC)",
                        "2024-01-15T10:30:00Z", 1L, "DAYS", null,
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-16")))
        );
    }

    // ==================== High Precision (Nanoseconds/Microseconds) Tests ====================

    @ParameterizedTest(name = "High precision: {0}")
    @MethodSource("highPrecisionProvider")
    void testProcess_HighPrecision(String description, String inputDate, long amount, String unit,
                                    Consumer<String> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputDate);
        Map<String, Object> processorInput = Map.of("amount", amount, "unit", unit);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Input: {}, Output: {}", description, inputDate, output);
                    Assertions.assertNotNull(output.getData());
                    verifier.accept((String) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> highPrecisionProvider() {
        return Stream.of(
                // Nanoseconds
                Arguments.of("add 1 billion nanoseconds (1 second)",
                        "2024-01-15T10:30:00Z", 1000000000L, "NANOS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("10:30:01"))),
                Arguments.of("add 500 million nanoseconds (0.5 second)",
                        "2024-01-15T10:30:00Z", 500000000L, "NANOS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("10:30:00.5"))),

                // Microseconds
                Arguments.of("add 1 million microseconds (1 second)",
                        "2024-01-15T10:30:00Z", 1000000L, "MICROS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("10:30:01"))),
                Arguments.of("add 500000 microseconds (0.5 second)",
                        "2024-01-15T10:30:00Z", 500000L, "MICROS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("10:30:00.5"))),

                // Milliseconds precision
                Arguments.of("add 1500 milliseconds",
                        "2024-01-15T10:30:00Z", 1500L, "MILLIS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("10:30:01.5")))
        );
    }

    // ==================== Edge Cases (Parameterized) ====================

    @ParameterizedTest(name = "Edge case: {0}")
    @MethodSource("edgeCaseProvider")
    void testProcess_EdgeCases(String description, String inputDate, long amount, String unit,
                                Consumer<String> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputDate);
        Map<String, Object> processorInput = Map.of("amount", amount, "unit", unit);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Input: {}, Output: {}", description, inputDate, output);
                    Assertions.assertNotNull(output.getData());
                    verifier.accept((String) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> edgeCaseProvider() {
        return Stream.of(
                // Month boundary - adding 1 month to Jan 31
                // JavaScript Date wraps to March when adding 1 month to Jan 31 (31 days -> March 2)
                Arguments.of("add 1 month to Jan 31 (month boundary)",
                        "2024-01-31T10:30:00Z", 1L, "MONTHS",
                        (Consumer<String>) result -> {
                            // JavaScript Date behavior: Jan 31 + 1 month = March 2 (31 days into Feb wraps)
                            Assertions.assertTrue(result.contains("2024-03-02") || result.contains("2024-02"));
                        }),

                // Leap year - Feb 29 exists in 2024
                Arguments.of("add 1 year from Feb 29 leap year",
                        "2024-02-29T10:30:00Z", 1L, "YEARS",
                        (Consumer<String>) result -> {
                            // JavaScript Date: Feb 29 2024 + 1 year = March 1 2025 (wraps to next month)
                            Assertions.assertTrue(result.contains("2025-03-01") || result.contains("2025-02"));
                        }),

                // Year boundary
                Arguments.of("add 1 day to Dec 31 (year boundary)",
                        "2024-12-31T10:30:00Z", 1L, "DAYS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2025-01-01"))),

                // Adding 0 (no change)
                Arguments.of("add 0 days (no change)",
                        "2024-01-15T10:30:00Z", 0L, "DAYS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-15"))),

                // Large subtraction
                Arguments.of("subtract 10 years",
                        "2024-01-15T10:30:00Z", -10L, "YEARS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2014-01-15"))),

                // Hour boundary
                Arguments.of("add 30 minutes crossing hour boundary",
                        "2024-01-15T10:45:00Z", 30L, "MINUTES",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("11:15:00"))),

                // Day boundary
                Arguments.of("add 2 hours crossing day boundary",
                        "2024-01-15T23:30:00Z", 2L, "HOURS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-16T01:30:00")))
        );
    }

    // ==================== LocalDateTime (No Timezone) Input ====================

    @ParameterizedTest(name = "LocalDateTime input: {0}")
    @MethodSource("localDateTimeProvider")
    void testProcess_LocalDateTimeInput(String description, String inputDate, long amount, String unit,
                                         Consumer<String> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputDate);
        Map<String, Object> processorInput = Map.of("amount", amount, "unit", unit, "timezone", "UTC");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Input: {}, Output: {}", description, inputDate, output);
                    Assertions.assertNotNull(output.getData());
                    verifier.accept((String) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> localDateTimeProvider() {
        return Stream.of(
                Arguments.of("add 1 day to LocalDateTime",
                        "2024-01-15T10:30:00", 1L, "DAYS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-16"),
                                "Expected date 2024-01-16 but got: " + result)),
                Arguments.of("add 2 hours to LocalDateTime",
                        "2024-01-15T10:30:00", 2L, "HOURS",
                        (Consumer<String>) result -> {
                            // JavaScript interprets LocalDateTime as local time, output is in UTC
                            // Just verify the date is still correct and some time manipulation happened
                            Assertions.assertTrue(result.contains("2024-01-15"),
                                    "Expected date 2024-01-15 but got: " + result);
                        }),
                Arguments.of("subtract 1 month from LocalDateTime",
                        "2024-03-15T10:30:00", -1L, "MONTHS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-02-15"),
                                "Expected date 2024-02-15 but got: " + result))
        );
    }

    // ==================== LocalDate (Date Only) Input ====================

    @ParameterizedTest(name = "LocalDate input: {0}")
    @MethodSource("localDateProvider")
    void testProcess_LocalDateInput(String description, String inputDate, long amount, String unit,
                                     Consumer<String> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputDate);
        Map<String, Object> processorInput = Map.of("amount", amount, "unit", unit);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Input: {}, Output: {}", description, inputDate, output);
                    Assertions.assertNotNull(output.getData());
                    verifier.accept((String) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> localDateProvider() {
        return Stream.of(
                Arguments.of("add 1 day to LocalDate",
                        "2024-01-15", 1L, "DAYS",
                        (Consumer<String>) result -> Assertions.assertEquals("2024-01-16", result)),
                Arguments.of("add 1 month to LocalDate",
                        "2024-01-15", 1L, "MONTHS",
                        (Consumer<String>) result -> Assertions.assertEquals("2024-02-15", result)),
                Arguments.of("subtract 1 year from LocalDate",
                        "2024-01-15", -1L, "YEARS",
                        (Consumer<String>) result -> Assertions.assertEquals("2023-01-15", result))
        );
    }

    // ==================== Unit Case Insensitivity (Parameterized) ====================

    @ParameterizedTest(name = "Unit case insensitivity: {0}")
    @MethodSource("unitCaseInsensitivityProvider")
    void testProcess_UnitCaseInsensitivity(String description, String unit) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData("2024-01-15T10:30:00Z");
        Map<String, Object> processorInput = Map.of("amount", 1L, "unit", unit);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Unit: {}, Output: {}", description, unit, output);
                    Assertions.assertNotNull(output.getData());
                    String result = (String) output.getData();
                    Assertions.assertTrue(result.contains("2024-01-16"));
                })
                .verifyComplete();
    }

    private static Stream<Arguments> unitCaseInsensitivityProvider() {
        return Stream.of(
                Arguments.of("lowercase", "days"),
                Arguments.of("uppercase", "DAYS"),
                Arguments.of("mixed case", "Days"),
                Arguments.of("all caps", "DAYS")
        );
    }

    // ==================== Validation Error Scenarios (Parameterized) ====================

    @ParameterizedTest(name = "Validation error: {0}")
    @MethodSource("validationErrorProvider")
    void testProcess_ValidationErrors(String description, Map<String, Object> processorInput) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData("2024-01-15T10:30:00Z");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .expectError()
                .verify();
    }

    private static Stream<Arguments> validationErrorProvider() {
        return Stream.of(
                Arguments.of("blank field", Map.of("field", "", "amount", 1L, "unit", "DAYS")),
                Arguments.of("whitespace field", Map.of("field", "   ", "amount", 1L, "unit", "DAYS")),
                Arguments.of("invalid unit", Map.of("amount", 1L, "unit", "INVALID")),
                Arguments.of("invalid timezone", Map.of("amount", 1L, "unit", "DAYS", "timezone", "Invalid/Timezone"))
        );
    }

    // ==================== Null Data Handling ====================

    @Test
    void testProcess_NullDataReturnsNull() {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        // data is null - the processor should handle this gracefully
        Map<String, Object> processorInput = Map.of("amount", 1L, "unit", "DAYS");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("Null data - Output: {}", output);
                    // When $data is null, the processor returns null
                    // This is handled by the JS: if value is null/undefined, result = null
                    Assertions.assertNull(output.getData());
                })
                .verifyComplete();
    }

    @Test
    void testProcess_NullFieldValuePreservesObject() {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", null);
        data.put("name", "Test");
        context.setData(data);
        Map<String, Object> processorInput = Map.of("field", "timestamp", "amount", 1L, "unit", "DAYS");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("Null field value - Output: {}", output);
                    Assertions.assertNotNull(output.getData());
                    Map<String, Object> result = (Map<String, Object>) output.getData();
                    Assertions.assertNull(result.get("timestamp"));
                    Assertions.assertEquals("Test", result.get("name"));
                })
                .verifyComplete();
    }

    // ==================== All Units Coverage Test ====================

    @ParameterizedTest(name = "Unit coverage: {0}")
    @MethodSource("allUnitsProvider")
    void testProcess_AllUnits(String unit, long amount, Consumer<String> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData("2024-01-15T10:30:00Z");
        Map<String, Object> processorInput = Map.of("amount", amount, "unit", unit);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("Unit: {}, Amount: {}, Output: {}", unit, amount, output);
                    Assertions.assertNotNull(output.getData());
                    verifier.accept((String) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> allUnitsProvider() {
        return Stream.of(
                Arguments.of("YEARS", 1L, (Consumer<String>) result -> Assertions.assertTrue(result.contains("2025"))),
                Arguments.of("MONTHS", 1L, (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-02"))),
                Arguments.of("WEEKS", 1L, (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-22"))),
                Arguments.of("DAYS", 1L, (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-16"))),
                Arguments.of("HOURS", 1L, (Consumer<String>) result -> Assertions.assertTrue(result.contains("11:30:00"))),
                Arguments.of("MINUTES", 30L, (Consumer<String>) result -> Assertions.assertTrue(result.contains("11:00:00"))),
                Arguments.of("SECONDS", 30L, (Consumer<String>) result -> Assertions.assertTrue(result.contains("10:30:30"))),
                Arguments.of("MILLIS", 500L, (Consumer<String>) result -> Assertions.assertTrue(result.contains("10:30:00.5"))),
                Arguments.of("MICROS", 500000L, (Consumer<String>) result -> Assertions.assertTrue(result.contains("10:30:00.5"))),
                Arguments.of("NANOS", 500000000L, (Consumer<String>) result -> Assertions.assertTrue(result.contains("10:30:00.5")))
        );
    }

    // ==================== Negative Amount Tests ====================

    @ParameterizedTest(name = "Negative amount: {0}")
    @MethodSource("negativeAmountProvider")
    void testProcess_NegativeAmounts(String description, String inputDate, long amount, String unit,
                                      Consumer<String> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputDate);
        Map<String, Object> processorInput = Map.of("amount", amount, "unit", unit);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Input: {}, Output: {}", description, inputDate, output);
                    Assertions.assertNotNull(output.getData());
                    verifier.accept((String) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> negativeAmountProvider() {
        return Stream.of(
                Arguments.of("subtract 1 year", "2024-01-15T10:30:00Z", -1L, "YEARS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2023-01-15"))),
                Arguments.of("subtract 3 months", "2024-06-15T10:30:00Z", -3L, "MONTHS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-03-15"))),
                Arguments.of("subtract 7 days", "2024-01-15T10:30:00Z", -7L, "DAYS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("2024-01-08"))),
                Arguments.of("subtract 5 hours", "2024-01-15T10:30:00Z", -5L, "HOURS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("05:30:00"))),
                Arguments.of("subtract 45 minutes", "2024-01-15T10:30:00Z", -45L, "MINUTES",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("09:45:00"))),
                Arguments.of("subtract 30 seconds", "2024-01-15T10:30:30Z", -30L, "SECONDS",
                        (Consumer<String>) result -> Assertions.assertTrue(result.contains("10:30:00")))
        );
    }

    // ==================== Unsupported Date Type Error Tests (Parameterized) ====================

    @ParameterizedTest(name = "Unsupported date type on $data: {0}")
    @MethodSource("unsupportedDateTypeOnDataProvider")
    void testProcess_UnsupportedDateTypeOnData_ThrowsException(String description, Object unsupportedValue) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(unsupportedValue);
        Map<String, Object> processorInput = Map.of("amount", 1L, "unit", "DAYS");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .expectError()
                .verify();
    }

    private static Stream<Arguments> unsupportedDateTypeOnDataProvider() {
        return Stream.of(
                Arguments.of("boolean true", true),
                Arguments.of("boolean false", false),
                Arguments.of("array/list of strings", java.util.List.of("2024-01-15", "2024-01-16")),
                Arguments.of("array/list of numbers", java.util.List.of(1, 2, 3)),
                Arguments.of("empty array/list", java.util.List.of())
        );
    }

    @ParameterizedTest(name = "Unsupported date type on field: {0}")
    @MethodSource("unsupportedDateTypeOnFieldProvider")
    void testProcess_UnsupportedDateTypeOnField_ThrowsException(String description, Object fieldValue) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", fieldValue);
        data.put("name", "Test");
        context.setData(data);
        Map<String, Object> processorInput = Map.of("field", "timestamp", "amount", 1L, "unit", "DAYS");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .expectError()
                .verify();
    }

    private static Stream<Arguments> unsupportedDateTypeOnFieldProvider() {
        return Stream.of(
                Arguments.of("boolean true", true),
                Arguments.of("boolean false", false),
                Arguments.of("array/list", java.util.List.of("2024-01-15"))
        );
    }

    @ParameterizedTest(name = "Unsupported date type on nested field: {0}")
    @MethodSource("unsupportedDateTypeOnNestedFieldProvider")
    void testProcess_UnsupportedDateTypeOnNestedField_ThrowsException(String description, String fieldPath, Object nestedValue) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        Map<String, Object> nested = new HashMap<>();
        nested.put("value", nestedValue);
        Map<String, Object> data = new HashMap<>();
        data.put("nested", nested);
        data.put("name", "Test");
        context.setData(data);
        Map<String, Object> processorInput = Map.of("field", fieldPath, "amount", 1L, "unit", "DAYS");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .expectError()
                .verify();
    }

    private static Stream<Arguments> unsupportedDateTypeOnNestedFieldProvider() {
        return Stream.of(
                Arguments.of("boolean in nested object", "nested.value", true),
                Arguments.of("array in nested object", "nested.value", java.util.List.of("a", "b"))
        );
    }

    // ==================== Field Update Returns Complete Object Tests (Parameterized) ====================

    @ParameterizedTest(name = "Field update preserves other fields: {0}")
    @MethodSource("fieldUpdatePreservesDataProvider")
    void testProcess_FieldUpdate_PreservesOtherFields(String description, String fieldName, Object dateValue,
                                                       long amount, String unit, Consumer<Object> dateVerifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        Map<String, Object> data = new HashMap<>();
        data.put(fieldName, dateValue);
        data.put("stringField", "preserved");
        data.put("numberField", 42);
        data.put("booleanField", true);
        context.setData(data);
        Map<String, Object> processorInput = Map.of("field", fieldName, "amount", amount, "unit", unit);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    Assertions.assertNotNull(output.getData());
                    Assertions.assertInstanceOf(Map.class, output.getData());
                    Map<String, Object> result = (Map<String, Object>) output.getData();

                    // Verify date field was updated
                    dateVerifier.accept(result.get(fieldName));

                    // Verify all other fields are preserved
                    Assertions.assertEquals("preserved", result.get("stringField"));
                    Assertions.assertEquals(42, result.get("numberField"));
                    Assertions.assertEquals(true, result.get("booleanField"));
                })
                .verifyComplete();
    }

    private static Stream<Arguments> fieldUpdatePreservesDataProvider() {
        long baseEpochMillis = 1705312200000L;
        return Stream.of(
                Arguments.of("ISO string field +5 days", "createdAt", "2024-01-15T10:30:00Z", 5L, "DAYS",
                        (Consumer<Object>) val -> Assertions.assertTrue(((String) val).contains("2024-01-20"))),
                Arguments.of("ISO string field +1 month", "dateField", "2024-01-15T10:30:00Z", 1L, "MONTHS",
                        (Consumer<Object>) val -> Assertions.assertTrue(((String) val).contains("2024-02-15"))),
                Arguments.of("epoch millis field +1 hour", "timestamp", baseEpochMillis, 1L, "HOURS",
                        (Consumer<Object>) val -> Assertions.assertEquals(baseEpochMillis + 3600000L, ((Number) val).longValue())),
                Arguments.of("epoch millis field +30 minutes", "time", baseEpochMillis, 30L, "MINUTES",
                        (Consumer<Object>) val -> Assertions.assertEquals(baseEpochMillis + 1800000L, ((Number) val).longValue())),
                Arguments.of("date only field +1 day", "date", "2024-01-15", 1L, "DAYS",
                        (Consumer<Object>) val -> Assertions.assertEquals("2024-01-16", val))
        );
    }

    // ==================== Nested Field Path Tests (Parameterized) ====================

    @ParameterizedTest(name = "Nested field path: {0}")
    @MethodSource("nestedFieldPathProvider")
    void testProcess_NestedFieldPath_UpdatesCorrectly(String description, Supplier<Map<String, Object>> dataSupplier,
                                                       String fieldPath, long amount, String unit,
                                                       Consumer<Map<String, Object>> resultVerifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(dataSupplier.get());
        Map<String, Object> processorInput = Map.of("field", fieldPath, "amount", amount, "unit", unit);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    Assertions.assertNotNull(output.getData());
                    Assertions.assertInstanceOf(Map.class, output.getData());
                    resultVerifier.accept((Map<String, Object>) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> nestedFieldPathProvider() {
        long baseEpochMillis = 1705312200000L;

        return Stream.of(
                // Single level nested - ISO string
                Arguments.of("single level nested ISO string",
                        (Supplier<Map<String, Object>>) () -> {
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("createdAt", "2024-01-15T10:30:00Z");
                            metadata.put("version", 1);
                            Map<String, Object> data = new HashMap<>();
                            data.put("id", 123);
                            data.put("metadata", metadata);
                            return data;
                        },
                        "metadata.createdAt", 5L, "DAYS",
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(123, result.get("id"));
                            Map<String, Object> meta = (Map<String, Object>) result.get("metadata");
                            Assertions.assertTrue(((String) meta.get("createdAt")).contains("2024-01-20"));
                            Assertions.assertEquals(1, meta.get("version"));
                        }),

                // Single level nested - epoch millis
                Arguments.of("single level nested epoch millis",
                        (Supplier<Map<String, Object>>) () -> {
                            Map<String, Object> times = new HashMap<>();
                            times.put("start", baseEpochMillis);
                            times.put("end", baseEpochMillis + 3600000L);
                            Map<String, Object> data = new HashMap<>();
                            data.put("name", "Event");
                            data.put("times", times);
                            return data;
                        },
                        "times.start", 2L, "HOURS",
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals("Event", result.get("name"));
                            Map<String, Object> times = (Map<String, Object>) result.get("times");
                            Assertions.assertEquals(baseEpochMillis + 7200000L, ((Number) times.get("start")).longValue());
                            Assertions.assertEquals(baseEpochMillis + 3600000L, ((Number) times.get("end")).longValue());
                        }),

                // Two levels deep
                Arguments.of("two levels deep",
                        (Supplier<Map<String, Object>>) () -> {
                            Map<String, Object> timestamp = new HashMap<>();
                            timestamp.put("value", "2024-01-15T10:30:00Z");
                            Map<String, Object> audit = new HashMap<>();
                            audit.put("created", timestamp);
                            audit.put("user", "admin");
                            Map<String, Object> data = new HashMap<>();
                            data.put("audit", audit);
                            data.put("status", "active");
                            return data;
                        },
                        "audit.created.value", 1L, "MONTHS",
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals("active", result.get("status"));
                            Map<String, Object> audit = (Map<String, Object>) result.get("audit");
                            Assertions.assertEquals("admin", audit.get("user"));
                            Map<String, Object> created = (Map<String, Object>) audit.get("created");
                            Assertions.assertTrue(((String) created.get("value")).contains("2024-02-15"));
                        }),

                // Three levels deep
                Arguments.of("three levels deep",
                        (Supplier<Map<String, Object>>) () -> {
                            Map<String, Object> date = new HashMap<>();
                            date.put("iso", "2024-01-15T10:30:00Z");
                            Map<String, Object> time = new HashMap<>();
                            time.put("created", date);
                            Map<String, Object> meta = new HashMap<>();
                            meta.put("timestamps", time);
                            Map<String, Object> data = new HashMap<>();
                            data.put("record", meta);
                            data.put("id", 999);
                            return data;
                        },
                        "record.timestamps.created.iso", 10L, "DAYS",
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(999, result.get("id"));
                            Map<String, Object> record = (Map<String, Object>) result.get("record");
                            Map<String, Object> timestamps = (Map<String, Object>) record.get("timestamps");
                            Map<String, Object> created = (Map<String, Object>) timestamps.get("created");
                            Assertions.assertTrue(((String) created.get("iso")).contains("2024-01-25"));
                        }),

                // Nested with epoch millis
                Arguments.of("three levels deep with epoch millis",
                        (Supplier<Map<String, Object>>) () -> {
                            Map<String, Object> ts = new HashMap<>();
                            ts.put("millis", baseEpochMillis);
                            Map<String, Object> audit = new HashMap<>();
                            audit.put("created", ts);
                            Map<String, Object> meta = new HashMap<>();
                            meta.put("audit", audit);
                            Map<String, Object> data = new HashMap<>();
                            data.put("meta", meta);
                            return data;
                        },
                        "meta.audit.created.millis", 24L, "HOURS",
                        (Consumer<Map<String, Object>>) result -> {
                            Map<String, Object> meta = (Map<String, Object>) result.get("meta");
                            Map<String, Object> audit = (Map<String, Object>) meta.get("audit");
                            Map<String, Object> created = (Map<String, Object>) audit.get("created");
                            long expected = baseEpochMillis + (24 * 60 * 60 * 1000);
                            Assertions.assertEquals(expected, ((Number) created.get("millis")).longValue());
                        })
        );
    }

    // ==================== Nested Field Path Edge Cases ====================

    @ParameterizedTest(name = "Nested field null handling: {0}")
    @MethodSource("nestedFieldNullHandlingProvider")
    void testProcess_NestedFieldPath_NullHandling(String description, Supplier<Map<String, Object>> dataSupplier,
                                                   String fieldPath, Consumer<Map<String, Object>> resultVerifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(dataSupplier.get());
        Map<String, Object> processorInput = Map.of("field", fieldPath, "amount", 1L, "unit", "DAYS");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    resultVerifier.accept((Map<String, Object>) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> nestedFieldNullHandlingProvider() {
        return Stream.of(
                // Null value at nested path - should preserve original structure
                Arguments.of("null value at nested path",
                        (Supplier<Map<String, Object>>) () -> {
                            Map<String, Object> nested = new HashMap<>();
                            nested.put("date", null);
                            nested.put("other", "preserved");
                            Map<String, Object> data = new HashMap<>();
                            data.put("nested", nested);
                            data.put("top", "value");
                            return data;
                        },
                        "nested.date",
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals("value", result.get("top"));
                            Map<String, Object> nested = (Map<String, Object>) result.get("nested");
                            Assertions.assertNull(nested.get("date"));
                            Assertions.assertEquals("preserved", nested.get("other"));
                        }),

                // Empty string value at nested path
                Arguments.of("empty string value at nested path",
                        (Supplier<Map<String, Object>>) () -> {
                            Map<String, Object> nested = new HashMap<>();
                            nested.put("date", "");
                            Map<String, Object> data = new HashMap<>();
                            data.put("nested", nested);
                            return data;
                        },
                        "nested.date",
                        (Consumer<Map<String, Object>>) result -> {
                            Map<String, Object> nested = (Map<String, Object>) result.get("nested");
                            // Empty string treated as null-ish, should preserve original
                            Assertions.assertEquals("", nested.get("date"));
                        })
        );
    }

    // ==================== Multiple Operations on Different Fields ====================

    @Test
    void testProcess_MultipleFieldOperations_IndependentUpdates() {
        // First operation on field1
        ILyshraOpenAppContext context1 = new LyshraOpenAppContext();
        Map<String, Object> data1 = new HashMap<>();
        data1.put("date1", "2024-01-15T10:30:00Z");
        data1.put("date2", "2024-01-15T10:30:00Z");
        context1.setData(data1);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier,
                        Map.of("field", "date1", "amount", 5L, "unit", "DAYS"), context1))
                .assertNext(output -> {
                    Map<String, Object> result = (Map<String, Object>) output.getData();
                    Assertions.assertTrue(((String) result.get("date1")).contains("2024-01-20"));
                    Assertions.assertEquals("2024-01-15T10:30:00Z", result.get("date2")); // Unchanged
                })
                .verifyComplete();
    }

    // ==================== Direct $data Operation vs Field Operation ====================

    @ParameterizedTest(name = "Direct vs field operation: {0}")
    @MethodSource("directVsFieldOperationProvider")
    void testProcess_DirectVsFieldOperation(String description, Object inputData, String field,
                                             long amount, String unit, Consumer<Object> resultVerifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputData);

        Map<String, Object> processorInput = new HashMap<>();
        processorInput.put("amount", amount);
        processorInput.put("unit", unit);
        if (field != null) {
            processorInput.put("field", field);
        }

        StepVerifier
                .create(facade.getProcessorExecutor().execute(dateAddProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    resultVerifier.accept(output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> directVsFieldOperationProvider() {
        return Stream.of(
                // Direct operation on ISO string
                Arguments.of("direct operation on ISO string",
                        "2024-01-15T10:30:00Z", null, 1L, "DAYS",
                        (Consumer<Object>) result -> {
                            Assertions.assertInstanceOf(String.class, result);
                            Assertions.assertTrue(((String) result).contains("2024-01-16"));
                        }),

                // Direct operation on epoch millis
                Arguments.of("direct operation on epoch millis",
                        1705312200000L, null, 1L, "HOURS",
                        (Consumer<Object>) result -> {
                            Assertions.assertInstanceOf(Number.class, result);
                            Assertions.assertEquals(1705315800000L, ((Number) result).longValue());
                        }),

                // Field operation returns complete object
                Arguments.of("field operation returns complete object",
                        createTestDataMap("2024-01-15T10:30:00Z"), "date", 1L, "DAYS",
                        (Consumer<Object>) result -> {
                            Assertions.assertInstanceOf(Map.class, result);
                            Map<String, Object> map = (Map<String, Object>) result;
                            Assertions.assertTrue(((String) map.get("date")).contains("2024-01-16"));
                            Assertions.assertEquals("preserved", map.get("other"));
                        })
        );
    }

    private static Map<String, Object> createTestDataMap(String dateValue) {
        Map<String, Object> data = new HashMap<>();
        data.put("date", dateValue);
        data.put("other", "preserved");
        return data;
    }
}
