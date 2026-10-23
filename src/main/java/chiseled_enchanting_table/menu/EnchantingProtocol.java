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
 * before the protocol was versioned. The 26.3 branch additionally probes a
 * {@code ..._26_3_revision_4} channel; no such channel ever existed here, so there is no second
 * legacy probe.</p>
 */
public final class EnchantingProtocol {
    public static final String REVISION = "26_2_revision_5";

    /** Query only. Never register a decoder on this old channel: an old server may send its
     * join-time packet before the client JOIN callback can disconnect it. */
    public static final CustomPacketPayload.Type<EnchantCostTablePayload> LEGACY_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table"));

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
