package com.lyshra.open.app.core.processors.plugin.utils;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Utility class for working with exceptions and their cause chains.
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Recursively searches through an exception and its cause chain to find a match.
     * Uses a predicate for flexible matching criteria.
     *
     * @param throwable the exception to search through
     * @param predicate the predicate to test against each exception in the chain
     * @return true if any exception in the chain matches the predicate, false otherwise
     */
    public static boolean matchesInCauseChain(Throwable throwable, Predicate<Throwable> predicate) {
        if (throwable == null) {
            return false;
        }

        // Track visited exceptions to prevent infinite loops with circular references
        Set<Throwable> visited = new HashSet<>();
        Throwable current = throwable;

        while (current != null && !visited.contains(current)) {
            if (predicate.test(current)) {
                return true;
            }
            visited.add(current);
            current = current.getCause();
        }

        return false;
    }

    /**
     * Checks if any exception message in the cause chain contains the specified substring.
     *
     * @param throwable the exception to search through
     * @param substring the substring to search for in exception messages
     * @return true if any exception message contains the substring, false otherwise
     */
    public static boolean messageContainsInCauseChain(Throwable throwable, String substring) {
        return matchesInCauseChain(throwable, ex -> {
            String message = ex.getMessage();
            return message != null && message.contains(substring);
        });
    }

    /**
     * Checks if any exception in the cause chain is of the specified type.
     *
     * @param throwable the exception to search through
     * @param exceptionType the exception class to search for
     * @return true if any exception in the chain is of the specified type, false otherwise
     */
    public static boolean hasExceptionTypeInCauseChain(Throwable throwable, Class<? extends Throwable> exceptionType) {
        return matchesInCauseChain(throwable, exceptionType::isInstance);
    }

    /**
     * Finds and returns the first exception in the cause chain that matches the predicate.
     *
     * @param throwable the exception to search through
     * @param predicate the predicate to test against each exception in the chain
     * @return the first matching exception, or null if none found
     */
    public static Throwable findInCauseChain(Throwable throwable, Predicate<Throwable> predicate) {
        if (throwable == null) {
            return null;
        }

        Set<Throwable> visited = new HashSet<>();
        Throwable current = throwable;

        while (current != null && !visited.contains(current)) {
            if (predicate.test(current)) {
                return current;
            }
            visited.add(current);
            current = current.getCause();
        }

        return null;
    }

    /**
     * Gets the root cause of an exception (the deepest cause in the chain).
     *
     * @param throwable the exception to get the root cause from
     * @return the root cause, or the original exception if it has no cause
     */
    public static Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        Set<Throwable> visited = new HashSet<>();
        Throwable current = throwable;
        Throwable rootCause = throwable;

        while (current != null && !visited.contains(current)) {
            rootCause = current;
            visited.add(current);
            current = current.getCause();
        }

        return rootCause;
    }
}
