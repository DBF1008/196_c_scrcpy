package com.genymobile.scrcpy.control;

import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.model.LaunchPlan;
import com.genymobile.scrcpy.model.LaunchQuery;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppLaunchResolverTest {

    private static final int DISPLAY_ID_NONE = -1;

    /**
     * In-memory {@link AppLaunchResolver.AppRepository} backed by a fixed list of apps.
     */
    private static final class FakeRepository implements AppLaunchResolver.AppRepository {
        private final List<DeviceApp> apps;

        private FakeRepository(DeviceApp... apps) {
            this.apps = Arrays.asList(apps);
        }

        @Override
        public DeviceApp findByPackageName(String packageName) {
            for (DeviceApp app : apps) {
                if (app.getPackageName().equals(packageName)) {
                    return app;
                }
            }
            return null;
        }

        @Override
        public List<DeviceApp> listLaunchableApps() {
            return new ArrayList<>(apps);
        }
    }

    private static AppLaunchResolver.DisplayResolver display(int id) {
        return () -> id;
    }

    @Test
    public void testPackageLaunch() {
        DeviceApp firefox = new DeviceApp("org.mozilla.firefox", "Firefox", false);
        FakeRepository repo = new FakeRepository(firefox);

        LaunchResolution res = AppLaunchResolver.resolve(LaunchQuery.parse("org.mozilla.firefox"), repo, display(2));

        Assert.assertEquals(LaunchResolution.Status.RESOLVED, res.getStatus());
        LaunchPlan plan = res.getPlan();
        Assert.assertNotNull(plan);
        Assert.assertEquals("org.mozilla.firefox", plan.getApp().getPackageName());
        Assert.assertEquals(2, plan.getDisplayId());
        Assert.assertFalse(plan.isForceStop());
        Assert.assertFalse(plan.isDryRun());
    }

    @Test
    public void testPackageNotFound() {
        FakeRepository repo = new FakeRepository(new DeviceApp("org.mozilla.firefox", "Firefox", false));

        LaunchResolution res = AppLaunchResolver.resolve(LaunchQuery.parse("com.absent.app"), repo, display(2));

        Assert.assertEquals(LaunchResolution.Status.NOT_FOUND, res.getStatus());
        Assert.assertNull(res.getPlan());
    }

    @Test
    public void testNameAmbiguityStableOrder() {
        DeviceApp maps = new DeviceApp("com.google.maps", "Maps", false);
        DeviceApp mapsGo = new DeviceApp("com.google.mapsgo", "Maps Go", false);
        // Intentionally provided unsorted: the resolver must impose a stable, canonical order.
        FakeRepository repo = new FakeRepository(mapsGo, maps);

        LaunchResolution res = AppLaunchResolver.resolve(LaunchQuery.parse("?map"), repo, display(2));

        Assert.assertEquals(LaunchResolution.Status.AMBIGUOUS, res.getStatus());
        Assert.assertNull(res.getPlan());
        List<DeviceApp> candidates = res.getCandidates();
        Assert.assertEquals(2, candidates.size());
        Assert.assertEquals("com.google.maps", candidates.get(0).getPackageName());
        Assert.assertEquals("com.google.mapsgo", candidates.get(1).getPackageName());
    }

    @Test
    public void testNamePrefixPromotesUniqueExact() {
        DeviceApp maps = new DeviceApp("com.google.maps", "Maps", false);
        DeviceApp mapsGo = new DeviceApp("com.google.mapsgo", "Maps Go", false);
        FakeRepository repo = new FakeRepository(maps, mapsGo);

        // "maps" prefix-matches both, but exactly one app is literally named "Maps" -> promote it.
        LaunchResolution res = AppLaunchResolver.resolve(LaunchQuery.parse("?maps"), repo, display(2));

        Assert.assertEquals(LaunchResolution.Status.RESOLVED, res.getStatus());
        Assert.assertEquals("com.google.maps", res.getPlan().getApp().getPackageName());
    }

    @Test
    public void testNameExactUnique() {
        DeviceApp maps = new DeviceApp("com.google.maps", "Maps", false);
        DeviceApp mapsGo = new DeviceApp("com.google.mapsgo", "Maps Go", false);
        FakeRepository repo = new FakeRepository(maps, mapsGo);

        LaunchResolution res = AppLaunchResolver.resolve(LaunchQuery.parse("?=Maps"), repo, display(2));

        Assert.assertEquals(LaunchResolution.Status.RESOLVED, res.getStatus());
        Assert.assertEquals("com.google.maps", res.getPlan().getApp().getPackageName());
    }

    @Test
    public void testNameExactAmbiguous() {
        DeviceApp systemCamera = new DeviceApp("com.android.camera", "Camera", true);
        DeviceApp userCamera = new DeviceApp("com.vendor.camera", "Camera", false);
        FakeRepository repo = new FakeRepository(userCamera, systemCamera);

        LaunchResolution res = AppLaunchResolver.resolve(LaunchQuery.parse("?=Camera"), repo, display(2));

        Assert.assertEquals(LaunchResolution.Status.AMBIGUOUS, res.getStatus());
        Assert.assertEquals(2, res.getCandidates().size());
        // System apps are ordered first.
        Assert.assertEquals("com.android.camera", res.getCandidates().get(0).getPackageName());
    }

    @Test
    public void testForceStop() {
        DeviceApp firefox = new DeviceApp("org.mozilla.firefox", "Firefox", false);
        FakeRepository repo = new FakeRepository(firefox);

        LaunchResolution res = AppLaunchResolver.resolve(LaunchQuery.parse("+org.mozilla.firefox"), repo, display(0));

        Assert.assertEquals(LaunchResolution.Status.RESOLVED, res.getStatus());
        Assert.assertTrue(res.getPlan().isForceStop());
        Assert.assertFalse(res.getPlan().isDryRun());
    }

    @Test
    public void testDryRunWithDisplay() {
        DeviceApp firefox = new DeviceApp("org.mozilla.firefox", "Firefox", false);
        FakeRepository repo = new FakeRepository(firefox);

        LaunchResolution res = AppLaunchResolver.resolve(LaunchQuery.parse("!org.mozilla.firefox"), repo, display(3));

        Assert.assertEquals(LaunchResolution.Status.RESOLVED, res.getStatus());
        Assert.assertTrue(res.getPlan().isDryRun());
        Assert.assertEquals(3, res.getPlan().getDisplayId());
    }

    @Test
    public void testDryRunToleratesUnavailableDisplay() {
        DeviceApp firefox = new DeviceApp("org.mozilla.firefox", "Firefox", false);
        FakeRepository repo = new FakeRepository(firefox);

        // A dry-run still resolves (for preview) even when no display is available.
        LaunchResolution res = AppLaunchResolver.resolve(LaunchQuery.parse("!org.mozilla.firefox"), repo, display(DISPLAY_ID_NONE));

        Assert.assertEquals(LaunchResolution.Status.RESOLVED, res.getStatus());
        Assert.assertTrue(res.getPlan().isDryRun());
        Assert.assertEquals(DISPLAY_ID_NONE, res.getPlan().getDisplayId());
    }

    @Test
    public void testVirtualDisplayNotReady() {
        DeviceApp firefox = new DeviceApp("org.mozilla.firefox", "Firefox", false);
        FakeRepository repo = new FakeRepository(firefox);

        // Non-dry-run launch with no resolvable display (e.g. --new-display virtual display not ready) -> explainable failure.
        LaunchResolution res = AppLaunchResolver.resolve(LaunchQuery.parse("org.mozilla.firefox"), repo, display(DISPLAY_ID_NONE));

        Assert.assertEquals(LaunchResolution.Status.NO_DISPLAY, res.getStatus());
        Assert.assertNull(res.getPlan());
        Assert.assertNotNull(res.getApp());
        Assert.assertEquals("org.mozilla.firefox", res.getApp().getPackageName());
    }

    @Test
    public void testScopeSystemOnly() {
        DeviceApp systemSettings = new DeviceApp("com.android.settings", "Settings", true);
        DeviceApp userSettings = new DeviceApp("com.thirdparty.settings", "Settings Helper", false);
        FakeRepository repo = new FakeRepository(systemSettings, userSettings);

        LaunchQuery query = new LaunchQuery("settings", LaunchQuery.MatchMode.NAME_PREFIX, LaunchQuery.Scope.SYSTEM, false, false);
        LaunchResolution res = AppLaunchResolver.resolve(query, repo, display(0));

        Assert.assertEquals(LaunchResolution.Status.RESOLVED, res.getStatus());
        Assert.assertEquals("com.android.settings", res.getPlan().getApp().getPackageName());
    }

    @Test
    public void testScopeUserExcludesSystem() {
        DeviceApp systemSettings = new DeviceApp("com.android.settings", "Settings", true);
        FakeRepository repo = new FakeRepository(systemSettings);

        LaunchQuery query = new LaunchQuery("settings", LaunchQuery.MatchMode.NAME_PREFIX, LaunchQuery.Scope.USER, false, false);
        LaunchResolution res = AppLaunchResolver.resolve(query, repo, display(0));

        Assert.assertEquals(LaunchResolution.Status.NOT_FOUND, res.getStatus());
    }
}
