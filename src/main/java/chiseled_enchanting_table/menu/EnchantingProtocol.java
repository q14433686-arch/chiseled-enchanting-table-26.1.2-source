package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Capability channel, independent of the unchanged release version. Bump its ID whenever any
 * menu/cost payload wire layout changes. Version the affected data channel IDs too. Registration in both directions advertises compatibility;
 * no probe needs to be sent before the first cost/menu packet can safely be decoded.
 */
public final class EnchantingProtocol {
    public static final String REVISION = "26_3_revision_7";

    // Query only. Never register a decoder on these older channels: an old server may send its
    // join-time packet before the client JOIN callback can disconnect it.
    public static final CustomPacketPayload.Type<EnchantCostTablePayload> LEGACY_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> PREVIOUS_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_3_revision_4"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> REVISION_5_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_3_revision_5"));

    public static final CustomPacketPayload.Type<EnchantCostTablePayload> REVISION_6_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_3_revision_6"));

    public static final String MISMATCH_MESSAGE = "Chiseled Enchanting Table network protocol mismatch. "
        + "Install the same current Minecraft 26.3 build on both client and server (version labels alone are not enough).";

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
