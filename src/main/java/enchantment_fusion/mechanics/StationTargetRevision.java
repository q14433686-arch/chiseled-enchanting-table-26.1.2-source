package enchantment_fusion.mechanics;

import net.minecraft.world.item.ItemStack;

/**
 * Server-owned, session-only revision of a station's target slot. Tracks both object
 * replacement and in-place component/count changes. Zero is reserved for clients that
 * have not received a revision. It never adds identifiers to or mutates the user's item.
 */
public final class StationTargetRevision {
    private ItemStack reference = ItemStack.EMPTY;
    private ItemStack snapshot = ItemStack.EMPTY;
    private long revision = 1;

    public long observe(ItemStack current) {
        if (current != reference || !ItemStack.matches(current, snapshot)) {
            reference = current;
            snapshot = current.copy();
            revision = revision == Long.MAX_VALUE ? 1 : revision + 1;
        }
        return revision;
    }
}
