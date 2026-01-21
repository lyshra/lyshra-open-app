package com.lyshra.open.app.core.processors.plugin.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExceptionUtils Tests")
class ExceptionUtilsTest {

    // ==================== matchesInCauseChain Tests ====================

    @Nested
    @DisplayName("matchesInCauseChain")
    class MatchesInCauseChainTests {

        @Test
        @DisplayName("returns false for null throwable")
        void nullThrowable_ReturnsFalse() {
            assertFalse(ExceptionUtils.matchesInCauseChain(null, ex -> true));
        }

        @Test
        @DisplayName("returns false for null predicate match")
        void nullPredicateResult_HandledGracefully() {
            Exception ex = new RuntimeException("Test");
            // Predicate that would cause NPE if message is null
            assertDoesNotThrow(() ->
                    ExceptionUtils.matchesInCauseChain(ex, e -> e.getMessage().contains("Test")));
        }

        @ParameterizedTest(name = "immediate match with message: {0}")
        @ValueSource(strings = {"Error", "Test error", "Unsupported date type: boolean", "Something went wrong"})
        @DisplayName("returns true for immediate message match")
        void immediateMatch_ReturnsTrue(String message) {
            Exception ex = new RuntimeException(message);
            assertTrue(ExceptionUtils.matchesInCauseChain(ex, e -> e.getMessage() != null && e.getMessage().equals(message)));
        }

        @ParameterizedTest(name = "nested match at depth {0}")
        @MethodSource("com.lyshra.open.app.core.processors.plugin.utils.ExceptionUtilsTest#nestedExceptionDepthProvider")
        @DisplayName("returns true for nested match at various depths")
        void nestedMatch_ReturnsTrue(int depth, Throwable exception, String targetMessage) {
            assertTrue(ExceptionUtils.matchesInCauseChain(exception,
                    e -> e.getMessage() != null && e.getMessage().contains(targetMessage)));
        }

        @Test
        @DisplayName("returns false when no exception matches")
        void noMatch_ReturnsFalse() {
            Exception ex = new RuntimeException("Some error");
            assertFalse(ExceptionUtils.matchesInCauseChain(ex, e -> e.getMessage() != null && e.getMessage().contains("Not found")));
        }

        @Test
        @DisplayName("handles exception with null message gracefully")
        void nullMessage_HandledGracefully() {
            Exception ex = new RuntimeException((String) null);
            assertFalse(ExceptionUtils.matchesInCauseChain(ex, e -> {
                String msg = e.getMessage();
                return msg != null && msg.contains("Test");
            }));
        }

        @ParameterizedTest(name = "exception type match: {0}")
        @MethodSource("com.lyshra.open.app.core.processors.plugin.utils.ExceptionUtilsTest#exceptionTypeProvider")
        @DisplayName("matches by exception type")
        void matchByExceptionType(Class<? extends Throwable> exceptionType, Throwable exception, boolean expectedMatch) {
            assertEquals(expectedMatch, ExceptionUtils.matchesInCauseChain(exception, exceptionType::isInstance));
        }
    }

    // ==================== messageContainsInCauseChain Tests ====================

    @Nested
    @DisplayName("messageContainsInCauseChain")
    class MessageContainsInCauseChainTests {

        @Test
        @DisplayName("returns false for null throwable")
        void nullThrowable_ReturnsFalse() {
            assertFalse(ExceptionUtils.messageContainsInCauseChain(null, "test"));
        }

        @ParameterizedTest(name = "immediate message contains: \"{1}\"")
        @MethodSource("com.lyshra.open.app.core.processors.plugin.utils.ExceptionUtilsTest#immediateMessageProvider")
        @DisplayName("returns true when immediate exception message contains substring")
        void immediateMessageContains_ReturnsTrue(String fullMessage, String substring) {
            Exception ex = new RuntimeException(fullMessage);
            assertTrue(ExceptionUtils.messageContainsInCauseChain(ex, substring));
        }

        @ParameterizedTest(name = "nested message at depth {0}")
        @MethodSource("com.lyshra.open.app.core.processors.plugin.utils.ExceptionUtilsTest#nestedMessageProvider")
        @DisplayName("returns true when nested exception message contains substring")
        void nestedMessageContains_ReturnsTrue(int depth, Throwable exception, String substring) {
            assertTrue(ExceptionUtils.messageContainsInCauseChain(exception, substring));
        }

