package com.genymobile.scrcpy.util;

/**
 * Minimal hand-rolled JSON serializer using StringBuilder.
 * <p>
 * No external dependencies. Handles RFC 8259 string escaping.
 */
public final class JsonBuilder {

    private final StringBuilder sb = new StringBuilder();
    private boolean needsComma;

    public JsonBuilder beginObject() {
        appendCommaIfNeeded();
        sb.append('{');
        needsComma = false;
        return this;
    }

    public JsonBuilder endObject() {
        sb.append('}');
        needsComma = true;
        return this;
    }

    public JsonBuilder beginArray() {
        appendCommaIfNeeded();
        sb.append('[');
        needsComma = false;
        return this;
    }

    public JsonBuilder endArray() {
        sb.append(']');
        needsComma = true;
        return this;
    }

    public JsonBuilder key(String name) {
        appendCommaIfNeeded();
        sb.append('"').append(escape(name)).append("\":");
        needsComma = false;
        return this;
    }

    public JsonBuilder value(String s) {
        appendCommaIfNeeded();
        if (s == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escape(s)).append('"');
        }
        needsComma = true;
        return this;
    }

    public JsonBuilder value(int n) {
        appendCommaIfNeeded();
        sb.append(n);
        needsComma = true;
        return this;
    }

    public JsonBuilder value(long n) {
        appendCommaIfNeeded();
        sb.append(n);
        needsComma = true;
        return this;
    }

    public JsonBuilder value(float f) {
        appendCommaIfNeeded();
        sb.append(f);
        needsComma = true;
        return this;
    }

    public JsonBuilder value(boolean b) {
        appendCommaIfNeeded();
        sb.append(b);
        needsComma = true;
        return this;
    }

    public JsonBuilder value(int[] arr) {
        appendCommaIfNeeded();
        sb.append('[');
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(arr[i]);
        }
        sb.append(']');
        needsComma = true;
        return this;
    }

    public JsonBuilder nullValue() {
        appendCommaIfNeeded();
        sb.append("null");
        needsComma = true;
        return this;
    }

    private void appendCommaIfNeeded() {
        if (needsComma) {
            sb.append(',');
        }
    }

    /**
     * Escape a string per RFC 8259 section 7.
     */
    static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '/':
                    out.append("\\/");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else if (Character.isHighSurrogate(c)) {
                        // Surrogate pair: write both chars as-is
                        out.append(c);
                        if (i + 1 < s.length()) {
                            out.append(s.charAt(++i));
                        }
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
