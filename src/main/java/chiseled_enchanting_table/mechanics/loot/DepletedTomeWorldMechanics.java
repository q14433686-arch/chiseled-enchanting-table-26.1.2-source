package chiseled_enchanting_table.mechanics.loot;

import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.jetbrains.annotations.Nullable;

/**
 * Pure + runtime world integration for {@code 褪魔残卷} ({@code chiseled_enchanting_table:depleted_tome})
 * and the unified 4-category bookshelf taxonomy:
 * <ul>
 *   <li><b>Bookshelf Taxonomy</b>: distinguishes {@code 普通书架}, {@code 空的雕纹书架}, and
 *       {@code 有书的雕纹书架（含褪魔残卷）} — all three unite under {@code 广义书架}, while slots holding
 *       {@code 褪魔残卷} or plain books count strictly as {@code 广义书架} (never as {@code 附魔母本}).</li>
 *   <li><b>World Structure Chest Loot</b>: structure-specific spawn probabilities across 4 ruin/library tiers.</li>
 *   <li><b>Wild Chiseled Bookshelf Decay</b>: 3-grade shelf weathering model with expected replacement
 *       rate {@code 17.8%} on plain books ({@code ~14.24%} across all books) and hard ceiling {@code <= 30.0%}.</li>
 *   <li><b>Librarian-Only Villager Trades</b>:
 *       <ol>
 *         <li>Buyback recycling: {@code 2× 褪魔残卷 -> 1× 绿宝石} and {@code 2× 褪魔残卷 -> 2× 紫水晶碎片};</li>
 *         <li>Plain book restoration: {@code 1× 褪魔残卷 + (4 青金石 / 2 墨囊 / 1 绿宝石) -> 1× 书};</li>
 *         <li>Enchanted book commission: BOTH the <b>required Villager Profession Level ({@code Lv.1 ~ Lv.5})</b>
 *             AND the <b>fee</b> are dynamically governed by {@code 稀有度 × 普适度 × 相对等级}, with emerald
 *             ceiling strictly below vanilla Librarian's typical {@code 经验修补} price ({@code 18 < 24})
 *             and {@code minecraft:infinity} strictly excluded.</li>
 *       </ol>
 *   </li>
 * </ul>
 */
public final class DepletedTomeWorldMechanics {

    // -------------------------------------------------------------------------
    // 1. Unified Bookshelf Taxonomy
    // -------------------------------------------------------------------------

    public enum ShelfTaxonomyKind {
        NONE,
        REGULAR_BOOKSHELF,
        EMPTY_CHISELED_BOOKSHELF,
        /** Chiseled bookshelf containing plain book / written book / grimoire / Depleted Tome (no enchanted tome). */
        BOOK_CHISELED_BOOKSHELF,
        /** Chiseled bookshelf containing at least one Enchanted Book (counts as BOTH broad shelf AND Master Tome). */
        MASTER_TOME_CHISELED_BOOKSHELF;

        public boolean isBroadBookshelf() {
            return this != NONE;
        }

        public boolean isRegularBookshelf() {
            return this == REGULAR_BOOKSHELF;
        }

        public boolean providesMasterTomeResonance() {
            return this == MASTER_TOME_CHISELED_BOOKSHELF;
        }
    }

    public static ShelfTaxonomyKind classifyBookshelf(
        boolean regularWoodenBookshelf,
        boolean chiseledBookshelfBlock,
        boolean anySlotHasBookIncludingDepletedTome,
        boolean anySlotHasEnchantedBook
    ) {
        if (regularWoodenBookshelf) {
            return ShelfTaxonomyKind.REGULAR_BOOKSHELF;
        }
        if (!chiseledBookshelfBlock) {
            return ShelfTaxonomyKind.NONE;
        }
        if (!anySlotHasBookIncludingDepletedTome) {
            return ShelfTaxonomyKind.EMPTY_CHISELED_BOOKSHELF;
        }
        return anySlotHasEnchantedBook
            ? ShelfTaxonomyKind.MASTER_TOME_CHISELED_BOOKSHELF
            : ShelfTaxonomyKind.BOOK_CHISELED_BOOKSHELF;
    }