        @ParameterizedTest(name = "no match for substring: \"{1}\"")
        @MethodSource("com.lyshra.open.app.core.processors.plugin.utils.ExceptionUtilsTest#noMatchMessageProvider")
        @DisplayName("returns false when no message contains substring")
        void noMessageMatch_ReturnsFalse(String message, String substring) {
            Exception ex = new RuntimeException(message);
            assertFalse(ExceptionUtils.messageContainsInCauseChain(ex, substring));
        }

        @Test
        @DisplayName("handles null message in chain gracefully")
        void nullMessageInChain_HandlesGracefully() {
            Exception root = new RuntimeException((String) null);
            Exception middle = new RuntimeException("Middle", root);
            Exception top = new RuntimeException((String) null, middle);

            // Should not throw and should find "Middle"
            assertTrue(ExceptionUtils.messageContainsInCauseChain(top, "Middle"));
            assertFalse(ExceptionUtils.messageContainsInCauseChain(top, "NotFound"));
        }

        @Test
        @DisplayName("handles all null messages in chain")
        void allNullMessagesInChain_ReturnsFalse() {
            Exception root = new RuntimeException((String) null);
            Exception middle = new RuntimeException((String) null, root);
            Exception top = new RuntimeException((String) null, middle);

            assertFalse(ExceptionUtils.messageContainsInCauseChain(top, "anything"));
        }

        @ParameterizedTest(name = "case sensitive search: \"{0}\" contains \"{1}\" = {2}")
        @MethodSource("com.lyshra.open.app.core.processors.plugin.utils.ExceptionUtilsTest#caseSensitiveProvider")
        @DisplayName("search is case sensitive")
        void caseSensitiveSearch(String message, String substring, boolean expected) {
            Exception ex = new RuntimeException(message);
            assertEquals(expected, ExceptionUtils.messageContainsInCauseChain(ex, substring));
        }
    }

    // ==================== hasExceptionTypeInCauseChain Tests ====================

    @Nested
    @DisplayName("hasExceptionTypeInCauseChain")
    class HasExceptionTypeInCauseChainTests {

        @ParameterizedTest(name = "immediate type match: {0}")
        @MethodSource("com.lyshra.open.app.core.processors.plugin.utils.ExceptionUtilsTest#immediateExceptionTypeProvider")
        @DisplayName("returns true for immediate exception type match")
        void immediateTypeMatch_ReturnsTrue(Class<? extends Throwable> type, Throwable exception) {
            assertTrue(ExceptionUtils.hasExceptionTypeInCauseChain(exception, type));
        }

        @ParameterizedTest(name = "nested type match at depth {0}: {1}")
        @MethodSource("com.lyshra.open.app.core.processors.plugin.utils.ExceptionUtilsTest#nestedExceptionTypeProvider")
        @DisplayName("returns true for nested exception type match")
        void nestedTypeMatch_ReturnsTrue(int depth, Class<? extends Throwable> type, Throwable exception) {
            assertTrue(ExceptionUtils.hasExceptionTypeInCauseChain(exception, type));
        }

        @ParameterizedTest(name = "no type match: looking for {0}")
        @MethodSource("com.lyshra.open.app.core.processors.plugin.utils.ExceptionUtilsTest#noTypeMatchProvider")
        @DisplayName("returns false when exception type not in chain")
        void noTypeMatch_ReturnsFalse(Class<? extends Throwable> searchType, Throwable exception) {
            assertFalse(ExceptionUtils.hasExceptionTypeInCauseChain(exception, searchType));
        }

        @Test
        @DisplayName("matches parent exception types (polymorphism)")
        void parentTypeMatch_ReturnsTrue() {
            Exception ex = new IllegalArgumentException("Invalid");
            // IllegalArgumentException extends RuntimeException extends Exception extends Throwable
            assertTrue(ExceptionUtils.hasExceptionTypeInCauseChain(ex, IllegalArgumentException.class));
            assertTrue(ExceptionUtils.hasExceptionTypeInCauseChain(ex, RuntimeException.class));
            assertTrue(ExceptionUtils.hasExceptionTypeInCauseChain(ex, Exception.class));
            assertTrue(ExceptionUtils.hasExceptionTypeInCauseChain(ex, Throwable.class));
        }

