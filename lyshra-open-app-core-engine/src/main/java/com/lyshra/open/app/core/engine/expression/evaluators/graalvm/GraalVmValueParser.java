package com.lyshra.open.app.core.engine.expression.evaluators.graalvm;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.time.Instant;
import java.util.*;

/**
 * Robust GraalVM Value → Java materializer.
 *
 * Handles:
 *  - Polyglot Value boundary
 *  - Host objects
 *  - Mixed object graphs
 *  - Temporal types
 *  - ProxyObject / ProxyArray
 */
@Slf4j
public final class GraalVmValueParser {

    private GraalVmValueParser() {}

    /* ============================================================
     * ENTRY POINT
     * ============================================================ */

    public static Object parseResult(Value value, ILyshraOpenAppPluginFacade facade) {
        log.trace("parseResult(Value) called");

        if (value == null) {
            log.warn("Value is null (Java null)");
            return null;
        }

        if (value.isNull()) {
            log.trace("Value is polyglot null");
            return null;
        }

        /* ---------- primitives ---------- */

        if (value.isBoolean()) {
            boolean b = value.asBoolean();
            log.trace("Boolean value: {}", b);
            return b;
        }

        if (value.isNumber()) {
            log.trace("Number value");
            if (value.fitsInInt()) return value.asInt();
            if (value.fitsInLong()) return value.asLong();
            if (value.fitsInBigInteger()) return value.asBigInteger();
            if (value.fitsInDouble()) return value.asDouble();
            return value.as(Number.class);
        }

        if (value.isString()) {
            String s = value.asString();
            log.trace("String value: {}", s);
            return s;
        }

        /* ---------- temporal ---------- */

        if (value.isInstant()) {
            Instant i = value.asInstant();
            log.trace("Instant value: {}", i);
            return i;
        }

        if (value.isDuration()) {
            log.trace("Duration value: {}", value.asDuration());
            return value.asDuration();
        }

        if (value.isDate()) {
            Instant i = Instant.from(value.asDate());
            log.trace("Date value → Instant: {}", i);
            return i;
        }

        if (value.isTimeZone()) {
            log.trace("TimeZone value: {}", value.asTimeZone());
            return value.asTimeZone();
        }

        /* ---------- host object ---------- */

        if (value.isHostObject()) {
            Object host = value.asHostObject();
            log.trace("Host object detected: {}", host.getClass().getName());
            return parseObject(host);
        }

        /* ---------- arrays ---------- */

        if (value.hasArrayElements()) {
            int size = (int) value.getArraySize();
            log.trace("Array detected, size={}", size);

            List<Object> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                log.trace("Parsing array element [{}]", i);
                list.add(parseResult(value.getArrayElement(i), facade));
            }
            return list;
        }

        /* ---------- objects ---------- */

        if (value.hasMembers()) {
            log.trace("Object with members detected");

            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                log.trace("Parsing object key='{}'", key);
                map.put(key, parseResult(value.getMember(key), facade));
            }
            return map;
        }

        /* ---------- fallback ---------- */

        log.warn("Unknown Value type: {} → toString()", value);
        return value.toString();
    }

    /* ============================================================
     * HOST OBJECT GRAPH PARSER
     * ============================================================ */

    private static Object parseObject(Object obj) {
        if (obj == null) {
            log.trace("parseObject: null");
            return null;
        }

        log.trace("parseObject: {}", obj.getClass().getName());

        /* ---------- atomic Java types ---------- */

        if (obj instanceof String
                || obj instanceof Number
                || obj instanceof Boolean
                || obj instanceof Instant) {
            log.trace("Atomic Java type detected: {}", obj);
            return obj;
        }

        /* ---------- Java Iterable ---------- */

        if (obj instanceof Iterable<?> iterable) {
            log.trace("Iterable detected");
            List<Object> list = new ArrayList<>();
            int idx = 0;

            for (Object item : iterable) {
                log.trace("Iterable element [{}]: {}", idx++, classOf(item));
                list.add(parseObject(item));
            }
            return list;
        }

        /* ---------- Java Map ---------- */

        if (obj instanceof Map<?, ?> map) {
            log.trace("Map detected, size={}", map.size());
            Map<Object, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object k = entry.getKey();
                Object v = entry.getValue();

                log.trace("Map entry key={}, value={}",
                        classOf(k), classOf(v));

                result.put(parseObject(k), parseObject(v));
            }
            return result;
        }

        /* ---------- Polyglot proxy leaked into host ---------- */

        if (obj instanceof ProxyObject || obj instanceof ProxyArray) {
            log.trace("Polyglot proxy detected, re-wrapping as Value");
            return parseResult(Value.asValue(obj), null);
        }

        /* ---------- fallback ---------- */

        log.trace("Returning host object as-is");
        return obj;
    }

    /* ============================================================
     * UTIL
     * ============================================================ */

    private static String classOf(Object o) {
        return o == null ? "null" : o.getClass().getName();
    }
}