package com.genymobile.scrcpy.control;

import com.genymobile.scrcpy.model.DeviceApp;
import com.genymobile.scrcpy.model.LaunchPlan;
import com.genymobile.scrcpy.model.LaunchQuery;

import java.util.Collections;
import java.util.List;

/**
 * The outcome of resolving a {@link LaunchQuery} against the device's apps and display availability.
 */
public final class LaunchResolution {

    public enum Status {
        /** A single app was resolved; {@link #getPlan()} holds the executable (or dry-run) plan. */
        RESOLVED,
        /** No app matched the query. */
        NOT_FOUND,
        /** Several apps matched; {@link #getCandidates()} lists them in stable order. */
        AMBIGUOUS,
        /** An app was resolved but no display id is available to launch it on. */
        NO_DISPLAY
    }

    private final Status status;
    private final LaunchQuery query;
    private final LaunchPlan plan;
    private final DeviceApp app;
    private final List<DeviceApp> candidates;

    private LaunchResolution(Status status, LaunchQuery query, LaunchPlan plan, DeviceApp app, List<DeviceApp> candidates) {
        this.status = status;
        this.query = query;
        this.plan = plan;
        this.app = app;
        this.candidates = candidates;
    }

    public static LaunchResolution resolved(LaunchQuery query, LaunchPlan plan) {
        return new LaunchResolution(Status.RESOLVED, query, plan, plan.getApp(), Collections.emptyList());
    }

    public static LaunchResolution notFound(LaunchQuery query) {
        return new LaunchResolution(Status.NOT_FOUND, query, null, null, Collections.emptyList());
    }

    public static LaunchResolution ambiguous(LaunchQuery query, List<DeviceApp> candidates) {
        return new LaunchResolution(Status.AMBIGUOUS, query, null, null, candidates);
    }

    public static LaunchResolution noDisplay(LaunchQuery query, DeviceApp app) {
        return new LaunchResolution(Status.NO_DISPLAY, query, null, app, Collections.emptyList());
    }

    public Status getStatus() {
        return status;
    }

    public LaunchQuery getQuery() {
        return query;
    }

    /**
     * @return the resolved plan when {@link #getStatus()} is {@link Status#RESOLVED}, otherwise {@code null}
     */
    public LaunchPlan getPlan() {
        return plan;
    }

    /**
     * @return the resolved app for {@link Status#RESOLVED} and {@link Status#NO_DISPLAY}, otherwise {@code null}
     */
    public DeviceApp getApp() {
        return app;
    }

    /**
     * @return the matching apps in stable order for {@link Status#AMBIGUOUS}, otherwise an empty list
     */
    public List<DeviceApp> getCandidates() {
        return candidates;
    }
}
