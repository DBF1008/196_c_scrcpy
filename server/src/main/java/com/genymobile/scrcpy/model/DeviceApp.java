package com.genymobile.scrcpy.model;

import java.util.Comparator;

public final class DeviceApp {

    /**
     * Canonical ordering for stable, explainable app listings:
     * <ol>
     *   <li>system apps before user apps;
     *   <li>then by display name;
     *   <li>then by package name.
     * </ol>
     * Shared by {@code LogUtils} and the launch resolver so that listings and resolution results are ordered identically.
     */
    public static final Comparator<DeviceApp> SORT_COMPARATOR = (thisApp, otherApp) -> {
        // System apps first
        int cmp = -Boolean.compare(thisApp.system, otherApp.system);
        if (cmp != 0) {
            return cmp;
        }
        cmp = thisApp.name.compareTo(otherApp.name);
        if (cmp != 0) {
            return cmp;
        }
        return thisApp.packageName.compareTo(otherApp.packageName);
    };

    private final String packageName;
    private final String name;
    private final boolean system;

    public DeviceApp(String packageName, String name, boolean system) {
        this.packageName = packageName;
        this.name = name;
        this.system = system;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getName() {
        return name;
    }

    public boolean isSystem() {
        return system;
    }
}
