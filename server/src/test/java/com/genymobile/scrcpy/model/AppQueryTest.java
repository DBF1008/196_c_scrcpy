package com.genymobile.scrcpy.model;

import org.junit.Assert;
import org.junit.Test;

public class AppQueryTest {

    @Test
    public void testPlainPackageName() {
        AppQuery query = AppQuery.parse("com.termux");
        Assert.assertEquals("com.termux", query.getTarget());
        Assert.assertFalse(query.isForceStop());
        Assert.assertFalse(query.isNameSearch());
        Assert.assertFalse(query.isDryRun());
        Assert.assertEquals(AppQuery.DISPLAY_DEFAULT, query.getDisplayId());
    }

    @Test
    public void testForceStopPrefix() {
        AppQuery query = AppQuery.parse("+com.termux");
        Assert.assertEquals("com.termux", query.getTarget());
        Assert.assertTrue(query.isForceStop());
        Assert.assertFalse(query.isNameSearch());
        Assert.assertFalse(query.isDryRun());
        Assert.assertEquals(AppQuery.DISPLAY_DEFAULT, query.getDisplayId());
    }

    @Test
    public void testNameSearchPrefix() {
        AppQuery query = AppQuery.parse("?firefox");
        Assert.assertEquals("firefox", query.getTarget());
        Assert.assertFalse(query.isForceStop());
        Assert.assertTrue(query.isNameSearch());
        Assert.assertFalse(query.isDryRun());
    }

    @Test
    public void testDryRunPrefix() {
        AppQuery query = AppQuery.parse("~com.termux");
        Assert.assertEquals("com.termux", query.getTarget());
        Assert.assertFalse(query.isForceStop());
        Assert.assertFalse(query.isNameSearch());
        Assert.assertTrue(query.isDryRun());
    }

    @Test
    public void testCombinedFlags() {
        AppQuery query = AppQuery.parse("+~?fire@2");
        Assert.assertEquals("fire", query.getTarget());
        Assert.assertTrue(query.isForceStop());
        Assert.assertTrue(query.isDryRun());
        Assert.assertTrue(query.isNameSearch());
        Assert.assertEquals(2, query.getDisplayId());
    }

    @Test
    public void testFlagOrderDoesNotMatter() {
        AppQuery q1 = AppQuery.parse("+~com.app");
        AppQuery q2 = AppQuery.parse("~+com.app");
        Assert.assertEquals(q1.getTarget(), q2.getTarget());
        Assert.assertEquals(q1.isForceStop(), q2.isForceStop());
        Assert.assertEquals(q1.isDryRun(), q2.isDryRun());
    }

    @Test
    public void testAllFlagsReversedOrder() {
        AppQuery query = AppQuery.parse("~?+com.app@0");
        Assert.assertEquals("com.app", query.getTarget());
        Assert.assertTrue(query.isDryRun());
        Assert.assertTrue(query.isNameSearch());
        Assert.assertTrue(query.isForceStop());
        Assert.assertEquals(0, query.getDisplayId());
    }

    @Test
    public void testDisplayIdZero() {
        AppQuery query = AppQuery.parse("com.app@0");
        Assert.assertEquals("com.app", query.getTarget());
        Assert.assertEquals(0, query.getDisplayId());
    }

    @Test
    public void testDisplayIdLarge() {
        AppQuery query = AppQuery.parse("com.app@123");
        Assert.assertEquals("com.app", query.getTarget());
        Assert.assertEquals(123, query.getDisplayId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDuplicateForceStopFlag() {
        AppQuery.parse("++com.app");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDuplicateNameSearchFlag() {
        AppQuery.parse("??com.app");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDuplicateDryRunFlag() {
        AppQuery.parse("~~com.app");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyTargetAfterFlags() {
        AppQuery.parse("+");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyString() {
        AppQuery.parse("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        AppQuery.parse(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNonNumericDisplayId() {
        AppQuery.parse("app@abc");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyDisplayId() {
        AppQuery.parse("app@");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnlyFlagsNoTarget() {
        AppQuery.parse("+~?@1");
    }

    @Test
    public void testForceStopAndNameSearch() {
        AppQuery query = AppQuery.parse("+?firefox");
        Assert.assertEquals("firefox", query.getTarget());
        Assert.assertTrue(query.isForceStop());
        Assert.assertTrue(query.isNameSearch());
        Assert.assertFalse(query.isDryRun());
    }

    @Test
    public void testDisplayIdNegative() {
        AppQuery query = AppQuery.parse("com.app@-1");
        Assert.assertEquals("com.app", query.getTarget());
        Assert.assertEquals(-1, query.getDisplayId());
    }

    @Test
    public void testTargetWithDots() {
        AppQuery query = AppQuery.parse("com.example.my.app");
        Assert.assertEquals("com.example.my.app", query.getTarget());
    }

    @Test
    public void testTargetWithSpaces() {
        // Name search with spaces
        AppQuery query = AppQuery.parse("?My App Name");
        Assert.assertEquals("My App Name", query.getTarget());
        Assert.assertTrue(query.isNameSearch());
    }

    @Test
    public void testToStringContainsTarget() {
        AppQuery query = AppQuery.parse("+~?fire@2");
        String str = query.toString();
        Assert.assertTrue(str.contains("fire"));
        Assert.assertTrue(str.contains("forceStop"));
        Assert.assertTrue(str.contains("nameSearch"));
        Assert.assertTrue(str.contains("dryRun"));
        Assert.assertTrue(str.contains("display=2"));
    }
}
