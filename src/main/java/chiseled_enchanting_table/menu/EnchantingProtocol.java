package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Capability channel, independent of the unchanged release version. Bump its ID whenever any
 * menu/cost payload wire layout changes. Registration in both directions advertises compatibility;
 * no probe needs to be sent before the first cost/menu packet can safely be decoded.
 */
public final class EnchantingProtocol {
    public static final String MISMATCH_MESSAGE = "Chiseled Enchanting Table network protocol mismatch. "
        + "Install the same current Minecraft 26.3 build on both client and server (version labels alone are not enough).";

    private EnchantingProtocol() {}

    public static boolean incompatible(boolean peerHasMod, boolean peerHasRevision) {
        return peerHasMod && !peerHasRevision;
    }

    public record Capability() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Capability> TYPE = new CustomPacketPayload.Type<>(
            ChiseledEnchantingTable.identifier("protocol_26_3_revision_2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Capability> CODEC =
            CustomPacketPayload.codec((value, buf) -> {}, buf -> new Capability());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
