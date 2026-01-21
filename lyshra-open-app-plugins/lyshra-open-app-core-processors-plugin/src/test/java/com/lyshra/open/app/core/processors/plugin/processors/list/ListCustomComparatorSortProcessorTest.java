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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
class ListCustomComparatorSortProcessorTest extends AbstractPluginTest {

    private static LyshraOpenAppProcessorDefinition listCustomComparatorSortProcessor;
    private static LyshraOpenAppProcessorIdentifier listCustomComparatorSortProcessorIdentifier;

    @BeforeAll
    static void setupClass() {
        log.info("Setting up test class");
        facade.getPluginLoader().loadPlugin(new LyshraOpenAppCoreProcessorsPlugin());
        listCustomComparatorSortProcessor = ListCustomComparatorSortProcessor.build(LyshraOpenAppProcessorDefinition.builder()).build();
        listCustomComparatorSortProcessorIdentifier = new LyshraOpenAppProcessorIdentifier(
                LyshraOpenAppCoreProcessorsPlugin.getPluginIdentifier(),
                listCustomComparatorSortProcessor.getName());
    }

    // ==================== Primitive Sorting with Custom Comparator (Parameterized) ====================

    @ParameterizedTest(name = "Sort primitives: {0}")
    @MethodSource("primitiveSortProvider")
    void testProcess_SortPrimitives(String description, List<?> inputList, String expression, List<?> expectedList) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(inputList);
        Map<String, Object> processorInput = Map.of("expression", expression);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listCustomComparatorSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    Assertions.assertEquals(expectedList, output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> primitiveSortProvider() {
        return Stream.of(
                // Numbers
                Arguments.of("numbers ascending", List.of(5, 3, 8, 1, 9, 2), "$a - $b", List.of(1, 2, 3, 5, 8, 9)),
                Arguments.of("numbers descending", List.of(5, 3, 8, 1, 9, 2), "$b - $a", List.of(9, 8, 5, 3, 2, 1)),
                Arguments.of("negative numbers", List.of(-5, 3, -1, 0, 2), "$a - $b", List.of(-5, -1, 0, 2, 3)),
                Arguments.of("floating point", List.of(3.14, 1.5, 2.71, 0.5), "$a - $b", List.of(0.5, 1.5, 2.71, 3.14)),
                // Strings
                Arguments.of("strings ascending", List.of("banana", "apple", "cherry", "date"), "$a.localeCompare($b)", List.of("apple", "banana", "cherry", "date")),
                Arguments.of("strings descending", List.of("banana", "apple", "cherry", "date"), "$b.localeCompare($a)", List.of("date", "cherry", "banana", "apple")),
                // Duplicates
                Arguments.of("with duplicates", List.of(1, 1, 1), "$a - $b", List.of(1, 1, 1)),
                Arguments.of("already sorted", List.of(1, 2, 3), "$a - $b", List.of(1, 2, 3))
        );
    }

    // ==================== Object Sorting by Single Field (Parameterized) ====================

    @ParameterizedTest(name = "Sort objects by field: {0}")
    @MethodSource("objectSingleFieldSortProvider")
    void testProcess_SortObjectsBySingleField(String description, String expression, List<String> expectedNameOrder) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(List.of(
                Map.of("name", "Charlie", "age", 30),
                Map.of("name", "Alice", "age", 25),
                Map.of("name", "Bob", "age", 35)
        ));
        Map<String, Object> processorInput = Map.of("expression", expression);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listCustomComparatorSortProcessorIdentifier, processorInput, context))
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

    private static Stream<Arguments> objectSingleFieldSortProvider() {
        return Stream.of(
                Arguments.of("by name ascending", "$a.name.localeCompare($b.name)", List.of("Alice", "Bob", "Charlie")),
                Arguments.of("by age ascending", "$a.age - $b.age", List.of("Alice", "Charlie", "Bob")),
                Arguments.of("by age descending", "$b.age - $a.age", List.of("Bob", "Charlie", "Alice"))
        );
    }

    // ==================== Complex Sorting Scenarios (Parameterized) ====================

    @ParameterizedTest(name = "Complex sort: {0}")
    @MethodSource("complexSortProvider")
    void testProcess_ComplexSorting(String description, Supplier<List<?>> dataSupplier, String expression,
                                     Consumer<List<?>> verifier) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(dataSupplier.get());
        Map<String, Object> processorInput = Map.of("expression", expression);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listCustomComparatorSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    verifier.accept((List<?>) output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> complexSortProvider() {
        return Stream.of(
                // Multi-field sorting
                Arguments.of("multi-field: age then name",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("name", "Charlie", "age", 30),
                                Map.of("name", "Alice", "age", 30),
                                Map.of("name", "Bob", "age", 25),
                                Map.of("name", "Diana", "age", 30)
                        ),
                        "$a.age - $b.age || $a.name.localeCompare($b.name)",
                        (Consumer<List<?>>) list -> {
                            List<Map<String, Object>> sorted = (List<Map<String, Object>>) list;
                            Assertions.assertEquals("Bob", sorted.get(0).get("name"));
                            Assertions.assertEquals("Alice", sorted.get(1).get("name"));
                            Assertions.assertEquals("Charlie", sorted.get(2).get("name"));
                            Assertions.assertEquals("Diana", sorted.get(3).get("name"));
                        }),
                // Nested object sorting
                Arguments.of("nested objects by city",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("person", Map.of("name", "Charlie", "address", Map.of("city", "NYC"))),
                                Map.of("person", Map.of("name", "Alice", "address", Map.of("city", "LA"))),
                                Map.of("person", Map.of("name", "Bob", "address", Map.of("city", "Chicago")))
                        ),
                        "$a.person.address.city.localeCompare($b.person.address.city)",
                        (Consumer<List<?>>) list -> {
                            List<Map<String, Object>> sorted = (List<Map<String, Object>>) list;
                            Assertions.assertEquals("Chicago", ((Map<String, Object>) ((Map<String, Object>) sorted.get(0).get("person")).get("address")).get("city"));
                            Assertions.assertEquals("LA", ((Map<String, Object>) ((Map<String, Object>) sorted.get(1).get("person")).get("address")).get("city"));
                            Assertions.assertEquals("NYC", ((Map<String, Object>) ((Map<String, Object>) sorted.get(2).get("person")).get("address")).get("city"));
                        }),
                // Sort by array length
                Arguments.of("by array length",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("name", "Item1", "tags", List.of("a", "b", "c")),
                                Map.of("name", "Item2", "tags", List.of("x")),
                                Map.of("name", "Item3", "tags", List.of("p", "q"))
                        ),
                        "$a.tags.length - $b.tags.length",
                        (Consumer<List<?>>) list -> {
                            List<Map<String, Object>> sorted = (List<Map<String, Object>>) list;
                            Assertions.assertEquals("Item2", sorted.get(0).get("name"));
                            Assertions.assertEquals("Item3", sorted.get(1).get("name"));
                            Assertions.assertEquals("Item1", sorted.get(2).get("name"));
                        }),
                // Sort by nested array element
                Arguments.of("by first array element",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("name", "Item1", "values", List.of(30, 10)),
                                Map.of("name", "Item2", "values", List.of(10, 20)),
                                Map.of("name", "Item3", "values", List.of(50, 5))
                        ),
                        "$a.values[0] - $b.values[0]",
                        (Consumer<List<?>>) list -> {
                            List<Map<String, Object>> sorted = (List<Map<String, Object>>) list;
                            Assertions.assertEquals("Item2", sorted.get(0).get("name"));
                            Assertions.assertEquals("Item1", sorted.get(1).get("name"));
                            Assertions.assertEquals("Item3", sorted.get(2).get("name"));
                        }),
                // Sort by computed value (absolute)
                Arguments.of("by absolute value",
                        (Supplier<List<?>>) () -> List.of(-5, 3, -8, 1, -2),
                        "Math.abs($a) - Math.abs($b)",
                        (Consumer<List<?>>) list -> {
                            Assertions.assertEquals(1, list.get(0));
                            Assertions.assertEquals(-2, list.get(1));
                            Assertions.assertEquals(3, list.get(2));
                            Assertions.assertEquals(-5, list.get(3));
                            Assertions.assertEquals(-8, list.get(4));
                        }),
                // Sort by string length
                Arguments.of("by string length",
                        (Supplier<List<?>>) () -> List.of("apple", "hi", "banana", "cat"),
                        "$a.length - $b.length",
                        (Consumer<List<?>>) list -> {
                            Assertions.assertEquals("hi", list.get(0));
                            Assertions.assertEquals("cat", list.get(1));
                            Assertions.assertEquals("apple", list.get(2));
                            Assertions.assertEquals("banana", list.get(3));
                        }),
                // Case insensitive string sort
                Arguments.of("case insensitive strings",
                        (Supplier<List<?>>) () -> List.of("Banana", "apple", "CHERRY", "date"),
                        "$a.toLowerCase().localeCompare($b.toLowerCase())",
                        (Consumer<List<?>>) list -> {
                            Assertions.assertEquals("apple", list.get(0));
                            Assertions.assertEquals("Banana", list.get(1));
                            Assertions.assertEquals("CHERRY", list.get(2));
                            Assertions.assertEquals("date", list.get(3));
                        }),
                // Boolean field sorting
                Arguments.of("by boolean field",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("name", "Item1", "active", true),
                                Map.of("name", "Item2", "active", false),
                                Map.of("name", "Item3", "active", true),
                                Map.of("name", "Item4", "active", false)
                        ),
                        "($a.active === $b.active) ? 0 : ($a.active ? 1 : -1)",
                        (Consumer<List<?>>) list -> {
                            List<Map<String, Object>> sorted = (List<Map<String, Object>>) list;
                            Assertions.assertEquals(false, sorted.get(0).get("active"));
                            Assertions.assertEquals(false, sorted.get(1).get("active"));
                            Assertions.assertEquals(true, sorted.get(2).get("active"));
                            Assertions.assertEquals(true, sorted.get(3).get("active"));
                        }),
                // Date string sorting
                Arguments.of("by date string",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("name", "Event1", "date", "2024-03-15"),
                                Map.of("name", "Event2", "date", "2024-01-01"),
                                Map.of("name", "Event3", "date", "2024-06-20")
                        ),
                        "new Date($a.date) - new Date($b.date)",
                        (Consumer<List<?>>) list -> {
                            List<Map<String, Object>> sorted = (List<Map<String, Object>>) list;
                            Assertions.assertEquals("2024-01-01", sorted.get(0).get("date"));
                            Assertions.assertEquals("2024-03-15", sorted.get(1).get("date"));
                            Assertions.assertEquals("2024-06-20", sorted.get(2).get("date"));
                        }),
                // Complex multi-field with descending
                Arguments.of("multi-field with descending",
                        (Supplier<List<?>>) () -> List.of(
                                Map.of("name", "Task1", "status", "open", "priority", 2),
                                Map.of("name", "Task2", "status", "closed", "priority", 1),
                                Map.of("name", "Task3", "status", "open", "priority", 1),
                                Map.of("name", "Task4", "status", "open", "priority", 1)
                        ),
                        "$b.status.localeCompare($a.status) || ($a.priority - $b.priority) || $a.name.localeCompare($b.name)",
                        (Consumer<List<?>>) list -> {
                            List<Map<String, Object>> sorted = (List<Map<String, Object>>) list;
                            Assertions.assertEquals("Task3", sorted.get(0).get("name"));
                            Assertions.assertEquals("Task4", sorted.get(1).get("name"));
                            Assertions.assertEquals("Task1", sorted.get(2).get("name"));
                            Assertions.assertEquals("Task2", sorted.get(3).get("name"));
                        }),
                // Even/odd grouping with ternary
                Arguments.of("even numbers first then odd",
                        (Supplier<List<?>>) () -> List.of(5, 3, 8, 1, 9, 2),
                        "($a % 2) - ($b % 2) || $a - $b",
                        (Consumer<List<?>>) list -> {
                            Assertions.assertEquals(2, list.get(0));
                            Assertions.assertEquals(8, list.get(1));
                            Assertions.assertEquals(1, list.get(2));
                            Assertions.assertEquals(3, list.get(3));
                            Assertions.assertEquals(5, list.get(4));
                            Assertions.assertEquals(9, list.get(5));
                        })
        );
    }

    // ==================== Edge Cases (Parameterized) ====================

    @ParameterizedTest(name = "Edge case: {0}")
    @MethodSource("edgeCaseProvider")
    void testProcess_EdgeCases(String description, Supplier<List<?>> dataSupplier, int expectedSize) {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(dataSupplier.get());
        Map<String, Object> processorInput = Map.of("expression", "$a - $b");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listCustomComparatorSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("{} - Output: {}", description, output);
                    List<?> sortedList = (List<?>) output.getData();
                    Assertions.assertEquals(expectedSize, sortedList.size());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> edgeCaseProvider() {
        return Stream.of(
                Arguments.of("empty list", (Supplier<List<?>>) ArrayList::new, 0),
                Arguments.of("single element", (Supplier<List<?>>) () -> List.of(42), 1)
        );
    }

    // ==================== Error Scenarios (Parameterized) ====================

    @ParameterizedTest(name = "Error scenario: {0}")
    @MethodSource("errorScenarioProvider")
    void testProcess_ErrorScenarios(String description, Supplier<ILyshraOpenAppContext> contextSupplier, String expression) {
        Map<String, Object> processorInput = Map.of("expression", expression);

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listCustomComparatorSortProcessorIdentifier, processorInput, contextSupplier.get()))
                .expectError()
                .verify();
    }

    private static Stream<Arguments> errorScenarioProvider() {
        return Stream.of(
                Arguments.of("null data context",
                        (Supplier<ILyshraOpenAppContext>) LyshraOpenAppContext::new,
                        "$a - $b"),
                Arguments.of("empty expression",
                        (Supplier<ILyshraOpenAppContext>) () -> {
                            ILyshraOpenAppContext ctx = new LyshraOpenAppContext();
                            ctx.setData(List.of(1, 2, 3));
                            return ctx;
                        },
                        ""),
                Arguments.of("expression too short",
                        (Supplier<ILyshraOpenAppContext>) () -> {
                            ILyshraOpenAppContext ctx = new LyshraOpenAppContext();
                            ctx.setData(List.of(1, 2, 3));
                            return ctx;
                        },
                        "ab"),
                Arguments.of("invalid syntax",
                        (Supplier<ILyshraOpenAppContext>) () -> {
                            ILyshraOpenAppContext ctx = new LyshraOpenAppContext();
                            ctx.setData(List.of(1, 2, 3));
                            return ctx;
                        },
                        "$a +++ invalid syntax")
        );
    }

    // ==================== Immutability Test ====================

    @Test
    void testProcess_DoesNotMutateOriginalList() {
        List<Integer> originalData = new ArrayList<>(List.of(5, 3, 8, 1));
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(originalData);
        Map<String, Object> processorInput = Map.of("expression", "$a - $b");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listCustomComparatorSortProcessorIdentifier, processorInput, context))
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
        Map<String, Object> processorInput = Map.of("expression", "$a - $b");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listCustomComparatorSortProcessorIdentifier, processorInput, context))
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

    // ==================== Stable Sort Test ====================

    @Test
    void testProcess_StableSort() {
        ILyshraOpenAppContext context = new LyshraOpenAppContext();
        context.setData(List.of(
                Map.of("name", "First", "value", 1),
                Map.of("name", "Second", "value", 1),
                Map.of("name", "Third", "value", 1)
        ));
        Map<String, Object> processorInput = Map.of("expression", "$a.value - $b.value");

        StepVerifier
                .create(facade.getProcessorExecutor().execute(listCustomComparatorSortProcessorIdentifier, processorInput, context))
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    List<Map<String, Object>> sortedList = (List<Map<String, Object>>) output.getData();
                    Assertions.assertEquals(3, sortedList.size());
                    Assertions.assertEquals("First", sortedList.get(0).get("name"));
                    Assertions.assertEquals("Second", sortedList.get(1).get("name"));
                    Assertions.assertEquals("Third", sortedList.get(2).get("name"));
                })
                .verifyComplete();
    }
}
