package enchantment_fusion.mechanics.signatures;

/** Pure preflight for the atomic one-slot sample exchange. */
public final class SealedOreChoice {
    private SealedOreChoice() {}
    public static boolean exchangeable(int inputs, int resultStacks, int resultCount, int stackLimit,
                                       boolean expectedItem, boolean itemEnabled) {
        return inputs == 1 && resultStacks == 1 && resultCount > 0 && stackLimit > 0 && resultCount <= stackLimit
            && expectedItem && itemEnabled;
    }
}
