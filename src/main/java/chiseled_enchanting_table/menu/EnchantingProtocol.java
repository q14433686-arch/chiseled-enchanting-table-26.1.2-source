package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Capability channel, independent of the unchanged release version. Bump
 * {@link #CURRENT_REVISION_NUMBER} whenever any menu/cost payload wire layout changes, and record
 * the wire change in {@code docs/PROTOCOL_HISTORY.md}. Registration in both directions advertises
 * compatibility; no probe needs to be sent before the first cost/menu packet can safely be decoded.
 */
public final class EnchantingProtocol {
    public static final int CURRENT_REVISION_NUMBER = 22;
    public static final String REVISION = "26_1_2_revision_" + CURRENT_REVISION_NUMBER;

    // Query only. Never register a decoder on legacy channels: an old server may send its
    // join-time packet before the client JOIN callback can disconnect it.
    //
    // Probe retirement policy (see docs/PROTOCOL_HISTORY.md): a probe channel remains listed while
    // any publicly shipped build registered that channel, because it is the signal that lets the
    // JOIN handshake disconnect an outdated peer gracefully instead of failing later with a raw
    // decode error. Generating 1..(CURRENT_REVISION_NUMBER - 1) automatically prevents forgetting
    // the immediately preceding revision on bump.
    public static final CustomPacketPayload.Type<EnchantCostTablePayload> LEGACY_COST_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("enchant_cost_table"));

    /** Authoritative list of all historical cost-table probes (`enchant_cost_table` + `revision_1..N-1`). */
    public static final List<CustomPacketPayload.Type<EnchantCostTablePayload>> LEGACY_COST_PROBES =
        Stream.concat(
            Stream.of(LEGACY_COST_TYPE),
            IntStream.range(1, CURRENT_REVISION_NUMBER)
                .mapToObj(rev -> new CustomPacketPayload.Type<EnchantCostTablePayload>(
                    ChiseledEnchantingTable.identifier("enchant_cost_table_26_1_2_revision_" + rev)
                ))
        ).toList();

    /**
     * Bare (pre-revision_9) apply channel, query-only like the cost probes above.
     */
    public static final CustomPacketPayload.Type<ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload> LEGACY_APPLY_TYPE =
        new CustomPacketPayload.Type<>(ChiseledEnchantingTable.identifier("apply_chiseled_enchantment"));

    /** Authoritative list of all historical serverbound apply probes (`apply_chiseled_enchantment` + `revision_9..N-1`). */
    public static final List<CustomPacketPayload.Type<ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload>> LEGACY_APPLY_PROBES =
        Stream.concat(
            Stream.of(LEGACY_APPLY_TYPE),
            IntStream.range(9, CURRENT_REVISION_NUMBER)
                .mapToObj(rev -> new CustomPacketPayload.Type<ChiseledEnchantingMenuPayloads.ApplyEnchantmentPayload>(
                    ChiseledEnchantingTable.identifier("apply_chiseled_enchantment_26_1_2_revision_" + rev)
                ))
        ).toList();

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
            if (enchantedBookStackSize < 1 || enchantedBookStackSize > PayloadLimits.MAX_WIRE_CAPABILITY_STACK) {
                throw new IllegalArgumentException(
                    "Capability enchantedBookStackSize out of wire bounds [1, "
                        + PayloadLimits.MAX_WIRE_CAPABILITY_STACK + "]: " + enchantedBookStackSize
                );
            }
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
