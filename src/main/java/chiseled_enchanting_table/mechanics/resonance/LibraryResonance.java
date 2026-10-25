package chiseled_enchanting_table.mechanics.resonance;

import chiseled_enchanting_table.ChiseledEnchantingTable;
import chiseled_enchanting_table.compat.SyncedEnchantmentCosts;
import chiseled_enchanting_table.data.ChiseledEnchantingTableData;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/**
 * Relative-mastery math shared by Library XP Discounts, Master Tome specific bonuses,
 * and Treasure Epiphany proc rate / simultaneous slots / rolled tier.
 *
 * <p>Every parameter is parsed directly from the nested structure in
 * {@code data/chiseled_enchanting_table/chiseled_enchanting_table/library_resonance.json}
 * ({@code wooden_bookshelves}, {@code chiseled_mastery}, {@code epiphany_affinity}, with flat
 * root keys also accepted as fallback overrides) and synced to clients in
 * {@link chiseled_enchanting_table.menu.EnchantCostTablePayload}.</p>
 */
public final class LibraryResonance {
    // Generated from library_resonance.json (single source of truth); see BundledDefaults.
    public static final int MIN_DISCOUNT_BOOKSHELVES = chiseled_enchanting_table.BundledDefaults.MIN_DISCOUNT_BOOKSHELVES;
    public static final int MAX_WOODEN_DISCOUNT_PERCENT = chiseled_enchanting_table.BundledDefaults.MAX_WOODEN_DISCOUNT_PERCENT;
    public static final int MAX_GLOBAL_MASTERY_DISCOUNT_PERCENT = chiseled_enchanting_table.BundledDefaults.MAX_GLOBAL_MASTERY_DISCOUNT_PERCENT;
    // NOTE (audit BAL-02): with the shipped defaults MAX_BASE == wooden + mastery, so this
    // layer only starts to bite when a datapack raises either sub-cap; that is intentional
    // (it is the datapack-visible brake on the sum), and the value stays independently settable.
    public static final int MAX_BASE_XP_DISCOUNT_PERCENT = chiseled_enchanting_table.BundledDefaults.MAX_BASE_XP_DISCOUNT_PERCENT;
    public static final int MAX_SPECIFIC_ENCHANT_BONUS_PERCENT = chiseled_enchanting_table.BundledDefaults.MAX_SPECIFIC_ENCHANT_BONUS_PERCENT;
    public static final int MAX_TOTAL_XP_DISCOUNT_PERCENT = chiseled_enchanting_table.BundledDefaults.MAX_TOTAL_XP_DISCOUNT_PERCENT;

    private static final TagKey<Enchantment> CURSE_TAG = chiseled_enchanting_table.registry.CetTags.Enchantments.CURSE;

