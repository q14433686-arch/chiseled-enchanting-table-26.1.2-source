package enchantment_fusion.mechanics.signatures;

/** Pure horizontal segment/crossing checks for a virtual flame boundary. */
public final class FireSweepMath {
    private FireSweepMath() {}

    public static boolean crosses(double oldX, double oldZ, double newX, double newZ,
                                  double centerX, double centerZ, double forwardX, double forwardZ,
                                  double halfLength, double maximumStep) {
        double moveX = newX - oldX, moveZ = newZ - oldZ;
        double movement2 = moveX * moveX + moveZ * moveZ;
        if (!Double.isFinite(movement2) || movement2 < 1.0E-8D || movement2 > maximumStep * maximumStep) return false;
        double oldSide = (oldX - centerX) * forwardX + (oldZ - centerZ) * forwardZ;
        double newSide = (newX - centerX) * forwardX + (newZ - centerZ) * forwardZ;
        if (!Double.isFinite(oldSide) || !Double.isFinite(newSide)
            || !((oldSide < -0.02D && newSide >= 0.02D) || (oldSide > 0.02D && newSide <= -0.02D))) return false;
        double t = oldSide / (oldSide - newSide);
        double crossX = oldX + moveX * t - centerX, crossZ = oldZ + moveZ * t - centerZ;
        double lateral = crossX * -forwardZ + crossZ * forwardX;
        return Double.isFinite(lateral) && Math.abs(lateral) <= halfLength;
    }

    public static boolean beyondCenter(double x, double z, double cx, double cz, double fx, double fz) {
        return Math.abs((x - cx) * fx + (z - cz) * fz) >= 0.02D;
    }
}