    public static int computeBroadBookshelfCount(
        int regularBookshelves,
        int emptyChiseledBookshelves,
        int occupiedChiseledBookshelvesIncludingDepletedTome
    ) {
        return Math.max(0, regularBookshelves)
            + Math.max(0, emptyChiseledBookshelves)
            + Math.max(0, occupiedChiseledBookshelvesIncludingDepletedTome);
    }

    // -------------------------------------------------------------------------
    // 2. Wild Chiseled Bookshelf Decay (<= 30% Hard Cap)
    // -------------------------------------------------------------------------

    /** Hard ceiling required by design: total replacement probability of shelf books never exceeds 30%. */
    public static final double MAX_WILD_BOOKSHELF_REPLACEMENT_PROBABILITY = 0.30D;

    /** Three shelf weathering grades: intact (35% weight -> 8%), aged (45% -> 20%), ruined (20% -> 30%). */
    public static final double[] WEATHERED_GRADE_WEIGHTS = { 0.35D, 0.45D, 0.20D };
    public static final double[] WEATHERED_GRADE_PROBS = { 0.08D, 0.20D, 0.30D };

    /** Exact expected replacement probability on plain-book slots: 0.35*0.08 + 0.45*0.20 + 0.20*0.30 = 0.178 (17.8%). */
    public static double expectedPlainBookReplacementRate() {
        double sum = 0.0D;
        for (int i = 0; i < WEATHERED_GRADE_WEIGHTS.length; i++) {
            sum += WEATHERED_GRADE_WEIGHTS[i]
                * Math.min(MAX_WILD_BOOKSHELF_REPLACEMENT_PROBABILITY, WEATHERED_GRADE_PROBS[i]);
        }
        return Math.min(MAX_WILD_BOOKSHELF_REPLACEMENT_PROBABILITY, sum);
    }

    /** Exact unconditional expected replacement probability across ALL occupied book slots on wild shelves. */
    public static double expectedTotalBookReplacementRate(double enchantedBookProbability) {
        double plainShare = Mth.clamp(1.0D - enchantedBookProbability, 0.0D, 1.0D);
        return Math.min(MAX_WILD_BOOKSHELF_REPLACEMENT_PROBABILITY, plainShare * expectedPlainBookReplacementRate());
    }

    /** Rolls per-shelf weathering replacement probability strictly within [0.0, 0.30]. */
    public static double rollShelfWeatheringProbability(RandomSource random) {
        double r = random.nextDouble();
        double cumulative = 0.0D;
        for (int i = 0; i < WEATHERED_GRADE_WEIGHTS.length; i++) {
            cumulative += WEATHERED_GRADE_WEIGHTS[i];
            if (r < cumulative) {
                return Mth.clamp(WEATHERED_GRADE_PROBS[i], 0.0D, MAX_WILD_BOOKSHELF_REPLACEMENT_PROBABILITY);
            }
        }
        return Mth.clamp(
            WEATHERED_GRADE_PROBS[WEATHERED_GRADE_PROBS.length - 1],
            0.0D,
            MAX_WILD_BOOKSHELF_REPLACEMENT_PROBABILITY
        );
    }

    // -------------------------------------------------------------------------
    // 3. Structure Chest Loot Spawn Table
    // -------------------------------------------------------------------------

    public record ChestLootSpec(String category, float probability, int minCount, int maxCount) {}

