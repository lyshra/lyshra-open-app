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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
class ListSortProcessorTest extends AbstractPluginTest {

    private static LyshraOpenAppProcessorDefinition listSortProcessor;
    private static LyshraOpenAppProcessorIdentifier listSortProcessorIdentifier;

    @BeforeAll
    static void setupClass() {
        log.info("Setting up test class");
        facade.getPluginLoader().loadPlugin(new LyshraOpenAppCoreProcessorsPlugin());
        listSortProcessor = ListSortProcessor.build(LyshraOpenAppProcessorDefinition.builder()).build();
        listSortProcessorIdentifier = new LyshraOpenAppProcessorIdentifier(
                LyshraOpenAppCoreProcessorsPlugin.getPluginIdentifier(),
                listSortProcessor.getName());
    }

    // ==================== Primitive List Sorting Tests (Parameterized) ====================

    @ParameterizedTest(name = "Sort primitives: {0}")
    @MethodSource("primitiveSortProvider")
    void testProcess_SortPrimitives(String description, List<?> inputList, String order, List<?> expectedList) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputList);
        Map<String, Object> processorInput = Map.of("order", order);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    Assertions.assertNotNull(output.getData());
                    Assertions.assertInstanceOf(List.class, output.getData());
                    Assertions.assertEquals(expectedList, output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> primitiveSortProvider() {
        return Stream.of(
                // Numbers ascending/descending
                Arguments.of("numbers ascending", List.of(5, 3, 8, 1, 9, 2), "asc", List.of(1, 2, 3, 5, 8, 9)),
                Arguments.of("numbers descending", List.of(5, 3, 8, 1, 9, 2), "desc", List.of(9, 8, 5, 3, 2, 1)),
                // Strings ascending/descending
                Arguments.of("strings ascending", List.of("banana", "apple", "cherry", "date"), "asc", List.of("apple", "banana", "cherry", "date")),
                Arguments.of("strings descending", List.of("banana", "apple", "cherry", "date"), "desc", List.of("date", "cherry", "banana", "apple")),
                // Booleans ascending/descending
                Arguments.of("booleans ascending", List.of(true, false, true, false, true), "asc", List.of(false, false, true, true, true)),
                Arguments.of("booleans descending", List.of(true, false, true, false, true), "desc", List.of(true, true, true, false, false)),
                // Floating point numbers
                Arguments.of("floating point ascending", List.of(3.14, 1.5, 2.71, 0.5), "asc", List.of(0.5, 1.5, 2.71, 3.14)),
                // Negative numbers
                Arguments.of("negative numbers ascending", List.of(-5, 3, -1, 0, 2), "asc", List.of(-5, -1, 0, 2, 3)),
                // Mixed string types (numeric strings)
                Arguments.of("mixed string types", List.of("apple", "123", "banana"), "asc", List.of("123", "apple", "banana")),
                // Already sorted
                Arguments.of("already sorted ascending", List.of(1, 2, 3), "asc", List.of(1, 2, 3)),
                Arguments.of("already sorted descending", List.of(1, 2, 3), "desc", List.of(3, 2, 1)),
                // Duplicates
                Arguments.of("with duplicates", List.of(1, 1, 1), "asc", List.of(1, 1, 1))
        );
    }

    // ==================== Object Sorting by Key Tests (Parameterized) ====================

    @ParameterizedTest(name = "Sort objects by key: {0}")
    @MethodSource("objectSortByKeyProvider")
    void testProcess_SortObjectsByKey(String description, String sortKey, String order, List<String> expectedNameOrder) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(List.of(
                Map.of("name", "Charlie", "age", 30),
                Map.of("name", "Alice", "age", 25),
                Map.of("name", "Bob", "age", 35)
        ));
        Map<String, Object> processorInput = Map.of("sortKey", sortKey, "order", order);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    List<Map<String, Object>> sortedList = (List<Map<String, Object>>) output.getData();
                    Assertions.assertEquals(3, sortedList.size());
                    for (int i = 0; i < expectedNameOrder.size(); i++) {
                        Assertions.assertEquals(expectedNameOrder.get(i), sortedList.get(i).get("name"));
                    }
                })
                .verifyComplete();
    }

    private static Stream<Arguments> objectSortByKeyProvider() {
        return Stream.of(
                Arguments.of("by name ascending", "name", "asc", List.of("Alice", "Bob", "Charlie")),
                Arguments.of("by name descending", "name", "desc", List.of("Charlie", "Bob", "Alice")),
                Arguments.of("by age ascending", "age", "asc", List.of("Alice", "Charlie", "Bob")),
                Arguments.of("by age descending", "age", "desc", List.of("Bob", "Charlie", "Alice"))
        );
    }

    // ==================== Edge Cases: Empty and Single Element Lists ====================

    @ParameterizedTest(name = "Edge case: {0}")
    @MethodSource("edgeCaseListProvider")
    void testProcess_EdgeCaseLists(String description, Supplier<List<?>> dataSupplier, int expectedSize) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(dataSupplier.get());
        Map<String, Object> processorInput = Map.of("order", "asc");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    List<?> sortedList = (List<?>) output.getData();
                    Assertions.assertNotNull(sortedList);
                    Assertions.assertEquals(expectedSize, sortedList.size());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> edgeCaseListProvider() {
        return Stream.of(
                Arguments.of("empty list", (Supplier<List<?>>) ArrayList::new, 0),
                Arguments.of("single element", (Supplier<List<?>>) () -> List.of(42), 1)
        );
    }

    // ==================== Error Scenarios (Parameterized) ====================

    @ParameterizedTest(name = "Error scenario: {0}")
    @MethodSource("errorScenarioProvider")
    void testProcess_ErrorScenarios(String description, Supplier<ILyshraOpenAppContext> contextSupplier, Map<String, Object> processorInput) {
        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSortProcessorIdentifier, processorInput, contextSupplier.get()))
                .expectError()
                .verify();
    }

    private static Stream<Arguments> errorScenarioProvider() {
        return Stream.of(
                Arguments.of("null data context",
                        (Supplier<ILyshraOpenAppContext>) LyshraOpenAppContext::new,
                        Map.of("order", "asc")),
                Arguments.of("invalid order value",
                        (Supplier<ILyshraOpenAppContext>) () -> {
                            ILyshraOpenAppContext ctx = new LyshraOpenAppContext();
                            ctx.setData(List.of(1, 2, 3));
                            return ctx;
                        },
                        Map.of("order", "invalid")),
                Arguments.of("empty string sortKey",
                        (Supplier<ILyshraOpenAppContext>) () -> {
                            ILyshraOpenAppContext ctx = new LyshraOpenAppContext();
                            ctx.setData(List.of(Map.of("name", "Alice"), Map.of("name", "Bob")));
                            return ctx;
                        },
                        Map.of("sortKey", "", "order", "asc")),
                Arguments.of("whitespace-only sortKey",
                        (Supplier<ILyshraOpenAppContext>) () -> {
                            ILyshraOpenAppContext ctx = new LyshraOpenAppContext();
                            ctx.setData(List.of(Map.of("name", "Alice"), Map.of("name", "Bob")));
                            return ctx;
                        },
                        Map.of("sortKey", "   ", "order", "asc")),
                Arguments.of("tab character sortKey",
                        (Supplier<ILyshraOpenAppContext>) () -> {
                            ILyshraOpenAppContext ctx = new LyshraOpenAppContext();
                            ctx.setData(List.of(Map.of("name", "Alice"), Map.of("name", "Bob")));
                            return ctx;
                        },
                        Map.of("sortKey", "\t", "order", "asc"))
        );
    }

    // ==================== Null/Undefined Handling Tests (Parameterized) ====================

    @ParameterizedTest(name = "Null handling: {0}")
    @MethodSource("nullHandlingProvider")
    void testProcess_NullHandling(String description, Supplier<List<?>> dataSupplier, Map<String, Object> processorInput,
                                   Consumer<List<?>> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(dataSupplier.get());

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    List<?> sortedList = (List<?>) output.getData();
                    Assertions.assertNotNull(sortedList);
                    verifier.accept(sortedList);
                })
                .verifyComplete();
    }

    private static Stream<Arguments> nullHandlingProvider() {
        return Stream.of(
                Arguments.of("list with null values",
                        (Supplier<List<?>>) () -> {
                            List<Integer> list = new ArrayList<>();
                            list.add(3);
                            list.add(null);
                            list.add(1);
                            list.add(null);
                            list.add(2);
                            return list;
                        },
                        Map.of("order", "asc"),
                        (Consumer<List<?>>) list -> {
                            Assertions.assertEquals(5, list.size());
                            Assertions.assertEquals(1, list.get(0));
                            Assertions.assertEquals(2, list.get(1));
                            Assertions.assertEquals(3, list.get(2));
                        }),
                Arguments.of("objects with null sortKey values",
                        (Supplier<List<?>>) () -> {
                            List<Map<String, Object>> dataList = new ArrayList<>();
                            Map<String, Object> item1 = new HashMap<>();
                            item1.put("name", "Charlie");
                            Map<String, Object> item2 = new HashMap<>();
                            item2.put("name", null);
                            Map<String, Object> item3 = new HashMap<>();
                            item3.put("name", "Alice");
                            dataList.add(item1);
                            dataList.add(item2);
                            dataList.add(item3);
                            return dataList;
                        },
                        Map.of("sortKey", "name", "order", "asc"),
                        (Consumer<List<?>>) list -> {
                            List<Map<String, Object>> sortedList = (List<Map<String, Object>>) list;
                            Assertions.assertEquals(3, sortedList.size());
                            Assertions.assertEquals("Alice", sortedList.get(0).get("name"));
                            Assertions.assertEquals("Charlie", sortedList.get(1).get("name"));
                            Assertions.assertNull(sortedList.get(2).get("name"));
                        }),
                Arguments.of("objects with missing sortKey",
                        (Supplier<List<?>>) () -> {
                            List<Map<String, Object>> dataList = new ArrayList<>();
                            dataList.add(Map.of("name", "Charlie"));
                            dataList.add(Map.of("other", "field"));
                            dataList.add(Map.of("name", "Alice"));
                            return dataList;
                        },
                        Map.of("sortKey", "name", "order", "asc"),
                        (Consumer<List<?>>) list -> {
                            List<Map<String, Object>> sortedList = (List<Map<String, Object>>) list;
                            Assertions.assertEquals(3, sortedList.size());
                            Assertions.assertEquals("Alice", sortedList.get(0).get("name"));
                            Assertions.assertEquals("Charlie", sortedList.get(1).get("name"));
                            Assertions.assertFalse(sortedList.get(2).containsKey("name"));
                        })
        );
    }

    // ==================== Default Behavior Tests ====================

    @Test
    void testProcess_DefaultOrderIsAscending() {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(List.of(3, 1, 2));
        Map<String, Object> processorInput = Collections.emptyMap();

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    Assertions.assertEquals(List.of(1, 2, 3), output.getData());
                })
                .verifyComplete();
    }

    @Test
    void testProcess_SortKeyWithNoOrder() {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(List.of(
                Map.of("value", 3),
                Map.of("value", 1),
                Map.of("value", 2)
        ));
        Map<String, Object> processorInput = Map.of("sortKey", "value");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    List<Map<String, Object>> sortedList = (List<Map<String, Object>>) output.getData();
                    Assertions.assertEquals(1, sortedList.get(0).get("value"));
                    Assertions.assertEquals(2, sortedList.get(1).get("value"));
                    Assertions.assertEquals(3, sortedList.get(2).get("value"));
                })
                .verifyComplete();
    }

    // ==================== Immutability Test ====================

    @Test
    void testProcess_DoesNotMutateOriginalList() {
        List<Integer> originalData = new ArrayList<>(List.of(5, 3, 8, 1));
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(originalData);
        Map<String, Object> processorInput = Map.of("order", "asc");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    Assertions.assertEquals(List.of(5, 3, 8, 1), originalData);
                    Assertions.assertEquals(List.of(1, 3, 5, 8), output.getData());
                })
                .verifyComplete();
    }

    // ==================== Performance Test ====================

    @Test
    void testProcess_LargeList() {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        List<Integer> largeList = new ArrayList<>();
        for (int i = 100; i > 0; i--) {
            largeList.add(i);
        }
        context.setData(largeList);
        Map<String, Object> processorInput = Map.of("order", "asc");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    List<Integer> sortedList = (List<Integer>) output.getData();
                    Assertions.assertEquals(100, sortedList.size());
                    for (int i = 0; i < 99; i++) {
                        Assertions.assertTrue(sortedList.get(i) <= sortedList.get(i + 1));
                    }
                    Assertions.assertEquals(1, sortedList.get(0));
                    Assertions.assertEquals(100, sortedList.get(99));
                })
                .verifyComplete();
    }

    // ==================== String Case Sensitivity Test ====================

    @Test
    void testProcess_CaseInsensitiveStringSorting() {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(List.of("banana", "Apple", "cherry", "APRICOT"));
        Map<String, Object> processorInput = Map.of("order", "asc");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    List<String> sortedList = (List<String>) output.getData();
                    Assertions.assertEquals(4, sortedList.size());
                    Assertions.assertNotNull(sortedList);
                })
                .verifyComplete();
    }
}
