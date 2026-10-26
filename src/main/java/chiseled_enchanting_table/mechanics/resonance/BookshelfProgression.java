package chiseled_enchanting_table.mechanics.resonance;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion;
import chiseled_enchanting_table.mechanics.enchanting.XpCostConversion.XpRequirement;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/**
 * Data-driven progression formulas connecting regular bookshelves and Chiseled Bookshelves to:
 * <ol>
 *   <li><b>Track 1 — Blank Wooden-Shelf Inscription</b> ({@link #maxBlankWoodenTier}): blank gear
 *       and blank books unlock up to {@code ceil(maxLevel / 2)} along the single unified
 *       {@code gamma = 1.25} curve without replacing Master Tomes.</li>
 *   <li><b>Track 2 — Direct Category Upgrade</b> ({@link #maxUnlockedDirectTier}): once an item or
 *       Enchanted Book already carries {@code existingLevel >= 1}, regular bookshelves unlock step
 *       upgrades all the way to {@code MAX} at {@code 38} shelves ({@code [0, 7, 16, 27, 38]} for
 *       {@code maxLevel = 5}, {@code [0, 10, 23, 38]} for {@code maxLevel = 4}, {@code [0, 16, 38]}
 *       for {@code maxLevel = 3}, {@code [0, 22]} for {@code maxLevel = 2} via
 *       {@code two_level_max_bookshelves = 22}, strictly increasing for
 *       {@code 1 <= maxLevel <= anchor + 1}, and smoothly distributed across all {@code 39}
 *       discrete shelf counts {@code 0..anchor} when {@code maxLevel > anchor + 1}).</li>
 *   <li><b>Depleted Tome 3-Gradient Restoration</b> ({@link DepletedTomeTier}): linear batch
 *       conversion with configurable batch sizes and deduplicated distinct high-tier Master Tome
 *       qualification ({@code M <= 1 -> 1}, {@code M == 2 -> 2}, {@code M >= 3 -> M - 1}).</li>
 * </ol>
 */
public final class BookshelfProgression {
    // Generated from shelf_gates.json (single source of truth); see BundledDefaults.
    public static final int DEFAULT_SCAN_HORIZONTAL_RADIUS = chiseled_enchanting_table.BundledDefaults.SCAN_HORIZONTAL_RADIUS;
    public static final int DEFAULT_SCAN_VERTICAL_RADIUS = chiseled_enchanting_table.BundledDefaults.SCAN_VERTICAL_RADIUS;
    public static final int MAX_SCAN_HORIZONTAL_RADIUS = 8;
    public static final int MAX_SCAN_VERTICAL_RADIUS = 6;
    public static final int MAX_SCAN_VOLUME_BLOCKS = 4096;
    public static final int DEFAULT_MAX_DIRECT_BOOKSHELVES = chiseled_enchanting_table.BundledDefaults.MAX_DIRECT_BOOKSHELVES;
    public static final int DEFAULT_TWO_LEVEL_MAX_BOOKSHELVES = chiseled_enchanting_table.BundledDefaults.TWO_LEVEL_MAX_BOOKSHELVES;
    public static final double DIRECT_TIER_CURVE_EXPONENT = chiseled_enchanting_table.BundledDefaults.DIRECT_TIER_GAMMA;

    public static final int DEFAULT_TIER1_BOOKSHELVES = chiseled_enchanting_table.BundledDefaults.DEPLETED_TIER1_BOOKSHELVES;
    public static final int DEFAULT_TIER2_BOOKSHELVES = chiseled_enchanting_table.BundledDefaults.DEPLETED_TIER2_BOOKSHELVES;
    public static final double DEFAULT_TIER2_MATERIAL_MULTIPLIER = chiseled_enchanting_table.BundledDefaults.DEPLETED_TIER2_MATERIAL_MULTIPLIER;
    public static final int DEFAULT_TIER3_QUALIFYING_ENCHANTMENTS = chiseled_enchanting_table.BundledDefaults.DEPLETED_TIER3_QUALIFYING_ENCHANTMENTS;
    public static final int DEFAULT_BASE_BATCH_SIZE = chiseled_enchanting_table.BundledDefaults.DEPLETED_BASE_BATCH_SIZE;
    public static final int DEFAULT_TIER3_BATCH_SIZE = chiseled_enchanting_table.BundledDefaults.DEPLETED_TIER3_BATCH_SIZE;
    public static final int DEFAULT_MIN_OPEN_ADJACENT_BLOCKS = chiseled_enchanting_table.BundledDefaults.MIN_OPEN_ADJACENT_BLOCKS;

    private static final TagKey<Enchantment> CURSE_TAG = chiseled_enchanting_table.registry.CetTags.Enchantments.CURSE;

