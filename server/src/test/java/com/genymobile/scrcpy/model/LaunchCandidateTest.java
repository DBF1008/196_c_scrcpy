package com.genymobile.scrcpy.model;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LaunchCandidateTest {

    private static DeviceApp app(String pkg, String name, boolean system) {
        return new DeviceApp(pkg, name, system);
    }

    @Test
    public void testExactPackageBeforePrefixName() {
        LaunchCandidate exact = new LaunchCandidate(app("com.exact", "Exact", false),
                LaunchCandidate.MatchType.EXACT_PACKAGE);
        LaunchCandidate prefix = new LaunchCandidate(app("com.prefix", "Prefix", false),
                LaunchCandidate.MatchType.PREFIX_NAME);
        Assert.assertTrue(exact.compareTo(prefix) < 0);
        Assert.assertTrue(prefix.compareTo(exact) > 0);
    }

    @Test
    public void testExactPackageBeforeExactName() {
        LaunchCandidate pkg = new LaunchCandidate(app("com.exact", "Exact", false),
                LaunchCandidate.MatchType.EXACT_PACKAGE);
        LaunchCandidate name = new LaunchCandidate(app("com.named", "Exact", false),
                LaunchCandidate.MatchType.EXACT_NAME);
        Assert.assertTrue(pkg.compareTo(name) < 0);
    }

    @Test
    public void testExactNameBeforePrefixName() {
        LaunchCandidate exact = new LaunchCandidate(app("com.exact", "Firefox", false),
                LaunchCandidate.MatchType.EXACT_NAME);
        LaunchCandidate prefix = new LaunchCandidate(app("com.prefix", "Firefox Beta", false),
                LaunchCandidate.MatchType.PREFIX_NAME);
        Assert.assertTrue(exact.compareTo(prefix) < 0);
    }

    @Test
    public void testUserAppBeforeSystemApp() {
        LaunchCandidate user = new LaunchCandidate(app("com.user", "Maps", false),
                LaunchCandidate.MatchType.PREFIX_NAME);
        LaunchCandidate system = new LaunchCandidate(app("com.system", "Maps", true),
                LaunchCandidate.MatchType.PREFIX_NAME);
        Assert.assertTrue(user.compareTo(system) < 0);
    }

    @Test
    public void testAlphabeticalTiebreak() {
        LaunchCandidate alpha = new LaunchCandidate(app("com.alpha", "Alpha", false),
                LaunchCandidate.MatchType.PREFIX_NAME);
        LaunchCandidate beta = new LaunchCandidate(app("com.beta", "Beta", false),
                LaunchCandidate.MatchType.PREFIX_NAME);
        Assert.assertTrue(alpha.compareTo(beta) < 0);
    }

    @Test
    public void testPackageNameTiebreak() {
        LaunchCandidate first = new LaunchCandidate(app("com.a.app", "Same", false),
                LaunchCandidate.MatchType.PREFIX_NAME);
        LaunchCandidate second = new LaunchCandidate(app("com.b.app", "Same", false),
                LaunchCandidate.MatchType.PREFIX_NAME);
        Assert.assertTrue(first.compareTo(second) < 0);
    }

    @Test
    public void testSameCandidateEqualsZero() {
        LaunchCandidate c = new LaunchCandidate(app("com.app", "App", false),
                LaunchCandidate.MatchType.EXACT_PACKAGE);
        Assert.assertEquals(0, c.compareTo(c));
    }

    @Test
    public void testEqualsAndHashCode() {
        LaunchCandidate c1 = new LaunchCandidate(app("com.app", "App", false),
                LaunchCandidate.MatchType.EXACT_PACKAGE);
        LaunchCandidate c2 = new LaunchCandidate(app("com.app", "App Different Label", true),
                LaunchCandidate.MatchType.EXACT_PACKAGE);
        Assert.assertEquals(c1, c2);
        Assert.assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    public void testNotEquals() {
        LaunchCandidate c1 = new LaunchCandidate(app("com.app1", "App", false),
                LaunchCandidate.MatchType.EXACT_PACKAGE);
        LaunchCandidate c2 = new LaunchCandidate(app("com.app2", "App", false),
                LaunchCandidate.MatchType.EXACT_PACKAGE);
        Assert.assertNotEquals(c1, c2);
    }

    @Test
    public void testSortStability() {
        // Same priority items should maintain relative order when sorted
        LaunchCandidate a = new LaunchCandidate(app("com.a", "Zebra", false),
                LaunchCandidate.MatchType.PREFIX_NAME);
        LaunchCandidate b = new LaunchCandidate(app("com.b", "Apple", false),
                LaunchCandidate.MatchType.PREFIX_NAME);
        LaunchCandidate c = new LaunchCandidate(app("com.c", "Mango", false),
                LaunchCandidate.MatchType.PREFIX_NAME);

        List<LaunchCandidate> list = new ArrayList<>();
        list.add(a);
        list.add(b);
        list.add(c);
        Collections.sort(list);

        // After sorting by name: Apple, Mango, Zebra
        Assert.assertEquals("Apple", list.get(0).getApp().getName());
        Assert.assertEquals("Mango", list.get(1).getApp().getName());
        Assert.assertEquals("Zebra", list.get(2).getApp().getName());
    }

    @Test
    public void testMixedSortOrder() {
        LaunchCandidate sysPrefix = new LaunchCandidate(app("com.sys.pref", "Firefox", true),
                LaunchCandidate.MatchType.PREFIX_NAME);
        LaunchCandidate usrPrefix = new LaunchCandidate(app("com.usr.pref", "Firefox", false),
                LaunchCandidate.MatchType.PREFIX_NAME);
        LaunchCandidate exactName = new LaunchCandidate(app("com.exact.name", "Firefox", false),
                LaunchCandidate.MatchType.EXACT_NAME);

        List<LaunchCandidate> list = new ArrayList<>();
        list.add(sysPrefix);
        list.add(usrPrefix);
        list.add(exactName);
        Collections.sort(list);

        // EXACT_NAME first, then user PREFIX_NAME, then system PREFIX_NAME
        Assert.assertEquals(LaunchCandidate.MatchType.EXACT_NAME, list.get(0).getMatchType());
        Assert.assertEquals("com.usr.pref", list.get(1).getApp().getPackageName());
        Assert.assertEquals("com.sys.pref", list.get(2).getApp().getPackageName());
    }

    @Test
    public void testMatchTypeLabel() {
        Assert.assertEquals("exact-package", LaunchCandidate.MatchType.EXACT_PACKAGE.getLabel());
        Assert.assertEquals("exact-name", LaunchCandidate.MatchType.EXACT_NAME.getLabel());
        Assert.assertEquals("prefix-name", LaunchCandidate.MatchType.PREFIX_NAME.getLabel());
    }

    @Test
    public void testToString() {
        LaunchCandidate c = new LaunchCandidate(app("com.app", "MyApp", true),
                LaunchCandidate.MatchType.PREFIX_NAME);
        String str = c.toString();
        Assert.assertTrue(str.contains("prefix-name"));
        Assert.assertTrue(str.contains("MyApp"));
        Assert.assertTrue(str.contains("com.app"));
        Assert.assertTrue(str.contains("system=true"));
    }
}
