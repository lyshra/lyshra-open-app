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
import java.util.function.Supplier;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Slf4j
class ListFilterProcessorTest extends AbstractPluginTest {

    private static LyshraOpenAppProcessorDefinition listFilterProcessor;
    private static LyshraOpenAppProcessorIdentifier listFilterProcessorIdentifier;
    private static ILyshraOpenAppContext context;

    @BeforeAll
    static void setupClass() throws Exception {
        log.info("Setting up test class");
        facade.getPluginLoader().loadPlugin(new LyshraOpenAppCoreProcessorsPlugin());
        listFilterProcessor = ListFilterProcessor.build(LyshraOpenAppProcessorDefinition.builder()).build();
        listFilterProcessorIdentifier = new LyshraOpenAppProcessorIdentifier(
                LyshraOpenAppCoreProcessorsPlugin.getPluginIdentifier(),
                listFilterProcessor.getName());
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

    @ParameterizedTest(name = "Filter with expression: {0}")
    @MethodSource("filterExpressionProvider")
    void testProcess_FilterExpressions(String expression, Predicate<Integer> expectedFilter) {
        // Given
        Map<String, Object> processorInput = Map.of("expression", expression);
        
        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(listFilterProcessorIdentifier, processorInput, context))
                
                // Then - verify filtered results match expected predicate
                .assertNext(output -> {
                    log.info("Expression: {}, Output: {}", expression, output);
                    Object data = output.getData();
                    Assertions.assertNotNull(data);
                    Assertions.assertInstanceOf(List.class, data);
                    List<Integer> filteredList = (List<Integer>) data;
                    
                    // Verify all items in filtered list match the predicate
                    Assertions.assertTrue(filteredList.stream().allMatch(expectedFilter));
                    // Verify no items outside the filtered list match (from original context data)
                    List<Integer> originalData = (List<Integer>) context.getData();
                    List<Integer> shouldBeExcluded = originalData.stream()
                            .filter(e -> !expectedFilter.test(e))
                            .toList();
                    Assertions.assertTrue(shouldBeExcluded.stream().noneMatch(filteredList::contains));
                })
                .verifyComplete();
    }

    private static Stream<Arguments> filterExpressionProvider() {
        return Stream.of(
                Arguments.of("$item > 2", (Predicate<Integer>) e -> e > 2),
                Arguments.of("$item >= 2", (Predicate<Integer>) e -> e >= 2),
                Arguments.of("$item == 2", (Predicate<Integer>) e -> e == 2),
                Arguments.of("$item < 3", (Predicate<Integer>) e -> e < 3),
                Arguments.of("$item > 0", (Predicate<Integer>) e -> e > 0),
                Arguments.of("$item > 100", (Predicate<Integer>) e -> e > 100),
                Arguments.of("$item % 2 == 0", (Predicate<Integer>) e -> e % 2 == 0),
                Arguments.of("$item < 50", (Predicate<Integer>) e -> e < 50),
                Arguments.of("$item > 1 && $item < 3", (Predicate<Integer>) e -> e > 1 && e < 3),
                Arguments.of("$item == 1 || $item == 3", (Predicate<Integer>) e -> e == 1 || e == 3)
        );
    }

    @Test
    void testProcess_FilterObjects_ByKey() {
        // Given - filter list of objects where item.key == 'value1'
        ILyshraOpenAppContext contextWithObjects = new LyshraOpenAppContext();
        contextWithObjects.setData(List.of(
                Map.of("key", "value1"),
                Map.of("key", "value2"),
                Map.of("key", "value3")
        ));
        Map<String, Object> processorInput = Map.of("expression", "$item.key == 'value1'");
        
        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(listFilterProcessorIdentifier, processorInput, contextWithObjects))
                
                // Then - should return one item with key 'value1'
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    Object data = output.getData();
                    Assertions.assertNotNull(data);
                    Assertions.assertInstanceOf(List.class, data);
                    List<?> filteredList = (List<?>) data;
                    Assertions.assertEquals(1, filteredList.size());
                    Map<?, ?> item = (Map<?, ?>) filteredList.get(0);
                    Assertions.assertEquals("value1", item.get("key"));
                })
                .verifyComplete();
    }

    @Test
    void testProcess_WithEmptyList_ShouldReturnEmptyList() {
        // Given - context data has empty list
        ILyshraOpenAppContext contextWithEmptyList = new LyshraOpenAppContext();
        contextWithEmptyList.setData(new ArrayList<>());
        Map<String, Object> processorInput = Map.of("expression", "$item > 3");
        
        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(listFilterProcessorIdentifier, processorInput, contextWithEmptyList))
                
                // Then - should return empty list
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    Object data = output.getData();
                    Assertions.assertNotNull(data);
                    Assertions.assertInstanceOf(List.class, data);
                    List<Integer> filteredList = (List<Integer>) data;
                    Assertions.assertEquals(0, filteredList.size());
                })
                .verifyComplete();
    }

    @ParameterizedTest(name = "Should throw error: {0}")
    @MethodSource("errorScenarioProvider")
    void testProcess_ErrorScenarios(String scenarioDescription, Supplier<ILyshraOpenAppContext> contextSupplier, String expression) {
        // Given
        ILyshraOpenAppContext testContext = contextSupplier.get();
        Map<String, Object> processorInput = Map.of("expression", expression);
        
        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(listFilterProcessorIdentifier, processorInput, testContext))
                
                // Then - should throw error
                .expectError()
                .verify();
    }

    private static Stream<Arguments> errorScenarioProvider() {
        return Stream.of(
                Arguments.of(
                        "Context data is a map, not a list",
                        (Supplier<ILyshraOpenAppContext>) () -> {
                            ILyshraOpenAppContext ctx = new LyshraOpenAppContext();
                            ctx.setData(Map.of("field", "value"));
                            return ctx;
                        },
                        "$item > 3"
                ),
                Arguments.of(
                        "Invalid syntax expression",
                        (Supplier<ILyshraOpenAppContext>) () -> context,
                        "invalid syntax !@#$%"
                ),
                Arguments.of(
                        "Empty expression",
                        (Supplier<ILyshraOpenAppContext>) () -> context,
                        ""
                ),
                Arguments.of(
                        "Null/empty context (no data set)",
                        (Supplier<ILyshraOpenAppContext>) LyshraOpenAppContext::new,
                        "$item > 5"
                )
        );
    }
}
