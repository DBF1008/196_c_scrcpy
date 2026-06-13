package com.genymobile.scrcpy.control;

import java.util.ArrayList;
import java.util.List;

/**
 * Tears down a fixed, ordered set of {@link LifecycleComponent}s in a deterministic order.
 *
 * <p>{@link #stopAll()} stops every component in registration order; {@link #joinAll()} joins every component in registration order. The two are
 * invoked separately (stop first for all components, then join), matching how the server shuts processors down.
 *
 * <p>Registration order is the teardown order, making the resource-recovery sequence explicit and testable.
 */
public final class LifecycleManager {

    private final List<LifecycleComponent> components = new ArrayList<>();

    /**
     * Register a component. {@code null} is ignored, which is convenient for components that only exist in some modes (e.g. the device-message
     * sender or the keep-active thread). Registration order defines the teardown order.
     *
     * @param component the component to register, or {@code null} to skip
     */
    public void register(LifecycleComponent component) {
        if (component != null) {
            components.add(component);
        }
    }

    /**
     * Stop all registered components, in registration order.
     */
    public void stopAll() {
        for (LifecycleComponent component : components) {
            component.stop();
        }
    }

    /**
     * Join all registered components, in registration order.
     *
     * @throws InterruptedException if the current thread is interrupted while joining (remaining components are not joined, matching the previous
     * inline behavior)
     */
    public void joinAll() throws InterruptedException {
        for (LifecycleComponent component : components) {
            component.join();
        }
    }
}
