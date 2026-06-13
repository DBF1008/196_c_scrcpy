package com.genymobile.scrcpy.control;

/**
 * A resource that participates in the {@link Controller} teardown sequence: it can be stopped, then joined.
 */
public interface LifecycleComponent {

    /**
     * Request the component to stop (typically by interrupting its thread). Must not block.
     */
    void stop();

    /**
     * Wait for the component to fully terminate.
     *
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    void join() throws InterruptedException;
}
