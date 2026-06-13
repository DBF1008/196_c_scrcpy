package com.genymobile.scrcpy.model;

import java.util.Collections;
import java.util.List;

/**
 * Immutable execution plan produced by app resolution.
 *
 * <p>Contains the selected candidate (if uniquely resolved), all sorted candidates,
 * display target, and a human-readable diagnostic report.
 */
public final class LaunchPlan {

    private final LaunchCandidate selected;
    private final List<LaunchCandidate> allCandidates;
    private final int targetDisplayId;
    private final boolean forceStop;
    private final boolean dryRun;
    private final String diagnosticMessage;

    public LaunchPlan(LaunchCandidate selected, List<LaunchCandidate> allCandidates,
                      int targetDisplayId, boolean forceStop, boolean dryRun,
                      String diagnosticMessage) {
        this.selected = selected;
        this.allCandidates = Collections.unmodifiableList(allCandidates);
        this.targetDisplayId = targetDisplayId;
        this.forceStop = forceStop;
        this.dryRun = dryRun;
        this.diagnosticMessage = diagnosticMessage;
    }

    public LaunchCandidate getSelected() {
        return selected;
    }

    public List<LaunchCandidate> getAllCandidates() {
        return allCandidates;
    }

    public int getTargetDisplayId() {
        return targetDisplayId;
    }

    public boolean isForceStop() {
        return forceStop;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public String getDiagnosticMessage() {
        return diagnosticMessage;
    }

    /**
     * @return {@code true} if exactly one candidate was selected for launch
     */
    public boolean hasUniqueMatch() {
        return selected != null && allCandidates.size() == 1;
    }

    /**
     * @return {@code true} if at least one candidate was found (selected may still be null in dry-run)
     */
    public boolean hasAnyMatch() {
        return !allCandidates.isEmpty();
    }

    /**
     * @return a human-readable multi-line report suitable for logging
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();

        if (dryRun) {
            sb.append("[DRY-RUN] ");
        }

        if (allCandidates.isEmpty()) {
            sb.append(diagnosticMessage);
            return sb.toString();
        }

        if (hasUniqueMatch()) {
            DeviceApp app = selected.getApp();
            sb.append("Launch plan: \"").append(app.getName()).append("\"");
            sb.append(" [").append(app.getPackageName()).append("]");
            sb.append(" on display ").append(targetDisplayId);
            if (forceStop) {
                sb.append(" (with force-stop)");
            }
            sb.append("\n  Match: ").append(selected.getMatchType().getLabel());
            if (app.isSystem()) {
                sb.append(" [system]");
            }
            return sb.toString();
        }

        // Multiple candidates
        sb.append("Multiple candidates (").append(allCandidates.size()).append(" apps matched):\n");
        for (int i = 0; i < allCandidates.size(); i++) {
            LaunchCandidate c = allCandidates.get(i);
            sb.append("  ").append(i + 1).append(". ");
            sb.append(c.getMatchType().getLabel()).append(": ");
            sb.append("\"").append(c.getApp().getName()).append("\"");
            sb.append(" [").append(c.getApp().getPackageName()).append("]");
            if (c.getApp().isSystem()) {
                sb.append(" [system]");
            }
            if (selected != null && selected.equals(c)) {
                sb.append("  ← selected");
            }
            sb.append('\n');
        }

        if (targetDisplayId >= 0) {
            sb.append("Target display: ").append(targetDisplayId);
        } else {
            sb.append("Target display: (none available)");
        }
        if (forceStop) {
            sb.append(" | force-stop: yes");
        }

        return sb.toString();
    }
}
