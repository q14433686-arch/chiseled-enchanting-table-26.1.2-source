package chiseled_enchanting_table.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;

/** Bound length-prefixed data before allocation/iteration; never treat negative counts as empty. */
public final class PayloadLimits {
    public static final int MAX_COLLECTION_ENTRIES = 65_536;

    private PayloadLimits() {}

    public static int readCount(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_COLLECTION_ENTRIES) {
            throw new IllegalArgumentException("Invalid enchanting payload collection count: " + count);
        }
        return count;
    }
}
