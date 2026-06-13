package com.genymobile.scrcpy.control;

import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.model.LaunchPlan;
import com.genymobile.scrcpy.model.LaunchQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Resolves a {@link LaunchQuery} into a {@link LaunchResolution}: either a unique {@link LaunchPlan}, or an explainable
 * not-found / ambiguous / no-display outcome.
 *
 * <p>This class is intentionally free of any {@code android.*} dependency. The Android-specific work (app enumeration, display
 * waiting) is provided through {@link AppRepository} and {@link DisplayResolver}, so the resolution logic can be unit-tested in
 * isolation.
 *
 * <p>Resolution is deterministic: ambiguous candidates are returned sorted by {@link DeviceApp#SORT_COMPARATOR}.
 */
public final class AppLaunchResolver {

    // Mirrors com.genymobile.scrcpy.device.Device.DISPLAY_ID_NONE; kept local to avoid an Android dependency in this pure class.
    private static final int DISPLAY_ID_NONE = -1;

    /**
     * Provides access to the device's installed apps.
     */
    public interface AppRepository {
        /**
         * @return the app with the given exact package name, or {@code null} if no such app is installed
         */
        DeviceApp findByPackageName(String packageName);

        /**
         * @return all launchable apps (any scope); scope filtering is applied by the resolver
         */
        List<DeviceApp> listLaunchableApps();
    }

    /**
     * Resolves the display id to launch on, or {@code -1} (Device.DISPLAY_ID_NONE) if none is available. The implementation may block
     * (e.g. waiting for a virtual display created by {@code --new-display}).
     */
    @FunctionalInterface
    public interface DisplayResolver {
        int resolveDisplayId();
    }

    private AppLaunchResolver() {
        // not instantiable
    }

    public static LaunchResolution resolve(LaunchQuery query, AppRepository repository, DisplayResolver displayResolver) {
        List<DeviceApp> matches = match(query, repository);
        DeviceApp app = pickUnique(query, matches);

        if (app == null) {
            if (matches.isEmpty()) {
                return LaunchResolution.notFound(query);
            }
            // Several candidates and none could be promoted to a unique match: return an explainable, stably ordered result.
            Collections.sort(matches, DeviceApp.SORT_COMPARATOR);
            return LaunchResolution.ambiguous(query, matches);
        }

        // A unique app was resolved: only now do we resolve the display target (which may block for --new-display).
        int displayId = displayResolver.resolveDisplayId();
        if (query.isDryRun()) {
            // A dry-run still produces a plan even if no display is available; the preview reports the (un)availability.
            return LaunchResolution.resolved(query, new LaunchPlan(app, displayId, query.isForceStop(), true));
        }
        if (displayId == DISPLAY_ID_NONE) {
            return LaunchResolution.noDisplay(query, app);
        }
        return LaunchResolution.resolved(query, new LaunchPlan(app, displayId, query.isForceStop(), false));
    }

    /**
     * Returns the apps matching the query's match mode and scope (unsorted).
     */
    private static List<DeviceApp> match(LaunchQuery query, AppRepository repository) {
        if (query.getMatchMode() == LaunchQuery.MatchMode.PACKAGE) {
            // Package names are unique; scope does not apply.
            DeviceApp app = repository.findByPackageName(query.getTerm());
            List<DeviceApp> result = new ArrayList<>(1);
            if (app != null) {
                result.add(app);
            }
            return result;
        }

        String term = query.getTerm().toLowerCase(Locale.getDefault());
        boolean exact = query.getMatchMode() == LaunchQuery.MatchMode.NAME_EXACT;

        List<DeviceApp> result = new ArrayList<>();
        for (DeviceApp app : repository.listLaunchableApps()) {
            if (!inScope(app, query.getScope())) {
                continue;
            }
            String name = app.getName().toLowerCase(Locale.getDefault());
            boolean nameMatches = exact ? name.equals(term) : name.startsWith(term);
            if (nameMatches) {
                result.add(app);
            }
        }
        return result;
    }

    private static boolean inScope(DeviceApp app, LaunchQuery.Scope scope) {
        switch (scope) {
            case SYSTEM:
                return app.isSystem();
            case USER:
                return !app.isSystem();
            default:
                return true;
        }
    }

    /**
     * Picks the single resolved app from the matches, or {@code null} if there is no unique resolution.
     *
     * <p>For {@link LaunchQuery.MatchMode#NAME_PREFIX} with several prefix matches, a single exact-name match is promoted
     * (e.g. {@code ?maps} resolves to the app literally named "Maps" even when "Maps Go" also matches the prefix).
     */
    private static DeviceApp pickUnique(LaunchQuery query, List<DeviceApp> matches) {
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1 && query.getMatchMode() == LaunchQuery.MatchMode.NAME_PREFIX) {
            String term = query.getTerm().toLowerCase(Locale.getDefault());
            DeviceApp exactMatch = null;
            for (DeviceApp app : matches) {
                if (app.getName().toLowerCase(Locale.getDefault()).equals(term)) {
                    if (exactMatch != null) {
                        // More than one app shares the exact name: still ambiguous.
                        return null;
                    }
                    exactMatch = app;
                }
            }
            return exactMatch;
        }
        return null;
    }
}
