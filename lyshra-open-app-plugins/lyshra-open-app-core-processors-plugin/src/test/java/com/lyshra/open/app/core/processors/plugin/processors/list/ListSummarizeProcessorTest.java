package com.lyshra.open.app.core.processors.plugin.processors.list;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
class ListSummarizeProcessorTest extends AbstractPluginTest {

    private static LyshraOpenAppProcessorDefinition listSummarizeProcessor;
    private static LyshraOpenAppProcessorIdentifier listSummarizeProcessorIdentifier;

    @BeforeAll
    static void setupClass() {
        log.info("Setting up test class");
        facade.getPluginLoader().loadPlugin(new LyshraOpenAppCoreProcessorsPlugin());
        listSummarizeProcessor = ListSummarizeProcessor.build(LyshraOpenAppProcessorDefinition.builder()).build();
        listSummarizeProcessorIdentifier = new LyshraOpenAppProcessorIdentifier(
                LyshraOpenAppCoreProcessorsPlugin.getPluginIdentifier(),
                listSummarizeProcessor.getName());
    }

    // ==================== Numeric List Summarization (Parameterized) ====================

    @ParameterizedTest(name = "Summarize numeric list: {0}")
    @MethodSource("numericSummarizeProvider")
    void testProcess_SummarizeNumericList(String description, List<?> inputList,
                                           Number expectedSum, Number expectedAvg,
                                           Number expectedMin, Number expectedMax) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputList);
        Map<String, Object> processorInput = Collections.emptyMap();

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSummarizeProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    Map<String, Object> result = (Map<String, Object>) output.getData();

                    Assertions.assertEquals(inputList.size(), result.get("count"));
                    Assertions.assertEquals("number", result.get("type"));
                    Assertions.assertEquals(inputList.get(0), result.get("first"));
                    Assertions.assertEquals(inputList.get(inputList.size() - 1), result.get("last"));

                    // Compare numeric values with tolerance for floating point
                    assertNumericEquals(expectedSum, result.get("sum"));
                    assertNumericEquals(expectedAvg, result.get("avg"));
                    assertNumericEquals(expectedMin, result.get("min"));
                    assertNumericEquals(expectedMax, result.get("max"));

                    // String-specific stats should be null
                    Assertions.assertNull(result.get("minLength"));
                    Assertions.assertNull(result.get("maxLength"));
                    // Boolean-specific stats should be null
                    Assertions.assertNull(result.get("trueCount"));
                    Assertions.assertNull(result.get("falseCount"));
                })
                .verifyComplete();
    }

    private void assertNumericEquals(Number expected, Object actual) {
        if (expected instanceof Double || actual instanceof Double) {
            Assertions.assertEquals(expected.doubleValue(), ((Number) actual).doubleValue(), 0.0001);
        } else {
            Assertions.assertEquals(expected.intValue(), ((Number) actual).intValue());
        }
    }

    private static Stream<Arguments> numericSummarizeProvider() {
        return Stream.of(
                Arguments.of("positive integers", List.of(1, 2, 3, 4, 5), 15, 3.0, 1, 5),
                Arguments.of("negative integers", List.of(-5, -3, -1), -9, -3.0, -5, -1),
                Arguments.of("mixed integers", List.of(-10, 0, 10, 20), 20, 5.0, -10, 20),
                Arguments.of("floating point", List.of(1.5, 2.5, 3.5), 7.5, 2.5, 1.5, 3.5),
                Arguments.of("single element", List.of(42), 42, 42.0, 42, 42),
                Arguments.of("all same values", List.of(5, 5, 5, 5), 20, 5.0, 5, 5),
                Arguments.of("large numbers", List.of(1000000, 2000000, 3000000), 6000000, 2000000.0, 1000000, 3000000)
        );
    }

    // ==================== String List Summarization (Parameterized) ====================

    @ParameterizedTest(name = "Summarize string list: {0}")
    @MethodSource("stringSummarizeProvider")
    void testProcess_SummarizeStringList(String description, List<String> inputList,
                                          String expectedMin, String expectedMax,
                                          int expectedMinLength, int expectedMaxLength) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputList);
        Map<String, Object> processorInput = Collections.emptyMap();

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSummarizeProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    Map<String, Object> result = (Map<String, Object>) output.getData();

                    Assertions.assertEquals(inputList.size(), result.get("count"));
                    Assertions.assertEquals("string", result.get("type"));
                    Assertions.assertEquals(inputList.get(0), result.get("first"));
                    Assertions.assertEquals(inputList.get(inputList.size() - 1), result.get("last"));

                    Assertions.assertEquals(expectedMin, result.get("min"));
                    Assertions.assertEquals(expectedMax, result.get("max"));
                    Assertions.assertEquals(expectedMinLength, result.get("minLength"));
                    Assertions.assertEquals(expectedMaxLength, result.get("maxLength"));

                    // Numeric-specific stats should be null
                    Assertions.assertNull(result.get("sum"));
                    Assertions.assertNull(result.get("avg"));
                    // Boolean-specific stats should be null
                    Assertions.assertNull(result.get("trueCount"));
                    Assertions.assertNull(result.get("falseCount"));
                })
                .verifyComplete();
    }

    private static Stream<Arguments> stringSummarizeProvider() {
        return Stream.of(
                Arguments.of("simple strings", List.of("banana", "apple", "cherry"), "apple", "cherry", 5, 6),
                Arguments.of("single char strings", List.of("a", "z", "m"), "a", "z", 1, 1),
                Arguments.of("varying lengths", List.of("hi", "hello", "hey"), "hello", "hi", 2, 5),
                Arguments.of("single string", List.of("only"), "only", "only", 4, 4),
                Arguments.of("empty and non-empty", List.of("a", "abc", "ab"), "a", "abc", 1, 3)
        );
    }

    // ==================== Boolean List Summarization (Parameterized) ====================

    @ParameterizedTest(name = "Summarize boolean list: {0}")
    @MethodSource("booleanSummarizeProvider")
    void testProcess_SummarizeBooleanList(String description, List<Boolean> inputList,
                                           int expectedTrueCount, int expectedFalseCount) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputList);
        Map<String, Object> processorInput = Collections.emptyMap();

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSummarizeProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    Map<String, Object> result = (Map<String, Object>) output.getData();

                    Assertions.assertEquals(inputList.size(), result.get("count"));
                    Assertions.assertEquals("boolean", result.get("type"));
                    Assertions.assertEquals(inputList.get(0), result.get("first"));
                    Assertions.assertEquals(inputList.get(inputList.size() - 1), result.get("last"));

                    Assertions.assertEquals(expectedTrueCount, result.get("trueCount"));
                    Assertions.assertEquals(expectedFalseCount, result.get("falseCount"));

                    // Numeric-specific stats should be null
                    Assertions.assertNull(result.get("sum"));
                    Assertions.assertNull(result.get("avg"));
                    Assertions.assertNull(result.get("min"));
                    Assertions.assertNull(result.get("max"));
                    // String-specific stats should be null
                    Assertions.assertNull(result.get("minLength"));
                    Assertions.assertNull(result.get("maxLength"));
                })
                .verifyComplete();
    }

    private static Stream<Arguments> booleanSummarizeProvider() {
        return Stream.of(
                Arguments.of("mixed booleans", List.of(true, false, true, false, true), 3, 2),
                Arguments.of("all true", List.of(true, true, true), 3, 0),
                Arguments.of("all false", List.of(false, false, false), 0, 3),
                Arguments.of("single true", List.of(true), 1, 0),
                Arguments.of("single false", List.of(false), 0, 1)
        );
    }

    // ==================== Object Field Summarization (Parameterized) ====================

    @ParameterizedTest(name = "Summarize objects by field: {0}")
    @MethodSource("objectFieldSummarizeProvider")
    void testProcess_SummarizeObjectsByField(String description, Supplier<List<?>> dataSupplier,
                                              String field, Consumer<Map<String, Object>> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(dataSupplier.get());
        Map<String, Object> processorInput = Map.of("field", field);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSummarizeProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    Map<String, Object> result = (Map<String, Object>) output.getData();
                    verifier.accept(result);
                })
                .verifyComplete();
    }

    private static Stream<Arguments> objectFieldSummarizeProvider() {
        return Stream.of(
                // Numeric field
                Arguments.of("objects by numeric field 'price'",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("name", "Item1", "price", 100),
                                Map.of("name", "Item2", "price", 200),
                                Map.of("name", "Item3", "price", 150)
                        ),
                        "price",
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(3, result.get("count"));
                            Assertions.assertEquals("number", result.get("type"));
                            Assertions.assertEquals(450, ((Number) result.get("sum")).intValue());
                            Assertions.assertEquals(150.0, ((Number) result.get("avg")).doubleValue(), 0.001);
                            Assertions.assertEquals(100, ((Number) result.get("min")).intValue());
                            Assertions.assertEquals(200, ((Number) result.get("max")).intValue());
                        }),
                // String field
                Arguments.of("objects by string field 'name'",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("name", "Charlie", "age", 30),
                                Map.of("name", "Alice", "age", 25),
                                Map.of("name", "Bob", "age", 35)
                        ),
                        "name",
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(3, result.get("count"));
                            Assertions.assertEquals("string", result.get("type"));
                            Assertions.assertEquals("Alice", result.get("min"));
                            Assertions.assertEquals("Charlie", result.get("max"));
                            Assertions.assertEquals(3, result.get("minLength")); // "Bob"
                            Assertions.assertEquals(7, result.get("maxLength")); // "Charlie"
                        }),
                // Boolean field
                Arguments.of("objects by boolean field 'active'",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("name", "Item1", "active", true),
                                Map.of("name", "Item2", "active", false),
                                Map.of("name", "Item3", "active", true),
                                Map.of("name", "Item4", "active", true)
                        ),
                        "active",
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(4, result.get("count"));
                            Assertions.assertEquals("boolean", result.get("type"));
                            Assertions.assertEquals(3, result.get("trueCount"));
                            Assertions.assertEquals(1, result.get("falseCount"));
                        }),
                // Floating point field
                Arguments.of("objects by floating point field 'score'",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("name", "Student1", "score", 85.5),
                                Map.of("name", "Student2", "score", 92.0),
                                Map.of("name", "Student3", "score", 78.5)
                        ),
                        "score",
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(3, result.get("count"));
                            Assertions.assertEquals("number", result.get("type"));
                            Assertions.assertEquals(256.0, ((Number) result.get("sum")).doubleValue(), 0.001);
                            Assertions.assertEquals(85.333, ((Number) result.get("avg")).doubleValue(), 0.01);
                            Assertions.assertEquals(78.5, ((Number) result.get("min")).doubleValue(), 0.001);
                            Assertions.assertEquals(92.0, ((Number) result.get("max")).doubleValue(), 0.001);
                        })
        );
    }

    // ==================== Edge Cases (Parameterized) ====================

    @ParameterizedTest(name = "Edge case: {0}")
    @MethodSource("edgeCaseProvider")
    void testProcess_EdgeCases(String description, Supplier<List<?>> dataSupplier,
                                Map<String, Object> processorInput, Consumer<Map<String, Object>> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(dataSupplier.get());

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSummarizeProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    Map<String, Object> result = (Map<String, Object>) output.getData();
                    verifier.accept(result);
                })
                .verifyComplete();
    }

    private static Stream<Arguments> edgeCaseProvider() {
        return Stream.of(
                // Empty list
                Arguments.of("empty list",
                        (Supplier<List<?>>) ArrayList::new,
                        Collections.emptyMap(),
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(0, result.get("count"));
                            Assertions.assertNull(result.get("type"));
                            Assertions.assertNull(result.get("first"));
                            Assertions.assertNull(result.get("last"));
                            Assertions.assertNull(result.get("sum"));
                        }),
                // List with null values (numeric)
                Arguments.of("list with null values",
                        (Supplier<List<?>>) () -> {
                            List<Integer> list = new ArrayList<>();
                            list.add(1);
                            list.add(null);
                            list.add(3);
                            list.add(null);
                            list.add(5);
                            return list;
                        },
                        Collections.emptyMap(),
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(5, result.get("count"));
                            Assertions.assertEquals(2, result.get("nullCount"));
                            Assertions.assertEquals("number", result.get("type"));
                            Assertions.assertEquals(1, result.get("first"));
                            Assertions.assertEquals(5, result.get("last"));
                            Assertions.assertEquals(9, ((Number) result.get("sum")).intValue());
                            Assertions.assertEquals(3.0, ((Number) result.get("avg")).doubleValue(), 0.001);
                        }),
                // Objects with missing field
                Arguments.of("objects with missing field values",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("name", "Item1", "price", 100),
                                Map.of("name", "Item2"), // missing price
                                Map.of("name", "Item3", "price", 200)
                        ),
                        Map.of("field", "price"),
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(3, result.get("count"));
                            Assertions.assertEquals(1, result.get("nullCount")); // one missing
                            Assertions.assertEquals("number", result.get("type"));
                            Assertions.assertEquals(300, ((Number) result.get("sum")).intValue());
                            Assertions.assertEquals(150.0, ((Number) result.get("avg")).doubleValue(), 0.001);
                        }),
                // No field specified for objects (summarizes entire objects - will be 'object' type)
                Arguments.of("objects without field (direct summarize)",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("a", 1),
                                Map.of("b", 2),
                                Map.of("c", 3)
                        ),
                        Collections.emptyMap(),
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(3, result.get("count"));
                            Assertions.assertEquals("object", result.get("type"));
                            // For objects, only count/first/last are meaningful
                            Assertions.assertNotNull(result.get("first"));
                            Assertions.assertNotNull(result.get("last"));
                        })
        );
    }

    // ==================== Null Data Handling ====================

    @Test
    void testProcess_NullDataReturnsNullSummary() {
        // When context data is null/unset, processor returns a summary with null values
        // This is graceful degradation rather than throwing an error
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        Map<String, Object> processorInput = Collections.emptyMap();

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSummarizeProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    Map<String, Object> result = (Map<String, Object>) output.getData();
                    // Null data results in null count and empty summary
                    Assertions.assertNull(result.get("type"));
                    Assertions.assertNull(result.get("first"));
                    Assertions.assertNull(result.get("last"));
                    Assertions.assertNull(result.get("sum"));
                })
                .verifyComplete();
    }

    // ==================== Field-based vs Direct Summarization Comparison ====================

    @Test
    void testProcess_DirectVsFieldSummarization() {
        // Direct summarization (no field)
        ILyshraOpenAppContext directContext = new LyshraOpenAppContext();
        directContext.setData(List.of(10, 20, 30));

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSummarizeProcessorIdentifier, Collections.emptyMap(), directContext))
                .assertNext(output -> {
                    Map<String, Object> result = (Map<String, Object>) output.getData();
                    Assertions.assertEquals(60, ((Number) result.get("sum")).intValue());
                    Assertions.assertEquals(20.0, ((Number) result.get("avg")).doubleValue(), 0.001);
                })
                .verifyComplete();

        // Field-based summarization
        ILyshraOpenAppContext fieldContext = new LyshraOpenAppContext();
        fieldContext.setData(List.of(
                Map.of("value", 10),
                Map.of("value", 20),
                Map.of("value", 30)
        ));

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSummarizeProcessorIdentifier, Map.of("field", "value"), fieldContext))
                .assertNext(output -> {
                    Map<String, Object> result = (Map<String, Object>) output.getData();
                    Assertions.assertEquals(60, ((Number) result.get("sum")).intValue());
                    Assertions.assertEquals(20.0, ((Number) result.get("avg")).doubleValue(), 0.001);
                })
                .verifyComplete();
    }

    // ==================== Large List Performance ====================

    @Test
    void testProcess_LargeList() {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        List<Integer> largeList = new ArrayList<>();
        int expectedSum = 0;
        for (int i = 1; i <= 1000; i++) {
            largeList.add(i);
            expectedSum += i;
        }
        context.setData(largeList);
        final int finalExpectedSum = expectedSum;

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSummarizeProcessorIdentifier, Collections.emptyMap(), context))
                .assertNext(output -> {
                    Map<String, Object> result = (Map<String, Object>) output.getData();
                    Assertions.assertEquals(1000, result.get("count"));
                    Assertions.assertEquals(finalExpectedSum, ((Number) result.get("sum")).intValue());
                    Assertions.assertEquals(500.5, ((Number) result.get("avg")).doubleValue(), 0.001);
                    Assertions.assertEquals(1, ((Number) result.get("min")).intValue());
                    Assertions.assertEquals(1000, ((Number) result.get("max")).intValue());
                })
                .verifyComplete();
    }

    // ==================== Special Numeric Cases ====================

    @ParameterizedTest(name = "Special numeric case: {0}")
    @MethodSource("specialNumericProvider")
    void testProcess_SpecialNumericCases(String description, List<?> inputList, Consumer<Map<String, Object>> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputList);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSummarizeProcessorIdentifier, Collections.emptyMap(), context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    Map<String, Object> result = (Map<String, Object>) output.getData();
                    verifier.accept(result);
                })
                .verifyComplete();
    }

    private static Stream<Arguments> specialNumericProvider() {
        return Stream.of(
                Arguments.of("zeros",
                        List.of(0, 0, 0),
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(0, ((Number) result.get("sum")).intValue());
                            Assertions.assertEquals(0.0, ((Number) result.get("avg")).doubleValue(), 0.001);
                            Assertions.assertEquals(0, ((Number) result.get("min")).intValue());
                            Assertions.assertEquals(0, ((Number) result.get("max")).intValue());
                        }),
                Arguments.of("very small decimals",
                        List.of(0.001, 0.002, 0.003),
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(0.006, ((Number) result.get("sum")).doubleValue(), 0.0001);
                            Assertions.assertEquals(0.002, ((Number) result.get("avg")).doubleValue(), 0.0001);
                        }),
                Arguments.of("negative and positive mix",
                        List.of(-100, 50, -25, 75),
                        (Consumer<Map<String, Object>>) result -> {
                            Assertions.assertEquals(0, ((Number) result.get("sum")).intValue());
                            Assertions.assertEquals(0.0, ((Number) result.get("avg")).doubleValue(), 0.001);
                            Assertions.assertEquals(-100, ((Number) result.get("min")).intValue());
                            Assertions.assertEquals(75, ((Number) result.get("max")).intValue());
                        })
        );
    }

    // ==================== Validation Error Scenarios ====================

    @ParameterizedTest(name = "Validation error: {0}")
    @MethodSource("validationErrorProvider")
    void testProcess_ValidationErrors(String description, Map<String, Object> processorInput) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(List.of(1, 2, 3));

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSummarizeProcessorIdentifier, processorInput, context))
                .expectError()
                .verify();
    }

    private static Stream<Arguments> validationErrorProvider() {
        return Stream.of(
                Arguments.of("empty string field", Map.of("field", "")),
                Arguments.of("whitespace-only field", Map.of("field", "   ")),
                Arguments.of("tab character field", Map.of("field", "\t"))
        );
    }

    // ==================== Verify All Output Fields Present ====================

    @Test
    void testProcess_AllOutputFieldsPresent() {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(List.of(1, 2, 3));

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSummarizeProcessorIdentifier, Collections.emptyMap(), context))
                .assertNext(output -> {
                    Map<String, Object> result = (Map<String, Object>) output.getData();

                    // Verify all expected keys are present
                    Assertions.assertTrue(result.containsKey("count"));
                    Assertions.assertTrue(result.containsKey("nullCount"));
                    Assertions.assertTrue(result.containsKey("first"));
                    Assertions.assertTrue(result.containsKey("last"));
                    Assertions.assertTrue(result.containsKey("type"));
                    Assertions.assertTrue(result.containsKey("sum"));
                    Assertions.assertTrue(result.containsKey("avg"));
                    Assertions.assertTrue(result.containsKey("min"));
                    Assertions.assertTrue(result.containsKey("max"));
                    Assertions.assertTrue(result.containsKey("minLength"));
                    Assertions.assertTrue(result.containsKey("maxLength"));
                    Assertions.assertTrue(result.containsKey("trueCount"));
                    Assertions.assertTrue(result.containsKey("falseCount"));
                })
                .verifyComplete();
    }
}
