package com.goldskinmc.lightworks.beam;

/**
 * A Pan-Tilt Head's swing back and forth: turns the shaft's steady movement into movement that bounces between
 * {@code arc} degrees either way of where the sweep began, losing no speed at the ends. With no arc the movement
 * passes through unchanged and the next sweep begins wherever the head then points.
 */
public final class Sweep {
    private float offset;
    private int direction = 1;

    /** Applies this tick's {@code move} degrees and returns how far the head really moves. */
    public float step(float move, float arc) {
        if (arc <= 0) {
            offset = 0;
            direction = 1;
            return move;
        }
        float before = offset;
        if (Math.abs(offset) > arc) {
            // The arc was made smaller: come back to it, then sweep as usual
            float side = Math.signum(offset);
            direction = -(int) side * (move < 0 ? -1 : 1);
            offset -= side * Math.min(Math.abs(move), Math.abs(offset) - arc);
            return offset - before;
        }
        float next = offset + direction * move;
        // A fast shaft on a small arc can bounce several times in a tick
        while (Math.abs(next) > arc) {
            next = Math.signum(next) * 2 * arc - next;
            direction = -direction;
        }
        offset = next;
        return offset - before;
    }

    /** What {@link #step} would return, leaving the sweep as it is. */
    public float peek(float move, float arc) {
        if (arc <= 0) {
            return move;
        }
        float savedOffset = offset;
        int savedDirection = direction;
        float step = step(move, arc);
        offset = savedOffset;
        direction = savedDirection;
        return step;
    }

    public float offset() {
        return offset;
    }

    public int direction() {
        return direction;
    }

    /** Back to a saved or synced state. */
    public void set(float offset, int direction) {
        this.offset = offset;
        this.direction = direction < 0 ? -1 : 1;
    }
}
