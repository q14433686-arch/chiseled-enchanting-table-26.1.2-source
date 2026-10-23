package chiseled_enchanting_table.mechanics.ritual;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.block.ChiseledEnchantingTableBlock;
import chiseled_enchanting_table.mechanics.resonance.BookshelfProgression;
import chiseled_enchanting_table.mechanics.resonance.MasterTomeWear;
import chiseled_enchanting_table.registry.BlockRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Pure deterministic mechanics &amp; world helpers for:
 * <ol>
 *   <li><b>Deep-Resonance Inscription Ritual Waiting &amp; Bounded Persistent Queue Accumulation</b>:
 *       active if and only if {@code nearbyBookshelves >= median(tier1, tier2, maxDirect)}
 *       OR {@code totalMasterTomeEnchantments >= 10}; single step bounded by {@code <= 5 min}
 *       ({@code 6,000 ticks}) with bounded {@code long} accumulation across multiple queued
 *       enchantments.</li>
 *   <li><b>Dual Skip Options</b> (enabled only when at least one Master Tome exists in scan range):
 *       <ul>
 *         <li><i>Sacrifice XP Skip</i>: consumes {@code 1..15} player XP levels scaled smoothly
 *             from remaining wait time, with {@code 0%} degradation risk.</li>
 *         <li><i>Force Rush Skip (No XP Sacrifice)</i>: live remaining-time probability up to
 *             {@code 50%} normally or {@code 70%} when enchanting from an {@code Infinity}-bearing
 *             Master Tome; on backfire, degrades the lowest-priority Master Tome (lowest relative
 *             level, lowest enchantment count, lowest rarity first) into {@code depleted_tome}
 *             ({@code 褪魔残卷}) and awards {@code haste_makes_waste} (「心急吃不了热豆腐」).</li>
 *       </ul>
 *   </li>
 *   <li><b>Overloaded {@code Infinity} Master Tome Transcription Slip («抄错»)</b>:
 *       when any Master Tome in nearby Chiseled Bookshelves carries {@code minecraft:infinity}
 *       AND {@code > 3} other enchantments ({@code totalEnchants >= 5}), transcription slip
 *       probability scales across single-tome enchantment count AND detected shelf count, capped
 *       strictly at {@code <= 20%}, downgrading the applied enchantment to a lower tier.</li>
 *   <li><b>Rarity &amp; Universality Weighted Treasure Epiphany Selection</b>:
 *       weights Treasure Epiphany candidates by inverse universality ({@code supportedItems.size()})
 *       and enchantment rarity weight.</li>
 * </ol>
 */
public final class InscriptionRitualMechanics {
    public static final int TICKS_PER_GAME_DAY = 24_000;
    public static final int SECONDS_PER_GAME_DAY = 1_200;

    /** Maximum wait ticks for ANY single enchantment step: 5 real minutes = 300s = 6,000 ticks. */
    public static final int MAX_SINGLE_WAIT_TICKS = 20 * 300;
    /** Minimum surrounding Master Tome enchantments that activate ritual waiting below the shelf threshold. */
    public static final int MIN_TOME_ENCHANTS_FOR_WAIT = 10;
    /** Maximum direct XP levels sacrificed when skipping an active ritual via XP sacrifice. */
    public static final int MAX_SACRIFICE_XP_LEVELS = 15;
    /** Hard cap on non-sacrifice rush degradation chance normally (50%) vs with Infinity tome (70%). */
    public static final double MAX_RUSH_DEGRADE_NORMAL = 0.50;
    public static final double MAX_RUSH_DEGRADE_INFINITY = 0.70;
    /** Hard cap on overloaded Infinity Master Tome merged slip («抄错 / 抄串行») chance (18%). */
    public static final double MAX_TRANSCRIPTION_SLIP_CHANCE = 0.18;

    public static final TagKey<Enchantment> EPIPHANY_EXCLUSIVE_TAG =
        TagKey.create(Registries.ENCHANTMENT, ChiseledEnchantingTable.identifier("epiphany_exclusive"));
    public static final TagKey<Enchantment> CROSSLINE_EXCLUDED_TAG =
        TagKey.create(Registries.ENCHANTMENT, ChiseledEnchantingTable.identifier("crossline_excluded"));

