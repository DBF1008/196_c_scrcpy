package com.genymobile.scrcpy.control;

/**
 * Parses the start-app request name received in a {@code TYPE_START_APP} control message.
 *
 * <p>The grammar (in order) is:
 * <ul>
 *     <li>an optional leading {@code '+'} requesting the app to be force-stopped before being started;</li>
 *     <li>an optional leading {@code '?'} requesting a search by app name instead of by package name;</li>
 *     <li>the remaining string: the package name (or the search prefix when {@code '?'} is present).</li>
 * </ul>
 *
 * <p>The prefixes must appear in that order: in {@code "?+foo"} the {@code '+'} is part of the name, since {@code '+'} is only consumed when it is
 * the very first character.
 */
public final class StartAppParser {

    private final boolean forceStop;
    private final boolean searchByName;
    private final String name;

    private StartAppParser(boolean forceStop, boolean searchByName, String name) {
        this.forceStop = forceStop;
        this.searchByName = searchByName;
        this.name = name;
    }

    public static StartAppParser parse(String rawName) {
        String name = rawName;

        boolean forceStop = name.startsWith("+");
        if (forceStop) {
            name = name.substring(1);
        }

        boolean searchByName = name.startsWith("?");
        if (searchByName) {
            name = name.substring(1);
        }

        return new StartAppParser(forceStop, searchByName, name);
    }

    public boolean isForceStop() {
        return forceStop;
    }

    public boolean isSearchByName() {
        return searchByName;
    }

    public String getName() {
        return name;
    }
}
