package com.lyshra.open.app.mongodb.plugin.converter;

import com.lyshra.open.app.mongodb.plugin.error.MongoProcessorErrorCodes;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * BSON Type Converter utility.
 * Handles conversion between Java types and MongoDB BSON types.
 * Supports both to-BSON and from-BSON conversions with proper type handling.
 */
public final class BsonTypeConverter {

    private static final String OBJECT_ID_KEY = "$oid";
    private static final String DATE_KEY = "$date";
    private static final String DECIMAL_KEY = "$numberDecimal";
    private static final String LONG_KEY = "$numberLong";
    private static final String INT_KEY = "$numberInt";
    private static final String DOUBLE_KEY = "$numberDouble";
    private static final String BINARY_KEY = "$binary";
    private static final String REGEX_KEY = "$regex";
    private static final String TIMESTAMP_KEY = "$timestamp";

    private BsonTypeConverter() {
        // Utility class - prevent instantiation
    }

    /**
     * Convert a Map to a BSON Document.
     * Handles special type markers and nested structures.
     *
     * @param map The map to convert
     * @return A BSON Document
     */
    public static Document toDocument(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Document doc = new Document();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            doc.put(entry.getKey(), toBsonValue(entry.getValue()));
        }
        return doc;
    }

    /**
     * Convert a list of Maps to a list of BSON Documents.
     *
     * @param maps The list of maps to convert
     * @return A list of BSON Documents
     */
    public static List<Document> toDocuments(List<Map<String, Object>> maps) {
        if (maps == null) {
            return Collections.emptyList();
        }
        return maps.stream()
                .map(BsonTypeConverter::toDocument)
                .toList();
    }

    /**
     * Convert a Java value to a BSON-compatible value.
     * Handles special type markers for ObjectId, Date, Decimal128, etc.
     *
     * @param value The value to convert
     * @return A BSON-compatible value
     */
    @SuppressWarnings("unchecked")
    public static Object toBsonValue(Object value) {
        if (value == null) {
            return null;
        }

        // Already BSON types
        if (value instanceof ObjectId || value instanceof Binary ||
            value instanceof Decimal128 || value instanceof BsonTimestamp) {
            return value;
        }

        // Handle special type markers in maps
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;

            // Check for special type markers
            if (map.containsKey(OBJECT_ID_KEY)) {
                return parseObjectId(map.get(OBJECT_ID_KEY));
            }
            if (map.containsKey(DATE_KEY)) {
                return parseDate(map.get(DATE_KEY));
            }
            if (map.containsKey(DECIMAL_KEY)) {
                return parseDecimal128(map.get(DECIMAL_KEY));
            }
            if (map.containsKey(LONG_KEY)) {
                return parseLong(map.get(LONG_KEY));
            }
            if (map.containsKey(INT_KEY)) {
                return parseInt(map.get(INT_KEY));
            }
            if (map.containsKey(DOUBLE_KEY)) {
                return parseDouble(map.get(DOUBLE_KEY));
            }
            if (map.containsKey(REGEX_KEY)) {
                return parseRegex(map);
            }
            if (map.containsKey(TIMESTAMP_KEY)) {
                return parseTimestamp(map.get(TIMESTAMP_KEY));
            }

            // Regular nested document
            return toDocument(map);
        }

        // Handle lists
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream()
                    .map(BsonTypeConverter::toBsonValue)
                    .toList();
        }

        // Handle Java date/time types
        if (value instanceof Instant instant) {
            return Date.from(instant);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return Date.from(zonedDateTime.toInstant());
        }
        if (value instanceof java.util.Date) {
            return value;
        }

        // Handle BigDecimal
        if (value instanceof BigDecimal bigDecimal) {
            return new Decimal128(bigDecimal);
        }

        // Handle byte arrays
        if (value instanceof byte[] bytes) {
            return new Binary(bytes);
        }

        // Handle primitives and strings as-is
        if (value instanceof String || value instanceof Number ||
            value instanceof Boolean) {
            return value;
        }

        // Try to convert string to ObjectId if it looks like one
        if (value instanceof String strValue && ObjectId.isValid(strValue)) {
            // Don't auto-convert - could be intentional string
            return strValue;
        }

        return value;
    }

    /**
     * Convert a BSON Document to a Java Map.
     * Converts BSON types to Java-friendly representations.
     *
     * @param document The document to convert
     * @return A Java Map
     */
    public static Map<String, Object> fromDocument(Document document) {
        if (document == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            result.put(entry.getKey(), fromBsonValue(entry.getValue()));
        }
        return result;
    }

    /**
     * Convert a list of BSON Documents to a list of Java Maps.
     *
     * @param documents The list of documents to convert
     * @return A list of Java Maps
     */
    public static List<Map<String, Object>> fromDocuments(List<Document> documents) {
        if (documents == null) {
            return Collections.emptyList();
        }
        return documents.stream()
                .map(BsonTypeConverter::fromDocument)
                .toList();
    }

    /**
     * Convert a BSON value to a Java-friendly value.
     * ObjectId becomes String, Date becomes Instant, Decimal128 becomes BigDecimal.
     *
     * @param value The BSON value to convert
     * @return A Java-friendly value
     */
    @SuppressWarnings("unchecked")
    public static Object fromBsonValue(Object value) {
        if (value == null) {
            return null;
        }

        // Convert ObjectId to String
        if (value instanceof ObjectId objectId) {
            return objectId.toHexString();
        }

        // Convert Date to Instant
        if (value instanceof Date date) {
            return date.toInstant();
        }

        // Convert Decimal128 to BigDecimal
        if (value instanceof Decimal128 decimal128) {
            return decimal128.bigDecimalValue();
        }

        // Convert Binary to byte[]
        if (value instanceof Binary binary) {
            return binary.getData();
        }

        // Convert BsonTimestamp to Map
        if (value instanceof BsonTimestamp timestamp) {
            Map<String, Object> timestampMap = new LinkedHashMap<>();
            timestampMap.put("seconds", timestamp.getTime());
            timestampMap.put("increment", timestamp.getInc());
            return timestampMap;
        }

        // Handle nested Documents
        if (value instanceof Document document) {
            return fromDocument(document);
        }

        // Handle lists
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream()
                    .map(BsonTypeConverter::fromBsonValue)
                    .toList();
        }

        // Handle Pattern (regex)
        if (value instanceof Pattern pattern) {
            Map<String, Object> regexMap = new LinkedHashMap<>();
            regexMap.put("pattern", pattern.pattern());
            regexMap.put("flags", pattern.flags());
            return regexMap;
        }

        // Return primitives and strings as-is
        return value;
    }

    /**
     * Convert a BSON value to a Java-friendly value, keeping ObjectId as ObjectId.
     * Used when the caller needs to work with ObjectId directly.
     *
     * @param value The BSON value to convert
     * @return A Java-friendly value with ObjectId preserved
     */
    @SuppressWarnings("unchecked")
    public static Object fromBsonValuePreserveObjectId(Object value) {
        if (value == null) {
            return null;
        }

        // Keep ObjectId as-is
        if (value instanceof ObjectId) {
            return value;
        }

        // Convert Date to Instant
        if (value instanceof Date date) {
            return date.toInstant();
        }

        // Convert Decimal128 to BigDecimal
        if (value instanceof Decimal128 decimal128) {
            return decimal128.bigDecimalValue();
        }

        // Convert Binary to byte[]
        if (value instanceof Binary binary) {
            return binary.getData();
        }

        // Handle nested Documents
        if (value instanceof Document document) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : document.entrySet()) {
                result.put(entry.getKey(), fromBsonValuePreserveObjectId(entry.getValue()));
            }
            return result;
        }

        // Handle lists
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream()
                    .map(BsonTypeConverter::fromBsonValuePreserveObjectId)
                    .toList();
        }

        return value;
    }

    /**
     * Parse an ObjectId from various input formats.
     *
     * @param value The value to parse (String or Map with $oid)
     * @return An ObjectId
     * @throws LyshraOpenAppProcessorRuntimeException if format is invalid
     */
    public static ObjectId parseObjectId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ObjectId objectId) {
            return objectId;
        }
        if (value instanceof String strValue) {
            if (!ObjectId.isValid(strValue)) {
                throw new LyshraOpenAppProcessorRuntimeException(
                        MongoProcessorErrorCodes.INVALID_OBJECT_ID,
                        Map.of("value", strValue)
                );
            }
            return new ObjectId(strValue);
        }
        throw new LyshraOpenAppProcessorRuntimeException(
                MongoProcessorErrorCodes.INVALID_OBJECT_ID,
                Map.of("value", String.valueOf(value))
        );
    }

    /**
     * Parse a Date from various input formats.
     *
     * @param value The value to parse
     * @return A Date
     */
    public static Date parseDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date date) {
            return date;
        }
        if (value instanceof Instant instant) {
            return Date.from(instant);
        }
        if (value instanceof Long timestamp) {
            return new Date(timestamp);
        }
        if (value instanceof String strValue) {
            try {
                return Date.from(Instant.parse(strValue));
            } catch (Exception e) {
                // Try epoch millis
                try {
                    return new Date(Long.parseLong(strValue));
                } catch (NumberFormatException nfe) {
                    throw new LyshraOpenAppProcessorRuntimeException(
                            MongoProcessorErrorCodes.TYPE_CONVERSION_ERROR,
                            Map.of("message", "Cannot parse date: " + strValue)
                    );
                }
            }
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            if (map.containsKey("$numberLong")) {
                return new Date(parseLong(map.get("$numberLong")));
            }
        }
        throw new LyshraOpenAppProcessorRuntimeException(
                MongoProcessorErrorCodes.TYPE_CONVERSION_ERROR,
                Map.of("message", "Cannot convert to Date: " + value.getClass().getName())
        );
    }

    /**
     * Parse a Decimal128 from various input formats.
     *
     * @param value The value to parse
     * @return A Decimal128
     */
    public static Decimal128 parseDecimal128(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Decimal128 decimal128) {
            return decimal128;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return new Decimal128(bigDecimal);
        }
        if (value instanceof String strValue) {
            return Decimal128.parse(strValue);
        }
        if (value instanceof Number number) {
            return new Decimal128(new BigDecimal(number.toString()));
        }
        throw new LyshraOpenAppProcessorRuntimeException(
                MongoProcessorErrorCodes.TYPE_CONVERSION_ERROR,
                Map.of("message", "Cannot convert to Decimal128: " + value.getClass().getName())
        );
    }

    /**
     * Parse a Long from various input formats.
     *
     * @param value The value to parse
     * @return A Long
     */
    public static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String strValue) {
            return Long.parseLong(strValue);
        }
        throw new LyshraOpenAppProcessorRuntimeException(
                MongoProcessorErrorCodes.TYPE_CONVERSION_ERROR,
                Map.of("message", "Cannot convert to Long: " + value.getClass().getName())
        );
    }

    /**
     * Parse an Integer from various input formats.
     *
     * @param value The value to parse
     * @return An Integer
     */
    public static Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer intValue) {
            return intValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String strValue) {
            return Integer.parseInt(strValue);
        }
        throw new LyshraOpenAppProcessorRuntimeException(
                MongoProcessorErrorCodes.TYPE_CONVERSION_ERROR,
                Map.of("message", "Cannot convert to Integer: " + value.getClass().getName())
        );
    }

    /**
     * Parse a Double from various input formats.
     *
     * @param value The value to parse
     * @return A Double
     */
    public static Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double doubleValue) {
            return doubleValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String strValue) {
            return Double.parseDouble(strValue);
        }
        throw new LyshraOpenAppProcessorRuntimeException(
                MongoProcessorErrorCodes.TYPE_CONVERSION_ERROR,
                Map.of("message", "Cannot convert to Double: " + value.getClass().getName())
        );
    }

    /**
     * Parse a regex Pattern from a map containing $regex and optional $options.
     *
     * @param map The map containing regex definition
     * @return A Pattern
     */
    private static Pattern parseRegex(Map<String, Object> map) {
        String pattern = String.valueOf(map.get(REGEX_KEY));
        Object options = map.get("$options");
        int flags = 0;
        if (options != null) {
            String optStr = String.valueOf(options);
            if (optStr.contains("i")) flags |= Pattern.CASE_INSENSITIVE;
            if (optStr.contains("m")) flags |= Pattern.MULTILINE;
            if (optStr.contains("s")) flags |= Pattern.DOTALL;
            if (optStr.contains("u")) flags |= Pattern.UNICODE_CASE;
        }
        return Pattern.compile(pattern, flags);
    }

    /**
     * Parse a BsonTimestamp from various input formats.
     *
     * @param value The value to parse
     * @return A BsonTimestamp
     */
    @SuppressWarnings("unchecked")
    private static BsonTimestamp parseTimestamp(Object value) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            int time = Optional.ofNullable(map.get("t"))
                    .or(() -> Optional.ofNullable(map.get("seconds")))
                    .map(v -> ((Number) v).intValue())
                    .orElse(0);
            int inc = Optional.ofNullable(map.get("i"))
                    .or(() -> Optional.ofNullable(map.get("increment")))
                    .map(v -> ((Number) v).intValue())
                    .orElse(0);
            return new BsonTimestamp(time, inc);
        }
        throw new LyshraOpenAppProcessorRuntimeException(
                MongoProcessorErrorCodes.TYPE_CONVERSION_ERROR,
                Map.of("message", "Cannot convert to BsonTimestamp: " + value.getClass().getName())
        );
    }
}
