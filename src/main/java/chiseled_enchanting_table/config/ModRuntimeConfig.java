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

        public void applyTo(Draft draft) { RuntimeConfigPresets.apply(this, draft); }

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

        public JsonObject toJson() { return RuntimePolicyJson.toJson(this); }

        public String toJsonString() {
            return GSON.toJson(toJson());
        }

        public static PackPolicy fromJson(JsonObject root, Set<String> externalDatapackKeys) { return RuntimePolicyJson.fromJson(root, externalDatapackKeys); }

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
        ConfigFileStore.writeAtomically(path, GSON.toJson(cfg.toJson()) + "\n");
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

    public JsonObject toJson() { return RuntimeConfigJson.toJson(this); }

    public static ModRuntimeConfig fromJson(JsonObject root) { return RuntimeConfigJson.fromJson(root); }

    /** Mutable editor buffer enforcing live cross-field coherence as users drag sliders or click toggles. */
    public static final class Draft extends ConfigDraft {
        public Draft() { super(); }
        public Draft(ModRuntimeConfig source) { super(source); }
        public void copyFrom(Draft source) { super.copyFrom(source); }
    }









    static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    static double clampDouble(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.max(min, Math.min(max, v));
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
