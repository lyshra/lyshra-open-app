package com.lyshra.open.app.core.engine.expression.evaluators.graalvm;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.models.LyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpressionEvaluator;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.commons.LyshraOpenAppExpression;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

@Slf4j
class GraalVmJsEvaluatorTest {

    private ILyshraOpenAppFacade facade;
    private ILyshraOpenAppContext context;

    @BeforeEach
    void setUp() {
        facade = LyshraOpenAppFacade.getInstance();
        context = new LyshraOpenAppContext();
    }

    @Test
    void testGetEvaluatorType_ShouldReturnGraalVmJs() {
        // Given
        GraalVmJsEvaluator evaluator = new GraalVmJsEvaluator();
        
        // When
        LyshraOpenAppExpressionType type = evaluator.getEvaluatorType();
        
        // Then
        Assertions.assertEquals(LyshraOpenAppExpressionType.GRAAALVM_JS, type);
    }

    @ParameterizedTest(name = "Test evaluate with expression: {0} should return {1}")
    @MethodSource("basicExpressionProvider")
    void testEvaluate_WithBasicExpressions_ShouldReturnExpectedResult(String expression, Object expectedResult) {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(LyshraOpenAppExpressionType.GRAAALVM_JS, expression);

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertEquals(expectedResult, result);
    }

    @ParameterizedTest(name = "Test evaluate with $data expression: {0} should return {1}")
    @MethodSource("dataExpressionProvider")
    void testEvaluate_WithDataExpressions_ShouldReturnExpectedResult(String expression, Object expectedResult) {
        // Given
        String dataJson = """
                {
                    "count": 10,
                    "name": "test",
                    "active": true,
                    "price": 99.99,
                    "items": [1, 2, 3],
                    "nested": {
                        "field": "value",
                        "number": 42
                    }
                }
                """;
        setDataFromJson(dataJson);

        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(LyshraOpenAppExpressionType.GRAAALVM_JS, expression);

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertEquals(expectedResult, result);
    }

    @ParameterizedTest(name = "Test evaluate with $variables expression: {0} should return {1}")
    @MethodSource("variablesExpressionProvider")
    void testEvaluate_WithVariablesExpressions_ShouldReturnExpectedResult(String expression, Object expectedResult) {
        // Given
        String variablesJson = """
                {
                    "threshold": 20,
                    "minScore": 80,
                    "status": "active",
                    "enabled": true,
                    "count": 5
                }
                """;
        setVariablesFromJson(variablesJson);

        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(LyshraOpenAppExpressionType.GRAAALVM_JS, expression);

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertEquals(expectedResult, result);
    }

    @ParameterizedTest(name = "Test evaluate with custom function: {0}")
    @MethodSource("customFunctionExpressionProvider")
    void testEvaluate_WithCustomFunctions_ShouldReturnExpectedResult(String expression, Class<?> expectedResultType) {
        // Given
        String dataJson = """
                {
                    "value": "test"
                }
                """;
        setDataFromJson(dataJson);

        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(LyshraOpenAppExpressionType.GRAAALVM_JS, expression);

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertTrue(expectedResultType.isInstance(result));
    }

    @Test
    void testEvaluate_WithInvalidFunctionInput_ShouldThrowException() {
        // Given - setLyshraOpenAppContextVariable() requires 2 arguments, but we're calling it with only 1
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = setLyshraOpenAppContextVariable('key')" // Missing second argument
        );

