package enchantment_fusion.mechanics.signatures;

/** One exact Mending value effect, without a reroll or attributing someone else's repair. */
public final class RealRepairObservation {
    private int depth, calls, rank;
    private boolean ambiguous, foreignChanged;
    private float before, after;
    public void enter() { if (++depth != 1) ambiguous = true; }
    public void nested() { ambiguous = true; }
    public void record(boolean ours, int level, float input, float output, boolean completed) {
        if (!completed || depth != 1 || !Float.isFinite(input) || !Float.isFinite(output)) { ambiguous = true; return; }
        if (ours) { calls++; rank = level; before = input; after = output; }
        else if (Float.compare(input, output) != 0) foreignChanged = true;
    }
    public void exit() { if (depth <= 0) ambiguous = true; else depth--; }
    public boolean proved(int expectedLevel, int totalInput, int totalOutput) {
        return !ambiguous && !foreignChanged && depth == 0 && calls == 1 && rank == expectedLevel
            && totalInput > 0 && Float.compare(before, (float) totalInput) == 0
            && after > before && Math.max(0, (int) after) == totalOutput;
    }
}
