package com.genymobile.scrcpy.model;

import org.junit.Assert;
import org.junit.Test;

public class LaunchQueryTest {

    @Test
    public void testPackage() {
        LaunchQuery q = LaunchQuery.parse("com.example.app");
        Assert.assertEquals("com.example.app", q.getTerm());
        Assert.assertEquals(LaunchQuery.MatchMode.PACKAGE, q.getMatchMode());
        Assert.assertEquals(LaunchQuery.Scope.ANY, q.getScope());
        Assert.assertFalse(q.isForceStop());
        Assert.assertFalse(q.isDryRun());
    }

    @Test
    public void testNamePrefix() {
        LaunchQuery q = LaunchQuery.parse("?firefox");
        Assert.assertEquals("firefox", q.getTerm());
        Assert.assertEquals(LaunchQuery.MatchMode.NAME_PREFIX, q.getMatchMode());
    }

    @Test
    public void testNameExact() {
        LaunchQuery q = LaunchQuery.parse("?=Firefox");
        Assert.assertEquals("Firefox", q.getTerm());
        Assert.assertEquals(LaunchQuery.MatchMode.NAME_EXACT, q.getMatchMode());
    }

    @Test
    public void testForceStopPackage() {
        LaunchQuery q = LaunchQuery.parse("+com.example.app");
        Assert.assertEquals("com.example.app", q.getTerm());
        Assert.assertEquals(LaunchQuery.MatchMode.PACKAGE, q.getMatchMode());
        Assert.assertTrue(q.isForceStop());
    }

    @Test
    public void testForceStopName() {
        // Backward compatibility: the documented "+?" combination
        LaunchQuery q = LaunchQuery.parse("+?firefox");
        Assert.assertEquals("firefox", q.getTerm());
        Assert.assertEquals(LaunchQuery.MatchMode.NAME_PREFIX, q.getMatchMode());
        Assert.assertTrue(q.isForceStop());
        Assert.assertFalse(q.isDryRun());
    }

    @Test
    public void testDryRun() {
        LaunchQuery q = LaunchQuery.parse("!com.example.app");
        Assert.assertTrue(q.isDryRun());
        Assert.assertFalse(q.isForceStop());
        Assert.assertEquals(LaunchQuery.MatchMode.PACKAGE, q.getMatchMode());
    }

    @Test
    public void testAllFlagsForwardOrder() {
        LaunchQuery q = LaunchQuery.parse("+!?=Maps");
        Assert.assertTrue(q.isForceStop());
        Assert.assertTrue(q.isDryRun());
        Assert.assertEquals(LaunchQuery.MatchMode.NAME_EXACT, q.getMatchMode());
        Assert.assertEquals("Maps", q.getTerm());
    }

    @Test
    public void testFlagsAnyOrder() {
        LaunchQuery q = LaunchQuery.parse("!+?Maps");
        Assert.assertTrue(q.isForceStop());
        Assert.assertTrue(q.isDryRun());
        Assert.assertEquals(LaunchQuery.MatchMode.NAME_PREFIX, q.getMatchMode());
        Assert.assertEquals("Maps", q.getTerm());
    }

    @Test
    public void testEmpty() {
        LaunchQuery q = LaunchQuery.parse("");
        Assert.assertEquals("", q.getTerm());
        Assert.assertEquals(LaunchQuery.MatchMode.PACKAGE, q.getMatchMode());
        Assert.assertFalse(q.isForceStop());
        Assert.assertFalse(q.isDryRun());
    }

    @Test
    public void testFlagCharsOnlyConsumedBeforeMatchSpec() {
        // A '+' that appears after the '?' is part of the term, not a flag.
        LaunchQuery q = LaunchQuery.parse("?+weird");
        Assert.assertEquals("+weird", q.getTerm());
        Assert.assertEquals(LaunchQuery.MatchMode.NAME_PREFIX, q.getMatchMode());
        Assert.assertFalse(q.isForceStop());
    }

    @Test
    public void testToStringRoundTripsFlags() {
        Assert.assertEquals("+!?=Maps", LaunchQuery.parse("+!?=Maps").toString());
        Assert.assertEquals("com.example.app", LaunchQuery.parse("com.example.app").toString());
        Assert.assertEquals("?firefox", LaunchQuery.parse("?firefox").toString());
    }
}
