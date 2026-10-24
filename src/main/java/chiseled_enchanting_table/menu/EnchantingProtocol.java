package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
    public static final String REVISION = "26_1_2_revision_12";
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

    /**
     * Bare (pre-revision_9) apply channel, query-only like the cost probes above. The
     * client JOIN check uses it to detect "peer has some build of this mod" now that the
     * live apply channel itself carries the revision suffix (audit NET-02: the probe and
     * the business channel must not be the same channel).
     */
    public static final CustomPacketPayload.Type<ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload> LEGACY_APPLY_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("apply_chiseled_enchantment"));

    public static final String MISMATCH_MESSAGE = "Chiseled Enchanting Table network protocol mismatch. "
        + "Install the same current Minecraft 26.1.2 build on both client and server (version labels alone are not enough).";

    private EnchantingProtocol() {}

    public static Identifier id(String baseName) {
        return ChiseledEnchantingTable.identifier(baseName + "_" + REVISION);
    }

    public static boolean incompatible(boolean peerHasMod, boolean peerHasRevision) {
        return peerHasMod && !peerHasRevision;
    }

    public record Capability() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Capability> TYPE = new CustomPacketPayload.Type<>(
            ChiseledEnchantingTable.identifier("protocol_" + REVISION));
        public static final StreamCodec<RegistryFriendlyByteBuf, Capability> CODEC =
            CustomPacketPayload.codec((value, buf) -> {}, buf -> new Capability());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
