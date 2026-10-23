package chiseled_enchanting_table.mechanics.resonance;

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
 *       for {@code maxLevel = 3}, {@code [0, 38]} for {@code maxLevel = 2}, strictly increasing
 *       for {@code 1 <= maxLevel <= anchor + 1}, and smoothly distributed across all {@code 39}
 *       discrete shelf counts {@code 0..anchor} when {@code maxLevel > anchor + 1}).</li>
 *   <li><b>Depleted Tome 3-Gradient Restoration</b> ({@link DepletedTomeTier}): linear batch
 *       conversion with configurable batch sizes and deduplicated distinct high-tier Master Tome
 *       qualification ({@code M <= 1 -> 1}, {@code M == 2 -> 2}, {@code M >= 3 -> M - 1}).</li>
 * </ol>
 */
public final class BookshelfProgression {
    public static final int DEFAULT_SCAN_HORIZONTAL_RADIUS = 3;
    public static final int DEFAULT_SCAN_VERTICAL_RADIUS = 2;
    public static final int MAX_SCAN_HORIZONTAL_RADIUS = 8;
    public static final int MAX_SCAN_VERTICAL_RADIUS = 6;
    public static final int MAX_SCAN_VOLUME_BLOCKS = 2048;
    public static final int DEFAULT_MAX_DIRECT_BOOKSHELVES = 38;
    public static final double DIRECT_TIER_CURVE_EXPONENT = 1.25;

    public static final int DEFAULT_TIER1_BOOKSHELVES = 12;
    public static final int DEFAULT_TIER2_BOOKSHELVES = 32;
    public static final double DEFAULT_TIER2_MATERIAL_MULTIPLIER = 0.50;
    public static final int DEFAULT_TIER3_QUALIFYING_ENCHANTMENTS = 6;
    public static final int DEFAULT_BASE_BATCH_SIZE = 8;
    public static final int DEFAULT_TIER3_BATCH_SIZE = 64;
    public static final int DEFAULT_MIN_OPEN_ADJACENT_BLOCKS = 6;

    private static final TagKey<Enchantment> CURSE_TAG =
        TagKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("curse"));

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
            true,
            DEFAULT_MAX_DIRECT_BOOKSHELVES,
            DIRECT_TIER_CURVE_EXPONENT,
            BlankMaxTierMode.HALF_CEIL,
            DEFAULT_SCAN_HORIZONTAL_RADIUS,
            DEFAULT_SCAN_VERTICAL_RADIUS,
            Map.of(),
            DEFAULT_TIER1_BOOKSHELVES,
            DEFAULT_TIER2_BOOKSHELVES,
            DEFAULT_TIER2_MATERIAL_MULTIPLIER,
            DEFAULT_TIER3_QUALIFYING_ENCHANTMENTS,
            DEFAULT_BASE_BATCH_SIZE,
            DEFAULT_TIER3_BATCH_SIZE,
            VanillaTableMode.BOOKS,
            true,
            DEFAULT_MIN_OPEN_ADJACENT_BLOCKS,
            false
        );

        public Config {
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
                        Identifier id = Identifier.tryParse(entry.getKey());
                        if (id == null) continue;
                        if (entry.getValue().isJsonArray()) {
                            List<Integer> ladder = new ArrayList<>();
                            for (JsonElement elem : entry.getValue().getAsJsonArray()) {
                                ladder.add(clampInt(elem.getAsInt(), 0, 256));
                            }
                            if (!ladder.isEmpty()) {
                                parsedOverrides.put(id, List.copyOf(ladder));
                            }
                        } else if (entry.getValue().isJsonObject()) {
                            int maxKey = 0;
                            Map<Integer, Integer> sparse = new HashMap<>();
                            for (Map.Entry<String, JsonElement> lvlEntry : entry.getValue().getAsJsonObject().entrySet()) {
                                try {
                                    int lvl = Integer.parseInt(lvlEntry.getKey());
                                    if (lvl >= 1) {
                                        maxKey = Math.max(maxKey, lvl);
                                        sparse.put(lvl, clampInt(lvlEntry.getValue().getAsInt(), 0, 256));
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                            if (maxKey >= 1) {
                                List<Integer> ladder = new ArrayList<>(maxKey);
                                for (int l = 1; l <= maxKey; l++) {
                                    ladder.add(sparse.getOrDefault(l, requiredBookshelvesForDirectTier(l, maxKey, maxDirect, curveExp)));
                                }
                                parsedOverrides.put(id, List.copyOf(ladder));
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
        return requiredBookshelvesForDirectTier(targetLevel, maxLevel, cfg.maxDirectBookshelves(), cfg.gamma());
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
            } else {
                break;
            }
        }
        return unlocked;
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

    public static XpRequirement applyDepletedTomeXpTier(XpRequirement baseXp, DepletedTomeTier tier) {
        return applyDepletedTomeXpTierWithBatch(baseXp, tier, 1);
    }

    public static XpRequirement applyDepletedTomeXpTierWithBatch(
        XpRequirement baseXp,
        DepletedTomeTier tier,
        int batchCount
    ) {
        return switch (tier) {
            case BASELINE -> {
                int safeBatch = Math.max(1, batchCount);
                if (safeBatch <= 1 || (baseXp.requiredLevel() <= 0 && baseXp.deductedLevels() <= 0)) {
                    yield baseXp;
                }
                int scaledDed = baseXp.deductedLevels() * safeBatch;
                int scaledReq = Math.max(scaledDed, baseXp.requiredLevel() * safeBatch);
                yield new XpRequirement(
                    baseXp.rawRequiredLevel() * safeBatch,
                    scaledReq,
                    scaledDed,
                    baseXp.discountPercent(),
                    baseXp.vanillaTierMode()
                );
            }
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
            return List.of();
        }
        int safeBatch = Math.max(1, batchCount);
        double mult = (tier == DepletedTomeTier.TIER_2_DISCOUNT_MAT)
            ? (config == null ? DEFAULT_TIER2_MATERIAL_MULTIPLIER : config.tier2MaterialMultiplier())
            : 1.0;

        List<List<ItemStack>> out = new ArrayList<>(baseBundles.size());
        for (List<ItemStack> bundle : baseBundles) {
            if (bundle.isEmpty()) continue;
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
}
