package chiseled_enchanting_table.mechanics.resonance;

import chiseled_enchanting_table.registry.CetTags;
import chiseled_enchanting_table.utils.CetCustomData;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Pure item-data layer for Master Tome wear (audit COUP-03 split): everything in this
 * class is a function of one {@link ItemStack} and plain numbers — no world, no block
 * entities, no scanning. The world-mutating half (candidate shelves, in-place wear,
 * regeneration handoff) stays in {@link MasterTomeWear}, which delegates here, so the
 * client tooltip and headless tests can use the exact same math as the server.
 *
 * <p>Generation fade (design audit, case 1): a tome copied from another tome is
 * stamped with {@code cet_generation = parent + 1}. Its <em>effective</em> charge cap
 * is {@code max(1, maxTomeUses - generation)} — copies of copies fade, so the
 * shelf-to-book edge can no longer mint full-charge tomes exponentially. Regeneration
 * refills {@code cet_tome_wear} but can never raise the generation cap; only a fresh
 * (generation 0) source — loot, vanilla table, direct inscription — starts a new line.
 * Generation 0 writes no NBT key at all, preserving vanilla stacking of fresh books.</p>
 *
 * <p>Scapegoat pricing (design audit, case 2): {@link ScapegoatPricing#ESSENCE} makes
 * an overflow sacrifice restore charges in proportion to the burned entry's essence
 * value relative to the most valuable entry on the tome, instead of always refilling
 * to full. {@link ScapegoatPricing#FLAT} keeps the historical full reset.</p>
 */
public final class TomeWearData {
    public static final Identifier INFINITY_ENCHANTMENT_ID = Identifier.withDefaultNamespace("infinity");
    public static final Identifier UNBREAKING_ENCHANTMENT_ID = Identifier.withDefaultNamespace("unbreaking");

    /** Datapack-extensible wear immunity (audit API-02): centralized in {@link CetTags.Enchantments#WEAR_IMMUNE}. */
    public static final TagKey<Enchantment> WEAR_IMMUNE_TAG = CetTags.Enchantments.WEAR_IMMUNE;

    public static final String NBT_WEAR_KEY = "cet_tome_wear";
    public static final String NBT_REGEN_TICKS_KEY = "cet_tome_regen_ticks";
    public static final String NBT_REGEN_BUCKET_KEY = "cet_tome_regen_bucket";
    public static final String NBT_LAST_USED_ENCHANT_KEY = "cet_last_used_enchant";
    public static final String NBT_LAST_USED_LEVEL_KEY = "cet_last_used_level";
    public static final String NBT_GENERATION_KEY = "cet_generation";

    public static final int MAX_GENERATION = 255;

    private TomeWearData() {}

    /** How an overflow (scapegoat) sacrifice is priced; datapack key {@code scapegoat_pricing}. */
    public enum ScapegoatPricing {
        ESSENCE,
        FLAT;

        public String token() {
            return switch (this) {
                case ESSENCE -> "essence";
                case FLAT -> "flat";
            };
        }

        public static ScapegoatPricing fromToken(String raw) {
            if (raw == null || raw.isBlank()) {
                return ESSENCE;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "flat", "legacy", "full" -> FLAT;
                default -> ESSENCE;
            };
        }
    }

    // ------------------------------------------------------------------
    // Wear immunity
    // ------------------------------------------------------------------

    /**
     * Returns {@code true} iff {@code stack} carries a wear-immune enchantment
     * ({@code #chiseled_enchanting_table:wear_immune}, shipping with
     * {@code minecraft:infinity}; the bare Infinity id is also accepted so a datapack
     * that empties the tag can never accidentally make Infinity tomes mortal).
     */
    /** True only for the concrete {@code minecraft:infinity} id (pure id check). */
    public static boolean hasInfinityEnchantment(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (var entry : enchants.entrySet()) {
            if (entry.getIntValue() <= 0) {
                continue;
            }
            var keyOpt = entry.getKey().unwrapKey();
            if (keyOpt.isPresent() && INFINITY_ENCHANTMENT_ID.equals(keyOpt.get().identifier())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWearImmuneEnchantment(net.minecraft.core.Holder<Enchantment> holder) {
        if (holder == null) {
            return false;
        }
        if (holder.is(WEAR_IMMUNE_TAG)) {
            return true;
        }
        var keyOpt = holder.unwrapKey();
        return keyOpt.isPresent() && INFINITY_ENCHANTMENT_ID.equals(keyOpt.get().identifier());
    }

    public static boolean hasWearImmunity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (var entry : enchants.entrySet()) {
            if (entry.getIntValue() <= 0) {
                continue;
            }
            if (isWearImmuneEnchantment(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    /** Pure lineage check: copies beyond {@code maxAllowedGeneration} forfeit wear-immunity privilege. */
    public static boolean hasEffectiveWearImmunity(
        boolean hasWearImmunityTrait,
        int generation,
        int maxAllowedGeneration
    ) {
        return hasWearImmunityTrait && clampGeneration(generation) <= Math.max(0, maxAllowedGeneration);
    }

    /** Returns {@code true} iff {@code stack} carries {@code #wear_immune} AND its copy generation is within {@code maxAllowedGeneration}. */
    public static boolean hasEffectiveWearImmunity(ItemStack stack, int maxAllowedGeneration) {
        return hasWearImmunity(stack) && getGeneration(stack) <= Math.max(0, maxAllowedGeneration);
    }

    /** Counts non-curse stored enchantments ({@code level > 0}) on {@code stack}. */
    public static int countNonCurseStoredEnchantments(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        int count = 0;
        for (var entry : enchants.entrySet()) {
            if (entry.getIntValue() > 0 && !entry.getKey().is(net.minecraft.tags.EnchantmentTags.CURSE)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Continuous wear-waiver curve for {@code #wear_immune} Master Tomes:
     * {@code nonCurseEnchantCount <= fullWaiverMaxEnchants} keeps {@code 100%} waiver;
     * each extra non-curse enchantment reduces waiver by {@code overloadDecayPerExtra} down to {@code floorWaiverChance}.
     */
    public static double computeWearImmuneWaiverChance(
        int nonCurseEnchantCount,
        int fullWaiverMaxEnchants,
        double overloadDecayPerExtra,
        double floorWaiverChance
    ) {
        if (nonCurseEnchantCount <= 0) {
            return 0.0;
        }
        int fullCap = Math.max(1, fullWaiverMaxEnchants);
        double floor = Double.isFinite(floorWaiverChance) ? Math.max(0.0, Math.min(1.0, floorWaiverChance)) : 0.30;
        if (nonCurseEnchantCount <= fullCap) {
            return 1.0;
        }
        double decayStep = Double.isFinite(overloadDecayPerExtra) ? Math.max(0.0, overloadDecayPerExtra) : 0.06;
        double raw = 1.0 - decayStep * (nonCurseEnchantCount - fullCap);
        return Math.max(floor, Math.min(1.0, raw));
    }

    public static double computeWearImmuneWaiverChance(LibraryResonance.Config cfg, int nonCurseEnchantCount) {
        LibraryResonance.Config c = cfg != null ? cfg : LibraryResonance.Config.DEFAULT;
        return computeWearImmuneWaiverChance(
            nonCurseEnchantCount,
            c.wearImmuneFullWaiverMaxEnchants(),
            c.wearImmuneOverloadWaiverDecay(),
            c.unbreakingWaiverMaxChance()
        );
    }

    // ------------------------------------------------------------------
    // Generation (pure int math is headless-testable without ItemStack)
    // ------------------------------------------------------------------

    public static int clampGeneration(int generation) {
        return Math.max(0, Math.min(MAX_GENERATION, generation));
    }

    /** Charge cap of a non-immune tome of {@code generation} under a configured cap: fades by 1 per generation, floor 1. */
    public static int effectiveMaxUses(int configuredMaxUses, int generation) {
        return effectiveMaxUses(configuredMaxUses, generation, false, 0);
    }

    /**
     * Charge cap of a tome accounting for {@code #wear_immune} copy-lineage grace generations:
     * <ul>
     *   <li>Non-immune tomes: {@code effectiveGen = generation} ({@code Gen 1 -> max - 1}, {@code Gen 2 -> max - 2}, ...).</li>
     *   <li>{@code #wear_immune} tomes: {@code effectiveGen = max(0, generation - wearImmuneMaxGeneration)}
     *       so {@code Gen 0..2} retain full {@code 4/4} capacity while {@code Gen 3 -> 3/4},
     *       {@code Gen 4 -> 2/4}, {@code Gen 5+ -> 1/4}.</li>
     * </ul>
     */
    public static int effectiveMaxUses(
        int configuredMaxUses,
        int generation,
        boolean hasWearImmunityTrait,
        int wearImmuneMaxGeneration
    ) {
        int gen = clampGeneration(generation);
        int effectiveGen = hasWearImmunityTrait
            ? Math.max(0, gen - Math.max(0, wearImmuneMaxGeneration))
            : gen;
        return Math.max(1, Math.max(1, configuredMaxUses) - effectiveGen);
    }

    /** Generation stamped onto a fresh copy of a parent of {@code parentGeneration}. */
    public static int childGeneration(int parentGeneration) {
        return clampGeneration(parentGeneration + 1);
    }

    /** Generation of a merge/transfer result: the older (more faded) parent dominates. */
    public static int mergedGeneration(int a, int b) {
        return clampGeneration(Math.max(clampGeneration(a), clampGeneration(b)));
    }

    public static int remainingCharges(int configuredMaxUses, int generation, int wearUsed) {
        return Math.max(1, effectiveMaxUses(configuredMaxUses, generation) - Math.max(0, wearUsed));
    }

    public static int remainingCharges(
        int configuredMaxUses,
        int generation,
        int wearUsed,
        boolean hasWearImmunityTrait,
        int wearImmuneMaxGeneration
    ) {
        return Math.max(
            1,
            effectiveMaxUses(configuredMaxUses, generation, hasWearImmunityTrait, wearImmuneMaxGeneration)
                - Math.max(0, wearUsed)
        );
    }

    // ------------------------------------------------------------------
    // CUSTOM_DATA accessors (wear + generation, schema-managed via CetCustomData)
    // ------------------------------------------------------------------

    public static int getGeneration(ItemStack stack) {
        return CetCustomData.read(stack)
            .map(root -> clampGeneration(root.getIntOr(NBT_GENERATION_KEY, 0)))
            .orElse(0);
    }

    /** Writes {@code cet_generation}; generation 0 removes the key so fresh books stay stackable. */
    public static void setGeneration(ItemStack stack, int generation) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int clamped = clampGeneration(generation);
        if (clamped == 0 && CetCustomData.read(stack).isEmpty()) {
            return;
        }
        CetCustomData.update(stack, tag -> {
            if (clamped == 0) {
                tag.remove(NBT_GENERATION_KEY);
            } else {
                tag.putInt(NBT_GENERATION_KEY, clamped);
            }
        });
    }

    /**
     * Raises the stack's generation to at least {@code inheritedGeneration} (never lowers it,
     * so re-inscribing an already-faded book cannot launder its lineage back to fresh).
     */
    public static int stampInheritedGeneration(ItemStack stack, int inheritedGeneration) {
        int next = mergedGeneration(getGeneration(stack), inheritedGeneration);
        setGeneration(stack, next);
        return next;
    }

    /**
     * Reads actual persisted wear on {@code stack} (including overloaded {@code N > 4} or
     * post-immunity {@code Gen >= 3} {@code #wear_immune} tomes that lost a charge).
     */
    public static int getWearUsed(ItemStack stack) {
        return CetCustomData.read(stack)
            .map(root -> Math.max(0, root.getIntOr(NBT_WEAR_KEY, 0)))
            .orElse(0);
    }

    public static void setWearUsed(ItemStack stack, int wearUsed) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int clamped = Math.max(0, wearUsed);
        if (clamped == 0 && CetCustomData.read(stack).isEmpty()) {
            return;
        }
        CetCustomData.update(stack, tag -> {
            if (clamped == 0) {
                tag.remove(NBT_WEAR_KEY);
                tag.remove(NBT_REGEN_TICKS_KEY);
                tag.remove(NBT_REGEN_BUCKET_KEY);
            } else {
                tag.putInt(NBT_WEAR_KEY, clamped);
            }
        });
    }

    /**
     * Records which enchantment most recently consumed this tome's charges, so the Tome
     * page can say what is actually recharging instead of showing a bare timer (field
     * feedback 2026-10-25). Stored in CUSTOM_DATA next to the wear keys; a full recovery
     * keeps the record (it is history, not state).
     */
    public static void recordLastUsed(ItemStack stack, net.minecraft.resources.Identifier enchantId, int level) {
        if (stack == null || stack.isEmpty() || enchantId == null) {
            return;
        }
        CetCustomData.update(stack, tag -> {
            tag.putString(NBT_LAST_USED_ENCHANT_KEY, enchantId.toString());
            tag.putInt(NBT_LAST_USED_LEVEL_KEY, Math.max(1, level));
        });
    }

    /** Identifier string of the last consumed enchantment, or {@code ""} when never recorded. */
    public static String getLastUsedEnchant(ItemStack stack) {
        return CetCustomData.read(stack)
            .map(root -> root.getStringOr(NBT_LAST_USED_ENCHANT_KEY, ""))
            .orElse("");
    }

    public static int getLastUsedLevel(ItemStack stack) {
        return CetCustomData.read(stack)
            .map(root -> Math.max(0, root.getIntOr(NBT_LAST_USED_LEVEL_KEY, 0)))
            .orElse(0);
    }

    public static int effectiveMaxUses(ItemStack stack, int configuredMaxUses) {
        return effectiveMaxUses(
            stack,
            configuredMaxUses,
            chiseled_enchanting_table.BundledDefaults.WEAR_IMMUNE_MAX_GENERATION
        );
    }

    public static int effectiveMaxUses(ItemStack stack, int configuredMaxUses, int wearImmuneMaxGeneration) {
        return effectiveMaxUses(
            configuredMaxUses,
            getGeneration(stack),
            hasWearImmunity(stack),
            wearImmuneMaxGeneration
        );
    }

    public static int remainingCharges(ItemStack stack, int configuredMaxUses) {
        return remainingCharges(
            stack,
            configuredMaxUses,
            chiseled_enchanting_table.BundledDefaults.WEAR_IMMUNE_MAX_GENERATION
        );
    }

    public static int remainingCharges(ItemStack stack, int configuredMaxUses, int wearImmuneMaxGeneration) {
        return remainingCharges(
            configuredMaxUses,
            getGeneration(stack),
            getWearUsed(stack),
            hasWearImmunity(stack),
            wearImmuneMaxGeneration
        );
    }

    // ------------------------------------------------------------------
    // Scapegoat overflow pricing
    // ------------------------------------------------------------------

    /**
     * Wear value after an overflow sacrifice. {@code FLAT} (or an unpriceable tome) is
     * the historical full reset to 0. {@code ESSENCE} restores
     * {@code round(maxUses * burned/top)} charges — at least one — so burning a junk
     * 1-level entry on a tome whose crown jewel is worth 6x more only buys back a
     * sliver of durability; the rest must come from library regeneration.
     */
    public static int computeOverflowNextWear(
        ScapegoatPricing pricing,
        int effectiveMaxUses,
        int burnedEssence,
        int topEssenceInTome
    ) {
        int safeMax = Math.max(1, effectiveMaxUses);
        if (pricing == null || pricing == ScapegoatPricing.FLAT || topEssenceInTome <= 0) {
            return 0;
        }
        double ratio = Math.max(0.0, Math.min(1.0, (double) Math.max(0, burnedEssence) / (double) topEssenceInTome));
        int restored = (int) Math.round(safeMax * ratio);
        return Math.max(0, safeMax - Math.max(1, restored));
    }
}