    public static int scanVolumeBlocks(int horizontalRadius, int verticalRadius) {
        int h = Math.max(1, horizontalRadius);
        int v = Math.max(0, verticalRadius);
        return (2 * h + 1) * (2 * h + 1) * (2 * v + 1);
    }

    public enum VanillaTableMode {
        BOOKS,
        ALIGN_CAPS,
        ALL,
        NONE;

        public String token() {
            return switch (this) {
                case BOOKS -> "books";
                case ALIGN_CAPS -> "align_caps";
                case ALL -> "all";
                case NONE -> "none";
            };
        }

        public static VanillaTableMode fromToken(String raw) {
            if (raw == null || raw.isBlank()) {
                return BOOKS;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "align_caps", "align", "cap" -> ALIGN_CAPS;
                case "all", "disable_all" -> ALL;
                case "none", "vanilla", "off" -> NONE;
                default -> BOOKS;
            };
        }
    }

    public enum BlankMaxTierMode {
        HALF_CEIL,
        LEVEL_ONE_ONLY,
        FULL_CURVE;

        public String token() {
            return switch (this) {
                case HALF_CEIL -> "half_ceil";
                case LEVEL_ONE_ONLY -> "level_one_only";
                case FULL_CURVE -> "full_curve";
            };
        }

        public static BlankMaxTierMode fromToken(String raw) {
            if (raw == null || raw.isBlank()) {
                return HALF_CEIL;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "level_one_only", "one" -> LEVEL_ONE_ONLY;
                case "full_curve", "full" -> FULL_CURVE;
                default -> HALF_CEIL;
            };
        }
    }

    public enum DepletedTomeTier {
        BASELINE,
        TIER_1_NO_XP,
        TIER_2_DISCOUNT_MAT,
        TIER_3_FREE
    }

