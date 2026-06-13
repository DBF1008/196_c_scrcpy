package com.genymobile.scrcpy.device;

import com.genymobile.scrcpy.model.AppQuery;
import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.model.LaunchCandidate;
import com.genymobile.scrcpy.model.LaunchPlan;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AppResolverTest {

    /**
     * Fake AppProvider for test isolation — no Android framework needed.
     */
    private static class FakeAppProvider implements AppResolver.AppProvider {
        private final List<DeviceApp> launchableApps;
        private final List<DeviceApp> allApps;

        FakeAppProvider(List<DeviceApp> apps) {
            this.launchableApps = new ArrayList<>(apps);
            this.allApps = new ArrayList<>(apps);
        }

        FakeAppProvider(List<DeviceApp> launchableApps, List<DeviceApp> allApps) {
            this.launchableApps = new ArrayList<>(launchableApps);
            this.allApps = new ArrayList<>(allApps);
        }

        @Override
        public List<DeviceApp> getLaunchableApps() {
            return launchableApps;
        }

        @Override
        public DeviceApp findByPackageName(String packageName) {
            for (DeviceApp app : allApps) {
                if (app.getPackageName().equals(packageName)) {
                    return app;
                }
            }
            return null;
        }
    }

    private static DeviceApp app(String pkg, String name, boolean system) {
        return new DeviceApp(pkg, name, system);
    }

    // ===== Package name resolution =====

    @Test
    public void testExactPackageFound() {
        DeviceApp target = app("com.exact", "ExactApp", false);
        AppResolver resolver = new AppResolver(new FakeAppProvider(Collections.singletonList(target)));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("com.exact"), 0);

        Assert.assertTrue(plan.hasAnyMatch());
        Assert.assertTrue(plan.hasUniqueMatch());
        Assert.assertNotNull(plan.getSelected());
        Assert.assertEquals("com.exact", plan.getSelected().getApp().getPackageName());
        Assert.assertEquals(LaunchCandidate.MatchType.EXACT_PACKAGE, plan.getSelected().getMatchType());
    }

    @Test
    public void testExactPackageNotFound() {
        AppResolver resolver = new AppResolver(new FakeAppProvider(Collections.<DeviceApp>emptyList()));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("com.missing"), 0);

        Assert.assertFalse(plan.hasAnyMatch());
        Assert.assertFalse(plan.hasUniqueMatch());
        Assert.assertNull(plan.getSelected());
        Assert.assertTrue(plan.getDiagnosticMessage().contains("com.missing"));
    }

    // ===== Name search resolution =====

    @Test
    public void testNamePrefixMultipleCandidates() {
        List<DeviceApp> apps = Arrays.asList(
                app("com.firefox", "Firefox", false),
                app("com.firefox.beta", "Firefox Beta", false),
                app("com.firewall", "Firewall Pro", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("?fire"), 0);

        Assert.assertTrue(plan.hasAnyMatch());
        Assert.assertEquals(3, plan.getAllCandidates().size());
        // Non-dry-run: selects best match (first after sort)
        Assert.assertNotNull(plan.getSelected());
    }

    @Test
    public void testNameExactMatchPreferred() {
        List<DeviceApp> apps = Arrays.asList(
                app("com.firewall", "Firewall", false),
                app("com.firefox", "Firefox", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("?firefox"), 0);

        Assert.assertEquals(1, plan.getAllCandidates().size());
        Assert.assertEquals(LaunchCandidate.MatchType.EXACT_NAME, plan.getSelected().getMatchType());
        Assert.assertEquals("com.firefox", plan.getSelected().getApp().getPackageName());
    }

    @Test
    public void testNameExactMatchBeforePrefix() {
        List<DeviceApp> apps = Arrays.asList(
                app("com.firefox.beta", "Firefox Beta", false),
                app("com.firefox", "Firefox", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("?firefox"), 0);

        Assert.assertEquals(2, plan.getAllCandidates().size());
        // EXACT_NAME should be first
        Assert.assertEquals(LaunchCandidate.MatchType.EXACT_NAME,
                plan.getAllCandidates().get(0).getMatchType());
        Assert.assertEquals("Firefox", plan.getAllCandidates().get(0).getApp().getName());
        Assert.assertEquals(LaunchCandidate.MatchType.PREFIX_NAME,
                plan.getAllCandidates().get(1).getMatchType());
    }

    @Test
    public void testNameCaseInsensitive() {
        List<DeviceApp> apps = Collections.singletonList(
                app("com.firefox", "Firefox", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("?FIREFOX"), 0);

        Assert.assertEquals(1, plan.getAllCandidates().size());
        Assert.assertEquals(LaunchCandidate.MatchType.EXACT_NAME, plan.getSelected().getMatchType());
    }

    @Test
    public void testNameSearchNoMatch() {
        List<DeviceApp> apps = Collections.singletonList(
                app("com.chrome", "Chrome", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("?zzzzz"), 0);

        Assert.assertFalse(plan.hasAnyMatch());
        Assert.assertNull(plan.getSelected());
        Assert.assertTrue(plan.getDiagnosticMessage().contains("zzzzz"));
    }

    // ===== Dry-run =====

    @Test
    public void testDryRunMultipleCandidatesNoSelection() {
        List<DeviceApp> apps = Arrays.asList(
                app("com.firefox", "Firefox", false),
                app("com.firefox.beta", "Firefox Beta", false),
                app("com.firewall", "Firewall Pro", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("~?fire"), 0);

        Assert.assertTrue(plan.hasAnyMatch());
        Assert.assertEquals(3, plan.getAllCandidates().size());
        Assert.assertTrue(plan.isDryRun());
        Assert.assertNull(plan.getSelected()); // dry-run never selects
        Assert.assertTrue(plan.formatReport().contains("[DRY-RUN]"));
    }

    @Test
    public void testDryRunSingleCandidateNoSelection() {
        List<DeviceApp> apps = Collections.singletonList(
                app("com.firefox", "Firefox", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("~?firefox"), 0);

        Assert.assertTrue(plan.hasAnyMatch());
        Assert.assertTrue(plan.isDryRun());
        Assert.assertNull(plan.getSelected()); // dry-run: even single match is not selected
    }

    // ===== Force-stop propagation =====

    @Test
    public void testForceStopPropagatedToPlan() {
        List<DeviceApp> apps = Collections.singletonList(
                app("com.app", "App", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("+com.app"), 0);

        Assert.assertTrue(plan.isForceStop());
        Assert.assertNotNull(plan.getSelected());
    }

    // ===== System app deprioritization =====

    @Test
    public void testSystemAppDeprioritized() {
        List<DeviceApp> apps = Arrays.asList(
                app("com.system.maps", "Maps", true),
                app("com.google.maps", "Maps", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("?maps"), 0);

        Assert.assertEquals(2, plan.getAllCandidates().size());
        // User app should be first
        Assert.assertFalse(plan.getAllCandidates().get(0).getApp().isSystem());
        Assert.assertEquals("com.google.maps", plan.getAllCandidates().get(0).getApp().getPackageName());
        Assert.assertTrue(plan.getAllCandidates().get(1).getApp().isSystem());
    }

    // ===== Display target =====

    @Test
    public void testExplicitDisplayId() {
        List<DeviceApp> apps = Collections.singletonList(
                app("com.app", "App", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("com.app@5"), 0);

        Assert.assertEquals(5, plan.getTargetDisplayId());
    }

    @Test
    public void testFallbackDisplayId() {
        List<DeviceApp> apps = Collections.singletonList(
                app("com.app", "App", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("com.app"), 3);

        Assert.assertEquals(3, plan.getTargetDisplayId());
    }

    @Test
    public void testExplicitDisplayOverridesFallback() {
        List<DeviceApp> apps = Collections.singletonList(
                app("com.app", "App", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("com.app@7"), 3);

        Assert.assertEquals(7, plan.getTargetDisplayId());
    }

    @Test
    public void testVirtualDisplayNotReady() {
        List<DeviceApp> apps = Collections.singletonList(
                app("com.app", "App", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("com.app"), -1);

        Assert.assertEquals(-1, plan.getTargetDisplayId());
        Assert.assertNotNull(plan.getSelected());
        // Diagnostic mentions no display
        String report = plan.formatReport();
        Assert.assertTrue(report.contains("none available") || plan.getDiagnosticMessage().contains("no display"));
    }

    // ===== Report formatting =====

    @Test
    public void testFormatReportSingleMatch() {
        List<DeviceApp> apps = Collections.singletonList(
                app("com.firefox", "Firefox", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("com.firefox"), 0);

        String report = plan.formatReport();
        Assert.assertTrue(report.contains("Firefox"));
        Assert.assertTrue(report.contains("com.firefox"));
        Assert.assertTrue(report.contains("display 0"));
    }

    @Test
    public void testFormatReportMultipleMatches() {
        List<DeviceApp> apps = Arrays.asList(
                app("com.firefox", "Firefox", false),
                app("com.firefox.beta", "Firefox Beta", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("?fire"), 0);

        String report = plan.formatReport();
        Assert.assertTrue(report.contains("Multiple candidates"));
        Assert.assertTrue(report.contains("Firefox"));
        Assert.assertTrue(report.contains("Firefox Beta"));
        Assert.assertTrue(report.contains("selected"));
    }

    @Test
    public void testFormatReportDryRunPrefix() {
        List<DeviceApp> apps = Collections.singletonList(
                app("com.app", "App", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("~com.app"), 0);

        String report = plan.formatReport();
        Assert.assertTrue(report.contains("[DRY-RUN]"));
    }

    @Test
    public void testFormatReportNoMatch() {
        AppResolver resolver = new AppResolver(new FakeAppProvider(Collections.<DeviceApp>emptyList()));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("com.missing"), 0);

        String report = plan.formatReport();
        Assert.assertTrue(report.contains("com.missing"));
    }

    @Test
    public void testFormatReportForceStop() {
        List<DeviceApp> apps = Collections.singletonList(
                app("com.app", "App", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));
        LaunchPlan plan = resolver.resolve(AppQuery.parse("+com.app"), 0);

        String report = plan.formatReport();
        Assert.assertTrue(report.contains("force-stop"));
    }

    // ===== Stable sort =====

    @Test
    public void testStableSortAcrossRuns() {
        List<DeviceApp> apps = Arrays.asList(
                app("com.c", "App Charlie", false),
                app("com.a", "App Alpha", false),
                app("com.b", "App Bravo", false)
        );
        AppResolver resolver = new AppResolver(new FakeAppProvider(apps));

        // Run twice to verify deterministic ordering
        LaunchPlan plan1 = resolver.resolve(AppQuery.parse("?app"), 0);
        LaunchPlan plan2 = resolver.resolve(AppQuery.parse("?app"), 0);

        Assert.assertEquals(3, plan1.getAllCandidates().size());
        Assert.assertEquals(plan1.getAllCandidates().size(), plan2.getAllCandidates().size());
        for (int i = 0; i < plan1.getAllCandidates().size(); i++) {
            Assert.assertEquals(
                    plan1.getAllCandidates().get(i).getApp().getPackageName(),
                    plan2.getAllCandidates().get(i).getApp().getPackageName()
            );
        }
        // Verify sorted: Alpha, Bravo, Charlie (alphabetical by name)
        Assert.assertEquals("App Alpha", plan1.getAllCandidates().get(0).getApp().getName());
        Assert.assertEquals("App Bravo", plan1.getAllCandidates().get(1).getApp().getName());
        Assert.assertEquals("App Charlie", plan1.getAllCandidates().get(2).getApp().getName());
    }
}
