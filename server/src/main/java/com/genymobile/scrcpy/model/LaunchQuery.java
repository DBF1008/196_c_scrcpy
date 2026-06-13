package com.genymobile.scrcpy.model;

/**
 * A parsed {@code --start-app} request.
 *
 * <p>Wire grammar (backward compatible): {@code [+][!][?[=]]term}
 * <ul>
 *   <li>{@code +} force-stop the app before starting it;
 *   <li>{@code !} dry-run: resolve and log the launch plan without starting anything;
 *   <li>{@code ?} match by app name (prefix), {@code ?=} match by app name (exact);
 *   <li>no {@code ?}: match by exact package name.
 * </ul>
 * The {@code +} and {@code !} flags are optional and may appear in any order before the (optional) match specifier.
 *
 * <p>{@link Scope} is not expressed on the wire; it is a programmatic dimension used by the resolver and may be set through the constructor.
 */
public final class LaunchQuery {

    public enum MatchMode {
        /** Match the exact package name. */
        PACKAGE,
        /** Match apps whose display name starts with the term. */
        NAME_PREFIX,
        /** Match apps whose display name equals the term. */
        NAME_EXACT
    }

    public enum Scope {
        /** System and user apps. */
        ANY,
        /** System apps only. */
        SYSTEM,
        /** User (non-system) apps only. */
        USER
    }

    private final String term;
    private final MatchMode matchMode;
    private final Scope scope;
    private final boolean forceStop;
    private final boolean dryRun;

    public LaunchQuery(String term, MatchMode matchMode, Scope scope, boolean forceStop, boolean dryRun) {
        this.term = term;
        this.matchMode = matchMode;
        this.scope = scope;
        this.forceStop = forceStop;
        this.dryRun = dryRun;
    }

    public static LaunchQuery parse(String text) {
        boolean forceStop = false;
        boolean dryRun = false;

        int i = 0;
        int len = text.length();

        // Leading boolean flags, in any order
        while (i < len) {
            char c = text.charAt(i);
            if (c == '+') {
                forceStop = true;
            } else if (c == '!') {
                dryRun = true;
            } else {
                break;
            }
            i++;
        }

        MatchMode matchMode = MatchMode.PACKAGE;
        if (i < len && text.charAt(i) == '?') {
            i++;
            if (i < len && text.charAt(i) == '=') {
                matchMode = MatchMode.NAME_EXACT;
                i++;
            } else {
                matchMode = MatchMode.NAME_PREFIX;
            }
        }

        String term = text.substring(i);
        return new LaunchQuery(term, matchMode, Scope.ANY, forceStop, dryRun);
    }

    public String getTerm() {
        return term;
    }

    public MatchMode getMatchMode() {
        return matchMode;
    }

    public Scope getScope() {
        return scope;
    }

    public boolean isForceStop() {
        return forceStop;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (forceStop) {
            builder.append('+');
        }
        if (dryRun) {
            builder.append('!');
        }
        switch (matchMode) {
            case NAME_PREFIX:
                builder.append('?');
                break;
            case NAME_EXACT:
                builder.append("?=");
                break;
            default:
                // PACKAGE: no match specifier
                break;
        }
        builder.append(term);
        return builder.toString();
    }
}
