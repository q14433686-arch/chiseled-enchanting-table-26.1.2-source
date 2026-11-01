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
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Correlates a real spear attack, its accepted damage, and the same native Lunge impulse. */
public final class GrapnelLunge {
    public static final String LUNGE = "minecraft:lunge";
    private static final ThreadLocal<Attack> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> REPLAY = ThreadLocal.withInitial(() -> false);
    public static final class Attack {
        final @Nullable Attack previous; final ServerPlayer owner; final ServerLevel level; final long time;
        final ItemStack weapon; final FusedPair pair;
        @Nullable LivingEntity target; boolean ambiguous, effectSeen, effectCompleted, pushed;
        int effectDepth;
        Attack(@Nullable Attack previous, ServerPlayer owner, ItemStack weapon, FusedPair pair) {
            this.previous = previous; this.owner = owner; this.level = owner.level(); this.time = level.getGameTime();
            this.weapon = weapon; this.pair = pair;
        }
    }
    public static final class Effect {
        final Attack attack; final Vec3 before, look;
        Effect(Attack attack, Vec3 before, Vec3 look) { this.attack = attack; this.before = before; this.look = look; }
    }
    private GrapnelLunge() {}
    public static void init() { ConfirmedMelee.register(GrapnelLunge::onHit); }
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && LUNGE.equals(pair.primaryId()) && GrapnelSkills.LURE.equals(pair.secondaryId());
    }
    public static boolean supportsCarrier(@Nullable Set<FusionCarrier> roles) { return roles != null && roles.contains(FusionCarrier.SPEAR); }
    public static boolean supportsHost(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack)) && stack.has(DataComponents.PIERCING_WEAPON);
    }
    public static @Nullable FusedPair selected(@Nullable ItemStack stack) {
        if (!supportsHost(stack) || stack.isBroken()) return null;
        FusedPair best = null;
        for (FusedPair pair : stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (isPair(pair) && GrapnelSkills.applies(pair, stack) && (best == null || pair.primaryLevel() > best.primaryLevel())) best = pair;
        }
        return best;
    }
    private static boolean ready(ServerPlayer owner) { return owner.isAlive() && !owner.isCreative() && !owner.isSpectator(); }
    private static boolean unambiguous(ItemStack stack) {
        long references = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().stream()
            .filter(pair -> pair.involves(LUNGE)).count();
        return references == 1 && stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()).stream()
            .noneMatch(retained -> LUNGE.equals(retained.id()));
    }
    public static @Nullable Attack begin(LivingEntity entity, EquipmentSlot slot, Object piercingWeapon) {
        Attack previous = CURRENT.get(); if (previous != null) previous.ambiguous = true;
        if (!(entity instanceof ServerPlayer owner) || slot != EquipmentSlot.MAINHAND || !ready(owner) || MatrixEffectExecutor.isExecuting()) return null;
        ItemStack weapon = owner.getMainHandItem(); FusedPair pair = selected(weapon);
        if (pair == null || owner.getWeaponItem() != weapon || weapon.get(DataComponents.PIERCING_WEAPON) != piercingWeapon || !unambiguous(weapon)) return null;
        Attack attack = new Attack(previous, owner, weapon, pair); CURRENT.set(attack); return attack;
    }
    private static void onHit(ConfirmedMelee.Hit hit) {
        Attack attack = CURRENT.get();
        if (attack == null || attack.ambiguous || attack.target != null || attack.owner != hit.player()
            || attack.weapon != hit.weapon() || hit.slot() != EquipmentSlot.MAINHAND || !hit.eligibleBefore()
            || !hit.target().isAlive() || hit.target().level() != attack.level || attack.owner.getMainHandItem() != attack.weapon) return;
        attack.target = hit.target();
    }
    public static @Nullable Effect beginEffect(Enchantment enchantment, ServerLevel level, int rank, EnchantedItemInUse item, net.minecraft.world.entity.Entity user) {
        Attack attack = CURRENT.get();
        if (attack == null || attack.owner != user || attack.level != level || item.owner() != attack.owner
            || item.inSlot() != EquipmentSlot.MAINHAND || item.itemStack() != attack.weapon
            || !FusionStationOperations.lookupEnchantment(level.registryAccess(), LUNGE).map(holder -> holder.value() == enchantment).orElse(false)) return null;
        if (attack.effectSeen || attack.effectDepth != 0 || rank != attack.pair.primaryLevel()) attack.ambiguous = true;
        attack.effectDepth++; attack.effectSeen = true;
        return new Effect(attack, attack.owner.getDeltaMovement(), attack.owner.getLookAngle());
    }
    public static void endEffect(@Nullable Effect effect, boolean completed) {
        if (effect == null) return;
        Attack attack = effect.attack;
        if (CURRENT.get() != attack || !completed || attack.effectDepth != 1) attack.ambiguous = true;
        if (attack.effectDepth > 0) attack.effectDepth--;
        attack.effectCompleted = completed;
        attack.pushed = GrapnelMotion.matchesInput(attack.owner.getDeltaMovement().subtract(effect.before), effect.look);
    }
    /** Preserve the missing native primary on every vanilla post-piercing event, including ordinary swings.
     * The stricter attack frame is only for attributing a new mark, not for suppressing old effects. */
    public static void replayMissing(ServerLevel level, LivingEntity entity) {
        if (Boolean.TRUE.equals(REPLAY.get()) || !(entity instanceof ServerPlayer owner) || owner.level() != level) return;
        ItemStack weapon = owner.getMainHandItem(); FusedPair selected = selected(weapon);
        if (selected == null || owner.getWeaponItem() != weapon) return;
        Holder<Enchantment> enchantment = FusionStationOperations.lookupEnchantment(level.registryAccess(), LUNGE).orElse(null);
        if (enchantment == null || !enchantment.value().matchingSlot(EquipmentSlot.MAINHAND)
            || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment) > 0) return;
        int rank = selected.primaryLevel();
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs())
            if (LUNGE.equals(pair.primaryId())) rank = Math.max(rank, pair.primaryLevel());
        for (RetainedEnchant retained : weapon.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of()))
            if (LUNGE.equals(retained.id())) rank = Math.max(rank, retained.level());
        REPLAY.set(true);
        try { enchantment.value().doPostPiercingAttack(level, rank,
            new EnchantedItemInUse(weapon, EquipmentSlot.MAINHAND, owner), owner); }
        finally { REPLAY.remove(); }
    }
    public static void end(@Nullable Attack attack, boolean completed) {
        if (attack == null) return;
        if (CURRENT.get() != attack) { CURRENT.remove(); return; }
        if (attack.previous == null) CURRENT.remove(); else CURRENT.set(attack.previous);
        ServerPlayer owner = attack.owner; LivingEntity target = attack.target;
        if (!completed || attack.ambiguous || attack.effectDepth != 0 || !attack.effectSeen || !attack.effectCompleted || !attack.pushed
            || target == null || !target.isAlive() || !ready(owner) || owner.level() != attack.level || target.level() != attack.level
            || attack.level.getGameTime() != attack.time || owner.getMainHandItem() != attack.weapon
            || !attack.pair.equals(selected(attack.weapon)) || !unambiguous(attack.weapon)) return;
        GrapnelSkills.markImpact(owner, target, attack.weapon, attack.pair);
    }
    public static void clearServer() { CURRENT.remove(); REPLAY.remove(); }
}
