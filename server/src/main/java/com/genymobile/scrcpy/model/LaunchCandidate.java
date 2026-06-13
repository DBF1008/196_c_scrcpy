package com.genymobile.scrcpy.model;

import java.util.Objects;

/**
 * Represents a resolved app candidate with match metadata for ranking.
 *
 * <p>Natural ordering (ascending priority, lower = better):
 * <ol>
 *   <li>{@link MatchType} priority (EXACT_PACKAGE &gt; EXACT_NAME &gt; PREFIX_NAME)</li>
 *   <li>User apps before system apps</li>
 *   <li>App name alphabetically (case-insensitive)</li>
 *   <li>Package name alphabetically (tiebreaker)</li>
 * </ol>
 */
public final class LaunchCandidate implements Comparable<LaunchCandidate> {

    public enum MatchType {
        EXACT_PACKAGE(0),
        EXACT_NAME(1),
        PREFIX_NAME(2);

        private final int priority;

        MatchType(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }

        public String getLabel() {
            switch (this) {
                case EXACT_PACKAGE:
                    return "exact-package";
                case EXACT_NAME:
                    return "exact-name";
                case PREFIX_NAME:
                    return "prefix-name";
                default:
                    return "unknown";
            }
        }
    }

    private final DeviceApp app;
    private final MatchType matchType;

    public LaunchCandidate(DeviceApp app, MatchType matchType) {
        this.app = Objects.requireNonNull(app);
        this.matchType = Objects.requireNonNull(matchType);
    }

    public DeviceApp getApp() {
        return app;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    @Override
    public int compareTo(LaunchCandidate other) {
        // 1. Match type priority (lower = better)
        int cmp = Integer.compare(this.matchType.priority, other.matchType.priority);
        if (cmp != 0) {
            return cmp;
        }

        // 2. User apps before system apps (!system first)
        cmp = -Boolean.compare(this.app.isSystem(), other.app.isSystem());
        if (cmp != 0) {
            return cmp;
        }

        // 3. Name alphabetically (case-insensitive)
        cmp = String.CASE_INSENSITIVE_ORDER.compare(this.app.getName(), other.app.getName());
        if (cmp != 0) {
            return cmp;
        }

        // 4. Package name alphabetically (tiebreaker)
        return this.app.getPackageName().compareTo(other.app.getPackageName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LaunchCandidate)) {
            return false;
        }
        LaunchCandidate that = (LaunchCandidate) o;
        return matchType == that.matchType
                && app.getPackageName().equals(that.app.getPackageName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(app.getPackageName(), matchType);
    }

    @Override
    public String toString() {
        return "LaunchCandidate{"
                + "match=" + matchType.getLabel()
                + ", name=\"" + app.getName() + "\""
                + ", pkg=" + app.getPackageName()
                + ", system=" + app.isSystem()
                + '}';
    }
}
