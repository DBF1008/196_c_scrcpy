package com.genymobile.scrcpy.model;

/**
 * An explicit, resolved plan describing how to launch a single app: which app, on which display, and whether to force-stop it first.
 *
 * <p>A {@code dryRun} plan must be previewed (logged) but not executed. {@link #getDisplayId()} may be {@code -1} (no display available)
 * only for a dry-run preview; an executable plan always carries a valid display id.
 */
public final class LaunchPlan {

    private final DeviceApp app;
    private final int displayId;
    private final boolean forceStop;
    private final boolean dryRun;

    public LaunchPlan(DeviceApp app, int displayId, boolean forceStop, boolean dryRun) {
        this.app = app;
        this.displayId = displayId;
        this.forceStop = forceStop;
        this.dryRun = dryRun;
    }

    public DeviceApp getApp() {
        return app;
    }

    public int getDisplayId() {
        return displayId;
    }

    public boolean isForceStop() {
        return forceStop;
    }

    public boolean isDryRun() {
        return dryRun;
    }
}
