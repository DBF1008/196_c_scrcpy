package com.genymobile.scrcpy.control;

import org.junit.Assert;
import org.junit.Test;

public class StartAppParserTest {

    private static void assertParsed(String raw, boolean forceStop, boolean searchByName, String name) {
        StartAppParser parsed = StartAppParser.parse(raw);
        Assert.assertEquals("forceStop for \"" + raw + "\"", forceStop, parsed.isForceStop());
        Assert.assertEquals("searchByName for \"" + raw + "\"", searchByName, parsed.isSearchByName());
        Assert.assertEquals("name for \"" + raw + "\"", name, parsed.getName());
    }

    @Test
    public void testPlainPackageName() {
        assertParsed("org.mozilla.firefox", false, false, "org.mozilla.firefox");
    }

    @Test
    public void testForceStop() {
        assertParsed("+org.mozilla.firefox", true, false, "org.mozilla.firefox");
    }

    @Test
    public void testSearchByName() {
        assertParsed("?Firefox", false, true, "Firefox");
    }

    @Test
    public void testForceStopAndSearchByName() {
        assertParsed("+?Firefox", true, true, "Firefox");
    }

    @Test
    public void testPrefixOrderMatters() {
        // '+' is only consumed as the very first char, so here it stays part of the name
        assertParsed("?+weird", false, true, "+weird");
    }

    @Test
    public void testDoublePlusKeepsSecondPlusInName() {
        assertParsed("++a", true, false, "+a");
    }

    @Test
    public void testEmptyName() {
        assertParsed("", false, false, "");
    }
}
