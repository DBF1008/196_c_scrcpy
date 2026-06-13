package com.genymobile.scrcpy.control;

import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.util.LogUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles asynchronous app launching.
 * <p>
 * Extracted from Controller to separate app search and launch concerns from message dispatch.
 * Uses a single-thread executor to avoid blocking the control thread during potentially slow
 * app listing operations.
 */
public final class AppLauncher {

    private static final long START_APP_TIMEOUT_MS = 1000;

    private final DisplayRouter router;
    private ExecutorService startAppExecutor;

    public AppLauncher(DisplayRouter router) {
        this.router = router;
    }

    /**
     * Launches an app asynchronously. The app can be specified by:
     * <ul>
     *   <li>Package name (e.g., {@code com.example.app})</li>
     *   <li>Name with {@code ?} prefix for fuzzy search (e.g., {@code ?chrome})</li>
     *   <li>Either with {@code +} prefix to force-stop before launch (e.g., {@code +com.example.app})</li>
     * </ul>
     */
    public void startAppAsync(String name) {
        if (startAppExecutor == null) {
            startAppExecutor = Executors.newSingleThreadExecutor();
        }

        // Listing and selecting the app may take a lot of time
        startAppExecutor.submit(() -> startApp(name));
    }

    /**
     * Shuts down the executor. Called from Controller.stop() to ensure clean resource release.
     */
    public void shutdown() {
        if (startAppExecutor != null) {
            startAppExecutor.shutdownNow();
        }
    }

    private void startApp(String name) {
        boolean forceStopBeforeStart = name.startsWith("+");
        if (forceStopBeforeStart) {
            name = name.substring(1);
        }

        DeviceApp app;
        boolean searchByName = name.startsWith("?");
        if (searchByName) {
            name = name.substring(1);

            Ln.i("Processing Android apps... (this may take some time)");
            List<DeviceApp> apps = Device.findByName(name);
            if (apps.isEmpty()) {
                Ln.w("No app found for name \"" + name + "\"");
                return;
            }

            if (apps.size() > 1) {
                String title = "No unique app found for name \"" + name + "\":";
                Ln.w(LogUtils.buildAppListMessage(title, apps));
                return;
            }

            app = apps.get(0);
        } else {
            app = Device.findByPackageName(name);
            if (app == null) {
                Ln.w("No app found for package \"" + name + "\"");
                return;
            }
        }

        int startAppDisplayId;
        try {
            startAppDisplayId = router.getStartAppDisplayId(START_APP_TIMEOUT_MS);
        } catch (InterruptedException e) {
            // do nothing
            startAppDisplayId = Device.DISPLAY_ID_NONE;
        }
        if (startAppDisplayId == Device.DISPLAY_ID_NONE) {
            Ln.e("No known display id to start app \"" + name + "\"");
            return;
        }

        Ln.i("Starting app \"" + app.getName() + "\" [" + app.getPackageName() + "] on display " + startAppDisplayId + "...");
        Device.startApp(app.getPackageName(), startAppDisplayId, forceStopBeforeStart);
    }
}
