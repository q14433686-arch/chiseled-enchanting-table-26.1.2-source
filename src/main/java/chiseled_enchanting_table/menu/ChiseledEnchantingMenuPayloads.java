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
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Networking payloads and the option-type model for the chiseled enchanting menu.
 */
public final class ChiseledEnchantingMenuPayloads {
    private ChiseledEnchantingMenuPayloads() {}

    public static final Identifier AVAILABLE_CHISELED_ENCHANTMENT_PACKET_ID = ChiseledEnchantingTable.identifier("available_chiseled_enchantment_" + EnchantingProtocol.REVISION);
    public static final Identifier LIVE_SHELF_STATE_PACKET_ID = ChiseledEnchantingTable.identifier("live_shelf_state_" + EnchantingProtocol.REVISION);
    // Audit NET-02: every business channel carries the protocol revision suffix. The bare
    // pre-revision_9 ids survive only as query-only probes in EnchantingProtocol.
    public static final Identifier APPLY_CHISELED_ENCHANTMENT_PACKET_ID = ChiseledEnchantingTable.identifier("apply_chiseled_enchantment_" + EnchantingProtocol.REVISION);
    public static final Identifier REROLL_EPIPHANY_PACKET_ID = ChiseledEnchantingTable.identifier("reroll_epiphany_" + EnchantingProtocol.REVISION);
    public static final Identifier FREEZE_EPIPHANY_PACKET_ID = ChiseledEnchantingTable.identifier("freeze_epiphany_" + EnchantingProtocol.REVISION);
    public static final Identifier REOPEN_TABLE_PACKET_ID = ChiseledEnchantingTable.identifier("reopen_table_" + EnchantingProtocol.REVISION);
    public static final Identifier SKIP_RITUAL_PACKET_ID = ChiseledEnchantingTable.identifier("skip_ritual_" + EnchantingProtocol.REVISION);
    public static final Identifier SYNC_RITUAL_STATE_PACKET_ID = ChiseledEnchantingTable.identifier("sync_ritual_state_" + EnchantingProtocol.REVISION);
    public static final Identifier TOME_PAGE_ACTION_PACKET_ID = ChiseledEnchantingTable.identifier("tome_page_action_" + EnchantingProtocol.REVISION);

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
            // Audit NET-03: never echo attacker-controlled input into server logs.
            throw new IllegalArgumentException(
                "Unknown enchanting option type (token length " + (token == null ? -1 : token.length()) + ")");
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
        public static void write(RegistryFriendlyByteBuf buf, TransformationDisplay value) {
            buf.writeIdentifier(value.id());
            buf.writeIdentifier(value.input_item());
            buf.writeBoolean(value.input_is_tag());
            buf.writeIdentifier(value.output_item());
            buf.writeVarInt(Math.max(0, value.level()));
            buf.writeVarInt(Math.max(1, value.min_player_level()));
            buf.writeVarInt(Math.max(0, value.deducted_levels()));
            buf.writeBoolean(value.unlocked());
        }

