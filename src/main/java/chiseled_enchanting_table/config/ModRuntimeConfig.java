package chiseled_enchanting_table.config;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData.VanillaChestBooksMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.BlankMaxTierMode;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression.VanillaTableMode;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Unified persistent &amp; live-synchronized configuration backing both bootstrap settings
 * ({@code config/chiseled_enchanting_table.json}) and granular runtime gameplay rules across all
 * mod subsystems, with built-in cross-field coherence invariants and client/server synchronization.
 *
 * <p>Every historical difference between classic and modern rulesets is represented as an
 * explicit, orthogonal rule atom in its natural domain rather than hidden behind a monolithic lock
 * switch. Presets ({@link Preset#CLASSIC}, {@link Preset#STANDARD}, etc.) act purely as
 * one-click profile writers.</p>
 */
public record ModRuntimeConfig(
    // 1. System & Vanilla Compatibility
    boolean overrideDatapacks,
    int enchantedBookStackSize,
    VanillaTableMode vanillaTableMode,
    boolean anvilFlatRepairCost,
    boolean anvilBlockSameBookMerge,

    // 2. Unlock & Bookshelf Progression
    ShelfBoostModel shelfBoostModel,
    DefaultUnlockFilter defaultUnlockFilter,
    boolean directTierUpgrade,
    boolean directGatesEnabled,
    BlankMaxTierMode blankMaxTierMode,
    int maxDirectBookshelves,
    int horizontalScanRadius,
    int verticalScanRadius,
    boolean requireBreathingRoom,
    int minOpenAdjacentBlocks,
    boolean requireLineOfSight,

    // 3. Master Tomes, Ritual & Pricing Economy
    MasterTomeWear.ConsumptionMode tomeConsumptionMode,
    int maxTomeUses,
    int bookCopyWearCost,
    MasterTomeWear.DepleteBehavior tomeDepleteBehavior,
    boolean tomeRegenEnabled,
    int tomeRegenBaseSeconds,
    int tomeRegenTier1Shelves,
    int tomeRegenTier1Seconds,
    int tomeRegenTier2Shelves,
    int tomeRegenTier2Seconds,
    boolean tomeLocksEnabled,
    boolean lockedShelfBreakable,
    boolean lockedBookExtractionBlocked,
    boolean provenanceEnabled,
    boolean ritualQueueEnabled,
    int earlyUnlockXpLevels,
    int earlyUnlockGritCount,
    int sacrificeRequired,
    int tomePageRequiredShelves,
    boolean incrementalCredit,
    XpSettlementMode xpMode,
    CostTableDataset costTableDataset,
    MaterialSelectionMode materialSelectionMode,
    ResearchRoundingMode researchRoundingMode,
    boolean libraryDiscountEnabled,
    int maxWoodenDiscountPercent,
    int maxTotalXpDiscountPercent,
    boolean bookCombineEnabled,
    double bookCombineXpMultiplier,
    boolean bookResearchEnabled,
    double bookResearchXpMultiplier,

    // 4. Treasure Epiphany & Cryo-Freeze
    boolean epiphanyEnabled,
    int epiphanyMinBookshelves,
    double epiphanyBaseChance,
    double epiphanyMaxChance,
    int epiphanyMaxSlotsCap,
    int epiphanyCycleSeconds,
    int epiphanyFreezeXpLevels,
    int epiphanyFreezeDurationSeconds,
    int epiphanyMaxFreezeSeconds,
    int epiphanyRerollXpLevels,

    // 5. Cleanse, Depleted Tomes & Item Transformations
    boolean disenchantEnabled,
    double xpRefundScale,
    double externalXpRefundScale,
    int stripXpMin,
    int stripXpMax,
    boolean allowCursePurge,
    int cursePurgeXpCost,
    TransformationSetMode transformationSetMode,
    boolean requirePriorDiscovery,
    DrainedBookResult drainedBookResult,
    boolean tomePageEnabled,
    int tier1Bookshelves,
    int tier2Bookshelves,
    int tier3QualifyingEnchantments,
    int transformationMaxBatchSize,

    // 6. World Generation, Villagers & Loot Tables
    VanillaChestBooksMode vanillaChestBooksMode,
    boolean bookshelfPoolEnabled,
    int bookshelfMinBooks,
    int bookshelfMaxBooks,
    double bookshelfEnchantedProb,
    boolean librarianTomeOffers,
    boolean restrictVillagerInfinityBooks,
    boolean grindstoneDepletedTome,

    // Explicit per-field customization tracking (used when overrideDatapacks == false)
    Set<String> customizedKeys
) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE_NAME = "chiseled_enchanting_table.json";

    private static <E extends Enum<E>> Optional<E> tryParseEnumToken(
        String fieldName,
        @Nullable String raw,
        E[] values,
        Function<E, String> tokenExtractor,
        Map<String, E> legacyAliases,
        E fallbackDefault
    ) {
        String accepted = Arrays.stream(values).map(tokenExtractor).toList().toString();
        if (raw == null || raw.isBlank()) {
            ChiseledEnchantingTable.LOGGER.warn(
                "Empty config token for '{}'; expected one of {} — failing closed to default '{}'",
                fieldName, accepted, tokenExtractor.apply(fallbackDefault)
            );
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (E candidate : values) {
            if (tokenExtractor.apply(candidate).equalsIgnoreCase(normalized)) {
                return Optional.of(candidate);
            }
        }
        E legacy = legacyAliases.get(normalized);
        if (legacy != null) {
            ChiseledEnchantingTable.LOGGER.warn(
                "Config field '{}' uses deprecated legacy token '{}' (alias for '{}'); legacy aliases will be removed in 2.0",
                fieldName, raw.trim(), tokenExtractor.apply(legacy)
            );
            return Optional.of(legacy);
        }
        ChiseledEnchantingTable.LOGGER.warn(
            "Unrecognized config token '{}' for '{}'; expected one of {} — failing closed to default '{}'",
            raw.trim(), fieldName, accepted, tokenExtractor.apply(fallbackDefault)
        );
        return Optional.empty();
    }

    /** Atom #1: How regular wooden bookshelves boost stored/default tiers. */
    public enum ShelfBoostModel {
        MODERN_CURVE("modern_curve"),
        CLASSIC_FLOOR_THIRD("classic_floor_third");

        private final String token;
        ShelfBoostModel(String token) { this.token = token; }
        public String token() { return token; }
        public static Optional<ShelfBoostModel> tryFromToken(@Nullable String raw) {
            return tryParseEnumToken("shelf_boost_model", raw, values(), ShelfBoostModel::token, Map.of(), MODERN_CURVE);
        }
        public static ShelfBoostModel fromToken(@Nullable String raw) {
            return tryFromToken(raw).orElse(MODERN_CURVE);
        }
    }

    /** Atom #2: Whether default level-1 enchantments exclude treasure/curse/epiphany-only entries. */
    public enum DefaultUnlockFilter {
        EXCLUDE_SPECIAL("exclude_special"),
        CLASSIC_UNFILTERED("classic_unfiltered");

        private final String token;
        DefaultUnlockFilter(String token) { this.token = token; }
        public String token() { return token; }
        public static Optional<DefaultUnlockFilter> tryFromToken(@Nullable String raw) {
            return tryParseEnumToken("default_unlock_filter", raw, values(), DefaultUnlockFilter::token, Map.of(), EXCLUDE_SPECIAL);
        }
        public static DefaultUnlockFilter fromToken(@Nullable String raw) {
            return tryFromToken(raw).orElse(EXCLUDE_SPECIAL);
        }
    }

    /** Atom #5: Dual stored-tome 1-3L / ritual XP settlement vs classic full-point level deduction. */
    public enum XpSettlementMode {
        DUAL_STORED_DISCOUNT("dual_stored_discount"),
        CLASSIC_FULL_POINTS("classic_full_points");

        private final String token;
        XpSettlementMode(String token) { this.token = token; }
        public String token() { return token; }
        public static Optional<XpSettlementMode> tryFromToken(@Nullable String raw) {
            return tryParseEnumToken("xp_mode", raw, values(), XpSettlementMode::token, Map.of(), DUAL_STORED_DISCOUNT);
        }
        public static XpSettlementMode fromToken(@Nullable String raw) {
            return tryFromToken(raw).orElse(DUAL_STORED_DISCOUNT);
        }
    }

    /**
     * Atom #7: Underlying cost &amp; book-upgrade data table.
     * {@link #CLASSIC} uses {@code enchantment_costs_classic_1_0_3.json} and
     * {@code book_upgrades_classic_1_0_3.json} (themed specialty &amp; multi-material costs);
     * {@link #MODERN} uses {@code enchantment_costs.json} (flat Lapis / Emerald / Amethyst).
     */
    public enum CostTableDataset {
        MODERN("modern"),
        CLASSIC("classic");

        /** @deprecated Compatibility alias for {@link #CLASSIC}; prefer {@link #CLASSIC}. */
        @Deprecated
        public static final CostTableDataset CLASSIC_1_0_3 = CLASSIC;

        public static final Map<String, CostTableDataset> LEGACY_ALIASES = Map.of(
            "modern_" + "1_1_0", MODERN,
            "1." + "1.0", MODERN,
            "classic_1_0_3", CLASSIC,
            "1.0.3", CLASSIC,
            "103", CLASSIC
        );

        private final String token;
        CostTableDataset(String token) { this.token = token; }
        public String token() { return token; }
        public static Optional<CostTableDataset> tryFromToken(@Nullable String raw) {
            return tryParseEnumToken("cost_table", raw, values(), CostTableDataset::token, LEGACY_ALIASES, MODERN);
        }
        public static CostTableDataset fromToken(@Nullable String raw) {
            return tryFromToken(raw).orElse(MODERN);
        }
    }

    /**
     * Atom #6: How alternative material options and min/max ranges in the active cost table resolve.
     * Orthogonal to {@link CostTableDataset}: works seamlessly on both classic and modern tables.
     */
    public enum MaterialSelectionMode {
        ALL_OPTIONS_MIDPOINT("all_options_midpoint"),
        PLAYER_SALTED_SINGLE("player_salted_single");

        private final String token;
        MaterialSelectionMode(String token) { this.token = token; }
        public String token() { return token; }
        public static Optional<MaterialSelectionMode> tryFromToken(@Nullable String raw) {
            return tryParseEnumToken("material_selection", raw, values(), MaterialSelectionMode::token, Map.of(), ALL_OPTIONS_MIDPOINT);
        }
        public static MaterialSelectionMode fromToken(@Nullable String raw) {
            return tryFromToken(raw).orElse(ALL_OPTIONS_MIDPOINT);
        }
    }

    /** Atom #8: Rounding formula for book research XP cost. */
    public enum ResearchRoundingMode {
        ROUND_NEAREST("round_nearest"),
        FLOOR_DOWN("floor_down");

        private final String token;
        ResearchRoundingMode(String token) { this.token = token; }
        public String token() { return token; }
        public static Optional<ResearchRoundingMode> tryFromToken(@Nullable String raw) {
            return tryParseEnumToken("research_rounding", raw, values(), ResearchRoundingMode::token, Map.of(), ROUND_NEAREST);
        }
        public static ResearchRoundingMode fromToken(@Nullable String raw) {
            return tryFromToken(raw).orElse(ROUND_NEAREST);
        }
    }

    /** Atom #16: Active item transformation recipe set (classic 2 items vs full 6 items). */
    public enum TransformationSetMode {
        FULL_SIX("full_six"),
        CLASSIC_TWO("classic_two");

        private final String token;
        TransformationSetMode(String token) { this.token = token; }
        public String token() { return token; }
        public static Optional<TransformationSetMode> tryFromToken(@Nullable String raw) {
            return tryParseEnumToken("transformation_set", raw, values(), TransformationSetMode::token, Map.of(), FULL_SIX);
        }
        public static TransformationSetMode fromToken(@Nullable String raw) {
            return tryFromToken(raw).orElse(FULL_SIX);
        }
    }

    /** Atom #17: Remainder item returned when an Enchanted Book is completely drained. */
    public enum DrainedBookResult {
        DEPLETED_TOME("depleted_tome"),
        PLAIN_BOOK("plain_book");

        private final String token;
        DrainedBookResult(String token) { this.token = token; }
        public String token() { return token; }
        public static Optional<DrainedBookResult> tryFromToken(@Nullable String raw) {
            return tryParseEnumToken("drained_book_result", raw, values(), DrainedBookResult::token, Map.of(), DEPLETED_TOME);
        }
        public static DrainedBookResult fromToken(@Nullable String raw) {
            return tryFromToken(raw).orElse(DEPLETED_TOME);
        }
    }

    /** Legacy policy/JSON key aliases expanded when migrating pre-atom {@code classic_1_0_3_mode}. */
    public static final Set<String> GRANULAR_RULE_ATOM_KEYS = Set.of(
        "shelf_boost_model",
        "default_unlock_filter",
        "direct_tier_upgrade",
        "incremental_credit",
        "xp_mode",
        "cost_table",
        "material_selection",
        "research_rounding",
        "library_discount",
        "tome_consumption_mode",
        "tome_locks",
        "provenance",
        "ritual_queue",
        "epiphany_enabled",
        "disenchant_enabled",
        "transformation_set",
        "drained_book_result",
        "tome_page_enabled",
        "librarian_tome_offers"
    );

    public static final ModRuntimeConfig DEFAULT = new Draft().build();

    /**
     * Built-in one-click gameplay presets exposed in {@code ChiseledEnchantingConfigScreen}
     * and {@code /cet}:
     * <ul>
     *   <li>{@link #CLASSIC}: 1:1 faithful profile of classic {@code 1.0.3} rules;</li>
     *   <li>{@link #CASUAL}: relaxed progression with infinite Master Tomes and generous discounts;</li>
     *   <li>{@link #STANDARD}: shipped default balance ({@link #DEFAULT});</li>
     *   <li>{@link #EXPERT}: high-stakes archivist rules with LOS, discovery gates, and strict wear.</li>
     * </ul>
     */
    public enum Preset {
        CLASSIC("classic"),
        CASUAL("casual"),
        STANDARD("standard"),
        EXPERT("expert");

        /** @deprecated Compatibility alias for {@link #CLASSIC}; prefer {@link #CLASSIC}. */
        @Deprecated
        public static final Preset CLASSIC_1_0_3 = CLASSIC;

        public static final Map<String, Preset> LEGACY_ALIASES = Map.ofEntries(
            Map.entry("classic_1_0_3", CLASSIC),
            Map.entry("1.0.3", CLASSIC),
            Map.entry("103", CLASSIC),
            Map.entry("purist", CLASSIC),
            Map.entry("easy", CASUAL),
            Map.entry("relaxed", CASUAL),
            Map.entry("generous", CASUAL),
            Map.entry("default", STANDARD),
            Map.entry("normal", STANDARD),
            Map.entry("1." + "1.0", STANDARD),
            Map.entry("hard", EXPERT),
            Map.entry("archivist", EXPERT)
        );

        private final String id;

        Preset(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public String buttonKey() {
            return "config.chiseled_enchanting_table.preset." + this.id;
        }

        public String tooltipKey() {
            return "config.chiseled_enchanting_table.preset." + this.id + ".desc";
        }

        public static Optional<Preset> tryFromToken(@Nullable String raw) {
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return tryParseEnumToken("preset", raw, values(), Preset::id, LEGACY_ALIASES, STANDARD);
        }

        @Nullable
        public static Preset fromToken(@Nullable String raw) {
            return tryFromToken(raw).orElse(null);
        }

        public void applyTo(Draft draft) {
            if (draft == null) {
                return;
            }
            boolean preservedOverrideDp = draft.overrideDatapacks;

            Draft fresh = new Draft();
            fresh.enchantedBookStackSize = 64;
            fresh.overrideDatapacks = preservedOverrideDp;

            switch (this) {
                case STANDARD -> {
                    // Pure shipped defaults (including 64-stackable Enchanted Books).
                }
                case CLASSIC -> {
                    fresh.applyClassic103Overrides();
                    markModifiedRuntimeKeys(fresh);
                }
                case CASUAL -> {
                    fresh.vanillaTableMode = VanillaTableMode.BOOKS;
                    fresh.anvilFlatRepairCost = true;
                    fresh.anvilBlockSameBookMerge = false;
                    fresh.restrictVillagerInfinityBooks = false;
                    fresh.grindstoneDepletedTome = false;

                    fresh.directGatesEnabled = false;
                    fresh.blankMaxTierMode = BlankMaxTierMode.FULL_CURVE;
                    fresh.horizontalScanRadius = 4;
                    fresh.verticalScanRadius = 2;
                    fresh.requireBreathingRoom = false;
                    fresh.requireLineOfSight = false;

                    fresh.tomeConsumptionMode = MasterTomeWear.ConsumptionMode.KEEP;
                    fresh.tomeRegenEnabled = true;
                    fresh.tomeRegenBaseSeconds = 3_600;
                    fresh.tomeRegenTier1Seconds = 1_800;
                    fresh.tomeRegenTier2Seconds = 600;
                    fresh.lockedShelfBreakable = true;
                    fresh.lockedBookExtractionBlocked = false;
                    fresh.maxWoodenDiscountPercent = 20;
                    fresh.maxTotalXpDiscountPercent = 50;
                    fresh.bookCombineEnabled = true;
                    fresh.bookCombineXpMultiplier = 0.50;
                    fresh.bookResearchEnabled = true;
                    fresh.bookResearchXpMultiplier = 1.00;

                    fresh.epiphanyEnabled = true;
                    fresh.epiphanyMinBookshelves = 16;
                    fresh.epiphanyBaseChance = 0.45;
                    fresh.epiphanyMaxChance = 0.95;

                    fresh.disenchantEnabled = true;
                    fresh.xpRefundScale = 1.00;
                    fresh.externalXpRefundScale = 0.50;
                    fresh.allowCursePurge = true;
                    fresh.cursePurgeXpCost = 6;
                    fresh.requirePriorDiscovery = false;
                    markModifiedRuntimeKeys(fresh);
                }
                case EXPERT -> {
                    fresh.vanillaTableMode = VanillaTableMode.ALL;
                    fresh.anvilFlatRepairCost = false;
                    fresh.anvilBlockSameBookMerge = true;
                    fresh.restrictVillagerInfinityBooks = true;
                    fresh.grindstoneDepletedTome = true;

                    fresh.defaultUnlockFilter = DefaultUnlockFilter.EXCLUDE_SPECIAL;
                    fresh.directTierUpgrade = false;
                    fresh.directGatesEnabled = true;
                    fresh.blankMaxTierMode = BlankMaxTierMode.LEVEL_ONE_ONLY;
                    fresh.maxDirectBookshelves = 48;
                    fresh.horizontalScanRadius = 3;
                    fresh.verticalScanRadius = 2;
                    fresh.requireBreathingRoom = true;
                    fresh.minOpenAdjacentBlocks = 12;
                    fresh.requireLineOfSight = true;

                    fresh.tomeConsumptionMode = MasterTomeWear.ConsumptionMode.WEAR;
                    fresh.maxTomeUses = 2;
                    fresh.bookCopyWearCost = 2;
                    fresh.tomeDepleteBehavior = MasterTomeWear.DepleteBehavior.DEPLETED_TOME;
                    fresh.tomeRegenEnabled = true;
                    fresh.tomeRegenBaseSeconds = 14_400;
                    fresh.tomeRegenTier1Shelves = 42;
                    fresh.tomeRegenTier1Seconds = 7_200;
                    fresh.tomeRegenTier2Shelves = 52;
                    fresh.tomeRegenTier2Seconds = 3_600;
                    fresh.tomeLocksEnabled = true;
                    fresh.lockedShelfBreakable = false;
                    fresh.lockedBookExtractionBlocked = true;
                    fresh.provenanceEnabled = true;
                    fresh.ritualQueueEnabled = true;

                    fresh.incrementalCredit = false;
                    fresh.materialSelectionMode = MaterialSelectionMode.PLAYER_SALTED_SINGLE;
                    fresh.researchRoundingMode = ResearchRoundingMode.ROUND_NEAREST;
                    fresh.libraryDiscountEnabled = true;
                    fresh.maxWoodenDiscountPercent = 8;
                    fresh.maxTotalXpDiscountPercent = 20;
                    fresh.bookCombineEnabled = true;
                    fresh.bookCombineXpMultiplier = 1.35;
                    fresh.bookResearchEnabled = true;
                    fresh.bookResearchXpMultiplier = 2.25;

                    fresh.epiphanyEnabled = true;
                    fresh.epiphanyMinBookshelves = 42;
                    fresh.epiphanyBaseChance = 0.12;
                    fresh.epiphanyMaxChance = 0.60;
                    fresh.epiphanyMaxSlotsCap = 2;
                    fresh.epiphanyCycleSeconds = 900;
                    fresh.epiphanyFreezeXpLevels = 8;
                    fresh.epiphanyFreezeDurationSeconds = 600;
                    fresh.epiphanyMaxFreezeSeconds = 1_800;
                    fresh.epiphanyRerollXpLevels = 10;

                    fresh.disenchantEnabled = true;
                    fresh.xpRefundScale = 0.50;
                    fresh.externalXpRefundScale = 0.05;
                    fresh.stripXpMin = 6;
                    fresh.stripXpMax = 18;
                    fresh.allowCursePurge = true;
                    fresh.cursePurgeXpCost = 28;
                    fresh.requirePriorDiscovery = true;
                    fresh.drainedBookResult = DrainedBookResult.DEPLETED_TOME;
                    fresh.tier1Bookshelves = 20;
                    fresh.tier2Bookshelves = 36;
                    fresh.tier3QualifyingEnchantments = 10;
                    fresh.transformationMaxBatchSize = 8;
                    fresh.tomePageEnabled = true;
                    fresh.tomePageRequiredShelves = 12;
                    fresh.sacrificeRequired = 24;
                    fresh.earlyUnlockXpLevels = 25;
                    fresh.earlyUnlockGritCount = 12;

                    fresh.vanillaChestBooksMode = VanillaChestBooksMode.STRIP_VANILLA;
                    fresh.bookshelfPoolEnabled = true;
                    fresh.bookshelfMinBooks = 1;
                    fresh.bookshelfMaxBooks = 2;
                    fresh.bookshelfEnchantedProb = 0.03;
                    fresh.librarianTomeOffers = false;
                    markModifiedRuntimeKeys(fresh);
                }
            }

            fresh.normalizeAfterEdit(null);
            draft.copyFrom(fresh);
        }

        public ModRuntimeConfig createConfig(boolean overrideDp) {
            Draft probe = new Draft();
            probe.overrideDatapacks = overrideDp;
            applyTo(probe);
            return probe.build();
        }

        public boolean matches(Draft draft) {
            if (draft == null) {
                return false;
            }
            return countDeviations(draft) == 0;
        }

        /** Counts how many catalog options in {@code draft} differ from this preset's baseline. */
        public int countDeviations(Draft draft) {
            if (draft == null) {
                return 0;
            }
            Draft target = createConfig(draft.overrideDatapacks).toDraft();
            int diff = 0;
            for (var opt : ConfigCatalog.ALL_OPTIONS) {
                if ("override_datapacks".equals(opt.key())) {
                    continue;
                }
                if (!Objects.equals(opt.get(draft), opt.get(target))) {
                    diff++;
                }
            }
            return diff;
        }

        private static void markModifiedRuntimeKeys(Draft target) {
            Draft def = new Draft();
            for (var opt : ConfigCatalog.ALL_OPTIONS) {
                if ("override_datapacks".equals(opt.key()) || "enchanted_book_stack_size".equals(opt.key())) {
                    continue;
                }
                if (!Objects.equals(opt.get(target), opt.get(def))) {
                    target.customizedKeys.add(opt.key());
                }
            }
        }
    }

    public static final int CLASSIC_RULE_ATOM_COUNT = 19;

    /**
     * Returns {@code true} iff all 19 core mechanism atoms currently match their {@code 1.0.3}
     * classic state. Derived directly from the active rule atoms — never stored as a separate
     * desynchronizable flag.
     */
    public boolean isAllClassicAtoms() {
        return countClassic103Atoms() == CLASSIC_RULE_ATOM_COUNT;
    }

    /** Convenience derived alias preserved for callers checking pure-1.0.3 presentation state. */
    public boolean classic103Mode() {
        return isAllClassicAtoms();
    }

    /** Counts how many of the 19 core mechanism rule atoms match their {@code 1.0.3} state. */
    public int countClassic103Atoms() {
        int n = 0;
        if (shelfBoostModel == ShelfBoostModel.CLASSIC_FLOOR_THIRD) n++;
        if (defaultUnlockFilter == DefaultUnlockFilter.CLASSIC_UNFILTERED) n++;
        if (!directTierUpgrade) n++;
        if (!incrementalCredit) n++;
        if (xpMode == XpSettlementMode.CLASSIC_FULL_POINTS) n++;
        if (costTableDataset == CostTableDataset.CLASSIC) n++;
        if (materialSelectionMode == MaterialSelectionMode.PLAYER_SALTED_SINGLE) n++;
        if (researchRoundingMode == ResearchRoundingMode.FLOOR_DOWN) n++;
        if (!libraryDiscountEnabled) n++;
        if (tomeConsumptionMode == MasterTomeWear.ConsumptionMode.KEEP) n++;
        if (!tomeLocksEnabled) n++;
        if (!provenanceEnabled) n++;
        if (!ritualQueueEnabled) n++;
        if (!epiphanyEnabled) n++;
        if (!disenchantEnabled) n++;
        if (transformationSetMode == TransformationSetMode.CLASSIC_TWO) n++;
        if (drainedBookResult == DrainedBookResult.PLAIN_BOOK) n++;
        if (!tomePageEnabled) n++;
        if (!librarianTomeOffers) n++;
        return n;
    }

    private static volatile ModRuntimeConfig localInstance;
    @Nullable
    private static volatile ModRuntimeConfig remoteServerInstance;
    private static volatile ModRuntimeConfig remotePreferences;
    public static ModRuntimeConfig preferences() { return remotePreferences != null ? remotePreferences : get(); }
    public static void setRemotePreferences(ModRuntimeConfig value) { remotePreferences = value; }
    private static volatile boolean clientCanEditServerConfig = false;
    private static volatile PackPolicy serverPackPolicy = PackPolicy.NONE;
    @Nullable
    private static volatile PackPolicy remotePackPolicy;

    /**
     * Authoritative governance policy loaded from datapacks ({@code pack_policy.json} plus any
     * keys explicitly specified by external datapacks).
     */
    public record PackPolicy(
        boolean enforceDatapackPriority,
        boolean lockInGameConfig,
        Set<String> datapackOverriddenKeys,
        Set<String> lockedKeys
    ) {
        public static final PackPolicy NONE = new PackPolicy(false, false, Set.of(), Set.of());

        public PackPolicy {
            datapackOverriddenKeys = datapackOverriddenKeys == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(datapackOverriddenKeys));
            lockedKeys = lockedKeys == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(lockedKeys));
        }

        public boolean isKeyLocked(String key) {
            if (lockInGameConfig) {
                return true;
            }
            if ("override_datapacks".equals(key) && enforceDatapackPriority) {
                return true;
            }
            return lockedKeys.contains(key);
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("enforce_datapack_priority", enforceDatapackPriority);
            obj.addProperty("lock_in_game_config", lockInGameConfig);
            com.google.gson.JsonArray dpArr = new com.google.gson.JsonArray();
            for (String k : datapackOverriddenKeys) dpArr.add(k);
            obj.add("datapack_overridden_keys", dpArr);
            com.google.gson.JsonArray lockArr = new com.google.gson.JsonArray();
            for (String k : lockedKeys) lockArr.add(k);
            obj.add("locked_keys", lockArr);
            return obj;
        }

        public String toJsonString() {
            return GSON.toJson(toJson());
        }

        public static PackPolicy fromJson(JsonObject root, Set<String> externalDatapackKeys) {
            if (root != null) {
                for (String key : Set.of("enforce_datapack_priority", "lock_in_game_config")) {
                    if (root.has(key) && (!root.get(key).isJsonPrimitive()
                        || !root.getAsJsonPrimitive(key).isBoolean()))
                        throw new IllegalArgumentException("Policy flag must be boolean: " + key);
                }
                for (String key : Set.of("locked_keys", "datapack_overridden_keys")) {
                    if (!root.has(key)) continue;
                    if (!root.get(key).isJsonArray()) throw new IllegalArgumentException("Policy keys must be array: " + key);
                    for (var element : root.getAsJsonArray(key)) {
                        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
                            throw new IllegalArgumentException("Policy key must be a string");
                        String k = element.getAsString();
                        if (!"classic_1_0_3_mode".equals(k)
                            && ConfigCatalog.ALL_OPTIONS.stream().noneMatch(option -> option.key().equals(k))) {
                            throw new IllegalArgumentException("Unknown policy key: " + k);
                        }
                    }
                }
            }
            boolean enforceDp = root != null && boolOr(root, "enforce_datapack_priority", false);
            boolean lockAll = root != null && boolOr(root, "lock_in_game_config", false);
            Set<String> dpKeys = new LinkedHashSet<>();
            if (externalDatapackKeys != null) {
                dpKeys.addAll(externalDatapackKeys);
            }
            if (root != null && root.has("datapack_overridden_keys") && root.get("datapack_overridden_keys").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("datapack_overridden_keys")) {
                    if (el.isJsonPrimitive()) {
                        String k = el.getAsString();
                        if ("classic_1_0_3_mode".equals(k)) {
                            dpKeys.addAll(GRANULAR_RULE_ATOM_KEYS);
                        } else {
                            dpKeys.add(k);
                        }
                    }
                }
            }
            Set<String> locked = new LinkedHashSet<>();
            if (root != null && root.has("locked_keys") && root.get("locked_keys").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("locked_keys")) {
                    if (el.isJsonPrimitive()) {
                        String k = el.getAsString();
                        if ("classic_1_0_3_mode".equals(k)) {
                            locked.addAll(GRANULAR_RULE_ATOM_KEYS);
                        } else {
                            locked.add(k);
                        }
                    }
                }
            }
            return new PackPolicy(enforceDp, lockAll, dpKeys, locked);
        }

        public static PackPolicy fromJsonString(String raw) {
            if (raw == null || raw.isBlank()) {
                return NONE;
            }
            return fromJson(JsonParser.parseString(raw).getAsJsonObject(), Set.of());
        }
    }

    public ModRuntimeConfig {
        enchantedBookStackSize = clampInt(enchantedBookStackSize, 1, 64);
        vanillaTableMode = vanillaTableMode != null ? vanillaTableMode : VanillaTableMode.BOOKS;

        shelfBoostModel = shelfBoostModel != null ? shelfBoostModel : ShelfBoostModel.MODERN_CURVE;
        defaultUnlockFilter = defaultUnlockFilter != null ? defaultUnlockFilter : DefaultUnlockFilter.EXCLUDE_SPECIAL;
        blankMaxTierMode = blankMaxTierMode != null ? blankMaxTierMode : BlankMaxTierMode.HALF_CEIL;
        maxDirectBookshelves = clampInt(maxDirectBookshelves, 4, 64);
        horizontalScanRadius = clampInt(horizontalScanRadius, 1, BookshelfProgression.MAX_SCAN_HORIZONTAL_RADIUS);
        verticalScanRadius = clampInt(verticalScanRadius, 0, BookshelfProgression.MAX_SCAN_VERTICAL_RADIUS);
        while (verticalScanRadius > 0
            && BookshelfProgression.scanVolumeBlocks(horizontalScanRadius, verticalScanRadius)
                > BookshelfProgression.MAX_SCAN_VOLUME_BLOCKS) {
            verticalScanRadius--;
        }
        while (horizontalScanRadius > 1
            && BookshelfProgression.scanVolumeBlocks(horizontalScanRadius, verticalScanRadius)
                > BookshelfProgression.MAX_SCAN_VOLUME_BLOCKS) {
            horizontalScanRadius--;
        }
        minOpenAdjacentBlocks = clampInt(minOpenAdjacentBlocks, 1, 17);

        tomeConsumptionMode = tomeConsumptionMode != null ? tomeConsumptionMode : MasterTomeWear.ConsumptionMode.WEAR;
        maxTomeUses = clampInt(maxTomeUses, 1, 256);
        bookCopyWearCost = clampInt(bookCopyWearCost, 1, maxTomeUses);
        tomeDepleteBehavior = tomeDepleteBehavior != null ? tomeDepleteBehavior : MasterTomeWear.DepleteBehavior.STEP_DOWN;
        tomeRegenBaseSeconds = clampInt(tomeRegenBaseSeconds, 1, 86_400);
        tomeRegenTier1Shelves = clampInt(tomeRegenTier1Shelves, 1, 128);
        tomeRegenTier1Seconds = clampInt(tomeRegenTier1Seconds, 1, tomeRegenBaseSeconds);
        tomeRegenTier2Shelves = clampInt(tomeRegenTier2Shelves, tomeRegenTier1Shelves, 128);
        tomeRegenTier2Seconds = clampInt(tomeRegenTier2Seconds, 1, tomeRegenTier1Seconds);
        earlyUnlockXpLevels = clampInt(earlyUnlockXpLevels, 0, 512);
        earlyUnlockGritCount = clampInt(earlyUnlockGritCount, 0, 512);
        sacrificeRequired = clampInt(sacrificeRequired, 1, 1024);
        tomePageRequiredShelves = clampInt(tomePageRequiredShelves, 1, 512);

        xpMode = xpMode != null ? xpMode : XpSettlementMode.DUAL_STORED_DISCOUNT;
        costTableDataset = costTableDataset != null ? costTableDataset : CostTableDataset.MODERN;
        materialSelectionMode = materialSelectionMode != null ? materialSelectionMode : MaterialSelectionMode.ALL_OPTIONS_MIDPOINT;
        researchRoundingMode = researchRoundingMode != null ? researchRoundingMode : ResearchRoundingMode.ROUND_NEAREST;
        maxTotalXpDiscountPercent = clampInt(maxTotalXpDiscountPercent, 0, 90);
        maxWoodenDiscountPercent = clampInt(maxWoodenDiscountPercent, 0, maxTotalXpDiscountPercent);
        bookCombineXpMultiplier = round2(clampDouble(bookCombineXpMultiplier, 0.10, 3.00));
        bookResearchXpMultiplier = round2(clampDouble(bookResearchXpMultiplier, 0.10, 3.00));

        epiphanyMinBookshelves = clampInt(epiphanyMinBookshelves, 0, 64);
        epiphanyBaseChance = round2(clampDouble(epiphanyBaseChance, 0.0, 1.0));
        epiphanyMaxChance = round2(clampDouble(epiphanyMaxChance, epiphanyBaseChance, 1.0));
        epiphanyMaxSlotsCap = clampInt(epiphanyMaxSlotsCap, 1, 8);
        epiphanyCycleSeconds = clampInt(epiphanyCycleSeconds, 10, 3600);
        epiphanyFreezeXpLevels = clampInt(epiphanyFreezeXpLevels, 0, 30);
        epiphanyFreezeDurationSeconds = clampInt(epiphanyFreezeDurationSeconds, 10, 3600);
        epiphanyMaxFreezeSeconds = clampInt(epiphanyMaxFreezeSeconds, epiphanyFreezeDurationSeconds, 7200);
        epiphanyRerollXpLevels = clampInt(epiphanyRerollXpLevels, 0, 30);

        xpRefundScale = round2(clampDouble(xpRefundScale, 0.0, 1.0));
        externalXpRefundScale = round2(clampDouble(externalXpRefundScale, 0.0, xpRefundScale));
        stripXpMin = clampInt(stripXpMin, 1, 30);
        stripXpMax = clampInt(stripXpMax, stripXpMin, 50);
        cursePurgeXpCost = clampInt(cursePurgeXpCost, 1, 50);
        transformationSetMode = transformationSetMode != null ? transformationSetMode : TransformationSetMode.FULL_SIX;
        drainedBookResult = drainedBookResult != null ? drainedBookResult : DrainedBookResult.DEPLETED_TOME;
        tier2Bookshelves = clampInt(tier2Bookshelves, 0, maxDirectBookshelves);
        tier1Bookshelves = clampInt(tier1Bookshelves, 0, tier2Bookshelves);
        tier3QualifyingEnchantments = clampInt(tier3QualifyingEnchantments, 1, 30);
        transformationMaxBatchSize = clampInt(transformationMaxBatchSize, 1, 64);

        vanillaChestBooksMode = vanillaChestBooksMode != null ? vanillaChestBooksMode : VanillaChestBooksMode.KEEP_BOTH;
        bookshelfMinBooks = clampInt(bookshelfMinBooks, 0, 6);
        bookshelfMaxBooks = clampInt(bookshelfMaxBooks, bookshelfMinBooks, 6);
        bookshelfEnchantedProb = round2(clampDouble(bookshelfEnchantedProb, 0.0, 1.0));

        customizedKeys = customizedKeys == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(customizedKeys));
    }

    public ModRuntimeConfig withEnforcedPolicy(PackPolicy policy) {
        if (policy == null || !policy.enforceDatapackPriority() || !this.overrideDatapacks) {
            return this;
        }
        Draft d = this.toDraft();
        d.overrideDatapacks = false;
        d.customizedKeys.remove("override_datapacks");
        return d.build();
    }

    public boolean shouldApplyKey(String key) {
        PackPolicy policy = effectivePackPolicy();
        if (policy.isKeyLocked(key)) {
            return false;
        }
        if (policy.datapackOverriddenKeys().contains(key)) {
            return !policy.enforceDatapackPriority() && overrideDatapacks;
        }
        return true;
    }

    public static PackPolicy serverPackPolicy() {
        return serverPackPolicy;
    }

    public static void setServerPackPolicy(PackPolicy policy) {
        serverPackPolicy = policy != null ? policy : PackPolicy.NONE;
    }

    public static PackPolicy effectivePackPolicy() {
        PackPolicy remote = remotePackPolicy;
        return remote != null ? remote : serverPackPolicy;
    }

    public Draft toDraft() {
        return new Draft(this);
    }

    // ---- Static lifecycle & active-session accessors ----

    public static ModRuntimeConfig get() {
        ModRuntimeConfig cur = localInstance;
        if (cur == null) {
            synchronized (ModRuntimeConfig.class) {
                cur = localInstance;
                if (cur == null) {
                    cur = loadFromDiskOrWriteDefault();
                    localInstance = cur;
                }
            }
        }
        return cur;
    }

    /** Returns server-synced config when connected to a multiplayer server, otherwise local config. */
    public static ModRuntimeConfig effective() {
        ModRuntimeConfig remote = remoteServerInstance;
        return remote != null ? remote : ChiseledEnchantingTableData.runtimeConfiguration();
    }

    /**
     * Side-aware authoritative config accessor: server threads (Dedicated Server and LAN/Singleplayer
     * IntegratedServer) always read {@link ChiseledEnchantingTableData#runtimeConfiguration()}, while
     * client threads read the server-synced {@link #effective()} snapshot.
     */
    public static ModRuntimeConfig forSide(boolean isClientSide) {
        return isClientSide ? effective() : ChiseledEnchantingTableData.runtimeConfiguration();
    }

    public static void refreshSameJvmRemoteIfPresent(ModRuntimeConfig effectiveSynced, PackPolicy policy) {
        if (remoteServerInstance != null) {
            remoteServerInstance = effectiveSynced;
            remotePackPolicy = policy;
        }
    }

    @Nullable
    public static ModRuntimeConfig forAnvilSide(boolean isClientSide) {
        if (!isClientSide) {
            return ChiseledEnchantingTableData.runtimeConfiguration();
        }
        return remoteServerInstance;
    }

    public static void setLocalAndSave(ModRuntimeConfig updated) {
        ModRuntimeConfig normalized = updated != null ? updated : DEFAULT;
        saveToDisk(normalized);
        localInstance = normalized;
    }

    /** Explicit reload must reject malformed input, not silently replace it with defaults. */
    public static ModRuntimeConfig reloadFromDisk() {
        try (Reader reader = Files.newBufferedReader(configFilePath(), StandardCharsets.UTF_8)) {
            return fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException("Cannot reload " + FILE_NAME, error);
        }
    }

    public static void applyRemoteSync(ModRuntimeConfig remote, PackPolicy policy, boolean canEdit) {
        remoteServerInstance = remote;
        remotePackPolicy = policy;
        clientCanEditServerConfig = canEdit;
    }

    public static void applyRemoteSync(ModRuntimeConfig remote, boolean canEdit) {
        applyRemoteSync(remote, null, canEdit);
    }

    public static void clearRemoteSync() {
        remoteServerInstance = null;
        remotePreferences = null;
        remotePackPolicy = null;
        clientCanEditServerConfig = false;
        acknowledgedSaveId = 0;
    }

    public static boolean clientCanEditServerConfig() {
        return clientCanEditServerConfig;
    }

    private static Path configFilePath() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        return configDir.resolve(FILE_NAME);
    }

    private static ModRuntimeConfig loadFromDiskOrWriteDefault() {
        try {
            Path path = configFilePath();
            Files.createDirectories(path.getParent());
            if (Files.isRegularFile(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    ModRuntimeConfig parsed = fromJson(obj);
                    saveToDisk(parsed);
                    return parsed;
                }
            }
            saveToDisk(DEFAULT);
            return DEFAULT;
        } catch (Exception e) {
            ChiseledEnchantingTable.LOGGER.warn("Failed to read {}; using built-in defaults", FILE_NAME, e);
            return DEFAULT;
        }
    }

    /** Exposed path overload allows failure/atomic-replacement tests without Fabric bootstrap. */
    public static void writeAtomically(Path path, ModRuntimeConfig cfg) throws java.io.IOException {
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        String json = GSON.toJson(cfg.toJson()) + "\n";
        Path temporary = Files.createTempFile(parent, ".cet-config-", ".tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (java.io.IOException atomicFailure) {
                clearWindowsReadOnly(path);
                java.io.IOException lastFailure = atomicFailure;
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        return;
                    } catch (java.io.IOException moveFailure) {
                        lastFailure = moveFailure;
                        pauseBeforeRetry(attempt);
                    }
                }
                try {
                    Files.copy(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (java.io.IOException copyFailure) {
                    lastFailure = copyFailure;
                }
                try {
                    Files.writeString(path, json, StandardCharsets.UTF_8);
                } catch (java.io.IOException directFailure) {
                    directFailure.addSuppressed(lastFailure);
                    throw directFailure;
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void clearWindowsReadOnly(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                Files.setAttribute(path, "dos:readonly", false);
            }
        } catch (Exception ignored) {
        }
    }

    private static void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(25L * (attempt + 1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public static void saveToDisk(ModRuntimeConfig cfg) {
        try {
            writeAtomically(configFilePath(), cfg);
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException("Cannot save " + FILE_NAME, error);
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong SAVE_REQUEST_IDS = new java.util.concurrent.atomic.AtomicLong();
    public static long nextSaveRequestId() { return SAVE_REQUEST_IDS.incrementAndGet(); }
    private static volatile long acknowledgedSaveId;
    private static volatile boolean acknowledgedSaveAccepted;
    public static void acknowledgeSave(long id, boolean accepted) {
        acknowledgedSaveAccepted = accepted;
        acknowledgedSaveId = id;
    }
    public static long acknowledgedSaveId() { return acknowledgedSaveId; }
    public static boolean acknowledgedSaveAccepted() { return acknowledgedSaveAccepted; }

    public String toJsonString() {
        return GSON.toJson(toJson());
    }

    public static ModRuntimeConfig fromJsonString(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        return fromJson(JsonParser.parseString(raw).getAsJsonObject());
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty(
            "_comment",
            "Chiseled Enchanting Table unified granular configuration. Editable directly in-game via the Config button, Arcane Grimoire, /cet, or Mod Menu."
        );
        root.addProperty("enchanted_book_stack_size", enchantedBookStackSize);

        JsonObject general = new JsonObject();
        general.addProperty("override_datapacks", overrideDatapacks);
        general.addProperty("enchanted_book_stack_size", enchantedBookStackSize);
        general.addProperty("vanilla_table_mode", vanillaTableMode.token());
        general.addProperty("anvil_flat_repair_cost", anvilFlatRepairCost);
        general.addProperty("anvil_block_same_book_merge", anvilBlockSameBookMerge);
        general.addProperty("restrict_villager_infinity_books", restrictVillagerInfinityBooks);
        general.addProperty("grindstone_depleted_tome", grindstoneDepletedTome);
        // Derived legacy mirror kept so external tools inspecting JSON can see full-1.0.3 state.
        general.addProperty("classic_1_0_3_mode", isAllClassicAtoms());
        root.add("general", general);

        JsonObject bookshelves = new JsonObject();
        bookshelves.addProperty("shelf_boost_model", shelfBoostModel.token());
        bookshelves.addProperty("default_unlock_filter", defaultUnlockFilter.token());
        bookshelves.addProperty("direct_tier_upgrade", directTierUpgrade);
        bookshelves.addProperty("direct_gates_enabled", directGatesEnabled);
        bookshelves.addProperty("blank_max_tier_mode", blankMaxTierMode.token());
        bookshelves.addProperty("max_direct_bookshelves", maxDirectBookshelves);
        bookshelves.addProperty("horizontal_scan_radius", horizontalScanRadius);
        bookshelves.addProperty("vertical_scan_radius", verticalScanRadius);
        bookshelves.addProperty("require_breathing_room", requireBreathingRoom);
        bookshelves.addProperty("min_open_adjacent_blocks", minOpenAdjacentBlocks);
        bookshelves.addProperty("require_line_of_sight", requireLineOfSight);
        root.add("bookshelves", bookshelves);

        JsonObject tomes = new JsonObject();
        tomes.addProperty("tome_consumption_mode", tomeConsumptionMode.token());
        tomes.addProperty("max_tome_uses", maxTomeUses);
        tomes.addProperty("book_copy_wear_cost", bookCopyWearCost);
        tomes.addProperty("tome_deplete_behavior", tomeDepleteBehavior.token());
        tomes.addProperty("tome_regen_enabled", tomeRegenEnabled);
        tomes.addProperty("tome_regen_base_seconds", tomeRegenBaseSeconds);
        tomes.addProperty("tome_regen_tier1_shelves", tomeRegenTier1Shelves);
        tomes.addProperty("tome_regen_tier1_seconds", tomeRegenTier1Seconds);
        tomes.addProperty("tome_regen_tier2_shelves", tomeRegenTier2Shelves);
        tomes.addProperty("tome_regen_tier2_seconds", tomeRegenTier2Seconds);
        tomes.addProperty("tome_locks", tomeLocksEnabled);
        tomes.addProperty("locked_shelf_breakable", lockedShelfBreakable);
        tomes.addProperty("locked_book_extraction_blocked", lockedBookExtractionBlocked);
        tomes.addProperty("provenance", provenanceEnabled);
        tomes.addProperty("ritual_queue", ritualQueueEnabled);
        tomes.addProperty("early_unlock_xp_levels", earlyUnlockXpLevels);
        tomes.addProperty("early_unlock_grit_count", earlyUnlockGritCount);
        tomes.addProperty("sacrifice_required", sacrificeRequired);
        tomes.addProperty("tome_page_required_shelves", tomePageRequiredShelves);
        tomes.addProperty("incremental_credit", incrementalCredit);
        tomes.addProperty("xp_mode", xpMode.token());
        tomes.addProperty("cost_table", costTableDataset.token());
        tomes.addProperty("material_selection", materialSelectionMode.token());
        tomes.addProperty("research_rounding", researchRoundingMode.token());
        tomes.addProperty("library_discount", libraryDiscountEnabled);
        tomes.addProperty("max_wooden_discount_percent", maxWoodenDiscountPercent);
        tomes.addProperty("max_total_xp_discount_percent", maxTotalXpDiscountPercent);
        tomes.addProperty("book_combine_enabled", bookCombineEnabled);
        tomes.addProperty("book_combine_xp_multiplier", bookCombineXpMultiplier);
        tomes.addProperty("book_research_enabled", bookResearchEnabled);
        tomes.addProperty("book_research_xp_multiplier", bookResearchXpMultiplier);
        root.add("tomes_and_economy", tomes);

        JsonObject epiphany = new JsonObject();
        epiphany.addProperty("epiphany_enabled", epiphanyEnabled);
        epiphany.addProperty("epiphany_min_bookshelves", epiphanyMinBookshelves);
        epiphany.addProperty("epiphany_base_chance", epiphanyBaseChance);
        epiphany.addProperty("epiphany_max_chance", epiphanyMaxChance);
        epiphany.addProperty("epiphany_max_slots_cap", epiphanyMaxSlotsCap);
        epiphany.addProperty("epiphany_cycle_seconds", epiphanyCycleSeconds);
        epiphany.addProperty("epiphany_freeze_xp_levels", epiphanyFreezeXpLevels);
        epiphany.addProperty("epiphany_freeze_duration_seconds", epiphanyFreezeDurationSeconds);
        epiphany.addProperty("epiphany_max_freeze_seconds", epiphanyMaxFreezeSeconds);
        epiphany.addProperty("epiphany_reroll_xp_levels", epiphanyRerollXpLevels);
        root.add("treasure_epiphany", epiphany);

        JsonObject cleanse = new JsonObject();
        cleanse.addProperty("disenchant_enabled", disenchantEnabled);
        cleanse.addProperty("xp_refund_scale", xpRefundScale);
        cleanse.addProperty("external_xp_refund_scale", externalXpRefundScale);
        cleanse.addProperty("strip_xp_min", stripXpMin);
        cleanse.addProperty("strip_xp_max", stripXpMax);
        cleanse.addProperty("allow_curse_purge", allowCursePurge);
        cleanse.addProperty("curse_purge_xp_cost", cursePurgeXpCost);
        cleanse.addProperty("transformation_set", transformationSetMode.token());
        cleanse.addProperty("require_prior_discovery", requirePriorDiscovery);
        cleanse.addProperty("drained_book_result", drainedBookResult.token());
        cleanse.addProperty("tome_page_enabled", tomePageEnabled);
        cleanse.addProperty("tier1_bookshelves", tier1Bookshelves);
        cleanse.addProperty("tier2_bookshelves", tier2Bookshelves);
        cleanse.addProperty("tier3_qualifying_enchantments", tier3QualifyingEnchantments);
        cleanse.addProperty("transformation_max_batch_size", transformationMaxBatchSize);
        root.add("cleanse_and_transformations", cleanse);

        JsonObject world = new JsonObject();
        world.addProperty("vanilla_chest_books_mode", vanillaChestBooksMode.token());
        world.addProperty("bookshelf_pool_enabled", bookshelfPoolEnabled);
        world.addProperty("bookshelf_min_books", bookshelfMinBooks);
        world.addProperty("bookshelf_max_books", bookshelfMaxBooks);
        world.addProperty("bookshelf_enchanted_prob", bookshelfEnchantedProb);
        world.addProperty("librarian_tome_offers", librarianTomeOffers);
        root.add("world_and_loot", world);

        com.google.gson.JsonArray customized = new com.google.gson.JsonArray();
        for (String key : customizedKeys) {
            customized.add(key);
        }
        root.add("customized_keys", customized);
        return root;
    }

    public static ModRuntimeConfig fromJson(JsonObject root) {
        if (root == null) {
            return DEFAULT;
        }
        Draft d = new Draft();
        JsonObject general = objOrEmpty(root, "general");
        JsonObject shelves = objOrEmpty(root, "bookshelves");
        JsonObject tomes = objOrEmpty(root, "tomes_and_economy");
        JsonObject epiphany = objOrEmpty(root, "treasure_epiphany");
        JsonObject cleanse = objOrEmpty(root, "cleanse_and_transformations");
        JsonObject world = objOrEmpty(root, "world_and_loot");

        // One-time migration for pre-atom configs carrying `classic_1_0_3_mode: true`:
        // expand all 19 classic rule atoms first, then overlay any explicit fields present in JSON.
        boolean legacyClassicFlag = boolOr(
            general,
            "classic_1_0_3_mode",
            boolOr(root, "classic_1_0_3_mode", false)
        );
        boolean hasGranularSchema = shelves.has("shelf_boost_model")
            || tomes.has("cost_table")
            || epiphany.has("epiphany_enabled")
            || cleanse.has("transformation_set");
        if (legacyClassicFlag && !hasGranularSchema) {
            d.applyClassic103Atoms();
            d.customizedKeys.addAll(GRANULAR_RULE_ATOM_KEYS);
        }

        d.overrideDatapacks = boolOr(general, "override_datapacks", d.overrideDatapacks);
        d.enchantedBookStackSize = intOr(
            general,
            "enchanted_book_stack_size",
            intOr(root, "enchanted_book_stack_size", d.enchantedBookStackSize)
        );
        if (general.has("vanilla_table_mode")) {
            d.vanillaTableMode = VanillaTableMode.fromToken(general.get("vanilla_table_mode").getAsString());
        }
        d.anvilFlatRepairCost = boolOr(general, "anvil_flat_repair_cost", d.anvilFlatRepairCost);
        d.anvilBlockSameBookMerge = boolOr(general, "anvil_block_same_book_merge", d.anvilBlockSameBookMerge);
        d.restrictVillagerInfinityBooks = boolOr(general, "restrict_villager_infinity_books", d.restrictVillagerInfinityBooks);
        d.grindstoneDepletedTome = boolOr(general, "grindstone_depleted_tome", d.grindstoneDepletedTome);

        if (shelves.has("shelf_boost_model")) {
            d.shelfBoostModel = ShelfBoostModel.fromToken(shelves.get("shelf_boost_model").getAsString());
        }
        if (shelves.has("default_unlock_filter")) {
            d.defaultUnlockFilter = DefaultUnlockFilter.fromToken(shelves.get("default_unlock_filter").getAsString());
        }
        d.directTierUpgrade = boolOr(shelves, "direct_tier_upgrade", d.directTierUpgrade);
        d.directGatesEnabled = boolOr(shelves, "direct_gates_enabled", d.directGatesEnabled);
        if (shelves.has("blank_max_tier_mode")) {
            d.blankMaxTierMode = BlankMaxTierMode.fromToken(shelves.get("blank_max_tier_mode").getAsString());
        }
        d.maxDirectBookshelves = intOr(shelves, "max_direct_bookshelves", d.maxDirectBookshelves);
        d.horizontalScanRadius = intOr(shelves, "horizontal_scan_radius", d.horizontalScanRadius);
        d.verticalScanRadius = intOr(shelves, "vertical_scan_radius", d.verticalScanRadius);
        d.requireBreathingRoom = boolOr(shelves, "require_breathing_room", d.requireBreathingRoom);
        d.minOpenAdjacentBlocks = intOr(shelves, "min_open_adjacent_blocks", d.minOpenAdjacentBlocks);
        d.requireLineOfSight = boolOr(shelves, "require_line_of_sight", d.requireLineOfSight);

        if (tomes.has("tome_consumption_mode")) {
            d.tomeConsumptionMode = MasterTomeWear.ConsumptionMode.fromToken(
                tomes.get("tome_consumption_mode").getAsString()
            );
        }
        d.maxTomeUses = intOr(tomes, "max_tome_uses", d.maxTomeUses);
        d.bookCopyWearCost = intOr(tomes, "book_copy_wear_cost", d.bookCopyWearCost);
        if (tomes.has("tome_deplete_behavior")) {
            d.tomeDepleteBehavior = MasterTomeWear.DepleteBehavior.fromToken(
                tomes.get("tome_deplete_behavior").getAsString()
            );
        }
        d.tomeRegenEnabled = boolOr(tomes, "tome_regen_enabled", d.tomeRegenEnabled);
        d.tomeRegenBaseSeconds = intOr(tomes, "tome_regen_base_seconds", d.tomeRegenBaseSeconds);
        d.tomeRegenTier1Shelves = intOr(tomes, "tome_regen_tier1_shelves", d.tomeRegenTier1Shelves);
        d.tomeRegenTier1Seconds = intOr(tomes, "tome_regen_tier1_seconds", d.tomeRegenTier1Seconds);
        d.tomeRegenTier2Shelves = intOr(tomes, "tome_regen_tier2_shelves", d.tomeRegenTier2Shelves);
        d.tomeRegenTier2Seconds = intOr(tomes, "tome_regen_tier2_seconds", d.tomeRegenTier2Seconds);
        d.tomeLocksEnabled = boolOr(tomes, "tome_locks", d.tomeLocksEnabled);
        d.lockedShelfBreakable = boolOr(tomes, "locked_shelf_breakable", d.lockedShelfBreakable);
        d.lockedBookExtractionBlocked = boolOr(tomes, "locked_book_extraction_blocked", d.lockedBookExtractionBlocked);
        d.provenanceEnabled = boolOr(tomes, "provenance", d.provenanceEnabled);
        d.ritualQueueEnabled = boolOr(tomes, "ritual_queue", d.ritualQueueEnabled);
        d.earlyUnlockXpLevels = intOr(tomes, "early_unlock_xp_levels", d.earlyUnlockXpLevels);
        d.earlyUnlockGritCount = intOr(tomes, "early_unlock_grit_count", d.earlyUnlockGritCount);
        d.sacrificeRequired = intOr(tomes, "sacrifice_required", d.sacrificeRequired);
        d.tomePageRequiredShelves = intOr(tomes, "tome_page_required_shelves", d.tomePageRequiredShelves);
        d.incrementalCredit = boolOr(tomes, "incremental_credit", d.incrementalCredit);
        if (tomes.has("xp_mode")) {
            d.xpMode = XpSettlementMode.fromToken(tomes.get("xp_mode").getAsString());
        }
        if (tomes.has("cost_table")) {
            d.costTableDataset = CostTableDataset.fromToken(tomes.get("cost_table").getAsString());
        }
        if (tomes.has("material_selection")) {
            d.materialSelectionMode = MaterialSelectionMode.fromToken(tomes.get("material_selection").getAsString());
        }
        if (tomes.has("research_rounding")) {
            d.researchRoundingMode = ResearchRoundingMode.fromToken(tomes.get("research_rounding").getAsString());
        }
        d.libraryDiscountEnabled = boolOr(tomes, "library_discount", d.libraryDiscountEnabled);
        d.maxWoodenDiscountPercent = intOr(tomes, "max_wooden_discount_percent", d.maxWoodenDiscountPercent);
        d.maxTotalXpDiscountPercent = intOr(tomes, "max_total_xp_discount_percent", d.maxTotalXpDiscountPercent);
        d.bookCombineEnabled = boolOr(tomes, "book_combine_enabled", d.bookCombineEnabled);
        d.bookCombineXpMultiplier = doubleOr(tomes, "book_combine_xp_multiplier", d.bookCombineXpMultiplier);
        d.bookResearchEnabled = boolOr(tomes, "book_research_enabled", d.bookResearchEnabled);
        d.bookResearchXpMultiplier = doubleOr(tomes, "book_research_xp_multiplier", d.bookResearchXpMultiplier);

        d.epiphanyEnabled = boolOr(epiphany, "epiphany_enabled", d.epiphanyEnabled);
        d.epiphanyMinBookshelves = intOr(epiphany, "epiphany_min_bookshelves", d.epiphanyMinBookshelves);
        d.epiphanyBaseChance = doubleOr(epiphany, "epiphany_base_chance", d.epiphanyBaseChance);
        d.epiphanyMaxChance = doubleOr(epiphany, "epiphany_max_chance", d.epiphanyMaxChance);
        d.epiphanyMaxSlotsCap = intOr(epiphany, "epiphany_max_slots_cap", d.epiphanyMaxSlotsCap);
        d.epiphanyCycleSeconds = intOr(epiphany, "epiphany_cycle_seconds", d.epiphanyCycleSeconds);
        d.epiphanyFreezeXpLevels = intOr(epiphany, "epiphany_freeze_xp_levels", d.epiphanyFreezeXpLevels);
        d.epiphanyFreezeDurationSeconds = intOr(epiphany, "epiphany_freeze_duration_seconds", d.epiphanyFreezeDurationSeconds);
        d.epiphanyMaxFreezeSeconds = intOr(epiphany, "epiphany_max_freeze_seconds", d.epiphanyMaxFreezeSeconds);
        d.epiphanyRerollXpLevels = intOr(epiphany, "epiphany_reroll_xp_levels", d.epiphanyRerollXpLevels);

        d.disenchantEnabled = boolOr(cleanse, "disenchant_enabled", d.disenchantEnabled);
        d.xpRefundScale = doubleOr(cleanse, "xp_refund_scale", d.xpRefundScale);
        d.externalXpRefundScale = doubleOr(cleanse, "external_xp_refund_scale", d.externalXpRefundScale);
        d.stripXpMin = intOr(cleanse, "strip_xp_min", d.stripXpMin);
        d.stripXpMax = intOr(cleanse, "strip_xp_max", d.stripXpMax);
        d.allowCursePurge = boolOr(cleanse, "allow_curse_purge", d.allowCursePurge);
        d.cursePurgeXpCost = intOr(cleanse, "curse_purge_xp_cost", d.cursePurgeXpCost);
        if (cleanse.has("transformation_set")) {
            d.transformationSetMode = TransformationSetMode.fromToken(cleanse.get("transformation_set").getAsString());
        }
        d.requirePriorDiscovery = boolOr(cleanse, "require_prior_discovery", d.requirePriorDiscovery);
        if (cleanse.has("drained_book_result")) {
            d.drainedBookResult = DrainedBookResult.fromToken(cleanse.get("drained_book_result").getAsString());
        }
        d.tomePageEnabled = boolOr(cleanse, "tome_page_enabled", d.tomePageEnabled);
        d.tier1Bookshelves = intOr(cleanse, "tier1_bookshelves", d.tier1Bookshelves);
        d.tier2Bookshelves = intOr(cleanse, "tier2_bookshelves", d.tier2Bookshelves);
        d.tier3QualifyingEnchantments = intOr(cleanse, "tier3_qualifying_enchantments", d.tier3QualifyingEnchantments);
        d.transformationMaxBatchSize = intOr(cleanse, "transformation_max_batch_size", d.transformationMaxBatchSize);

        if (world.has("vanilla_chest_books_mode")) {
            d.vanillaChestBooksMode = VanillaChestBooksMode.fromToken(
                world.get("vanilla_chest_books_mode").getAsString()
            );
        }
        d.bookshelfPoolEnabled = boolOr(world, "bookshelf_pool_enabled", d.bookshelfPoolEnabled);
        d.bookshelfMinBooks = intOr(world, "bookshelf_min_books", d.bookshelfMinBooks);
        d.bookshelfMaxBooks = intOr(world, "bookshelf_max_books", d.bookshelfMaxBooks);
        d.bookshelfEnchantedProb = doubleOr(world, "bookshelf_enchanted_prob", d.bookshelfEnchantedProb);
        d.librarianTomeOffers = boolOr(world, "librarian_tome_offers", d.librarianTomeOffers);

        if (root.has("customized_keys") && root.get("customized_keys").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("customized_keys")) {
                if (el.isJsonPrimitive()) {
                    String k = el.getAsString();
                    if ("classic_1_0_3_mode".equals(k)) {
                        d.customizedKeys.addAll(GRANULAR_RULE_ATOM_KEYS);
                    } else {
                        d.customizedKeys.add(k);
                    }
                }
            }
        }
        return d.build();
    }

    /** Mutable editor buffer enforcing live cross-field coherence as users drag sliders or click toggles. */
    public static final class Draft {
        public void markCustomized(String key) {
            if (key != null && !key.isBlank()) {
                this.customizedKeys.add(key);
            }
        }

        public boolean overrideDatapacks = false;
        public int enchantedBookStackSize = 64;
        public VanillaTableMode vanillaTableMode = VanillaTableMode.BOOKS;
        public boolean anvilFlatRepairCost = false;
        public boolean anvilBlockSameBookMerge = true;
        public boolean restrictVillagerInfinityBooks = false;
        public boolean grindstoneDepletedTome = true;

        public ShelfBoostModel shelfBoostModel = ShelfBoostModel.MODERN_CURVE;
        public DefaultUnlockFilter defaultUnlockFilter = DefaultUnlockFilter.EXCLUDE_SPECIAL;
        public boolean directTierUpgrade = true;
        public boolean directGatesEnabled = true;
        public BlankMaxTierMode blankMaxTierMode = BlankMaxTierMode.HALF_CEIL;
        public int maxDirectBookshelves = 38;
        public int horizontalScanRadius = 3;
        public int verticalScanRadius = 2;
        public boolean requireBreathingRoom = true;
        public int minOpenAdjacentBlocks = 6;
        public boolean requireLineOfSight = false;

        public MasterTomeWear.ConsumptionMode tomeConsumptionMode = MasterTomeWear.ConsumptionMode.WEAR;
        public int maxTomeUses = MasterTomeWear.DEFAULT_MAX_TOME_USES;
        public int bookCopyWearCost = MasterTomeWear.DEFAULT_BOOK_COPY_WEAR_COST;
        public MasterTomeWear.DepleteBehavior tomeDepleteBehavior = MasterTomeWear.DepleteBehavior.STEP_DOWN;
        public boolean tomeRegenEnabled = true;
        public int tomeRegenBaseSeconds = 7_200;
        public int tomeRegenTier1Shelves = 38;
        public int tomeRegenTier1Seconds = 3_600;
        public int tomeRegenTier2Shelves = 42;
        public int tomeRegenTier2Seconds = 1_200;
        public boolean tomeLocksEnabled = true;
        public boolean lockedShelfBreakable = true;
        public boolean lockedBookExtractionBlocked = true;
        public boolean provenanceEnabled = true;
        public boolean ritualQueueEnabled = true;
        public int earlyUnlockXpLevels = 1;
        public int earlyUnlockGritCount = 1;
        public int sacrificeRequired = 12;
        public int tomePageRequiredShelves = 4;
        public boolean incrementalCredit = true;
        public XpSettlementMode xpMode = XpSettlementMode.DUAL_STORED_DISCOUNT;
        public CostTableDataset costTableDataset = CostTableDataset.MODERN;
        public MaterialSelectionMode materialSelectionMode = MaterialSelectionMode.ALL_OPTIONS_MIDPOINT;
        public ResearchRoundingMode researchRoundingMode = ResearchRoundingMode.ROUND_NEAREST;
        public boolean libraryDiscountEnabled = true;
        public int maxWoodenDiscountPercent = 15;
        public int maxTotalXpDiscountPercent = 40;
        public boolean bookCombineEnabled = true;
        public double bookCombineXpMultiplier = 0.60;
        public boolean bookResearchEnabled = true;
        public double bookResearchXpMultiplier = 1.40;

        public boolean epiphanyEnabled = true;
        public int epiphanyMinBookshelves = 32;
        public double epiphanyBaseChance = 0.28;
        public double epiphanyMaxChance = 0.90;
        public int epiphanyMaxSlotsCap = 4;
        public int epiphanyCycleSeconds = 600;
        public int epiphanyFreezeXpLevels = 1;
        public int epiphanyFreezeDurationSeconds = 1200;
        public int epiphanyMaxFreezeSeconds = 3600;
        public int epiphanyRerollXpLevels = 2;

        public boolean disenchantEnabled = true;
        public double xpRefundScale = 1.00;
        public double externalXpRefundScale = 0.25;
        public int stripXpMin = 3;
        public int stripXpMax = 10;
        public boolean allowCursePurge = true;
        public int cursePurgeXpCost = 12;
        public TransformationSetMode transformationSetMode = TransformationSetMode.FULL_SIX;
        public boolean requirePriorDiscovery = true;
        public DrainedBookResult drainedBookResult = DrainedBookResult.DEPLETED_TOME;
        public boolean tomePageEnabled = true;
        public int tier1Bookshelves = 12;
        public int tier2Bookshelves = 32;
        public int tier3QualifyingEnchantments = 6;
        public int transformationMaxBatchSize = 64;

        public VanillaChestBooksMode vanillaChestBooksMode = VanillaChestBooksMode.KEEP_BOTH;
        public boolean bookshelfPoolEnabled = true;
        public int bookshelfMinBooks = 3;
        public int bookshelfMaxBooks = 5;
        public double bookshelfEnchantedProb = 0.12;
        public boolean librarianTomeOffers = true;

        public final Set<String> customizedKeys = new LinkedHashSet<>();

        public Draft() {}

        public Draft(ModRuntimeConfig src) {
            this.overrideDatapacks = src.overrideDatapacks;
            this.enchantedBookStackSize = src.enchantedBookStackSize;
            this.vanillaTableMode = src.vanillaTableMode;
            this.anvilFlatRepairCost = src.anvilFlatRepairCost;
            this.anvilBlockSameBookMerge = src.anvilBlockSameBookMerge;
            this.restrictVillagerInfinityBooks = src.restrictVillagerInfinityBooks;
            this.grindstoneDepletedTome = src.grindstoneDepletedTome;

            this.shelfBoostModel = src.shelfBoostModel;
            this.defaultUnlockFilter = src.defaultUnlockFilter;
            this.directTierUpgrade = src.directTierUpgrade;
            this.directGatesEnabled = src.directGatesEnabled;
            this.blankMaxTierMode = src.blankMaxTierMode;
            this.maxDirectBookshelves = src.maxDirectBookshelves;
            this.horizontalScanRadius = src.horizontalScanRadius;
            this.verticalScanRadius = src.verticalScanRadius;
            this.requireBreathingRoom = src.requireBreathingRoom;
            this.minOpenAdjacentBlocks = src.minOpenAdjacentBlocks;
            this.requireLineOfSight = src.requireLineOfSight;

            this.tomeConsumptionMode = src.tomeConsumptionMode;
            this.maxTomeUses = src.maxTomeUses;
            this.bookCopyWearCost = src.bookCopyWearCost;
            this.tomeDepleteBehavior = src.tomeDepleteBehavior;
            this.tomeRegenEnabled = src.tomeRegenEnabled;
            this.tomeRegenBaseSeconds = src.tomeRegenBaseSeconds;
            this.tomeRegenTier1Shelves = src.tomeRegenTier1Shelves;
            this.tomeRegenTier1Seconds = src.tomeRegenTier1Seconds;
            this.tomeRegenTier2Shelves = src.tomeRegenTier2Shelves;
            this.tomeRegenTier2Seconds = src.tomeRegenTier2Seconds;
            this.tomeLocksEnabled = src.tomeLocksEnabled;
            this.lockedShelfBreakable = src.lockedShelfBreakable;
            this.lockedBookExtractionBlocked = src.lockedBookExtractionBlocked;
            this.provenanceEnabled = src.provenanceEnabled;
            this.ritualQueueEnabled = src.ritualQueueEnabled;
            this.earlyUnlockXpLevels = src.earlyUnlockXpLevels;
            this.earlyUnlockGritCount = src.earlyUnlockGritCount;
            this.sacrificeRequired = src.sacrificeRequired;
            this.tomePageRequiredShelves = src.tomePageRequiredShelves;
            this.incrementalCredit = src.incrementalCredit;
            this.xpMode = src.xpMode;
            this.costTableDataset = src.costTableDataset;
            this.materialSelectionMode = src.materialSelectionMode;
            this.researchRoundingMode = src.researchRoundingMode;
            this.libraryDiscountEnabled = src.libraryDiscountEnabled;
            this.maxWoodenDiscountPercent = src.maxWoodenDiscountPercent;
            this.maxTotalXpDiscountPercent = src.maxTotalXpDiscountPercent;
            this.bookCombineEnabled = src.bookCombineEnabled;
            this.bookCombineXpMultiplier = src.bookCombineXpMultiplier;
            this.bookResearchEnabled = src.bookResearchEnabled;
            this.bookResearchXpMultiplier = src.bookResearchXpMultiplier;

            this.epiphanyEnabled = src.epiphanyEnabled;
            this.epiphanyMinBookshelves = src.epiphanyMinBookshelves;
            this.epiphanyBaseChance = src.epiphanyBaseChance;
            this.epiphanyMaxChance = src.epiphanyMaxChance;
            this.epiphanyMaxSlotsCap = src.epiphanyMaxSlotsCap;
            this.epiphanyCycleSeconds = src.epiphanyCycleSeconds;
            this.epiphanyFreezeXpLevels = src.epiphanyFreezeXpLevels;
            this.epiphanyFreezeDurationSeconds = src.epiphanyFreezeDurationSeconds;
            this.epiphanyMaxFreezeSeconds = src.epiphanyMaxFreezeSeconds;
            this.epiphanyRerollXpLevels = src.epiphanyRerollXpLevels;

            this.disenchantEnabled = src.disenchantEnabled;
            this.xpRefundScale = src.xpRefundScale;
            this.externalXpRefundScale = src.externalXpRefundScale;
            this.stripXpMin = src.stripXpMin;
            this.stripXpMax = src.stripXpMax;
            this.allowCursePurge = src.allowCursePurge;
            this.cursePurgeXpCost = src.cursePurgeXpCost;
            this.transformationSetMode = src.transformationSetMode;
            this.requirePriorDiscovery = src.requirePriorDiscovery;
            this.drainedBookResult = src.drainedBookResult;
            this.tomePageEnabled = src.tomePageEnabled;
            this.tier1Bookshelves = src.tier1Bookshelves;
            this.tier2Bookshelves = src.tier2Bookshelves;
            this.tier3QualifyingEnchantments = src.tier3QualifyingEnchantments;
            this.transformationMaxBatchSize = src.transformationMaxBatchSize;

            this.vanillaChestBooksMode = src.vanillaChestBooksMode;
            this.bookshelfPoolEnabled = src.bookshelfPoolEnabled;
            this.bookshelfMinBooks = src.bookshelfMinBooks;
            this.bookshelfMaxBooks = src.bookshelfMaxBooks;
            this.bookshelfEnchantedProb = src.bookshelfEnchantedProb;
            this.librarianTomeOffers = src.librarianTomeOffers;
            this.customizedKeys.addAll(src.customizedKeys);
        }

        /** Enforces live inter-option ordering and volume bounds after editing {@code editedKey}. */
        public void normalizeAfterEdit(@Nullable String editedKey) {
            if (editedKey != null && !editedKey.isBlank()) {
                this.customizedKeys.add(editedKey);
            }
            if ("cost_table".equals(editedKey)) {
                // Align default combine/research XP multipliers with the selected table unless
                // the player has explicitly customized those sliders.
                if (!this.customizedKeys.contains("book_combine_xp_multiplier")) {
                    this.bookCombineXpMultiplier = this.costTableDataset == CostTableDataset.CLASSIC ? 1.00 : 0.60;
                }
                if (!this.customizedKeys.contains("book_research_xp_multiplier")) {
                    this.bookResearchXpMultiplier = this.costTableDataset == CostTableDataset.CLASSIC ? 0.50 : 1.40;
                }
            }

            this.horizontalScanRadius = clampInt(this.horizontalScanRadius, 1, BookshelfProgression.MAX_SCAN_HORIZONTAL_RADIUS);
            this.verticalScanRadius = clampInt(this.verticalScanRadius, 0, BookshelfProgression.MAX_SCAN_VERTICAL_RADIUS);
            if ("vertical_scan_radius".equals(editedKey)) {
                while (this.horizontalScanRadius > 1
                    && BookshelfProgression.scanVolumeBlocks(this.horizontalScanRadius, this.verticalScanRadius)
                        > BookshelfProgression.MAX_SCAN_VOLUME_BLOCKS) {
                    this.horizontalScanRadius--;
                }
            }
            while (this.verticalScanRadius > 0
                && BookshelfProgression.scanVolumeBlocks(this.horizontalScanRadius, this.verticalScanRadius)
                    > BookshelfProgression.MAX_SCAN_VOLUME_BLOCKS) {
                this.verticalScanRadius--;
            }

            if ("max_tome_uses".equals(editedKey) && this.bookCopyWearCost > this.maxTomeUses) {
                this.bookCopyWearCost = this.maxTomeUses;
            } else if ("book_copy_wear_cost".equals(editedKey) && this.bookCopyWearCost > this.maxTomeUses) {
                this.maxTomeUses = this.bookCopyWearCost;
            }

            if ("max_wooden_discount_percent".equals(editedKey) && this.maxWoodenDiscountPercent > this.maxTotalXpDiscountPercent) {
                this.maxTotalXpDiscountPercent = this.maxWoodenDiscountPercent;
            } else if ("max_total_xp_discount_percent".equals(editedKey) && this.maxTotalXpDiscountPercent < this.maxWoodenDiscountPercent) {
                this.maxWoodenDiscountPercent = this.maxTotalXpDiscountPercent;
            }

            if ("epiphany_base_chance".equals(editedKey) && this.epiphanyBaseChance > this.epiphanyMaxChance) {
                this.epiphanyMaxChance = this.epiphanyBaseChance;
            } else if ("epiphany_max_chance".equals(editedKey) && this.epiphanyMaxChance < this.epiphanyBaseChance) {
                this.epiphanyBaseChance = this.epiphanyMaxChance;
            }

            if ("epiphany_freeze_duration_seconds".equals(editedKey)
                && this.epiphanyFreezeDurationSeconds > this.epiphanyMaxFreezeSeconds) {
                this.epiphanyMaxFreezeSeconds = this.epiphanyFreezeDurationSeconds;
            } else if ("epiphany_max_freeze_seconds".equals(editedKey)
                && this.epiphanyMaxFreezeSeconds < this.epiphanyFreezeDurationSeconds) {
                this.epiphanyFreezeDurationSeconds = this.epiphanyMaxFreezeSeconds;
            }

            if ("xp_refund_scale".equals(editedKey) && this.externalXpRefundScale > this.xpRefundScale) {
                this.externalXpRefundScale = this.xpRefundScale;
            } else if ("external_xp_refund_scale".equals(editedKey) && this.externalXpRefundScale > this.xpRefundScale) {
                this.xpRefundScale = this.externalXpRefundScale;
            }

            if ("strip_xp_min".equals(editedKey) && this.stripXpMin > this.stripXpMax) {
                this.stripXpMax = this.stripXpMin;
            } else if ("strip_xp_max".equals(editedKey) && this.stripXpMax < this.stripXpMin) {
                this.stripXpMin = this.stripXpMax;
            }

            if ("tier1_bookshelves".equals(editedKey)) {
                if (this.tier2Bookshelves < this.tier1Bookshelves) this.tier2Bookshelves = this.tier1Bookshelves;
                if (this.maxDirectBookshelves < this.tier2Bookshelves) this.maxDirectBookshelves = Math.max(4, this.tier2Bookshelves);
            } else if ("tier2_bookshelves".equals(editedKey)) {
                if (this.tier1Bookshelves > this.tier2Bookshelves) this.tier1Bookshelves = this.tier2Bookshelves;
                if (this.maxDirectBookshelves < this.tier2Bookshelves) this.maxDirectBookshelves = Math.max(4, this.tier2Bookshelves);
            } else if ("max_direct_bookshelves".equals(editedKey)) {
                if (this.tier2Bookshelves > this.maxDirectBookshelves) this.tier2Bookshelves = this.maxDirectBookshelves;
                if (this.tier1Bookshelves > this.tier2Bookshelves) this.tier1Bookshelves = this.tier2Bookshelves;
            }

            if ("bookshelf_min_books".equals(editedKey) && this.bookshelfMinBooks > this.bookshelfMaxBooks) {
                this.bookshelfMaxBooks = this.bookshelfMinBooks;
            } else if ("bookshelf_max_books".equals(editedKey) && this.bookshelfMaxBooks < this.bookshelfMinBooks) {
                this.bookshelfMinBooks = this.bookshelfMaxBooks;
            }

            if ("tome_regen_tier1_shelves".equals(editedKey) && this.tomeRegenTier1Shelves > this.tomeRegenTier2Shelves) {
                this.tomeRegenTier2Shelves = this.tomeRegenTier1Shelves;
            } else if ("tome_regen_tier2_shelves".equals(editedKey) && this.tomeRegenTier2Shelves < this.tomeRegenTier1Shelves) {
                this.tomeRegenTier1Shelves = this.tomeRegenTier2Shelves;
            }

            if ("tome_regen_base_seconds".equals(editedKey)) {
                if (this.tomeRegenTier1Seconds > this.tomeRegenBaseSeconds) this.tomeRegenTier1Seconds = this.tomeRegenBaseSeconds;
                if (this.tomeRegenTier2Seconds > this.tomeRegenTier1Seconds) this.tomeRegenTier2Seconds = this.tomeRegenTier1Seconds;
            } else if ("tome_regen_tier1_seconds".equals(editedKey)) {
                if (this.tomeRegenBaseSeconds < this.tomeRegenTier1Seconds) this.tomeRegenBaseSeconds = this.tomeRegenTier1Seconds;
                if (this.tomeRegenTier2Seconds > this.tomeRegenTier1Seconds) this.tomeRegenTier2Seconds = this.tomeRegenTier1Seconds;
            } else if ("tome_regen_tier2_seconds".equals(editedKey)) {
                if (this.tomeRegenTier1Seconds < this.tomeRegenTier2Seconds) this.tomeRegenTier2Seconds = this.tomeRegenTier2Seconds;
                if (this.tomeRegenBaseSeconds < this.tomeRegenTier1Seconds) this.tomeRegenBaseSeconds = this.tomeRegenTier1Seconds;
            }

            this.bookCombineXpMultiplier = round2(clampDouble(this.bookCombineXpMultiplier, 0.10, 3.00));
            this.bookResearchXpMultiplier = round2(clampDouble(this.bookResearchXpMultiplier, 0.10, 3.00));
            this.epiphanyBaseChance = round2(clampDouble(this.epiphanyBaseChance, 0.0, 1.0));
            this.epiphanyMaxChance = round2(clampDouble(this.epiphanyMaxChance, this.epiphanyBaseChance, 1.0));
            this.xpRefundScale = round2(clampDouble(this.xpRefundScale, 0.0, 1.0));
            this.externalXpRefundScale = round2(clampDouble(this.externalXpRefundScale, 0.0, this.xpRefundScale));
            this.bookshelfEnchantedProb = round2(clampDouble(this.bookshelfEnchantedProb, 0.0, 1.0));
        }

        public void copyFrom(Draft other) {
            if (other == null || other == this) return;
            ModRuntimeConfig snap = other.build();
            this.overrideDatapacks = snap.overrideDatapacks;
            this.enchantedBookStackSize = snap.enchantedBookStackSize;
            this.vanillaTableMode = snap.vanillaTableMode;
            this.anvilFlatRepairCost = snap.anvilFlatRepairCost;
            this.anvilBlockSameBookMerge = snap.anvilBlockSameBookMerge;
            this.restrictVillagerInfinityBooks = snap.restrictVillagerInfinityBooks;
            this.grindstoneDepletedTome = snap.grindstoneDepletedTome;

            this.shelfBoostModel = snap.shelfBoostModel;
            this.defaultUnlockFilter = snap.defaultUnlockFilter;
            this.directTierUpgrade = snap.directTierUpgrade;
            this.directGatesEnabled = snap.directGatesEnabled;
            this.blankMaxTierMode = snap.blankMaxTierMode;
            this.maxDirectBookshelves = snap.maxDirectBookshelves;
            this.horizontalScanRadius = snap.horizontalScanRadius;
            this.verticalScanRadius = snap.verticalScanRadius;
            this.requireBreathingRoom = snap.requireBreathingRoom;
            this.minOpenAdjacentBlocks = snap.minOpenAdjacentBlocks;
            this.requireLineOfSight = snap.requireLineOfSight;

            this.tomeConsumptionMode = snap.tomeConsumptionMode;
            this.maxTomeUses = snap.maxTomeUses;
            this.bookCopyWearCost = snap.bookCopyWearCost;
            this.tomeDepleteBehavior = snap.tomeDepleteBehavior;
            this.tomeRegenEnabled = snap.tomeRegenEnabled;
            this.tomeRegenBaseSeconds = snap.tomeRegenBaseSeconds;
            this.tomeRegenTier1Shelves = snap.tomeRegenTier1Shelves;
            this.tomeRegenTier1Seconds = snap.tomeRegenTier1Seconds;
            this.tomeRegenTier2Shelves = snap.tomeRegenTier2Shelves;
            this.tomeRegenTier2Seconds = snap.tomeRegenTier2Seconds;
            this.tomeLocksEnabled = snap.tomeLocksEnabled;
            this.lockedShelfBreakable = snap.lockedShelfBreakable;
            this.lockedBookExtractionBlocked = snap.lockedBookExtractionBlocked;
            this.provenanceEnabled = snap.provenanceEnabled;
            this.ritualQueueEnabled = snap.ritualQueueEnabled;
            this.earlyUnlockXpLevels = snap.earlyUnlockXpLevels;
            this.earlyUnlockGritCount = snap.earlyUnlockGritCount;
            this.sacrificeRequired = snap.sacrificeRequired;
            this.tomePageRequiredShelves = snap.tomePageRequiredShelves;
            this.incrementalCredit = snap.incrementalCredit;
            this.xpMode = snap.xpMode;
            this.costTableDataset = snap.costTableDataset;
            this.materialSelectionMode = snap.materialSelectionMode;
            this.researchRoundingMode = snap.researchRoundingMode;
            this.libraryDiscountEnabled = snap.libraryDiscountEnabled;
            this.maxWoodenDiscountPercent = snap.maxWoodenDiscountPercent;
            this.maxTotalXpDiscountPercent = snap.maxTotalXpDiscountPercent;
            this.bookCombineEnabled = snap.bookCombineEnabled;
            this.bookCombineXpMultiplier = snap.bookCombineXpMultiplier;
            this.bookResearchEnabled = snap.bookResearchEnabled;
            this.bookResearchXpMultiplier = snap.bookResearchXpMultiplier;

            this.epiphanyEnabled = snap.epiphanyEnabled;
            this.epiphanyMinBookshelves = snap.epiphanyMinBookshelves;
            this.epiphanyBaseChance = snap.epiphanyBaseChance;
            this.epiphanyMaxChance = snap.epiphanyMaxChance;
            this.epiphanyMaxSlotsCap = snap.epiphanyMaxSlotsCap;
            this.epiphanyCycleSeconds = snap.epiphanyCycleSeconds;
            this.epiphanyFreezeXpLevels = snap.epiphanyFreezeXpLevels;
            this.epiphanyFreezeDurationSeconds = snap.epiphanyFreezeDurationSeconds;
            this.epiphanyMaxFreezeSeconds = snap.epiphanyMaxFreezeSeconds;
            this.epiphanyRerollXpLevels = snap.epiphanyRerollXpLevels;

            this.disenchantEnabled = snap.disenchantEnabled;
            this.xpRefundScale = snap.xpRefundScale;
            this.externalXpRefundScale = snap.externalXpRefundScale;
            this.stripXpMin = snap.stripXpMin;
            this.stripXpMax = snap.stripXpMax;
            this.allowCursePurge = snap.allowCursePurge;
            this.cursePurgeXpCost = snap.cursePurgeXpCost;
            this.transformationSetMode = snap.transformationSetMode;
            this.requirePriorDiscovery = snap.requirePriorDiscovery;
            this.drainedBookResult = snap.drainedBookResult;
            this.tomePageEnabled = snap.tomePageEnabled;
            this.tier1Bookshelves = snap.tier1Bookshelves;
            this.tier2Bookshelves = snap.tier2Bookshelves;
            this.tier3QualifyingEnchantments = snap.tier3QualifyingEnchantments;
            this.transformationMaxBatchSize = snap.transformationMaxBatchSize;

            this.vanillaChestBooksMode = snap.vanillaChestBooksMode;
            this.bookshelfPoolEnabled = snap.bookshelfPoolEnabled;
            this.bookshelfMinBooks = snap.bookshelfMinBooks;
            this.bookshelfMaxBooks = snap.bookshelfMaxBooks;
            this.bookshelfEnchantedProb = snap.bookshelfEnchantedProb;
            this.librarianTomeOffers = snap.librarianTomeOffers;
            this.customizedKeys.clear();
            this.customizedKeys.addAll(snap.customizedKeys);
        }

        /** Sets all 19 core mechanism rule atoms to their classic {@code 1.0.3} values. */
        public void applyClassic103Atoms() {
            shelfBoostModel = ShelfBoostModel.CLASSIC_FLOOR_THIRD;
            defaultUnlockFilter = DefaultUnlockFilter.CLASSIC_UNFILTERED;
            directTierUpgrade = false;
            incrementalCredit = false;
            xpMode = XpSettlementMode.CLASSIC_FULL_POINTS;
            costTableDataset = CostTableDataset.CLASSIC;
            materialSelectionMode = MaterialSelectionMode.PLAYER_SALTED_SINGLE;
            researchRoundingMode = ResearchRoundingMode.FLOOR_DOWN;
            libraryDiscountEnabled = false;
            tomeConsumptionMode = MasterTomeWear.ConsumptionMode.KEEP;
            tomeLocksEnabled = false;
            provenanceEnabled = false;
            ritualQueueEnabled = false;
            epiphanyEnabled = false;
            disenchantEnabled = false;
            transformationSetMode = TransformationSetMode.CLASSIC_TWO;
            drainedBookResult = DrainedBookResult.PLAIN_BOOK;
            tomePageEnabled = false;
            librarianTomeOffers = false;
        }

        /** Sets all 19 core mechanism rule atoms to their modern default values. */
        public void applyModern110Atoms() {
            shelfBoostModel = ShelfBoostModel.MODERN_CURVE;
            defaultUnlockFilter = DefaultUnlockFilter.EXCLUDE_SPECIAL;
            directTierUpgrade = true;
            incrementalCredit = true;
            xpMode = XpSettlementMode.DUAL_STORED_DISCOUNT;
            costTableDataset = CostTableDataset.MODERN;
            materialSelectionMode = MaterialSelectionMode.ALL_OPTIONS_MIDPOINT;
            researchRoundingMode = ResearchRoundingMode.ROUND_NEAREST;
            libraryDiscountEnabled = true;
            tomeConsumptionMode = MasterTomeWear.ConsumptionMode.WEAR;
            tomeLocksEnabled = true;
            provenanceEnabled = true;
            ritualQueueEnabled = true;
            epiphanyEnabled = true;
            disenchantEnabled = true;
            transformationSetMode = TransformationSetMode.FULL_SIX;
            drainedBookResult = DrainedBookResult.DEPLETED_TOME;
            tomePageEnabled = true;
            librarianTomeOffers = true;
        }

        /**
         * Writes the complete {@code 1.0.3} preset profile into this draft (all 19 core atoms plus
         * {@code 1.0.3} scan geometry, multiplier, and loot defaults). Every field remains freely
         * editable afterwards.
         */
        public void applyClassic103Overrides() {
            applyClassic103Atoms();
            enchantedBookStackSize = 64;
            vanillaTableMode = VanillaTableMode.BOOKS;
            anvilFlatRepairCost = false;
            anvilBlockSameBookMerge = true;
            restrictVillagerInfinityBooks = false;
            grindstoneDepletedTome = false;
            directGatesEnabled = false;
            blankMaxTierMode = BlankMaxTierMode.FULL_CURVE;
            horizontalScanRadius = 2;
            verticalScanRadius = 2;
            requireBreathingRoom = false;
            requireLineOfSight = false;
            tomeRegenEnabled = false;
            lockedShelfBreakable = true;
            lockedBookExtractionBlocked = false;
            maxWoodenDiscountPercent = 0;
            maxTotalXpDiscountPercent = 0;
            bookCombineEnabled = true;
            bookCombineXpMultiplier = 1.00;
            bookResearchEnabled = true;
            bookResearchXpMultiplier = 0.50;
            epiphanyBaseChance = 0.0;
            epiphanyMaxChance = 0.0;
            allowCursePurge = false;
            requirePriorDiscovery = false;
            transformationMaxBatchSize = 1;
            bookshelfEnchantedProb = 0.03;
            vanillaChestBooksMode = VanillaChestBooksMode.KEEP_BOTH;
            bookshelfPoolEnabled = true;
        }

        public ModRuntimeConfig build() {
            return new ModRuntimeConfig(
                overrideDatapacks,
                enchantedBookStackSize,
                vanillaTableMode,
                anvilFlatRepairCost,
                anvilBlockSameBookMerge,
                shelfBoostModel,
                defaultUnlockFilter,
                directTierUpgrade,
                directGatesEnabled,
                blankMaxTierMode,
                maxDirectBookshelves,
                horizontalScanRadius,
                verticalScanRadius,
                requireBreathingRoom,
                minOpenAdjacentBlocks,
                requireLineOfSight,
                tomeConsumptionMode,
                maxTomeUses,
                bookCopyWearCost,
                tomeDepleteBehavior,
                tomeRegenEnabled,
                tomeRegenBaseSeconds,
                tomeRegenTier1Shelves,
                tomeRegenTier1Seconds,
                tomeRegenTier2Shelves,
                tomeRegenTier2Seconds,
                tomeLocksEnabled,
                lockedShelfBreakable,
                lockedBookExtractionBlocked,
                provenanceEnabled,
                ritualQueueEnabled,
                earlyUnlockXpLevels,
                earlyUnlockGritCount,
                sacrificeRequired,
                tomePageRequiredShelves,
                incrementalCredit,
                xpMode,
                costTableDataset,
                materialSelectionMode,
                researchRoundingMode,
                libraryDiscountEnabled,
                maxWoodenDiscountPercent,
                maxTotalXpDiscountPercent,
                bookCombineEnabled,
                bookCombineXpMultiplier,
                bookResearchEnabled,
                bookResearchXpMultiplier,
                epiphanyEnabled,
                epiphanyMinBookshelves,
                epiphanyBaseChance,
                epiphanyMaxChance,
                epiphanyMaxSlotsCap,
                epiphanyCycleSeconds,
                epiphanyFreezeXpLevels,
                epiphanyFreezeDurationSeconds,
                epiphanyMaxFreezeSeconds,
                epiphanyRerollXpLevels,
                disenchantEnabled,
                xpRefundScale,
                externalXpRefundScale,
                stripXpMin,
                stripXpMax,
                allowCursePurge,
                cursePurgeXpCost,
                transformationSetMode,
                requirePriorDiscovery,
                drainedBookResult,
                tomePageEnabled,
                tier1Bookshelves,
                tier2Bookshelves,
                tier3QualifyingEnchantments,
                transformationMaxBatchSize,
                vanillaChestBooksMode,
                bookshelfPoolEnabled,
                bookshelfMinBooks,
                bookshelfMaxBooks,
                bookshelfEnchantedProb,
                librarianTomeOffers,
                restrictVillagerInfinityBooks,
                grindstoneDepletedTome,
                customizedKeys
            );
        }
    }

    private static JsonObject objOrEmpty(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
            ? parent.getAsJsonObject(key)
            : new JsonObject();
    }

    private static boolean boolOr(JsonObject obj, String key, boolean fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsBoolean() : fallback;
    }

    private static int intOr(JsonObject obj, String key, int fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsInt() : fallback;
    }

    private static double doubleOr(JsonObject obj, String key, double fallback) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsDouble() : fallback;
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clampDouble(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.max(min, Math.min(max, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
