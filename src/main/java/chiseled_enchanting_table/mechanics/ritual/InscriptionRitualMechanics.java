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

    /** Single source of truth for persisted ritual-queue bounds (DATA-01). */
    public static final int MAX_QUEUE_STEPS = 256;
    public static final int MAX_QUEUE_CHARACTERS = 32_000;
    /** Default wait-curve normalization denominators (BAL-01); datapack-overridable via {@link Config}. */
    public static final double DEFAULT_SHELF_LOAD_REFERENCE = chiseled_enchanting_table.BundledDefaults.SHELF_LOAD_REFERENCE;
    public static final double DEFAULT_TOME_LOAD_REFERENCE = chiseled_enchanting_table.BundledDefaults.TOME_LOAD_REFERENCE;

    /**
     * Saturation curve of a single inscription wait (audit P0: these six numbers used
     * to be private literals that no datapack could reach). Loaded from
     * library_resonance.json ritual.wait_curve and synced in EnchantCostTablePayload.
     */
    public record WaitCurve(
        double shelfWeight,
        double tomeWeight,
        double levelFloor,
        double levelSpan,
        double levelExponent,
        double saturation,
        int minTicks
    ) {
        public static final WaitCurve DEFAULT = new WaitCurve(
            chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_SHELF_WEIGHT,
            chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_TOME_WEIGHT,
            chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_LEVEL_FLOOR,
            chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_LEVEL_SPAN,
            chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_LEVEL_EXPONENT,
            chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_SATURATION,
            chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_MIN_TICKS
        );

        public WaitCurve {
            shelfWeight = Double.isFinite(shelfWeight) ? net.minecraft.util.Mth.clamp(shelfWeight, 0.0, 10.0) : chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_SHELF_WEIGHT;
            tomeWeight = Double.isFinite(tomeWeight) ? net.minecraft.util.Mth.clamp(tomeWeight, 0.0, 10.0) : chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_TOME_WEIGHT;
            levelFloor = Double.isFinite(levelFloor) ? net.minecraft.util.Mth.clamp(levelFloor, 0.0, 10.0) : chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_LEVEL_FLOOR;
            levelSpan = Double.isFinite(levelSpan) ? net.minecraft.util.Mth.clamp(levelSpan, 0.0, 10.0) : chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_LEVEL_SPAN;
            levelExponent = Double.isFinite(levelExponent) ? net.minecraft.util.Mth.clamp(levelExponent, 0.1, 5.0) : chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_LEVEL_EXPONENT;
            saturation = Double.isFinite(saturation) ? net.minecraft.util.Mth.clamp(saturation, 0.01, 10.0) : chiseled_enchanting_table.BundledDefaults.WAIT_CURVE_SATURATION;
            minTicks = net.minecraft.util.Mth.clamp(minTicks, 1, 6_000);
        }

        static WaitCurve fromJson(@Nullable com.google.gson.JsonObject ritual) {
            if (ritual == null || !ritual.has("wait_curve") || !ritual.get("wait_curve").isJsonObject()) {
                return DEFAULT;
            }
            com.google.gson.JsonObject w = ritual.getAsJsonObject("wait_curve");
            return new WaitCurve(
                Config.doubleOr(w, "shelf_weight", DEFAULT.shelfWeight),
                Config.doubleOr(w, "tome_weight", DEFAULT.tomeWeight),
                Config.doubleOr(w, "level_floor", DEFAULT.levelFloor),
                Config.doubleOr(w, "level_span", DEFAULT.levelSpan),
                Config.doubleOr(w, "level_exponent", DEFAULT.levelExponent),
                Config.doubleOr(w, "saturation", DEFAULT.saturation),
                Config.intOr(w, "min_ticks", DEFAULT.minTicks)
            );
        }
    }

    /** Maximum wait ticks for ANY single enchantment step: 5 real minutes = 300s = 6,000 ticks. */
    public static final int MAX_SINGLE_WAIT_TICKS = 20 * chiseled_enchanting_table.BundledDefaults.MAX_SINGLE_WAIT_SECONDS;
    /** Minimum surrounding Master Tome enchantments that activate ritual waiting below the shelf threshold. */
    public static final int MIN_TOME_ENCHANTS_FOR_WAIT = chiseled_enchanting_table.BundledDefaults.MIN_TOME_ENCHANTS_FOR_WAIT;
    /** Maximum direct XP levels sacrificed when skipping a 1-step ritual via XP sacrifice. */
    public static final int MAX_SACRIFICE_XP_LEVELS = chiseled_enchanting_table.BundledDefaults.MAX_SACRIFICE_XP_LEVELS;
    public static final int DEFAULT_SACRIFICE_EXTRA_XP_PER_QUEUED_STEP = chiseled_enchanting_table.BundledDefaults.SACRIFICE_EXTRA_XP_PER_QUEUED_STEP;
    public static final double DEFAULT_SACRIFICE_XP_TIME_CONSTANT_SECONDS = chiseled_enchanting_table.BundledDefaults.SACRIFICE_XP_TIME_CONSTANT_SECONDS;
    /** Hard cap on non-sacrifice rush degradation chance normally (50%) vs with Infinity tome (70%). */
    public static final double MAX_RUSH_DEGRADE_NORMAL = chiseled_enchanting_table.BundledDefaults.MAX_RUSH_DEGRADE_NORMAL;
    public static final double MAX_RUSH_DEGRADE_INFINITY = chiseled_enchanting_table.BundledDefaults.MAX_RUSH_DEGRADE_INFINITY;
    public static final double DEFAULT_RUSH_DEGRADE_TIME_CONSTANT_SECONDS = chiseled_enchanting_table.BundledDefaults.RUSH_DEGRADE_TIME_CONSTANT_SECONDS;
    /** Hard cap on overloaded wear-immune Master Tome merged slip («抄错 / 抄串行») chance (25%). */
    public static final double MAX_TRANSCRIPTION_SLIP_CHANCE = chiseled_enchanting_table.BundledDefaults.CROSSLINE_MAX_SLIP_CHANCE;

    public static final TagKey<Enchantment> EPIPHANY_EXCLUSIVE_TAG =
        chiseled_enchanting_table.registry.CetTags.Enchantments.EPIPHANY_EXCLUSIVE;
    public static final TagKey<Enchantment> CROSSLINE_EXCLUDED_TAG =
        chiseled_enchanting_table.registry.CetTags.Enchantments.CROSSLINE_EXCLUDED;
    public static final TagKey<Enchantment> BROAD_APPLICABILITY_TAG =
        chiseled_enchanting_table.registry.CetTags.Enchantments.BROAD_APPLICABILITY;
    public static final TagKey<Enchantment> KEYSTONE_MAJOR_TAG =
        chiseled_enchanting_table.registry.CetTags.Enchantments.KEYSTONE_MAJOR;
    public static final TagKey<Enchantment> KEYSTONE_MINOR_TAG =
        chiseled_enchanting_table.registry.CetTags.Enchantments.KEYSTONE_MINOR;

    /**
     * Data-driven universality &amp; keystone applicability multipliers loaded from
     * {@code library_resonance.json -> ritual.applicability} (Audit COUP-01 / BAL-02 / BAL-03).
     */
    public record ApplicabilityConfig(
        double itemBreadthReference,
        double breadthWeight,
        double keystoneMajorMultiplier,
        double keystoneMinorMultiplier,
        double broadMultiplier,
        double rarityWeight1Multiplier,
        double rarityWeight2Multiplier,
        double rarityWeight5Multiplier,
        double minFactor,
        double maxFactor
    ) {
        public static final ApplicabilityConfig DEFAULT = new ApplicabilityConfig(
            chiseled_enchanting_table.BundledDefaults.APPLICABILITY_ITEM_BREADTH_REFERENCE,
            chiseled_enchanting_table.BundledDefaults.APPLICABILITY_BREADTH_WEIGHT,
            chiseled_enchanting_table.BundledDefaults.APPLICABILITY_KEYSTONE_MAJOR_MULTIPLIER,
            chiseled_enchanting_table.BundledDefaults.APPLICABILITY_KEYSTONE_MINOR_MULTIPLIER,
            chiseled_enchanting_table.BundledDefaults.APPLICABILITY_BROAD_MULTIPLIER,
            chiseled_enchanting_table.BundledDefaults.APPLICABILITY_RARITY_WEIGHT_1_MULTIPLIER,
            chiseled_enchanting_table.BundledDefaults.APPLICABILITY_RARITY_WEIGHT_2_MULTIPLIER,
            chiseled_enchanting_table.BundledDefaults.APPLICABILITY_RARITY_WEIGHT_5_MULTIPLIER,
            chiseled_enchanting_table.BundledDefaults.APPLICABILITY_MIN_FACTOR,
            chiseled_enchanting_table.BundledDefaults.APPLICABILITY_MAX_FACTOR
        );

        public ApplicabilityConfig {
            itemBreadthReference = Double.isFinite(itemBreadthReference) && itemBreadthReference >= 1.0
                ? Mth.clamp(itemBreadthReference, 1.0, 1024.0)
                : DEFAULT.itemBreadthReference;
            breadthWeight = Double.isFinite(breadthWeight) ? Mth.clamp(breadthWeight, 0.0, 4.0) : DEFAULT.breadthWeight;
            keystoneMajorMultiplier = Double.isFinite(keystoneMajorMultiplier)
                ? Mth.clamp(keystoneMajorMultiplier, 1.0, 5.0)
                : DEFAULT.keystoneMajorMultiplier;
            keystoneMinorMultiplier = Double.isFinite(keystoneMinorMultiplier)
                ? Mth.clamp(keystoneMinorMultiplier, 1.0, 5.0)
                : DEFAULT.keystoneMinorMultiplier;
            broadMultiplier = Double.isFinite(broadMultiplier)
                ? Mth.clamp(broadMultiplier, 1.0, 5.0)
                : DEFAULT.broadMultiplier;
            rarityWeight1Multiplier = Double.isFinite(rarityWeight1Multiplier)
                ? Mth.clamp(rarityWeight1Multiplier, 1.0, 5.0)
                : DEFAULT.rarityWeight1Multiplier;
            rarityWeight2Multiplier = Double.isFinite(rarityWeight2Multiplier)
                ? Mth.clamp(rarityWeight2Multiplier, 1.0, 5.0)
                : DEFAULT.rarityWeight2Multiplier;
            rarityWeight5Multiplier = Double.isFinite(rarityWeight5Multiplier)
                ? Mth.clamp(rarityWeight5Multiplier, 1.0, 5.0)
                : DEFAULT.rarityWeight5Multiplier;
            minFactor = Double.isFinite(minFactor) ? Mth.clamp(minFactor, 0.5, 5.0) : DEFAULT.minFactor;
            maxFactor = Double.isFinite(maxFactor) ? Mth.clamp(maxFactor, minFactor, 10.0) : Math.max(minFactor, DEFAULT.maxFactor);
        }

        static ApplicabilityConfig fromJson(@Nullable com.google.gson.JsonObject ritual) {
            if (ritual == null || !ritual.has("applicability") || !ritual.get("applicability").isJsonObject()) {
                return DEFAULT;
            }
            com.google.gson.JsonObject a = ritual.getAsJsonObject("applicability");
            return new ApplicabilityConfig(
                Config.doubleOr(a, "item_breadth_reference", DEFAULT.itemBreadthReference),
                Config.doubleOr(a, "breadth_weight", DEFAULT.breadthWeight),
                Config.doubleOr(a, "keystone_major_multiplier", DEFAULT.keystoneMajorMultiplier),
                Config.doubleOr(a, "keystone_minor_multiplier", DEFAULT.keystoneMinorMultiplier),
                Config.doubleOr(a, "broad_multiplier", DEFAULT.broadMultiplier),
                Config.doubleOr(a, "rarity_weight_1_multiplier", DEFAULT.rarityWeight1Multiplier),
                Config.doubleOr(a, "rarity_weight_2_multiplier", DEFAULT.rarityWeight2Multiplier),
                Config.doubleOr(a, "rarity_weight_5_multiplier", DEFAULT.rarityWeight5Multiplier),
                Config.doubleOr(a, "min_factor", DEFAULT.minFactor),
                Config.doubleOr(a, "max_factor", DEFAULT.maxFactor)
            );
        }
    }

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
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_MIN_LOAD_THRESHOLD,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_PER_LOAD_SLOPE,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_MAX_SLIP_CHANCE,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_SHELF_TIER1_COUNT, chiseled_enchanting_table.BundledDefaults.CROSSLINE_SHELF_TIER1_BONUS,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_SHELF_TIER2_COUNT, chiseled_enchanting_table.BundledDefaults.CROSSLINE_SHELF_TIER2_BONUS,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_SHELF_TIER3_COUNT, chiseled_enchanting_table.BundledDefaults.CROSSLINE_SHELF_TIER3_BONUS,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_TREASURE_SCORE,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_SINGLE_TREASURE_MIN_LOAD,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_HIGH_RARITY_MAX_WEIGHT, chiseled_enchanting_table.BundledDefaults.CROSSLINE_HIGH_RARITY_SCORE,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_MID_RARITY_MAX_WEIGHT, chiseled_enchanting_table.BundledDefaults.CROSSLINE_MID_RARITY_SCORE,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_BROAD_APPLICABILITY_MIN_ITEMS, chiseled_enchanting_table.BundledDefaults.CROSSLINE_BROAD_APPLICABILITY_SCORE,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_RELATIVE_LEVEL_MIN_RATIO, chiseled_enchanting_table.BundledDefaults.CROSSLINE_RELATIVE_LEVEL_SCORE,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_UNBREAKING_HIGH_MIN_LEVEL, chiseled_enchanting_table.BundledDefaults.CROSSLINE_UNBREAKING_HIGH_SCORE,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_TIER_DROP_SHARE,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_JUNK_SHARE,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_CURSE_SHARE,
            chiseled_enchanting_table.BundledDefaults.CROSSLINE_POLLUTION_REFUND_RATIO
        );

        public CrosslineSlipConfig {
            minLoadThreshold = clampFinite(minLoadThreshold, 0.5, 64.0, 3.0);
            perLoadSlope = clampFinite(perLoadSlope, 0.0, 1.0, 0.035);
            maxSlipChance = clampFinite(maxSlipChance, 0.0, 1.0, MAX_TRANSCRIPTION_SLIP_CHANCE);
            shelfTier1Count = Mth.clamp(shelfTier1Count, 1, 256);
            shelfTier1Bonus = clampFinite(shelfTier1Bonus, 0.0, 1.0, 0.02);
            shelfTier2Count = Mth.clamp(shelfTier2Count, shelfTier1Count, 256);
            shelfTier2Bonus = clampFinite(shelfTier2Bonus, 0.0, 1.0, 0.04);
            shelfTier3Count = Mth.clamp(shelfTier3Count, shelfTier2Count, 256);
            shelfTier3Bonus = clampFinite(shelfTier3Bonus, 0.0, 1.0, 0.06);
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
        double shelfLoadReference,
        double tomeLoadReference,
        CrosslineSlipConfig crosslineSlip,
        WaitCurve waitCurve,
        int sacrificeExtraXpPerQueuedStep,
        double sacrificeXpTimeConstantSeconds,
        double rushDegradeTimeConstantSeconds,
        ApplicabilityConfig applicability
    ) {
        public static final Config DEFAULT = new Config(
            MAX_SINGLE_WAIT_TICKS / 20,
            MIN_TOME_ENCHANTS_FOR_WAIT,
            MAX_SACRIFICE_XP_LEVELS,
            MAX_RUSH_DEGRADE_NORMAL,
            MAX_RUSH_DEGRADE_INFINITY,
            MAX_TRANSCRIPTION_SLIP_CHANCE,
            DEFAULT_SHELF_LOAD_REFERENCE,
            DEFAULT_TOME_LOAD_REFERENCE,
            CrosslineSlipConfig.DEFAULT,
            WaitCurve.DEFAULT,
            DEFAULT_SACRIFICE_EXTRA_XP_PER_QUEUED_STEP,
            DEFAULT_SACRIFICE_XP_TIME_CONSTANT_SECONDS,
            DEFAULT_RUSH_DEGRADE_TIME_CONSTANT_SECONDS,
            ApplicabilityConfig.DEFAULT
        );

        public Config(
            int maxSingleWaitSeconds,
            int minTomeEnchantsForWait,
            int maxSacrificeXpLevels,
            double maxRushDegradeNormal,
            double maxRushDegradeInfinity,
            double maxTranscriptionSlipChance,
            double shelfLoadReference,
            double tomeLoadReference,
            CrosslineSlipConfig crosslineSlip,
            WaitCurve waitCurve,
            int sacrificeExtraXpPerQueuedStep,
            double sacrificeXpTimeConstantSeconds,
            double rushDegradeTimeConstantSeconds
        ) {
            this(
                maxSingleWaitSeconds,
                minTomeEnchantsForWait,
                maxSacrificeXpLevels,
                maxRushDegradeNormal,
                maxRushDegradeInfinity,
                maxTranscriptionSlipChance,
                shelfLoadReference,
                tomeLoadReference,
                crosslineSlip,
                waitCurve,
                sacrificeExtraXpPerQueuedStep,
                sacrificeXpTimeConstantSeconds,
                rushDegradeTimeConstantSeconds,
                ApplicabilityConfig.DEFAULT
            );
        }

        public Config(
            int maxSingleWaitSeconds,
            int minTomeEnchantsForWait,
            int maxSacrificeXpLevels,
            double maxRushDegradeNormal,
            double maxRushDegradeInfinity,
            double maxTranscriptionSlipChance,
            double shelfLoadReference,
            double tomeLoadReference,
            CrosslineSlipConfig crosslineSlip,
            WaitCurve waitCurve
        ) {
            this(
                maxSingleWaitSeconds,
                minTomeEnchantsForWait,
                maxSacrificeXpLevels,
                maxRushDegradeNormal,
                maxRushDegradeInfinity,
                maxTranscriptionSlipChance,
                shelfLoadReference,
                tomeLoadReference,
                crosslineSlip,
                waitCurve,
                DEFAULT_SACRIFICE_EXTRA_XP_PER_QUEUED_STEP,
                DEFAULT_SACRIFICE_XP_TIME_CONSTANT_SECONDS,
                DEFAULT_RUSH_DEGRADE_TIME_CONSTANT_SECONDS
            );
        }

        public Config(
            int maxSingleWaitSeconds,
            int minTomeEnchantsForWait,
            int maxSacrificeXpLevels,
            double maxRushDegradeNormal,
            double maxRushDegradeInfinity,
            double maxTranscriptionSlipChance,
            CrosslineSlipConfig crosslineSlip
        ) {
            this(
                maxSingleWaitSeconds,
                minTomeEnchantsForWait,
                maxSacrificeXpLevels,
                maxRushDegradeNormal,
                maxRushDegradeInfinity,
                maxTranscriptionSlipChance,
                DEFAULT_SHELF_LOAD_REFERENCE,
                DEFAULT_TOME_LOAD_REFERENCE,
                crosslineSlip,
                WaitCurve.DEFAULT
            );
        }

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
                DEFAULT_SHELF_LOAD_REFERENCE,
                DEFAULT_TOME_LOAD_REFERENCE,
                CrosslineSlipConfig.fromJson(null, maxTranscriptionSlipChance),
                WaitCurve.DEFAULT
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
            shelfLoadReference = Double.isFinite(shelfLoadReference) && shelfLoadReference > 0.0
                ? Mth.clamp(shelfLoadReference, 4.0, 512.0)
                : DEFAULT_SHELF_LOAD_REFERENCE;
            tomeLoadReference = Double.isFinite(tomeLoadReference) && tomeLoadReference > 0.0
                ? Mth.clamp(tomeLoadReference, 1.0, 512.0)
                : DEFAULT_TOME_LOAD_REFERENCE;
            crosslineSlip = crosslineSlip != null ? crosslineSlip : CrosslineSlipConfig.fromJson(null, maxTranscriptionSlipChance);
            waitCurve = waitCurve != null ? waitCurve : WaitCurve.DEFAULT;
            sacrificeExtraXpPerQueuedStep = Mth.clamp(sacrificeExtraXpPerQueuedStep, 0, 64);
            sacrificeXpTimeConstantSeconds = Double.isFinite(sacrificeXpTimeConstantSeconds) && sacrificeXpTimeConstantSeconds > 0.0
                ? Mth.clamp(sacrificeXpTimeConstantSeconds, 5.0, 3_600.0)
                : DEFAULT_SACRIFICE_XP_TIME_CONSTANT_SECONDS;
            rushDegradeTimeConstantSeconds = Double.isFinite(rushDegradeTimeConstantSeconds) && rushDegradeTimeConstantSeconds > 0.0
                ? Mth.clamp(rushDegradeTimeConstantSeconds, 5.0, 3_600.0)
                : DEFAULT_RUSH_DEGRADE_TIME_CONSTANT_SECONDS;
            applicability = applicability != null ? applicability : ApplicabilityConfig.DEFAULT;
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
            com.google.gson.JsonObject csObj = root.has("crossline_slip") && root.get("crossline_slip").isJsonObject()
                ? root.getAsJsonObject("crossline_slip")
                : null;
            int maxWaitSec = r != null ? intOr(r, "max_single_wait_seconds", DEFAULT.maxSingleWaitSeconds) : DEFAULT.maxSingleWaitSeconds;
            int minTomeEnch = r != null ? intOr(r, "min_tome_enchants_for_wait", DEFAULT.minTomeEnchantsForWait) : DEFAULT.minTomeEnchantsForWait;
            int maxSacXp = r != null ? intOr(r, "max_sacrifice_xp_levels", DEFAULT.maxSacrificeXpLevels) : DEFAULT.maxSacrificeXpLevels;
            int extraSacPerStep = r != null ? intOr(r, "sacrifice_extra_xp_per_queued_step", DEFAULT.sacrificeExtraXpPerQueuedStep) : DEFAULT.sacrificeExtraXpPerQueuedStep;
            double sacTau = r != null ? doubleOr(r, "sacrifice_xp_time_constant_seconds", DEFAULT.sacrificeXpTimeConstantSeconds) : DEFAULT.sacrificeXpTimeConstantSeconds;
            double rushNorm = r != null ? doubleOr(r, "max_rush_degrade_normal", DEFAULT.maxRushDegradeNormal) : DEFAULT.maxRushDegradeNormal;
            double rushInf = r != null ? doubleOr(r, "max_rush_degrade_infinity", DEFAULT.maxRushDegradeInfinity) : DEFAULT.maxRushDegradeInfinity;
            double rushTau = r != null ? doubleOr(r, "rush_degrade_time_constant_seconds", DEFAULT.rushDegradeTimeConstantSeconds) : DEFAULT.rushDegradeTimeConstantSeconds;

            boolean hasRitualCap = r != null && r.has("max_transcription_slip_chance");
            boolean hasCrosslineCap = csObj != null && csObj.has("max_slip_chance");
            double ritualSlipCap = hasRitualCap ? doubleOr(r, "max_transcription_slip_chance", DEFAULT.maxTranscriptionSlipChance) : DEFAULT.maxTranscriptionSlipChance;
            if (hasRitualCap && hasCrosslineCap) {
                double csCap = doubleOr(csObj, "max_slip_chance", ritualSlipCap);
                if (Math.abs(ritualSlipCap - csCap) > 1e-6) {
                    ChiseledEnchantingTable.LOGGER.warn(
                        "library_resonance.json declares conflicting ritual.max_transcription_slip_chance ({}) and "
                            + "crossline_slip.max_slip_chance ({}); using canonical crossline_slip.max_slip_chance={}",
                        ritualSlipCap,
                        csCap,
                        csCap
                    );
                }
            }
            double shelfRef = r != null ? doubleOr(r, "shelf_load_reference", DEFAULT.shelfLoadReference) : DEFAULT.shelfLoadReference;
            double tomeRef = r != null ? doubleOr(r, "tome_load_reference", DEFAULT.tomeLoadReference) : DEFAULT.tomeLoadReference;
            CrosslineSlipConfig crossline = CrosslineSlipConfig.fromJson(root, ritualSlipCap);
            return new Config(
                maxWaitSec,
                minTomeEnch,
                maxSacXp,
                rushNorm,
                rushInf,
                crossline.maxSlipChance(),
                shelfRef,
                tomeRef,
                crossline,
                WaitCurve.fromJson(r),
                extraSacPerStep,
                sacTau,
                rushTau,
                ApplicabilityConfig.fromJson(r)
            );
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

    private InscriptionRitualMechanics() {}

    /** Compact persisted material cost entry for 50% cross-line pollution compensation. */
    public record PaidItemEntry(Identifier itemId, int count) {
        public static final com.mojang.serialization.Codec<PaidItemEntry> CODEC =
            com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(PaidItemEntry::itemId),
                com.mojang.serialization.Codec.intRange(1, 1024).fieldOf("count").forGetter(PaidItemEntry::count)
            ).apply(instance, PaidItemEntry::new));

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
        List<PaidItemEntry> paidItems,
        int inheritedGeneration
    ) {
        public static final com.mojang.serialization.Codec<QueuedInscription> CODEC =
            com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(QueuedInscription::enchantmentId),
                com.mojang.serialization.Codec.intRange(1, 255).fieldOf("target_level").forGetter(QueuedInscription::targetLevel),
                com.mojang.serialization.Codec.intRange(0, 255).fieldOf("previous_level").forGetter(QueuedInscription::previousLevel),
                com.mojang.serialization.Codec.BOOL.optionalFieldOf("master_tome_used", false).forGetter(QueuedInscription::masterTomeUsed),
                com.mojang.serialization.Codec.BOOL.optionalFieldOf("used_infinity_tome", false).forGetter(QueuedInscription::usedInfinityTome),
                com.mojang.serialization.Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("conflict_refund", 0).forGetter(QueuedInscription::conflictRefundPoints),
                com.mojang.serialization.Codec.BOOL.optionalFieldOf("consumed_epiphany", false).forGetter(QueuedInscription::consumedEpiphany),
                com.mojang.serialization.Codec.doubleRange(0.0, 256.0).optionalFieldOf("infinity_load", 0.0).forGetter(QueuedInscription::infinityTomeLoad),
                com.mojang.serialization.Codec.intRange(0, 1_000_000).optionalFieldOf("paid_xp", 0).forGetter(QueuedInscription::paidXpPoints),
                PaidItemEntry.CODEC.sizeLimitedListOf(64).optionalFieldOf("paid_items", List.of()).forGetter(QueuedInscription::paidItems),
                com.mojang.serialization.Codec.intRange(-1, 255).optionalFieldOf("inherited_gen", -1).forGetter(QueuedInscription::inheritedGeneration)
            ).apply(instance, QueuedInscription::new));
        public QueuedInscription {
            infinityTomeLoad = Double.isFinite(infinityTomeLoad) ? Math.max(0.0, Math.min(256.0, infinityTomeLoad)) : 0.0;
            paidXpPoints = Math.max(0, Math.min(1_000_000, paidXpPoints));
            paidItems = paidItems == null ? List.of() : List.copyOf(paidItems);
            inheritedGeneration = inheritedGeneration < 0 ? -1 : Math.min(255, inheritedGeneration);
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
                List.of(),
                -1
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
                + "|" + itemsEnc
                + "|" + inheritedGeneration;
        }

        @Nullable
        public static QueuedInscription decode(String token) {
            if (token == null || token.isBlank()) {
                return null;
            }
            String[] parts = token.split("\\|", -1);
            if ((parts.length != 7 && parts.length != 10 && parts.length != 11) || token.length() > 2048) {
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
                int inheritedGen = -1;
                if (parts.length == 11) {
                    inheritedGen = Integer.parseInt(parts[10].trim());
                    if (inheritedGen < -1 || inheritedGen > 255) {
                        return null;
                    }
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
                return new QueuedInscription(id, rawTarget, rawPrev, tomeUsed, infTome, rawRefund, epip, load, paidXp, items, inheritedGen);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    /** Structured list codec for persisted ritual steps (CODEC-02), paired with legacy ';'-string fallback. */
    public static final com.mojang.serialization.Codec<List<QueuedInscription>> STRUCTURED_QUEUE_CODEC =
        QueuedInscription.CODEC.sizeLimitedListOf(MAX_QUEUE_STEPS);

    public static final com.mojang.serialization.Codec<List<QueuedInscription>> QUEUE_CODEC =
        com.mojang.serialization.Codec.withAlternative(
            STRUCTURED_QUEUE_CODEC,
            com.mojang.serialization.Codec.STRING.comapFlatMap(
                raw -> {
                    try {
                        return com.mojang.serialization.DataResult.success(List.copyOf(deserializeQueue(raw)));
                    } catch (IllegalArgumentException e) {
                        return com.mojang.serialization.DataResult.error(e::getMessage);
                    }
                },
                InscriptionRitualMechanics::serializeQueue
            )
        );

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
        if (raw.length() > MAX_QUEUE_CHARACTERS) throw new IllegalArgumentException("Oversized persisted ritual");
        List<QueuedInscription> out = new ArrayList<>();
        long refund = 0;
        for (String part : raw.split(";", -1)) {
            if (out.size() >= MAX_QUEUE_STEPS) throw new IllegalArgumentException("Too many ritual steps");
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
        int maxOverloadedInfinityTomeEnchants,
        double totalWearImmuneLoad
    ) {
        public LibraryRitualScan(
            int totalDetectedBookshelves,
            boolean hasAnyMasterTome,
            int totalMasterTomeEnchantments,
            int maxOverloadedInfinityTomeEnchants
        ) {
            this(
                totalDetectedBookshelves,
                hasAnyMasterTome,
                totalMasterTomeEnchantments,
                maxOverloadedInfinityTomeEnchants,
                0.0
            );
        }
    }

    /** Metadata for one candidate Master Tome when resolving non-sacrifice skip degradation. */
    public record TomeCandidate(
        BlockPos shelfPos,
        int slot,
        int enchantCount,
        double averageRelativeLevel,
        double averageRarityScore,
        boolean hasInfinity,
        boolean backsActiveRitual
    ) {
        public TomeCandidate(
            BlockPos shelfPos,
            int slot,
            int enchantCount,
            double averageRelativeLevel,
            double averageRarityScore,
            boolean hasInfinity
        ) {
            this(shelfPos, slot, enchantCount, averageRelativeLevel, averageRarityScore, hasInfinity, false);
        }

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

    /**
     * Pure tag- and config-driven applicability factor reflecting enchantment universality &amp;
     * keystone impact (Audit COUP-01 / DEAD-03 / BAL-02 / BAL-03).
     *
     * <p>Replaces the legacy hardcoded {@code HIGH_APPLICABILITY_IDS} list and 3-arg bypass overload
     * with datapack-modifiable tags ({@code #keystone_major}, {@code #keystone_minor},
     * {@code #broad_applicability}) and {@code ritual.applicability} curve parameters.
     */
    public static double computeApplicabilityFactorFromTags(
        @Nullable ApplicabilityConfig appCfg,
        int supportedItemCount,
        int anvilWeight,
        boolean keystoneMajor,
        boolean keystoneMinor,
        boolean broadApplicability
    ) {
        ApplicabilityConfig a = appCfg != null ? appCfg : ApplicabilityConfig.DEFAULT;
        double itemBreadth = 1.0 + a.breadthWeight()
            * Mth.clamp((supportedItemCount - 1) / a.itemBreadthReference(), 0.0, 1.0);
        double rarityBoost = anvilWeight <= 1
            ? a.rarityWeight1Multiplier()
            : (anvilWeight <= 2
                ? a.rarityWeight2Multiplier()
                : (anvilWeight <= 5 ? a.rarityWeight5Multiplier() : 1.0));
        double keystone = keystoneMajor
            ? a.keystoneMajorMultiplier()
            : (keystoneMinor
                ? a.keystoneMinorMultiplier()
                : (broadApplicability ? a.broadMultiplier() : 1.0));
        return Mth.clamp(itemBreadth * rarityBoost * keystone, a.minFactor(), a.maxFactor());
    }

    public static double computeApplicabilityFactor(
        @Nullable Config ritualCfg,
        Holder<Enchantment> holder
    ) {
        Config c = ritualCfg != null ? ritualCfg : Config.DEFAULT;
        int supportedCount = holder.value().definition().supportedItems().size();
        int weight = holder.value().definition().weight();
        return computeApplicabilityFactorFromTags(
            c.applicability(),
            supportedCount,
            weight,
            holder.is(KEYSTONE_MAJOR_TAG),
            holder.is(KEYSTONE_MINOR_TAG),
            holder.is(BROAD_APPLICABILITY_TAG)
        );
    }

    public static double computeApplicabilityFactor(Identifier enchantmentId, Holder<Enchantment> holder) {
        return computeApplicabilityFactor(Config.DEFAULT, holder);
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
        double shelfLoad = Math.max(0.0, nearbyBookshelves) / c.shelfLoadReference();
        double tomeLoad = Math.max(0.0, totalMasterTomeEnchantments) / c.tomeLoadReference();
        WaitCurve w = c.waitCurve();
        double intensity = (w.shelfWeight() * shelfLoad + w.tomeWeight() * tomeLoad)
            * (w.levelFloor() + w.levelSpan() * Math.pow(relLevel, w.levelExponent()))
            * Math.max(1.0, applicabilityFactor);
        double maxSec = c.maxSingleWaitSeconds();
        double seconds = maxSec * (1.0 - Math.exp(-w.saturation() * intensity));
        int ticks = (int) Math.round(seconds * 20.0);
        return Mth.clamp(ticks, w.minTicks(), c.maxSingleWaitTicks());
    }

    /**
     * Pure completion-time guard for queued inscriptions (audit C-01): a step priced against the
     * virtual planned item may only be written when the <b>actual</b> current level still equals
     * the planned previous level and the effective target is a strict upgrade. Otherwise the
     * step must be skipped (and its unexecuted cost refunded) instead of jumping levels.
     */
    public static boolean isQueuedStepStillApplicable(
        int actualPreviousLevel,
        int plannedPreviousLevel,
        int effectiveTargetLevel
    ) {
        return actualPreviousLevel == plannedPreviousLevel && effectiveTargetLevel > actualPreviousLevel;
    }

    /** Clamps a (possibly legacy/over-leveled) queued target to the live definition maximum. */
    public static int effectiveQueuedTargetLevel(int queuedTargetLevel, int definitionMaxLevel) {
        return Math.max(1, Math.min(Math.max(1, queuedTargetLevel), Math.max(1, definitionMaxLevel)));
    }

    /**
     * Direct XP level sacrifice cost ({@code 1..maxSacrificeXpLevels}) derived smoothly from live remaining ticks.
     */

    public static int computeSacrificeXpLevels(@Nullable Config ritualCfg, long remainingTicks) {
        return computeSacrificeXpLevels(ritualCfg, remainingTicks, 1);
    }

    /**
     * Direct XP level sacrifice cost derived smoothly from live remaining ticks and queued step count,
     * scaling the sacrifice ceiling with queued step count so multi-step mega-queues cannot be skipped cheaply.
     */
    public static int computeSacrificeXpLevels(@Nullable Config ritualCfg, long remainingTicks, int queuedStepCount) {
        if (remainingTicks <= 0L) {
            return 0;
        }
        Config c = ritualCfg != null ? ritualCfg : Config.DEFAULT;
        int extraSteps = Math.max(0, Math.max(1, queuedStepCount) - 1);
        int cap = Math.min(255, c.maxSacrificeXpLevels() + extraSteps * c.sacrificeExtraXpPerQueuedStep());
        double tau = Math.max(1.0, c.sacrificeXpTimeConstantSeconds() * (1.0 + 0.35 * extraSteps));
        double remSeconds = remainingTicks / 20.0;
        int raw = (int) Math.ceil(cap * (1.0 - Math.exp(-remSeconds / tau)));
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
        double tau = Math.max(1.0, c.rushDegradeTimeConstantSeconds());
        double remSeconds = remainingTicks / 20.0;
        double raw = cap * (1.0 - Math.exp(-remSeconds / tau));
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

    /** Pure library-wide wear-immune overload sum {@code Sigma L} across all active wear-immune Master Tomes. */
    public static double computeLibraryWearImmuneLoad(
        @Nullable CrosslineSlipConfig slipCfg,
        List<List<TraitLoadInput>> activeWearImmuneTomes
    ) {
        if (activeWearImmuneTomes == null || activeWearImmuneTomes.isEmpty()) {
            return 0.0;
        }
        CrosslineSlipConfig c = slipCfg != null ? slipCfg : CrosslineSlipConfig.DEFAULT;
        double sum = 0.0;
        for (List<TraitLoadInput> traits : activeWearImmuneTomes) {
            sum += computeInfinityTomeQualificationLoad(c, traits);
        }
        return sum;
    }

    /** Live-stack overload computing qualification load {@code L} directly on a chosen wear-immune Master Tome stack. */
    public static double computeInfinityTomeQualificationLoad(
        @Nullable Config ritualCfg,
        ItemStack tomeStack
    ) {
        return computeInfinityTomeQualificationLoad(
            ritualCfg,
            chiseled_enchanting_table.mechanics.resonance.LibraryResonance.activeServerConfig(),
            tomeStack
        );
    }

    public static double computeInfinityTomeQualificationLoad(
        @Nullable Config ritualCfg,
        @Nullable chiseled_enchanting_table.mechanics.resonance.LibraryResonance.Config resonanceCfg,
        ItemStack tomeStack
    ) {
        if (tomeStack == null || tomeStack.isEmpty() || !MasterTomeWear.hasEffectiveWearImmunity(resonanceCfg, tomeStack)) {
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
            boolean isInf = chiseled_enchanting_table.mechanics.resonance.TomeWearData.isWearImmuneEnchantment(h);
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
     * {@code P = min(maxSlipChance, 0.035 * (L - 3) + shelfBonus)} when {@code L >= 3}, and {@code 0%} when
     * {@code L < 3}; the cap and shelf bonuses come from {@code crossline_slip} (bundled cap 25%, bonuses +2/+4/+6%).
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
     * Full (100%) material refund stacks for a queued step that never executed because its
     * planned precondition no longer holds at completion time (audit C-01). Unlike pollution
     * compensation this is not a partial consolation: nothing was inscribed for this receipt.
     */
    public static List<ItemStack> fullMaterialRefundStacks(List<PaidItemEntry> paidItems) {
        if (paidItems == null || paidItems.isEmpty()) {
            return List.of();
        }
        List<ItemStack> refunded = new ArrayList<>();
        for (PaidItemEntry entry : paidItems) {
            if (entry == null || entry.count() <= 0) continue;
            Item item = BuiltInRegistries.ITEM.getValue(entry.itemId());
            if (item == Items.AIR) continue;
            int remaining = entry.count();
            while (remaining > 0) {
                int batch = Math.min(remaining, Math.max(1, item.getDefaultMaxStackSize()));
                refunded.add(new ItemStack(item, batch));
                remaining -= batch;
            }
        }
        return List.copyOf(refunded);
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
     * Selects the highest-priority degradation victim:
     * <ol>
     *   <li>Master Tomes backing an enchantment active in the current ritual queue ALWAYS degrade
     *       before unrelated books (eliminating junk-book scapegoat shielding);</li>
     *   <li>Within that group, mortal Master Tomes degrade before wear-immune Master Tomes;</li>
     *   <li>Ties break by minimum preservation score (lowest level, fewest enchants, lowest rarity).</li>
     * </ol>
     */
    @Nullable
    public static TomeCandidate selectDegradationVictim(List<TomeCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
            .min(Comparator
                .comparing((TomeCandidate c) -> !c.backsActiveRitual())
                .thenComparing(TomeCandidate::hasInfinity)
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
            return new LibraryRitualScan(0, false, 0, 0, 0.0);
        }
        BookshelfProgression.Config safeCfg = cfg != null ? cfg : BookshelfProgression.Config.DEFAULT;
        boolean ceilingBlocked = ChiseledEnchantingTableBlock.isTableCeilingBlocked(level, tablePos);
        int openAdj = ChiseledEnchantingTableBlock.countOpenAdjacentBlocks(level, tablePos);
        if (safeCfg.requireBreathingRoom()
            && BookshelfProgression.effectiveShelvesAfterBreathingRoom(1, ceilingBlocked, openAdj, safeCfg) <= 0) {
            return new LibraryRitualScan(0, false, 0, 0, 0.0);
        }

        var lrCfg = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.configForSide(level.isClientSide());
        var ritCfg = level.isClientSide()
            ? chiseled_enchanting_table.compat.SyncedEnchantmentCosts.ritualConfig()
            : chiseled_enchanting_table.data.ChiseledEnchantingTableData.ritualConfig();

        int rawShelves = 0;
        boolean anyTome = false;
        int rawTomeEnchants = 0;
        int maxOverloadedInf = 0;
        double totalWearImmuneLoad = 0.0;
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
                if (chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
                    .classifyBookshelf(true, false, false, false).isBroadBookshelf()) {
                    rawShelves++;
                }
                continue;
            }
            if (!state.is(Blocks.CHISELED_BOOKSHELF)) {
                continue;
            }
            if (!(level.getBlockEntity(p) instanceof ChiseledBookShelfBlockEntity shelfBe)) {
                boolean occupiedProp = false;
                for (var prop : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) {
                    if (state.hasProperty(prop) && state.getValue(prop)) {
                        occupiedProp = true;
                        break;
                    }
                }
                if (chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
                    .classifyBookshelf(false, true, occupiedProp, false).isBroadBookshelf()) {
                    rawShelves++;
                }
                continue;
            }
            boolean anyBookInShelf = false;
            boolean anyEnchantedInShelf = false;
            for (int slot = 0; slot < shelfBe.getContainerSize(); slot++) {
                ItemStack book = shelfBe.getItem(slot);
                if (book.isEmpty()) continue;
                anyBookInShelf = true;
                ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(book);
                int count = enchants.size();
                if (count <= 0) continue;
                anyEnchantedInShelf = true;
                if (chiseled_enchanting_table.config.ModRuntimeConfig.forSide(level.isClientSide()).tomeLocksEnabled()
                    && chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(book)) {
                    continue;
                }
                anyTome = true;
                rawTomeEnchants += count;
                boolean hasInf = MasterTomeWear.hasEffectiveWearImmunity(lrCfg, book);
                if (hasInf) {
                    totalWearImmuneLoad += computeInfinityTomeQualificationLoad(ritCfg, lrCfg, book);
                    if ((count - 1) > 3 && count > maxOverloadedInf) {
                        maxOverloadedInf = count;
                    }
                }
            }
            if (chiseled_enchanting_table.mechanics.loot.DepletedTomeWorldMechanics
                .classifyBookshelf(false, true, anyBookInShelf, anyEnchantedInShelf).isBroadBookshelf()) {
                rawShelves++;
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
        return new LibraryRitualScan(effectiveShelves, anyTome, effectiveTomeEnchants, maxOverloadedInf, totalWearImmuneLoad);
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
        return degradeLowestPriorityMasterTomeInWorld(level, tablePos, cfg, Set.of());
    }

    @Nullable
    public static ItemStack degradeLowestPriorityMasterTomeInWorld(
        Level level,
        BlockPos tablePos,
        BookshelfProgression.Config cfg,
        @Nullable Set<Identifier> activeRitualEnchantments
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
        var lrCfg = chiseled_enchanting_table.mechanics.resonance.LibraryResonance.configForSide(level.isClientSide());
        Set<Identifier> activeIds = activeRitualEnchantments != null ? activeRitualEnchantments : Set.of();
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
                if (book.isEmpty()
                    || (chiseled_enchanting_table.config.ModRuntimeConfig.forSide(level.isClientSide()).tomeLocksEnabled()
                        && chiseled_enchanting_table.mechanics.resonance.TomeLockData.isEffectivelyLocked(book))) continue;
                ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(book);
                if (enchants.isEmpty()) continue;
                int count = enchants.size();
                double relSum = 0.0;
                double raritySum = 0.0;
                boolean backsActive = false;
                for (var entry : enchants.entrySet()) {
                    if (entry.getIntValue() > 0) {
                        var keyOpt = entry.getKey().unwrapKey();
                        if (keyOpt.isPresent() && activeIds.contains(keyOpt.get().identifier())) {
                            backsActive = true;
                        }
                    }
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
                    MasterTomeWear.hasEffectiveWearImmunity(lrCfg, book),
                    backsActive
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
    // Audit C-01: this configuration owns its wire layout. EnchantCostTablePayload delegates
    // here so field order lives next to the record definition instead of as anonymous
    // positional locals in the payload; the byte order itself is unchanged (no revision bump).
    public static void writeWire(net.minecraft.network.RegistryFriendlyByteBuf buf, Config cfg) {
        buf.writeVarInt(cfg.maxSingleWaitSeconds());
        buf.writeVarInt(cfg.minTomeEnchantsForWait());
        buf.writeVarInt(cfg.maxSacrificeXpLevels());
        buf.writeDouble(cfg.maxRushDegradeNormal());
        buf.writeDouble(cfg.maxRushDegradeInfinity());
        buf.writeDouble(cfg.maxTranscriptionSlipChance());
        buf.writeDouble(cfg.shelfLoadReference());
        buf.writeDouble(cfg.tomeLoadReference());
        buf.writeVarInt(cfg.sacrificeExtraXpPerQueuedStep());
        buf.writeDouble(cfg.sacrificeXpTimeConstantSeconds());
        buf.writeDouble(cfg.rushDegradeTimeConstantSeconds());
        var cs = cfg.crosslineSlip();
        buf.writeDouble(cs.minLoadThreshold());
        buf.writeDouble(cs.perLoadSlope());
        buf.writeDouble(cs.maxSlipChance());
        buf.writeVarInt(cs.shelfTier1Count());
        buf.writeDouble(cs.shelfTier1Bonus());
        buf.writeVarInt(cs.shelfTier2Count());
        buf.writeDouble(cs.shelfTier2Bonus());
        buf.writeVarInt(cs.shelfTier3Count());
        buf.writeDouble(cs.shelfTier3Bonus());
        buf.writeDouble(cs.treasureScore());
        buf.writeDouble(cs.singleTreasureMinLoad());
        buf.writeVarInt(cs.highRarityMaxWeight());
        buf.writeDouble(cs.highRarityScore());
        buf.writeVarInt(cs.midRarityMaxWeight());
        buf.writeDouble(cs.midRarityScore());
        buf.writeVarInt(cs.broadApplicabilityMinItems());
        buf.writeDouble(cs.broadApplicabilityScore());
        buf.writeDouble(cs.relativeLevelMinRatio());
        buf.writeDouble(cs.relativeLevelScore());
        buf.writeVarInt(cs.unbreakingHighMinLevel());
        buf.writeDouble(cs.unbreakingHighScore());
        buf.writeDouble(cs.tierDropShare());
        buf.writeDouble(cs.junkCrosslineShare());
        buf.writeDouble(cs.curseCrosslineShare());
        buf.writeDouble(cs.pollutionRefundRatio());
        var wc = cfg.waitCurve();
        buf.writeDouble(wc.shelfWeight());
        buf.writeDouble(wc.tomeWeight());
        buf.writeDouble(wc.levelFloor());
        buf.writeDouble(wc.levelSpan());
        buf.writeDouble(wc.levelExponent());
        buf.writeDouble(wc.saturation());
        buf.writeVarInt(wc.minTicks());
        var app = cfg.applicability();
        buf.writeDouble(app.itemBreadthReference());
        buf.writeDouble(app.breadthWeight());
        buf.writeDouble(app.keystoneMajorMultiplier());
        buf.writeDouble(app.keystoneMinorMultiplier());
        buf.writeDouble(app.broadMultiplier());
        buf.writeDouble(app.rarityWeight1Multiplier());
        buf.writeDouble(app.rarityWeight2Multiplier());
        buf.writeDouble(app.rarityWeight5Multiplier());
        buf.writeDouble(app.minFactor());
        buf.writeDouble(app.maxFactor());
    }

    public static Config readWire(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        int rcMaxWait = buf.readVarInt();
        int rcMinTome = buf.readVarInt();
        int rcMaxSac = buf.readVarInt();
        double rcRushNorm = buf.readDouble();
        double rcRushInf = buf.readDouble();
        double rcSlipCap = buf.readDouble();
        double rcShelfRef = buf.readDouble();
        double rcTomeRef = buf.readDouble();
        int rcExtraSacPerStep = buf.readVarInt();
        double rcSacTau = buf.readDouble();
        double rcRushTau = buf.readDouble();
        var csConfig = new CrosslineSlipConfig(
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble()
        );
        var wcConfig = new WaitCurve(
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readVarInt()
        );
        var appConfig = new ApplicabilityConfig(
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble()
        );
        return new Config(
            rcMaxWait, rcMinTome, rcMaxSac, rcRushNorm, rcRushInf, rcSlipCap, rcShelfRef, rcTomeRef,
            csConfig, wcConfig,
            rcExtraSacPerStep, rcSacTau, rcRushTau,
            appConfig
        );
    }
}
