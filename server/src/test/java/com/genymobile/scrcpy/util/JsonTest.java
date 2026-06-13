package com.genymobile.scrcpy.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonTest {

    @Test
    public void testPrimitives() {
        Assert.assertEquals("null", Json.encode(null));
        Assert.assertEquals("true", Json.encode(true));
        Assert.assertEquals("false", Json.encode(false));
        Assert.assertEquals("42", Json.encode(42));
        Assert.assertEquals("42", Json.encode(42L));
        Assert.assertEquals("-7", Json.encode(-7));
    }

    @Test
    public void testFloats() {
        Assert.assertEquals("1.0", Json.encode(1.0f));
        Assert.assertEquals("8.0", Json.encode(8.0f));
        Assert.assertEquals("2.5", Json.encode(2.5));
        // JSON cannot represent these, they must become null
        Assert.assertEquals("null", Json.encode(Float.NaN));
        Assert.assertEquals("null", Json.encode(Double.POSITIVE_INFINITY));
        Assert.assertEquals("null", Json.encode(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void testStringBasic() {
        Assert.assertEquals("\"hello\"", Json.encode("hello"));
        Assert.assertEquals("\"\"", Json.encode(""));
        // Forward slash is not escaped, non-ASCII is passed through verbatim
        Assert.assertEquals("\"a/b\"", Json.encode("a/b"));
        Assert.assertEquals("\"é\"", Json.encode("é"));
    }

    @Test
    public void testStringEscaping() {
        Assert.assertEquals("\"\\\"\"", Json.encode("\""));   // " -> \"
        Assert.assertEquals("\"\\\\\"", Json.encode("\\"));   // \ -> \\
        Assert.assertEquals("\"\\n\"", Json.encode("\n"));
        Assert.assertEquals("\"\\r\"", Json.encode("\r"));
        Assert.assertEquals("\"\\t\"", Json.encode("\t"));
        Assert.assertEquals("\"\\b\"", Json.encode("\b"));
        Assert.assertEquals("\"\\f\"", Json.encode("\f"));
        // Other control characters use the \\uXXXX form (inputs built without literal control chars)
        Assert.assertEquals("\"\\u0001\"", Json.encode(String.valueOf((char) 1)));
        Assert.assertEquals("\"\\u001f\"", Json.encode(String.valueOf((char) 0x1f)));
    }

    @Test
    public void testArray() {
        Assert.assertEquals("[]", Json.encode(new ArrayList<>()));
        Assert.assertEquals("[1,2,3]", Json.encode(Arrays.asList(1, 2, 3)));
        Assert.assertEquals("[\"a\",\"b\"]", Json.encode(Arrays.asList("a", "b")));
        Assert.assertEquals("[1,null,true]", Json.encode(Arrays.asList(1, null, true)));
    }

    @Test
    public void testObjectPreservesInsertionOrder() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("b", 1);
        map.put("a", 2);
        Assert.assertEquals("{\"b\":1,\"a\":2}", Json.encode(map));
    }

    @Test
    public void testEmptyObject() {
        Assert.assertEquals("{}", Json.encode(new LinkedHashMap<>()));
    }

    @Test
    public void testNested() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("x", 1);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("list", Arrays.asList(inner));
        root.put("flag", false);
        Assert.assertEquals("{\"list\":[{\"x\":1}],\"flag\":false}", Json.encode(root));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnsupportedTypeThrows() {
        Json.encode(new Object());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNonStringKeyThrows() {
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put(1, "value");
        Json.encode(map);
    }
}