        public static TransformationDisplay read(RegistryFriendlyByteBuf buf) {
            Identifier id = buf.readIdentifier();
            Identifier input = buf.readIdentifier();
            boolean isTag = buf.readBoolean();
            Identifier output = buf.readIdentifier();
            int level = Math.max(0, buf.readVarInt());
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
        int empty_chiseled_bookshelves,
        Map<EnchantmentWithLevel, Integer> master_tome_charges
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AvailableEnchantmentPayload> AVAILABLE_CHISELED_ENCHANTMENT_PAYLOAD_ID =
            new CustomPacketPayload.Type<>(AVAILABLE_CHISELED_ENCHANTMENT_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AvailableEnchantmentPayload> AVAILABLE_CHISELED_ENCHANTMENT_CODEC =
            CustomPacketPayload.codec(AvailableEnchantmentPayload::write, AvailableEnchantmentPayload::read);

        public AvailableEnchantmentPayload(Set<EnchantmentWithLevel> unlocked, List<TransformationDisplay> transforms,
            int discount, int regular, int books, int high, int seed, Map<Identifier,Integer> masters, boolean cramped) {
            this(unlocked, transforms, discount, regular, books, high, seed, masters, cramped, 0, 0, Map.of());
        }

        public AvailableEnchantmentPayload(Set<EnchantmentWithLevel> unlocked_enchantements) {
            this(unlocked_enchantements, buildTransformationDisplays(null), 0, 0, 0, 0, 1, Map.of(), false, 0, 0, Map.of());
        }

        public AvailableEnchantmentPayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int epiphany_seed
        ) {
            this(unlocked_enchantements, buildTransformationDisplays(null), xp_discount_percent, regular_bookshelves, chiseled_book_count, 0, epiphany_seed, Map.of(), false, 0, 0, Map.of());
        }

        public AvailableEnchantmentPayload(
            Set<EnchantmentWithLevel> unlocked_enchantements,
            int xp_discount_percent,
            int regular_bookshelves,
            int chiseled_book_count,
            int epiphany_seed,
            Map<Identifier, Integer> stored_master_levels
        ) {
            this(unlocked_enchantements, buildTransformationDisplays(null), xp_discount_percent, regular_bookshelves, chiseled_book_count, 0, epiphany_seed, stored_master_levels, false, 0, 0, Map.of());
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
            this(unlocked_enchantements, buildTransformationDisplays(null), xp_discount_percent, regular_bookshelves, chiseled_book_count, high_tier_chiseled_books, epiphany_seed, stored_master_levels, false, 0, 0, Map.of());
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
                0,
                master_tome_charges == null ? Map.of() : Map.copyOf(master_tome_charges)
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
            int empty_chiseled_bookshelves,
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
                empty_chiseled_bookshelves,
                master_tome_charges == null ? Map.of() : Map.copyOf(master_tome_charges)
            );
        }

        public static List<TransformationDisplay> buildTransformationDisplays() {
            return buildTransformationDisplays(null);
        }

        public static List<TransformationDisplay> buildTransformationDisplays(@Nullable Player player) {
            List<TransformationDisplay> displays = new ArrayList<>();
            for (var desc : ChiseledEnchantingTableData.transformationDescriptors(player)) {
                displays.add(new TransformationDisplay(
                    desc.id(),
                    desc.inputItemId(),
                    desc.inputIsTag(),
                    desc.outputItemId(),
                    desc.outputCount(),
                    desc.minRequiredLevel(),
                    desc.deductedLevels(),
                    desc.unlocked()
                ));
            }
            return List.copyOf(displays);
        }

        private void write(RegistryFriendlyByteBuf buf) {
            // 26.3 removed FriendlyByteBuf collection helpers. Preserve bounded VarInt + elements.
            PayloadLimits.writeBoundedCount(buf, unlocked_enchantements.size(), PayloadLimits.MAX_COLLECTION_ENTRIES);
            for (EnchantmentWithLevel value : unlocked_enchantements) {
                EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.encode(buf, value);
            }
            PayloadLimits.writeBoundedCount(buf, transformations.size(), PayloadLimits.MAX_COLLECTION_ENTRIES);
            for (TransformationDisplay value : transformations) TransformationDisplay.write(buf, value);
            buf.writeVarInt(Math.max(0, xp_discount_percent));
            buf.writeVarInt(Math.max(0, regular_bookshelves));
            buf.writeVarInt(Math.max(0, chiseled_book_count));
            buf.writeVarInt(Math.max(0, high_tier_chiseled_books));
            buf.writeInt(epiphany_seed);
            PayloadLimits.writeBoundedCount(
                buf,
                stored_master_levels == null ? 0 : stored_master_levels.size(),
                PayloadLimits.MAX_COLLECTION_ENTRIES
            );
            if (stored_master_levels != null) {
                for (var entry : stored_master_levels.entrySet()) {
                    buf.writeIdentifier(entry.getKey());
                    buf.writeVarInt(Math.max(1, entry.getValue()));
                }
            }
            buf.writeBoolean(cramped_library);
            buf.writeVarInt(Math.max(0, chiseled_bookshelves));
            buf.writeVarInt(Math.max(0, empty_chiseled_bookshelves));
            writeMasterCharges(buf, master_tome_charges);
        }