        @Test
        @DisplayName("returns false for null throwable")
        void nullThrowable_ReturnsFalse() {
            assertFalse(ExceptionUtils.hasExceptionTypeInCauseChain(null, RuntimeException.class));
        }
    }

    // ==================== findInCauseChain Tests ====================

    @Nested
    @DisplayName("findInCauseChain")
    class FindInCauseChainTests {

        @Test
        @DisplayName("returns null for null throwable")
        void nullThrowable_ReturnsNull() {
            assertNull(ExceptionUtils.findInCauseChain(null, ex -> true));
        }

        @ParameterizedTest(name = "find immediate exception with message: {0}")
        @ValueSource(strings = {"Target", "Error message", "Unsupported date type"})
        @DisplayName("finds immediate exception")
        void immediateFind_ReturnsException(String message) {
            Exception ex = new RuntimeException(message);
            Throwable found = ExceptionUtils.findInCauseChain(ex, e -> e.getMessage() != null && e.getMessage().equals(message));
            assertSame(ex, found);
        }

        @ParameterizedTest(name = "find nested exception at depth {0}")
        @MethodSource("com.lyshra.open.app.core.processors.plugin.utils.ExceptionUtilsTest#findNestedExceptionProvider")
        @DisplayName("finds nested exception at various depths")
        void nestedFind_ReturnsCorrectException(int depth, Throwable topException, Throwable targetException, String targetMessage) {
            Throwable found = ExceptionUtils.findInCauseChain(topException,
                    e -> e.getMessage() != null && e.getMessage().equals(targetMessage));
            assertSame(targetException, found);
        }

        @Test
        @DisplayName("returns null when no match found")
        void noMatch_ReturnsNull() {
            Exception ex = new RuntimeException("Test");
            assertNull(ExceptionUtils.findInCauseChain(ex, e -> e.getMessage() != null && e.getMessage().equals("Not found")));
        }

        @Test
        @DisplayName("finds first matching exception when multiple match")
        void multipleMatches_ReturnsFirst() {
            Exception root = new RuntimeException("Match");
            Exception middle = new RuntimeException("Match", root);
            Exception top = new RuntimeException("Top", middle);

            Throwable found = ExceptionUtils.findInCauseChain(top,
                    e -> e.getMessage() != null && e.getMessage().equals("Match"));
            assertSame(middle, found); // First match in chain (not top, which doesn't match)
        }
    }

    // ==================== getRootCause Tests ====================

    @Nested
    @DisplayName("getRootCause")
    class GetRootCauseTests {

        @Test
        @DisplayName("returns null for null throwable")
        void nullThrowable_ReturnsNull() {
            assertNull(ExceptionUtils.getRootCause(null));
        }

        @Test
        @DisplayName("returns self when no cause")
        void noCause_ReturnsSelf() {
            Exception ex = new RuntimeException("No cause");
            assertSame(ex, ExceptionUtils.getRootCause(ex));
        }

        @ParameterizedTest(name = "chain depth {0}")
        @MethodSource("com.lyshra.open.app.core.processors.plugin.utils.ExceptionUtilsTest#rootCauseDepthProvider")
        @DisplayName("returns deepest root at various chain depths")
        void variousDepths_ReturnsDeepestRoot(int depth, Throwable topException, Throwable expectedRoot) {
            assertSame(expectedRoot, ExceptionUtils.getRootCause(topException));
        }

