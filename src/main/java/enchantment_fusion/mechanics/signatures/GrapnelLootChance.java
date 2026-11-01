package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.EntityLootProvenance;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import org.jetbrains.annotations.Nullable;

/** Credits a necessary pool condition from its one actual random roll, without drawing another. */
public final class GrapnelLootChance {
    private static final ThreadLocal<Pool> POOL = new ThreadLocal<>();
    private static final ThreadLocal<Chance> CHANCE = new ThreadLocal<>();
    public static final class Pool {
        final @Nullable Pool previous;
        final @Nullable GrapnelLooting.Frame frame;
        final LootContext context;
        final @Nullable LootItemRandomChanceWithEnchantedBonusCondition condition;
        boolean evaluating, conditionsPassed, won, ambiguous;
        int visits, conditionCalls;
        Pool(@Nullable Pool previous, @Nullable GrapnelLooting.Frame frame, LootContext context,
             @Nullable LootItemRandomChanceWithEnchantedBonusCondition condition) {
            this.previous = previous; this.frame = frame; this.context = context; this.condition = condition;
            this.ambiguous = previous != null;
        }
    }
    public static final class Chance {
        final @Nullable Chance previous;
        final @Nullable Pool pool;
        final LootItemRandomChanceWithEnchantedBonusCondition condition;
        int queries, level, rolls, calculations;
        float roll, threshold;
        boolean ambiguous;
        Chance(@Nullable Chance previous, @Nullable Pool pool, LootItemRandomChanceWithEnchantedBonusCondition condition) {
            this.previous = previous; this.pool = pool; this.condition = condition; this.ambiguous = previous != null;
        }
    }
    private GrapnelLootChance() {}
    public static boolean savedByPrimary(float roll, float baseline, float enchanted) {
        return Float.isFinite(roll) && Float.isFinite(baseline) && Float.isFinite(enchanted)
            && roll >= 0.0F && roll < 1.0F && baseline >= 0.0F && baseline <= 1.0F
            && roll >= baseline && roll < enchanted;
    }
    public static @Nullable Pool beginPool(LootContext context, List<LootItemCondition> conditions) {
        Pool previous = POOL.get();
        if (previous != null) previous.ambiguous = true;
        GrapnelLooting.Frame frame = GrapnelLooting.currentForLoot(context);
        if (frame == null && previous == null) return null;
        LootItemRandomChanceWithEnchantedBonusCondition selected = null;
        int count = 0;
        for (LootItemCondition condition : conditions)
            if (condition instanceof LootItemRandomChanceWithEnchantedBonusCondition bonus && bonus.enchantment().is(Enchantments.LOOTING)) {
                selected = bonus; count++;
            }
        if (count != 1) { frame = null; selected = null; }
        Pool pool = new Pool(previous, frame, context, selected); POOL.set(pool); return pool;
    }
    public static void beginConditions(LootContext context) {
        Pool pool = POOL.get();
        if (pool == null) return;
        if (pool.context != context || pool.evaluating || ++pool.conditionCalls != 1) { pool.ambiguous = true; return; }
        pool.evaluating = true;
    }
    public static void endConditions(LootContext context, boolean passed, boolean completed) {
        Pool pool = POOL.get();
        if (pool == null) return;
        if (pool.context != context || !pool.evaluating || !completed) pool.ambiguous = true;
        pool.evaluating = false; pool.conditionsPassed = completed && passed;
    }
    public static @Nullable Chance beginChance(LootItemRandomChanceWithEnchantedBonusCondition condition, LootContext context) {
        Chance previous = CHANCE.get();
        if (previous != null) previous.ambiguous = true;
        Pool pool = POOL.get();
        boolean capture = pool != null && pool.evaluating && pool.context == context && pool.condition == condition
            && pool.frame != null && GrapnelLooting.currentForLoot(context) == pool.frame;
        if (!capture && previous == null) return null;
        Chance chance = new Chance(previous, capture ? pool : null, condition); CHANCE.set(chance); return chance;
    }
    public static void observedLevel(Holder<Enchantment> enchantment, LivingEntity owner, int level) {
        Chance chance = CHANCE.get(); Pool pool = chance == null ? null : chance.pool;
        if (pool != null && pool.frame != null && pool.frame.owner == owner && pool.frame.primary != null
            && chance.condition.enchantment().value() == enchantment.value() && pool.frame.primary.value() == enchantment.value()) {
            chance.queries++; chance.level = level;
        }
    }
    public static void observedThreshold(LootItemRandomChanceWithEnchantedBonusCondition condition, int level, float threshold) {
        Chance chance = CHANCE.get();
        if (chance != null && chance.condition == condition && chance.pool != null) {
            chance.calculations++; chance.threshold = threshold;
            if (chance.level != level) chance.ambiguous = true;
        }
    }
    public static void observedRoll(LootItemRandomChanceWithEnchantedBonusCondition condition, float roll) {
        Chance chance = CHANCE.get();
        if (chance != null && chance.condition == condition && chance.pool != null) { chance.rolls++; chance.roll = roll; }
    }
    public static void endChance(@Nullable Chance chance, boolean result, boolean completed) {
        if (chance == null) return;
        if (CHANCE.get() != chance) { CHANCE.remove(); if (chance.previous != null) chance.previous.ambiguous = true; return; }
        if (chance.previous == null) CHANCE.remove(); else CHANCE.set(chance.previous);
        Pool pool = chance.pool;
        if (pool == null || POOL.get() != pool || pool.frame == null || pool.frame.pair == null) return;
        pool.visits++;
        if (!completed || chance.ambiguous || chance.queries != 1 || chance.rolls != 1 || chance.calculations != 1
            || chance.level != pool.frame.pair.primaryLevel() || !GrapnelLooting.solePrimary(pool.frame)) {
            pool.ambiguous = true; return;
        }
        pool.won = result && savedByPrimary(chance.roll, chance.condition.unenchantedChance(), chance.threshold);
    }
    /** Snapshot after entry and pool functions, before any table-level functions or splitting. */
    public static void output(@Nullable Pool pool, ItemStack stack) {
        if (pool == null || POOL.get() != pool || pool.frame == null || pool.context == null || pool.ambiguous
            || !pool.conditionsPassed || !pool.won || pool.visits != 1 || pool.conditionCalls != 1
            || GrapnelLooting.currentForLoot(pool.context) != pool.frame) return;
        EntityLootProvenance.recordPoolResult(pool.context, stack);
    }
    public static void endPool(@Nullable Pool pool, boolean completed) {
        if (pool == null) return;
        if (POOL.get() != pool) { POOL.remove(); if (pool.previous != null) pool.previous.ambiguous = true; return; }
        if (pool.previous == null) POOL.remove(); else POOL.set(pool.previous);
        if (!completed && pool.previous != null) pool.previous.ambiguous = true;
    }
    static void clear() { POOL.remove(); CHANCE.remove(); }
}
