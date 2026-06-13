package com.genymobile.scrcpy.util;

import java.util.List;
import java.util.Map;

/**
 * Minimal JSON encoder (no external dependency).
 * <p>
 * Encodes a tree composed of:
 * <ul>
 *     <li>{@link Map} (with {@link String} keys) → JSON object (insertion order is preserved when a {@link java.util.LinkedHashMap}
 *     is used);</li>
 *     <li>{@link List} → JSON array;</li>
 *     <li>{@link String} → JSON string (escaped per RFC 8259);</li>
 *     <li>{@link Number} → JSON number ({@code NaN} and infinities, which JSON cannot represent, are encoded as {@code null});</li>
 *     <li>{@link Boolean} → {@code true}/{@code false};</li>
 *     <li>{@code null} → {@code null}.</li>
 * </ul>
 */
public final class Json {

    private Json() {
        // not instantiable
    }

    public static String encode(Object value) {
        StringBuilder builder = new StringBuilder();
        append(builder, value);
        return builder.toString();
    }

    private static void append(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String) {
            appendString(builder, (String) value);
        } else if (value instanceof Boolean) {
            builder.append(((Boolean) value).booleanValue() ? "true" : "false");
        } else if (value instanceof Number) {
            appendNumber(builder, (Number) value);
        } else if (value instanceof Map) {
            appendObject(builder, (Map<?, ?>) value);
        } else if (value instanceof List) {
            appendArray(builder, (List<?>) value);
        } else {
            throw new IllegalArgumentException("Cannot encode value of type " + value.getClass().getName() + " to JSON");
        }
    }

    private static void appendObject(StringBuilder builder, Map<?, ?> map) {
        builder.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("JSON object keys must be strings");
            }
            if (!first) {
                builder.append(',');
            } else {
                first = false;
            }
            appendString(builder, (String) key);
            builder.append(':');
            append(builder, entry.getValue());
        }
        builder.append('}');
    }

    private static void appendArray(StringBuilder builder, List<?> list) {
        builder.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                builder.append(',');
            } else {
                first = false;
            }
            append(builder, item);
        }
        builder.append(']');
    }

    private static void appendNumber(StringBuilder builder, Number number) {
        if (number instanceof Float || number instanceof Double) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                // JSON cannot represent these values
                builder.append("null");
                return;
            }
        }
        // Number.toString() is locale-independent (always uses '.' as decimal separator)
        builder.append(number.toString());
    }

    private static void appendString(StringBuilder builder, String s) {
        builder.append('"');
        int length = s.length();
        for (int i = 0; i < length; ++i) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                    break;
            }
        }
        builder.append('"');
    }
}
