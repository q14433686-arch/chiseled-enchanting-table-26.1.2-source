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
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

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
    public static final Identifier SKIP_RITUAL_PACKET_ID = ChiseledEnchantingTable.identifier("skip_ritual");
    public static final Identifier SYNC_RITUAL_STATE_PACKET_ID = ChiseledEnchantingTable.identifier("sync_ritual_state");

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
        int deducted_levels,
        boolean unlocked
    ) {
        public TransformationDisplay(Identifier id, Identifier input_item, Identifier output_item, int level) {
            this(id, input_item, false, output_item, level, 1, 0, true);
        }

        public TransformationDisplay(
            Identifier id,
            Identifier input_item,
            boolean input_is_tag,
            Identifier output_item,
            int level,
            int min_player_level
        ) {
            this(id, input_item, input_is_tag, output_item, level, min_player_level, 0, true);
        }

        public TransformationDisplay(
            Identifier id,
            Identifier input_item,
            boolean input_is_tag,
            Identifier output_item,
            int level,
            int min_player_level,
            int deducted_levels
        ) {
            this(id, input_item, input_is_tag, output_item, level, min_player_level, deducted_levels, true);
        }

        public static void write(RegistryFriendlyByteBuf buf, TransformationDisplay value) {
            buf.writeIdentifier(value.id());
            buf.writeIdentifier(value.input_item());
            buf.writeBoolean(value.input_is_tag());
            buf.writeIdentifier(value.output_item());
            buf.writeInt(value.level());
            buf.writeVarInt(Math.max(1, value.min_player_level()));
            buf.writeVarInt(Math.max(0, value.deducted_levels()));
            buf.writeBoolean(value.unlocked());
        }

        public static TransformationDisplay read(RegistryFriendlyByteBuf buf) {
            Identifier id = buf.readIdentifier();
            Identifier input = buf.readIdentifier();
            boolean isTag = buf.readBoolean();
            Identifier output = buf.readIdentifier();
            int level = buf.readInt();
            int minPlayerLevel = Math.max(1, buf.readVarInt());
            int deductedLevels = Math.max(0, buf.readVarInt());
            boolean unlocked = buf.readBoolean();
            return new TransformationDisplay(id, input, isTag, output, level, minPlayerLevel, deductedLevels, unlocked);
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
        Map<Identifier, Integer> stored_master_levels,
        boolean cramped_library,
        int chiseled_bookshelves,
        Map<EnchantmentWithLevel, Integer> master_tome_charges
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AvailableEnchantmentPayload> AVAILABLE_CHISELED_ENCHANTMENT_PAYLOAD_ID =
            new CustomPacketPayload.Type<>(AVAILABLE_CHISELED_ENCHANTMENT_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AvailableEnchantmentPayload> AVAILABLE_CHISELED_ENCHANTMENT_CODEC =
            CustomPacketPayload.codec(AvailableEnchantmentPayload::write, AvailableEnchantmentPayload::read);

        public AvailableEnchantmentPayload(Set<EnchantmentWithLevel> unlocked_enchantements) {
            this(unlocked_enchantements, buildTransformationDisplays(null), 0, 0, 0, 0, 1, Map.of(), false, 0, Map.of());
        }

        public AvailableEnchantmentPayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int epiphany_seed
        ) {
            this(unlocked_enchantements, buildTransformationDisplays(null), xp_discount_percent, regular_bookshelves, chiseled_book_count, 0, epiphany_seed, Map.of(), false, 0, Map.of());
        }

        public AvailableEnchantmentPayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int epiphany_seed,
            Map<Identifier, Integer> stored_master_levels
        ) {
            this(unlocked_enchantements, buildTransformationDisplays(null), xp_discount_percent, regular_bookshelves, chiseled_book_count, 0, epiphany_seed, stored_master_levels, false, 0, Map.of());
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
            this(unlocked_enchantements, buildTransformationDisplays(null), xp_discount_percent, regular_bookshelves, chiseled_book_count, high_tier_chiseled_books, epiphany_seed, stored_master_levels, false, 0, Map.of());
        }

        public AvailableEnchantmentPayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int high_tier_chiseled_books,
            int epiphany_seed,
            Map<Identifier, Integer> stored_master_levels,
            boolean cramped_library,
            @Nullable Player player
        ) {
            this(
                unlocked_enchantements,
                buildTransformationDisplays(player),
                xp_discount_percent,
                regular_bookshelves,
                chiseled_book_count,
                high_tier_chiseled_books,
                epiphany_seed,
                stored_master_levels,
                cramped_library,
                0,
                Map.of()
            );
        }

        public AvailableEnchantmentPayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int high_tier_chiseled_books,
            int epiphany_seed,
            Map<Identifier, Integer> stored_master_levels,
            boolean cramped_library,
            int chiseled_bookshelves,
            Map<EnchantmentWithLevel, Integer> master_tome_charges,
            @Nullable Player player
        ) {
            this(
                unlocked_enchantements,
                buildTransformationDisplays(player),
                xp_discount_percent,
                regular_bookshelves,
                chiseled_book_count,
                high_tier_chiseled_books,
                epiphany_seed,
                stored_master_levels,
                cramped_library,
                chiseled_bookshelves,
                master_tome_charges == null ? Map.of() : Map.copyOf(master_tome_charges)
            );
        }

        public static List<TransformationDisplay> buildTransformationDisplays(@Nullable Player player) {
            List<TransformationDisplay> displays = new ArrayList<>();
            for (Object[] tuple : ChiseledEnchantingTableData.transformationDisplayTuples(player)) {
                boolean isTag = tuple.length > 4 && Boolean.TRUE.equals(tuple[4]);
                int minLvl = tuple.length > 5 ? (int) tuple[5] : 1;
                int dedLvl = tuple.length > 6 ? (int) tuple[6] : 0;
                boolean unlocked = tuple.length <= 7 || Boolean.TRUE.equals(tuple[7]);
                displays.add(new TransformationDisplay(
                    (Identifier) tuple[0],
                    (Identifier) tuple[1],
                    isTag,
                    (Identifier) tuple[2],
                    (int) tuple[3],
                    minLvl,
                    dedLvl,
                    unlocked
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
            buf.writeBoolean(cramped_library);
            buf.writeVarInt(Math.max(0, chiseled_bookshelves));
            writeMasterCharges(buf, master_tome_charges);
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
            boolean crampedLibrary = buf.readBoolean();
            int chiseledBookshelves = buf.readVarInt();
            Map<EnchantmentWithLevel, Integer> masterCharges = readMasterCharges(buf);
            return new AvailableEnchantmentPayload(
                unlocked,
                transformations,
                xpDiscountPercent,
                regularBookshelves,
                chiseledBookCount,
                highTierChiseledBooks,
                epiphanySeed,
                Map.copyOf(masterLevels),
                crampedLibrary,
                chiseledBookshelves,
                masterCharges
            );
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return AVAILABLE_CHISELED_ENCHANTMENT_PAYLOAD_ID;
        }
    }

    public record LiveShelfStatePayload(
        Set<EnchantmentWithLevel> unlocked_enchantements,
        int xp_discount_percent,
        int regular_bookshelves,
        int chiseled_book_count,
        int high_tier_chiseled_books,
        Map<Identifier, Integer> stored_master_levels,
        boolean cramped_library,
        int chiseled_bookshelves,
        Map<EnchantmentWithLevel, Integer> master_tome_charges
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LiveShelfStatePayload> TYPE =
            new CustomPacketPayload.Type<>(LIVE_SHELF_STATE_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, LiveShelfStatePayload> CODEC =
            CustomPacketPayload.codec(LiveShelfStatePayload::write, LiveShelfStatePayload::read);

        public LiveShelfStatePayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int high_tier_chiseled_books,
            Map<Identifier, Integer> stored_master_levels
        ) {
            this(
                unlocked_enchantements,
                xp_discount_percent,
                regular_bookshelves,
                chiseled_book_count,
                high_tier_chiseled_books,
                stored_master_levels,
                false,
                0,
                Map.of()
            );
        }

        public LiveShelfStatePayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int high_tier_chiseled_books,
            Map<Identifier, Integer> stored_master_levels,
            boolean cramped_library
        ) {
            this(
                unlocked_enchantements,
                xp_discount_percent,
                regular_bookshelves,
                chiseled_book_count,
                high_tier_chiseled_books,
                stored_master_levels,
                cramped_library,
                0,
                Map.of()
            );
        }

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
            buf.writeBoolean(cramped_library);
            buf.writeVarInt(Math.max(0, chiseled_bookshelves));
            writeMasterCharges(buf, master_tome_charges);
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
            boolean crampedLibrary = buf.readBoolean();
            int chiseledBookshelves = buf.readVarInt();
            Map<EnchantmentWithLevel, Integer> masterCharges = readMasterCharges(buf);
            return new LiveShelfStatePayload(
                unlocked,
                xpDiscountPercent,
                regularBookshelves,
                chiseledBookCount,
                highTierChiseledBooks,
                Map.copyOf(masterLevels),
                crampedLibrary,
                chiseledBookshelves,
                masterCharges
            );
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeMasterCharges(
        RegistryFriendlyByteBuf buf,
        Map<EnchantmentWithLevel, Integer> charges
    ) {
        buf.writeVarInt(charges == null ? 0 : charges.size());
        if (charges != null) {
            for (var entry : charges.entrySet()) {
                EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.encode(buf, entry.getKey());
                buf.writeVarInt(entry.getValue());
            }
        }
    }

    private static Map<EnchantmentWithLevel, Integer> readMasterCharges(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size <= 0) {
            return Map.of();
        }
        Map<EnchantmentWithLevel, Integer> map = new java.util.HashMap<>(Math.max(4, size * 2));
        for (int i = 0; i < size; i++) {
            EnchantmentWithLevel key = EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.decode(buf);
            int val = buf.readVarInt();
            map.put(key, val);
        }
        return Map.copyOf(map);
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

    public record SkipRitualPayload(boolean sacrificeXp) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SkipRitualPayload> TYPE =
            new CustomPacketPayload.Type<>(SKIP_RITUAL_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SkipRitualPayload> CODEC =
            CustomPacketPayload.codec(
                (payload, buf) -> buf.writeBoolean(payload.sacrificeXp()),
                buf -> new SkipRitualPayload(buf.readBoolean())
            );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SyncRitualStatePayload(
        List<EnchantmentWithLevel> queuedSteps,
        boolean usedInfinityTome,
        boolean hasAnyMasterTome
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncRitualStatePayload> TYPE =
            new CustomPacketPayload.Type<>(SYNC_RITUAL_STATE_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncRitualStatePayload> CODEC =
            CustomPacketPayload.codec(SyncRitualStatePayload::write, SyncRitualStatePayload::read);

        private void write(RegistryFriendlyByteBuf buf) {
            List<EnchantmentWithLevel> list = queuedSteps == null ? List.of() : queuedSteps;
            buf.writeVarInt(list.size());
            for (EnchantmentWithLevel e : list) {
                EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.encode(buf, e);
            }
            buf.writeBoolean(usedInfinityTome);
            buf.writeBoolean(hasAnyMasterTome);
        }

        private static SyncRitualStatePayload read(RegistryFriendlyByteBuf buf) {
            int n = Math.max(0, buf.readVarInt());
            List<EnchantmentWithLevel> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.decode(buf));
            }
            boolean inf = buf.readBoolean();
            boolean tome = buf.readBoolean();
            return new SyncRitualStatePayload(List.copyOf(list), inf, tome);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
