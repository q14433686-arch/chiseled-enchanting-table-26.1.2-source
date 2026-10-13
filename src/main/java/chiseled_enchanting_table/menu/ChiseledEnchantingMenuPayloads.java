package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Networking payloads and the option-type model for the chiseled enchanting menu.
 *
 * <p>Architecture note (P15): payload/wire semantics live in the {@code menu} package, separated
 * from both the menu logic ({@code AbstractContainerMenu}) and the gameplay mechanics
 * ({@code mechanics.*}). This makes the wire contract — and the explicit enchantment-vs-transformation
 * discriminator — a first-class, isolated concern.</p>
 */
public final class ChiseledEnchantingMenuPayloads {
    private ChiseledEnchantingMenuPayloads() {}

    public static final Identifier AVAILABLE_CHISELED_ENCHANTMENT_PACKET_ID = ChiseledEnchantingTable.identifier("available_chiseled_enchantment");
    public static final Identifier APPLY_CHISELED_ENCHANTMENT_PACKET_ID = ChiseledEnchantingTable.identifier("apply_chiseled_enchantment");

    /**
     * Option kinds carried by {@link ApplyEnchantmentPayload}. This makes the payload semantics
     * explicit instead of inferring "is this a transformation?" from a data lookup on the server.
     * <p>The string token is what travels over the wire so future kinds can be added without
     * breaking older clients (unknown tokens fall back to {@link #ENCHANTMENT}).</p>
     */
    public enum OptionType {
        ENCHANTMENT("enchantment"),
        TRANSFORMATION("transformation"),
        BOOK_COMBINE_UPGRADE("book_combine_upgrade"),
        BOOK_RESEARCH_UPGRADE("book_research_upgrade");

        private final String token;

        OptionType(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        public static OptionType fromToken(String token) {
            for (OptionType type : values()) {
                if (type.token.equals(token)) {
                    return type;
                }
            }
            return ENCHANTMENT;
        }
    }

    /**
     * Compact, client-displayable description of a data-driven item transformation. Synced from the
     * server when the menu opens so the client UI can render correct names and filter by input item
     * even for datapack-defined transformations the client never loaded itself (e.g. on a dedicated
     * server). Costs stay server-authoritative.
     */
    public record TransformationDisplay(Identifier id, Identifier input_item, Identifier output_item, int level) {
        public static void write(RegistryFriendlyByteBuf buf, TransformationDisplay value) {
            buf.writeIdentifier(value.id());
            buf.writeIdentifier(value.input_item());
            buf.writeIdentifier(value.output_item());
            buf.writeInt(value.level());
        }

        public static TransformationDisplay read(RegistryFriendlyByteBuf buf) {
            Identifier id = buf.readIdentifier();
            Identifier input = buf.readIdentifier();
            Identifier output = buf.readIdentifier();
            int level = buf.readInt();
            return new TransformationDisplay(id, input, output, level);
        }
    }

    public record AvailableEnchantmentPayload(Set<EnchantmentWithLevel> unlocked_enchantements, List<TransformationDisplay> transformations) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AvailableEnchantmentPayload> AVAILABLE_CHISELED_ENCHANTMENT_PAYLOAD_ID = new CustomPacketPayload.Type<>(AVAILABLE_CHISELED_ENCHANTMENT_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AvailableEnchantmentPayload> AVAILABLE_CHISELED_ENCHANTMENT_CODEC =
            CustomPacketPayload.codec(AvailableEnchantmentPayload::write, AvailableEnchantmentPayload::read);

        /** Backwards-friendly constructor that derives the transformation table from loaded data. */
        public AvailableEnchantmentPayload(Set<EnchantmentWithLevel> unlocked_enchantements) {
            this(unlocked_enchantements, buildTransformationDisplays());
        }

        private static List<TransformationDisplay> buildTransformationDisplays() {
            List<TransformationDisplay> displays = new ArrayList<>();
            for (Object[] tuple : ChiseledEnchantingTableData.transformationDisplayTuples()) {
                displays.add(new TransformationDisplay(
                    (Identifier) tuple[0],
                    (Identifier) tuple[1],
                    (Identifier) tuple[2],
                    (int) tuple[3]
                ));
            }
            return List.copyOf(displays);
        }

        private void write(RegistryFriendlyByteBuf buf) {
            // 26.3 removed the FriendlyByteBuf collection helpers (writeCollection / readCollection /
            // readList — the same wave that removed readMap/writeMap). Hand-roll the identical wire
            // format: a VarInt length prefix followed by the elements.
            buf.writeVarInt(unlocked_enchantements.size());
            for (EnchantmentWithLevel value : unlocked_enchantements) {
                EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.encode(buf, value);
            }
            buf.writeVarInt(transformations.size());
            for (TransformationDisplay value : transformations) {
                TransformationDisplay.write(buf, value);
            }
        }

        private static AvailableEnchantmentPayload read(RegistryFriendlyByteBuf buf) {
            int enchantmentCount = buf.readVarInt();
            Set<EnchantmentWithLevel> unlocked = new HashSet<>(Math.max(16, enchantmentCount * 2));
            for (int i = 0; i < enchantmentCount; i++) {
                unlocked.add(EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.decode(buf));
            }
            int transformationCount = buf.readVarInt();
            List<TransformationDisplay> transformations = new ArrayList<>(transformationCount);
            for (int i = 0; i < transformationCount; i++) {
                transformations.add(TransformationDisplay.read(buf));
            }
            return new AvailableEnchantmentPayload(unlocked, transformations);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return AVAILABLE_CHISELED_ENCHANTMENT_PAYLOAD_ID;
        }
    }

    public record ApplyEnchantmentPayload(OptionType option_type, Identifier enchantment_id, int enchantment_level) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ApplyEnchantmentPayload> APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID = new CustomPacketPayload.Type<>(APPLY_CHISELED_ENCHANTMENT_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ApplyEnchantmentPayload> APPLY_CHISELED_ENCHANTMENT_CODEC =
            CustomPacketPayload.codec(ApplyEnchantmentPayload::write, ApplyEnchantmentPayload::read);

        /** Backwards-friendly constructor that defaults to a real enchantment option. */
        public ApplyEnchantmentPayload(Identifier enchantment_id, int enchantment_level) {
            this(OptionType.ENCHANTMENT, enchantment_id, enchantment_level);
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(option_type.token());
            buf.writeInt(enchantment_level);
            buf.writeIdentifier(enchantment_id);
        }

        private static ApplyEnchantmentPayload read(RegistryFriendlyByteBuf buf) {
            OptionType optionType = OptionType.fromToken(buf.readUtf());
            int enchantmentLevel = buf.readInt();
            Identifier enchantmentId = buf.readIdentifier();
            return new ApplyEnchantmentPayload(optionType, enchantmentId, enchantmentLevel);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID;
        }
    }
}
