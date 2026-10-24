package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Capability channel, independent of the unchanged release version. Bump its ID whenever any
 * menu/cost payload wire layout changes, and version the affected data channel IDs too.
 * Registration in both directions advertises compatibility; no probe needs to be sent before the
 * first cost/menu packet can safely be decoded.
 */
public final class EnchantingProtocol {
    public static final String REVISION = "26_1_2_revision_15";
    // Query only. Never register a decoder on legacy channels: an old server may send its
    // join-time packet before the client JOIN callback can disconnect it.
    //
    // Probe retirement policy (audit DEAD-03): a probe constant stays while any publicly
    // shipped build registered that channel, because it is the only signal that lets the
    // JOIN handshake disconnect an outdated peer gracefully instead of failing later with
    // a raw decode error. Internal-only revisions may be dropped from this list.
    public static final CustomPacketPayload.Type<EnchantCostTablePayload> LEGACY_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> PREVIOUS_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_4"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> REVISION_5_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_5"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> REVISION_6_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_6"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> REVISION_7_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_7"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> REVISION_8_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_8"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> REVISION_9_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_9"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> REVISION_10_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_10"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> REVISION_11_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_11"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> REVISION_12_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_12"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> REVISION_13_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_13"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> REVISION_14_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_14"));

    /** Authoritative list of all historical cost-table probes so handshake checks never omit a revision. */
    public static final List<CustomPacketPayload.Type<EnchantCostTablePayload>> LEGACY_COST_PROBES = List.of(
        LEGACY_COST_TYPE,
        PREVIOUS_COST_TYPE,
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_1")),
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_2")),
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_3")),
        REVISION_5_COST_TYPE,
        REVISION_6_COST_TYPE,
        REVISION_7_COST_TYPE,
        REVISION_8_COST_TYPE,
        REVISION_9_COST_TYPE,
        REVISION_10_COST_TYPE,
        REVISION_11_COST_TYPE,
        REVISION_12_COST_TYPE,
        REVISION_13_COST_TYPE,
        REVISION_14_COST_TYPE
    );

    /**
     * Bare (pre-revision_9) apply channel, query-only like the cost probes above.
     */
    public static final CustomPacketPayload.Type<ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload> LEGACY_APPLY_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("apply_chiseled_enchantment"));

    /** Authoritative list of all historical serverbound apply probes for client JOIN detection. */
    public static final List<CustomPacketPayload.Type<ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload>> LEGACY_APPLY_PROBES = List.of(
        LEGACY_APPLY_TYPE,
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("apply_chiseled_enchantment_26_1_2_revision_9")),
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("apply_chiseled_enchantment_26_1_2_revision_10")),
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("apply_chiseled_enchantment_26_1_2_revision_11")),
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("apply_chiseled_enchantment_26_1_2_revision_12")),
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("apply_chiseled_enchantment_26_1_2_revision_13")),
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("apply_chiseled_enchantment_26_1_2_revision_14"))
    );

    public static final String MISMATCH_MESSAGE = "Chiseled Enchanting Table network protocol mismatch. "
        + "Install the same current Minecraft 26.1.2 build on both client and server (version labels alone are not enough).";

    private EnchantingProtocol() {}

    public static Identifier id(String baseName) {
        return ChiseledEnchantingTable.identifier(baseName + "_" + REVISION);
    }

    public static boolean incompatible(boolean peerHasMod, boolean peerHasRevision) {
        return peerHasMod && !peerHasRevision;
    }

    public static boolean stackSizeIncompatible(int localStackSize, int peerStackSize) {
        return Math.max(1, localStackSize) != Math.max(1, peerStackSize);
    }

    public static Component protocolMismatchComponent() {
        return Component.translatableWithFallback(
            "disconnect.chiseled_enchanting_table.protocol_mismatch",
            MISMATCH_MESSAGE
        );
    }

    public static Component stackSizeMismatchComponent(int localStackSize, int peerStackSize) {
        int localSafe = Math.max(1, localStackSize);
        int peerSafe = Math.max(1, peerStackSize);
        return Component.translatableWithFallback(
            "disconnect.chiseled_enchanting_table.book_stack_mismatch",
            "Chiseled Enchanting Table boot stack-size mismatch: enchanted_book_stack_size is "
                + localSafe + " locally vs " + peerSafe
                + " on peer. Restart after matching config/chiseled_enchanting_table.json.",
            localSafe,
            peerSafe
        );
    }

    public record Capability(int enchantedBookStackSize) implements CustomPacketPayload {
        public Capability {
            enchantedBookStackSize = Math.max(1, Math.min(64, enchantedBookStackSize));
        }

        public Capability() {
            this(ChiseledEnchantingTable.bootEnchantedBookStackSize());
        }

        public static final CustomPacketPayload.Type<Capability> TYPE = new CustomPacketPayload.Type<>(
            ChiseledEnchantingTable.identifier("protocol_" + REVISION));
        public static final StreamCodec<RegistryFriendlyByteBuf, Capability> CODEC =
            CustomPacketPayload.codec(
                (value, buf) -> buf.writeVarInt(value.enchantedBookStackSize()),
                buf -> new Capability(buf.readVarInt())
            );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