        @Test
        @DisplayName("handles mixed exception types in chain")
        void mixedExceptionTypes_ReturnsRoot() {
            SQLException root = new SQLException("Database error");
            IOException io = new IOException("IO error", root);
            RuntimeException runtime = new RuntimeException("Runtime error", io);
            Exception top = new Exception("Top error", runtime);

            assertSame(root, ExceptionUtils.getRootCause(top));
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("handles very deep exception chain")
        void veryDeepChain_HandlesGracefully() {
            Throwable current = new RuntimeException("Root - target message");
            for (int i = 0; i < 100; i++) {
                current = new RuntimeException("Level " + i, current);
            }

            assertTrue(ExceptionUtils.messageContainsInCauseChain(current, "target message"));
            assertTrue(ExceptionUtils.messageContainsInCauseChain(current, "Root"));
            assertNotNull(ExceptionUtils.getRootCause(current));
        }

        @Test
        @DisplayName("handles empty string message")
        void emptyStringMessage_HandlesGracefully() {
            Exception ex = new RuntimeException("");
            assertFalse(ExceptionUtils.messageContainsInCauseChain(ex, "test"));
            assertTrue(ExceptionUtils.messageContainsInCauseChain(ex, "")); // Empty contains empty
        }

        @Test
        @DisplayName("handles whitespace-only message")
        void whitespaceMessage_HandlesGracefully() {
            Exception ex = new RuntimeException("   ");
            assertTrue(ExceptionUtils.messageContainsInCauseChain(ex, " "));
            assertFalse(ExceptionUtils.messageContainsInCauseChain(ex, "test"));
        }

        @Test
        @DisplayName("predicate receives correct exception instances")
        void predicateReceivesCorrectInstances() {
            Exception root = new IllegalArgumentException("Root");
            Exception middle = new RuntimeException("Middle", root);
            Exception top = new IOException("Top", middle);

            // Verify we can identify specific exceptions by their type and message
            assertTrue(ExceptionUtils.matchesInCauseChain(top,
                    e -> e instanceof IOException && "Top".equals(e.getMessage())));
            assertTrue(ExceptionUtils.matchesInCauseChain(top,
                    e -> e instanceof RuntimeException && "Middle".equals(e.getMessage())));
            assertTrue(ExceptionUtils.matchesInCauseChain(top,
                    e -> e instanceof IllegalArgumentException && "Root".equals(e.getMessage())));
        }
    }

    // ==================== Method Source Providers ====================

    static Stream<Arguments> nestedExceptionDepthProvider() {
        String targetMessage = "Target error message";
        Exception target = new RuntimeException(targetMessage);

        Exception depth1 = target;
        Exception depth2 = new RuntimeException("Wrapper 1", target);
        Exception depth3 = new RuntimeException("Wrapper 2", depth2);
        Exception depth4 = new RuntimeException("Wrapper 3", depth3);
        Exception depth5 = new RuntimeException("Wrapper 4", depth4);

        return Stream.of(
                Arguments.of(1, depth1, targetMessage),
                Arguments.of(2, depth2, targetMessage),
                Arguments.of(3, depth3, targetMessage),
                Arguments.of(4, depth4, targetMessage),
                Arguments.of(5, depth5, targetMessage)
        );
    }

    static Stream<Arguments> exceptionTypeProvider() {
        Exception runtime = new RuntimeException("Runtime");
        Exception illegal = new IllegalArgumentException("Illegal");
        Exception io = new IOException("IO");

        Exception wrappedIo = new RuntimeException("Wrapper", io);
        Exception deepIo = new Exception("Deep", new RuntimeException("Mid", io));

        return Stream.of(
                Arguments.of(RuntimeException.class, runtime, true),
                Arguments.of(IllegalArgumentException.class, illegal, true),
                Arguments.of(IOException.class, io, true),
                Arguments.of(IOException.class, wrappedIo, true),
                Arguments.of(IOException.class, deepIo, true),
                Arguments.of(SQLException.class, runtime, false),
                Arguments.of(IOException.class, illegal, false)
        );
    }

    static Stream<Arguments> immediateMessageProvider() {
        return Stream.of(
                Arguments.of("Unsupported date type: boolean", "Unsupported date type"),
                Arguments.of("Unsupported date type: object", "date type"),
                Arguments.of("Error: Something went wrong", "Error"),
                Arguments.of("Error: Something went wrong", "went wrong"),
                Arguments.of("Processing failed for input", "failed"),
                Arguments.of("NullPointerException occurred", "NullPointer")
        );
    }

    static Stream<Arguments> nestedMessageProvider() {
        String target = "Unsupported date type";

        Exception root = new RuntimeException(target + ": boolean");
        Exception depth2 = new RuntimeException("Execution failed", root);
        Exception depth3 = new RuntimeException("Processing error", depth2);
        Exception depth4 = new IOException("IO wrapper", depth3);
        Exception depth5 = new Exception("Top level", depth4);

        return Stream.of(
                Arguments.of(1, root, target),
                Arguments.of(2, depth2, target),
                Arguments.of(3, depth3, target),
                Arguments.of(4, depth4, target),
                Arguments.of(5, depth5, target)
        );
    }