    private static final Map<String, ChestLootSpec> EXACT_CHEST_SPECS = Map.ofEntries(
        // Tier S — Ancient Libraries & Grand Archives (35% - 55%)
        Map.entry("chests/stronghold_library", new ChestLootSpec("archive", 0.55F, 1, 3)),
        Map.entry("chests/woodland_mansion", new ChestLootSpec("archive", 0.42F, 1, 2)),
        Map.entry("chests/ancient_city", new ChestLootSpec("archive", 0.40F, 1, 3)),
        Map.entry("chests/ancient_city_ice_box", new ChestLootSpec("archive", 0.28F, 1, 2)),
        Map.entry("chests/end_city_treasure", new ChestLootSpec("archive", 0.35F, 1, 2)),
        // Tier A — Temples, Strongholds, Bastions & Nether Fortresses (24% - 30%)
        Map.entry("chests/desert_pyramid", new ChestLootSpec("temple", 0.30F, 1, 2)),
        Map.entry("chests/jungle_temple", new ChestLootSpec("temple", 0.30F, 1, 2)),
        Map.entry("chests/stronghold_corridor", new ChestLootSpec("temple", 0.28F, 1, 2)),
        Map.entry("chests/stronghold_crossing", new ChestLootSpec("temple", 0.28F, 1, 2)),
        Map.entry("chests/nether_bridge", new ChestLootSpec("temple", 0.25F, 1, 2)),
        Map.entry("chests/bastion_treasure", new ChestLootSpec("temple", 0.28F, 1, 2)),
        Map.entry("chests/bastion_other", new ChestLootSpec("temple", 0.24F, 1, 2)),
        Map.entry("chests/bastion_bridge", new ChestLootSpec("temple", 0.22F, 1, 2)),
        Map.entry("chests/bastion_hoglin_stable", new ChestLootSpec("temple", 0.20F, 1, 1)),
        Map.entry("chests/ruined_portal", new ChestLootSpec("temple", 0.24F, 1, 2)),
        // Tier B — Dungeons, Mineshafts, Shipwrecks & Ruins (18% - 20%)
        Map.entry("chests/simple_dungeon", new ChestLootSpec("dungeon", 0.20F, 1, 2)),
        Map.entry("chests/abandoned_mineshaft", new ChestLootSpec("dungeon", 0.20F, 1, 2)),
        Map.entry("chests/shipwreck_map", new ChestLootSpec("dungeon", 0.22F, 1, 2)),
        Map.entry("chests/shipwreck_treasure", new ChestLootSpec("dungeon", 0.18F, 1, 1)),
        Map.entry("chests/underwater_ruin_big", new ChestLootSpec("dungeon", 0.20F, 1, 2)),
        Map.entry("chests/underwater_ruin_small", new ChestLootSpec("dungeon", 0.16F, 1, 1)),
        Map.entry("chests/pillager_outpost", new ChestLootSpec("dungeon", 0.18F, 1, 1)),
        Map.entry("chests/igloo_chest", new ChestLootSpec("dungeon", 0.18F, 1, 1)),
        Map.entry("chests/buried_treasure", new ChestLootSpec("dungeon", 0.18F, 1, 1))
    );

    @Nullable
    public static ChestLootSpec chestSpawnSpecForTableId(String namespace, String path) {
        if (!"minecraft".equals(namespace) || path == null) {
            return null;
        }
        ChestLootSpec exact = EXACT_CHEST_SPECS.get(path);
        if (exact != null) {
            return exact;
        }
        if (path.startsWith("chests/trial_chambers/")) {
            return new ChestLootSpec("temple", 0.28F, 1, 2);
        }
        if (path.startsWith("chests/village/")) {
            return new ChestLootSpec("village", 0.10F, 1, 1);
        }
        return null;
    }