    /**
     * Data-driven qualification load, probability curve, 3-branch outcome split ({@code 70% / 25% / 5%}),
     * and {@code 50%} pollution refund rules loaded from {@code library_resonance.json -> crossline_slip}.
     */
    public record CrosslineSlipConfig(
        double minLoadThreshold,
        double perLoadSlope,
        double maxSlipChance,
        int shelfTier1Count,
        double shelfTier1Bonus,
        int shelfTier2Count,
        double shelfTier2Bonus,
        int shelfTier3Count,
        double shelfTier3Bonus,
        double treasureScore,
        double singleTreasureMinLoad,
        int highRarityMaxWeight,
        double highRarityScore,
        int midRarityMaxWeight,
        double midRarityScore,
        int broadApplicabilityMinItems,
        double broadApplicabilityScore,
        double relativeLevelMinRatio,
        double relativeLevelScore,
        int unbreakingHighMinLevel,
        double unbreakingHighScore,
        double tierDropShare,
        double junkCrosslineShare,
        double curseCrosslineShare,
        double pollutionRefundRatio
    ) {
        public static final CrosslineSlipConfig DEFAULT = new CrosslineSlipConfig(
            3.0,
            0.035,
            MAX_TRANSCRIPTION_SLIP_CHANCE,
            32, 0.01,
            38, 0.02,
            42, 0.03,
            1.0,
            3.0,
            2, 1.0,
            5, 0.5,
            12, 0.5,
            0.60, 0.5,
            3, 1.0,
            0.70,
            0.25,
            0.05,
            0.50
        );

        public CrosslineSlipConfig {
            minLoadThreshold = clampFinite(minLoadThreshold, 0.5, 64.0, 3.0);
            perLoadSlope = clampFinite(perLoadSlope, 0.0, 1.0, 0.035);
            maxSlipChance = clampFinite(maxSlipChance, 0.0, 1.0, MAX_TRANSCRIPTION_SLIP_CHANCE);
            shelfTier1Count = Mth.clamp(shelfTier1Count, 1, 256);
            shelfTier1Bonus = clampFinite(shelfTier1Bonus, 0.0, 1.0, 0.01);
            shelfTier2Count = Mth.clamp(shelfTier2Count, shelfTier1Count, 256);
            shelfTier2Bonus = clampFinite(shelfTier2Bonus, 0.0, 1.0, 0.02);
            shelfTier3Count = Mth.clamp(shelfTier3Count, shelfTier2Count, 256);
            shelfTier3Bonus = clampFinite(shelfTier3Bonus, 0.0, 1.0, 0.03);
            treasureScore = clampFinite(treasureScore, 0.0, 16.0, 1.0);
            singleTreasureMinLoad = clampFinite(singleTreasureMinLoad, 0.0, 64.0, 3.0);
            highRarityMaxWeight = Mth.clamp(highRarityMaxWeight, 1, 32);
            highRarityScore = clampFinite(highRarityScore, 0.0, 16.0, 1.0);
            midRarityMaxWeight = Mth.clamp(midRarityMaxWeight, highRarityMaxWeight, 64);
            midRarityScore = clampFinite(midRarityScore, 0.0, 16.0, 0.5);
            broadApplicabilityMinItems = Mth.clamp(broadApplicabilityMinItems, 1, 512);
            broadApplicabilityScore = clampFinite(broadApplicabilityScore, 0.0, 16.0, 0.5);
            relativeLevelMinRatio = clampFinite(relativeLevelMinRatio, 0.05, 1.0, 0.60);
            relativeLevelScore = clampFinite(relativeLevelScore, 0.0, 16.0, 0.5);
            unbreakingHighMinLevel = Mth.clamp(unbreakingHighMinLevel, 1, 255);
            unbreakingHighScore = clampFinite(unbreakingHighScore, 0.0, 16.0, 1.0);
            tierDropShare = clampFinite(tierDropShare, 0.0, 100.0, 0.70);
            junkCrosslineShare = clampFinite(junkCrosslineShare, 0.0, 100.0, 0.25);
            curseCrosslineShare = clampFinite(curseCrosslineShare, 0.0, 100.0, 0.05);
            pollutionRefundRatio = clampFinite(pollutionRefundRatio, 0.0, 1.0, 0.50);
        }

        private static double clampFinite(double val, double min, double max, double fallback) {
            return Double.isFinite(val) ? Mth.clamp(val, min, max) : fallback;
        }

        public static CrosslineSlipConfig fromJson(@Nullable com.google.gson.JsonObject root, double fallbackSlipCap) {
            double safeCap = Double.isFinite(fallbackSlipCap)
                ? Mth.clamp(fallbackSlipCap, 0.0, 1.0)
                : MAX_TRANSCRIPTION_SLIP_CHANCE;
            if (root == null || !root.has("crossline_slip") || !root.get("crossline_slip").isJsonObject()) {
                return new CrosslineSlipConfig(
                    DEFAULT.minLoadThreshold,
                    DEFAULT.perLoadSlope,
                    safeCap,
                    DEFAULT.shelfTier1Count, DEFAULT.shelfTier1Bonus,
                    DEFAULT.shelfTier2Count, DEFAULT.shelfTier2Bonus,
                    DEFAULT.shelfTier3Count, DEFAULT.shelfTier3Bonus,
                    DEFAULT.treasureScore,
                    DEFAULT.singleTreasureMinLoad,
                    DEFAULT.highRarityMaxWeight, DEFAULT.highRarityScore,
                    DEFAULT.midRarityMaxWeight, DEFAULT.midRarityScore,
                    DEFAULT.broadApplicabilityMinItems, DEFAULT.broadApplicabilityScore,
                    DEFAULT.relativeLevelMinRatio, DEFAULT.relativeLevelScore,
                    DEFAULT.unbreakingHighMinLevel, DEFAULT.unbreakingHighScore,
                    DEFAULT.tierDropShare,
                    DEFAULT.junkCrosslineShare,
                    DEFAULT.curseCrosslineShare,
                    DEFAULT.pollutionRefundRatio
                );
            }
            com.google.gson.JsonObject c = root.getAsJsonObject("crossline_slip");
            return new CrosslineSlipConfig(
                Config.doubleOr(c, "min_load_threshold", DEFAULT.minLoadThreshold),
                Config.doubleOr(c, "per_load_slope", DEFAULT.perLoadSlope),
                Config.doubleOr(c, "max_slip_chance", safeCap),
                Config.intOr(c, "shelf_tier1_count", DEFAULT.shelfTier1Count),
                Config.doubleOr(c, "shelf_tier1_bonus", DEFAULT.shelfTier1Bonus),
                Config.intOr(c, "shelf_tier2_count", DEFAULT.shelfTier2Count),
                Config.doubleOr(c, "shelf_tier2_bonus", DEFAULT.shelfTier2Bonus),
                Config.intOr(c, "shelf_tier3_count", DEFAULT.shelfTier3Count),
                Config.doubleOr(c, "shelf_tier3_bonus", DEFAULT.shelfTier3Bonus),
                Config.doubleOr(c, "treasure_score", DEFAULT.treasureScore),
                Config.doubleOr(c, "single_treasure_min_load", DEFAULT.singleTreasureMinLoad),
                Config.intOr(c, "high_rarity_max_weight", DEFAULT.highRarityMaxWeight),
                Config.doubleOr(c, "high_rarity_score", DEFAULT.highRarityScore),
                Config.intOr(c, "mid_rarity_max_weight", DEFAULT.midRarityMaxWeight),
                Config.doubleOr(c, "mid_rarity_score", DEFAULT.midRarityScore),
                Config.intOr(c, "broad_applicability_min_items", DEFAULT.broadApplicabilityMinItems),
                Config.doubleOr(c, "broad_applicability_score", DEFAULT.broadApplicabilityScore),
                Config.doubleOr(c, "relative_level_min_ratio", DEFAULT.relativeLevelMinRatio),
                Config.doubleOr(c, "relative_level_score", DEFAULT.relativeLevelScore),
                Config.intOr(c, "unbreaking_high_min_level", DEFAULT.unbreakingHighMinLevel),
                Config.doubleOr(c, "unbreaking_high_score", DEFAULT.unbreakingHighScore),
                Config.doubleOr(c, "tier_drop_share", DEFAULT.tierDropShare),
                Config.doubleOr(c, "junk_crossline_share", DEFAULT.junkCrosslineShare),
                Config.doubleOr(c, "curse_crossline_share", DEFAULT.curseCrosslineShare),
                Config.doubleOr(c, "pollution_refund_ratio", DEFAULT.pollutionRefundRatio)
            );
        }
    }

    /**
     * Data-driven ritual balance rules loaded from {@code library_resonance.json -> ritual / crossline_slip}
     * and synchronized to clients via {@code EnchantCostTablePayload}.
     */
    public record Config(
        int maxSingleWaitSeconds,
        int minTomeEnchantsForWait,
        int maxSacrificeXpLevels,
        double maxRushDegradeNormal,
        double maxRushDegradeInfinity,
        double maxTranscriptionSlipChance,
        CrosslineSlipConfig crosslineSlip
    ) {
        public static final Config DEFAULT = new Config(
            MAX_SINGLE_WAIT_TICKS / 20,
            MIN_TOME_ENCHANTS_FOR_WAIT,
            MAX_SACRIFICE_XP_LEVELS,
            MAX_RUSH_DEGRADE_NORMAL,
            MAX_RUSH_DEGRADE_INFINITY,
            MAX_TRANSCRIPTION_SLIP_CHANCE,
            CrosslineSlipConfig.DEFAULT
        );

        public Config(
            int maxSingleWaitSeconds,
            int minTomeEnchantsForWait,
            int maxSacrificeXpLevels,
            double maxRushDegradeNormal,
            double maxRushDegradeInfinity,
            double maxTranscriptionSlipChance
        ) {
            this(
                maxSingleWaitSeconds,
                minTomeEnchantsForWait,
                maxSacrificeXpLevels,
                maxRushDegradeNormal,
                maxRushDegradeInfinity,
                maxTranscriptionSlipChance,
                CrosslineSlipConfig.fromJson(null, maxTranscriptionSlipChance)
            );
        }

        public Config {
            maxSingleWaitSeconds = Mth.clamp(maxSingleWaitSeconds, 5, 3_600);
            minTomeEnchantsForWait = Mth.clamp(minTomeEnchantsForWait, 1, 256);
            maxSacrificeXpLevels = Mth.clamp(maxSacrificeXpLevels, 1, 100);
            maxRushDegradeNormal = Double.isFinite(maxRushDegradeNormal) ? Mth.clamp(maxRushDegradeNormal, 0.0, 1.0) : MAX_RUSH_DEGRADE_NORMAL;
            maxRushDegradeInfinity = Double.isFinite(maxRushDegradeInfinity)
                ? Mth.clamp(maxRushDegradeInfinity, maxRushDegradeNormal, 1.0)
                : Math.max(maxRushDegradeNormal, MAX_RUSH_DEGRADE_INFINITY);
            maxTranscriptionSlipChance = Double.isFinite(maxTranscriptionSlipChance)
                ? Mth.clamp(maxTranscriptionSlipChance, 0.0, 1.0)
                : MAX_TRANSCRIPTION_SLIP_CHANCE;
            crosslineSlip = crosslineSlip != null ? crosslineSlip : CrosslineSlipConfig.fromJson(null, maxTranscriptionSlipChance);
        }

        public int maxSingleWaitTicks() {
            return maxSingleWaitSeconds * 20;
        }

        public static Config fromJson(@Nullable com.google.gson.JsonObject root) {
            if (root == null) {
                return DEFAULT;
            }
            com.google.gson.JsonObject r = root.has("ritual") && root.get("ritual").isJsonObject()
                ? root.getAsJsonObject("ritual")
                : null;
            int maxWaitSec = r != null ? intOr(r, "max_single_wait_seconds", DEFAULT.maxSingleWaitSeconds) : DEFAULT.maxSingleWaitSeconds;
            int minTomeEnch = r != null ? intOr(r, "min_tome_enchants_for_wait", DEFAULT.minTomeEnchantsForWait) : DEFAULT.minTomeEnchantsForWait;
            int maxSacXp = r != null ? intOr(r, "max_sacrifice_xp_levels", DEFAULT.maxSacrificeXpLevels) : DEFAULT.maxSacrificeXpLevels;
            double rushNorm = r != null ? doubleOr(r, "max_rush_degrade_normal", DEFAULT.maxRushDegradeNormal) : DEFAULT.maxRushDegradeNormal;
            double rushInf = r != null ? doubleOr(r, "max_rush_degrade_infinity", DEFAULT.maxRushDegradeInfinity) : DEFAULT.maxRushDegradeInfinity;
            double slipCap = r != null ? doubleOr(r, "max_transcription_slip_chance", DEFAULT.maxTranscriptionSlipChance) : DEFAULT.maxTranscriptionSlipChance;
            CrosslineSlipConfig crossline = CrosslineSlipConfig.fromJson(root, slipCap);
            return new Config(maxWaitSec, minTomeEnch, maxSacXp, rushNorm, rushInf, crossline.maxSlipChance(), crossline);
        }

        static int intOr(com.google.gson.JsonObject obj, String key, int fallback) {
            if (!obj.has(key) || !obj.get(key).isJsonPrimitive() || !obj.get(key).getAsJsonPrimitive().isNumber()) {
                return fallback;
            }
            return obj.get(key).getAsInt();
        }

        static double doubleOr(com.google.gson.JsonObject obj, String key, double fallback) {
            if (!obj.has(key) || !obj.get(key).isJsonPrimitive() || !obj.get(key).getAsJsonPrimitive().isNumber()) {
                return fallback;
            }
            return obj.get(key).getAsDouble();
        }
    }

