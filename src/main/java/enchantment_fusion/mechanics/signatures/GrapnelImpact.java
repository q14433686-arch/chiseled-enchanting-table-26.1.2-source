package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.RetainedEnchant;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.Nullable;

/** One real melee action, one effect calculation and its actual damage/impulse; no value is rerolled. */
public final class GrapnelImpact {
    public static final String KNOCKBACK = "minecraft:knockback", BREACH = "minecraft:breach", DENSITY = "minecraft:density";
    public enum Kind { KNOCKBACK, ARMOR, FALL }
    private static final int MAX_PAIRS = 16;
    private static final ThreadLocal<Attack> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> REPLAY = new ThreadLocal<>();
    private static final class Evidence {
        final FusedPair pair; final Kind kind;
        boolean seen, improved, ambiguous;
        Evidence(FusedPair pair, Kind kind) { this.pair = pair; this.kind = kind; }
    }
    public static final class Attack {
        final @Nullable Attack previous; final Player player; final ItemStack weapon, snapshot; final Entity target;
        final List<Evidence> evidence; @Nullable DamageSource source, confirmed, replayed;
        int depth; boolean ambiguous;
        Attack(@Nullable Attack previous, Player player, ItemStack weapon, Entity target, EquipmentSlot slot) {
            this.previous = previous; this.player = player; this.weapon = weapon; this.snapshot = weapon.copy(); this.target = target;
            this.evidence = slot == EquipmentSlot.MAINHAND && player instanceof ServerPlayer ? evidence(weapon) : List.of();
        }
    }
    public static final class Effect {
        final Attack attack; final Kind kind; final Enchantment enchantment; final int level; final float before;
        Effect(Attack attack, Kind kind, Enchantment enchantment, int level, float before) {
            this.attack = attack; this.kind = kind; this.enchantment = enchantment; this.level = level; this.before = before;
        }
    }
    private GrapnelImpact() {}
    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && GrapnelSkills.LURE.equals(pair.secondaryId()) && kind(pair.primaryId()) != null;
    }
    private static @Nullable Kind kind(String id) {
        return KNOCKBACK.equals(id) ? Kind.KNOCKBACK : BREACH.equals(id) ? Kind.ARMOR : DENSITY.equals(id) ? Kind.FALL : null;
    }
    public static boolean supportsCarrier(FusedPair pair, Set<FusionCarrier> roles) {
        return DENSITY.equals(pair.primaryId()) ? roles.contains(FusionCarrier.MACE) : MeleeAttackThemes.supportsWeapon(roles);
    }
    public static boolean hasEquipped(ItemStack weapon) { return !evidence(weapon).isEmpty(); }
    private static List<Evidence> evidence(ItemStack weapon) {
        List<Evidence> result = new ArrayList<>();
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (isPair(pair) && GrapnelSkills.applies(pair, weapon)) result.add(new Evidence(pair, kind(pair.primaryId())));
            if (result.size() >= MAX_PAIRS) break;
        }
        return result;
    }
    private static boolean unambiguous(ItemStack stack, FusedPair pair) {
        String id = pair.primaryId();
        long references = stack.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().stream()
            .filter(other -> other.involves(id)).count();
        return references == 1 && stack.getOrDefault(FusionDataComponents.RETAINED_ENCHANTS, List.<RetainedEnchant>of())
            .stream().noneMatch(other -> id.equals(other.id()));
    }
    public static Attack beginAttack(Player player, ItemStack weapon, Entity target, EquipmentSlot slot) {
        Attack previous = CURRENT.get(); if (previous != null) previous.ambiguous = true;
        Attack frame = new Attack(previous, player, weapon, target, slot); CURRENT.set(frame); return frame;
    }
    public static void endAttack(Attack frame) {
        if (CURRENT.get() != frame) { CURRENT.remove(); return; }
        if (frame.previous == null) CURRENT.remove(); else CURRENT.set(frame.previous);
    }
    /** Observe the exact enchantment execution already being performed by the game or its one projection. */
    public static @Nullable Effect beginEffect(Kind kind, ServerLevel level, Enchantment enchantment, int enchantmentLevel,
                                               ItemStack weapon, Entity target, DamageSource source, float before) {
        Attack frame = CURRENT.get();
        if (frame == null || frame.evidence.isEmpty()) return null;
        if (!(frame.player instanceof ServerPlayer owner) || owner.level() != level || frame.weapon != weapon
            || frame.target != target || source.getEntity() != owner || source.getDirectEntity() != owner
            || (frame.source != null && frame.source != source)) {
            if (frame.depth > 0) frame.ambiguous = true;
            return null;
        }
        frame.source = source;
        if (frame.depth++ > 0) frame.ambiguous = true;
        return new Effect(frame, kind, enchantment, enchantmentLevel, before);
    }
    public static void endEffect(@Nullable Effect effect, float after, boolean completed) {
        if (effect == null) return;
        Attack frame = effect.attack;
        if (CURRENT.get() != frame || frame.depth <= 0 || !completed) frame.ambiguous = true;
        if (frame.depth > 0) frame.depth--;
        if (!(frame.player instanceof ServerPlayer owner)) { frame.ambiguous = true; return; }
        for (Evidence evidence : frame.evidence) {
            if (evidence.kind != effect.kind) continue;
            Holder<Enchantment> primary = FusionStationOperations.lookupEnchantment(owner.registryAccess(), evidence.pair.primaryId()).orElse(null);
            boolean ours = primary != null && primary.value() == effect.enchantment;
            if (!Float.isFinite(effect.before) || !Float.isFinite(after)) { evidence.ambiguous = true; continue; }
            if (ours) {
                if (evidence.seen || effect.level != evidence.pair.primaryLevel()) evidence.ambiguous = true;
                evidence.seen = true;
                evidence.improved = evidence.kind == Kind.ARMOR
                    ? GrapnelImpactMath.reducedArmor(effect.before, after) : GrapnelImpactMath.increased(effect.before, after);
            } else if (evidence.seen && Float.compare(effect.before, after) != 0) evidence.ambiguous = true;
        }
    }
    private static @Nullable Evidence primary(Attack frame, Kind kind) {
        Evidence best = null;
        for (Evidence evidence : frame.evidence) if (evidence.kind == kind
            && (best == null || evidence.pair.primaryLevel() > best.pair.primaryLevel())) best = evidence;
        return best;
    }
    /** Replay only a missing primary, after existing replayers, inside the original calculation. */
    public static float project(Kind kind, ServerLevel level, ItemStack weapon, Entity target, DamageSource source, float value) {
        Attack frame = CURRENT.get();
        if (Boolean.TRUE.equals(REPLAY.get()) || frame == null || !(frame.player instanceof ServerPlayer owner)
            || owner.level() != level || owner.getMainHandItem() != weapon || frame.weapon != weapon || frame.target != target
            || source.getEntity() != owner || source.getDirectEntity() != owner || !Float.isFinite(value)) return value;
        Evidence primary = primary(frame, kind);
        if (primary == null || primary.seen || (kind != Kind.KNOCKBACK && HeavyCrushSkill.selected(weapon) != null)) return value;
        Holder<Enchantment> enchantment = FusionStationOperations.lookupEnchantment(level.registryAccess(), primary.pair.primaryId()).orElse(null);
        if (enchantment == null || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment) > 0) return value;
        MutableFloat modified = new MutableFloat(value); REPLAY.set(Boolean.TRUE);
        try {
            switch (kind) {
                case KNOCKBACK -> enchantment.value().modifyKnockback(level, primary.pair.primaryLevel(), weapon, target, source, modified);
                case ARMOR -> enchantment.value().modifyArmorEffectivness(level, primary.pair.primaryLevel(), weapon, target, source, modified);
                case FALL -> enchantment.value().modifyFallBasedDamage(level, primary.pair.primaryLevel(), weapon, target, source, modified);
            }
        } finally { REPLAY.remove(); }
        if (!Float.isFinite(modified.floatValue())) return value;
        if (kind == Kind.KNOCKBACK) frame.replayed = source;
        return modified.floatValue();
    }
    public static boolean alreadyReplayed(Player player, ItemStack weapon, Entity target, DamageSource source) {
        Attack frame = CURRENT.get();
        return frame != null && frame.player == player && frame.weapon == weapon && frame.target == target && frame.replayed == source;
    }
    private static boolean eligible(Attack frame, Evidence evidence, ServerPlayer owner, LivingEntity target, DamageSource source) {
        return CURRENT.get() == frame && !frame.ambiguous && frame.depth == 0 && evidence.seen && evidence.improved && !evidence.ambiguous
            && frame.source == source && frame.player == owner && frame.target == target && owner.getMainHandItem() == frame.weapon
            && ItemStack.matches(frame.weapon, frame.snapshot) && unambiguous(frame.weapon, evidence.pair)
            && GrapnelSkills.applies(evidence.pair, frame.weapon) && target.isAlive();
    }
    /** Density/Breach are confirmed only after real eligible health/absorption loss. */
    public static void onHit(ConfirmedMelee.Hit hit) {
        Attack frame = CURRENT.get(); ServerPlayer owner = hit.player();
        if (frame == null || !hit.eligibleBefore() || hit.slot() != EquipmentSlot.MAINHAND || frame.weapon != hit.weapon()
            || frame.target != hit.target() || frame.player != owner) return;
        frame.confirmed = hit.source();
        for (Evidence evidence : frame.evidence) if (evidence.kind != Kind.KNOCKBACK
            && eligible(frame, evidence, owner, hit.target(), hit.source())) {
            GrapnelSkills.markImpact(owner, hit.target(), frame.weapon, evidence.pair); return;
        }
    }
    /** Knockback requires both a confirmed hit and the real resisted velocity change. */
    public static void afterKnockback(Player player, LivingEntity target, Vec3 before) {
        Attack frame = CURRENT.get();
        if (frame == null || !(player instanceof ServerPlayer owner) || frame.confirmed == null) return;
        Vec3 delta = target.getDeltaMovement().subtract(before);
        if (!GrapnelImpactMath.changedHorizontal(delta.x, delta.z)) return;
        for (Evidence evidence : frame.evidence) if (evidence.kind == Kind.KNOCKBACK
            && eligible(frame, evidence, owner, target, frame.confirmed)) {
            GrapnelSkills.markImpact(owner, target, frame.weapon, evidence.pair); return;
        }
    }
    public static void clearServer() { CURRENT.remove(); REPLAY.remove(); }
}
