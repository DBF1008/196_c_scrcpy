package com.genymobile.scrcpy.device;

import com.genymobile.scrcpy.model.DeviceApp;

import java.util.List;

/**
 * Production adapter for {@link AppResolver.AppProvider} that delegates to
 * the static methods on {@link Device}.
 */
public final class DeviceAppProvider implements AppResolver.AppProvider {

    @Override
    public List<DeviceApp> getLaunchableApps() {
        return Device.listApps();
    }

    @Override
    public DeviceApp findByPackageName(String packageName) {
        return Device.findByPackageName(packageName);
    }
}
