package chiseled_enchanting_table.menu;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.utils.EnchantmentWithLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    public static final Identifier LIVE_SHELF_STATE_PACKET_ID = ChiseledEnchantingTable.identifier("live_shelf_state");
    public static final Identifier APPLY_CHISELED_ENCHANTMENT_PACKET_ID = ChiseledEnchantingTable.identifier("apply_chiseled_enchantment");
    public static final Identifier REROLL_EPIPHANY_PACKET_ID = ChiseledEnchantingTable.identifier("reroll_epiphany");
    public static final Identifier FREEZE_EPIPHANY_PACKET_ID = ChiseledEnchantingTable.identifier("freeze_epiphany");
    public static final Identifier REOPEN_TABLE_PACKET_ID = ChiseledEnchantingTable.identifier("reopen_table");

    public enum OptionType {
        ENCHANTMENT("enchantment"),
        TRANSFORMATION("transformation"),
        BOOK_COMBINE_UPGRADE("book_combine_upgrade"),
        BOOK_RESEARCH_UPGRADE("book_research_upgrade"),
        TREASURE_EPIPHANY("treasure_epiphany"),
        DISENCHANT_ALL("disenchant_all"),
        DOWNGRADE_ENCHANTMENT("downgrade_enchantment"),
        STRIP_SINGLE_ENCHANTMENT("strip_single_enchantment"),
        PURGE_CURSE("purge_curse");

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

    public record TransformationDisplay(
        Identifier id,
        Identifier input_item,
        boolean input_is_tag,
        Identifier output_item,
        int level,
        int min_player_level,
        int deducted_levels
    ) {
        public TransformationDisplay(Identifier id, Identifier input_item, Identifier output_item, int level) {
            this(id, input_item, false, output_item, level, 1, 0);
        }

        public TransformationDisplay(
            Identifier id,
            Identifier input_item,
            boolean input_is_tag,
            Identifier output_item,
            int level,
            int min_player_level
        ) {
            this(id, input_item, input_is_tag, output_item, level, min_player_level, 0);
        }

        public static void write(RegistryFriendlyByteBuf buf, TransformationDisplay value) {
            buf.writeIdentifier(value.id());
            buf.writeIdentifier(value.input_item());
            buf.writeBoolean(value.input_is_tag());
            buf.writeIdentifier(value.output_item());
            buf.writeInt(value.level());
            buf.writeVarInt(Math.max(1, value.min_player_level()));
            buf.writeVarInt(Math.max(0, value.deducted_levels()));
        }

        public static TransformationDisplay read(RegistryFriendlyByteBuf buf) {
            Identifier id = buf.readIdentifier();
            Identifier input = buf.readIdentifier();
            boolean isTag = buf.readBoolean();
            Identifier output = buf.readIdentifier();
            int level = buf.readInt();
            int minPlayerLevel = Math.max(1, buf.readVarInt());
            int deductedLevels = Math.max(0, buf.readVarInt());
            return new TransformationDisplay(id, input, isTag, output, level, minPlayerLevel, deductedLevels);
        }
    }

    public record AvailableEnchantmentPayload(
        Set<EnchantmentWithLevel> unlocked_enchantements,
        List<TransformationDisplay> transformations,
        int xp_discount_percent,
        int regular_bookshelves,
        int chiseled_book_count,
        int high_tier_chiseled_books,
        int epiphany_seed,
        Map<Identifier, Integer> stored_master_levels
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AvailableEnchantmentPayload> AVAILABLE_CHISELED_ENCHANTMENT_PAYLOAD_ID =
            new CustomPacketPayload.Type<>(AVAILABLE_CHISELED_ENCHANTMENT_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AvailableEnchantmentPayload> AVAILABLE_CHISELED_ENCHANTMENT_CODEC =
            CustomPacketPayload.codec(AvailableEnchantmentPayload::write, AvailableEnchantmentPayload::read);

        public AvailableEnchantmentPayload(Set<EnchantmentWithLevel> unlocked_enchantements) {
            this(unlocked_enchantements, buildTransformationDisplays(), 0, 0, 0, 0, 1, Map.of());
        }

        public AvailableEnchantmentPayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int epiphany_seed
        ) {
            this(unlocked_enchantements, buildTransformationDisplays(), xp_discount_percent, regular_bookshelves, chiseled_book_count, 0, epiphany_seed, Map.of());
        }

        public AvailableEnchantmentPayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int epiphany_seed,
            Map<Identifier, Integer> stored_master_levels
        ) {
            this(unlocked_enchantements, buildTransformationDisplays(), xp_discount_percent, regular_bookshelves, chiseled_book_count, 0, epiphany_seed, stored_master_levels);
        }

        public AvailableEnchantmentPayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int high_tier_chiseled_books,
            int epiphany_seed,
            Map<Identifier, Integer> stored_master_levels
        ) {
            this(unlocked_enchantements, buildTransformationDisplays(), xp_discount_percent, regular_bookshelves, chiseled_book_count, high_tier_chiseled_books, epiphany_seed, stored_master_levels);
        }

        private static List<TransformationDisplay> buildTransformationDisplays() {
            List<TransformationDisplay> displays = new ArrayList<>();
            for (Object[] tuple : ChiseledEnchantingTableData.transformationDisplayTuples()) {
                boolean isTag = tuple.length > 4 && Boolean.TRUE.equals(tuple[4]);
                int minLvl = tuple.length > 5 ? (int) tuple[5] : 1;
                int dedLvl = tuple.length > 6 ? (int) tuple[6] : 0;
                displays.add(new TransformationDisplay(
                    (Identifier) tuple[0],
                    (Identifier) tuple[1],
                    isTag,
                    (Identifier) tuple[2],
                    (int) tuple[3],
                    minLvl,
                    dedLvl
                ));
            }
            return List.copyOf(displays);
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeCollection(unlocked_enchantements, (target, value) -> EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.encode((RegistryFriendlyByteBuf) target, value));
            buf.writeCollection(transformations, (target, value) -> TransformationDisplay.write((RegistryFriendlyByteBuf) target, value));
            buf.writeVarInt(Math.max(0, xp_discount_percent));
            buf.writeVarInt(Math.max(0, regular_bookshelves));
            buf.writeVarInt(Math.max(0, chiseled_book_count));
            buf.writeVarInt(Math.max(0, high_tier_chiseled_books));
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
            Set<EnchantmentWithLevel> unlocked = buf.readCollection(HashSet::new, source -> EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.decode((RegistryFriendlyByteBuf) source));
            List<TransformationDisplay> transformations = buf.readList(source -> TransformationDisplay.read((RegistryFriendlyByteBuf) source));
            int xpDiscountPercent = buf.readVarInt();
            int regularBookshelves = buf.readVarInt();
            int chiseledBookCount = buf.readVarInt();
            int highTierChiseledBooks = buf.readVarInt();
            int epiphanySeed = buf.readInt();
            int masterSize = buf.readVarInt();
            Map<Identifier, Integer> masterLevels = new java.util.HashMap<>(Math.max(4, masterSize * 2));
            for (int i = 0; i < masterSize; i++) {
                Identifier id = buf.readIdentifier();
                int lvl = buf.readVarInt();
                masterLevels.put(id, Math.max(1, lvl));
            }
            return new AvailableEnchantmentPayload(
                unlocked,
                transformations,
                xpDiscountPercent,
                regularBookshelves,
                chiseledBookCount,
                highTierChiseledBooks,
                epiphanySeed,
                Map.copyOf(masterLevels)
            );
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return AVAILABLE_CHISELED_ENCHANTMENT_PAYLOAD_ID;
        }
    }

    /**
     * S2C real-time synchronization packet pushing updated shelf counts, stored Master Tomes,
     * and unlocked enchantments to an already-open {@link ChiseledEnchantingTableScreenHandler}
     * whenever surrounding shelves change while the GUI stays open.
     */
    public record LiveShelfStatePayload(
        Set<EnchantmentWithLevel> unlocked_enchantements,
        int xp_discount_percent,
        int regular_bookshelves,
        int chiseled_book_count,
        int high_tier_chiseled_books,
        Map<Identifier, Integer> stored_master_levels
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LiveShelfStatePayload> TYPE =
            new CustomPacketPayload.Type<>(LIVE_SHELF_STATE_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, LiveShelfStatePayload> CODEC =
            CustomPacketPayload.codec(LiveShelfStatePayload::write, LiveShelfStatePayload::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeCollection(unlocked_enchantements, (target, value) -> EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.encode((RegistryFriendlyByteBuf) target, value));
            buf.writeVarInt(Math.max(0, xp_discount_percent));
            buf.writeVarInt(Math.max(0, regular_bookshelves));
            buf.writeVarInt(Math.max(0, chiseled_book_count));
            buf.writeVarInt(Math.max(0, high_tier_chiseled_books));
            buf.writeVarInt(stored_master_levels == null ? 0 : stored_master_levels.size());
            if (stored_master_levels != null) {
                for (var entry : stored_master_levels.entrySet()) {
                    buf.writeIdentifier(entry.getKey());
                    buf.writeVarInt(Math.max(1, entry.getValue()));
                }
            }
        }

        private static LiveShelfStatePayload read(RegistryFriendlyByteBuf buf) {
            Set<EnchantmentWithLevel> unlocked = buf.readCollection(HashSet::new, source -> EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.decode((RegistryFriendlyByteBuf) source));
            int xpDiscountPercent = buf.readVarInt();
            int regularBookshelves = buf.readVarInt();
            int chiseledBookCount = buf.readVarInt();
            int highTierChiseledBooks = buf.readVarInt();
            int masterSize = buf.readVarInt();
            Map<Identifier, Integer> masterLevels = new java.util.HashMap<>(Math.max(4, masterSize * 2));
            for (int i = 0; i < masterSize; i++) {
                Identifier id = buf.readIdentifier();
                int lvl = buf.readVarInt();
                masterLevels.put(id, Math.max(1, lvl));
            }
            return new LiveShelfStatePayload(
                unlocked,
                xpDiscountPercent,
                regularBookshelves,
                chiseledBookCount,
                highTierChiseledBooks,
                Map.copyOf(masterLevels)
            );
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
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

    public record ReopenTablePayload(net.minecraft.core.BlockPos pos) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ReopenTablePayload> TYPE =
            new CustomPacketPayload.Type<>(REOPEN_TABLE_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ReopenTablePayload> CODEC =
            CustomPacketPayload.codec(
                (payload, buf) -> buf.writeBlockPos(payload.pos()),
                buf -> new ReopenTablePayload(buf.readBlockPos())
            );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