        private static AvailableEnchantmentPayload read(RegistryFriendlyByteBuf buf) {
            int unlockedSize = PayloadLimits.readCount(buf);
            Set<EnchantmentWithLevel> unlocked = new HashSet<>();
            for (int i = 0; i < unlockedSize; i++) {
                unlocked.add(EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.decode(buf));
            }
            int transformationSize = PayloadLimits.readCount(buf);
            List<TransformationDisplay> transformations = new ArrayList<>();
            for (int i = 0; i < transformationSize; i++) {
                transformations.add(TransformationDisplay.read(buf));
            }
            int xpDiscountPercent = buf.readVarInt();
            int regularBookshelves = buf.readVarInt();
            int chiseledBookCount = buf.readVarInt();
            int highTierChiseledBooks = buf.readVarInt();
            int epiphanySeed = buf.readInt();
            int masterSize = PayloadLimits.readCount(buf);
            Map<Identifier, Integer> masterLevels = new java.util.HashMap<>(Math.max(4, masterSize * 2));
            for (int i = 0; i < masterSize; i++) {
                Identifier id = buf.readIdentifier();
                int lvl = buf.readVarInt();
                masterLevels.put(id, Math.max(1, lvl));
            }
            boolean crampedLibrary = buf.readBoolean();
            int chiseledBookshelves = buf.readVarInt();
            int emptyChiseledBookshelves = buf.readVarInt();
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
                emptyChiseledBookshelves,
                masterCharges
            );
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return AVAILABLE_CHISELED_ENCHANTMENT_PAYLOAD_ID;
        }
    }

    /** One template book shown on the Tome page. Lock mode: -1 none, 0 next, 1 full, 2 permanent. */
    public record TomeBookEntry(
        BlockPos shelf_pos,
        int slot,
        String custom_name,
        List<EnchantmentWithLevel> enchantments,
        int wear_used,
        int remaining_charges,
        int effective_max_uses,
        int generation,
        boolean charging,
        boolean wear_immune,
        int eta_next_ticks,
        int eta_full_ticks,
        int lock_mode,
        String lock_owner,
        String lock_owner_name,
        int lock_target_wear,
        boolean locked_by_viewer
    ) {
        public TomeBookEntry {
            shelf_pos = shelf_pos == null ? BlockPos.ZERO : shelf_pos.immutable();
            slot = Math.max(0, Math.min(5, slot));
            custom_name = custom_name == null ? "" : custom_name.substring(0, Math.min(128, custom_name.length()));
            enchantments = enchantments == null ? List.of() : List.copyOf(enchantments.subList(0, Math.min(PayloadLimits.MAX_TOME_BOOK_ENCHANTMENTS, enchantments.size())));
            wear_used = Math.max(0, wear_used);
            remaining_charges = Math.max(0, remaining_charges);
            effective_max_uses = Math.max(1, effective_max_uses);
            generation = Math.max(0, Math.min(255, generation));
            lock_mode = Math.max(-1, Math.min(2, lock_mode));
            lock_owner = lock_owner == null ? "" : lock_owner.substring(0, Math.min(64, lock_owner.length()));
            lock_owner_name = lock_owner_name == null ? "" : lock_owner_name.substring(0, Math.min(64, lock_owner_name.length()));
            lock_target_wear = Math.max(-1, lock_target_wear);
        }

        public boolean locked() {
            return lock_mode >= 0;
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(shelf_pos);
            buf.writeVarInt(slot);
            buf.writeUtf(custom_name, 128);
            PayloadLimits.writeBoundedCount(buf, enchantments.size(), PayloadLimits.MAX_TOME_BOOK_ENCHANTMENTS);
            for (EnchantmentWithLevel e : enchantments) {
                EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.encode(buf, e);
            }
            buf.writeVarInt(wear_used);
            buf.writeVarInt(remaining_charges);
            buf.writeVarInt(effective_max_uses);
            buf.writeVarInt(generation);
            buf.writeBoolean(charging);
            buf.writeBoolean(wear_immune);
            buf.writeVarInt(eta_next_ticks);
            buf.writeVarInt(eta_full_ticks);
            buf.writeVarInt(lock_mode);
            buf.writeUtf(lock_owner, 64);
            buf.writeUtf(lock_owner_name, 64);
            buf.writeVarInt(lock_target_wear);
            buf.writeBoolean(locked_by_viewer);
        }

        private static TomeBookEntry read(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int slot = buf.readVarInt();
            String name = buf.readUtf(128);
            int count = PayloadLimits.readBoundedCount(buf, PayloadLimits.MAX_TOME_BOOK_ENCHANTMENTS);
            List<EnchantmentWithLevel> ench = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ench.add(EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.decode(buf));
            }
            return new TomeBookEntry(
                pos,
                slot,
                name,
                ench,
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readBoolean()
            );
        }
    }

    public record LiveShelfStatePayload(
        int container_id,
        Set<EnchantmentWithLevel> unlocked_enchantements,
        int xp_discount_percent,
        int regular_bookshelves,
        int chiseled_book_count,
        int high_tier_chiseled_books,
        Map<Identifier, Integer> stored_master_levels,
        boolean cramped_library,
        int chiseled_bookshelves,
        int empty_chiseled_bookshelves,
        Map<EnchantmentWithLevel, Integer> master_tome_charges,
        boolean cleanse_unlocked,
        boolean tome_page_unlocked,
        int sacrifice_progress,
        int sacrifice_required,
        int qualifying_enchanted_bookshelves,
        int tome_page_required_shelves,
        int total_library_bookshelves,
        int regen_tier,
        int next_regen_tier_gap,
        int regen_interval_ticks,
        List<TomeBookEntry> tome_books
    ) implements CustomPacketPayload {
        public LiveShelfStatePayload {
            tome_books = tome_books == null ? List.of() : List.copyOf(tome_books.subList(0, Math.min(PayloadLimits.MAX_TOME_BOOK_ENTRIES, tome_books.size())));
            sacrifice_required = Math.max(1, sacrifice_required);
            tome_page_required_shelves = Math.max(1, tome_page_required_shelves);
        }
        public static final CustomPacketPayload.Type<LiveShelfStatePayload> TYPE =
            new CustomPacketPayload.Type<>(LIVE_SHELF_STATE_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, LiveShelfStatePayload> CODEC =
            CustomPacketPayload.codec(LiveShelfStatePayload::write, LiveShelfStatePayload::read);

        public LiveShelfStatePayload(int containerId, Set<EnchantmentWithLevel> unlocked, int discount, int regular,
            int books, int high, Map<Identifier, Integer> masters, boolean cramped) {
            this(containerId, unlocked, discount, regular, books, high, masters, cramped, 0, 0, Map.of(),
                false, false, 0, 12, 0, 4, 0, 0, 0, -1, List.of());
        }

        /** Revision-10-shaped constructor retained for existing callers and codec tests. */
        public LiveShelfStatePayload(int containerId, Set<EnchantmentWithLevel> unlocked, int discount, int regular,
            int books, int high, Map<Identifier, Integer> masters, boolean cramped,
            int chiseledBookshelves, int emptyChiseledBookshelves,
            Map<EnchantmentWithLevel, Integer> masterTomeCharges) {
            this(containerId, unlocked, discount, regular, books, high, masters, cramped,
                chiseledBookshelves, emptyChiseledBookshelves, masterTomeCharges,
                false, false, 0, 12, 0, 4, 0, 0, 0, -1, List.of());
        }

        public boolean targets(int containerId) {
            return this.container_id == containerId;
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(container_id);
            PayloadLimits.writeBoundedCount(buf, unlocked_enchantements.size(), PayloadLimits.MAX_COLLECTION_ENTRIES);
            for (EnchantmentWithLevel value : unlocked_enchantements) {
                EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.encode(buf, value);
            }
            buf.writeVarInt(Math.max(0, xp_discount_percent));
            buf.writeVarInt(Math.max(0, regular_bookshelves));
            buf.writeVarInt(Math.max(0, chiseled_book_count));
            buf.writeVarInt(Math.max(0, high_tier_chiseled_books));
            PayloadLimits.writeBoundedCount(
                buf,
                stored_master_levels == null ? 0 : stored_master_levels.size(),
                PayloadLimits.MAX_COLLECTION_ENTRIES
            );
            if (stored_master_levels != null) {
                for (var entry : stored_master_levels.entrySet()) {
                    buf.writeIdentifier(entry.getKey());
                    buf.writeVarInt(Math.max(1, entry.getValue()));
                }
            }
            buf.writeBoolean(cramped_library);
            buf.writeVarInt(Math.max(0, chiseled_bookshelves));
            buf.writeVarInt(Math.max(0, empty_chiseled_bookshelves));
            writeMasterCharges(buf, master_tome_charges);
            buf.writeBoolean(cleanse_unlocked);
            buf.writeBoolean(tome_page_unlocked);
            buf.writeVarInt(Math.max(0, sacrifice_progress));
            buf.writeVarInt(Math.max(1, sacrifice_required));
            buf.writeVarInt(Math.max(0, qualifying_enchanted_bookshelves));
            buf.writeVarInt(Math.max(1, tome_page_required_shelves));
            buf.writeVarInt(Math.max(0, total_library_bookshelves));
            buf.writeVarInt(Math.max(0, regen_tier));
            buf.writeVarInt(Math.max(0, next_regen_tier_gap));
            buf.writeVarInt(regen_interval_ticks);
            PayloadLimits.writeBoundedCount(buf, tome_books.size(), PayloadLimits.MAX_TOME_BOOK_ENTRIES);
            for (TomeBookEntry entry : tome_books) {
                entry.write(buf);
            }
        }

        private static LiveShelfStatePayload read(RegistryFriendlyByteBuf buf) {
            int containerId = buf.readVarInt();
            int unlockedSize = PayloadLimits.readCount(buf);
            Set<EnchantmentWithLevel> unlocked = new HashSet<>();
            for (int i = 0; i < unlockedSize; i++) {
                unlocked.add(EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.decode(buf));
            }
            int xpDiscountPercent = buf.readVarInt();
            int regularBookshelves = buf.readVarInt();
            int chiseledBookCount = buf.readVarInt();
            int highTierChiseledBooks = buf.readVarInt();
            int masterSize = PayloadLimits.readCount(buf);
            Map<Identifier, Integer> masterLevels = new java.util.HashMap<>(Math.max(4, masterSize * 2));
            for (int i = 0; i < masterSize; i++) {
                Identifier id = buf.readIdentifier();
                int lvl = buf.readVarInt();
                masterLevels.put(id, Math.max(1, lvl));
            }
            boolean crampedLibrary = buf.readBoolean();
            int chiseledBookshelves = buf.readVarInt();
            int emptyChiseledBookshelves = buf.readVarInt();
            Map<EnchantmentWithLevel, Integer> masterCharges = readMasterCharges(buf);
            boolean cleanseUnlocked = buf.readBoolean();
            boolean tomeUnlocked = buf.readBoolean();
            int sacrificeProgress = buf.readVarInt();
            int sacrificeRequired = buf.readVarInt();
            int qualifyingShelves = buf.readVarInt();
            int tomeRequiredShelves = buf.readVarInt();
            int totalLibrary = buf.readVarInt();
            int regenTier = buf.readVarInt();
            int regenGap = buf.readVarInt();
            int regenInterval = buf.readVarInt();
            int tomeCount = PayloadLimits.readBoundedCount(buf, PayloadLimits.MAX_TOME_BOOK_ENTRIES);
            List<TomeBookEntry> tomeBooks = new ArrayList<>(tomeCount);
            for (int i = 0; i < tomeCount; i++) {
                tomeBooks.add(TomeBookEntry.read(buf));
            }
            return new LiveShelfStatePayload(
                containerId,
                unlocked,
                xpDiscountPercent,
                regularBookshelves,
                chiseledBookCount,
                highTierChiseledBooks,
                Map.copyOf(masterLevels),
                crampedLibrary,
                chiseledBookshelves,
                emptyChiseledBookshelves,
                masterCharges,
                cleanseUnlocked,
                tomeUnlocked,
                sacrificeProgress,
                sacrificeRequired,
                qualifyingShelves,
                tomeRequiredShelves,
                totalLibrary,
                regenTier,
                regenGap,
                regenInterval,
                tomeBooks
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
        PayloadLimits.writeBoundedCount(buf, charges == null ? 0 : charges.size(), PayloadLimits.MAX_COLLECTION_ENTRIES);
        if (charges != null) {
            for (var entry : charges.entrySet()) {
                EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.encode(buf, entry.getKey());
                buf.writeVarInt(entry.getValue());
            }
        }
    }

    private static Map<EnchantmentWithLevel, Integer> readMasterCharges(RegistryFriendlyByteBuf buf) {
        int size = PayloadLimits.readCount(buf);
        if (size == 0) {
            return Map.of();
        }
        Map<EnchantmentWithLevel, Integer> map = new java.util.HashMap<>(Math.max(4, size * 2));
        for (int i = 0; i < size; i++) {
            EnchantmentWithLevel key = EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.decode(buf);
            int val = buf.readVarInt();
            if (val < -1 || val > 256) throw new IllegalArgumentException("Invalid master charge");
            map.put(key, val);
        }
        return Map.copyOf(map);
    }

    public record TomePageActionPayload(
        int containerId,
        Action action,
        BlockPos shelf_pos,
        int slot
    ) implements CustomPacketPayload {
        public enum Action {
            LOCK_NEXT,
            LOCK_FULL,
            LOCK_PERMANENT,
            UNLOCK,
            LOCK_ALL_FULL,
            SACRIFICE_DEPLETED
        }

        public TomePageActionPayload {
            if (containerId < PayloadLimits.NO_CONTAINER) {
                throw new IllegalArgumentException("Invalid container id: " + containerId);
            }
            if (action == null) {
                throw new IllegalArgumentException("Missing tome page action");
            }
            shelf_pos = shelf_pos == null ? BlockPos.ZERO : shelf_pos.immutable();
            if (slot < -1 || slot > 5) {
                throw new IllegalArgumentException("Invalid shelf slot: " + slot);
            }
        }

        public static final CustomPacketPayload.Type<TomePageActionPayload> TYPE =
            new CustomPacketPayload.Type<>(TOME_PAGE_ACTION_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, TomePageActionPayload> CODEC =
            CustomPacketPayload.codec(TomePageActionPayload::write, TomePageActionPayload::read);

        public boolean targets(int activeContainerId) {
            return activeContainerId > 0 && this.containerId == activeContainerId;
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(containerId);
            buf.writeVarInt(action.ordinal());
            buf.writeBlockPos(shelf_pos);
            buf.writeVarInt(slot);
        }

        private static TomePageActionPayload read(RegistryFriendlyByteBuf buf) {
            int container = buf.readVarInt();
            int ordinal = buf.readVarInt();
            Action[] values = Action.values();
            if (ordinal < 0 || ordinal >= values.length) {
                throw new IllegalArgumentException("Invalid tome page action: " + ordinal);
            }
            return new TomePageActionPayload(container, values[ordinal], buf.readBlockPos(), buf.readVarInt());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ApplyEnchantmentPayload(
        int containerId,
        OptionType option_type,
        Identifier enchantment_id,
        int enchantment_level
    ) implements CustomPacketPayload {
        /**
         * Audit NET-03: decode-boundary validation lives in the compact constructor so no
         * construction path (wire or programmatic) can smuggle out-of-range values into
         * business logic. Level {@code 0} stays legal because DISENCHANT_ALL uses it as a
         * sentinel; per-operation ranges are still enforced by the appliers.
         */
        public ApplyEnchantmentPayload {
            if (containerId < PayloadLimits.NO_CONTAINER) {
                throw new IllegalArgumentException("Invalid container id: " + containerId);
            }
            if (option_type == null) {
                throw new IllegalArgumentException("Missing enchanting option type");
            }
            if (enchantment_id == null || enchantment_id.toString().length() > PayloadLimits.MAX_IDENTIFIER_LENGTH) {
                throw new IllegalArgumentException("Invalid enchantment identifier");
            }
            if (enchantment_level < 0 || enchantment_level > PayloadLimits.MAX_ENCHANTMENT_LEVEL) {
                throw new IllegalArgumentException("Invalid enchantment level: " + enchantment_level);
            }
        }

        public static final CustomPacketPayload.Type<ApplyEnchantmentPayload> APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID = new CustomPacketPayload.Type<>(APPLY_CHISELED_ENCHANTMENT_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ApplyEnchantmentPayload> APPLY_CHISELED_ENCHANTMENT_CODEC =
            CustomPacketPayload.codec(ApplyEnchantmentPayload::write, ApplyEnchantmentPayload::read);

        public ApplyEnchantmentPayload(OptionType option_type, Identifier enchantment_id, int enchantment_level) {
            this(-1, option_type, enchantment_id, enchantment_level);
        }

        public ApplyEnchantmentPayload(Identifier enchantment_id, int enchantment_level) {
            this(-1, OptionType.ENCHANTMENT, enchantment_id, enchantment_level);
        }

        public boolean targets(int activeContainerId) {
            return activeContainerId > 0 && this.containerId == activeContainerId;
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(containerId);
            buf.writeUtf(option_type.token());
            buf.writeInt(enchantment_level);
            buf.writeIdentifier(enchantment_id);
        }

        private static ApplyEnchantmentPayload read(RegistryFriendlyByteBuf buf) {
            int containerId = buf.readVarInt();
            OptionType optionType = OptionType.fromToken(buf.readUtf(PayloadLimits.MAX_OPTION_TOKEN_LENGTH));
            int enchantmentLevel = buf.readInt();
            Identifier enchantmentId = buf.readIdentifier();
            return new ApplyEnchantmentPayload(containerId, optionType, enchantmentId, enchantmentLevel);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return APPLY_CHISELED_ENCHANTMENT_PAYLOAD_ID;
        }
    }

    public record RerollEpiphanyPayload(int containerId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RerollEpiphanyPayload> TYPE =
            new CustomPacketPayload.Type<>(REROLL_EPIPHANY_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, RerollEpiphanyPayload> CODEC =
            CustomPacketPayload.codec(
                (payload, buf) -> buf.writeVarInt(payload.containerId()),
                buf -> new RerollEpiphanyPayload(buf.readVarInt())
            );

        public RerollEpiphanyPayload() {
            this(-1);
        }

        public boolean targets(int activeContainerId) {
            return activeContainerId > 0 && this.containerId == activeContainerId;
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FreezeEpiphanyPayload(int containerId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FreezeEpiphanyPayload> TYPE =
            new CustomPacketPayload.Type<>(FREEZE_EPIPHANY_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, FreezeEpiphanyPayload> CODEC =
            CustomPacketPayload.codec(
                (payload, buf) -> buf.writeVarInt(payload.containerId()),
                buf -> new FreezeEpiphanyPayload(buf.readVarInt())
            );

        public FreezeEpiphanyPayload() {
            this(-1);
        }

        public boolean targets(int activeContainerId) {
            return activeContainerId > 0 && this.containerId == activeContainerId;
        }

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

    public record SkipRitualPayload(int containerId, long session, boolean sacrificeXp) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SkipRitualPayload> TYPE =
            new CustomPacketPayload.Type<>(SKIP_RITUAL_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SkipRitualPayload> CODEC =
            CustomPacketPayload.codec(
                (payload, buf) -> { buf.writeVarInt(payload.containerId()); buf.writeLong(payload.session()); buf.writeBoolean(payload.sacrificeXp()); },
                buf -> new SkipRitualPayload(buf.readVarInt(), buf.readLong(), buf.readBoolean())
            );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SyncRitualStatePayload(
        int containerId,
        long session,
        List<EnchantmentWithLevel> queuedSteps,
        boolean usedInfinityTome,
        boolean hasAnyMasterTome,
        boolean ritualBlocked
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncRitualStatePayload> TYPE =
            new CustomPacketPayload.Type<>(SYNC_RITUAL_STATE_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncRitualStatePayload> CODEC =
            CustomPacketPayload.codec(SyncRitualStatePayload::write, SyncRitualStatePayload::read);

        public SyncRitualStatePayload(
            int containerId,
            long session,
            List<EnchantmentWithLevel> queuedSteps,
            boolean usedInfinityTome,
            boolean hasAnyMasterTome
        ) {
            this(containerId, session, queuedSteps, usedInfinityTome, hasAnyMasterTome, false);
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(containerId);
            buf.writeLong(session);
            List<EnchantmentWithLevel> list = queuedSteps == null ? List.of() : queuedSteps;
            if (list.size() > 256) throw new IllegalArgumentException("Too many ritual steps");
            buf.writeVarInt(list.size());
            for (EnchantmentWithLevel e : list) {
                EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.encode(buf, e);
            }
            buf.writeBoolean(usedInfinityTome);
            buf.writeBoolean(hasAnyMasterTome);
            buf.writeBoolean(ritualBlocked);
        }

        private static SyncRitualStatePayload read(RegistryFriendlyByteBuf buf) {
            int containerId = buf.readVarInt();
            long session = buf.readLong();
            int n = PayloadLimits.readCount(buf);
            if (n > 256) throw new IllegalArgumentException("Too many ritual steps");
            List<EnchantmentWithLevel> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(EnchantmentWithLevel.ENCHANTMENT_WITH_LEVEL_CODEC.decode(buf));
            }
            boolean inf = buf.readBoolean();
            boolean tome = buf.readBoolean();
            boolean blocked = buf.readBoolean();
            return new SyncRitualStatePayload(containerId, session, List.copyOf(list), inf, tome, blocked);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
