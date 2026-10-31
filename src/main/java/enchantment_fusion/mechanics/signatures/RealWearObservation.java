package enchantment_fusion.mechanics.signatures;

/**
 * Evidence for one real durability request. Observe the already-executed effect, never roll it.
 * Unknown nested effects or subsequent modifications make attribution ambiguous, so award nothing.
 */
public final class RealWearObservation {
    private int depth, observed;
    private boolean seen, ambiguous;

    public void enterEffect() { if (depth++ > 0) ambiguous = true; }
    public void nestedWear() { if (depth > 0) ambiguous = true; }
    public void record(boolean unbreaking, float before, float after) {
        if (depth <= 0 || !Float.isFinite(before) || !Float.isFinite(after)) { ambiguous = true; return; }
        if (unbreaking) {
            if (seen) ambiguous = true;
            seen = true;
            observed = DurabilityReserveMath.savedByEffect(before, after);
        } else if (seen && Float.compare(before, after) != 0) {
            ambiguous = true;
        }
    }
    public void exitEffect() {
        if (depth <= 0) ambiguous = true; else depth--;
    }
    public int acceptedSaved(int requested, int result) {
        if (ambiguous || !seen || depth != 0 || requested <= 0 || result < 0) return 0;
        return DurabilityReserveMath.acceptedSaved(observed, requested, result);
    }
}