    public static void initChestLootInjection() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) {
                return;
            }
            Identifier id = key.identifier();
            ChestLootSpec spec = chestSpawnSpecForTableId(id.getNamespace(), id.getPath());
            if (spec == null || spec.probability() <= 0.0F) {
                return;
            }
            int minC = Math.max(1, spec.minCount());
            int maxC = Math.max(minC, spec.maxCount());
            int span = maxC - minC + 1;
            int perCountHitWeight = Math.max(1, Math.round(spec.probability() * 1000.0F));
            int totalPoolWeight = 1000 * span;
            int emptyWeight = Math.max(0, totalPoolWeight - perCountHitWeight * span);

            LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1));
            for (int c = minC; c <= maxC; c++) {
                pool.add(
                    LootItem.lootTableItem(BlockRegistry.DEPLETED_TOME)
                        .apply(SetItemCountFunction.setCount(ConstantValue.exactly(c)))
                        .setWeight(perCountHitWeight)
                );
            }
            if (emptyWeight > 0) {
                pool.add(EmptyLootItem.emptyItem().setWeight(emptyWeight));
            }
            tableBuilder.withPool(pool);
        });
    }

    // -------------------------------------------------------------------------
    // 4. Librarian Recycling & Dynamic-Weighted Commissioning (+ Villager Level Gate)
    // -------------------------------------------------------------------------

    /** Buyback ratio: 2 Depleted Tomes = 1 Emerald OR 2 Amethyst Shards. */
    public static final int RECYCLE_INPUT_DEPLETED_TOMES = 2;
    public static final int RECYCLE_OUTPUT_EMERALDS = 1;
    public static final int RECYCLE_OUTPUT_AMETHYST_SHARDS = 2;

    /** Plain/base commission cost constants: 4 Lapis / 2 Ink Sacs / 1 Emerald. */
    public static final int BASE_COMMISSION_LAPIS = 4;
    public static final int BASE_COMMISSION_INK_SACS = 2;
    public static final int BASE_COMMISSION_EMERALDS = 1;

    /**
     * Vanilla Librarian's {@code EnchantBookForEmeralds} price for Treasure Lv.1 {@code Mending}
     * rolls uniformly in {@code [5..19] * 2 = [10..38]} emeralds -> typical/mean price = {@code 24}.
     * Our maximum dynamic price is capped strictly below that benchmark ({@code 18 < 24}).
     */
    public static final int VANILLA_TYPICAL_MENDING_EMERALDS = 24;
    public static final int MAX_COMMISSION_EMERALDS = 18;
    public static final int MAX_COMMISSION_LAPIS = 24;
    public static final int MAX_COMMISSION_INK_SACS = 14;

    public static final int MIN_VILLAGER_PROFESSION_LEVEL = 1;
    public static final int MAX_VILLAGER_PROFESSION_LEVEL = 5;

    /**
     * Dynamic-weighted score thresholds mapping {@code P = R(rarity) * U(universality) * Q(level/maxLevel)}
     * onto the 5 vanilla Librarian profession ranks:
     * <ul>
     *   <li>{@code Lv.1 新手 (Novice)}     : {@code P < 0.065}  (e.g. Sharpness I, Protection I, Bane I)</li>
     *   <li>{@code Lv.2 学徒 (Apprentice)} : {@code 0.065 <= P < 0.120} (e.g. Sharpness III, Protection II, Efficiency II)</li>
     *   <li>{@code Lv.3 老手 (Journeyman)} : {@code 0.120 <= P < 0.235} (e.g. Sharpness V, Protection IV, Looting III, Fortune I)</li>
     *   <li>{@code Lv.4 专家 (Expert)}     : {@code 0.235 <= P < 0.500} (e.g. Efficiency V, Unbreaking III, Fortune II, Thorns II)</li>
     *   <li>{@code Lv.5 大师 (Master)}     : {@code P >= 0.500} (e.g. Mending I, Silk Touch I, Fortune III, Thorns III)</li>
     * </ul>
     */
    public static final double VILLAGER_LEVEL_2_THRESHOLD = 0.065D;
    public static final double VILLAGER_LEVEL_3_THRESHOLD = 0.120D;
    public static final double VILLAGER_LEVEL_4_THRESHOLD = 0.235D;
    public static final double VILLAGER_LEVEL_5_THRESHOLD = 0.500D;

    private static final double MIN_TRIPLE_PRODUCT = 0.25D * 0.35D * 0.10D;

    public enum CommissionCurrency {
        EMERALD,
        LAPIS,
        INK_SAC
    }

    public record DynamicCommissionQuote(
        double rarityFactor,
        double universalityFactor,
        double relativeLevelFactor,
        double tripleWeightedScore,
        int requiredVillagerLevel,
        int emeraldCost,
        int lapisCost,
        int inkSacCost
    ) {
        public int costFor(CommissionCurrency currency) {
            return switch (currency) {
                case EMERALD -> emeraldCost;
                case LAPIS -> lapisCost;
                case INK_SAC -> inkSacCost;
            };
        }
    }

    /**
     * Computes BOTH the required Librarian profession level ({@code 1..5}) AND the dynamic trade fee
     * strictly from {@code 稀有度(rarityFactor) × 普适度(universalityFactor) × 相对等级(relativeLevelFactor)}.
     */
    public static int computeRequiredVillagerLevel(double tripleWeightedScore) {
        if (tripleWeightedScore >= VILLAGER_LEVEL_5_THRESHOLD) {
            return 5;
        }
        if (tripleWeightedScore >= VILLAGER_LEVEL_4_THRESHOLD) {
            return 4;
        }
        if (tripleWeightedScore >= VILLAGER_LEVEL_3_THRESHOLD) {
            return 3;
        }
        if (tripleWeightedScore >= VILLAGER_LEVEL_2_THRESHOLD) {
            return 2;
        }
        return 1;
    }

    public static boolean canLibrarianOfferCommission(int currentVillagerLevel, DynamicCommissionQuote quote) {
        if (quote == null) {
            return false;
        }
        return currentVillagerLevel >= quote.requiredVillagerLevel();
    }

    public static DynamicCommissionQuote computeDynamicCommissionQuote(
        int rarityWeight,
        int supportedItemCount,
        int level,
        int maxLevel
    ) {
        int clampedWeight = Mth.clamp(rarityWeight, 1, 10);
        double rarityFactor = Mth.clamp(
            Math.pow(10.0D / clampedWeight, 0.60D) / Math.pow(10.0D, 0.60D),
            0.25D,
            1.00D
        );
        int safeItems = Math.max(1, supportedItemCount);
        double universalityFactor = Mth.clamp(
            0.35D + 0.65D * (Math.log(safeItems) / Math.log(25.0D)),
            0.35D,
            1.00D
        );
        int safeMax = Math.max(1, maxLevel);
        int safeLvl = Mth.clamp(level, 1, safeMax);
        double relativeLevelFactor = Mth.clamp((double) safeLvl / safeMax, 0.10D, 1.00D);

        double product = rarityFactor * universalityFactor * relativeLevelFactor;
        int reqVillagerLvl = computeRequiredVillagerLevel(product);
        double normalized = Mth.clamp(
            (product - MIN_TRIPLE_PRODUCT) / (1.0D - MIN_TRIPLE_PRODUCT),
            0.0D,
            1.0D
        );

        int emeralds = Mth.clamp(
            BASE_COMMISSION_EMERALDS + (int) Math.round((MAX_COMMISSION_EMERALDS - BASE_COMMISSION_EMERALDS) * normalized),
            BASE_COMMISSION_EMERALDS,
            MAX_COMMISSION_EMERALDS
        );
        int lapis = Mth.clamp(
            BASE_COMMISSION_LAPIS + (int) Math.round((MAX_COMMISSION_LAPIS - BASE_COMMISSION_LAPIS) * normalized),
            BASE_COMMISSION_LAPIS,
            MAX_COMMISSION_LAPIS
        );
        int inkSacs = Mth.clamp(
            BASE_COMMISSION_INK_SACS + (int) Math.round((MAX_COMMISSION_INK_SACS - BASE_COMMISSION_INK_SACS) * normalized),
            BASE_COMMISSION_INK_SACS,
            MAX_COMMISSION_INK_SACS
        );

        return new DynamicCommissionQuote(
            rarityFactor,
            universalityFactor,
            relativeLevelFactor,
            product,
            reqVillagerLvl,
            emeralds,
            lapis,
            inkSacs
        );
    }

    /** True only for non-curse enchantments excluding {@code minecraft:infinity}. */
    public static boolean isAllowedLibrarianCommissionEnchantment(Identifier enchantmentId, boolean isCurse) {
        if (enchantmentId == null || isCurse) {
            return false;
        }
        return !MasterTomeWear.INFINITY_ENCHANTMENT_ID.equals(enchantmentId);
    }

    public static final ResourceKey<VillagerProfession> LIBRARIAN_PROFESSION_KEY =
        ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.withDefaultNamespace("librarian"));

    /** Pure profession check helper for JUnit tests and runtime guard. */
    public static boolean isLibrarianProfessionKey(@Nullable ResourceKey<VillagerProfession> key) {
        return LIBRARIAN_PROFESSION_KEY.equals(key);
    }

    private static Item itemForCurrency(CommissionCurrency currency) {
        return switch (currency) {
            case EMERALD -> Items.EMERALD;
            case LAPIS -> Items.LAPIS_LAZULI;
            case INK_SAC -> Items.INK_SAC;
        };
    }

    private static final String COMMISSION_BAND = "cet_commission_band";
    private static boolean hasCommissionBand(MerchantOffers offers, int band) {
        for (var offer : offers) {
            if (offer.getItemCostA().item().value() != BlockRegistry.DEPLETED_TOME) continue;
            var data = offer.getResult().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (data != null && data.copyTag().getIntOr(COMMISSION_BAND, -1) == band) return true;
        }
        return false;
    }

    private record CandidateEnchantRoll(
        Holder.Reference<Enchantment> holder,
        int enchantLevel,
        DynamicCommissionQuote quote
    ) {}

    private static boolean hasRecycleOrPlainOffer(MerchantOffers offers, Item resultItem) {
        for (MerchantOffer existing : offers) {
            if (existing.getItemCostA().item().value() == BlockRegistry.DEPLETED_TOME
                && existing.getResult().is(resultItem)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCommissionEnchantOffer(
        MerchantOffers offers,
        Holder.Reference<Enchantment> targetEnchant,
        int targetLevel
    ) {
        for (MerchantOffer existing : offers) {
            if (existing.getItemCostA().item().value() != BlockRegistry.DEPLETED_TOME) {
                continue;
            }
            ItemStack res = existing.getResult();
            if (!res.is(Items.ENCHANTED_BOOK)) {
                continue;
            }
            ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(res);
            if (stored.getLevel(targetEnchant) == targetLevel) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ensures a {@link Villager} of profession {@code minecraft:librarian} carries idempotent
     * Depleted Tome buyback and restoration offers, AND unlocks enchanted-book commission offers
     * ONLY when {@code villager.getVillagerData().level() >= quote.requiredVillagerLevel()} (computed
     * via the same {@code 稀有度 × 普适度 × 相对等级} dynamic weighting). Re-opening the same Librarian
     * after leveling them up (`Lv.1 -> ... -> Lv.5`) automatically unlocks newly reached tiers without
     * duplicating prior trades.
     */
    public static void ensureLibrarianDepletedTomeOffers(Level world, Villager villager) {
        if (world == null || villager == null) {
            return;
        }
        if (!villager.getVillagerData().profession().is(LIBRARIAN_PROFESSION_KEY)) {
            return;
        }
        MerchantOffers offers = villager.getOffers();
        if (offers == null) {
            return;
        }
        int currentVillagerLevel = Mth.clamp(
            villager.getVillagerData().level(),
            MIN_VILLAGER_PROFESSION_LEVEL,
            MAX_VILLAGER_PROFESSION_LEVEL
        );

        UUID uuid = villager.getUUID();
        Random rng = new Random(uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 21));

        // 1) Base Novice (Lv.1+) Recycle trades: 2× Depleted Tome -> 1× Emerald, and 2× Depleted Tome -> 2× Amethyst Shard
        if (!hasRecycleOrPlainOffer(offers, Items.EMERALD)) {
            offers.add(new MerchantOffer(
                new ItemCost(BlockRegistry.DEPLETED_TOME, RECYCLE_INPUT_DEPLETED_TOMES),
                Optional.empty(),
                new ItemStack(Items.EMERALD, RECYCLE_OUTPUT_EMERALDS),
                16,
                2,
                0.05F
            ));
        }
        if (!hasRecycleOrPlainOffer(offers, Items.AMETHYST_SHARD)) {
            offers.add(new MerchantOffer(
                new ItemCost(BlockRegistry.DEPLETED_TOME, RECYCLE_INPUT_DEPLETED_TOMES),
                Optional.empty(),
                new ItemStack(Items.AMETHYST_SHARD, RECYCLE_OUTPUT_AMETHYST_SHARDS),
                16,
                2,
                0.05F
            ));
        }

        // 2) Base Novice (Lv.1+) Plain Book Restoration: 1× Depleted Tome + base reagent (4 Lapis / 2 Ink / 1 Emerald) -> 1× Book
        CommissionCurrency plainCurrency = switch (rng.nextInt(3)) {
            case 0 -> CommissionCurrency.LAPIS;
            case 1 -> CommissionCurrency.INK_SAC;
            default -> CommissionCurrency.EMERALD;
        };
        int plainFee = switch (plainCurrency) {
            case LAPIS -> BASE_COMMISSION_LAPIS;
            case INK_SAC -> BASE_COMMISSION_INK_SACS;
            case EMERALD -> BASE_COMMISSION_EMERALDS;
        };
        if (!hasRecycleOrPlainOffer(offers, Items.BOOK)) {
            offers.add(new MerchantOffer(
                new ItemCost(BlockRegistry.DEPLETED_TOME, 1),
                Optional.of(new ItemCost(itemForCurrency(plainCurrency), plainFee)),
                new ItemStack(Items.BOOK, 1),
                12,
                3,
                0.05F
            ));
        }

        // 3) Dynamic-Weighted Enchanted Book Commissions gated by Villager Profession Level (Lv.1 .. Lv.5)
        var enchRegistry = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder.Reference<Enchantment>> eligible = new ArrayList<>();
        enchRegistry.listElements().forEach(ref -> {
            Identifier id = ref.key().identifier();
            boolean curse = ref.is(EnchantmentTags.CURSE);
            if (isAllowedLibrarianCommissionEnchantment(id, curse)) {
                eligible.add(ref);
            }
        });
        eligible.sort(Comparator.comparing(ref -> ref.key().identifier().toString()));
        if (eligible.isEmpty()) {
            return;
        }

        // Group all valid (enchantment, level) pairs into dynamic villager-rank buckets [1..5]
        @SuppressWarnings("unchecked")
        List<CandidateEnchantRoll>[] byRequiredRank = new List[MAX_VILLAGER_PROFESSION_LEVEL + 1];
        for (int r = 1; r <= MAX_VILLAGER_PROFESSION_LEVEL; r++) {
            byRequiredRank[r] = new ArrayList<>();
        }
        for (Holder.Reference<Enchantment> ref : eligible) {
            Enchantment ench = ref.value();
            int maxLvl = Mth.clamp(ench.getMaxLevel(), 1, 255);
            int weight = Math.max(1, ench.definition().weight());
            int supportedCount = Math.max(1, ench.getSupportedItems().size());
            for (int lvl = 1; lvl <= maxLvl; lvl++) {
                DynamicCommissionQuote q = computeDynamicCommissionQuote(weight, supportedCount, lvl, maxLvl);
                byRequiredRank[q.requiredVillagerLevel()].add(new CandidateEnchantRoll(ref, lvl, q));
            }
        }

        // Deterministically assign this Librarian 3 progressive commissions across Apprentice (Lv.2),
        // Journeyman/Expert (Lv.3-4), and Expert/Master (Lv.4-5), plus an optional Novice (Lv.1) entry,
        // unlocking each strictly when currentVillagerLevel >= candidate.quote().requiredVillagerLevel()!
        int[][] rankBands = {
            { 1, 2 },
            { 3, 4 },
            { 4, 5 }
        };
        CommissionCurrency[] bandCurrencies = {
            rng.nextBoolean() ? CommissionCurrency.LAPIS : CommissionCurrency.INK_SAC,
            CommissionCurrency.EMERALD,
            CommissionCurrency.EMERALD
        };

        for (int b = 0; b < rankBands.length; b++) {
            List<CandidateEnchantRoll> pool = new ArrayList<>();
            for (int rank : rankBands[b]) {
                pool.addAll(byRequiredRank[rank]);
            }
            if (pool.isEmpty()) {
                continue;
            }
            CandidateEnchantRoll pick = pool.get(rng.nextInt(pool.size()));
            if (hasCommissionBand(offers, b)) continue;
            if (!canLibrarianOfferCommission(currentVillagerLevel, pick.quote())) {
                // Villager has not yet reached the dynamic profession rank required for this enchantment!
                continue;
            }
            if (hasCommissionEnchantOffer(offers, pick.holder(), pick.enchantLevel())) {
                continue;
            }
            CommissionCurrency currency = bandCurrencies[b];
            int fee = pick.quote().costFor(currency);

            ItemStack bookOut = new ItemStack(Items.ENCHANTED_BOOK, 1);
            EnchantmentHelper.updateEnchantments(bookOut, mutable -> mutable.set(pick.holder(), pick.enchantLevel()));
            var marker = new net.minecraft.nbt.CompoundTag();
            marker.putInt(COMMISSION_BAND, b);
            bookOut.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(marker));

            offers.add(new MerchantOffer(
                new ItemCost(BlockRegistry.DEPLETED_TOME, 1),
                Optional.of(new ItemCost(itemForCurrency(currency), fee)),
                bookOut,
                8,
                2 + pick.quote().requiredVillagerLevel() * 3,
                0.05F
            ));
        }
    }

    private DepletedTomeWorldMechanics() {}
}
