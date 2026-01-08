package com.lyshra.open.app.core.util;

import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.util.FileCopyUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CommonUtil {
    public static final char COMMA_SEPARATOR_CHAR = ',';
    public static final String COMMA_SEPARATOR = String.valueOf(COMMA_SEPARATOR_CHAR);

    private CommonUtil() {
    }

    public static boolean isNullOrBlank(final String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNotBlank(final String str) {
        return str != null && !str.trim().isEmpty();
    }

    public static <T> boolean isNullOrEmpty(final Collection<T> collection) {
        return collection == null || collection.isEmpty();
    }

    public static <T> boolean isNotEmpty(final Collection<T> collection) {
        return collection != null && !collection.isEmpty();
    }

    public static <T> boolean isNotEmpty(final T[] array) {
        return array != null && array.length > 0;
    }

    public static Set<String> csvToSet(final String commaSeparatedValues) {
        return Optional
            .ofNullable(commaSeparatedValues)
            .filter(CommonUtil::isNotBlank)
            .map(str -> str.split(COMMA_SEPARATOR))
            .map(Arrays::asList)
            .map(list -> (Set<String>) new HashSet<>(list))
            .orElse(Collections.emptySet());
    }

    public static String toCsv(final Iterable<String> iterator) {
        return String.join(COMMA_SEPARATOR, iterator);
    }

    public static boolean isBoolean(String s) {
        return Boolean.TRUE.toString().equalsIgnoreCase(s) || Boolean.FALSE.toString().equalsIgnoreCase(s);
    }

    public static boolean isTrue(String s) {
        if (isNullOrBlank(s))
            return false;

        s = s.trim();
        return Boolean.TRUE.toString().equalsIgnoreCase(s) || "1".equals(s);
    }

    public static boolean isFalse(String s) {
        return !isTrue(s);
    }

    public static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isDouble(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isNumber(String s) {
        return isDouble(s);
    }

    public static boolean isNumberPositive(String s) {
        try {
            return Double.parseDouble(s) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isNumberPositiveOrZero(String s) {
        try {
            return Double.parseDouble(s) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isNumberNegative(String s) {
        try {
            return Double.parseDouble(s) < 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isNumberNegativeOrZero(String s) {
        try {
            return Double.parseDouble(s) <= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // caller need to ensure that the set contains an element, else it'll throw NoSuchElementException
    public static <T> T getFirst(Collection<T> collection) {
        return collection.stream().findFirst().get();
    }

    public static <T> List<T> nonNullList(List<T> list) {
        return Optional.ofNullable(list).orElse(Collections.emptyList());
    }

    public static <T> Set<T> nonNullSet(Set<T> set) {
        return Optional.ofNullable(set).orElse(Collections.emptySet());
    }

    public static <T> Collection<T> nonNullCollection(Collection<T> collection) {
        return Optional.ofNullable(collection).orElse(Collections.emptyList());
    }

    public static <T> Iterator<T> nonNullIterator(Iterator<T> iterator) {
        return Optional.ofNullable(iterator).orElse(Collections.emptyIterator());
    }

    public static <K, V> Map<K, V> nonNullMap(Map<K, V> map) {
        return Optional.ofNullable(map).orElse(Collections.emptyMap());
    }

    public static String readResource(Resource resource) throws IOException {
        Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
        return FileCopyUtils.copyToString(reader);
    }

    public static String bufferToString(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return new String(bytes, Charset.defaultCharset());
    }

    public static boolean patternMatches(Pattern pattern, String input) {
        boolean matches = false;
        if (CommonUtil.isNotBlank(input)) {
            matches = pattern.matcher(input).matches();
        }
        return matches;
    }

    public static void close(Closeable closeable) {

        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // do nothing
            }
        }

    }

    public static <R> Set<String> extractUniqueStringData(Collection<R> records, Function<R, String> extractor) {
        return new HashSet<>(extractStringData(records, extractor));
    }

    public static <R> List<String> extractStringData(Collection<R> records, Function<R, String> extractor) {
        return records.stream()
            .map(extractor)
            .collect(Collectors.toList());
    }

    public static <T> T[] reverseArray(T[] array) {
        for (int i = 0; i < array.length / 2; i++) {
            T j = array[i];
            array[i] = array[array.length - i - 1];
            array[array.length - i - 1] = j;
        }
        return array;
    }

    public static char[] reverseArray(char[] array) {
        for (int i = 0; i < array.length / 2; i++) {
            char j = array[i];
            array[i] = array[array.length - i - 1];
            array[array.length - i - 1] = j;
        }
        return array;
    }

}


