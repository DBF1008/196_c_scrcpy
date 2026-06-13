package com.genymobile.scrcpy.device;

import com.genymobile.scrcpy.model.AppQuery;
import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.model.LaunchCandidate;
import com.genymobile.scrcpy.model.LaunchCandidate.MatchType;
import com.genymobile.scrcpy.model.LaunchPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Stateless app resolution engine.
 *
 * <p>Takes an {@link AppQuery} and a fallback display ID, produces a {@link LaunchPlan}.
 * Uses an {@link AppProvider} to access installed app data, enabling clean testability
 * without Android framework dependencies.
 */
public final class AppResolver {

    /**
     * Abstraction over app data access, enabling test isolation.
     */
    public interface AppProvider {
        /**
         * @return all launchable apps (enabled apps with a launch intent)
         */
        List<DeviceApp> getLaunchableApps();

        /**
         * Find an app by exact package name. Searches all installed apps, not just launchable ones.
         *
         * @param packageName the exact package name to match
         * @return the matching app, or {@code null} if not found
         */
        DeviceApp findByPackageName(String packageName);
    }

    private final AppProvider provider;

    public AppResolver(AppProvider provider) {
        this.provider = provider;
    }

    /**
     * Resolve an app query into a launch plan.
     *
     * @param query             the parsed query
     * @param fallbackDisplayId the display ID to use if the query does not specify one
     * @return an immutable launch plan with candidates and diagnostics
     */
    public LaunchPlan resolve(AppQuery query, int fallbackDisplayId) {
        List<LaunchCandidate> candidates = findCandidates(query);
        Collections.sort(candidates);

        int targetDisplayId = query.getDisplayId() >= 0 ? query.getDisplayId() : fallbackDisplayId;

        LaunchCandidate selected = selectCandidate(candidates, query.isDryRun());
        String diagnostic = buildDiagnostic(query, candidates, selected, targetDisplayId);

        return new LaunchPlan(selected, candidates, targetDisplayId,
                query.isForceStop(), query.isDryRun(), diagnostic);
    }

    private List<LaunchCandidate> findCandidates(AppQuery query) {
        List<LaunchCandidate> candidates = new ArrayList<>();

        if (query.isNameSearch()) {
            findByName(query.getTarget(), candidates);
        } else {
            findByPackage(query.getTarget(), candidates);
        }

        return candidates;
    }

    private void findByPackage(String packageName, List<LaunchCandidate> candidates) {
        DeviceApp app = provider.findByPackageName(packageName);
        if (app != null) {
            candidates.add(new LaunchCandidate(app, MatchType.EXACT_PACKAGE));
        }
    }

    private void findByName(String searchName, List<LaunchCandidate> candidates) {
        String searchLower = searchName.toLowerCase(Locale.getDefault());
        List<DeviceApp> apps = provider.getLaunchableApps();

        for (DeviceApp app : apps) {
            String nameLower = app.getName().toLowerCase(Locale.getDefault());

            if (nameLower.equals(searchLower)) {
                candidates.add(new LaunchCandidate(app, MatchType.EXACT_NAME));
            } else if (nameLower.startsWith(searchLower)) {
                candidates.add(new LaunchCandidate(app, MatchType.PREFIX_NAME));
            }
        }
    }

    private LaunchCandidate selectCandidate(List<LaunchCandidate> candidates, boolean dryRun) {
        if (candidates.isEmpty()) {
            return null;
        }

        if (dryRun) {
            // Dry-run: never auto-select, show all candidates
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // Multiple candidates, non-dry-run: select the best match (first after sorting)
        return candidates.get(0);
    }

    private String buildDiagnostic(AppQuery query, List<LaunchCandidate> candidates,
                                   LaunchCandidate selected, int targetDisplayId) {
        StringBuilder sb = new StringBuilder();

        if (candidates.isEmpty()) {
            if (query.isNameSearch()) {
                sb.append("No app found for name \"").append(query.getTarget()).append("\"");
            } else {
                sb.append("No app found for package \"").append(query.getTarget()).append("\"");
            }
            return sb.toString();
        }

        if (selected != null && candidates.size() == 1) {
            sb.append("Resolved: \"").append(selected.getApp().getName()).append("\"");
            sb.append(" [").append(selected.getApp().getPackageName()).append("]");
            sb.append(" via ").append(selected.getMatchType().getLabel());
            return sb.toString();
        }

        // Multiple candidates
        sb.append("Found ").append(candidates.size()).append(" candidates for \"")
                .append(query.getTarget()).append("\":");
        if (selected != null) {
            sb.append(" best match = \"").append(selected.getApp().getName())
                    .append("\" [").append(selected.getApp().getPackageName()).append("]");
        }
        if (targetDisplayId < 0) {
            sb.append(" (no display available)");
        }

        return sb.toString();
    }
}
