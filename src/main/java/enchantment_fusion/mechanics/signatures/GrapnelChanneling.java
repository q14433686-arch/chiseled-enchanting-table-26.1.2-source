package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 40.1: bind a lure to a lightning entity actually summoned by this trident's Channeling effect. */
public final class GrapnelChanneling {
    public static final String CHANNELING = "minecraft:channeling";
    private enum Kind { TARGET, BLOCK }
    private static final ThreadLocal<Action> ACTION = new ThreadLocal<>();
    private static final ThreadLocal<Chain> CHAIN = new ThreadLocal<>();
    private static final ThreadLocal<Effect> EFFECT = new ThreadLocal<>();

    public static final class Action {
        final @Nullable Action previous; final Kind kind; final ServerPlayer owner; final ServerLevel level;
        final ThrownTrident projectile; final ItemStack stack; final FusedPair pair; final long tick;
        final @Nullable LivingEntity target; final @Nullable BlockPos block; final @Nullable Vec3 hitPosition;
        final @Nullable BlockState blockState; @Nullable DamageSource source; @Nullable LightningBolt bolt; @Nullable Vec3 strike;
        boolean damageSeen, confirmedDamage, effectSeen, ambiguous;
        Action(@Nullable Action previous, Kind kind, ServerPlayer owner, ThrownTrident projectile, ItemStack stack,
               FusedPair pair, @Nullable LivingEntity target, @Nullable BlockPos block, @Nullable Vec3 hitPosition,
               @Nullable BlockState blockState) {
            this.previous = previous; this.kind = kind; this.owner = owner; this.level = owner.level();
            this.projectile = projectile; this.stack = stack; this.pair = pair; this.tick = level.getGameTime();
            this.target = target; this.block = block; this.hitPosition = hitPosition; this.blockState = blockState;
        }
    }
    public static final class Damage {
        final Action action; final DamageSource source; final float health, absorption;
        Damage(Action action, DamageSource source) {
            this.action = action; this.source = source; this.health = action.target.getHealth();
            this.absorption = action.target.getAbsorptionAmount();
        }
    }
    public static final class Chain {
        final @Nullable Chain previous; final Kind kind; final ServerLevel level; final ServerPlayer owner;
        final ThrownTrident projectile; final ItemStack stack; final Entity affected;
        final @Nullable DamageSource source; final @Nullable Vec3 position; final @Nullable BlockState state;
        boolean seen;
        Chain(@Nullable Chain previous, Kind kind, ServerLevel level, ServerPlayer owner, ThrownTrident projectile,
              ItemStack stack, Entity affected, @Nullable DamageSource source, @Nullable Vec3 position, @Nullable BlockState state) {
            this.previous = previous; this.kind = kind; this.level = level; this.owner = owner; this.projectile = projectile;
            this.stack = stack; this.affected = affected; this.source = source; this.position = position; this.state = state;
        }
        public boolean seen() { return seen; }
    }
    public static final class Effect {
        final @Nullable Effect previous; final @Nullable Action action;
        Effect(@Nullable Effect previous, @Nullable Action action) { this.previous = previous; this.action = action; }
    }
    private GrapnelChanneling() {}

    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && CHANNELING.equals(pair.primaryId()) && GrapnelSkills.LURE.equals(pair.secondaryId());
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) {
        return roles != null && roles.contains(FusionCarrier.TRIDENT);
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (isPair(pair) && GrapnelSkills.applies(pair, stack) && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        return best;
    }
    static boolean unambiguous(List<FusedPair> pairs, List<RetainedEnchant> retained) {
        return pairs.stream().filter(pair -> pair.involves(CHANNELING)).count() == 1
            && retained.stream().noneMatch(enchantment -> CHANNELING.equals(enchantment.id()));
    }
    private static boolean unambiguous(ItemStack stack) {
        return unambiguous(stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs(),
            stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.of()));
    }
    private static @Nullable FusedPair actionPair(ThrownTrident projectile, ServerPlayer owner) {
        ItemStack stack = projectile.getWeaponItem(); FusedPair pair = selected(stack);
        return pair != null && !MatrixEffectExecutor.isExecuting() && unambiguous(stack)
            && GrapnelTridentFlight.valid(owner, projectile, stack, pair) ? pair : null;
    }
    public static @Nullable Action beginEntity(ThrownTrident projectile, Entity victim) {
        Action previous = ACTION.get(); if (previous != null) previous.ambiguous = true;
        if (!(projectile.getOwner() instanceof ServerPlayer owner) || !(victim instanceof LivingEntity target)
            || !target.isAlive() || target.level() != owner.level()) return null;
        FusedPair pair = actionPair(projectile, owner);
        if (pair == null || !GrapnelSkills.eligibleTarget(owner, target, pair)) return null;
        Action action = new Action(previous, Kind.TARGET, owner, projectile, projectile.getWeaponItem(), pair, target, null, null, null);
        ACTION.set(action); return action;
    }
    public static @Nullable Action beginBlock(ThrownTrident projectile, ServerLevel level, BlockHitResult hit, ItemStack stack) {
        Action previous = ACTION.get(); if (previous != null) previous.ambiguous = true;
        if (!(projectile.getOwner() instanceof ServerPlayer owner) || owner.level() != level
            || projectile.getWeaponItem() != stack) return null;
        FusedPair pair = actionPair(projectile, owner); BlockPos block = hit.getBlockPos().immutable();
        Vec3 center = Vec3.atCenterOf(block);
        if (pair == null || !GrapnelPositionTargets.allowedOrigin(owner, center, false)) return null;
        Action action = new Action(previous, Kind.BLOCK, owner, projectile, stack, pair, null, block,
            block.clampLocationWithin(hit.getLocation()), level.getBlockState(block));
        ACTION.set(action); return action;
    }
    public static @Nullable Damage beforeDamage(ThrownTrident projectile, Entity target, DamageSource source) {
        Action action = ACTION.get();
        if (action == null || action.kind != Kind.TARGET || action.projectile != projectile || action.target != target) return null;
        if (action.damageSeen) { action.ambiguous = true; return null; }
        action.damageSeen = true;
        if (source.getDirectEntity() != projectile || source.getEntity() != action.owner
            || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
            || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) return null;
        return new Damage(action, source);
    }
    public static void afterDamage(@Nullable Damage damage, boolean accepted) {
        if (damage == null) return;
        Action action = damage.action;
        if (ACTION.get() != action || action.target == null) { action.ambiguous = true; return; }
        action.source = damage.source;
        action.confirmedDamage = accepted && ConfirmedMeleeMath.actualLoss(damage.health, damage.absorption,
            action.target.getHealth(), action.target.getAbsorptionAmount()) > 0;
    }
    /** Finish only after the native post-attack/on-block effects and their cause/position updates have returned. */
    public static void endAction(@Nullable Action action, boolean completed) {
        if (action == null) return;
        if (ACTION.get() != action) { ACTION.remove(); return; }
        if (action.previous == null) ACTION.remove(); else ACTION.set(action.previous);
        LightningBolt bolt = action.bolt;
        if (!completed || action.ambiguous || !action.effectSeen || bolt == null || action.strike == null || !bolt.isAlive()
            || !finite(bolt.position()) || bolt.position().distanceToSqr(action.strike) > 1.0E-8D
            || bolt.level() != action.level || action.level.getEntity(bolt.getUUID()) != bolt || bolt.getCause() != action.owner
            || action.owner.level() != action.level || action.level.getGameTime() != action.tick
            || !unambiguous(action.stack) || !GrapnelTridentFlight.valid(action.owner, action.projectile, action.stack, action.pair)) return;
        if (action.kind == Kind.TARGET && action.confirmedDamage && action.target != null && action.target.isAlive()
            && GrapnelSkills.eligibleTarget(action.owner, action.target, action.pair)) {
            GrapnelSkills.markImpact(action.owner, action.target, action.stack, action.pair, action.projectile);
        } else if (action.kind == Kind.BLOCK && action.block != null && action.blockState != null
            && action.level.getBlockState(action.block).equals(action.blockState)) {
            GrapnelSkills.markProjectilePosition(action.owner, action.projectile, action.stack, action.pair, Vec3.atCenterOf(action.block));
        }
    }

    private static @Nullable Chain beginChain(Kind kind, ServerLevel level, ItemStack stack, Entity projectile, Entity affected,
                                               @Nullable DamageSource source, @Nullable Vec3 position, @Nullable BlockState state) {
        Chain previous = CHAIN.get(); Action action = ACTION.get();
        if (previous != null && action != null) action.ambiguous = true;
        if (stack == null || stack.isEmpty() || !(projectile instanceof ThrownTrident trident)
            || !(trident.getOwner() instanceof ServerPlayer owner) || owner.level() != level || trident.level() != level
            || trident.getWeaponItem() != stack || MatrixEffectExecutor.isExecuting()
            || (selected(stack) == null && MeleeLightningSkills.primaryProjectile(level, stack, projectile) == null)) return null;
        Chain chain = new Chain(previous, kind, level, owner, trident, stack, affected, source, position, state);
        CHAIN.set(chain); return chain;
    }
    public static @Nullable Chain beginPost(ServerLevel level, ItemStack stack, Entity victim, DamageSource source) {
        if (!(source.getDirectEntity() instanceof ThrownTrident trident) || source.getEntity() != trident.getOwner()) return null;
        return beginChain(Kind.TARGET, level, stack, trident, victim, source, null, null);
    }
    public static @Nullable Chain beginBlockEffects(ServerLevel level, ItemStack stack, Entity owner, Entity projectile,
                                                     @Nullable EquipmentSlot slot, Vec3 position, BlockState state) {
        if (!(projectile instanceof ThrownTrident trident) || trident.getOwner() != owner || slot != null) return null;
        return beginChain(Kind.BLOCK, level, stack, projectile, projectile, null, position, state);
    }
    public static void endChain(@Nullable Chain chain) {
        if (chain == null) return;
        if (CHAIN.get() != chain) { CHAIN.remove(); return; }
        if (chain.previous == null) CHAIN.remove(); else CHAIN.set(chain.previous);
    }
    private static boolean primary(ServerLevel level, Enchantment enchantment) {
        return FusionStationOperations.lookupEnchantment(level.registryAccess(), CHANNELING)
            .map(holder -> holder.value() == enchantment).orElse(false);
    }
    private static @Nullable Effect beginEffect(Enchantment enchantment, ServerLevel level, int rank, EnchantedItemInUse item,
                                                 Kind kind, Entity affected, @Nullable DamageSource source,
                                                 @Nullable Vec3 position, @Nullable BlockState state, boolean correctSide) {
        Chain chain = CHAIN.get(); Effect previous = EFFECT.get();
        if (chain == null && previous == null) return null;
        if (previous != null && previous.action != null) previous.action.ambiguous = true;
        Action action = ACTION.get(), candidate = null;
        boolean matches = chain != null && chain.kind == kind && chain.level == level && chain.affected == affected
            && chain.stack == item.itemStack() && chain.owner == item.owner() && chain.source == source
            && (kind != Kind.BLOCK || (chain.position != null && chain.position.equals(position) && chain.state == state))
            && correctSide && primary(level, enchantment);
        if (matches) {
            if (chain.seen && action != null) action.ambiguous = true;
            chain.seen = true;
            if (action != null && action.kind == kind && action.owner == chain.owner && action.projectile == chain.projectile
                && action.stack == chain.stack && (kind == Kind.BLOCK || (action.target == affected && action.source == source))) {
                if (action.effectSeen || rank != action.pair.primaryLevel()
                    || (kind == Kind.BLOCK && (action.hitPosition == null || !action.hitPosition.equals(position)
                        || action.blockState != state))) action.ambiguous = true;
                action.effectSeen = true; candidate = action;
            }
        }
        Effect effect = new Effect(previous, candidate); EFFECT.set(effect); return effect;
    }
    public static @Nullable Effect beginPostEffect(Enchantment enchantment, ServerLevel level, int rank, EnchantedItemInUse item,
                                                    EnchantmentTarget side, Entity victim, DamageSource source) {
        return beginEffect(enchantment, level, rank, item, Kind.TARGET, victim, source, null, null,
            side == EnchantmentTarget.ATTACKER && item.inSlot() == EquipmentSlot.MAINHAND);
    }
    public static @Nullable Effect beginBlockEffect(Enchantment enchantment, ServerLevel level, int rank, EnchantedItemInUse item,
                                                     Entity projectile, Vec3 position, BlockState state) {
        return beginEffect(enchantment, level, rank, item, Kind.BLOCK, projectile, null, position, state, item.inSlot() == null);
    }
    public static void endEffect(@Nullable Effect effect, boolean completed) {
        if (effect == null) return;
        if (effect.action != null && (!completed || EFFECT.get() != effect)) effect.action.ambiguous = true;
        if (EFFECT.get() != effect) { EFFECT.remove(); return; }
        if (effect.previous == null) EFFECT.remove(); else EFFECT.set(effect.previous);
    }
    /** Observe the return value of this exact SummonEntityEffect, not a weather check or a nearby strike. */
    public static void summoned(ServerLevel level, EnchantedItemInUse item, Entity affected, Vec3 position, @Nullable Entity spawned) {
        Effect effect = EFFECT.get(); Action action = effect == null ? null : effect.action;
        if (action == null || action.ambiguous || !(spawned instanceof LightningBolt bolt) || !bolt.isAlive()
            || level != action.level || item.itemStack() != action.stack || item.owner() != action.owner
            || level.getEntity(bolt.getUUID()) != bolt) return;
        Entity expected = action.kind == Kind.TARGET ? action.target : action.projectile;
        Vec3 expectedPosition = action.kind == Kind.TARGET && action.target != null ? action.target.position() : action.hitPosition;
        if (affected != expected || expectedPosition == null || !finite(position) || position.distanceToSqr(expectedPosition) > 1.0E-8D) return;
        if (action.bolt != null) action.ambiguous = true; else { action.bolt = bolt; action.strike = position; }
    }
    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    public static void clearServer() { ACTION.remove(); CHAIN.remove(); EFFECT.remove(); ChannelingTridentPrimary.clearServer(); }
}
