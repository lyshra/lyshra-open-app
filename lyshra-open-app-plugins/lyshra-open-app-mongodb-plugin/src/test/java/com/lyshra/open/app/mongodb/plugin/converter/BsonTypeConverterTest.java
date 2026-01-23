package com.lyshra.open.app.mongodb.plugin.converter;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BSON Type Converter.
 */
class BsonTypeConverterTest {

    @Test
    void testToDocument_SimpleMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "John");
        map.put("age", 30);
        map.put("active", true);

        Document doc = BsonTypeConverter.toDocument(map);

        assertNotNull(doc);
        assertEquals("John", doc.getString("name"));
        assertEquals(30, doc.getInteger("age"));
        assertEquals(true, doc.getBoolean("active"));
    }

    @Test
    void testToDocument_NestedMap() {
        Map<String, Object> address = new HashMap<>();
        address.put("city", "New York");
        address.put("zip", "10001");

        Map<String, Object> map = new HashMap<>();
        map.put("name", "John");
        map.put("address", address);

        Document doc = BsonTypeConverter.toDocument(map);

        assertNotNull(doc);
        assertEquals("John", doc.getString("name"));
        assertTrue(doc.get("address") instanceof Document);
        assertEquals("New York", ((Document) doc.get("address")).getString("city"));
    }

    @Test
    void testToDocument_WithObjectIdMarker() {
        Map<String, Object> map = new HashMap<>();
        map.put("_id", Map.of("$oid", "507f1f77bcf86cd799439011"));
        map.put("name", "John");

        Document doc = BsonTypeConverter.toDocument(map);

        assertNotNull(doc);
        assertTrue(doc.get("_id") instanceof ObjectId);
        assertEquals("507f1f77bcf86cd799439011", ((ObjectId) doc.get("_id")).toHexString());
    }

    @Test
    void testToDocument_WithDateMarker() {
        long timestamp = System.currentTimeMillis();
        Map<String, Object> map = new HashMap<>();
        map.put("createdAt", Map.of("$date", timestamp));

        Document doc = BsonTypeConverter.toDocument(map);

        assertNotNull(doc);
        assertTrue(doc.get("createdAt") instanceof Date);
        assertEquals(timestamp, ((Date) doc.get("createdAt")).getTime());
    }

    @Test
    void testToDocument_WithDecimalMarker() {
        Map<String, Object> map = new HashMap<>();
        map.put("amount", Map.of("$numberDecimal", "123.456"));

        Document doc = BsonTypeConverter.toDocument(map);

        assertNotNull(doc);
        assertTrue(doc.get("amount") instanceof Decimal128);
    }

    @Test
    void testFromDocument_SimpleDocument() {
        Document doc = new Document()
                .append("name", "John")
                .append("age", 30)
                .append("active", true);

        Map<String, Object> map = BsonTypeConverter.fromDocument(doc);

        assertNotNull(map);
        assertEquals("John", map.get("name"));
        assertEquals(30, map.get("age"));
        assertEquals(true, map.get("active"));
    }

    @Test
    void testFromDocument_WithObjectId() {
        ObjectId objectId = new ObjectId("507f1f77bcf86cd799439011");
        Document doc = new Document()
                .append("_id", objectId)
                .append("name", "John");

        Map<String, Object> map = BsonTypeConverter.fromDocument(doc);

        assertNotNull(map);
        assertEquals("507f1f77bcf86cd799439011", map.get("_id"));
        assertEquals("John", map.get("name"));
    }

    @Test
    void testFromDocument_WithDate() {
        Date date = new Date();
        Document doc = new Document()
                .append("createdAt", date)
                .append("name", "John");

        Map<String, Object> map = BsonTypeConverter.fromDocument(doc);

        assertNotNull(map);
        assertTrue(map.get("createdAt") instanceof Instant);
    }

    @Test
    void testFromDocument_WithDecimal128() {
        Decimal128 decimal = Decimal128.parse("123.456");
        Document doc = new Document()
                .append("amount", decimal)
                .append("name", "John");

        Map<String, Object> map = BsonTypeConverter.fromDocument(doc);

        assertNotNull(map);
        assertTrue(map.get("amount") instanceof BigDecimal);
        assertEquals(new BigDecimal("123.456"), map.get("amount"));
    }

    @Test
    void testFromDocument_NestedDocument() {
        Document address = new Document()
                .append("city", "New York")
                .append("zip", "10001");

        Document doc = new Document()
                .append("name", "John")
                .append("address", address);

        Map<String, Object> map = BsonTypeConverter.fromDocument(doc);

        assertNotNull(map);
        assertEquals("John", map.get("name"));
        assertTrue(map.get("address") instanceof Map);
        assertEquals("New York", ((Map<?, ?>) map.get("address")).get("city"));
    }

    @Test
    void testToDocuments() {
        List<Map<String, Object>> maps = List.of(
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );

        List<Document> docs = BsonTypeConverter.toDocuments(maps);

        assertNotNull(docs);
        assertEquals(2, docs.size());
        assertEquals("Alice", docs.get(0).getString("name"));
        assertEquals("Bob", docs.get(1).getString("name"));
    }

    @Test
    void testFromDocuments() {
        List<Document> docs = List.of(
                new Document("name", "Alice"),
                new Document("name", "Bob")
        );

        List<Map<String, Object>> maps = BsonTypeConverter.fromDocuments(docs);

        assertNotNull(maps);
        assertEquals(2, maps.size());
        assertEquals("Alice", maps.get(0).get("name"));
        assertEquals("Bob", maps.get(1).get("name"));
    }

    @Test
    void testParseObjectId_FromString() {
        String hexString = "507f1f77bcf86cd799439011";
        ObjectId objectId = BsonTypeConverter.parseObjectId(hexString);

        assertNotNull(objectId);
        assertEquals(hexString, objectId.toHexString());
    }

    @Test
    void testParseObjectId_FromObjectId() {
        ObjectId original = new ObjectId("507f1f77bcf86cd799439011");
        ObjectId parsed = BsonTypeConverter.parseObjectId(original);

        assertSame(original, parsed);
    }

    @Test
    void testNullHandling() {
        assertNull(BsonTypeConverter.toDocument(null));
        assertNull(BsonTypeConverter.fromDocument(null));
        assertTrue(BsonTypeConverter.toDocuments(null).isEmpty());
        assertTrue(BsonTypeConverter.fromDocuments(null).isEmpty());
    }

    @Test
    void testListConversion() {
        Map<String, Object> map = new HashMap<>();
        map.put("tags", List.of("java", "mongodb", "reactive"));

        Document doc = BsonTypeConverter.toDocument(map);

        assertNotNull(doc);
        assertTrue(doc.get("tags") instanceof List);
        List<?> tags = (List<?>) doc.get("tags");
        assertEquals(3, tags.size());
        assertTrue(tags.contains("java"));
    }
}
