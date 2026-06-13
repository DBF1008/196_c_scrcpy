package com.genymobile.scrcpy.model;

/**
 * Parses and holds the structured representation of a start-app query string.
 *
 * <p>Query grammar:
 * <pre>
 *   query  := [flags] target [@displayId]
 *   flags  := ('+' | '?' | '~')+   // order-independent, each at most once
 *   target := &lt;non-empty string&gt;
 * </pre>
 *
 * <ul>
 *   <li>{@code +} — force-stop the app before launching</li>
 *   <li>{@code ?} — search by app name (prefix match) instead of exact package name</li>
 *   <li>{@code ~} — dry-run: resolve and report, do not execute</li>
 *   <li>{@code @N} — target display ID N explicitly</li>
 * </ul>
 *
 * <p>This class is pure Java with no Android dependencies.
 */
public final class AppQuery {

    public static final int DISPLAY_DEFAULT = -1;

    private final String target;
    private final boolean forceStop;
    private final boolean nameSearch;
    private final boolean dryRun;
    private final int displayId;

    private AppQuery(String target, boolean forceStop, boolean nameSearch, boolean dryRun, int displayId) {
        this.target = target;
        this.forceStop = forceStop;
        this.nameSearch = nameSearch;
        this.dryRun = dryRun;
        this.displayId = displayId;
    }

    /**
     * Parse a raw query string into an {@link AppQuery}.
     *
     * @param raw the raw query string from the control message
     * @return the parsed query
     * @throws IllegalArgumentException if the query is malformed
     */
    public static AppQuery parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Query must not be empty");
        }

        boolean forceStop = false;
        boolean nameSearch = false;
        boolean dryRun = false;
        int pos = 0;

        // Parse leading flags
        while (pos < raw.length()) {
            char c = raw.charAt(pos);
            if (c == '+') {
                if (forceStop) {
                    throw new IllegalArgumentException("Duplicate '+' flag");
                }
                forceStop = true;
                pos++;
            } else if (c == '?') {
                if (nameSearch) {
                    throw new IllegalArgumentException("Duplicate '?' flag");
                }
                nameSearch = true;
                pos++;
            } else if (c == '~') {
                if (dryRun) {
                    throw new IllegalArgumentException("Duplicate '~' flag");
                }
                dryRun = true;
                pos++;
            } else {
                break;
            }
        }

        String remaining = raw.substring(pos);
        if (remaining.isEmpty()) {
            throw new IllegalArgumentException("Query target must not be empty");
        }

        // Parse trailing @displayId
        int displayId = DISPLAY_DEFAULT;
        int atIndex = remaining.lastIndexOf('@');
        if (atIndex >= 0) {
            String displayPart = remaining.substring(atIndex + 1);
            if (displayPart.isEmpty()) {
                throw new IllegalArgumentException("Display id must not be empty after '@'");
            }
            try {
                displayId = Integer.parseInt(displayPart);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid display id: \"" + displayPart + "\"");
            }
            remaining = remaining.substring(0, atIndex);
        }

        if (remaining.isEmpty()) {
            throw new IllegalArgumentException("Query target must not be empty");
        }

        return new AppQuery(remaining, forceStop, nameSearch, dryRun, displayId);
    }

    public String getTarget() {
        return target;
    }

    public boolean isForceStop() {
        return forceStop;
    }

    public boolean isNameSearch() {
        return nameSearch;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public int getDisplayId() {
        return displayId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AppQuery{");
        if (forceStop) {
            sb.append("forceStop, ");
        }
        if (nameSearch) {
            sb.append("nameSearch, ");
        }
        if (dryRun) {
            sb.append("dryRun, ");
        }
        sb.append("target=\"").append(target).append("\"");
        if (displayId != DISPLAY_DEFAULT) {
            sb.append(", display=").append(displayId);
        }
        sb.append('}');
        return sb.toString();
    }
}
