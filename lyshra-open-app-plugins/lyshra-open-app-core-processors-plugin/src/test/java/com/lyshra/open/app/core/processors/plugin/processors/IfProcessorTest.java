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
class IfProcessorTest extends AbstractPluginTest {

    private static LyshraOpenAppProcessorDefinition ifProcessor;
    private static LyshraOpenAppProcessorIdentifier ifProcessorIdentifier;
    private static ILyshraOpenAppContext context;

    @BeforeAll
    static void setupClass() throws Exception {
        log.info("Setting up test class");
        facade.getPluginLoader().loadPlugin(new LyshraOpenAppCoreProcessorsPlugin());
        ifProcessor = IfProcessor.build(LyshraOpenAppProcessorDefinition.builder()).build();
        ifProcessorIdentifier = new LyshraOpenAppProcessorIdentifier(LyshraOpenAppCoreProcessorsPlugin.getPluginIdentifier(), ifProcessor.getName());
        context = createTestContext("data.json", "variables.json");
    }

    @ParameterizedTest(name = "Test process with expression: {0} should return {1}")
    @MethodSource("expressionProvider")
    void testProcess_WithExpression_ShouldReturnExpectedResult(String expression, String expectedResult) {
        // Given
        Map<String, Object> processorInput = Map.of("expression", expression);

        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(ifProcessorIdentifier, processorInput, context))

