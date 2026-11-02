package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.EntityLootProvenance;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jetbrains.annotations.Nullable;

/** Looting ▷ Lure: retain the real primary, then mark only a proven, successful death-loot output. */
public final class GrapnelLooting {
    public static final String LOOTING = "minecraft:looting";
    private static final ThreadLocal<Action> ACTION = new ThreadLocal<>();
    private static final ThreadLocal<Frame> DAMAGE = new ThreadLocal<>();
    private static final ThreadLocal<Count> COUNT = new ThreadLocal<>();
    private static final ThreadLocal<EquipmentScope> EQUIPMENT = new ThreadLocal<>();

    /** Small independently testable completion gate; death and positive net loss must each occur once. */
    public static final class Evidence {
        private int losses, deaths;
        private boolean validLoss, ambiguous;
        public void damage(float loss) { losses++; validLoss = Float.isFinite(loss) && loss > 0; }
        public void death() { deaths++; }
        public void nested() { ambiguous = true; }
        public boolean confirmed(boolean accepted) { return accepted && !ambiguous && losses == 1 && validLoss && deaths == 1; }
    }
    /** The outer virtual hurt call has to accept the damage too; a superclass return is not enough. */
    public static final class Action {
        final @Nullable Action previous;
        final @Nullable LivingEntity victim;
        final @Nullable DamageSource source;
        @Nullable Frame frame;
        boolean ambiguous;
        Action(@Nullable Action previous, @Nullable LivingEntity victim, @Nullable DamageSource source) {
            this.previous = previous; this.victim = victim; this.source = source;
        }
    }
    public static final class Frame {
        final @Nullable Frame previous;
        final @Nullable ServerLevel level;
        final @Nullable ServerPlayer owner;
        final @Nullable LivingEntity victim;
        final @Nullable DamageSource source;
        final @Nullable ItemStack weapon, snapshot;
        final @Nullable FusedPair pair;
        final @Nullable Holder<Enchantment> primary;
        final boolean eligible;
        final Evidence evidence = new Evidence();
        boolean inDeath;
        int lootCalls;
        @Nullable EntityLootProvenance.Scope loot;
        final Deque<EntityLootProvenance.Scope> lootScopes = new ArrayDeque<>();
        @Nullable BlockPos origin;
        List<EntityLootProvenance.Verified> drops = List.of();
        private Frame(@Nullable Frame previous, @Nullable ServerLevel level, @Nullable ServerPlayer owner,
                      @Nullable LivingEntity victim, @Nullable DamageSource source, @Nullable ItemStack weapon,
                      @Nullable FusedPair pair, @Nullable Holder<Enchantment> primary, boolean eligible) {
            this.previous = previous; this.level = level; this.owner = owner; this.victim = victim; this.source = source;
            this.weapon = weapon; this.snapshot = weapon == null ? null : weapon.copy();
            this.pair = pair; this.primary = primary; this.eligible = eligible;
        }
    }
    public static final class Count {
        final @Nullable Count previous;
        final @Nullable Frame frame;
        final @Nullable LootContext context;
        final @Nullable ItemStack stack, before;
        final int beforeCount;
        int queries, level;
        boolean ambiguous;
        private Count(@Nullable Count previous, @Nullable Frame frame, @Nullable LootContext context, @Nullable ItemStack stack) {
            this.previous = previous; this.frame = frame; this.context = context; this.stack = stack;
            this.beforeCount = stack == null ? 0 : stack.getCount();
            this.before = stack == null || beforeCount < 1 ? null : stack.copyWithCount(1);
        }
    }
    public static final class EquipmentScope {
        final @Nullable EquipmentScope previous;
        final @Nullable Frame frame;
        int iterations;
        EquipmentScope(@Nullable EquipmentScope previous, @Nullable Frame frame) { this.previous = previous; this.frame = frame; }
    }
    private GrapnelLooting() {}
    public static void init() { ConfirmedLivingDamage.register(GrapnelLooting::observedDamage); }
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && LOOTING.equals(pair.primaryId()) && GrapnelSkills.LURE.equals(pair.secondaryId());
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) { return MeleeAttackThemes.supportsWeapon(roles); }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (isPair(pair) && GrapnelSkills.applies(pair, stack) && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        if (best != null) return best; // preserve the existing Grapnel selection on legacy multi-fusion gear
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if ((MeleeDisarmSkills.applies(pair, stack) || LootingFireSeedSkills.applies(pair, stack))
                && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        return best;
    }
    public static int highestLevel(List<FusedPair> pairs, List<RetainedEnchant> retained) {
        int best = 0;
        for (FusedPair pair : pairs) if (isPair(pair)) best = Math.max(best, pair.primaryLevel());
        for (RetainedEnchant item : retained) if (LOOTING.equals(item.id())) best = Math.max(best, item.level());
        return best;
    }
    public static int projectedLevel(int vanilla, int missing, boolean matchingSlot) {
        return vanilla > 0 ? vanilla : matchingSlot ? Math.max(0, missing) : 0;
    }
    /** Do not claim one primary's bonus when another fusion or retained entry could also explain it. */
    public static boolean unambiguous(List<FusedPair> pairs, List<RetainedEnchant> retained, FusedPair selected) {
        int sources = 0;
        for (FusedPair pair : pairs) if (LOOTING.equals(pair.primaryId()) || LOOTING.equals(pair.secondaryId())) {
            if (!pair.equals(selected)) return false;
            sources++;
        }
        return sources == 1 && retained.stream().noneMatch(item -> LOOTING.equals(item.id()));
    }
    public static boolean positiveIncrease(int before, int after, int calls, int actualLevel, int pairLevel, boolean ambiguous) {
        return !ambiguous && before > 0 && after > before && calls == 1 && pairLevel > 0 && actualLevel == pairLevel;
    }
    static boolean held(Frame frame) {
        return frame.owner != null && frame.level != null && frame.weapon != null && frame.pair != null && frame.primary != null
            && frame.owner.level() == frame.level && frame.owner.getMainHandItem() == frame.weapon && !frame.weapon.isEmpty()
            && frame.weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(frame.pair)
            && (GrapnelSkills.applies(frame.pair, frame.weapon) || MeleeDisarmSkills.applies(frame.pair, frame.weapon)
                || LootingFireSeedSkills.applies(frame.pair, frame.weapon))
            && frame.primary.value().matchingSlot(EquipmentSlot.MAINHAND);
    }
    private static int wanted(Frame frame) {
        var pairs = frame.weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs();
        int best = highestLevel(pairs, frame.weapon.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()));
        for (FusedPair pair : pairs) if (MeleeDisarmSkills.applies(pair, frame.weapon) || LootingFireSeedSkills.applies(pair, frame.weapon))
            best = Math.max(best, pair.primaryLevel());
        return best;
    }
    static boolean solePrimary(Frame frame) {
        if (!held(frame) || !unambiguous(frame.weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs(),
            frame.weapon.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()), frame.pair)) return false;
        for (var entry : frame.primary.value().getSlotItems(frame.owner).entrySet()) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(frame.primary, entry.getValue());
            if (entry.getKey() == EquipmentSlot.MAINHAND) {
                if (level > 0 && level != frame.pair.primaryLevel()) return false;
            } else if (level > 0) return false;
        }
        return true;
    }
    private static boolean markEligible(ServerLevel level, ServerPlayer owner, LivingEntity victim, DamageSource source, FusedPair pair) {
        Action action = ACTION.get();
        boolean direct = source.getDirectEntity() == owner && action != null && !action.ambiguous
            && action.victim == victim && action.source == source;
        return isPair(pair) && owner.isAlive() && !owner.isCreative() && !owner.isSpectator() && victim instanceof Mob && victim.isAlive()
            && level.getEntity(victim.getUUID()) == victim && direct && GrapnelSkills.eligibleTarget(owner, victim, pair)
            && !level.getServer().isUnderSpawnProtection(level, victim.blockPosition(), owner)
            && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
            && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && !MatrixEffectExecutor.isExecuting()
            && !GlazedStrikeEffect.isApplyingBonus() && !AmberSealSkill.isApplyingBurst() && !LoyaltyGuard.isExecuting()
            && !Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get());
    }
    /** Capture the full virtual damage call, not only a subclass's call to super.hurtServer. */
    public static @Nullable Action beginAction(Entity target, DamageSource source) {
        Action previous = ACTION.get();
        if (previous != null) previous.ambiguous = true;
        if (target instanceof LivingEntity victim && source.getEntity() instanceof ServerPlayer owner
            && source.getDirectEntity() == owner && owner.level() == victim.level() && selected(owner.getMainHandItem()) != null) {
            Action action = new Action(previous, victim, source); ACTION.set(action); return action;
        }
        if (previous == null) return null;
        Action sentinel = new Action(previous, null, null); ACTION.set(sentinel); return sentinel;
    }
    public static void endAction(@Nullable Action action, boolean accepted) {
        if (action == null) return;
        if (ACTION.get() != action) { ACTION.remove(); if (action.previous != null) action.previous.ambiguous = true; return; }
        if (action.previous == null) ACTION.remove(); else ACTION.set(action.previous);
        Frame frame = action.frame;
        if (!accepted || action.ambiguous || frame == null || action.victim != frame.victim || action.source != frame.source
            || frame.owner == null || frame.victim == null || frame.level == null || frame.weapon == null || frame.snapshot == null
            || frame.pair == null || frame.origin == null || frame.drops.isEmpty() || !held(frame)
            || !frame.owner.isAlive() || frame.owner.isCreative() || frame.owner.isSpectator() || !frame.victim.isDeadOrDying()
            || frame.victim.level() != frame.level || !ItemStack.matches(frame.weapon, frame.snapshot) || !solePrimary(frame)) return;
        List<ItemEntity> verified = frame.drops.stream()
            .filter(drop -> GrapnelDropTargets.unchanged(frame.owner, drop.entity(), drop.stack(), drop.snapshot()))
            .map(EntityLootProvenance.Verified::entity).toList();
        GrapnelSkills.markDrops(frame.owner, frame.weapon, frame.pair, EquipmentSlot.MAINHAND, frame.origin, verified);
    }
    /** Wrap the real victim's damage call before death loot can run. */
    public static @Nullable Frame beginDamage(LivingEntity victim, ServerLevel level, DamageSource source) {
        Frame previous = DAMAGE.get();
        if (previous != null) previous.evidence.nested();
        if (source.getEntity() instanceof ServerPlayer owner && owner.level() == level && victim.level() == level) {
            ItemStack weapon = owner.getMainHandItem(); FusedPair pair = selected(weapon);
            Holder<Enchantment> primary = pair == null ? null : FusionStationOperations.lookupEnchantment(level.registryAccess(), LOOTING).orElse(null);
            if (pair != null && primary != null && primary.value().matchingSlot(EquipmentSlot.MAINHAND)) {
                Frame frame = new Frame(previous, level, owner, victim, source, weapon, pair, primary,
                    markEligible(level, owner, victim, source, pair));
                DAMAGE.set(frame); return frame;
            }
        }
        if (previous == null) return null;
        Frame sentinel = new Frame(previous, null, null, null, null, null, null, null, false);
        DAMAGE.set(sentinel); return sentinel;
    }
    private static void observedDamage(ConfirmedLivingDamage.Hit hit) {
        Frame frame = DAMAGE.get();
        if (frame != null && frame.level == hit.level() && frame.victim == hit.victim() && frame.source == hit.source())
            frame.evidence.damage(hit.loss());
    }
    /** Fabric fires AFTER_DEATH after the loot call; a totem or cancelled death never reaches it. */
    public static void afterDeath(LivingEntity victim, DamageSource source) {
        Frame frame = DAMAGE.get();
        if (frame != null && frame.victim == victim && frame.source == source) frame.evidence.death();
    }
    public static @Nullable Frame beginDeath(LivingEntity victim, ServerLevel level, DamageSource source) {
        Frame frame = DAMAGE.get();
        if (frame == null) return null;
        if (frame.victim != victim || frame.level != level || frame.source != source || frame.inDeath) {
            frame.evidence.nested(); return null;
        }
        frame.inDeath = true; return frame;
    }
    public static void endDeath(@Nullable Frame frame) {
        if (frame == null) return;
        if (DAMAGE.get() != frame || !frame.inDeath) frame.evidence.nested();
        frame.inDeath = false;
    }
    public static @Nullable EntityLootProvenance.Scope beginLoot(LivingEntity victim, ServerLevel level, DamageSource source) {
        Frame frame = DAMAGE.get();
        if (frame == null || !frame.inDeath || frame.victim != victim || frame.level != level || frame.source != source || !held(frame)) return null;
        if (++frame.lootCalls != 1 || frame.loot != null) frame.evidence.nested();
        EntityLootProvenance.Scope scope = EntityLootProvenance.open(level, frame.owner, victim, source);
        frame.lootScopes.push(scope); frame.loot = scope;
        return scope;
    }
    public static void endLoot(@Nullable EntityLootProvenance.Scope scope, boolean completed) {
        if (scope == null) return;
        Frame frame = DAMAGE.get();
        List<EntityLootProvenance.Verified> drops = EntityLootProvenance.close(scope, completed);
        if (frame == null || frame.loot != scope || frame.lootScopes.peek() != scope) {
            if (frame != null) { frame.evidence.nested(); frame.loot = null; frame.lootScopes.clear(); }
            return;
        }
        frame.lootScopes.pop(); frame.loot = frame.lootScopes.peek();
        // Nested provenance is ambiguous, but its completion must not disable the enclosing
        // action's native-primary projection for the rest of the original loot table.
        if (frame.loot == null) { frame.origin = scope.origin(); frame.drops = drops; }
    }
    public static void endDamage(@Nullable Frame frame, boolean accepted) {
        if (frame == null) return;
        if (DAMAGE.get() != frame) {
            DAMAGE.remove(); if (frame.previous != null) frame.previous.evidence.nested(); return;
        }
        if (frame.previous == null) DAMAGE.remove(); else DAMAGE.set(frame.previous);
        Action action = ACTION.get();
        if (!frame.eligible || !frame.evidence.confirmed(accepted) || frame.inDeath || frame.loot != null || !frame.lootScopes.isEmpty() || frame.lootCalls != 1
            || action == null || action.ambiguous || action.victim != frame.victim || action.source != frame.source) return;
        if (action.frame != null) { action.ambiguous = true; return; }
        action.frame = frame;
    }
    /** Only the actual entity-loot query sees a missing mainhand primary; native Looting wins unchanged. */
    public static int lootLevel(Holder<Enchantment> enchantment, LivingEntity owner, int vanilla) {
        Frame frame = DAMAGE.get(); int result = vanilla;
        if (frame != null && frame.owner == owner && frame.primary != null && enchantment.is(Enchantments.LOOTING)
            && enchantment.value() == frame.primary.value() && EntityLootProvenance.current(frame.loot) && held(frame))
            result = projectedLevel(vanilla, wanted(frame), true);
        GrapnelLootChance.observedLevel(enchantment, owner, result);
        Count count = COUNT.get();
        if (count != null && count.frame == frame && frame != null && frame.owner == owner && frame.primary != null
            && enchantment.value() == frame.primary.value() && enchantment.is(Enchantments.LOOTING)) {
            count.queries++; count.level = result;
        }
        return result;
    }
    static @Nullable Frame currentForLoot(LootContext context) {
        Frame frame = DAMAGE.get();
        return frame != null && frame.eligible && EntityLootProvenance.current(frame.loot)
            && EntityLootProvenance.canCapture(context) && solePrimary(frame) ? frame : null;
    }
    public static @Nullable Count beginCount(Holder<Enchantment> enchantment, ItemStack stack, LootContext context) {
        Count previous = COUNT.get();
        if (previous != null) previous.ambiguous = true;
        Frame frame = DAMAGE.get();
        boolean capture = frame != null && frame.eligible && frame.primary != null && enchantment.is(Enchantments.LOOTING)
            && enchantment.value() == frame.primary.value() && EntityLootProvenance.current(frame.loot)
            && EntityLootProvenance.canCapture(context) && solePrimary(frame);
        if (!capture && previous == null) return null;
        Count count = new Count(previous, capture ? frame : null, capture ? context : null, capture ? stack : null);
        COUNT.set(count); return count;
    }
    public static void endCount(@Nullable Count count, @Nullable ItemStack result, boolean completed) {
        if (count == null) return;
        if (COUNT.get() != count) { COUNT.remove(); if (count.previous != null) count.previous.ambiguous = true; return; }
        if (count.previous == null) COUNT.remove(); else COUNT.set(count.previous);
        Frame frame = count.frame;
        if (!completed || frame == null || frame != DAMAGE.get() || count.stack == null || count.before == null || result != count.stack
            || count.context == null || frame.pair == null || !solePrimary(frame)
            || !positiveIncrease(count.beforeCount, result.getCount(), count.queries, count.level, frame.pair.primaryLevel(), count.ambiguous)) return;
        EntityLootProvenance.recordIncrease(count.context, result, count.before, count.beforeCount);
    }
    /** Scope a read-only enchanted copy to the vanilla equipment-drop-chance iteration. */
    public static @Nullable EquipmentScope beginEquipment(ServerLevel level, LivingEntity entity, DamageSource source) {
        EquipmentScope previous = EQUIPMENT.get(); Frame frame = DAMAGE.get();
        boolean applies = frame != null && frame.inDeath && frame.level == level && frame.owner == entity && frame.source == source && held(frame);
        if (!applies && previous == null) return null;
        EquipmentScope scope = new EquipmentScope(previous, applies ? frame : null);
        EQUIPMENT.set(scope); return scope;
    }
    public static void endEquipment(@Nullable EquipmentScope scope) {
        if (scope == null) return;
        if (EQUIPMENT.get() != scope) { EQUIPMENT.remove(); return; }
        if (scope.previous == null) EQUIPMENT.remove(); else EQUIPMENT.set(scope.previous);
    }
    public static void enterEquipmentIteration() { EquipmentScope scope = EQUIPMENT.get(); if (scope != null) scope.iterations++; }
    public static void leaveEquipmentIteration() { EquipmentScope scope = EQUIPMENT.get(); if (scope != null && scope.iterations > 0) scope.iterations--; }
    public static ItemStack equipmentStack(LivingEntity owner, EquipmentSlot slot, ItemStack actual) {
        EquipmentScope scope = EQUIPMENT.get(); Frame frame = scope == null ? null : scope.frame;
        if (frame == null || scope.iterations != 1 || DAMAGE.get() != frame || frame.owner != owner || slot != EquipmentSlot.MAINHAND
            || frame.weapon != actual || !held(frame)) return actual;
        int nativeLevel = 0;
        for (ItemStack item : frame.primary.value().getSlotItems(owner).values())
            nativeLevel = Math.max(nativeLevel, EnchantmentHelper.getItemEnchantmentLevel(frame.primary, item));
        int projected = projectedLevel(nativeLevel, wanted(frame), true);
        if (nativeLevel > 0 || projected <= 0) return actual;
        ItemStack copy = actual.copy();
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(copy.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        enchantments.set(frame.primary, projected); EnchantmentHelper.setEnchantments(copy, enchantments.toImmutable());
        return copy;
    }
    public static void clearServer() { ACTION.remove(); DAMAGE.remove(); COUNT.remove(); EQUIPMENT.remove(); GrapnelLootChance.clear(); EntityLootProvenance.clear(); }
}