    /** Nested section {@code wooden_bookshelves} in {@code library_resonance.json} (Audit COUP-02). */
    public record WoodenBookshelves(
        int minDiscountBookshelves,
        int maxDiscountBookshelves,
        int maxWoodenDiscountPercent,
        double shelfUpgradeMaterialMultiplier
    ) {
        public static final WoodenBookshelves DEFAULT = new WoodenBookshelves(
            chiseled_enchanting_table.BundledDefaults.MIN_DISCOUNT_BOOKSHELVES,
            chiseled_enchanting_table.BundledDefaults.MAX_DISCOUNT_BOOKSHELVES,
            chiseled_enchanting_table.BundledDefaults.MAX_WOODEN_DISCOUNT_PERCENT,
            chiseled_enchanting_table.BundledDefaults.SHELF_UPGRADE_MATERIAL_MULTIPLIER
        );

        public static final Codec<WoodenBookshelves> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("min_discount_bookshelves", DEFAULT.minDiscountBookshelves).forGetter(WoodenBookshelves::minDiscountBookshelves),
            Codec.INT.optionalFieldOf("max_discount_bookshelves", DEFAULT.maxDiscountBookshelves).forGetter(WoodenBookshelves::maxDiscountBookshelves),
            Codec.INT.optionalFieldOf("max_wooden_discount_percent", DEFAULT.maxWoodenDiscountPercent).forGetter(WoodenBookshelves::maxWoodenDiscountPercent),
            Codec.DOUBLE.optionalFieldOf("shelf_upgrade_material_multiplier", DEFAULT.shelfUpgradeMaterialMultiplier).forGetter(WoodenBookshelves::shelfUpgradeMaterialMultiplier)
        ).apply(instance, WoodenBookshelves::new));

        public WoodenBookshelves {
            minDiscountBookshelves = Config.clampInt(minDiscountBookshelves, 0, 256);
            maxDiscountBookshelves = Config.clampInt(maxDiscountBookshelves, Math.max(1, minDiscountBookshelves + 1), 256);
            maxWoodenDiscountPercent = Config.clampInt(maxWoodenDiscountPercent, 0, 95);
            shelfUpgradeMaterialMultiplier = Config.clampDouble(shelfUpgradeMaterialMultiplier, 0.05, 1.0);
        }
    }

    /** Mastery discount & overload-waiver half of {@code chiseled_mastery} (13 fields, within DFU P16). */
    public record MasteryScaling(
        double masteryUnitExponent,
        double globalMasteryMultiplier,
        int maxGlobalMasteryDiscountPercent,
        int maxBaseXpDiscountPercent,
        int maxSpecificEnchantBonusPercent,
        double specificEnchantExponent,
        int maxTotalXpDiscountPercent,
        double masterTomeUpgradeMaterialMultiplier,
        double incrementalStepCreditFactor,
        double maxMasteryUnitsPerSlot,
        int wearImmuneMaxGeneration,
        int wearImmuneFullWaiverMaxEnchants,
        double wearImmuneOverloadWaiverDecay
    ) {
        public static final MasteryScaling DEFAULT = new MasteryScaling(
            chiseled_enchanting_table.BundledDefaults.MASTERY_UNIT_EXPONENT,
            chiseled_enchanting_table.BundledDefaults.GLOBAL_MASTERY_MULTIPLIER,
            chiseled_enchanting_table.BundledDefaults.MAX_GLOBAL_MASTERY_DISCOUNT_PERCENT,
            chiseled_enchanting_table.BundledDefaults.MAX_BASE_XP_DISCOUNT_PERCENT,
            chiseled_enchanting_table.BundledDefaults.MAX_SPECIFIC_ENCHANT_BONUS_PERCENT,
            chiseled_enchanting_table.BundledDefaults.SPECIFIC_ENCHANT_EXPONENT,
            chiseled_enchanting_table.BundledDefaults.MAX_TOTAL_XP_DISCOUNT_PERCENT,
            chiseled_enchanting_table.BundledDefaults.MASTER_TOME_UPGRADE_MATERIAL_MULTIPLIER,
            chiseled_enchanting_table.BundledDefaults.INCREMENTAL_STEP_CREDIT_FACTOR,
            chiseled_enchanting_table.BundledDefaults.MAX_MASTERY_UNITS_PER_SLOT,
            chiseled_enchanting_table.BundledDefaults.WEAR_IMMUNE_MAX_GENERATION,
            chiseled_enchanting_table.BundledDefaults.WEAR_IMMUNE_FULL_WAIVER_MAX_ENCHANTS,
            chiseled_enchanting_table.BundledDefaults.WEAR_IMMUNE_OVERLOAD_WAIVER_DECAY
        );

        public static final MapCodec<MasteryScaling> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("mastery_unit_exponent", DEFAULT.masteryUnitExponent).forGetter(MasteryScaling::masteryUnitExponent),
            Codec.DOUBLE.optionalFieldOf("global_mastery_multiplier", DEFAULT.globalMasteryMultiplier).forGetter(MasteryScaling::globalMasteryMultiplier),
            Codec.INT.optionalFieldOf("max_global_mastery_discount_percent", DEFAULT.maxGlobalMasteryDiscountPercent).forGetter(MasteryScaling::maxGlobalMasteryDiscountPercent),
            Codec.INT.optionalFieldOf("max_base_xp_discount_percent", DEFAULT.maxBaseXpDiscountPercent).forGetter(MasteryScaling::maxBaseXpDiscountPercent),
            Codec.INT.optionalFieldOf("max_specific_enchant_bonus_percent", DEFAULT.maxSpecificEnchantBonusPercent).forGetter(MasteryScaling::maxSpecificEnchantBonusPercent),
            Codec.DOUBLE.optionalFieldOf("specific_enchant_exponent", DEFAULT.specificEnchantExponent).forGetter(MasteryScaling::specificEnchantExponent),
            Codec.INT.optionalFieldOf("max_total_xp_discount_percent", DEFAULT.maxTotalXpDiscountPercent).forGetter(MasteryScaling::maxTotalXpDiscountPercent),
            Codec.DOUBLE.optionalFieldOf("master_tome_upgrade_material_multiplier", DEFAULT.masterTomeUpgradeMaterialMultiplier).forGetter(MasteryScaling::masterTomeUpgradeMaterialMultiplier),
            Codec.DOUBLE.optionalFieldOf("incremental_step_credit_factor", DEFAULT.incrementalStepCreditFactor).forGetter(MasteryScaling::incrementalStepCreditFactor),
            Codec.DOUBLE.optionalFieldOf("max_mastery_units_per_slot", DEFAULT.maxMasteryUnitsPerSlot).forGetter(MasteryScaling::maxMasteryUnitsPerSlot),
            Codec.INT.optionalFieldOf("wear_immune_max_generation", DEFAULT.wearImmuneMaxGeneration).forGetter(MasteryScaling::wearImmuneMaxGeneration),
            Codec.INT.optionalFieldOf("wear_immune_full_waiver_max_enchants", DEFAULT.wearImmuneFullWaiverMaxEnchants).forGetter(MasteryScaling::wearImmuneFullWaiverMaxEnchants),
            Codec.DOUBLE.optionalFieldOf("wear_immune_overload_waiver_decay", DEFAULT.wearImmuneOverloadWaiverDecay).forGetter(MasteryScaling::wearImmuneOverloadWaiverDecay)
        ).apply(instance, MasteryScaling::new));

        public MasteryScaling {
            masteryUnitExponent = Config.clampDouble(masteryUnitExponent, 0.25, 4.0);
            globalMasteryMultiplier = Config.clampDouble(globalMasteryMultiplier, 0.0, 20.0);
            maxTotalXpDiscountPercent = Config.clampInt(maxTotalXpDiscountPercent, 0, 95);
            if (maxGlobalMasteryDiscountPercent > maxTotalXpDiscountPercent
                || maxBaseXpDiscountPercent > maxTotalXpDiscountPercent
                || maxSpecificEnchantBonusPercent > maxTotalXpDiscountPercent) {
                ChiseledEnchantingTable.LOGGER.warn(
                    "library_resonance.json chiseled_mastery sub-cap(s) ({}/{}/{}) exceed max_total_xp_discount_percent ({}); clamping to total ceiling",
                    maxGlobalMasteryDiscountPercent,
                    maxBaseXpDiscountPercent,
                    maxSpecificEnchantBonusPercent,
                    maxTotalXpDiscountPercent
                );
            }
            maxGlobalMasteryDiscountPercent = Config.clampInt(maxGlobalMasteryDiscountPercent, 0, maxTotalXpDiscountPercent);
            maxBaseXpDiscountPercent = Config.clampInt(maxBaseXpDiscountPercent, 0, maxTotalXpDiscountPercent);
            maxSpecificEnchantBonusPercent = Config.clampInt(maxSpecificEnchantBonusPercent, 0, maxTotalXpDiscountPercent);
            specificEnchantExponent = Config.clampDouble(specificEnchantExponent, 0.25, 4.0);
            masterTomeUpgradeMaterialMultiplier = Config.clampDouble(masterTomeUpgradeMaterialMultiplier, 0.05, 1.0);
            incrementalStepCreditFactor = Config.clampDouble(incrementalStepCreditFactor, 0.0, 0.95);
            maxMasteryUnitsPerSlot = Config.clampDouble(maxMasteryUnitsPerSlot, 0.0, 256.0);
            wearImmuneMaxGeneration = Config.clampInt(wearImmuneMaxGeneration, 0, TomeWearData.MAX_GENERATION);
            wearImmuneFullWaiverMaxEnchants = Config.clampInt(wearImmuneFullWaiverMaxEnchants, 1, 64);
            wearImmuneOverloadWaiverDecay = Config.clampDouble(wearImmuneOverloadWaiverDecay, 0.0, 1.0);
        }
    }

    /** Wear, Unbreaking waiver & passive regeneration half of {@code chiseled_mastery} (13 fields). */
    public record TomeLifecycle(
        MasterTomeWear.ConsumptionMode tomeConsumptionMode,
        int maxTomeUses,
        int bookCopyWearCost,
        double unbreakingWaiverPerLevel,
        double unbreakingWaiverMaxChance,
        MasterTomeWear.DepleteBehavior tomeDepleteBehavior,
        boolean tomeRegenEnabled,
        int tomeRegenBaseSeconds,
        int tomeRegenTier1Shelves,
        int tomeRegenTier1Seconds,
        int tomeRegenTier2Shelves,
        int tomeRegenTier2Seconds,
        TomeWearData.ScapegoatPricing scapegoatPricing
    ) {
        public static final TomeLifecycle DEFAULT = new TomeLifecycle(
            MasterTomeWear.ConsumptionMode.fromToken(chiseled_enchanting_table.BundledDefaults.TOME_CONSUMPTION_MODE),
            chiseled_enchanting_table.BundledDefaults.MAX_TOME_USES,
            chiseled_enchanting_table.BundledDefaults.BOOK_COPY_WEAR_COST,
            chiseled_enchanting_table.BundledDefaults.UNBREAKING_WAIVER_PER_LEVEL,
            chiseled_enchanting_table.BundledDefaults.UNBREAKING_WAIVER_MAX_CHANCE,
            MasterTomeWear.DepleteBehavior.fromToken(chiseled_enchanting_table.BundledDefaults.TOME_DEPLETE_BEHAVIOR),
            chiseled_enchanting_table.BundledDefaults.TOME_REGEN_ENABLED,
            chiseled_enchanting_table.BundledDefaults.TOME_REGEN_BASE_SECONDS,
            chiseled_enchanting_table.BundledDefaults.TOME_REGEN_TIER1_SHELVES,
            chiseled_enchanting_table.BundledDefaults.TOME_REGEN_TIER1_SECONDS,
            chiseled_enchanting_table.BundledDefaults.TOME_REGEN_TIER2_SHELVES,
            chiseled_enchanting_table.BundledDefaults.TOME_REGEN_TIER2_SECONDS,
            TomeWearData.ScapegoatPricing.fromToken(chiseled_enchanting_table.BundledDefaults.SCAPEGOAT_PRICING)
        );

        public static final MapCodec<TomeLifecycle> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(MasterTomeWear.ConsumptionMode::fromToken, MasterTomeWear.ConsumptionMode::token)
                .optionalFieldOf("tome_consumption_mode", DEFAULT.tomeConsumptionMode).forGetter(TomeLifecycle::tomeConsumptionMode),
            Codec.INT.optionalFieldOf("max_tome_uses", DEFAULT.maxTomeUses).forGetter(TomeLifecycle::maxTomeUses),
            Codec.INT.optionalFieldOf("book_copy_wear_cost", DEFAULT.bookCopyWearCost).forGetter(TomeLifecycle::bookCopyWearCost),
            Codec.DOUBLE.optionalFieldOf("unbreaking_waiver_per_level", DEFAULT.unbreakingWaiverPerLevel).forGetter(TomeLifecycle::unbreakingWaiverPerLevel),
            Codec.DOUBLE.optionalFieldOf("unbreaking_waiver_max_chance", DEFAULT.unbreakingWaiverMaxChance).forGetter(TomeLifecycle::unbreakingWaiverMaxChance),
            Codec.STRING.xmap(MasterTomeWear.DepleteBehavior::fromToken, MasterTomeWear.DepleteBehavior::token)
                .optionalFieldOf("tome_deplete_behavior", DEFAULT.tomeDepleteBehavior).forGetter(TomeLifecycle::tomeDepleteBehavior),
            Codec.BOOL.optionalFieldOf("tome_regen_enabled", DEFAULT.tomeRegenEnabled).forGetter(TomeLifecycle::tomeRegenEnabled),
            Codec.INT.optionalFieldOf("tome_regen_base_seconds", DEFAULT.tomeRegenBaseSeconds).forGetter(TomeLifecycle::tomeRegenBaseSeconds),
            Codec.INT.optionalFieldOf("tome_regen_tier1_shelves", DEFAULT.tomeRegenTier1Shelves).forGetter(TomeLifecycle::tomeRegenTier1Shelves),
            Codec.INT.optionalFieldOf("tome_regen_tier1_seconds", DEFAULT.tomeRegenTier1Seconds).forGetter(TomeLifecycle::tomeRegenTier1Seconds),
            Codec.INT.optionalFieldOf("tome_regen_tier2_shelves", DEFAULT.tomeRegenTier2Shelves).forGetter(TomeLifecycle::tomeRegenTier2Shelves),
            Codec.INT.optionalFieldOf("tome_regen_tier2_seconds", DEFAULT.tomeRegenTier2Seconds).forGetter(TomeLifecycle::tomeRegenTier2Seconds),
            Codec.STRING.xmap(TomeWearData.ScapegoatPricing::fromToken, TomeWearData.ScapegoatPricing::token)
                .optionalFieldOf("scapegoat_pricing", DEFAULT.scapegoatPricing).forGetter(TomeLifecycle::scapegoatPricing)
        ).apply(instance, TomeLifecycle::new));

        public TomeLifecycle {
            tomeConsumptionMode = tomeConsumptionMode != null ? tomeConsumptionMode : MasterTomeWear.ConsumptionMode.WEAR;
            scapegoatPricing = scapegoatPricing != null ? scapegoatPricing : TomeWearData.ScapegoatPricing.ESSENCE;
            maxTomeUses = Config.clampInt(maxTomeUses, 1, 1024);
            bookCopyWearCost = Config.clampInt(bookCopyWearCost, 1, 1024);
            unbreakingWaiverPerLevel = Config.clampDouble(unbreakingWaiverPerLevel, 0.0, 0.30);
            unbreakingWaiverMaxChance = Config.clampDouble(unbreakingWaiverMaxChance, 0.0, 0.30);
            tomeDepleteBehavior = tomeDepleteBehavior != null ? tomeDepleteBehavior : MasterTomeWear.DepleteBehavior.STEP_DOWN;
            tomeRegenBaseSeconds = Config.clampInt(tomeRegenBaseSeconds, 1, 86_400);
            tomeRegenTier1Shelves = Config.clampInt(tomeRegenTier1Shelves, 1, 256);
            tomeRegenTier1Seconds = Config.clampInt(tomeRegenTier1Seconds, 1, tomeRegenBaseSeconds);
            tomeRegenTier2Shelves = Config.clampInt(tomeRegenTier2Shelves, tomeRegenTier1Shelves, 256);
            tomeRegenTier2Seconds = Config.clampInt(tomeRegenTier2Seconds, 1, tomeRegenTier1Seconds);
        }
    }

    /** Nested section {@code chiseled_mastery} in {@code library_resonance.json}. */
    public record ChiseledMastery(MasteryScaling scaling, TomeLifecycle lifecycle) {
        public static final ChiseledMastery DEFAULT = new ChiseledMastery(MasteryScaling.DEFAULT, TomeLifecycle.DEFAULT);

        public static final Codec<ChiseledMastery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MasteryScaling.MAP_CODEC.forGetter(ChiseledMastery::scaling),
            TomeLifecycle.MAP_CODEC.forGetter(ChiseledMastery::lifecycle)
        ).apply(instance, ChiseledMastery::new));

        public ChiseledMastery {
            scaling = scaling != null ? scaling : MasteryScaling.DEFAULT;
            lifecycle = lifecycle != null ? lifecycle : TomeLifecycle.DEFAULT;
        }
    }

    /** Nested section {@code epiphany_affinity} in {@code library_resonance.json} (12 fields). */
    public record EpiphanyAffinity(
        double epiphanyProbabilityGlobalWeight,
        double epiphanyProbabilityAffinityWeight,
        double blankBookAffinityWeight,
        double epiphanyShelfScoreFactor,
        double epiphanyGlobalMasteryScoreFactor,
        double epiphanyAffinityMasteryScoreFactor,
        double epiphanyTier2MaxProb,
        double epiphanyTier2ScoreDivisor,
        double epiphanyTier2MasteryBonus,
        double epiphanyTierPlusMaxProb,
        double epiphanyTierPlusScoreDivisor,
        double epiphanyTierPlusMasteryBonus
    ) {
        public static final EpiphanyAffinity DEFAULT = new EpiphanyAffinity(
            chiseled_enchanting_table.BundledDefaults.EPIPHANY_PROBABILITY_GLOBAL_WEIGHT,
            chiseled_enchanting_table.BundledDefaults.EPIPHANY_PROBABILITY_AFFINITY_WEIGHT,
            chiseled_enchanting_table.BundledDefaults.BLANK_BOOK_AFFINITY_WEIGHT,
            chiseled_enchanting_table.BundledDefaults.EPIPHANY_SHELF_SCORE_FACTOR,
            chiseled_enchanting_table.BundledDefaults.EPIPHANY_GLOBAL_MASTERY_SCORE_FACTOR,
            chiseled_enchanting_table.BundledDefaults.EPIPHANY_AFFINITY_MASTERY_SCORE_FACTOR,
            chiseled_enchanting_table.BundledDefaults.EPIPHANY_TIER2_MAX_PROB,
            chiseled_enchanting_table.BundledDefaults.EPIPHANY_TIER2_SCORE_DIVISOR,
            chiseled_enchanting_table.BundledDefaults.EPIPHANY_TIER2_MASTERY_BONUS,
            chiseled_enchanting_table.BundledDefaults.EPIPHANY_TIER_PLUS_MAX_PROB,
            chiseled_enchanting_table.BundledDefaults.EPIPHANY_TIER_PLUS_SCORE_DIVISOR,
            chiseled_enchanting_table.BundledDefaults.EPIPHANY_TIER_PLUS_MASTERY_BONUS
        );

        public static final Codec<EpiphanyAffinity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("epiphany_probability_global_weight", DEFAULT.epiphanyProbabilityGlobalWeight).forGetter(EpiphanyAffinity::epiphanyProbabilityGlobalWeight),
            Codec.DOUBLE.optionalFieldOf("epiphany_probability_affinity_weight", DEFAULT.epiphanyProbabilityAffinityWeight).forGetter(EpiphanyAffinity::epiphanyProbabilityAffinityWeight),
            Codec.DOUBLE.optionalFieldOf("blank_book_affinity_weight", DEFAULT.blankBookAffinityWeight).forGetter(EpiphanyAffinity::blankBookAffinityWeight),
            Codec.DOUBLE.optionalFieldOf("epiphany_shelf_score_factor", DEFAULT.epiphanyShelfScoreFactor).forGetter(EpiphanyAffinity::epiphanyShelfScoreFactor),
            Codec.DOUBLE.optionalFieldOf("epiphany_global_mastery_score_factor", DEFAULT.epiphanyGlobalMasteryScoreFactor).forGetter(EpiphanyAffinity::epiphanyGlobalMasteryScoreFactor),
            Codec.DOUBLE.optionalFieldOf("epiphany_affinity_mastery_score_factor", DEFAULT.epiphanyAffinityMasteryScoreFactor).forGetter(EpiphanyAffinity::epiphanyAffinityMasteryScoreFactor),
            Codec.DOUBLE.optionalFieldOf("epiphany_tier2_max_prob", DEFAULT.epiphanyTier2MaxProb).forGetter(EpiphanyAffinity::epiphanyTier2MaxProb),
            Codec.DOUBLE.optionalFieldOf("epiphany_tier2_score_divisor", DEFAULT.epiphanyTier2ScoreDivisor).forGetter(EpiphanyAffinity::epiphanyTier2ScoreDivisor),
            Codec.DOUBLE.optionalFieldOf("epiphany_tier2_mastery_bonus", DEFAULT.epiphanyTier2MasteryBonus).forGetter(EpiphanyAffinity::epiphanyTier2MasteryBonus),
            Codec.DOUBLE.optionalFieldOf("epiphany_tier_plus_max_prob", DEFAULT.epiphanyTierPlusMaxProb).forGetter(EpiphanyAffinity::epiphanyTierPlusMaxProb),
            Codec.DOUBLE.optionalFieldOf("epiphany_tier_plus_score_divisor", DEFAULT.epiphanyTierPlusScoreDivisor).forGetter(EpiphanyAffinity::epiphanyTierPlusScoreDivisor),
            Codec.DOUBLE.optionalFieldOf("epiphany_tier_plus_mastery_bonus", DEFAULT.epiphanyTierPlusMasteryBonus).forGetter(EpiphanyAffinity::epiphanyTierPlusMasteryBonus)
        ).apply(instance, EpiphanyAffinity::new));

        public EpiphanyAffinity {
            epiphanyProbabilityGlobalWeight = Config.clampDouble(epiphanyProbabilityGlobalWeight, 0.0, 10.0);
            epiphanyProbabilityAffinityWeight = Config.clampDouble(epiphanyProbabilityAffinityWeight, 0.0, 10.0);
            blankBookAffinityWeight = Config.clampDouble(blankBookAffinityWeight, 0.0, 2.0);
            epiphanyShelfScoreFactor = Config.clampDouble(epiphanyShelfScoreFactor, 0.0, 20.0);
            epiphanyGlobalMasteryScoreFactor = Config.clampDouble(epiphanyGlobalMasteryScoreFactor, 0.0, 20.0);
            epiphanyAffinityMasteryScoreFactor = Config.clampDouble(epiphanyAffinityMasteryScoreFactor, 0.0, 20.0);
            epiphanyTier2MaxProb = Config.clampDouble(epiphanyTier2MaxProb, 0.0, 1.0);
            epiphanyTier2ScoreDivisor = Config.clampDouble(epiphanyTier2ScoreDivisor, 1.0, 1000.0);
            epiphanyTier2MasteryBonus = Config.clampDouble(epiphanyTier2MasteryBonus, 0.0, 1.0);
            epiphanyTierPlusMaxProb = Config.clampDouble(epiphanyTierPlusMaxProb, 0.0, 1.0);
            epiphanyTierPlusScoreDivisor = Config.clampDouble(epiphanyTierPlusScoreDivisor, 1.0, 1000.0);
            epiphanyTierPlusMasteryBonus = Config.clampDouble(epiphanyTierPlusMasteryBonus, 0.0, 1.0);
        }
    }

    /** Nested section {@code wild_book_wear} in {@code library_resonance.json}. */
    public record WildBookWear(boolean enabled, List<Integer> remainingDurabilityWeights) {
        public static final WildBookWear DEFAULT = new WildBookWear(
            chiseled_enchanting_table.BundledDefaults.WILD_BOOK_WEAR_ENABLED,
            chiseled_enchanting_table.BundledDefaults.WILD_REMAINING_WEIGHTS
        );

        public static final Codec<WildBookWear> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled).forGetter(WildBookWear::enabled),
            Codec.INT.listOf().optionalFieldOf("remaining_durability_weights", DEFAULT.remainingDurabilityWeights).forGetter(WildBookWear::remainingDurabilityWeights)
        ).apply(instance, WildBookWear::new));

        public WildBookWear {
            if (remainingDurabilityWeights == null || remainingDurabilityWeights.isEmpty()) {
                remainingDurabilityWeights = MasterTomeWear.DEFAULT_WILD_REMAINING_WEIGHTS;
            } else {
                List<Integer> cleaned = new ArrayList<>(remainingDurabilityWeights.size());
                for (Integer w : remainingDurabilityWeights) {
                    cleaned.add(w == null ? 0 : Config.clampInt(w, 0, 10_000));
                }
                remainingDurabilityWeights = List.copyOf(cleaned);
            }
        }
    }

    /** Nested section {@code tome_pages} in {@code library_resonance.json} (6 fields). */
    public record TomePages(
        boolean lockedShelfBreakable,
        boolean lockedBookExtractionBlocked,
        int earlyUnlockXpLevels,
        int earlyUnlockGritCount,
        int sacrificeRequired,
        int tomePageRequiredShelves
    ) {
        public static final TomePages DEFAULT = new TomePages(
            chiseled_enchanting_table.BundledDefaults.LOCKED_SHELF_BREAKABLE,
            chiseled_enchanting_table.BundledDefaults.LOCKED_BOOK_EXTRACTION_BLOCKED,
            chiseled_enchanting_table.BundledDefaults.EARLY_UNLOCK_XP_LEVELS,
            chiseled_enchanting_table.BundledDefaults.EARLY_UNLOCK_GRIT_COUNT,
            chiseled_enchanting_table.BundledDefaults.SACRIFICE_REQUIRED,
            chiseled_enchanting_table.BundledDefaults.TOME_PAGE_REQUIRED_SHELVES
        );

        public static final Codec<TomePages> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("locked_shelf_breakable", DEFAULT.lockedShelfBreakable).forGetter(TomePages::lockedShelfBreakable),
            Codec.BOOL.optionalFieldOf("locked_book_extraction_blocked", DEFAULT.lockedBookExtractionBlocked).forGetter(TomePages::lockedBookExtractionBlocked),
            Codec.INT.optionalFieldOf("early_unlock_xp_levels", DEFAULT.earlyUnlockXpLevels).forGetter(TomePages::earlyUnlockXpLevels),
            Codec.INT.optionalFieldOf("early_unlock_grit_count", DEFAULT.earlyUnlockGritCount).forGetter(TomePages::earlyUnlockGritCount),
            Codec.INT.optionalFieldOf("sacrifice_required", DEFAULT.sacrificeRequired).forGetter(TomePages::sacrificeRequired),
            Codec.INT.optionalFieldOf("tome_page_required_shelves", DEFAULT.tomePageRequiredShelves).forGetter(TomePages::tomePageRequiredShelves)
        ).apply(instance, TomePages::new));

        public TomePages {
            earlyUnlockXpLevels = Config.clampInt(earlyUnlockXpLevels, 0, 512);
            earlyUnlockGritCount = Config.clampInt(earlyUnlockGritCount, 0, 512);
            sacrificeRequired = Config.clampInt(sacrificeRequired, 1, 1024);
            tomePageRequiredShelves = Config.clampInt(tomePageRequiredShelves, 1, 512);
        }
    }

    /** Nested section {@code essence_valuation} in {@code library_resonance.json} (Audit BAL-01). */
    public record EssenceValuation(
        double rarityMinMultiplier,
        double rarityMaxMultiplier,
        double rarityLogSlope,
        double treasureMultiplier,
        int conservationSafetyMarginPoints
    ) {
        public static final EssenceValuation DEFAULT = new EssenceValuation(
            chiseled_enchanting_table.BundledDefaults.ESSENCE_RARITY_MIN_MULTIPLIER,
            chiseled_enchanting_table.BundledDefaults.ESSENCE_RARITY_MAX_MULTIPLIER,
            chiseled_enchanting_table.BundledDefaults.ESSENCE_RARITY_LOG_SLOPE,
            chiseled_enchanting_table.BundledDefaults.ESSENCE_TREASURE_MULTIPLIER,
            chiseled_enchanting_table.BundledDefaults.CONSERVATION_SAFETY_MARGIN_POINTS
        );

        public static final Codec<EssenceValuation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("rarity_min_multiplier", DEFAULT.rarityMinMultiplier).forGetter(EssenceValuation::rarityMinMultiplier),
            Codec.DOUBLE.optionalFieldOf("rarity_max_multiplier", DEFAULT.rarityMaxMultiplier).forGetter(EssenceValuation::rarityMaxMultiplier),
            Codec.DOUBLE.optionalFieldOf("rarity_log_slope", DEFAULT.rarityLogSlope).forGetter(EssenceValuation::rarityLogSlope),
            Codec.DOUBLE.optionalFieldOf("treasure_multiplier", DEFAULT.treasureMultiplier).forGetter(EssenceValuation::treasureMultiplier),
            Codec.INT.optionalFieldOf("conservation_safety_margin_points", DEFAULT.conservationSafetyMarginPoints).forGetter(EssenceValuation::conservationSafetyMarginPoints)
        ).apply(instance, EssenceValuation::new));

        public EssenceValuation {
            rarityMinMultiplier = Config.clampDouble(rarityMinMultiplier, 0.50, 4.0);
            rarityMaxMultiplier = Config.clampDouble(rarityMaxMultiplier, rarityMinMultiplier, 6.0);
            rarityLogSlope = Config.clampDouble(rarityLogSlope, 0.0, 2.0);
            treasureMultiplier = Config.clampDouble(treasureMultiplier, 0.50, 4.0);
            conservationSafetyMarginPoints = Config.clampInt(conservationSafetyMarginPoints, 1, 100);
        }
    }

    /**
     * Structured, Codec-backed Library Resonance configuration grouped to mirror
     * {@code library_resonance.json} (Audit COUP-02 / COUP-03 / BAL-01).
     */
    public record Config(
        WoodenBookshelves woodenBookshelves,
        ChiseledMastery chiseledMastery,
        EpiphanyAffinity epiphanyAffinity,
        WildBookWear wildBookWear,
        TomePages tomePages,
        EssenceValuation essenceValuation
    ) {
        public static final Config DEFAULT = new Config(
            WoodenBookshelves.DEFAULT,
            ChiseledMastery.DEFAULT,
            EpiphanyAffinity.DEFAULT,
            WildBookWear.DEFAULT,
            TomePages.DEFAULT,
            EssenceValuation.DEFAULT
        );

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            WoodenBookshelves.CODEC.optionalFieldOf("wooden_bookshelves", WoodenBookshelves.DEFAULT).forGetter(Config::woodenBookshelves),
            ChiseledMastery.CODEC.optionalFieldOf("chiseled_mastery", ChiseledMastery.DEFAULT).forGetter(Config::chiseledMastery),
            EpiphanyAffinity.CODEC.optionalFieldOf("epiphany_affinity", EpiphanyAffinity.DEFAULT).forGetter(Config::epiphanyAffinity),
            WildBookWear.CODEC.optionalFieldOf("wild_book_wear", WildBookWear.DEFAULT).forGetter(Config::wildBookWear),
            TomePages.CODEC.optionalFieldOf("tome_pages", TomePages.DEFAULT).forGetter(Config::tomePages),
            EssenceValuation.CODEC.optionalFieldOf("essence_valuation", EssenceValuation.DEFAULT).forGetter(Config::essenceValuation)
        ).apply(instance, Config::new));

        public Config {
            woodenBookshelves = woodenBookshelves != null ? woodenBookshelves : WoodenBookshelves.DEFAULT;
            chiseledMastery = chiseledMastery != null ? chiseledMastery : ChiseledMastery.DEFAULT;
            epiphanyAffinity = epiphanyAffinity != null ? epiphanyAffinity : EpiphanyAffinity.DEFAULT;
            wildBookWear = wildBookWear != null ? wildBookWear : WildBookWear.DEFAULT;
            tomePages = tomePages != null ? tomePages : TomePages.DEFAULT;
            essenceValuation = essenceValuation != null ? essenceValuation : EssenceValuation.DEFAULT;
            int totalCap = chiseledMastery.scaling().maxTotalXpDiscountPercent();
            if (woodenBookshelves.maxWoodenDiscountPercent() > totalCap) {
                ChiseledEnchantingTable.LOGGER.warn(
                    "library_resonance.json max_wooden_discount_percent ({}) exceeds max_total_xp_discount_percent ({}); clamping wooden sub-cap to {}",
                    woodenBookshelves.maxWoodenDiscountPercent(),
                    totalCap,
                    totalCap
                );
                woodenBookshelves = new WoodenBookshelves(
                    woodenBookshelves.minDiscountBookshelves(),
                    woodenBookshelves.maxDiscountBookshelves(),
                    totalCap,
                    woodenBookshelves.shelfUpgradeMaterialMultiplier()
                );
            }
        }

        // --- Delegating accessors preserving full source & binary compatibility across callers ---
        public int minDiscountBookshelves() { return woodenBookshelves.minDiscountBookshelves(); }
        public int maxDiscountBookshelves() { return woodenBookshelves.maxDiscountBookshelves(); }
        public int maxWoodenDiscountPercent() { return woodenBookshelves.maxWoodenDiscountPercent(); }
        public double shelfUpgradeMaterialMultiplier() { return woodenBookshelves.shelfUpgradeMaterialMultiplier(); }

        public int maxGlobalMasteryDiscountPercent() { return chiseledMastery.scaling().maxGlobalMasteryDiscountPercent(); }
        public int maxBaseXpDiscountPercent() { return chiseledMastery.scaling().maxBaseXpDiscountPercent(); }
        public int maxSpecificEnchantBonusPercent() { return chiseledMastery.scaling().maxSpecificEnchantBonusPercent(); }
        public double specificEnchantExponent() { return chiseledMastery.scaling().specificEnchantExponent(); }
        public int maxTotalXpDiscountPercent() { return chiseledMastery.scaling().maxTotalXpDiscountPercent(); }
        public double masteryUnitExponent() { return chiseledMastery.scaling().masteryUnitExponent(); }
        public double globalMasteryMultiplier() { return chiseledMastery.scaling().globalMasteryMultiplier(); }
        public double masterTomeUpgradeMaterialMultiplier() { return chiseledMastery.scaling().masterTomeUpgradeMaterialMultiplier(); }
        public double incrementalStepCreditFactor() { return chiseledMastery.scaling().incrementalStepCreditFactor(); }
        public double maxMasteryUnitsPerSlot() { return chiseledMastery.scaling().maxMasteryUnitsPerSlot(); }
        public int wearImmuneMaxGeneration() { return chiseledMastery.scaling().wearImmuneMaxGeneration(); }
        public int wearImmuneFullWaiverMaxEnchants() { return chiseledMastery.scaling().wearImmuneFullWaiverMaxEnchants(); }
        public double wearImmuneOverloadWaiverDecay() { return chiseledMastery.scaling().wearImmuneOverloadWaiverDecay(); }

        public MasterTomeWear.ConsumptionMode tomeConsumptionMode() { return chiseledMastery.lifecycle().tomeConsumptionMode(); }
        public int maxTomeUses() { return chiseledMastery.lifecycle().maxTomeUses(); }
        public int bookCopyWearCost() { return chiseledMastery.lifecycle().bookCopyWearCost(); }
        public MasterTomeWear.DepleteBehavior tomeDepleteBehavior() { return chiseledMastery.lifecycle().tomeDepleteBehavior(); }
        public boolean tomeRegenEnabled() { return chiseledMastery.lifecycle().tomeRegenEnabled(); }
        public int tomeRegenBaseSeconds() { return chiseledMastery.lifecycle().tomeRegenBaseSeconds(); }
        public int tomeRegenTier1Shelves() { return chiseledMastery.lifecycle().tomeRegenTier1Shelves(); }
        public int tomeRegenTier1Seconds() { return chiseledMastery.lifecycle().tomeRegenTier1Seconds(); }
        public int tomeRegenTier2Shelves() { return chiseledMastery.lifecycle().tomeRegenTier2Shelves(); }
        public int tomeRegenTier2Seconds() { return chiseledMastery.lifecycle().tomeRegenTier2Seconds(); }
        public double unbreakingWaiverPerLevel() { return chiseledMastery.lifecycle().unbreakingWaiverPerLevel(); }
        public double unbreakingWaiverMaxChance() { return chiseledMastery.lifecycle().unbreakingWaiverMaxChance(); }
        public TomeWearData.ScapegoatPricing scapegoatPricing() { return chiseledMastery.lifecycle().scapegoatPricing(); }

        public double epiphanyProbabilityGlobalWeight() { return epiphanyAffinity.epiphanyProbabilityGlobalWeight(); }
        public double epiphanyProbabilityAffinityWeight() { return epiphanyAffinity.epiphanyProbabilityAffinityWeight(); }
        public double blankBookAffinityWeight() { return epiphanyAffinity.blankBookAffinityWeight(); }
        public double epiphanyShelfScoreFactor() { return epiphanyAffinity.epiphanyShelfScoreFactor(); }
        public double epiphanyGlobalMasteryScoreFactor() { return epiphanyAffinity.epiphanyGlobalMasteryScoreFactor(); }
        public double epiphanyAffinityMasteryScoreFactor() { return epiphanyAffinity.epiphanyAffinityMasteryScoreFactor(); }
        public double epiphanyTier2MaxProb() { return epiphanyAffinity.epiphanyTier2MaxProb(); }
        public double epiphanyTier2ScoreDivisor() { return epiphanyAffinity.epiphanyTier2ScoreDivisor(); }
        public double epiphanyTier2MasteryBonus() { return epiphanyAffinity.epiphanyTier2MasteryBonus(); }
        public double epiphanyTierPlusMaxProb() { return epiphanyAffinity.epiphanyTierPlusMaxProb(); }
        public double epiphanyTierPlusScoreDivisor() { return epiphanyAffinity.epiphanyTierPlusScoreDivisor(); }
        public double epiphanyTierPlusMasteryBonus() { return epiphanyAffinity.epiphanyTierPlusMasteryBonus(); }

        public boolean wildBookWearEnabled() { return wildBookWear.enabled(); }
        public List<Integer> wildBookRemainingWeights() { return wildBookWear.remainingDurabilityWeights(); }

        public boolean lockedShelfBreakable() { return tomePages.lockedShelfBreakable(); }
        public boolean lockedBookExtractionBlocked() { return tomePages.lockedBookExtractionBlocked(); }
        public int earlyUnlockXpLevels() { return tomePages.earlyUnlockXpLevels(); }
        public int earlyUnlockGritCount() { return tomePages.earlyUnlockGritCount(); }
        public int sacrificeRequired() { return tomePages.sacrificeRequired(); }
        public int tomePageRequiredShelves() { return tomePages.tomePageRequiredShelves(); }

        public double rarityMinMultiplier() { return essenceValuation.rarityMinMultiplier(); }
        public double rarityMaxMultiplier() { return essenceValuation.rarityMaxMultiplier(); }
        public double rarityLogSlope() { return essenceValuation.rarityLogSlope(); }
        public double treasureMultiplier() { return essenceValuation.treasureMultiplier(); }
        public int conservationSafetyMarginPoints() { return essenceValuation.conservationSafetyMarginPoints(); }

        /**
         * Applies {@link chiseled_enchanting_table.config.ModRuntimeConfig} overrides onto this
         * datapack-derived {@link Config} (Audit COUP-03 / COUP-06): enforces
         * {@code woodDisc <= totalDisc} without hidden asymmetric {@code globalMasteryMultiplier}
         * scaling, and zeroes all sub-caps when library resonance discounts are disabled.
         */
        public Config withRuntimeOverrides(chiseled_enchanting_table.config.ModRuntimeConfig cfg) {
            boolean resDiscountOff = !cfg.libraryDiscountEnabled();
            int totalDisc = resDiscountOff ? 0 : clampInt(cfg.maxTotalXpDiscountPercent(), 0, 95);
            int requestedWoodDisc = resDiscountOff ? 0 : clampInt(cfg.maxWoodenDiscountPercent(), 0, 95);
            if (!resDiscountOff && requestedWoodDisc > totalDisc) {
                ChiseledEnchantingTable.LOGGER.warn(
                    "Runtime config max_wooden_discount_percent ({}) exceeds max_total_xp_discount_percent ({}); clamping wooden sub-cap to {}",
                    requestedWoodDisc,
                    totalDisc,
                    totalDisc
                );
            }
            int woodDisc = Math.min(totalDisc, requestedWoodDisc);
            int globalDiscCap = resDiscountOff ? 0 : Math.min(totalDisc, this.maxGlobalMasteryDiscountPercent());
            int specDiscCap = resDiscountOff ? 0 : Math.min(totalDisc, this.maxSpecificEnchantBonusPercent());
            int baseDiscCap = resDiscountOff
                ? 0
                : Math.min(totalDisc, Math.max(woodDisc + globalDiscCap, this.maxBaseXpDiscountPercent()));

            return new Config(
                new WoodenBookshelves(
                    this.minDiscountBookshelves(),
                    Math.max(this.minDiscountBookshelves() + 1, this.maxDiscountBookshelves()),
                    woodDisc,
                    this.shelfUpgradeMaterialMultiplier()
                ),
                new ChiseledMastery(
                    new MasteryScaling(
                        this.masteryUnitExponent(),
                        this.globalMasteryMultiplier(),
                        globalDiscCap,
                        baseDiscCap,
                        specDiscCap,
                        this.specificEnchantExponent(),
                        totalDisc,
                        this.masterTomeUpgradeMaterialMultiplier(),
                        this.incrementalStepCreditFactor(),
                        this.maxMasteryUnitsPerSlot(),
                        this.wearImmuneMaxGeneration(),
                        this.wearImmuneFullWaiverMaxEnchants(),
                        this.wearImmuneOverloadWaiverDecay()
                    ),
                    new TomeLifecycle(
                        cfg.tomeConsumptionMode(),
                        cfg.maxTomeUses(),
                        cfg.bookCopyWearCost(),
                        this.unbreakingWaiverPerLevel(),
                        this.unbreakingWaiverMaxChance(),
                        cfg.tomeDepleteBehavior(),
                        cfg.tomeRegenEnabled(),
                        cfg.tomeRegenBaseSeconds(),
                        cfg.tomeRegenTier1Shelves(),
                        cfg.tomeRegenTier1Seconds(),
                        cfg.tomeRegenTier2Shelves(),
                        cfg.tomeRegenTier2Seconds(),
                        this.scapegoatPricing()
                    )
                ),
                this.epiphanyAffinity(),
                new WildBookWear(
                    cfg.tomeConsumptionMode() == MasterTomeWear.ConsumptionMode.WEAR && this.wildBookWearEnabled(),
                    this.wildBookRemainingWeights()
                ),
                new TomePages(
                    !cfg.tomeLocksEnabled() || cfg.lockedShelfBreakable(),
                    cfg.tomeLocksEnabled() && cfg.lockedBookExtractionBlocked(),
                    cfg.earlyUnlockXpLevels(),
                    cfg.earlyUnlockGritCount(),
                    cfg.sacrificeRequired(),
                    cfg.tomePageRequiredShelves()
                ),
                this.essenceValuation()
            );
        }

        // --- Legacy positional constructors retained as bridges for existing callers & tests ---
        @Deprecated
        public Config(
            int minDiscountBookshelves,
            int maxDiscountBookshelves,
            int maxWoodenDiscountPercent,
            int maxGlobalMasteryDiscountPercent,
            int maxBaseXpDiscountPercent,
            int maxSpecificEnchantBonusPercent,
            double specificEnchantExponent,
            int maxTotalXpDiscountPercent,
            double masteryUnitExponent,
            double globalMasteryMultiplier,
            double masterTomeUpgradeMaterialMultiplier,
            double shelfUpgradeMaterialMultiplier,
            double epiphanyProbabilityGlobalWeight,
            double epiphanyProbabilityAffinityWeight,
            double blankBookAffinityWeight,
            double epiphanyShelfScoreFactor,
            double epiphanyGlobalMasteryScoreFactor,
            double epiphanyAffinityMasteryScoreFactor,
            double epiphanyTier2MaxProb,
            double epiphanyTier2ScoreDivisor,
            double epiphanyTier2MasteryBonus,
            double epiphanyTierPlusMaxProb,
            double epiphanyTierPlusScoreDivisor,
            double epiphanyTierPlusMasteryBonus,
            double incrementalStepCreditFactor,
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
            double unbreakingWaiverPerLevel,
            double unbreakingWaiverMaxChance,
            boolean wildBookWearEnabled,
            List<Integer> wildBookRemainingWeights,
            TomeWearData.ScapegoatPricing scapegoatPricing,
            boolean lockedShelfBreakable,
            boolean lockedBookExtractionBlocked,
            int earlyUnlockXpLevels,
            int earlyUnlockGritCount,
            int sacrificeRequired,
            int tomePageRequiredShelves,
            double maxMasteryUnitsPerSlot,
            int wearImmuneMaxGeneration,
            int wearImmuneFullWaiverMaxEnchants,
            double wearImmuneOverloadWaiverDecay
        ) {
            this(
                new WoodenBookshelves(
                    minDiscountBookshelves,
                    maxDiscountBookshelves,
                    maxWoodenDiscountPercent,
                    shelfUpgradeMaterialMultiplier
                ),
                new ChiseledMastery(
                    new MasteryScaling(
                        masteryUnitExponent,
                        globalMasteryMultiplier,
                        maxGlobalMasteryDiscountPercent,
                        maxBaseXpDiscountPercent,
                        maxSpecificEnchantBonusPercent,
                        specificEnchantExponent,
                        maxTotalXpDiscountPercent,
                        masterTomeUpgradeMaterialMultiplier,
                        incrementalStepCreditFactor,
                        maxMasteryUnitsPerSlot,
                        wearImmuneMaxGeneration,
                        wearImmuneFullWaiverMaxEnchants,
                        wearImmuneOverloadWaiverDecay
                    ),
                    new TomeLifecycle(
                        tomeConsumptionMode,
                        maxTomeUses,
                        bookCopyWearCost,
                        unbreakingWaiverPerLevel,
                        unbreakingWaiverMaxChance,
                        tomeDepleteBehavior,
                        tomeRegenEnabled,
                        tomeRegenBaseSeconds,
                        tomeRegenTier1Shelves,
                        tomeRegenTier1Seconds,
                        tomeRegenTier2Shelves,
                        tomeRegenTier2Seconds,
                        scapegoatPricing
                    )
                ),
                new EpiphanyAffinity(
                    epiphanyProbabilityGlobalWeight,
                    epiphanyProbabilityAffinityWeight,
                    blankBookAffinityWeight,
                    epiphanyShelfScoreFactor,
                    epiphanyGlobalMasteryScoreFactor,
                    epiphanyAffinityMasteryScoreFactor,
                    epiphanyTier2MaxProb,
                    epiphanyTier2ScoreDivisor,
                    epiphanyTier2MasteryBonus,
                    epiphanyTierPlusMaxProb,
                    epiphanyTierPlusScoreDivisor,
                    epiphanyTierPlusMasteryBonus
                ),
                new WildBookWear(wildBookWearEnabled, wildBookRemainingWeights),
                new TomePages(
                    lockedShelfBreakable,
                    lockedBookExtractionBlocked,
                    earlyUnlockXpLevels,
                    earlyUnlockGritCount,
                    sacrificeRequired,
                    tomePageRequiredShelves
                ),
                EssenceValuation.DEFAULT
            );
        }

        @Deprecated
        public Config(
            int minDiscountBookshelves,
            int maxDiscountBookshelves,
            int maxWoodenDiscountPercent,
            int maxGlobalMasteryDiscountPercent,
            int maxBaseXpDiscountPercent,
            int maxSpecificEnchantBonusPercent,
            double specificEnchantExponent,
            int maxTotalXpDiscountPercent,
            double masteryUnitExponent,
            double globalMasteryMultiplier,
            double masterTomeUpgradeMaterialMultiplier,
            double shelfUpgradeMaterialMultiplier,
            double epiphanyProbabilityGlobalWeight,
            double epiphanyProbabilityAffinityWeight,
            double blankBookAffinityWeight,
            double epiphanyShelfScoreFactor,
            double epiphanyGlobalMasteryScoreFactor,
            double epiphanyAffinityMasteryScoreFactor,
            double epiphanyTier2MaxProb,
            double epiphanyTier2ScoreDivisor,
            double epiphanyTier2MasteryBonus,
            double epiphanyTierPlusMaxProb,
            double epiphanyTierPlusScoreDivisor,
            double epiphanyTierPlusMasteryBonus,
            double incrementalStepCreditFactor,
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
            double unbreakingWaiverPerLevel,
            double unbreakingWaiverMaxChance,
            boolean wildBookWearEnabled,
            List<Integer> wildBookRemainingWeights,
            TomeWearData.ScapegoatPricing scapegoatPricing,
            boolean lockedShelfBreakable,
            boolean lockedBookExtractionBlocked,
            int earlyUnlockXpLevels,
            int earlyUnlockGritCount,
            int sacrificeRequired,
            int tomePageRequiredShelves
        ) {
            this(
                minDiscountBookshelves, maxDiscountBookshelves, maxWoodenDiscountPercent,
                maxGlobalMasteryDiscountPercent, maxBaseXpDiscountPercent, maxSpecificEnchantBonusPercent,
                specificEnchantExponent, maxTotalXpDiscountPercent, masteryUnitExponent, globalMasteryMultiplier,
                masterTomeUpgradeMaterialMultiplier, shelfUpgradeMaterialMultiplier,
                epiphanyProbabilityGlobalWeight, epiphanyProbabilityAffinityWeight, blankBookAffinityWeight,
                epiphanyShelfScoreFactor, epiphanyGlobalMasteryScoreFactor, epiphanyAffinityMasteryScoreFactor,
                epiphanyTier2MaxProb, epiphanyTier2ScoreDivisor, epiphanyTier2MasteryBonus,
                epiphanyTierPlusMaxProb, epiphanyTierPlusScoreDivisor, epiphanyTierPlusMasteryBonus,
                incrementalStepCreditFactor, tomeConsumptionMode, maxTomeUses, bookCopyWearCost, tomeDepleteBehavior,
                tomeRegenEnabled, tomeRegenBaseSeconds, tomeRegenTier1Shelves, tomeRegenTier1Seconds,
                tomeRegenTier2Shelves, tomeRegenTier2Seconds, unbreakingWaiverPerLevel, unbreakingWaiverMaxChance,
                wildBookWearEnabled, wildBookRemainingWeights, scapegoatPricing,
                lockedShelfBreakable, lockedBookExtractionBlocked, earlyUnlockXpLevels, earlyUnlockGritCount,
                sacrificeRequired, tomePageRequiredShelves,
                chiseled_enchanting_table.BundledDefaults.MAX_MASTERY_UNITS_PER_SLOT,
                chiseled_enchanting_table.BundledDefaults.WEAR_IMMUNE_MAX_GENERATION,
                chiseled_enchanting_table.BundledDefaults.WEAR_IMMUNE_FULL_WAIVER_MAX_ENCHANTS,
                chiseled_enchanting_table.BundledDefaults.WEAR_IMMUNE_OVERLOAD_WAIVER_DECAY
            );
        }

        @Deprecated
        public Config(
            int minDiscountBookshelves,
            int maxDiscountBookshelves,
            int maxWoodenDiscountPercent,
            int maxGlobalMasteryDiscountPercent,
            int maxBaseXpDiscountPercent,
            int maxSpecificEnchantBonusPercent,
            double specificEnchantExponent,
            int maxTotalXpDiscountPercent,
            double masteryUnitExponent,
            double globalMasteryMultiplier,
            double masterTomeUpgradeMaterialMultiplier,
            double shelfUpgradeMaterialMultiplier,
            double epiphanyProbabilityGlobalWeight,
            double epiphanyProbabilityAffinityWeight,
            double blankBookAffinityWeight,
            double epiphanyShelfScoreFactor,
            double epiphanyGlobalMasteryScoreFactor,
            double epiphanyAffinityMasteryScoreFactor,
            double epiphanyTier2MaxProb,
            double epiphanyTier2ScoreDivisor,
            double epiphanyTier2MasteryBonus,
            double epiphanyTierPlusMaxProb,
            double epiphanyTierPlusScoreDivisor,
            double epiphanyTierPlusMasteryBonus,
            double incrementalStepCreditFactor,
            MasterTomeWear.ConsumptionMode tomeConsumptionMode,
            int maxTomeUses,
            int bookCopyWearCost,
            MasterTomeWear.DepleteBehavior tomeDepleteBehavior,
            boolean tomeRegenEnabled,
            int tomeRegenBaseSeconds,
            int tomeRegenTier1Shelves,
            int tomeRegenTier1Seconds,
            int tomeRegenTier2Shelves,
            int tomeRegenTier2Seconds
        ) {
            this(
                minDiscountBookshelves, maxDiscountBookshelves, maxWoodenDiscountPercent,
                maxGlobalMasteryDiscountPercent, maxBaseXpDiscountPercent, maxSpecificEnchantBonusPercent,
                specificEnchantExponent, maxTotalXpDiscountPercent, masteryUnitExponent, globalMasteryMultiplier,
                masterTomeUpgradeMaterialMultiplier, shelfUpgradeMaterialMultiplier,
                epiphanyProbabilityGlobalWeight, epiphanyProbabilityAffinityWeight, blankBookAffinityWeight,
                epiphanyShelfScoreFactor, epiphanyGlobalMasteryScoreFactor, epiphanyAffinityMasteryScoreFactor,
                epiphanyTier2MaxProb, epiphanyTier2ScoreDivisor, epiphanyTier2MasteryBonus,
                epiphanyTierPlusMaxProb, epiphanyTierPlusScoreDivisor, epiphanyTierPlusMasteryBonus,
                incrementalStepCreditFactor, tomeConsumptionMode, maxTomeUses, bookCopyWearCost, tomeDepleteBehavior,
                tomeRegenEnabled, tomeRegenBaseSeconds, tomeRegenTier1Shelves, tomeRegenTier1Seconds,
                tomeRegenTier2Shelves, tomeRegenTier2Seconds,
                MasterTomeWear.DEFAULT_UNBREAKING_WAIVER_PER_LEVEL,
                MasterTomeWear.DEFAULT_UNBREAKING_WAIVER_MAX_CHANCE,
                MasterTomeWear.DEFAULT_WILD_BOOK_WEAR_ENABLED,
                MasterTomeWear.DEFAULT_WILD_REMAINING_WEIGHTS,
                TomeWearData.ScapegoatPricing.ESSENCE,
                DEFAULT.lockedShelfBreakable(),
                DEFAULT.lockedBookExtractionBlocked(),
                DEFAULT.earlyUnlockXpLevels(),
                DEFAULT.earlyUnlockGritCount(),
                DEFAULT.sacrificeRequired(),
                DEFAULT.tomePageRequiredShelves()
            );
        }

        @Deprecated
        public Config(
            int minDiscountBookshelves,
            int maxDiscountBookshelves,
            int maxWoodenDiscountPercent,
            int maxGlobalMasteryDiscountPercent,
            int maxBaseXpDiscountPercent,
            int maxSpecificEnchantBonusPercent,
            double specificEnchantExponent,
            int maxTotalXpDiscountPercent,
            double masteryUnitExponent,
            double globalMasteryMultiplier,
            double masterTomeUpgradeMaterialMultiplier,
            double shelfUpgradeMaterialMultiplier,
            double epiphanyProbabilityGlobalWeight,
            double epiphanyProbabilityAffinityWeight,
            double blankBookAffinityWeight,
            double epiphanyShelfScoreFactor,
            double epiphanyGlobalMasteryScoreFactor,
            double epiphanyAffinityMasteryScoreFactor,
            double epiphanyTier2MaxProb,
            double epiphanyTier2ScoreDivisor,
            double epiphanyTier2MasteryBonus,
            double epiphanyTierPlusMaxProb,
            double epiphanyTierPlusScoreDivisor,
            double epiphanyTierPlusMasteryBonus,
            double incrementalStepCreditFactor,
            MasterTomeWear.ConsumptionMode tomeConsumptionMode,
            int maxTomeUses,
            int bookCopyWearCost,
            MasterTomeWear.DepleteBehavior tomeDepleteBehavior
        ) {
            this(
                minDiscountBookshelves, maxDiscountBookshelves, maxWoodenDiscountPercent,
                maxGlobalMasteryDiscountPercent, maxBaseXpDiscountPercent, maxSpecificEnchantBonusPercent,
                specificEnchantExponent, maxTotalXpDiscountPercent, masteryUnitExponent, globalMasteryMultiplier,
                masterTomeUpgradeMaterialMultiplier, shelfUpgradeMaterialMultiplier,
                epiphanyProbabilityGlobalWeight, epiphanyProbabilityAffinityWeight, blankBookAffinityWeight,
                epiphanyShelfScoreFactor, epiphanyGlobalMasteryScoreFactor, epiphanyAffinityMasteryScoreFactor,
                epiphanyTier2MaxProb, epiphanyTier2ScoreDivisor, epiphanyTier2MasteryBonus,
                epiphanyTierPlusMaxProb, epiphanyTierPlusScoreDivisor, epiphanyTierPlusMasteryBonus,
                incrementalStepCreditFactor, tomeConsumptionMode, maxTomeUses, bookCopyWearCost, tomeDepleteBehavior,
                MasterTomeWear.DEFAULT_REGEN_ENABLED,
                MasterTomeWear.DEFAULT_REGEN_BASE_SECONDS,
                MasterTomeWear.DEFAULT_REGEN_TIER1_SHELVES,
                MasterTomeWear.DEFAULT_REGEN_TIER1_SECONDS,
                MasterTomeWear.DEFAULT_REGEN_TIER2_SHELVES,
                MasterTomeWear.DEFAULT_REGEN_TIER2_SECONDS,
                MasterTomeWear.DEFAULT_UNBREAKING_WAIVER_PER_LEVEL,
                MasterTomeWear.DEFAULT_UNBREAKING_WAIVER_MAX_CHANCE,
                true,
                MasterTomeWear.DEFAULT_WILD_REMAINING_WEIGHTS,
                TomeWearData.ScapegoatPricing.ESSENCE,
                DEFAULT.lockedShelfBreakable(),
                DEFAULT.lockedBookExtractionBlocked(),
                DEFAULT.earlyUnlockXpLevels(),
                DEFAULT.earlyUnlockGritCount(),
                DEFAULT.sacrificeRequired(),
                DEFAULT.tomePageRequiredShelves()
            );
        }

        @Deprecated
        public Config(
            int minDiscountBookshelves,
            int maxDiscountBookshelves,
            int maxWoodenDiscountPercent,
            int maxGlobalMasteryDiscountPercent,
            int maxBaseXpDiscountPercent,
            int maxSpecificEnchantBonusPercent,
            double specificEnchantExponent,
            int maxTotalXpDiscountPercent,
            double masteryUnitExponent,
            double globalMasteryMultiplier,
            double masterTomeUpgradeMaterialMultiplier,
            double shelfUpgradeMaterialMultiplier,
            double epiphanyProbabilityGlobalWeight,
            double epiphanyProbabilityAffinityWeight,
            double blankBookAffinityWeight,
            double epiphanyShelfScoreFactor,
            double epiphanyGlobalMasteryScoreFactor,
            double epiphanyAffinityMasteryScoreFactor,
            double epiphanyTier2MaxProb,
            double epiphanyTier2ScoreDivisor,
            double epiphanyTier2MasteryBonus,
            double epiphanyTierPlusMaxProb,
            double epiphanyTierPlusScoreDivisor,
            double epiphanyTierPlusMasteryBonus,
            double incrementalStepCreditFactor
        ) {
            this(
                minDiscountBookshelves, maxDiscountBookshelves, maxWoodenDiscountPercent,
                maxGlobalMasteryDiscountPercent, maxBaseXpDiscountPercent, maxSpecificEnchantBonusPercent,
                specificEnchantExponent, maxTotalXpDiscountPercent, masteryUnitExponent, globalMasteryMultiplier,
                masterTomeUpgradeMaterialMultiplier, shelfUpgradeMaterialMultiplier,
                epiphanyProbabilityGlobalWeight, epiphanyProbabilityAffinityWeight, blankBookAffinityWeight,
                epiphanyShelfScoreFactor, epiphanyGlobalMasteryScoreFactor, epiphanyAffinityMasteryScoreFactor,
                epiphanyTier2MaxProb, epiphanyTier2ScoreDivisor, epiphanyTier2MasteryBonus,
                epiphanyTierPlusMaxProb, epiphanyTierPlusScoreDivisor, epiphanyTierPlusMasteryBonus,
                incrementalStepCreditFactor,
                MasterTomeWear.ConsumptionMode.WEAR,
                MasterTomeWear.DEFAULT_MAX_TOME_USES,
                MasterTomeWear.DEFAULT_BOOK_COPY_WEAR_COST,
                MasterTomeWear.DepleteBehavior.STEP_DOWN
            );
        }

        public static Config fromJson(JsonObject root) {
            if (root == null) {
                return DEFAULT;
            }
            JsonObject normalized = normalizeFlatAndLegacyAliases(root);
            return CODEC.parse(JsonOps.INSTANCE, normalized)
                .resultOrPartial(err -> ChiseledEnchantingTable.LOGGER.warn(
                    "library_resonance.json codec parse warning (falling back on partial/defaults): {}",
                    err
                ))
                .orElse(DEFAULT);
        }

        private static JsonObject normalizeFlatAndLegacyAliases(JsonObject root) {
            JsonObject copy = root.deepCopy();
            JsonObject wood = ensureObject(copy, "wooden_bookshelves");
            JsonObject mastery = ensureObject(copy, "chiseled_mastery");
            JsonObject epiphany = ensureObject(copy, "epiphany_affinity");
            JsonObject wild = ensureObject(copy, "wild_book_wear");
            JsonObject tomePages = ensureObject(copy, "tome_pages");
            JsonObject essence = ensureObject(copy, "essence_valuation");

            promoteKey(copy, wood, "min_discount_bookshelves", null);
            promoteKey(copy, wood, "max_discount_bookshelves", null);
            promoteKey(copy, wood, "max_wooden_discount_percent", "max_discount_percent");
            promoteKey(copy, wood, "shelf_upgrade_material_multiplier", "upgrade_material_multiplier");

            promoteKey(copy, mastery, "mastery_unit_exponent", null);
            promoteKey(copy, mastery, "global_mastery_multiplier", "global_discount_per_unit");
            promoteKey(copy, mastery, "max_global_mastery_discount_percent", "max_global_discount_percent");
            promoteKey(copy, mastery, "max_base_xp_discount_percent", "max_base_discount_percent");
            promoteKey(copy, mastery, "max_specific_enchant_bonus_percent", "max_specific_discount_percent");
            promoteKey(copy, mastery, "specific_enchant_exponent", "specific_exponent");
            promoteKey(copy, mastery, "max_total_xp_discount_percent", null);
            promoteKey(copy, mastery, "master_tome_upgrade_material_multiplier", "upgrade_material_multiplier");
            promoteKey(copy, mastery, "incremental_step_credit_factor", "incremental_threshold_credit_factor");
            promoteKey(copy, mastery, "tome_consumption_mode", null);
            promoteKey(copy, mastery, "max_tome_uses", null);
            promoteKey(copy, mastery, "book_copy_wear_cost", null);
            promoteKey(copy, mastery, "unbreaking_waiver_per_level", null);
            promoteKey(copy, mastery, "unbreaking_waiver_max_chance", null);
            promoteKey(copy, mastery, "max_mastery_units_per_slot", null);
            promoteKey(copy, mastery, "wear_immune_max_generation", null);
            promoteKey(copy, mastery, "wear_immune_full_waiver_max_enchants", null);
            promoteKey(copy, mastery, "wear_immune_overload_waiver_decay", null);
            promoteKey(copy, mastery, "tome_deplete_behavior", null);
            promoteKey(copy, mastery, "tome_regen_enabled", null);
            promoteKey(copy, mastery, "tome_regen_base_seconds", null);
            promoteKey(copy, mastery, "tome_regen_tier1_shelves", null);
            promoteKey(copy, mastery, "tome_regen_tier1_seconds", null);
            promoteKey(copy, mastery, "tome_regen_tier2_shelves", null);
            promoteKey(copy, mastery, "tome_regen_tier2_seconds", null);
            promoteKey(copy, mastery, "scapegoat_pricing", null);

            promoteKey(copy, epiphany, "epiphany_probability_global_weight", null);
            promoteKey(copy, epiphany, "epiphany_probability_affinity_weight", "affinity_chance_per_unit");
            promoteKey(copy, epiphany, "blank_book_affinity_weight", null);
            promoteKey(copy, epiphany, "epiphany_shelf_score_factor", null);
            promoteKey(copy, epiphany, "epiphany_global_mastery_score_factor", "global_slot_weight");
            promoteKey(copy, epiphany, "epiphany_affinity_mastery_score_factor", "affinity_slot_weight");
            promoteKey(copy, epiphany, "epiphany_tier2_max_prob", null);
            promoteKey(copy, epiphany, "epiphany_tier2_score_divisor", null);
            promoteKey(copy, epiphany, "epiphany_tier2_mastery_bonus", null);
            promoteKey(copy, epiphany, "epiphany_tier_plus_max_prob", null);
            promoteKey(copy, epiphany, "epiphany_tier_plus_score_divisor", null);
            promoteKey(copy, epiphany, "epiphany_tier_plus_mastery_bonus", null);

            promoteKey(copy, wild, "enabled", "wild_book_wear_enabled");
            promoteKey(copy, wild, "remaining_durability_weights", null);

            promoteKey(copy, tomePages, "locked_shelf_breakable", null);
            promoteKey(copy, tomePages, "locked_book_extraction_blocked", null);
            promoteKey(copy, tomePages, "early_unlock_xp_levels", null);
            promoteKey(copy, tomePages, "early_unlock_grit_count", null);
            promoteKey(copy, tomePages, "sacrifice_required", null);
            promoteKey(copy, tomePages, "tome_page_required_shelves", null);

            promoteKey(copy, essence, "rarity_min_multiplier", null);
            promoteKey(copy, essence, "rarity_max_multiplier", null);
            promoteKey(copy, essence, "rarity_log_slope", null);
            promoteKey(copy, essence, "treasure_multiplier", null);
            promoteKey(copy, essence, "conservation_safety_margin_points", null);
            return copy;
        }

        private static JsonObject ensureObject(JsonObject root, String key) {
            if (root.has(key) && root.get(key).isJsonObject()) {
                return root.getAsJsonObject(key);
            }
            JsonObject created = new JsonObject();
            root.add(key, created);
            return created;
        }

        private static void promoteKey(JsonObject root, JsonObject section, String primaryKey, String aliasKey) {
            if (!section.has(primaryKey)) {
                if (aliasKey != null && section.has(aliasKey)) {
                    section.add(primaryKey, section.get(aliasKey));
                } else if (root.has(primaryKey)) {
                    section.add(primaryKey, root.get(primaryKey));
                } else if (aliasKey != null && root.has(aliasKey)) {
                    section.add(primaryKey, root.get(aliasKey));
                }
            }
        }

        static int clampInt(int v, int lo, int hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        static double clampDouble(double v, double lo, double hi) {
            if (!Double.isFinite(v)) return lo;
            return Math.max(lo, Math.min(hi, v));
        }
    }

    private LibraryResonance() {}

    public static Config activeServerConfig() {
        Config cfg = ChiseledEnchantingTableData.libraryResonanceConfig();
        return cfg != null ? cfg : Config.DEFAULT;
    }

    public static Config configForSide(boolean isClientSide) {
        Config cfg = isClientSide
            ? SyncedEnchantmentCosts.libraryResonanceConfig()
            : ChiseledEnchantingTableData.libraryResonanceConfig();
        return cfg != null ? cfg : Config.DEFAULT;
    }

    public static Config activeConfig() {
        return activeServerConfig();
    }

    public static double relativeMastery(int storedLevel, int maxLevel) {
        if (storedLevel <= 0) {
            return 0.0;
        }
        int cappedMax = Math.max(1, maxLevel);
        return Math.min(1.0, (double) Math.min(storedLevel, cappedMax) / (double) cappedMax);
    }

    public static double masteryUnitsForEntry(int storedLevel, int maxLevel) {
        return masteryUnitsForEntry(activeServerConfig(), storedLevel, maxLevel);
    }

    public static double masteryUnitsForEntry(Config cfg, int storedLevel, int maxLevel) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        double r = relativeMastery(storedLevel, maxLevel);
        if (r <= 0.0) {
            return 0.0;
        }
        return Math.pow(r, c.masteryUnitExponent());
    }

    public static int resolveMaxLevel(Level level, Identifier enchantmentId) {
        try {
            Holder<Enchantment> holder = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
            return Math.max(1, holder.value().getMaxLevel());
        } catch (Exception ignored) {
            return 1;
        }
    }

    public static double totalMasteryUnits(Level level, Map<Identifier, Integer> storedMasterLevels) {
        return totalMasteryUnits(configForSide(level.isClientSide()), level, storedMasterLevels);
    }

    public static double totalMasteryUnits(Config cfg, Level level, Map<Identifier, Integer> storedMasterLevels) {
        if (storedMasterLevels == null || storedMasterLevels.isEmpty()) {
            return 0.0;
        }
        Config c = cfg != null ? cfg : Config.DEFAULT;
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        double sum = 0.0;
        for (Map.Entry<Identifier, Integer> entry : storedMasterLevels.entrySet()) {
            int lvl = entry.getValue();
            if (lvl <= 0) continue;
            try {
                Holder.Reference<Enchantment> holder = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, entry.getKey()));
                if (holder.is(CURSE_TAG)) continue;
                sum += masteryUnitsForEntry(c, lvl, holder.value().getMaxLevel());
            } catch (Exception ignored) {
            }
        }
        return sum;
    }

    /** Caps the mastery units contributed by a single occupied Master Tome slot ({@code <= 0} disables per-slot cap). */
    public static double capSingleSlotMasteryUnits(Config cfg, double rawSlotUnits) {
        if (rawSlotUnits <= 0.0) {
            return 0.0;
        }
        Config c = cfg != null ? cfg : Config.DEFAULT;
        double cap = c.maxMasteryUnitsPerSlot();
        return cap > 0.0 ? Math.min(cap, rawSlotUnits) : rawSlotUnits;
    }

    /**
     * Combines deduplicated library mastery units ({@code U_dedup}) with the sum of per-slot capped units
     * ({@code sum_i min(maxMasteryUnitsPerSlot, u_i)}), defeating both single-slot super-book stacking
     * and multi-slot duplicate-enchantment farming.
     */
    public static double effectiveTotalMasteryUnits(
        Config cfg,
        double deduplicatedLibraryUnits,
        double sumOfSlotCappedUnits
    ) {
        if (deduplicatedLibraryUnits <= 0.0 || sumOfSlotCappedUnits <= 0.0) {
            return 0.0;
        }
        Config c = cfg != null ? cfg : Config.DEFAULT;
        if (c.maxMasteryUnitsPerSlot() <= 0.0) {
            return deduplicatedLibraryUnits;
        }
        return Math.min(deduplicatedLibraryUnits, sumOfSlotCappedUnits);
    }

    /** Computes the capped mastery-unit budget of one Enchanted Book slot inside a Chiseled Bookshelf. */
    public static double slotCappedMasteryUnits(Config cfg, Level level, ItemStack tomeStack) {
        if (tomeStack == null || tomeStack.isEmpty() || !tomeStack.is(Items.ENCHANTED_BOOK)) {
            return 0.0;
        }
        Config c = cfg != null ? cfg : Config.DEFAULT;
        var enchants = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(tomeStack);
        if (enchants.isEmpty()) {
            return 0.0;
        }
        double raw = 0.0;
        for (var entry : enchants.entrySet()) {
            int lvl = entry.getIntValue();
            if (lvl <= 0 || entry.getKey().is(CURSE_TAG)) continue;
            raw += masteryUnitsForEntry(c, lvl, entry.getKey().value().getMaxLevel());
        }
        return capSingleSlotMasteryUnits(c, raw);
    }

    public static double affinityMasteryUnits(
        Level level,
        Map<Identifier, Integer> storedMasterLevels,
        ItemStack targetStack
    ) {
        return affinityMasteryUnits(configForSide(level.isClientSide()), level, storedMasterLevels, targetStack);
    }

    public static double affinityMasteryUnits(
        Config cfg,
        Level level,
        Map<Identifier, Integer> storedMasterLevels,
        ItemStack targetStack
    ) {
        if (storedMasterLevels == null || storedMasterLevels.isEmpty() || targetStack.isEmpty()) {
            return 0.0;
        }
        Config c = cfg != null ? cfg : Config.DEFAULT;
        boolean isBlankBook = targetStack.is(Items.BOOK);
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        double sum = 0.0;
        for (Map.Entry<Identifier, Integer> entry : storedMasterLevels.entrySet()) {
            int lvl = entry.getValue();
            if (lvl <= 0) continue;
            try {
                Holder.Reference<Enchantment> holder = registry.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, entry.getKey()));
                if (holder.is(CURSE_TAG)) continue;
                if (isBlankBook || holder.value().canEnchant(targetStack)) {
                    double units = masteryUnitsForEntry(c, lvl, holder.value().getMaxLevel());
                    sum += isBlankBook ? (units * c.blankBookAffinityWeight()) : units;
                }
            } catch (Exception ignored) {
            }
        }
        return sum;
    }

    public static int woodenBookshelfDiscountPercent(int regularBookshelves) {
        return woodenBookshelfDiscountPercent(activeServerConfig(), regularBookshelves);
    }

    public static int woodenBookshelfDiscountPercent(Config cfg, int regularBookshelves) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        if (regularBookshelves < c.minDiscountBookshelves() || c.maxWoodenDiscountPercent() <= 0) {
            return 0;
        }
        int clamped = Math.min(c.maxDiscountBookshelves(), regularBookshelves);
        int span = Math.max(1, c.maxDiscountBookshelves() - c.minDiscountBookshelves());
        double progress = (double) (clamped - c.minDiscountBookshelves()) / (double) span;
        return Math.min(c.maxWoodenDiscountPercent(), Math.max(1, (int) Math.round(1.0 + Math.max(0, c.maxWoodenDiscountPercent() - 1) * progress)));
    }

    public static int globalMasteryDiscountPercent(double totalMasteryUnits) {
        return globalMasteryDiscountPercent(activeServerConfig(), totalMasteryUnits);
    }

    public static int globalMasteryDiscountPercent(Config cfg, double totalMasteryUnits) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        if (totalMasteryUnits <= 0.0) {
            return 0;
        }
        return Math.min(c.maxGlobalMasteryDiscountPercent(), (int) Math.floor(totalMasteryUnits * c.globalMasteryMultiplier()));
    }

    public static int specificEnchantmentDiscountBonus(int storedLevel, int maxLevel) {
        return specificEnchantmentDiscountBonus(activeServerConfig(), storedLevel, maxLevel);
    }

    public static int specificEnchantmentDiscountBonus(Config cfg, int storedLevel, int maxLevel) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        double r = relativeMastery(storedLevel, maxLevel);
        if (r <= 0.0) {
            return 0;
        }
        double weighted = c.specificEnchantExponent() == 1.0 ? r : Math.pow(r, c.specificEnchantExponent());
        return Math.min(c.maxSpecificEnchantBonusPercent(), (int) Math.round(c.maxSpecificEnchantBonusPercent() * weighted));
    }

    public static int baseXpDiscountPercent(int regularBookshelves, double totalMasteryUnits) {
        return baseXpDiscountPercent(activeServerConfig(), regularBookshelves, totalMasteryUnits);
    }

    public static int baseXpDiscountPercent(Config cfg, int regularBookshelves, double totalMasteryUnits) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        int cap = Math.min(c.maxTotalXpDiscountPercent(), c.maxBaseXpDiscountPercent());
        return Math.min(
            cap,
            woodenBookshelfDiscountPercent(c, regularBookshelves) + globalMasteryDiscountPercent(c, totalMasteryUnits)
        );
    }

    public static int effectiveXpDiscountPercent(
        int regularBookshelves,
        double totalMasteryUnits,
        int specificStoredLevel,
        int specificMaxLevel
    ) {
        return effectiveXpDiscountPercent(
            activeServerConfig(),
            regularBookshelves,
            totalMasteryUnits,
            specificStoredLevel,
            specificMaxLevel
        );
    }

    public static int effectiveXpDiscountPercent(
        Config cfg,
        int regularBookshelves,
        double totalMasteryUnits,
        int specificStoredLevel,
        int specificMaxLevel
    ) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        int base = baseXpDiscountPercent(c, regularBookshelves, totalMasteryUnits);
        int specific = specificEnchantmentDiscountBonus(c, specificStoredLevel, specificMaxLevel);
        return Math.min(c.maxTotalXpDiscountPercent(), base + specific);
    }

    public static double epiphanyResonanceScore(
        int regularBookshelves,
        int minBookshelves,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        return epiphanyResonanceScore(activeServerConfig(), regularBookshelves, minBookshelves, totalMasteryUnits, affinityMasteryUnits);
    }

    public static double epiphanyResonanceScore(
        Config cfg,
        int regularBookshelves,
        int minBookshelves,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        if (regularBookshelves < minBookshelves) {
            return 0.0;
        }
        double shelfExcess = Math.max(0, regularBookshelves - minBookshelves) * c.epiphanyShelfScoreFactor();
        return shelfExcess
            + c.epiphanyGlobalMasteryScoreFactor() * Math.max(0.0, totalMasteryUnits)
            + c.epiphanyAffinityMasteryScoreFactor() * Math.max(0.0, affinityMasteryUnits);
    }

    public static double epiphanyProbability(
        int regularBookshelves,
        int minBookshelves,
        double baseChance,
        double perShelfChance,
        double perTomeChance,
        double maxChance,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        return epiphanyProbability(
            activeServerConfig(),
            regularBookshelves,
            minBookshelves,
            baseChance,
            perShelfChance,
            perTomeChance,
            maxChance,
            totalMasteryUnits,
            affinityMasteryUnits
        );
    }

    public static double epiphanyProbability(
        Config cfg,
        int regularBookshelves,
        int minBookshelves,
        double baseChance,
        double perShelfChance,
        double perTomeChance,
        double maxChance,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        if (regularBookshelves < minBookshelves) {
            return 0.0;
        }
        double effectiveMastery = c.epiphanyProbabilityGlobalWeight() * Math.max(0.0, totalMasteryUnits)
            + c.epiphanyProbabilityAffinityWeight() * Math.max(0.0, affinityMasteryUnits);
        double raw = baseChance
            + Math.max(0, regularBookshelves - minBookshelves) * perShelfChance
            + effectiveMastery * perTomeChance;
        return Math.max(0.0, Math.min(maxChance, raw));
    }

    public static int epiphanyMaxSlots(
        int regularBookshelves,
        int minBookshelves,
        int maxSlotsCap,
        int scoreStepPerExtraSlot,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        return epiphanyMaxSlots(
            activeServerConfig(),
            regularBookshelves,
            minBookshelves,
            maxSlotsCap,
            scoreStepPerExtraSlot,
            totalMasteryUnits,
            affinityMasteryUnits
        );
    }

    public static int epiphanyMaxSlots(
        Config cfg,
        int regularBookshelves,
        int minBookshelves,
        int maxSlotsCap,
        int scoreStepPerExtraSlot,
        double totalMasteryUnits,
        double affinityMasteryUnits
    ) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        if (regularBookshelves < minBookshelves) {
            return 0;
        }
        double score = epiphanyResonanceScore(c, regularBookshelves, minBookshelves, totalMasteryUnits, affinityMasteryUnits);
        int step = Math.max(1, scoreStepPerExtraSlot);
        int unlocked = 1 + (int) Math.floor(score / (double) step);
        return Math.max(1, Math.min(Math.max(1, maxSlotsCap), unlocked));
    }

    public static int rollEpiphanyTier(
        long hashSeed,
        int minTier,
        int maxLevel,
        double resonanceScore,
        double ownStoredRelativeMastery
    ) {
        return rollEpiphanyTier(activeServerConfig(), hashSeed, minTier, maxLevel, resonanceScore, ownStoredRelativeMastery);
    }

    public static int rollEpiphanyTier(
        Config cfg,
        long hashSeed,
        int minTier,
        int maxLevel,
        double resonanceScore,
        double ownStoredRelativeMastery
    ) {
        Config c = cfg != null ? cfg : Config.DEFAULT;
        int cappedMax = Math.max(1, maxLevel);
        int tier = Math.max(1, Math.min(cappedMax, minTier));
        if (tier >= cappedMax) {
            return cappedMax;
        }
        long s = hashSeed;
        while (tier < cappedMax) {
            s = splitMix64(s + 0x9E3779B97F4A7C15L);
            double u = (s >>> 11) * (1.0 / (1L << 53));
            double p = (tier == 1)
                ? Math.min(c.epiphanyTier2MaxProb(), (resonanceScore / c.epiphanyTier2ScoreDivisor()) + c.epiphanyTier2MasteryBonus() * ownStoredRelativeMastery)
                : Math.min(c.epiphanyTierPlusMaxProb(), (resonanceScore / c.epiphanyTierPlusScoreDivisor()) + c.epiphanyTierPlusMasteryBonus() * ownStoredRelativeMastery);
            if (u < p) {
                tier++;
            } else {
                break;
            }
        }
        return tier;
    }

    private static long splitMix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