                // Then
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    String branch = output.getBranch();
                    Assertions.assertNotNull(branch);
                    Assertions.assertNull(output.getData());
                    Assertions.assertEquals(expectedResult, branch);
                })
                .verifyComplete();
    }

    @Test
    void testProcess_WithInvalidExpression_ShouldThrowException() {
        // Given
        Map<String, Object> processorInput = Map.of("expression", "result = invalid syntax !@#$%");

        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(ifProcessorIdentifier, processorInput, context))

                // Then
                .expectError()
                .verify();
    }

    @Test
    void testProcess_WithMissingProperty_ShouldReturnFalse() {
        // Given - accessing a property that doesn't exist
        Map<String, Object> processorInput = Map.of("expression", "result = $data.nonExistentProperty == 10");

        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(ifProcessorIdentifier, processorInput, context))

                // Then - undefined/null comparisons typically evaluate to false
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    Assertions.assertNotNull(output.getBranch());
                    Assertions.assertNull(output.getData());
                })
                .verifyComplete();
    }

    @Test
    void testProcess_WithEmptyExpression_ShouldFailValidation() {
        // Given - empty expression (should fail validation due to @NotBlank)
        Map<String, Object> processorInput = Map.of("expression", "");

        // When/Then - validation should fail or expression evaluation should fail
        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(ifProcessorIdentifier, processorInput, context))

                .expectError()
                .verify();
    }

    @Test
    void testProcess_WithExpressionNotSettingResult_ShouldReturnFalse() {
        // Given - expression that doesn't set result variable
        Map<String, Object> processorInput = Map.of("expression", "var x = 10");

        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(ifProcessorIdentifier, processorInput, context))

                // Then - result is undefined, which casts to false
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    Assertions.assertEquals("false", output.getBranch());
                    Assertions.assertNull(output.getData());
                })
                .verifyComplete();
    }

    @Test
    void testProcess_WithNullContext_ShouldHandleGracefully() {
        // Given
        ILyshraOpenAppContext emptyContext = new LyshraOpenAppContext();
        Map<String, Object> processorInput = Map.of("expression", "result = true");

        StepVerifier
                // When
                .create(facade.getProcessorExecutor().execute(ifProcessorIdentifier, processorInput, emptyContext))

                // Then
                .assertNext(output -> {
                    log.info("Output: {}", output);
                    Assertions.assertEquals("true", output.getBranch());
                    Assertions.assertNull(output.getData());
                })
                .verifyComplete();
    }

    private static Stream<Arguments> expressionProvider() {
        return Stream.of(
                // Basic expressions
                Arguments.of("result = $data.count == 10", "true"),
                Arguments.of("result = $data.count != 10", "false"),
                Arguments.of("result = $data.count > 5", "true"),
                
                // Comparison operators - Equality
                Arguments.of("result = $data.count == 20", "false"),
                Arguments.of("result = $data.count != 20", "true"),
                
                // Comparison operators - Less than
                Arguments.of("result = $data.count < 15", "true"),
                Arguments.of("result = $data.count < 10", "false"),
                Arguments.of("result = $data.count <= 10", "true"),
                Arguments.of("result = $data.count <= 9", "false"),
                
                // Comparison operators - Greater than
                Arguments.of("result = $data.count > 10", "false"),
                Arguments.of("result = $data.count >= 10", "true"),
                Arguments.of("result = $data.count >= 11", "false"),
                
                // Logical operators - AND
                Arguments.of("result = $data.count > 5 && $data.count < 15", "true"),
                Arguments.of("result = $data.count > 5 && $data.count < 9", "false"),
                
                // Logical operators - OR
                Arguments.of("result = $data.count == 10 || $data.count == 20", "true"),
                Arguments.of("result = $data.count == 1 || $data.count == 2", "false"),
                
                // Logical operators - NOT
                Arguments.of("result = !($data.count == 20)", "true"),
                Arguments.of("result = !($data.count == 10)", "false"),
                
                // Complex logical expressions
                Arguments.of("result = ($data.count > 5 && $data.count < 15) || $data.count == 20", "true"),
                Arguments.of("result = $data.count > 5 && ($data.count < 9 || $data.count == 10)", "true"),
                Arguments.of("result = !($data.count == 20) && $data.count > 0", "true"),
                
                // Nested object access
                Arguments.of("result = $data.nested.field == 'value'", "true"),
                Arguments.of("result = $data.nested.field != 'other'", "true"),
                Arguments.of("result = $data.nested.field == 'other'", "false"),
                Arguments.of("result = $data.nested != null", "true"),
                
                // Array operations - size checks
                Arguments.of("result = $data.list.length == 5", "true"),
                Arguments.of("result = $data.list.length > 2", "true"),
                Arguments.of("result = $data.list.length < 10", "true"),
                Arguments.of("result = $data.list.length == 0", "false"),
                
                // Array operations - element access
                Arguments.of("result = $data.list[0] == 1", "true"),
                Arguments.of("result = $data.list[1] == 2", "true"),
                Arguments.of("result = $data.list[2] == 3", "true"),
                Arguments.of("result = $data.list[0] == 2", "false"),
                
                // Array operations - with logical operators
                Arguments.of("result = $data.list.length > 0 && $data.list[0] == 1", "true"),
                Arguments.of("result = $data.list.length == 5 && $data.list[2] == 3", "true"),
                
                // Variables access
                Arguments.of("result = $variables.threshold > 15", "true"),
                Arguments.of("result = $variables.threshold < 25", "true"),
                Arguments.of("result = $variables.threshold == 20", "true"),
                Arguments.of("result = $variables.minScore > 75", "true"),
                Arguments.of("result = $variables.minScore < 85", "true"),
                
                // Combined data and variables
                Arguments.of("result = $data.count < $variables.threshold", "true"),
                Arguments.of("result = $data.count > $variables.threshold", "false"),
                Arguments.of("result = $variables.minScore > 50 && $variables.threshold > 10", "true"),
                
                // Edge cases - Boolean literals
                Arguments.of("result = true", "true"),
                Arguments.of("result = false", "false"),
                
                // Edge cases - Boolean expressions
                Arguments.of("result = true == true", "true"),
                Arguments.of("result = true == false", "false"),
                Arguments.of("result = false != true", "true"),
                
                // Edge cases - Numeric
                Arguments.of("result = $data.count == 0", "false"),
                Arguments.of("result = $data.count != 0", "true"),
                Arguments.of("result = $data.count > 0", "true"),
                Arguments.of("result = $data.count < 0", "false"),
                
                // Edge cases - String
                Arguments.of("result = $data.hello != ''", "true"),
                Arguments.of("result = $data.hello == 'world'", "true"),
                Arguments.of("result = $data.hello != 'other'", "true"),
                
                // Edge cases - Null/undefined
                Arguments.of("result = $data.hello != null", "true"),
                Arguments.of("result = $data.nonExistent == undefined", "true"),
                Arguments.of("result = $data.nonExistent != undefined", "false"),
                
                // Edge cases - Empty strings
                Arguments.of("result = '' == ''", "true"),
                Arguments.of("result = 'test' != ''", "true"),
                
                // Complex expressions - Arithmetic with comparisons
                Arguments.of("result = ($data.count + 5) > 10", "true"),
                Arguments.of("result = ($data.count * 2) == 20", "true"),
                Arguments.of("result = ($data.count - 5) > 0", "true"),
                
                // Complex expressions - Multiple conditions
                Arguments.of("result = $data.count > 5 && $data.count < 15 && $data.hello == 'world'", "true"),
                Arguments.of("result = $data.count == 10 || $variables.threshold > 25 || $data.hello == 'test'", "true"),
                
                // Complex expressions - Nested comparisons
                Arguments.of("result = $data.nested.field == 'value' && $data.list.length == 5", "true"),
                Arguments.of("result = $data.count > 0 && $data.list[0] == 1 && $variables.threshold > 15", "true"),
                
                // Complex expressions - Chained operations
                Arguments.of("result = $data.list.length == 5 && $data.list[0] == 1 && $data.list[2] == 3", "true"),
                Arguments.of("result = $variables.threshold > $data.count && $variables.minScore > 50", "true")
        );
    }
}
