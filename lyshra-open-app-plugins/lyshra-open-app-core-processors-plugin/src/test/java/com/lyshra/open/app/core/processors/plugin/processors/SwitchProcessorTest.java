package com.lyshra.open.app.core.processors.plugin.processors;

import com.lyshra.open.app.core.models.LyshraOpenAppContext;
import com.lyshra.open.app.core.processors.plugin.LyshraOpenAppCoreProcessorsPlugin;
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

import java.util.Map;
import java.util.stream.Stream;

@Slf4j
class SwitchProcessorTest extends AbstractPluginTest {

    private static LyshraOpenAppProcessorDefinition switchProcessor;
    private static LyshraOpenAppProcessorIdentifier switchProcessorIdentifier;
    private static ILyshraOpenAppContext context;

    @BeforeAll
    static void setupClass() throws Exception {
        log.info("Setting up test class");
        facade.getPluginLoader().loadPlugin(new LyshraOpenAppCoreProcessorsPlugin());
        switchProcessor = SwitchProcessor.build(LyshraOpenAppProcessorDefinition.builder()).build();
        switchProcessorIdentifier = new LyshraOpenAppProcessorIdentifier(
                LyshraOpenAppCoreProcessorsPlugin.getPluginIdentifier(),
                switchProcessor.getName());
        context = createTestContext("data.json", "variables.json");
    }

    @ParameterizedTest(name = "Test process with expression: {0} should return branch {1}")
    @MethodSource("expressionProvider")
    void testProcess_WithExpression_ShouldReturnExpectedBranch(String expression, String expectedBranch) {
        // Given
        Map<String, Object> processorInput = Map.of("expression", expression);

        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(switchProcessorIdentifier, processorInput, context))

                // Then
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    String branch = output.getBranch();
                    Assertions.assertNotNull(branch);
                    Assertions.assertNull(output.getData());
                    Assertions.assertEquals(expectedBranch, branch);
                })
                .verifyComplete();
    }

    @Test
    void testProcess_WithInvalidExpression_ShouldThrowException() {
        // Given
        Map<String, Object> processorInput = Map.of("expression", "result = invalid syntax !@#$%");

        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(switchProcessorIdentifier, processorInput, context))

                // Then
                .expectError()
                .verify();
    }

    @Test
    void testProcess_WithEmptyExpression_ShouldFailValidation() {
        // Given - empty expression (should fail validation due to @NotBlank)
        Map<String, Object> processorInput = Map.of("expression", "");

        // When/Then - validation should fail or expression evaluation should fail
        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(switchProcessorIdentifier, processorInput, context))

                .expectError()
                .verify();
    }

    @Test
    void testProcess_WithExpressionNotSettingResult_ShouldReturnNullAsBranch() {
        // Given - expression that doesn't set result variable
        Map<String, Object> processorInput = Map.of("expression", "var x = 10");

        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(switchProcessorIdentifier, processorInput, context))

                // Then - result is undefined, which evaluates to null/empty string
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    Assertions.assertNull(output.getBranch());
                    Assertions.assertNull(output.getData());
                })
                .verifyComplete();
    }

    @Test
    void testProcess_WithEmptyContext_ShouldHandleGracefully() {
        // Given
        ILyshraOpenAppContext emptyContext = new LyshraOpenAppContext();
        Map<String, Object> processorInput = Map.of("expression", "result = 'defaultBranch'");

        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(switchProcessorIdentifier, processorInput, emptyContext))

                // Then
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    Assertions.assertEquals("defaultBranch", output.getBranch());
                    Assertions.assertNull(output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> expressionProvider() {
        return Stream.of(
                // String literals
                Arguments.of("result = 'branch1'", "branch1"),
                Arguments.of("result = 'branch2'", "branch2"),
                Arguments.of("result = 'default'", "default"),
                Arguments.of("result = 'case1'", "case1"),
                
                // String literals with special characters
                Arguments.of("result = 'branch-with-dash'", "branch-with-dash"),
                Arguments.of("result = 'branch_with_underscore'", "branch_with_underscore"),
                Arguments.of("result = 'branch.with.dots'", "branch.with.dots"),
                
                // Empty string
                Arguments.of("result = ''", ""),
                
                // Data access - string fields
                Arguments.of("result = $data.hello", "world"),
                Arguments.of("result = $data.nested.field", "value"),
                
                // Data access - numeric to string conversion
                Arguments.of("result = String($data.count)", "10"),
                Arguments.of("result = $data.count.toString()", "10"),
                
                // String concatenation
                Arguments.of("result = 'prefix-' + $data.hello", "prefix-world"),
                Arguments.of("result = $data.hello + '-suffix'", "world-suffix"),
                Arguments.of("result = $data.nested.field + '-' + $data.count", "value-10"),
                
                // Conditional string expressions
                Arguments.of("result = $data.count > 5 ? 'high' : 'low'", "high"),
                Arguments.of("result = $data.count < 5 ? 'low' : 'high'", "high"),
                Arguments.of("result = $data.count == 10 ? 'exact' : 'other'", "exact"),
                Arguments.of("result = $data.count == 20 ? 'exact' : 'other'", "other"),
                
                // Variables access
                Arguments.of("result = String($variables.threshold)", "20"),
                Arguments.of("result = $variables.threshold.toString()", "20"),
                
                // Complex string expressions with variables
                Arguments.of("result = 'threshold-' + String($variables.threshold)", "threshold-20"),
                Arguments.of("result = $data.hello + '-' + String($variables.threshold)", "world-20"),
                
                // Array element to string
                Arguments.of("result = String($data.list[0])", "1"),
                Arguments.of("result = $data.list[0].toString()", "1"),
                Arguments.of("result = String($data.list[1])", "2"),
                Arguments.of("result = String($data.list[2])", "3"),
                
                // Array length as string
                Arguments.of("result = String($data.list.length)", "5"),
                Arguments.of("result = $data.list.length.toString()", "5"),
                
                // Complex conditional with multiple conditions
                Arguments.of("result = $data.count > 5 && $data.count < 15 ? 'medium' : 'other'", "medium"),
                Arguments.of("result = $data.count >= 10 ? 'tenOrMore' : 'lessThanTen'", "tenOrMore"),
                
                // Nested ternary operators
                Arguments.of("result = $data.count > 10 ? 'high' : ($data.count > 5 ? 'medium' : 'low')", "medium"),
                
                // String template-like expressions
                Arguments.of("result = 'count:' + String($data.count) + ',threshold:' + String($variables.threshold)", "count:10,threshold:20"),
                
                // Boolean to string conversion (for switch cases)
                Arguments.of("result = String(true)", "true"),
                Arguments.of("result = String(false)", "false"),
                Arguments.of("result = ($data.count == 10).toString()", "true"),
                Arguments.of("result = ($data.count == 20).toString()", "false"),
                
                // Edge cases - null handling
                Arguments.of("result = $data.nonExistent || 'default'", "default"),
                Arguments.of("result = $data.nonExistent != null ? $data.nonExistent : 'fallback'", "fallback"),
                
                // Number formatting as string
                Arguments.of("result = 'value-' + ($data.count + 5).toString()", "value-15"),
                Arguments.of("result = 'multiplied-' + ($data.count * 2).toString()", "multiplied-20"),
                
                // Complex string building
                Arguments.of("result = $data.hello + '-' + String($data.count) + '-' + $data.nested.field", "world-10-value"),
                Arguments.of("result = 'prefix-' + $data.hello + '-middle-' + String($variables.threshold) + '-suffix'", "prefix-world-middle-20-suffix")
        );
    }
}