        // When & Then - should throw an exception (either PolyglotException from GraalVM or LyshraOpenAppProcessorRuntimeException)
        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            facade.getExpressionExecutor().evaluate(expr, context, facade);
        });
        // The exception should be either:
        // 1. LyshraOpenAppProcessorRuntimeException (wrapping InvalidGraalVmFunctionInputException)
        // 2. PolyglotException (from GraalVM when function call fails)
        // 3. Or have a cause that is one of the above
        boolean isCorrectException = exception instanceof LyshraOpenAppProcessorRuntimeException ||
                exception.getClass().getSimpleName().equals("PolyglotException");
        if (!isCorrectException && exception.getCause() != null) {
            isCorrectException = exception.getCause() instanceof LyshraOpenAppProcessorRuntimeException ||
                    exception.getCause().getClass().getSimpleName().equals("PolyglotException");
        }
        Assertions.assertTrue(isCorrectException, 
                "Expected LyshraOpenAppProcessorRuntimeException or PolyglotException, but got: " + exception.getClass().getName());
    }

    @Test
    void testEvaluate_WithInvalidSyntax_ShouldThrowException() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = invalid syntax !@#$%"
        );

        // When & Then
        Assertions.assertThrows(Exception.class, () -> {
            facade.getExpressionExecutor().evaluate(expr, context, facade);
        });
    }

    @Test
    void testEvaluate_WithNullContext_ShouldThrowException() {
        // Given - null context will cause NPE when accessing context.getData() and context.getVariables()
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = 42"
        );

        // When & Then - should throw NullPointerException
        Assertions.assertThrows(NullPointerException.class, () -> {
            facade.getExpressionExecutor().evaluate(expr, null, facade);
        });
    }

    @Test
    void testEvaluate_WithEmptyContext_ShouldHandleGracefully() {
        // Given
        ILyshraOpenAppContext emptyContext = new LyshraOpenAppContext();
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = $data == undefined ? 'no data' : 'has data'"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, emptyContext, facade);

        // Then
        Assertions.assertNotNull(result);
    }

    @Test
    void testEvaluate_WithArrayResult_ShouldReturnList() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = [1, 2, 3, 4, 5]"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) result;
        Assertions.assertEquals(5, list.size());
        Assertions.assertEquals(1, list.get(0));
        Assertions.assertEquals(5, list.get(4));
    }

    @Test
    void testEvaluate_WithObjectResult_ShouldReturnMap() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = {key1: 'value1', key2: 42, key3: true}"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        Assertions.assertEquals("value1", map.get("key1"));
        Assertions.assertEquals(42, map.get("key2"));
        Assertions.assertEquals(true, map.get("key3"));
    }

    @Test
    void testEvaluate_WithNestedObjectResult_ShouldReturnNestedMap() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = {outer: {inner: 'value', number: 123}}"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> outer = (Map<String, Object>) result;
        Assertions.assertTrue(outer.get("outer") instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) outer.get("outer");
        Assertions.assertEquals("value", inner.get("inner"));
        Assertions.assertEquals(123, inner.get("number"));
    }

    @Test
    void testEvaluate_WithNullResult_ShouldReturnNull() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = null"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertNull(result);
    }

    @Test
    void testEvaluate_WithUndefinedResult_ShouldReturnNull() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "var x; result = x"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertNull(result);
    }

    @Test
    void testEvaluate_WithLogging_ShouldCaptureLogs() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "console.log('Test log message'); result = 42"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertEquals(42, result);
        // Logs are captured in outputStream and logged via log.info, but we can't easily verify that
        // The important thing is that it doesn't throw an exception
    }

    @Test
    void testEvaluate_WithComplexExpression_ShouldReturnCorrectResult() {
        // Given
        String dataJson = """
                {
                    "count": 10,
                    "items": [1, 2, 3]
                }
                """;
        setDataFromJson(dataJson);
        
        String variablesJson = """
                {
                    "threshold": 5
                }
                """;
        setVariablesFromJson(variablesJson);

        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = $data.count > $variables.threshold && $data.items.length > 2"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertEquals(true, result);
    }

    @Test
    void testEvaluate_WithArithmeticOperations_ShouldReturnCorrectResult() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = (10 + 5) * 2 - 3"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertEquals(27, result);
    }

    @Test
    void testEvaluate_WithStringOperations_ShouldReturnCorrectResult() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = 'Hello' + ' ' + 'World'"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertEquals("Hello World", result);
    }

    @Test
    void testEvaluate_WithConditionalExpression_ShouldReturnCorrectResult() {
        // Given
        String dataJson = """
                {
                    "value": 10
                }
                """;
        setDataFromJson(dataJson);

        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = $data.value > 5 ? 'greater' : 'lesser'"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertEquals("greater", result);
    }

    @Test
    void testEvaluate_WithFunctionCall_ShouldExecuteFunction() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = jlog('test', 123)"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof List);
    }

    @Test
    void testEvaluate_WithNowMinusXMinFunction_ShouldReturnInstant() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = nowMinusXMin(5)"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof java.time.Instant);
    }

    @Test
    void testEvaluate_WithSetContextVariableFunction_ShouldSetVariable() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "result = setLyshraOpenAppContextVariable('testKey', 'testValue')"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals("testValue", context.getVariable("testKey"));
    }

    @Test
    void testEvaluate_WithEmptyExpression_ShouldHandleGracefully() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                ""
        );

        // When & Then - empty expression might throw or return undefined
        try {
            Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);
            // If it doesn't throw, result should be null or undefined
            Assertions.assertTrue(result == null || result.toString().equals("undefined"));
        } catch (Exception e) {
            // It's acceptable for empty expression to throw
            Assertions.assertNotNull(e);
        }
    }

    @Test
    void testEvaluate_WithExpressionNotSettingResult_ShouldReturnNull() {
        // Given
        ILyshraOpenAppExpression expr = new LyshraOpenAppExpression(
                LyshraOpenAppExpressionType.GRAAALVM_JS,
                "var x = 10"
        );

        // When - use facade to evaluate expression
        Object result = facade.getExpressionExecutor().evaluate(expr, context, facade);

        // Then
        Assertions.assertNull(result);
    }

    /**
     * Helper method to set context data from JSON string
     */
    private void setDataFromJson(String json) {
        try {
            tools.jackson.databind.ObjectMapper objectMapper = (tools.jackson.databind.ObjectMapper) facade.getObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            context.setData(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse data JSON", e);
        }
    }

    /**
     * Helper method to set context variables from JSON string
     */
    private void setVariablesFromJson(String json) {
        try {
            tools.jackson.databind.ObjectMapper objectMapper = (tools.jackson.databind.ObjectMapper) facade.getObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> variables = objectMapper.readValue(json, Map.class);
            context.setVariables(variables);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse variables JSON", e);
        }
    }

    private static Stream<Arguments> basicExpressionProvider() {
        return Stream.of(
                // Numbers
                Arguments.of("result = 42", 42),
                Arguments.of("result = 3.14", 3.14),
                Arguments.of("result = -10", -10),
                Arguments.of("result = 0", 0),

                // Booleans
                Arguments.of("result = true", true),
                Arguments.of("result = false", false),

                // Strings
                Arguments.of("result = 'hello'", "hello"),
                Arguments.of("result = \"world\"", "world"),
                Arguments.of("result = ''", ""),

                // Simple arithmetic
                Arguments.of("result = 2 + 2", 4),
                Arguments.of("result = 10 - 3", 7),
                Arguments.of("result = 4 * 5", 20),
                Arguments.of("result = 15 / 3", 5),
                Arguments.of("result = 10 % 3", 1),

                // Comparisons
                Arguments.of("result = 5 > 3", true),
                Arguments.of("result = 5 < 3", false),
                Arguments.of("result = 5 >= 5", true),
                Arguments.of("result = 5 <= 3", false),
                Arguments.of("result = 5 == 5", true),
                Arguments.of("result = 5 != 3", true),

                // Logical operators
                Arguments.of("result = true && true", true),
                Arguments.of("result = true && false", false),
                Arguments.of("result = true || false", true),
                Arguments.of("result = false || false", false),
                Arguments.of("result = !true", false),
                Arguments.of("result = !false", true)
        );
    }

    private static Stream<Arguments> dataExpressionProvider() {
        return Stream.of(
                // Direct property access
                Arguments.of("result = $data.count", 10),
                Arguments.of("result = $data.name", "test"),
                Arguments.of("result = $data.active", true),
                Arguments.of("result = $data.price", 99.99),

                // Nested property access
                Arguments.of("result = $data.nested.field", "value"),
                Arguments.of("result = $data.nested.number", 42),

                // Array access
                Arguments.of("result = $data.items[0]", 1),
                Arguments.of("result = $data.items[1]", 2),
                Arguments.of("result = $data.items.length", 3),

                // Comparisons with data
                Arguments.of("result = $data.count == 10", true),
                Arguments.of("result = $data.count > 5", true),
                Arguments.of("result = $data.count < 15", true),
                Arguments.of("result = $data.name == 'test'", true),
                Arguments.of("result = $data.active == true", true),

                // Complex expressions with data
                Arguments.of("result = $data.count + 5", 15),
                Arguments.of("result = $data.count * 2", 20),
                Arguments.of("result = $data.count > 5 && $data.count < 15", true),
                Arguments.of("result = $data.items.length > 2", true)
        );
    }

    private static Stream<Arguments> variablesExpressionProvider() {
        return Stream.of(
                // Direct variable access
                Arguments.of("result = $variables.threshold", 20),
                Arguments.of("result = $variables.minScore", 80),
                Arguments.of("result = $variables.status", "active"),
                Arguments.of("result = $variables.enabled", true),
                Arguments.of("result = $variables.count", 5),

                // Comparisons with variables
                Arguments.of("result = $variables.threshold == 20", true),
                Arguments.of("result = $variables.threshold > 15", true),
                Arguments.of("result = $variables.minScore < 100", true),
                Arguments.of("result = $variables.status == 'active'", true),
                Arguments.of("result = $variables.enabled == true", true),

                // Complex expressions with variables
                Arguments.of("result = $variables.threshold + 10", 30),
                Arguments.of("result = $variables.threshold * 2", 40),
                Arguments.of("result = $variables.threshold > 15 && $variables.minScore > 50", true),
                Arguments.of("result = $variables.count + 5", 10)
        );
    }

    private static Stream<Arguments> customFunctionExpressionProvider() {
        return Stream.of(
                // jlog function
                Arguments.of("result = jlog('test')", List.class),
                Arguments.of("result = jlog('test', 123)", List.class),
                Arguments.of("result = jlog('test', 123, true)", List.class),

                // nowMinusXMin function
                Arguments.of("result = nowMinusXMin(5)", java.time.Instant.class),
                Arguments.of("result = nowMinusXMin(10)", java.time.Instant.class),
                Arguments.of("result = nowMinusXMin(0)", java.time.Instant.class),

                // setLyshraOpenAppContextVariable function
                Arguments.of("result = setLyshraOpenAppContextVariable('key', 'value')", List.class),
                Arguments.of("result = setLyshraOpenAppContextVariable('num', 42)", List.class),
                Arguments.of("result = setLyshraOpenAppContextVariable('bool', true)", List.class)
        );
    }
}