    static Stream<Arguments> noMatchMessageProvider() {
        return Stream.of(
                Arguments.of("Error occurred", "warning"),
                Arguments.of("Processing complete", "failed"),
                Arguments.of("Success", "error"),
                Arguments.of("Valid input", "invalid")
        );
    }

    static Stream<Arguments> caseSensitiveProvider() {
        return Stream.of(
                Arguments.of("Unsupported Date Type", "Date", true),
                Arguments.of("Unsupported Date Type", "date", false),
                Arguments.of("ERROR MESSAGE", "ERROR", true),
                Arguments.of("ERROR MESSAGE", "error", false),
                Arguments.of("MixedCase", "Mixed", true),
                Arguments.of("MixedCase", "mixed", false)
        );
    }

    static Stream<Arguments> immediateExceptionTypeProvider() {
        return Stream.of(
                Arguments.of(RuntimeException.class, new RuntimeException("test")),
                Arguments.of(IllegalArgumentException.class, new IllegalArgumentException("test")),
                Arguments.of(IOException.class, new IOException("test")),
                Arguments.of(NullPointerException.class, new NullPointerException("test")),
                Arguments.of(IllegalStateException.class, new IllegalStateException("test"))
        );
    }

    static Stream<Arguments> nestedExceptionTypeProvider() {
        IOException ioRoot = new IOException("IO error");
        SQLException sqlRoot = new SQLException("SQL error");
        TimeoutException timeoutRoot = new TimeoutException("Timeout");

        return Stream.of(
                Arguments.of(2, IOException.class, new RuntimeException("Wrapper", ioRoot)),
                Arguments.of(3, IOException.class, new Exception("Level2", new RuntimeException("Level1", ioRoot))),
                Arguments.of(2, SQLException.class, new RuntimeException("Wrapper", sqlRoot)),
                Arguments.of(4, TimeoutException.class,
                        new Exception("L3", new RuntimeException("L2", new IllegalStateException("L1", timeoutRoot))))
        );
    }

    static Stream<Arguments> noTypeMatchProvider() {
        return Stream.of(
                Arguments.of(IOException.class, new RuntimeException("Runtime only")),
                Arguments.of(SQLException.class, new IllegalArgumentException("Illegal only")),
                Arguments.of(TimeoutException.class, new RuntimeException("Wrapped",
                        new IllegalStateException("Nested")))
        );
    }

    static Stream<Arguments> findNestedExceptionProvider() {
        Exception target1 = new RuntimeException("Target1");
        Exception top1 = new RuntimeException("Wrapper", target1);

        Exception target2 = new IllegalArgumentException("Target2");
        Exception mid2 = new RuntimeException("Middle", target2);
        Exception top2 = new Exception("Top", mid2);

        Exception target3 = new IOException("Target3");
        Exception l1 = new RuntimeException("L1", target3);
        Exception l2 = new RuntimeException("L2", l1);
        Exception top3 = new RuntimeException("Top", l2);

        return Stream.of(
                Arguments.of(2, top1, target1, "Target1"),
                Arguments.of(3, top2, target2, "Target2"),
                Arguments.of(4, top3, target3, "Target3")
        );
    }

    static Stream<Arguments> rootCauseDepthProvider() {
        Exception root1 = new RuntimeException("Root1");

        Exception root2 = new IllegalArgumentException("Root2");
        Exception top2 = new RuntimeException("Top2", root2);

        Exception root3 = new IOException("Root3");
        Exception mid3 = new RuntimeException("Mid3", root3);
        Exception top3 = new Exception("Top3", mid3);

        Exception root4 = new SQLException("Root4");
        Exception l1 = new RuntimeException("L1", root4);
        Exception l2 = new RuntimeException("L2", l1);
        Exception top4 = new RuntimeException("Top4", l2);

        return Stream.of(
                Arguments.of(1, root1, root1),
                Arguments.of(2, top2, root2),
                Arguments.of(3, top3, root3),
                Arguments.of(4, top4, root4)
        );
    }
}
