package enchantment_fusion.mechanics.signatures;

/** Evidence from one zero-based acceleration query, not a second or simulated enchantment roll. */
public final class RealReturnObservation {
    private int depth, calls, rank;
    private boolean ambiguous, foreignChanged;
    private float before, after;
    public void enter(boolean sameContext) {
        if (++depth != 1 || !sameContext) ambiguous = true;
    }
    public void record(boolean ours, int level, float input, float output, boolean completed) {
        if (!completed || depth != 1 || !Float.isFinite(input) || !Float.isFinite(output)) { ambiguous = true; return; }
        if (ours) { calls++; rank = level; before = input; after = output; }
        else if (Float.compare(input, output) != 0) foreignChanged = true;
    }
    public void exit() { if (depth <= 0) ambiguous = true; else depth--; }
    public void nested() { ambiguous = true; }
    public boolean proved(int expectedLevel, int result) {
        return !ambiguous && !foreignChanged && depth == 0 && calls == 1 && rank == expectedLevel
            && Float.compare(before, 0.0F) == 0 && after > before && result > 0 && Math.max(0, (int) after) == result;
    }
}