    public static final Identifier MENDING_ID = Identifier.withDefaultNamespace("mending");
    public static final Identifier UNBREAKING_ID = Identifier.withDefaultNamespace("unbreaking");
    private static final Set<Identifier> HIGH_APPLICABILITY_IDS = Set.of(
        Identifier.withDefaultNamespace("protection"),
        Identifier.withDefaultNamespace("sharpness"),
        Identifier.withDefaultNamespace("efficiency"),
        Identifier.withDefaultNamespace("fortune"),
        Identifier.withDefaultNamespace("looting"),
        Identifier.withDefaultNamespace("power"),
        Identifier.withDefaultNamespace("silk_touch"),
        Identifier.withDefaultNamespace("wind_burst"),
        Identifier.withDefaultNamespace("breach"),
        Identifier.withDefaultNamespace("density")
    );

    private InscriptionRitualMechanics() {}

    /** Compact persisted material cost entry for 50% cross-line pollution compensation. */
    public record PaidItemEntry(Identifier itemId, int count) {
        public PaidItemEntry {
            count = Mth.clamp(count, 1, 1024);
        }
    }

    /** Single queued enchantment inscription persisted on {@code ChiseledEnchantingTableBlockEntity}. */
    public record QueuedInscription(
        Identifier enchantmentId,
        int targetLevel,
        int previousLevel,
        boolean masterTomeUsed,
        boolean usedInfinityTome,
        int conflictRefundPoints,
        boolean consumedEpiphany,
        double infinityTomeLoad,
        int paidXpPoints,
        List<PaidItemEntry> paidItems
    ) {
        public QueuedInscription {
            infinityTomeLoad = Double.isFinite(infinityTomeLoad) ? Math.max(0.0, Math.min(256.0, infinityTomeLoad)) : 0.0;
            paidXpPoints = Math.max(0, Math.min(1_000_000, paidXpPoints));
            paidItems = paidItems == null ? List.of() : List.copyOf(paidItems);
        }

        public QueuedInscription(
            Identifier enchantmentId,
            int targetLevel,
            int previousLevel,
            boolean masterTomeUsed,
            boolean usedInfinityTome,
            int conflictRefundPoints,
            boolean consumedEpiphany
        ) {
            this(
                enchantmentId,
                targetLevel,
                previousLevel,
                masterTomeUsed,
                usedInfinityTome,
                conflictRefundPoints,
                consumedEpiphany,
                0.0,
                0,
                List.of()
            );
        }

        public static List<PaidItemEntry> fromStacks(List<ItemStack> stacks) {
            if (stacks == null || stacks.isEmpty()) {
                return List.of();
            }
            List<PaidItemEntry> out = new ArrayList<>(stacks.size());
            for (ItemStack st : stacks) {
                if (st == null || st.isEmpty() || st.getCount() <= 0) {
                    continue;
                }
                Identifier id = BuiltInRegistries.ITEM.getKey(st.getItem());
                if (id != null && !BuiltInRegistries.ITEM.getKey(Items.AIR).equals(id)) {
                    out.add(new PaidItemEntry(id, st.getCount()));
                }
            }
            return List.copyOf(out);
        }

        public String encode() {
            StringBuilder itemsEnc = new StringBuilder();
            for (int i = 0; i < paidItems.size(); i++) {
                if (i > 0) itemsEnc.append(',');
                PaidItemEntry e = paidItems.get(i);
                itemsEnc.append(e.itemId()).append('*').append(e.count());
            }
            return enchantmentId.toString()
                + "|" + targetLevel
                + "|" + previousLevel
                + "|" + (masterTomeUsed ? 1 : 0)
                + "|" + (usedInfinityTome ? 1 : 0)
                + "|" + conflictRefundPoints
                + "|" + (consumedEpiphany ? 1 : 0)
                + "|" + String.format(Locale.ROOT, "%.4f", infinityTomeLoad)
                + "|" + paidXpPoints
                + "|" + itemsEnc;
        }

        @Nullable
        public static QueuedInscription decode(String token) {
            if (token == null || token.isBlank()) {
                return null;
            }
            String[] parts = token.split("\\|", -1);
            if ((parts.length != 7 && parts.length != 10) || token.length() > 2048) {
                return null;
            }
            try {
                Identifier id = Identifier.parse(parts[0].trim());
                int rawTarget = Integer.parseInt(parts[1].trim());
                int rawPrev = Integer.parseInt(parts[2].trim());
                int rawRefund = Integer.parseInt(parts[5].trim());
                if (rawTarget < 1 || rawTarget > 255 || rawPrev < 0 || rawPrev > 255 || rawRefund < 0
                    || !Set.of("0", "1").contains(parts[3].trim())
                    || !Set.of("0", "1").contains(parts[4].trim())
                    || !Set.of("0", "1").contains(parts[6].trim())) {
                    return null;
                }
                boolean tomeUsed = "1".equals(parts[3].trim());
                boolean infTome = "1".equals(parts[4].trim());
                boolean epip = "1".equals(parts[6].trim());
                if (parts.length == 7) {
                    return new QueuedInscription(id, rawTarget, rawPrev, tomeUsed, infTome, rawRefund, epip);
                }
                double load = Double.parseDouble(parts[7].trim());
                int paidXp = Integer.parseInt(parts[8].trim());
                if (!Double.isFinite(load) || load < 0.0 || load > 256.0 || paidXp < 0 || paidXp > 1_000_000) {
                    return null;
                }
                List<PaidItemEntry> items = new ArrayList<>();
                String rawItems = parts[9].trim();
                if (!rawItems.isEmpty()) {
                    String[] entries = rawItems.split(",", -1);
                    if (entries.length > 16) {
                        return null;
                    }
                    for (String itemToken : entries) {
                        int star = itemToken.indexOf('*');
                        if (star <= 0 || star >= itemToken.length() - 1) {
                            return null;
                        }
                        Identifier itemId = Identifier.parse(itemToken.substring(0, star).trim());
                        int cnt = Integer.parseInt(itemToken.substring(star + 1).trim());
                        if (cnt < 1 || cnt > 1024) {
                            return null;
                        }
                        items.add(new PaidItemEntry(itemId, cnt));
                    }
                }
                return new QueuedInscription(id, rawTarget, rawPrev, tomeUsed, infTome, rawRefund, epip, load, paidXp, items);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    public static String serializeQueue(List<QueuedInscription> queue) {
        if (queue == null || queue.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < queue.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(queue.get(i).encode());
        }
        return sb.toString();
    }

    public static List<QueuedInscription> deserializeQueue(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        if (raw.length() > 32_000) throw new IllegalArgumentException("Oversized persisted ritual");
        List<QueuedInscription> out = new ArrayList<>();
        long refund = 0;
        for (String part : raw.split(";", -1)) {
            if (out.size() >= 256) throw new IllegalArgumentException("Too many ritual steps");
            QueuedInscription parsed = QueuedInscription.decode(part);
            if (parsed == null) throw new IllegalArgumentException("Malformed ritual step");
            refund += parsed.conflictRefundPoints();
            if (refund > Integer.MAX_VALUE) throw new IllegalArgumentException("Ritual refund exceeds budget");
            out.add(parsed);
        }
        return out;
    }

    /** Snapshot of surrounding bookshelves &amp; Chiseled Bookshelf Master Tomes within scan range. */
    public record LibraryRitualScan(
        int totalDetectedBookshelves,
        boolean hasAnyMasterTome,
        int totalMasterTomeEnchantments,
        int maxOverloadedInfinityTomeEnchants
    ) {}

    /** Metadata for one candidate Master Tome when resolving non-sacrifice skip degradation. */
    public record TomeCandidate(
        BlockPos shelfPos,
        int slot,
        int enchantCount,
        double averageRelativeLevel,
        double averageRarityScore,
        boolean hasInfinity
    ) {
        /**
         * Lower score = more expendable = degraded FIRST («相对低等级、低附魔数、低稀有度最优先»).
         */
        public double preservationScore() {
            double countNorm = Math.min(1.5, Math.max(1, enchantCount) / 5.0);
            double relNorm = Mth.clamp(averageRelativeLevel, 0.05, 1.0);
            double rarityNorm = Mth.clamp(averageRarityScore, 0.05, 1.0);
            return 0.40 * countNorm + 0.35 * relNorm + 0.25 * rarityNorm;
        }
    }

    /** Exact median (middle tier value) of the 3 bookshelf gate thresholds. */
    public static int medianShelfGate(BookshelfProgression.Config cfg) {
        BookshelfProgression.Config c = cfg != null ? cfg : BookshelfProgression.Config.DEFAULT;
        return medianOfThree(c.tier1Bookshelves(), c.tier2Bookshelves(), c.maxDirectBookshelves());
    }

    public static int medianOfThree(int a, int b, int c) {
        return a + b + c - Math.min(a, Math.min(b, c)) - Math.max(a, Math.max(b, c));
    }

    /**
     * Strict iff activation gate: waiting exists IF AND ONLY IF nearby shelves {@code >= middleShelfTier}
     * OR surrounding Master Tome enchantments {@code >= minTomeEnchantsForWait}.
     */
    public static boolean shouldRequireRitualWait(
        int nearbyBookshelves,
        int middleShelfTier,
        int totalMasterTomeEnchantments
    ) {
        return shouldRequireRitualWait(Config.DEFAULT, nearbyBookshelves, middleShelfTier, totalMasterTomeEnchantments);
    }

    public static boolean shouldRequireRitualWait(
        @Nullable Config ritualCfg,
        int nearbyBookshelves,
        int middleShelfTier,
        int totalMasterTomeEnchantments
    ) {
        Config c = ritualCfg != null ? ritualCfg : Config.DEFAULT;
        return Math.max(0, nearbyBookshelves) >= Math.max(1, middleShelfTier)
            || Math.max(0, totalMasterTomeEnchantments) >= c.minTomeEnchantsForWait();
    }

    /** Applicability factor reflecting enchantment universality &amp; keystone impact ({@code >= 1.0}). */
    public static double computeApplicabilityFactor(Identifier enchantmentId, int supportedItemCount, int anvilWeight) {
        double itemBreadth = 1.0 + 0.42 * Mth.clamp((supportedItemCount - 1) / 24.0, 0.0, 1.0);
        double rarityBoost = anvilWeight <= 1 ? 1.18 : (anvilWeight <= 2 ? 1.12 : (anvilWeight <= 5 ? 1.05 : 1.0));
        double keystone = 1.0;
        if (MasterTomeWear.INFINITY_ENCHANTMENT_ID.equals(enchantmentId) || MENDING_ID.equals(enchantmentId)) {
            keystone = 1.55;
        } else if (UNBREAKING_ID.equals(enchantmentId)) {
            keystone = 1.32;
        } else if (HIGH_APPLICABILITY_IDS.contains(enchantmentId)) {
            keystone = 1.20;
        }
        return Mth.clamp(itemBreadth * rarityBoost * keystone, 1.0, 2.35);
    }

    public static double computeApplicabilityFactor(Identifier enchantmentId, Holder<Enchantment> holder) {
        int supportedCount = holder.value().definition().supportedItems().size();
        int weight = holder.value().definition().weight();
        return computeApplicabilityFactor(enchantmentId, supportedCount, weight);
    }

    /**
     * Smooth saturating single-enchantment casting duration in ticks ({@code 0} when gate is inactive;
     * otherwise bounded strictly within {@code [100, maxSingleWaitTicks]}).
     */
    public static int computeSingleEnchantWaitTicks(
        int nearbyBookshelves,
        int middleShelfTier,
        int totalMasterTomeEnchantments,
        int targetLevel,
        int maxLevel,
        double applicabilityFactor
    ) {
        return computeSingleEnchantWaitTicks(
            Config.DEFAULT,
            nearbyBookshelves,
            middleShelfTier,
            totalMasterTomeEnchantments,
            targetLevel,
            maxLevel,
            applicabilityFactor
        );
    }

    public static int computeSingleEnchantWaitTicks(
        @Nullable Config ritualCfg,
        int nearbyBookshelves,
        int middleShelfTier,
        int totalMasterTomeEnchantments,
        int targetLevel,
        int maxLevel,
        double applicabilityFactor
    ) {
        Config c = ritualCfg != null ? ritualCfg : Config.DEFAULT;
        if (!shouldRequireRitualWait(c, nearbyBookshelves, middleShelfTier, totalMasterTomeEnchantments)) {
            return 0;
        }
        double relLevel = Mth.clamp((double) Math.max(1, targetLevel) / (double) Math.max(1, maxLevel), 0.10, 1.0);
        double shelfLoad = Math.max(0.0, nearbyBookshelves) / 42.0;
        double tomeLoad = Math.max(0.0, totalMasterTomeEnchantments) / 22.0;
        double intensity = (0.42 * shelfLoad + 0.58 * tomeLoad)
            * (0.35 + 0.65 * Math.pow(relLevel, 1.15))
            * Math.max(1.0, applicabilityFactor);
        double maxSec = c.maxSingleWaitSeconds();
        double seconds = maxSec * (1.0 - Math.exp(-0.62 * intensity));
        int ticks = (int) Math.round(seconds * 20.0);
        return Mth.clamp(ticks, 100, c.maxSingleWaitTicks());
    }

    /**
     * Direct XP level sacrifice cost ({@code 1..maxSacrificeXpLevels}) derived smoothly from live remaining ticks.
     */
    public static int computeSacrificeXpLevels(long remainingTicks) {
        return computeSacrificeXpLevels(Config.DEFAULT, remainingTicks);
    }

    public static int computeSacrificeXpLevels(@Nullable Config ritualCfg, long remainingTicks) {
        if (remainingTicks <= 0L) {
            return 0;
        }
        Config c = ritualCfg != null ? ritualCfg : Config.DEFAULT;
        int cap = c.maxSacrificeXpLevels();
        double remSeconds = remainingTicks / 20.0;
        int raw = (int) Math.ceil(cap * (1.0 - Math.exp(-remSeconds / 240.0)));
        return Mth.clamp(raw, 1, cap);
    }

    /**
     * Live Master Tome degradation probability when forcing a rush skip without sacrificing XP.
     */
    public static double computeRushDegradeChance(long remainingTicks, boolean usedInfinityMasterTome) {
        return computeRushDegradeChance(Config.DEFAULT, remainingTicks, usedInfinityMasterTome);
    }

    public static double computeRushDegradeChance(
        @Nullable Config ritualCfg,
        long remainingTicks,
        boolean usedInfinityMasterTome
    ) {
        if (remainingTicks <= 0L) {
            return 0.0;
        }
        Config c = ritualCfg != null ? ritualCfg : Config.DEFAULT;
        double cap = usedInfinityMasterTome ? c.maxRushDegradeInfinity() : c.maxRushDegradeNormal();
        if (cap <= 0.0) {
            return 0.0;
        }
        double remSeconds = remainingTicks / 20.0;
        double raw = cap * (1.0 - Math.exp(-remSeconds / 210.0));
        return Mth.clamp(raw, cap * 0.02, cap);
    }

    /**
     * Overloaded {@code Infinity} Master Tome transcription slip («抄错») probability:
     * active ONLY when a tome in scan range holds {@code minecraft:infinity} AND {@code > 3} other
     * enchantments ({@code maxOverloadedTomeEnchants >= 5}); scales across both single-tome
     * enchantment count and detected shelf count, strictly capped at {@code maxTranscriptionSlipChance}.
     */
    /** Pure descriptor of a single enchantment on an {@code Infinity} Master Tome for headless load scoring. */
    public record TraitLoadInput(
        boolean isInfinity,
        boolean isCurse,
        boolean isTreasure,
        boolean isUnbreaking,
        int weight,
        int supportedItemCount,
        int level,
        int maxLevel
    ) {}

    /** Three-branch outcome when a single-roll overloaded {@code Infinity} Master Tome slip triggers. */
    public enum SlipOutcomeKind {
        NONE,
        /** 70%: drops 1 enchantment tier («笔误成瑕»). */
        TIER_DROP,
        /** 25%: cross-lines into an unusable / low-rarity non-treasure enchantment («……抄串行了»). */
        JUNK_CROSSLINE,
        /** 5%: cross-lines into a curse enchantment («……抄串行了»). */
        CURSE_CROSSLINE;

        public boolean isCrosslinePollution() {
            return this == JUNK_CROSSLINE || this == CURSE_CROSSLINE;
        }
    }

    /**
     * Computes the qualification score contribution of a single trait on an {@code Infinity} Master Tome
     * (excluding {@code minecraft:infinity} itself and {@code #minecraft:curse}):
     * <ul>
     *   <li>Treasure ({@code #minecraft:treasure \ #minecraft:curse}): {@code +1.0}</li>
     *   <li>Rarity {@code weight <= 2}: {@code +1.0} (or {@code 3..5}: {@code +0.5})</li>
     *   <li>Supported items {@code >= 12}: {@code +0.5}</li>
     *   <li>Relative level {@code level / maxLevel >= 0.6}: {@code +0.5}</li>
     *   <li>{@code minecraft:unbreaking >= III}: {@code +1.0}</li>
     * </ul>
     */
    public static double computeTraitQualificationScore(@Nullable CrosslineSlipConfig slipCfg, TraitLoadInput t) {
        if (t == null || t.isInfinity() || t.isCurse() || t.level() <= 0) {
            return 0.0;
        }
        CrosslineSlipConfig c = slipCfg != null ? slipCfg : CrosslineSlipConfig.DEFAULT;
        double score = 0.0;
        if (t.isTreasure()) {
            score += c.treasureScore();
        }
        int w = Math.max(1, t.weight());
        if (w <= c.highRarityMaxWeight()) {
            score += c.highRarityScore();
        } else if (w <= c.midRarityMaxWeight()) {
            score += c.midRarityScore();
        }
        if (t.supportedItemCount() >= c.broadApplicabilityMinItems()) {
            score += c.broadApplicabilityScore();
        }
        int maxLvl = Math.max(1, t.maxLevel());
        if (((double) t.level() / (double) maxLvl) + 1e-9 >= c.relativeLevelMinRatio()) {
            score += c.relativeLevelScore();
        }
        if (t.isUnbreaking() && t.level() >= c.unbreakingHighMinLevel()) {
            score += c.unbreakingHighScore();
        }
        return score;
    }

    /**
     * Computes total qualification load {@code L} of an {@code Infinity} Master Tome from pure trait descriptors.
     * Returns {@code 0.0} if the book does not carry {@code minecraft:infinity}.
     */
    public static double computeInfinityTomeQualificationLoad(
        @Nullable CrosslineSlipConfig slipCfg,
        List<TraitLoadInput> traits
    ) {
        if (traits == null || traits.isEmpty()) {
            return 0.0;
        }
        CrosslineSlipConfig c = slipCfg != null ? slipCfg : CrosslineSlipConfig.DEFAULT;
        boolean hasInf = false;
        boolean hasNonCurseTreasure = false;
        double total = 0.0;
        for (TraitLoadInput t : traits) {
            if (t == null || t.level() <= 0) continue;
            if (t.isInfinity()) {
                hasInf = true;
                continue;
            }
            if (t.isCurse()) {
                continue;
            }
            if (t.isTreasure()) {
                hasNonCurseTreasure = true;
            }
            total += computeTraitQualificationScore(c, t);
        }
        if (!hasInf) {
            return 0.0;
        }
        if (hasNonCurseTreasure) {
            total = Math.max(total, c.singleTreasureMinLoad());
        }
        return total;
    }

    /** Live-stack overload computing qualification load {@code L} directly on a chosen Master Tome stack. */
    public static double computeInfinityTomeQualificationLoad(
        @Nullable Config ritualCfg,
        ItemStack tomeStack
    ) {
        if (tomeStack == null || tomeStack.isEmpty() || !MasterTomeWear.hasInfinityImmunity(tomeStack)) {
            return 0.0;
        }
        CrosslineSlipConfig c = ritualCfg != null ? ritualCfg.crosslineSlip() : CrosslineSlipConfig.DEFAULT;
        ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(tomeStack);
        if (enchants.isEmpty()) {
            return 0.0;
        }
        List<TraitLoadInput> inputs = new ArrayList<>(enchants.size());
        for (var entry : enchants.entrySet()) {
            int lv = entry.getIntValue();
            if (lv <= 0) continue;
            Holder<Enchantment> h = entry.getKey();
            Identifier id = h.unwrapKey().map(k -> k.identifier()).orElse(null);
            boolean isInf = MasterTomeWear.INFINITY_ENCHANTMENT_ID.equals(id);
            boolean isCurse = h.is(EnchantmentTags.CURSE);
            boolean isTreasure = !isCurse && h.is(EnchantmentTags.TREASURE);
            boolean isUnb = UNBREAKING_ID.equals(id);
            int w = h.value().definition().weight();
            int sup = h.value().definition().supportedItems().size();
            int maxLvl = h.value().getMaxLevel();
            inputs.add(new TraitLoadInput(isInf, isCurse, isTreasure, isUnb, w, sup, lv, maxLvl));
        }
        return computeInfinityTomeQualificationLoad(c, inputs);
    }

    /**
     * Exact single-roll slip probability:
     * {@code P = min(18%, 0.035 * (L - 3) + shelfBonus)} when {@code L >= 3}, and {@code 0%} when {@code L < 3}.
     */
    public static double computeLoadSlipProbability(
        @Nullable Config ritualCfg,
        double tomeLoadL,
        int totalDetectedBookshelves
    ) {
        CrosslineSlipConfig c = ritualCfg != null ? ritualCfg.crosslineSlip() : CrosslineSlipConfig.DEFAULT;
        if (c.maxSlipChance() <= 0.0 || tomeLoadL + 1e-9 < c.minLoadThreshold()) {
            return 0.0;
        }
        int s = Math.max(0, totalDetectedBookshelves);
        double shelfBonus;
        if (s >= c.shelfTier3Count()) {
            shelfBonus = c.shelfTier3Bonus();
        } else if (s >= c.shelfTier2Count()) {
            shelfBonus = c.shelfTier2Bonus();
        } else if (s >= c.shelfTier1Count()) {
            shelfBonus = c.shelfTier1Bonus();
        } else {
            shelfBonus = 0.0;
        }
        double raw = c.perLoadSlope() * Math.max(0.0, tomeLoadL - c.minLoadThreshold()) + shelfBonus;
        return Mth.clamp(Math.min(c.maxSlipChance(), raw), 0.0, 1.0);
    }

    /**
     * Resolves the single-roll, three-tier outcome ({@code 70%} tier-1 drop, {@code 25%} junk cross-line,
     * {@code 5%} curse cross-line) given {@code slipProbability} and uniform rolls in {@code [0, 1)}.
     */
    public static SlipOutcomeKind rollMergedSlipOutcome(
        @Nullable Config ritualCfg,
        double slipProbability,
        double triggerRoll,
        double branchRoll
    ) {
        if (slipProbability <= 0.0 || triggerRoll >= slipProbability) {
            return SlipOutcomeKind.NONE;
        }
        CrosslineSlipConfig c = ritualCfg != null ? ritualCfg.crosslineSlip() : CrosslineSlipConfig.DEFAULT;
        double wDrop = Math.max(0.0, c.tierDropShare());
        double wJunk = Math.max(0.0, c.junkCrosslineShare());
        double wCurse = Math.max(0.0, c.curseCrosslineShare());
        double sum = wDrop + wJunk + wCurse;
        if (sum <= 1e-9) {
            return SlipOutcomeKind.TIER_DROP;
        }
        double u = Mth.clamp(branchRoll, 0.0, 0.999999999) * sum;
        if (u < wDrop) {
            return SlipOutcomeKind.TIER_DROP;
        }
        if (u < wDrop + wJunk) {
            return SlipOutcomeKind.JUNK_CROSSLINE;
        }
        return SlipOutcomeKind.CURSE_CROSSLINE;
    }

    /**
     * Checks whether {@code holder} is allowed inside the {@code 25%} junk cross-line pool:
     * excludes {@code #minecraft:treasure}, {@code #minecraft:curse},
     * {@code #chiseled_enchanting_table:epiphany_exclusive} (including {@code wind_burst}),
     * {@code #chiseled_enchanting_table:crossline_excluded}, {@code infinity}, {@code mending},
     * the originally requested {@code intendedEnchantId}, and any enchantment already present on the item.
     */
    public static boolean isEligibleJunkCrosslineCandidate(
        Holder<Enchantment> holder,
        @Nullable Identifier intendedEnchantId,
        Set<Holder<Enchantment>> alreadyPresent
    ) {
        if (holder == null || holder.value().getMaxLevel() <= 0) {
            return false;
        }
        if (alreadyPresent != null && alreadyPresent.contains(holder)) {
            return false;
        }
        if (holder.is(EnchantmentTags.TREASURE)
            || holder.is(EnchantmentTags.CURSE)
            || holder.is(EPIPHANY_EXCLUSIVE_TAG)
            || holder.is(CROSSLINE_EXCLUDED_TAG)) {
            return false;
        }
        Identifier id = holder.unwrapKey().map(k -> k.identifier()).orElse(null);
        if (id == null
            || MasterTomeWear.INFINITY_ENCHANTMENT_ID.equals(id)
            || MENDING_ID.equals(id)
            || id.equals(intendedEnchantId)) {
            return false;
        }
        return true;
    }

    /**
     * Selects a low-rarity cross-line enchantment weighted by vanilla {@code definition().weight()}:
     * when {@code !targetWasBook}, strictly prioritizes enchantments that CANNOT enchant {@code targetStack}
     * ("completely unusable on the item"); when {@code targetWasBook}, samples from the low-rarity pool.
     */
    @Nullable
    public static Holder<Enchantment> selectJunkCrosslineEnchantment(
        Registry<Enchantment> registry,
        ItemStack targetStack,
        boolean targetWasBook,
        @Nullable Identifier intendedEnchantId,
        Set<Holder<Enchantment>> alreadyPresent,
        RandomSource random
    ) {
        List<Holder.Reference<Enchantment>> unusable = new ArrayList<>();
        List<Holder.Reference<Enchantment>> usable = new ArrayList<>();
        registry.listElements().forEach(ref -> {
            if (!isEligibleJunkCrosslineCandidate(ref, intendedEnchantId, alreadyPresent)) {
                return;
            }
            if (!targetWasBook && targetStack != null && !targetStack.isEmpty() && !ref.value().canEnchant(targetStack)) {
                unusable.add(ref);
            } else {
                usable.add(ref);
            }
        });
        List<Holder.Reference<Enchantment>> pool = (!targetWasBook && !unusable.isEmpty()) ? unusable : usable;
        if (pool.isEmpty()) {
            pool = unusable.isEmpty() ? usable : unusable;
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pickByVanillaWeight(pool, random);
    }

    /** Selects a curse enchantment from {@code #minecraft:curse} for the {@code 5%} curse cross-line branch. */
    @Nullable
    public static Holder<Enchantment> selectCurseCrosslineEnchantment(
        Registry<Enchantment> registry,
        Set<Holder<Enchantment>> alreadyPresent,
        RandomSource random
    ) {
        List<Holder.Reference<Enchantment>> freshCurses = new ArrayList<>();
        List<Holder.Reference<Enchantment>> allCurses = new ArrayList<>();
        registry.listElements().forEach(ref -> {
            if (ref.is(EnchantmentTags.CURSE) && ref.value().getMaxLevel() > 0) {
                allCurses.add(ref);
                if (alreadyPresent == null || !alreadyPresent.contains(ref)) {
                    freshCurses.add(ref);
                }
            }
        });
        List<Holder.Reference<Enchantment>> pool = !freshCurses.isEmpty() ? freshCurses : allCurses;
        return pool.isEmpty() ? null : pickByVanillaWeight(pool, random);
    }

    private static Holder<Enchantment> pickByVanillaWeight(
        List<Holder.Reference<Enchantment>> pool,
        RandomSource random
    ) {
        long totalWeight = 0L;
        for (var ref : pool) {
            totalWeight += Math.max(1, ref.value().definition().weight());
        }
        long pick = Math.floorMod(random.nextLong(), Math.max(1L, totalWeight));
        long acc = 0L;
        for (var ref : pool) {
            acc += Math.max(1, ref.value().definition().weight());
            if (pick < acc) {
                return ref;
            }
        }
        return pool.getLast();
    }

    /** Computes exact 50% XP point compensation bounded strictly by {@code [0, paidXpPoints]}. */
    public static int computePollutionXpRefund(@Nullable Config ritualCfg, int paidXpPoints) {
        if (paidXpPoints <= 0) {
            return 0;
        }
        double ratio = ritualCfg != null
            ? ritualCfg.crosslineSlip().pollutionRefundRatio()
            : CrosslineSlipConfig.DEFAULT.pollutionRefundRatio();
        if (ratio <= 0.0) {
            return 0;
        }
        int rounded = Math.max(1, (int) Math.round(paidXpPoints * ratio));
        return Math.min(paidXpPoints, rounded);
    }

    /**
     * Computes unbiased {@code pollutionRefundRatio} (default {@code 50%}) material compensation stacks:
     * for count {@code C}, returns {@code floor(C * r) + Bernoulli(frac(C * r))}, guaranteeing exact
     * {@code 50%} expected return even for {@code 1}-item catalysts while never exceeding {@code C}.
     */
    public static List<ItemStack> computePollutionMaterialRefunds(
        @Nullable Config ritualCfg,
        List<PaidItemEntry> paidItems,
        RandomSource random
    ) {
        if (paidItems == null || paidItems.isEmpty()) {
            return List.of();
        }
        double ratio = ritualCfg != null
            ? ritualCfg.crosslineSlip().pollutionRefundRatio()
            : CrosslineSlipConfig.DEFAULT.pollutionRefundRatio();
        if (ratio <= 0.0) {
            return List.of();
        }
        List<ItemStack> refunded = new ArrayList<>();
        for (PaidItemEntry entry : paidItems) {
            if (entry == null || entry.count() <= 0) continue;
            Item item = BuiltInRegistries.ITEM.getValue(entry.itemId());
            if (item == Items.AIR) continue;
            double exact = entry.count() * ratio;
            int whole = (int) Math.floor(exact);
            double frac = exact - whole;
            int retCount = whole + (frac > 1e-6 && random.nextDouble() < frac ? 1 : 0);
            retCount = Math.min(entry.count(), Math.max(0, retCount));
            while (retCount > 0) {
                int batch = Math.min(retCount, item.getDefaultMaxStackSize());
                refunded.add(new ItemStack(item, batch));
                retCount -= batch;
            }
        }
        return List.copyOf(refunded);
    }

    public static double computeTranscriptionSlipChance(
        int maxOverloadedTomeEnchants,
        int totalDetectedBookshelves
    ) {
        return computeTranscriptionSlipChance(Config.DEFAULT, maxOverloadedTomeEnchants, totalDetectedBookshelves);
    }

    public static double computeTranscriptionSlipChance(
        @Nullable Config ritualCfg,
        int maxOverloadedTomeEnchants,
        int totalDetectedBookshelves
    ) {
        if (maxOverloadedTomeEnchants < 5) {
            return 0.0;
        }
        // Legacy proxy mapping total enchantment count (>= 5 -> load >= 3.5) onto the unified curve
        double proxyLoad = 3.0 + (maxOverloadedTomeEnchants - 4) * 1.25;
        return computeLoadSlipProbability(ritualCfg, proxyLoad, totalDetectedBookshelves);
    }

    /**
     * Resolves the post-transcription enchantment level given {@code slipChance} and uniform rolls
     * in {@code [0, 1)}. When {@code targetLevel > 1} and {@code slipRoll < slipChance}, downgrades
     * to a strictly lower valid tier {@code [1 .. targetLevel - 1]}.
     */
    public static int resolveTranscribedLevel(
        int targetLevel,
        int maxOverloadedTomeEnchants,
        double slipChance,
        double slipRoll,
        double severityRoll
    ) {
        int safeTarget = Math.max(1, targetLevel);
        if (safeTarget <= 1 || slipChance <= 0.0 || slipRoll >= slipChance) {
            return safeTarget;
        }
        int drop = (maxOverloadedTomeEnchants >= 7 && safeTarget >= 3 && severityRoll < 0.25) ? 2 : 1;
        return Math.max(1, safeTarget - drop);
    }

    /**
     * Computes deterministic selection weight for Treasure Epiphany candidates:
     * rarer enchantments (lower {@code definition.weight()}) AND broader universal enchantments
     * (larger {@code supportedItems.size()}, such as {@code Mending}) appear less frequently than
     * specialized niche treasures.
     */
    public static double computeEpiphanySelectionWeight(int enchantmentWeight, int supportedItemCount) {
        double rarityFactor = Math.pow(Mth.clamp(enchantmentWeight, 1, 16) / 2.0, 0.75);
        double universalityFactor = 1.0 / (1.0 + 0.085 * Math.max(0, supportedItemCount - 1));
        return Math.max(0.05, rarityFactor * universalityFactor);
    }

    public static double computeEpiphanySelectionWeight(Holder<Enchantment> holder) {
        int w = holder.value().definition().weight();
        int sup = holder.value().definition().supportedItems().size();
        return computeEpiphanySelectionWeight(w, sup);
    }

    /** Deterministic weighted roulette-wheel pick without replacement using an unsigned 64-bit hash. */
    public static int pickWeightedIndex(List<Double> weights, long unsignedHash) {
        if (weights == null || weights.isEmpty()) {
            return 0;
        }
        double total = 0.0;
        for (double w : weights) {
            total += Math.max(1e-6, w);
        }
        double u = ((unsignedHash >>> 11) * (1.0 / (1L << 53))) * total;
        double acc = 0.0;
        for (int i = 0; i < weights.size(); i++) {
            acc += Math.max(1e-6, weights.get(i));
            if (u <= acc) {
                return i;
            }
        }
        return weights.size() - 1;
    }

    /**
     * Selects the highest-priority degradation victim: non-{@code Infinity} Master Tomes are ALWAYS
     * chosen before {@code Infinity}-blessed Master Tomes, then broken by minimum preservation score.
     */
    @Nullable
    public static TomeCandidate selectDegradationVictim(List<TomeCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
            .min(Comparator
                .comparing(TomeCandidate::hasInfinity)
                .thenComparingDouble(TomeCandidate::preservationScore)
                .thenComparingInt(TomeCandidate::enchantCount)
                .thenComparingDouble(TomeCandidate::averageRelativeLevel)
                .thenComparingDouble(TomeCandidate::averageRarityScore)
                .thenComparing(c -> c.shelfPos().asLong())
                .thenComparingInt(TomeCandidate::slot))
            .orElse(null);
    }

    /** Scans all nearby bookshelves &amp; Chiseled Bookshelf Master Tomes around {@code tablePos}. */
    public static LibraryRitualScan scanLibraryRitualContext(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config cfg
    ) {
        if (level == null || tablePos == null) {
            return new LibraryRitualScan(0, false, 0, 0);
        }
        BookshelfProgression.Config safeCfg = cfg != null ? cfg : BookshelfProgression.Config.DEFAULT;
        boolean ceilingBlocked = ChiseledEnchantingTableBlock.isTableCeilingBlocked(level, tablePos);
        int openAdj = ChiseledEnchantingTableBlock.countOpenAdjacentBlocks(level, tablePos);
        if (safeCfg.requireBreathingRoom()
            && BookshelfProgression.effectiveShelvesAfterBreathingRoom(1, ceilingBlocked, openAdj, safeCfg) <= 0) {
            return new LibraryRitualScan(0, false, 0, 0);
        }

        int rawShelves = 0;
        boolean anyTome = false;
        int rawTomeEnchants = 0;
        int maxOverloadedInf = 0;
        boolean checkLos = safeCfg.requireLineOfSight();

        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(safeCfg)) {
            BlockPos p = tablePos.offset(offset);
            if (!level.hasChunkAt(p)) {
                continue;
            }
            if (checkLos && !ChiseledEnchantingTableBlock.hasClearLineOfSight(level, tablePos, p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (ChiseledEnchantingTableBlock.isRegularBookshelf(state)) {
                rawShelves++;
                continue;
            }
            if (!state.is(Blocks.CHISELED_BOOKSHELF)) {
                continue;
            }
            // Broad bookshelves (广义书架) include regular bookshelves, empty chiseled bookshelves,
            // AND book-holding chiseled bookshelves (including Depleted Tomes); only slots holding
            // Enchanted Books count toward Master Tome resonance below.
            rawShelves++;
            if (!(level.getBlockEntity(p) instanceof ChiseledBookShelfBlockEntity shelfBe)) {
                continue;
            }
            for (int slot = 0; slot < shelfBe.getContainerSize(); slot++) {
                ItemStack book = shelfBe.getItem(slot);
                if (book.isEmpty()) continue;
                ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(book);
                int count = enchants.size();
                if (count <= 0) continue;
                anyTome = true;
                rawTomeEnchants += count;
                boolean hasInf = MasterTomeWear.hasInfinityImmunity(book);
                // Strictly: possesses Infinity (1) AND strictly more than 3 OTHER enchantments (> 3 => count - 1 > 3 => count >= 5).
                if (hasInf && (count - 1) > 3 && count > maxOverloadedInf) {
                    maxOverloadedInf = count;
                }
            }
        }
        int effectiveShelves = BookshelfProgression.effectiveShelvesAfterBreathingRoom(
            rawShelves,
            ceilingBlocked,
            openAdj,
            safeCfg
        );
        int effectiveTomeEnchants = BookshelfProgression.effectiveShelvesAfterBreathingRoom(
            rawTomeEnchants,
            ceilingBlocked,
            openAdj,
            safeCfg
        );
        return new LibraryRitualScan(effectiveShelves, anyTome, effectiveTomeEnchants, maxOverloadedInf);
    }

    /** Returns {@code true} if any nearby Master Tome providing {@code enchantmentId >= minLevel} carries {@code minecraft:infinity}. */
    public static boolean doesMatchingMasterTomeHaveInfinity(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config cfg,
        Identifier enchantmentId,
        int minLevel
    ) {
        if (MasterTomeWear.INFINITY_ENCHANTMENT_ID.equals(enchantmentId)) {
            return true;
        }
        if (level == null || tablePos == null || enchantmentId == null) {
            return false;
        }
        BookshelfProgression.Config safeCfg = cfg != null ? cfg : BookshelfProgression.Config.DEFAULT;
        if (safeCfg.requireBreathingRoom()) {
            boolean ceilingBlocked = ChiseledEnchantingTableBlock.isTableCeilingBlocked(level, tablePos);
            int openAdj = ChiseledEnchantingTableBlock.countOpenAdjacentBlocks(level, tablePos);
            if (BookshelfProgression.effectiveShelvesAfterBreathingRoom(1, ceilingBlocked, openAdj, safeCfg) <= 0) {
                return false;
            }
        }
        boolean checkLos = safeCfg.requireLineOfSight();
        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(safeCfg)) {
            BlockPos p = tablePos.offset(offset);
            if (!level.hasChunkAt(p)) {
                continue;
            }
            if (checkLos && !ChiseledEnchantingTableBlock.hasClearLineOfSight(level, tablePos, p)) {
                continue;
            }
            if (!(level.getBlockEntity(p) instanceof ChiseledBookShelfBlockEntity shelfBe)) {
                continue;
            }
            for (int slot = 0; slot < shelfBe.getContainerSize(); slot++) {
                ItemStack book = shelfBe.getItem(slot);
                if (book.isEmpty() || !MasterTomeWear.hasInfinityImmunity(book)) continue;
                ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(book);
                for (var entry : enchants.entrySet()) {
                    var keyOpt = entry.getKey().unwrapKey();
                    if (keyOpt.isPresent()
                        && enchantmentId.equals(keyOpt.get().identifier())
                        && entry.getIntValue() >= minLevel) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Degrades the lowest-priority Master Tome in scan range directly inside its Chiseled Bookshelf
     * slot into {@link BlockRegistry#DEPLETED_TOME} ({@code 褪魔残卷}), returning the degraded book's
     * prior stack copy (or {@code null} if no tome existed).
     */
    @Nullable
    public static ItemStack degradeLowestPriorityMasterTomeInWorld(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config cfg
    ) {
        if (level == null || tablePos == null) {
            return null;
        }
        BookshelfProgression.Config safeCfg = cfg != null ? cfg : BookshelfProgression.Config.DEFAULT;
        if (safeCfg.requireBreathingRoom()) {
            boolean ceilingBlocked = ChiseledEnchantingTableBlock.isTableCeilingBlocked(level, tablePos);
            int openAdj = ChiseledEnchantingTableBlock.countOpenAdjacentBlocks(level, tablePos);
            if (BookshelfProgression.effectiveShelvesAfterBreathingRoom(1, ceilingBlocked, openAdj, safeCfg) <= 0) {
                return null;
            }
        }
        boolean checkLos = safeCfg.requireLineOfSight();
        List<TomeCandidate> candidates = new ArrayList<>();
        for (BlockPos offset : ChiseledEnchantingTableBlock.getScanOffsets(safeCfg)) {
            BlockPos p = tablePos.offset(offset);
            if (!level.hasChunkAt(p)) {
                continue;
            }
            if (checkLos && !ChiseledEnchantingTableBlock.hasClearLineOfSight(level, tablePos, p)) {
                continue;
            }
            if (!(level.getBlockEntity(p) instanceof ChiseledBookShelfBlockEntity shelfBe)) {
                continue;
            }
            for (int slot = 0; slot < shelfBe.getContainerSize(); slot++) {
                ItemStack book = shelfBe.getItem(slot);
                if (book.isEmpty()) continue;
                ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(book);
                if (enchants.isEmpty()) continue;
                int count = enchants.size();
                double relSum = 0.0;
                double raritySum = 0.0;
                for (var entry : enchants.entrySet()) {
                    int maxLvl = Math.max(1, entry.getKey().value().getMaxLevel());
                    relSum += Mth.clamp((double) entry.getIntValue() / maxLvl, 0.05, 1.0);
                    int w = Math.max(1, entry.getKey().value().definition().weight());
                    raritySum += 1.0 / Math.min(16.0, w);
                }
                candidates.add(new TomeCandidate(
                    p.immutable(),
                    slot,
                    count,
                    relSum / count,
                    raritySum / count,
                    MasterTomeWear.hasInfinityImmunity(book)
                ));
            }
        }
        TomeCandidate victim = selectDegradationVictim(candidates);
        if (victim == null) {
            return null;
        }
        if (!(level.getBlockEntity(victim.shelfPos()) instanceof ChiseledBookShelfBlockEntity targetShelf)) {
            return null;
        }
        ItemStack destroyedCopy = targetShelf.getItem(victim.slot()).copy();
        targetShelf.setItem(victim.slot(), new ItemStack(BlockRegistry.DEPLETED_TOME, 1));
        targetShelf.setChanged();
        BlockState shelfState = level.getBlockState(victim.shelfPos());
        if (shelfState.is(Blocks.CHISELED_BOOKSHELF)
            && victim.slot() >= 0
            && victim.slot() < ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size()) {
            var occProp = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(victim.slot());
            if (shelfState.hasProperty(occProp) && !shelfState.getValue(occProp)) {
                level.setBlock(victim.shelfPos(), shelfState.setValue(occProp, true), Block.UPDATE_ALL);
            } else {
                level.sendBlockUpdated(victim.shelfPos(), shelfState, shelfState, Block.UPDATE_ALL);
            }
        }
        level.playSound(
            null,
            victim.shelfPos(),
            SoundEvents.AMETHYST_CLUSTER_BREAK,
            SoundSource.BLOCKS,
            1.0F,
            0.75F
        );
        return destroyedCopy;
    }

    /** Formats seconds cleanly as game days ({@code 10.0d}) or minutes/seconds. */
    public static String formatRegenGameDays(int seconds) {
        double days = seconds / (double) SECONDS_PER_GAME_DAY;
        if (Math.abs(days - Math.round(days)) < 0.005) {
            return Math.round(days) + "d (" + seconds + "s)";
        }
        return String.format(java.util.Locale.ROOT, "%.1fd (%ds)", days, seconds);
    }
}
