package chiseled_enchanting_table.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;

/** Bound length-prefixed data before allocation/iteration; never treat negative counts as empty. */
public final class PayloadLimits {
    public static final int MAX_COLLECTION_ENTRIES = 65_536;
    public static final int MAX_COLLECTION = MAX_COLLECTION_ENTRIES;
    public static final int MAX_COST_OPTIONS = 256;
    public static final int MAX_BUNDLE_STACKS = 64;
    public static final int MAX_TOTAL_COST_STACKS = 131_072;

    private PayloadLimits() {}

    public static int readCount(RegistryFriendlyByteBuf buf) {
        return readBoundedCount(buf, MAX_COLLECTION_ENTRIES);
    }

    public static int readBoundedCount(RegistryFriendlyByteBuf buf, int maxEntries) {
        int count = buf.readVarInt();
        if (count < 0 || count > maxEntries) {
            throw new IllegalArgumentException("Invalid enchanting payload collection count: " + count + " (max " + maxEntries + ")");
        }
        return count;
    }

    public static void writeBoundedCount(RegistryFriendlyByteBuf buf, int count, int maxEntries) {
        if (count < 0 || count > maxEntries) {
            throw new IllegalArgumentException("Outbound enchanting payload collection count exceeds budget: " + count + " > " + maxEntries);
        }
        buf.writeVarInt(count);
    }
}
