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
 */
public final class ChiseledEnchantingMenuPayloads {
    private ChiseledEnchantingMenuPayloads() {}

    public static final Identifier AVAILABLE_CHISELED_ENCHANTMENT_PACKET_ID = ChiseledEnchantingTable.identifier("available_chiseled_enchantment");
    public static final Identifier APPLY_CHISELED_ENCHANTMENT_PACKET_ID = ChiseledEnchantingTable.identifier("apply_chiseled_enchantment");
    public static final Identifier REROLL_EPIPHANY_PACKET_ID = ChiseledEnchantingTable.identifier("reroll_epiphany");
    public static final Identifier FREEZE_EPIPHANY_PACKET_ID = ChiseledEnchantingTable.identifier("freeze_epiphany");

    public enum OptionType {
        ENCHANTMENT("enchantment"),
        TRANSFORMATION("transformation"),
        BOOK_COMBINE_UPGRADE("book_combine_upgrade"),
        BOOK_RESEARCH_UPGRADE("book_research_upgrade"),
        TREASURE_EPIPHANY("treasure_epiphany");

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

    public record AvailableEnchantmentPayload(
        Set<EnchantmentWithLevel> unlocked_enchantements,
        List<TransformationDisplay> transformations,
        int xp_discount_percent,
        int regular_bookshelves,
        int chiseled_book_count,
        int epiphany_seed,
        java.util.Map<Identifier, Integer> stored_master_levels
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AvailableEnchantmentPayload> AVAILABLE_CHISELED_ENCHANTMENT_PAYLOAD_ID =
            new CustomPacketPayload.Type<>(AVAILABLE_CHISELED_ENCHANTMENT_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AvailableEnchantmentPayload> AVAILABLE_CHISELED_ENCHANTMENT_CODEC =
            CustomPacketPayload.codec(AvailableEnchantmentPayload::write, AvailableEnchantmentPayload::read);

        public AvailableEnchantmentPayload(Set<EnchantmentWithLevel> unlocked_enchantements) {
            this(unlocked_enchantements, buildTransformationDisplays(), 0, 0, 0, 1, java.util.Map.of());
        }

        public AvailableEnchantmentPayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int epiphany_seed
        ) {
            this(unlocked_enchantements, buildTransformationDisplays(), xp_discount_percent, regular_bookshelves, chiseled_book_count, epiphany_seed, java.util.Map.of());
        }

        public AvailableEnchantmentPayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int epiphany_seed,
            java.util.Map<Identifier, Integer> stored_master_levels
        ) {
            this(unlocked_enchantements, buildTransformationDisplays(), xp_discount_percent, regular_bookshelves, chiseled_book_count, epiphany_seed, stored_master_levels);
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
            // 26.3 removed FriendlyByteBuf's collection helpers; preserve the same VarInt-prefixed
            // wire representation explicitly for each collection.
            buf.writeVarInt(unlocked_enchantements.size());
            for (EnchantmentWithLevel value : unlocked_enchantements) {
                EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.encode(buf, value);
            }
            buf.writeVarInt(transformations.size());
            for (TransformationDisplay value : transformations) {
                TransformationDisplay.write(buf, value);
            }
            buf.writeVarInt(Math.max(0, xp_discount_percent));
            buf.writeVarInt(Math.max(0, regular_bookshelves));
            buf.writeVarInt(Math.max(0, chiseled_book_count));
            buf.writeInt(epiphany_seed);
            buf.writeVarInt(stored_master_levels == null ? 0 : stored_master_levels.size());
            if (stored_master_levels != null) {
                for (var entry : stored_master_levels.entrySet()) {
                    buf.writeIdentifier(entry.getKey());
                    buf.writeVarInt(Math.max(1, entry.getValue()));
                }
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
            int xpDiscountPercent = buf.readVarInt();
            int regularBookshelves = buf.readVarInt();
            int chiseledBookCount = buf.readVarInt();
            int epiphanySeed = buf.readInt();
            int masterSize = buf.readVarInt();
            java.util.Map<Identifier, Integer> masterLevels = new java.util.HashMap<>(Math.max(4, masterSize * 2));
            for (int i = 0; i < masterSize; i++) {
                Identifier id = buf.readIdentifier();
                int lvl = buf.readVarInt();
                masterLevels.put(id, Math.max(1, lvl));
            }
            return new AvailableEnchantmentPayload(unlocked, transformations, xpDiscountPercent, regularBookshelves, chiseledBookCount, epiphanySeed, java.util.Map.copyOf(masterLevels));
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

    public record RerollEpiphanyPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RerollEpiphanyPayload> TYPE =
            new CustomPacketPayload.Type<>(REROLL_EPIPHANY_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, RerollEpiphanyPayload> CODEC =
            CustomPacketPayload.codec((payload, buf) -> {}, buf -> new RerollEpiphanyPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FreezeEpiphanyPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FreezeEpiphanyPayload> TYPE =
            new CustomPacketPayload.Type<>(FREEZE_EPIPHANY_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, FreezeEpiphanyPayload> CODEC =
            CustomPacketPayload.codec((payload, buf) -> {}, buf -> new FreezeEpiphanyPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
