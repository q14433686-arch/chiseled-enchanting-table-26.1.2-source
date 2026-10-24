package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Capability channel, independent of the release version. Bump its ID whenever any menu/cost payload
 * wire layout changes, and version the affected data channel IDs too. Registering in both
 * directions advertises compatibility, so no probe packet has to be sent before the first
 * cost/menu packet can safely be decoded.
 *
 * <p>Ported from the 26.3 line, re-targeted to this Minecraft line: the revision string carries
 * {@code 26_2} and the legacy channel is the plain {@code enchant_cost_table} id this line shipped
 * before the protocol was versioned. Only two channels were ever
 * shipped on this line, so there are exactly two legacy probes: the unversioned
 * {@code enchant_cost_table} id and {@code ..._26_2_revision_5}. (The 26.3 branch needs three
 * because it also shipped a {@code ..._26_3_revision_4} layout that never existed here.)</p>
 */
public final class EnchantingProtocol {
    public static final String REVISION = "26_2_revision_6";

    /** Query only. Never register a decoder on this old channel: an old server may send its
     * join-time packet before the client JOIN callback can disconnect it. */
    public static final CustomPacketPayload.Type<EnchantCostTablePayload> LEGACY_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table"));

    /** The revision-5 wire layout of this line. Query only, same reasoning as {@link #LEGACY_COST_TYPE}. */
    public static final CustomPacketPayload.Type<EnchantCostTablePayload> PREVIOUS_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table_26_2_revision_5"));

    public static final String MISMATCH_MESSAGE = "Chiseled Enchanting Table network protocol mismatch. "
        + "Install the same current Minecraft 26.2 build on both client and server (version labels alone are not enough).";

    private EnchantingProtocol() {}

    /** {@code peerHasMod} is true whenever the peer speaks any version of this mod's cost channel. */
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
