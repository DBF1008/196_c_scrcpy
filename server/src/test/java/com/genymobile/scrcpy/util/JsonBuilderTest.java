package com.genymobile.scrcpy.util;

import org.junit.Assert;
import org.junit.Test;

public class JsonBuilderTest {

    @Test
    public void testEmptyObject() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject().endObject();
        Assert.assertEquals("{}", jb.toString());
    }

    @Test
    public void testStringField() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject().key("key").value("value").endObject();
        Assert.assertEquals("{\"key\":\"value\"}", jb.toString());
    }

    @Test
    public void testNullValue() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject().key("key").value((String) null).endObject();
        Assert.assertEquals("{\"key\":null}", jb.toString());
    }

    @Test
    public void testIntAndBoolean() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject()
                .key("n").value(42)
                .key("b").value(true)
                .endObject();
        Assert.assertEquals("{\"n\":42,\"b\":true}", jb.toString());
    }

    @Test
    public void testNestedObject() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject()
                .key("outer")
                .beginObject()
                .key("inner").value("val")
                .endObject()
                .endObject();
        Assert.assertEquals("{\"outer\":{\"inner\":\"val\"}}", jb.toString());
    }

    @Test
    public void testArray() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject()
                .key("a")
                .beginArray()
                .value(1)
                .value(2)
                .value(3)
                .endArray()
                .endObject();
        Assert.assertEquals("{\"a\":[1,2,3]}", jb.toString());
    }

    @Test
    public void testStringEscaping() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject()
                .key("q").value("say \"hello\"")
                .key("bs").value("back\\slash")
                .key("nl").value("line1\nline2")
                .key("tab").value("col1\tcol2")
                .endObject();
        Assert.assertEquals("{\"q\":\"say \\\"hello\\\"\",\"bs\":\"back\\\\slash\",\"nl\":\"line1\\nline2\",\"tab\":\"col1\\tcol2\"}",
                jb.toString());
    }

    @Test
    public void testIntArray() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject()
                .key("fps").value(new int[]{30, 60})
                .endObject();
        Assert.assertEquals("{\"fps\":[30,60]}", jb.toString());
    }

    @Test
    public void testMultipleKeysNoTrailingComma() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject()
                .key("a").value(1)
                .key("b").value(2)
                .key("c").value(3)
                .endObject();
        Assert.assertEquals("{\"a\":1,\"b\":2,\"c\":3}", jb.toString());
        // Verify no trailing comma
        Assert.assertFalse(jb.toString().contains(",}"));
    }

    @Test
    public void testEmptyArray() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject()
                .key("items")
                .beginArray()
                .endArray()
                .endObject();
        Assert.assertEquals("{\"items\":[]}", jb.toString());
    }

    @Test
    public void testNullValueMethod() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject()
                .key("x").nullValue()
                .endObject();
        Assert.assertEquals("{\"x\":null}", jb.toString());
    }

    @Test
    public void testEscapeControlChars() {
        // Test control character escaping for chars < 0x20
        String escaped = JsonBuilder.escape("");
        Assert.assertEquals("\\u0001\\u001f", escaped);
    }

    @Test
    public void testFloatValue() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject()
                .key("f").value(1.5f)
                .endObject();
        Assert.assertEquals("{\"f\":1.5}", jb.toString());
    }

    @Test
    public void testLongValue() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject()
                .key("l").value(100000000000L)
                .endObject();
        Assert.assertEquals("{\"l\":100000000000}", jb.toString());
    }

    @Test
    public void testArrayOfStrings() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject()
                .key("tags")
                .beginArray()
                .value("a")
                .value("b")
                .endArray()
                .endObject();
        Assert.assertEquals("{\"tags\":[\"a\",\"b\"]}", jb.toString());
    }

    @Test
    public void testArrayOfObjects() {
        JsonBuilder jb = new JsonBuilder();
        jb.beginObject()
                .key("items")
                .beginArray()
                .beginObject().key("id").value(1).endObject()
                .beginObject().key("id").value(2).endObject()
                .endArray()
                .endObject();
        Assert.assertEquals("{\"items\":[{\"id\":1},{\"id\":2}]}", jb.toString());
    }
}
