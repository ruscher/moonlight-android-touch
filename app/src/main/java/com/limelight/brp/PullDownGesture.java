package com.limelight.brp;

/**
 * Decides when a drag on the top handle opens the Control Center.
 *
 * A tap never opens it (no accidental opening); a deliberate pull does: either
 * far enough down, or a quick flick that is clearly downward. Sideways drags are
 * ignored so a finger sliding across the top of a game does not trigger it.
 *
 * Distances are in pixels, given by the caller from dp. Pure Java, unit-tested.
 */
public final class PullDownGesture {
    private final float openDistancePx;
    private final float flickDistancePx;
    private final float flickVelocityPxPerMs;

    private boolean tracking;
    private boolean fired;
    private float startX, startY;
    private long startTime;

    /**
     * @param openDistancePx       a pull this far down always opens
     * @param flickDistancePx      a shorter pull opens if it is fast
     * @param flickVelocityPxPerMs how fast is fast
     */
    public PullDownGesture(float openDistancePx, float flickDistancePx, float flickVelocityPxPerMs) {
        this.openDistancePx = openDistancePx;
        this.flickDistancePx = flickDistancePx;
        this.flickVelocityPxPerMs = flickVelocityPxPerMs;
    }

    public void down(float x, float y, long timeMs) {
        tracking = true;
        fired = false;
        startX = x;
        startY = y;
        startTime = timeMs;
    }

    /** Returns true once, on the move that completes the pull. */
    public boolean move(float x, float y, long timeMs) {
        if (!tracking || fired) {
            return false;
        }
        float dy = y - startY;
        float dx = Math.abs(x - startX);
        if (dx > Math.max(dy, 0) * 1.2f && dx > flickDistancePx) {
            // Mostly sideways: not a pull.
            tracking = false;
            return false;
        }
        if (dy >= openDistancePx) {
            fired = true;
            return true;
        }
        return false;
    }

    /** Returns true if lifting the finger completes a flick. */
    public boolean up(float x, float y, long timeMs) {
        if (!tracking || fired) {
            tracking = false;
            return false;
        }
        tracking = false;
        float dy = y - startY;
        float dx = Math.abs(x - startX);
        long dt = Math.max(1, timeMs - startTime);
        return dy >= flickDistancePx && dy > dx && dy / dt >= flickVelocityPxPerMs;
    }

    public void cancel() {
        tracking = false;
    }
}
