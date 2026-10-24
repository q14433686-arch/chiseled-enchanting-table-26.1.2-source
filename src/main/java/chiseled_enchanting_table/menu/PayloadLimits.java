package chiseled_enchanting_table.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;

/** Bound length-prefixed data before allocation/iteration; never treat negative counts as empty. */
public final class PayloadLimits {
    public static final int MAX_COLLECTION_ENTRIES = 65_536;
    public static final int MAX_COST_OPTIONS = 256;
    public static final int MAX_BUNDLE_STACKS = 64;
    public static final int MAX_TOTAL_COST_STACKS = 131_072;
    /** Dashboard cap: shelf scans are bounded well below the generic collection limit. */
    public static final int MAX_TOME_BOOK_ENTRIES = 256;
    /** Per-book enchantment list cap used by the tome dashboard wire format. */
    public static final int MAX_TOME_BOOK_ENCHANTMENTS = 64;

    // Audit NET-03: named bounds for the apply payload, enforced at decode time in the
    // record's compact constructor instead of deep inside business logic.
    /** Wire-level enchantment level ceiling (0 is the DISENCHANT_ALL sentinel). */
    public static final int MAX_ENCHANTMENT_LEVEL = 255;
    /** Maximum accepted length of a stringified enchantment/transformation identifier. */
    public static final int MAX_IDENTIFIER_LENGTH = 512;
    /** Maximum accepted length of an OptionType wire token. */
    public static final int MAX_OPTION_TOKEN_LENGTH = 64;
    /** Container id sentinel used for payloads not bound to an open menu. */
    public static final int NO_CONTAINER = -1;

    // Audit R-01: entry/stack counts alone do not bound encoded size. The cost-table
    // builder tracks a conservative byte estimate and truncates (marking the table
    // incomplete) before the payload can exceed vanilla's custom-payload ceiling.
    /** Estimated fixed header size (flags, configs, ids) of EnchantCostTablePayload. */
    public static final int ESTIMATED_HEADER_BYTES = 8_192;
    /** Conservative per-row estimate (token + identifier + ints + option counts). */
    public static final int ESTIMATED_BYTES_PER_ROW = 96;
    /** Conservative per-stack estimate for plain stacks without custom components. */
    public static final int ESTIMATED_BYTES_PER_STACK = 96;
    /** Total estimated-byte budget for one EnchantCostTablePayload. */
    public static final int MAX_ESTIMATED_PAYLOAD_BYTES = 900_000;

    private PayloadLimits() {}

    /**
     * Conservative encoded-size estimate for an {@link net.minecraft.world.item.ItemStack},
     * accounting for custom data, lore, custom names, and non-default component patches so
     * datapack cost stacks with rich components cannot blow past {@link #MAX_ESTIMATED_PAYLOAD_BYTES}.
     */
    public static int estimateStackBytes(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 8;
        }
        int bytes = ESTIMATED_BYTES_PER_STACK;
        var patch = stack.getComponentsPatch();
        if (patch == null || patch.isEmpty()) {
            return bytes;
        }
        bytes += patch.size() * 48;
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null && !customData.isEmpty()) {
            bytes += Math.max(64, customData.copyTag().toString().length() * 2);
        }
        var customName = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (customName != null) {
            bytes += Math.max(32, customName.getString().length() * 4);
        }
        var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            for (var line : lore.lines()) {
                bytes += 24 + line.getString().length() * 4;
            }
        }
        return bytes;
    }

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
