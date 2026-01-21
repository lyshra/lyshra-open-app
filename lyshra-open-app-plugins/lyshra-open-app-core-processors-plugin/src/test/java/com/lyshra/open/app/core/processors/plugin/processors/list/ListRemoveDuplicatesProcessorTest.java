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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
class ListRemoveDuplicatesProcessorTest extends AbstractPluginTest {

    private static LyshraOpenAppProcessorDefinition listRemoveDuplicatesProcessor;
    private static LyshraOpenAppProcessorIdentifier listRemoveDuplicatesProcessorIdentifier;
    private static ILyshraOpenAppContext context;

    @BeforeAll
    static void setupClass() throws Exception {
        log.info("Setting up test class");
        facade.getPluginLoader().loadPlugin(new LyshraOpenAppCoreProcessorsPlugin());
        listRemoveDuplicatesProcessor = ListRemoveDuplicatesProcessor.build(LyshraOpenAppProcessorDefinition.builder()).build();
        listRemoveDuplicatesProcessorIdentifier = new LyshraOpenAppProcessorIdentifier(
                LyshraOpenAppCoreProcessorsPlugin.getPluginIdentifier(),
                listRemoveDuplicatesProcessor.getName());
        context = createTestContext();
    }

    private static ILyshraOpenAppContext createTestContext() {
        ILyshraOpenAppContext testContext = new LyshraOpenAppContext();
        List<Integer> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            data.add(i);
        }
        testContext.setData(data);
        return testContext;
    }

    // TODO fix this
//    @ParameterizedTest(name = "Remove duplicates from list: {0}")
//    @MethodSource("duplicateListProvider")
    void testProcess_RemoveDuplicates(String description, Supplier<List<?>> listSupplier, Supplier<Set<Object>> expectedUniqueSet) {
        // Given
        ILyshraOpenAppContext testContext = new LyshraOpenAppContext();
        List<?> testData = listSupplier.get();
        testContext.setData(testData);
        Map<String, Object> processorInput = Map.of();
        
        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(listRemoveDuplicatesProcessorIdentifier, processorInput, testContext))
                
                // Then - verify no duplicates and all expected items are present
                .assertNext(output -> {
                    log.info("Description: {}, Output: {}", description, output);
                    Object data = output.getData();
                    Assertions.assertNotNull(data);
                    Assertions.assertInstanceOf(List.class, data);
                    List<?> resultList = (List<?>) data;
                    
                    // Verify expected count
                    Set<Object> expectedUnique = expectedUniqueSet.get();

                    Assertions.assertEquals(expectedUnique.size(), resultList.size(), "Result size should match expected unique count");
                    Assertions.assertTrue(expectedUnique.containsAll(resultList), "All expected items should be present");
                    // Verify no duplicates (size equals number of unique items)
                    Assertions.assertEquals(resultList.size(), new HashSet<>(resultList).size(), "List should not contain duplicates");

                })
                .verifyComplete();
    }

    private static Stream<Arguments> duplicateListProvider() {
        return Stream.of(
                Arguments.of(
                        "Simple integers with duplicates",
                        (Supplier<List<?>>) () -> List.of(1, 2, 2, 3, 3, 3, 4),
                        (Supplier<Set<Object>>) () -> Set.of(1, 2, 3, 4)
                ),
                Arguments.of(
                        "All same numbers",
                        (Supplier<List<?>>) () -> List.of(5, 5, 5, 5),
                        (Supplier<Set<Object>>) () -> Set.of(5)
                ),
                Arguments.of(
                        "Mixed order duplicates",
                        (Supplier<List<?>>) () -> List.of(1, 3, 2, 1, 3, 2),
                        (Supplier<Set<Object>>) () -> Set.of(1, 2, 3)
                ),
                Arguments.of(
                        "No duplicates (already unique)",
                        (Supplier<List<?>>) () -> List.of(1, 2, 3, 4, 5),
                        (Supplier<Set<Object>>) () -> Set.of(1, 2, 3, 4, 5)
                ),
                Arguments.of(
                        "Strings with duplicates",
                        (Supplier<List<?>>) () -> List.of("a", "b", "b", "c", "a"),
                        (Supplier<Set<Object>>) () -> Set.of("a", "b", "c")
                ),
                Arguments.of(
                        "Objects with same reference (should deduplicate)",
                        (Supplier<List<?>>) () -> {
                            // Create objects and reuse same reference for duplicate
                            Map<String, String> obj1 = Map.of("key", "value1");
                            Map<String, String> obj2 = Map.of("key", "value2");
                            Map<String, String> obj3 = Map.of("key", "value3");
                            // Reuse same reference - should be deduped
                            return List.of(obj1, obj2, obj1, obj3);
                        },
                        (Supplier<Set<Object>>) () -> Set.of(1, 2, 3) // Expected: 3 unique
                ),
                Arguments.of(
                        "Mixed types (numbers and strings)",
                        (Supplier<List<?>>) () -> List.of(1, "1", 2, "2", 1, "1"),
                        (Supplier<Set<Object>>) () -> Set.of(1, "1", 2, "2")
                )
        );
    }

    @Test
    void testProcess_WithEmptyList_ShouldReturnEmptyList() {
        // Given
        ILyshraOpenAppContext contextWithEmptyList = new LyshraOpenAppContext();
        contextWithEmptyList.setData(new ArrayList<>());
        Map<String, Object> processorInput = Map.of();
        
        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(listRemoveDuplicatesProcessorIdentifier, processorInput, contextWithEmptyList))
                
                // Then
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    Object data = output.getData();
                    Assertions.assertNotNull(data);
                    Assertions.assertInstanceOf(List.class, data);
                    List<?> resultList = (List<?>) data;
                    Assertions.assertEquals(0, resultList.size());
                })
                .verifyComplete();
    }

    @ParameterizedTest(name = "Non-list data returns empty list: {0}")
    @MethodSource("nonListDataProvider")
    void testProcess_WithNonListData_ShouldReturnEmptyList(String scenarioDescription, Supplier<ILyshraOpenAppContext> contextSupplier) {
        // Given - processor uses $data || [] so non-list data becomes empty array
        ILyshraOpenAppContext testContext = contextSupplier.get();
        Map<String, Object> processorInput = Map.of();
        
        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(listRemoveDuplicatesProcessorIdentifier, processorInput, testContext))
                
                // Then - should return empty list (processor handles gracefully)
                .assertNext(output -> {
                    log.info("Description: {}, Output: {}", scenarioDescription, output);
                    Object data = output.getData();
                    Assertions.assertNotNull(data);
                    Assertions.assertInstanceOf(List.class, data);
                    List<?> resultList = (List<?>) data;
                    Assertions.assertEquals(0, resultList.size());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> nonListDataProvider() {
        return Stream.of(
                Arguments.of(
                        "Context data is a map, not a list",
                        (Supplier<ILyshraOpenAppContext>) () -> {
                            ILyshraOpenAppContext ctx = new LyshraOpenAppContext();
                            ctx.setData(Map.of("field", "value"));
                            return ctx;
                        }
                ),
                Arguments.of(
                        "Null/empty context (no data set)",
                        (Supplier<ILyshraOpenAppContext>) LyshraOpenAppContext::new
                )
        );
    }
}
