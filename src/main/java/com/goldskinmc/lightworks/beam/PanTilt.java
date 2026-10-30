package com.goldskinmc.lightworks.beam;

/**
 * A light's aim as a player sets it on its two value boards: {@code pan} degrees round the vertical,
 * positive to the right (clockwise seen from above), and {@code tilt} degrees up from level (negative is
 * down). Pan counts from north on a floor or ceiling and from straight out on a wall, so a light placed
 * level reads 0 and 0 on a wall. Whole degrees, like {@link Aim}.
 * <p>
 * The light itself stores an {@link Aim}, which is relative to its mount; these are converted both ways.
 * On a floor or ceiling the conversion is exact, on a wall it rounds to the nearest degree, so a light
 * keeps the values last set on its boards next to its aim (see {@link #matches}).
 * <p>
 * Pure maths with no Minecraft classes, so it is unit-tested without booting the game.
 */
public record PanTilt(int pan, int tilt) {
    public static final PanTilt LEVEL = new PanTilt(0, 0);

    /** Largest pan either way for a light mounted with vertical facing {@code fy}: a wall light faces out of it. */
    public static int maxPan(int fy) {
        return fy != 0 ? 180 : 90;
    }

    /** Highest tilt for vertical facing {@code fy}: a ceiling light cannot point above level. */
    public static int maxTilt(int fy) {
        return fy < 0 ? 0 : 90;
    }

    /** Lowest tilt for vertical facing {@code fy}: a floor light cannot point below level. */
    public static int minTilt(int fy) {
        return fy > 0 ? 0 : -90;
    }

    /** This pan and tilt limited to what a light mounted with vertical facing {@code fy} can point at. */
    public PanTilt clamp(int fy) {
        int limit = maxPan(fy);
        int p = limit == 180 ? Math.floorMod(pan + 179, 360) - 179 : Math.max(-limit, Math.min(limit, pan));
        return new PanTilt(p, Math.max(minTilt(fy), Math.min(maxTilt(fy), tilt)));
    }

    /** Unit direction for a light mounted with facing (fx, fy, fz). */
    public double[] direction(int fx, int fy, int fz) {
        double[] r = reference(fx, fy, fz);
        double p = Math.toRadians(pan);
        double t = Math.toRadians(tilt);
        double hx = r[0] * Math.cos(p) - r[1] * Math.sin(p);
        double hz = r[0] * Math.sin(p) + r[1] * Math.cos(p);
        return new double[] {hx * Math.cos(t), Math.sin(t), hz * Math.cos(t)};
    }

    /** The aim (relative to the mount) that points this way. */
    public Aim toAim(int fx, int fy, int fz) {
        double[] d = direction(fx, fy, fz);
        return Aim.fromDirection(fx, fy, fz, d[0], d[1], d[2]);
    }

    /** True if these values point a light mounted with facing (fx, fy, fz) along {@code aim}. */
    public boolean matches(int fx, int fy, int fz, Aim aim) {
        return toAim(fx, fy, fz).equals(aim);
    }

    /** The pan and tilt nearest {@code aim}; pan is 0 when the light points straight up or down. */
    public static PanTilt of(int fx, int fy, int fz, Aim aim) {
        double[] d = aim.direction(fx, fy, fz);
        int tilt = (int) Math.round(Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, d[1])))));
        if (Math.hypot(d[0], d[2]) < 1e-6) {
            return new PanTilt(0, tilt);
        }
        double[] r = reference(fx, fy, fz);
        double sin = r[0] * d[2] - r[1] * d[0];
        double cos = r[0] * d[0] + r[1] * d[2];
        return new PanTilt((int) Math.round(Math.toDegrees(Math.atan2(sin, cos))), tilt).clamp(fy);
    }

    /** {x, z} of the level direction pan 0 points along: north on a floor or ceiling, else out of the wall. */
    private static double[] reference(int fx, int fy, int fz) {
        return fy != 0 ? new double[] {0, -1} : new double[] {fx, fz};
    }
}
