package com.genymobile.scrcpy.video;

import java.util.Objects;

/**
 * An inclusive frame-rate range, as a pure (Android-free) value type so it can be used in unit tests.
 */
public final class FpsRange {
    private final int min;
    private final int max;

    public FpsRange(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public boolean contains(int fps) {
        return fps >= min && fps <= max;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FpsRange that = (FpsRange) o;
        return min == that.min && max == that.max;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }

    @Override
    public String toString() {
        return "[" + min + ", " + max + "]";
    }
}