    public record Config(
        boolean directGatesEnabled,
        int maxDirectBookshelves,
        int twoLevelMaxBookshelves,
        double gamma,
        BlankMaxTierMode blankMaxTierMode,
        int horizontalScanRadius,
        int verticalScanRadius,
        Map<Identifier, List<Integer>> overrides,
        int tier1Bookshelves,
        int tier2Bookshelves,
        double tier2MaterialMultiplier,
        int tier3QualifyingEnchantments,
        int baseBatchSize,
        int tier3BatchSize,
        VanillaTableMode vanillaTableMode,
        boolean requireBreathingRoom,
        int minOpenAdjacentBlocks,
        boolean requireLineOfSight
    ) {
        public static final Config DEFAULT = new Config(
            chiseled_enchanting_table.BundledDefaults.DIRECT_GATES_ENABLED,
            DEFAULT_MAX_DIRECT_BOOKSHELVES,
            DEFAULT_TWO_LEVEL_MAX_BOOKSHELVES,
            DIRECT_TIER_CURVE_EXPONENT,
            BlankMaxTierMode.fromToken(chiseled_enchanting_table.BundledDefaults.BLANK_MAX_TIER_MODE),
            DEFAULT_SCAN_HORIZONTAL_RADIUS,
            DEFAULT_SCAN_VERTICAL_RADIUS,
            Map.of(),
            DEFAULT_TIER1_BOOKSHELVES,
            DEFAULT_TIER2_BOOKSHELVES,
            DEFAULT_TIER2_MATERIAL_MULTIPLIER,
            DEFAULT_TIER3_QUALIFYING_ENCHANTMENTS,
            DEFAULT_BASE_BATCH_SIZE,
            DEFAULT_TIER3_BATCH_SIZE,
            VanillaTableMode.fromToken(chiseled_enchanting_table.BundledDefaults.VANILLA_TABLE_MODE),
            chiseled_enchanting_table.BundledDefaults.REQUIRE_BREATHING_ROOM,
            DEFAULT_MIN_OPEN_ADJACENT_BLOCKS,
            chiseled_enchanting_table.BundledDefaults.REQUIRE_LINE_OF_SIGHT
        );

        public Config {
            maxDirectBookshelves = clampInt(maxDirectBookshelves, 1, 256);
            twoLevelMaxBookshelves = clampInt(twoLevelMaxBookshelves, 1, maxDirectBookshelves);
            horizontalScanRadius = clampInt(horizontalScanRadius, 1, MAX_SCAN_HORIZONTAL_RADIUS);
            verticalScanRadius = clampInt(verticalScanRadius, 0, MAX_SCAN_VERTICAL_RADIUS);
            vanillaTableMode = vanillaTableMode != null ? vanillaTableMode : VanillaTableMode.BOOKS;
            minOpenAdjacentBlocks = clampInt(minOpenAdjacentBlocks, 1, 17);
            while (verticalScanRadius > 0
                && scanVolumeBlocks(horizontalScanRadius, verticalScanRadius) > MAX_SCAN_VOLUME_BLOCKS) {
                verticalScanRadius--;
            }
            while (horizontalScanRadius > 1
                && scanVolumeBlocks(horizontalScanRadius, verticalScanRadius) > MAX_SCAN_VOLUME_BLOCKS) {
                horizontalScanRadius--;
            }
        }

        public Config(
            boolean directGatesEnabled,
            int maxDirectBookshelves,
            double gamma,
            BlankMaxTierMode blankMaxTierMode,
            int horizontalScanRadius,
            int verticalScanRadius,
            Map<Identifier, List<Integer>> overrides,
            int tier1Bookshelves,
            int tier2Bookshelves,
            double tier2MaterialMultiplier,
            int tier3QualifyingEnchantments,
            int baseBatchSize,
            int tier3BatchSize,
            VanillaTableMode vanillaTableMode,
            boolean requireBreathingRoom,
            int minOpenAdjacentBlocks,
            boolean requireLineOfSight
        ) {
            this(
                directGatesEnabled,
                maxDirectBookshelves,
                Math.min(Math.max(1, maxDirectBookshelves), DEFAULT_TWO_LEVEL_MAX_BOOKSHELVES),
                gamma,
                blankMaxTierMode,
                horizontalScanRadius,
                verticalScanRadius,
                overrides,
                tier1Bookshelves,
                tier2Bookshelves,
                tier2MaterialMultiplier,
                tier3QualifyingEnchantments,
                baseBatchSize,
                tier3BatchSize,
                vanillaTableMode,
                requireBreathingRoom,
                minOpenAdjacentBlocks,
                requireLineOfSight
            );
        }

        public Config(
            boolean directGatesEnabled,
            int maxDirectBookshelves,
            double gamma,
            BlankMaxTierMode blankMaxTierMode,
            int horizontalScanRadius,
            int verticalScanRadius,
            Map<Identifier, List<Integer>> overrides,
            int tier1Bookshelves,
            int tier2Bookshelves,
            double tier2MaterialMultiplier,
            int tier3QualifyingEnchantments,
            int baseBatchSize,
            int tier3BatchSize
        ) {
            this(
                directGatesEnabled,
                maxDirectBookshelves,
                gamma,
                blankMaxTierMode,
                horizontalScanRadius,
                verticalScanRadius,
                overrides,
                tier1Bookshelves,
                tier2Bookshelves,
                tier2MaterialMultiplier,
                tier3QualifyingEnchantments,
                baseBatchSize,
                tier3BatchSize,
                VanillaTableMode.BOOKS,
                true,
                DEFAULT_MIN_OPEN_ADJACENT_BLOCKS,
                false
            );
        }

        public Config(
            boolean directGatesEnabled,
            int maxDirectBookshelves,
            double gamma,
            Map<Identifier, List<Integer>> overrides,
            int tier1Bookshelves,
            int tier2Bookshelves,
            double tier2MaterialMultiplier,
            int tier3QualifyingEnchantments
        ) {
            this(
                directGatesEnabled,
                maxDirectBookshelves,
                gamma,
                BlankMaxTierMode.HALF_CEIL,
                DEFAULT_SCAN_HORIZONTAL_RADIUS,
                DEFAULT_SCAN_VERTICAL_RADIUS,
                overrides,
                tier1Bookshelves,
                tier2Bookshelves,
                tier2MaterialMultiplier,
                tier3QualifyingEnchantments,
                DEFAULT_BASE_BATCH_SIZE,
                DEFAULT_TIER3_BATCH_SIZE
            );
        }

        public static int scanVolumeBlocks(int rx, int ry) {
            int sx = 2 * Math.max(1, rx) + 1;
            int sy = 2 * Math.max(0, ry) + 1;
            return Math.max(0, sx * sx * sy - 1);
        }

        public static Config fromJson(JsonObject root) {
            if (root == null) {
                return DEFAULT;
            }
            int hRadius = DEFAULT_SCAN_HORIZONTAL_RADIUS;
            int vRadius = DEFAULT_SCAN_VERTICAL_RADIUS;
            boolean breathingRoom = true;
            int minOpen = DEFAULT_MIN_OPEN_ADJACENT_BLOCKS;
            boolean lineOfSight = false;
            VanillaTableMode vanillaMode = VanillaTableMode.BOOKS;
            if (root.has("vanilla_table_mode") && root.get("vanilla_table_mode").isJsonPrimitive()) {
                vanillaMode = VanillaTableMode.fromToken(root.get("vanilla_table_mode").getAsString());
            }
            if (root.has("scan_volume") && root.get("scan_volume").isJsonObject()) {
                JsonObject sv = root.getAsJsonObject("scan_volume");
                if (sv.has("horizontal_radius")) {
                    hRadius = clampInt(sv.get("horizontal_radius").getAsInt(), 1, MAX_SCAN_HORIZONTAL_RADIUS);
                }
                if (sv.has("vertical_radius")) {
                    vRadius = clampInt(sv.get("vertical_radius").getAsInt(), 0, MAX_SCAN_VERTICAL_RADIUS);
                }
                if (sv.has("require_breathing_room")) {
                    breathingRoom = sv.get("require_breathing_room").getAsBoolean();
                }
                if (sv.has("min_open_adjacent_blocks")) {
                    minOpen = clampInt(sv.get("min_open_adjacent_blocks").getAsInt(), 1, 17);
                }
                if (sv.has("require_line_of_sight")) {
                    lineOfSight = sv.get("require_line_of_sight").getAsBoolean();
                }
            }

            boolean enabled = true;
            int maxDirect = DEFAULT_MAX_DIRECT_BOOKSHELVES;
            int twoLevelMax = DEFAULT_TWO_LEVEL_MAX_BOOKSHELVES;
            double curveExp = DIRECT_TIER_CURVE_EXPONENT;
            BlankMaxTierMode blankMode = BlankMaxTierMode.HALF_CEIL;
            Map<Identifier, List<Integer>> parsedOverrides = new HashMap<>();

            if (root.has("direct_upgrade_gates") && root.get("direct_upgrade_gates").isJsonObject()) {
                JsonObject directObj = root.getAsJsonObject("direct_upgrade_gates");
                if (directObj.has("enabled")) {
                    enabled = directObj.get("enabled").getAsBoolean();
                }
                if (directObj.has("max_bookshelves")) {
                    maxDirect = clampInt(directObj.get("max_bookshelves").getAsInt(), 1, 256);
                }
                twoLevelMax = Math.min(maxDirect, DEFAULT_TWO_LEVEL_MAX_BOOKSHELVES);
                if (directObj.has("two_level_max_bookshelves")) {
                    twoLevelMax = clampInt(directObj.get("two_level_max_bookshelves").getAsInt(), 1, maxDirect);
                }
                if (directObj.has("gamma")) {
                    double rawExp = directObj.get("gamma").getAsDouble();
                    curveExp = Double.isFinite(rawExp) ? Math.max(0.5, Math.min(3.0, rawExp)) : DIRECT_TIER_CURVE_EXPONENT;
                } else if (directObj.has("curve_exponent")) {
                    double rawExp = directObj.get("curve_exponent").getAsDouble();
                    curveExp = Double.isFinite(rawExp) ? Math.max(0.5, Math.min(3.0, rawExp)) : DIRECT_TIER_CURVE_EXPONENT;
                }
                if (directObj.has("blank_max_tier_mode")) {
                    blankMode = BlankMaxTierMode.fromToken(directObj.get("blank_max_tier_mode").getAsString());
                }
                if (directObj.has("overrides") && directObj.get("overrides").isJsonObject()) {
                    JsonObject ovRoot = directObj.getAsJsonObject("overrides");
                    for (Map.Entry<String, JsonElement> entry : ovRoot.entrySet()) {
                        if (entry.getKey().startsWith("_")) continue;
                        Identifier id = Identifier.tryParse(entry.getKey());
                        if (id == null) {
                            ChiseledEnchantingTable.LOGGER.warn(
                                "shelf_gates.json direct_upgrade_gates.overrides has malformed enchantment ID '{}'; ignoring",
                                entry.getKey()
                            );
                            continue;
                        }
                        List<Integer> rawLadder = null;
                        if (entry.getValue().isJsonArray()) {
                            rawLadder = new ArrayList<>();
                            boolean validNumbers = true;
                            for (JsonElement elem : entry.getValue().getAsJsonArray()) {
                                if (elem == null || !elem.isJsonPrimitive() || !elem.getAsJsonPrimitive().isNumber()) {
                                    validNumbers = false;
                                    break;
                                }
                                rawLadder.add(elem.getAsInt());
                            }
                            if (!validNumbers) {
                                ChiseledEnchantingTable.LOGGER.warn(
                                    "shelf_gates.json direct_upgrade_gates.overrides '{}' contains non-numeric entry; falling back to default curve",
                                    id
                                );
                                rawLadder = null;
                            }
                        } else if (entry.getValue().isJsonObject()) {
                            int maxKey = 0;
                            Map<Integer, Integer> sparse = new HashMap<>();
                            boolean validSparse = true;
                            for (Map.Entry<String, JsonElement> lvlEntry : entry.getValue().getAsJsonObject().entrySet()) {
                                if (lvlEntry.getKey().startsWith("_")) continue;
                                try {
                                    int lvl = Integer.parseInt(lvlEntry.getKey());
                                    if (lvl < 1
                                        || lvlEntry.getValue() == null
                                        || !lvlEntry.getValue().isJsonPrimitive()
                                        || !lvlEntry.getValue().getAsJsonPrimitive().isNumber()) {
                                        validSparse = false;
                                        break;
                                    }
                                    maxKey = Math.max(maxKey, lvl);
                                    sparse.put(lvl, lvlEntry.getValue().getAsInt());
                                } catch (Exception ex) {
                                    validSparse = false;
                                    break;
                                }
                            }
                            if (!validSparse) {
                                ChiseledEnchantingTable.LOGGER.warn(
                                    "shelf_gates.json direct_upgrade_gates.overrides '{}' contains invalid sparse level mapping; falling back to default curve",
                                    id
                                );
                            } else if (maxKey >= 1) {
                                rawLadder = new ArrayList<>(maxKey);
                                for (int l = 1; l <= maxKey; l++) {
                                    rawLadder.add(sparse.getOrDefault(l, requiredBookshelvesForDirectTier(l, maxKey, maxDirect, curveExp)));
                                }
                            }
                        }
                        if (rawLadder != null && !rawLadder.isEmpty()) {
                            List<Integer> validated = validateOverrideLadder(id, rawLadder, maxDirect);
                            if (validated != null) {
                                parsedOverrides.put(id, validated);
                            }
                        }
                    }
                }
            }

            int t1 = DEFAULT_TIER1_BOOKSHELVES;
            int t2 = DEFAULT_TIER2_BOOKSHELVES;
            double t2Mult = DEFAULT_TIER2_MATERIAL_MULTIPLIER;
            int t3Ench = DEFAULT_TIER3_QUALIFYING_ENCHANTMENTS;
            int baseBatch = DEFAULT_BASE_BATCH_SIZE;
            int tier3Batch = DEFAULT_TIER3_BATCH_SIZE;
            if (root.has("depleted_tome_gradients") && root.get("depleted_tome_gradients").isJsonObject()) {
                JsonObject depObj = root.getAsJsonObject("depleted_tome_gradients");
                if (depObj.has("tier1_bookshelves")) {
                    t1 = clampInt(depObj.get("tier1_bookshelves").getAsInt(), 1, 256);
                }
                if (depObj.has("tier2_bookshelves")) {
                    t2 = clampInt(depObj.get("tier2_bookshelves").getAsInt(), t1, 256);
                }
                if (depObj.has("tier2_material_multiplier")) {
                    t2Mult = Math.max(0.05, Math.min(1.0, depObj.get("tier2_material_multiplier").getAsDouble()));
                }
                if (depObj.has("tier3_qualifying_enchantments")) {
                    t3Ench = clampInt(depObj.get("tier3_qualifying_enchantments").getAsInt(), 1, 256);
                }
                if (depObj.has("base_batch_size")) {
                    baseBatch = clampInt(depObj.get("base_batch_size").getAsInt(), 1, 64);
                }
                if (depObj.has("tier3_batch_size")) {
                    tier3Batch = clampInt(depObj.get("tier3_batch_size").getAsInt(), baseBatch, 64);
                }
            }

            return new Config(
                enabled,
                maxDirect,
                twoLevelMax,
                curveExp,
                blankMode,
                hRadius,
                vRadius,
                Map.copyOf(parsedOverrides),
                t1,
                t2,
                t2Mult,
                t3Ench,
                baseBatch,
                tier3Batch,
                vanillaMode,
                breathingRoom,
                minOpen,
                lineOfSight
            );
        }

        private static int clampInt(int v, int lo, int hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }

    private BookshelfProgression() {}

    public static int requiredBookshelvesForDirectTier(int targetLevel, int maxLevel, int maxBookshelvesCap) {
        return requiredBookshelvesForDirectTier(targetLevel, maxLevel, maxBookshelvesCap, DIRECT_TIER_CURVE_EXPONENT);
    }

    /**
     * Maps {@code targetLevel in [1..maxLevel]} onto discrete shelf gates {@code [0..cap]}:
     * <ul>
     *   <li>When {@code maxLevel <= cap + 1} (all standard &amp; modded enchantments up to {@code 39} tiers
     *       at default {@code cap = 38}), enough discrete shelf states exist so every tier has a strictly
     *       increasing integer requirement.</li>
     *   <li>When {@code maxLevel > cap + 1} (e.g. {@code maxLevel = 40..255}), only {@code cap + 1}
     *       shelf states exist; intermediate levels map smoothly along {@code round(cap * progress^exp)}
     *       clamped to {@code [1..cap-1]}, distributing tiers evenly across all {@code 39} shelf states
     *       instead of collapsing lower tiers onto {@code 1} shelf.</li>
     * </ul>
     */
    public static int requiredBookshelvesForDirectTier(
        int targetLevel,
        int maxLevel,
        int maxBookshelvesCap,
        double curveExponent
    ) {
        int safeMax = Math.max(1, maxLevel);
        int safeTarget = Math.max(1, Math.min(safeMax, targetLevel));
        int cap = Math.max(1, maxBookshelvesCap);
        if (safeMax <= 1 || safeTarget <= 1) {
            return 0;
        }
        if (safeTarget >= safeMax) {
            return cap;
        }
        double exp = Double.isFinite(curveExponent) ? Math.max(0.5, curveExponent) : DIRECT_TIER_CURVE_EXPONENT;
        double progress = (double) (safeTarget - 1) / (double) (safeMax - 1);
        int raw = (int) Math.floor(cap * Math.pow(progress, exp) + 0.5);
        if (safeMax > cap + 1) {
            return Math.max(1, Math.min(cap - 1, raw));
        }
        int minAllowed = Math.min(cap - 1, safeTarget - 1);
        int maxAllowed = Math.max(1, cap - (safeMax - safeTarget));
        return Math.max(Math.min(minAllowed, maxAllowed), Math.min(raw, maxAllowed));
    }

    public static int requiredBookshelvesForDirectTier(
        Config config,
        Identifier enchantmentId,
        int targetLevel,
        int maxLevel
    ) {
        Config cfg = config == null ? Config.DEFAULT : config;
        if (!cfg.directGatesEnabled()) {
            return 0;
        }
        if (enchantmentId != null) {
            List<Integer> ladder = cfg.overrides().get(enchantmentId);
            if (ladder != null && targetLevel >= 1 && targetLevel <= ladder.size()) {
                return Math.max(0, ladder.get(targetLevel - 1));
            }
        }
        int effectiveCap = Math.max(1, maxLevel) == 2
            ? Math.min(cfg.maxDirectBookshelves(), cfg.twoLevelMaxBookshelves())
            : cfg.maxDirectBookshelves();
        return requiredBookshelvesForDirectTier(targetLevel, maxLevel, effectiveCap, cfg.gamma());
    }

    public static int maxUnlockedDirectTier(
        Config config,
        Identifier enchantmentId,
        int maxLevel,
        int regularBookshelves
    ) {
        Config cfg = config == null ? Config.DEFAULT : config;
        int clampedMax = Math.max(1, maxLevel);
        if (!cfg.directGatesEnabled()) {
            return clampedMax;
        }
        int shelves = Math.max(0, regularBookshelves);
        int unlocked = 1;
        for (int lvl = 2; lvl <= clampedMax; lvl++) {
            int req = requiredBookshelvesForDirectTier(cfg, enchantmentId, lvl, clampedMax);
            if (shelves >= req) {
                unlocked = lvl;
            }
        }
        return unlocked;
    }

    public static List<Integer> validateOverrideLadder(Identifier id, List<Integer> rawLadder, int maxDirect) {
        int prev = -1;
        for (int i = 0; i < rawLadder.size(); i++) {
            int req = rawLadder.get(i);
            if (req < 0 || req > maxDirect || req < prev) {
                ChiseledEnchantingTable.LOGGER.warn(
                    "shelf_gates.json direct_upgrade_gates.overrides '{}' must be monotonically non-decreasing within [0, {}] (got {}); falling back to default curve",
                    id,
                    maxDirect,
                    rawLadder
                );
                return null;
            }
            prev = req;
        }
        return List.copyOf(rawLadder);
    }

    public static int maxBlankWoodenTier(
        Config config,
        Identifier enchantmentId,
        int maxLevel,
        int regularBookshelves
    ) {
        Config cfg = config == null ? Config.DEFAULT : config;
        int clampedMax = Math.max(1, maxLevel);
        if (!cfg.directGatesEnabled()) {
            return clampedMax;
        }
        int curveUnlocked = maxUnlockedDirectTier(cfg, enchantmentId, clampedMax, regularBookshelves);
        return switch (cfg.blankMaxTierMode()) {
            case LEVEL_ONE_ONLY -> 1;
            case FULL_CURVE -> curveUnlocked;
            case HALF_CEIL -> Math.min((clampedMax + 1) / 2, curveUnlocked);
        };
    }

    public static int effectiveUnlockedTierCap(
        Config config,
        Identifier enchantmentId,
        int maxLevel,
        int regularBookshelves,
        int storedMasterLevel
    ) {
        int clampedMax = Math.max(1, maxLevel);
        int shelfUnlocked = maxUnlockedDirectTier(config, enchantmentId, clampedMax, regularBookshelves);
        return Math.min(clampedMax, Math.max(Math.max(0, storedMasterLevel), shelfUnlocked));
    }

    public static int minQualifyingHighTierLevel(int maxLevel) {
        if (maxLevel <= 1) return 1;
        if (maxLevel == 2) return 2;
        return maxLevel - 1;
    }

    public static int countQualifyingStoredMasterEnchantments(
        Level level,
        Map<Identifier, Integer> storedMasterLevels
    ) {
        if (storedMasterLevels == null || storedMasterLevels.isEmpty()) {
            return 0;
        }
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int qualifyingCount = 0;
        for (Map.Entry<Identifier, Integer> entry : storedMasterLevels.entrySet()) {
            int lvl = entry.getValue();
            if (lvl <= 0) continue;
            try {
                Holder.Reference<Enchantment> holder = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, entry.getKey()));
                if (holder.is(CURSE_TAG)) continue;
                int maxLvl = Math.max(1, holder.value().getMaxLevel());
                if (lvl >= minQualifyingHighTierLevel(maxLvl)) {
                    qualifyingCount++;
                }
            } catch (Exception ignored) {
            }
        }
        return qualifyingCount;
    }

    public static DepletedTomeTier resolveDepletedTomeTier(
        Config config,
        int regularBookshelves,
        int qualifyingChiseledEnchantments
    ) {
        Config cfg = config == null ? Config.DEFAULT : config;
        int shelves = Math.max(0, regularBookshelves);
        int qualifyingEnchants = Math.max(0, qualifyingChiseledEnchantments);
        if (shelves >= cfg.tier2Bookshelves() && qualifyingEnchants >= cfg.tier3QualifyingEnchantments()) {
            return DepletedTomeTier.TIER_3_FREE;
        }
        if (shelves >= cfg.tier2Bookshelves()) {
            return DepletedTomeTier.TIER_2_DISCOUNT_MAT;
        }
        if (shelves >= cfg.tier1Bookshelves()) {
            return DepletedTomeTier.TIER_1_NO_XP;
        }
        return DepletedTomeTier.BASELINE;
    }


    public static XpRequirement applyDepletedTomeXpTierWithBatch(
        XpRequirement baseXp,
        DepletedTomeTier tier,
        int batchCount
    ) {
        return switch (tier) {
            case BASELINE -> XpCostConversion.scaleTransformationXpForBatch(baseXp, batchCount);
            case TIER_1_NO_XP, TIER_2_DISCOUNT_MAT, TIER_3_FREE -> XpRequirement.FREE;
        };
    }

    public static List<List<ItemStack>> applyDepletedTomeMaterialTier(
        Config config,
        List<List<ItemStack>> baseBundles,
        DepletedTomeTier tier
    ) {
        return applyDepletedTomeMaterialTierWithBatch(config, baseBundles, tier, 1);
    }

    public static List<List<ItemStack>> applyDepletedTomeMaterialTierWithBatch(
        Config config,
        List<List<ItemStack>> baseBundles,
        DepletedTomeTier tier,
        int batchCount
    ) {
        if (baseBundles == null || baseBundles.isEmpty()) {
            return List.of();
        }
        if (tier == DepletedTomeTier.TIER_3_FREE) {
            // One explicitly-empty bundle = legally material-free (audit H-02). Returning a
            // completely empty option list here used to be indistinguishable from missing
            // cost data, which the menu layer now treats as fail-closed.
            return List.of(List.of());
        }
        int safeBatch = Math.max(1, batchCount);
        double mult = (tier == DepletedTomeTier.TIER_2_DISCOUNT_MAT)
            ? (config == null ? DEFAULT_TIER2_MATERIAL_MULTIPLIER : config.tier2MaterialMultiplier())
            : 1.0;

        List<List<ItemStack>> out = new ArrayList<>(baseBundles.size());
        for (List<ItemStack> bundle : baseBundles) {
            if (bundle.isEmpty()) {
                out.add(List.of());
                continue;
            }
            List<ItemStack> scaled = new ArrayList<>(bundle.size());
            for (ItemStack stack : bundle) {
                ItemStack copy = stack.copy();
                int unitCount = tier == DepletedTomeTier.TIER_2_DISCOUNT_MAT
                    ? Math.max(1, (int) Math.floor(stack.getCount() * mult))
                    : Math.max(1, stack.getCount());
                copy.setCount(unitCount * safeBatch);
                scaled.add(copy);
            }
            out.add(List.copyOf(scaled));
        }
        return List.copyOf(out);
    }

    public static List<List<ItemStack>> scaleBundlesWithBatch(
        List<List<ItemStack>> unitBundles,
        int batchCount
    ) {
        if (unitBundles == null || unitBundles.isEmpty()) {
            return List.of();
        }
        int safeBatch = Math.max(1, batchCount);
        if (safeBatch <= 1) {
            return unitBundles;
        }
        List<List<ItemStack>> out = new ArrayList<>(unitBundles.size());
        for (List<ItemStack> bundle : unitBundles) {
            if (bundle.isEmpty()) {
                // Explicitly-empty (material-free) bundles survive batch scaling (audit H-02).
                out.add(List.of());
                continue;
            }
            List<ItemStack> scaled = new ArrayList<>(bundle.size());
            for (ItemStack stack : bundle) {
                ItemStack copy = stack.copy();
                copy.setCount(Math.max(1, stack.getCount()) * safeBatch);
                scaled.add(copy);
            }
            out.add(List.copyOf(scaled));
        }
        return List.copyOf(out);
    }

    /**
     * Pure breathing-room shelf multiplier ({@code D4}) callable both in-game and in headless tests.
     */
    public static int effectiveShelvesAfterBreathingRoom(
        int rawShelves,
        boolean ceilingBlocked,
        int openAdjacentBlocks,
        Config cfg
    ) {
        if (rawShelves <= 0) {
            return 0;
        }
        var c = cfg != null ? cfg : Config.DEFAULT;
        if (!c.requireBreathingRoom()) {
            return rawShelves;
        }
        if (ceilingBlocked) {
            return 0;
        }
        int minOpen = Math.max(1, c.minOpenAdjacentBlocks());
        if (openAdjacentBlocks >= minOpen) {
            return rawShelves;
        }
        if (openAdjacentBlocks <= 0) {
            return 0;
        }
        return (int) Math.floor((double) rawShelves * (double) openAdjacentBlocks / (double) minOpen);
    }
    // Audit C-01: this configuration owns its wire layout. EnchantCostTablePayload delegates
    // here so field order lives next to the record definition instead of as anonymous
    // positional locals in the payload; the byte order itself is unchanged (no revision bump).
    public static void writeWire(net.minecraft.network.RegistryFriendlyByteBuf buf, Config cfg) {
        buf.writeBoolean(cfg.directGatesEnabled());
        buf.writeVarInt(cfg.maxDirectBookshelves());
        buf.writeVarInt(cfg.twoLevelMaxBookshelves());
        buf.writeDouble(cfg.gamma());
        buf.writeUtf(cfg.blankMaxTierMode().token());
        buf.writeVarInt(cfg.horizontalScanRadius());
        buf.writeVarInt(cfg.verticalScanRadius());
        PayloadLimits.writeBoundedCount(buf, cfg.overrides().size(), PayloadLimits.MAX_COLLECTION_ENTRIES);
        for (var ov : cfg.overrides().entrySet()) {
            buf.writeIdentifier(ov.getKey());
            PayloadLimits.writeBoundedCount(buf, ov.getValue().size(), PayloadLimits.MAX_COLLECTION_ENTRIES);
            for (int v : ov.getValue()) {
                buf.writeVarInt(Math.max(0, v));
            }
    }
    buf.writeVarInt(cfg.tier1Bookshelves());
    buf.writeVarInt(cfg.tier2Bookshelves());
    buf.writeDouble(cfg.tier2MaterialMultiplier());
    buf.writeVarInt(cfg.tier3QualifyingEnchantments());
    buf.writeVarInt(cfg.baseBatchSize());
    buf.writeVarInt(cfg.tier3BatchSize());
    buf.writeUtf(cfg.vanillaTableMode().token());
    buf.writeBoolean(cfg.requireBreathingRoom());
    buf.writeVarInt(cfg.minOpenAdjacentBlocks());
    buf.writeBoolean(cfg.requireLineOfSight());
    }

    public static Config readWire(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        boolean sgEnabled = buf.readBoolean();
        int sgMax = buf.readVarInt();
        int sgTwoLevelMax = buf.readVarInt();
        double sgGamma = buf.readDouble();
        var sgBlankMode = BlankMaxTierMode.fromToken(buf.readUtf());
        int sgHScan = buf.readVarInt();
        int sgVScan = buf.readVarInt();
        int ovCount = chiseled_enchanting_table.menu.PayloadLimits.readCount(buf);
        java.util.Map<Identifier, List<Integer>> sgOverrides = new java.util.HashMap<>();
        for (int i = 0; i < ovCount; i++) {
            Identifier ovId = buf.readIdentifier();
            int len = chiseled_enchanting_table.menu.PayloadLimits.readCount(buf);
            List<Integer> ladder = new ArrayList<>(len);
            for (int j = 0; j < len; j++) {
                ladder.add(Math.max(0, buf.readVarInt()));
            }
            if (!ladder.isEmpty()) {
                sgOverrides.put(ovId, List.copyOf(ladder));
            }
    }
    int sgT1 = buf.readVarInt();
    int sgT2 = buf.readVarInt();
    double sgMult = buf.readDouble();
    int sgT3 = buf.readVarInt();
    int sgBaseBatch = buf.readVarInt();
    int sgTier3Batch = buf.readVarInt();
    var sgVanillaMode = VanillaTableMode.fromToken(buf.readUtf());
    boolean sgBreathing = buf.readBoolean();
    int sgMinOpen = buf.readVarInt();
    boolean sgLos = buf.readBoolean();
    return new Config(
        sgEnabled, sgMax, sgTwoLevelMax, sgGamma, sgBlankMode, sgHScan, sgVScan,
        sgOverrides, sgT1, sgT2, sgMult, sgT3, sgBaseBatch, sgTier3Batch,
        sgVanillaMode, sgBreathing, sgMinOpen, sgLos
    );
    }
}
