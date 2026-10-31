package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.data.FusionMatrixRegistry;
import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.CrossDomainTransmutations.TargetRole;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.TriggerType;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fire Aspect ▷ Silk Touch: a fully charged real hit shatters a target that was already
 * burning before the attack. Only the selected main-hand weapon carriers use this redesign;
 * legacy items on other carriers keep their former effect. The normal attack and 26.1 spear
 * stab use separate vanilla entry points, but both must confirm the exact direct damage call.
 */
public final class GlazedStrikeEffect {
    public static final String SIGNATURE = "glazed_kiln_edge";
    private static final String FIRE = "minecraft:fire_aspect";
    private static final String SILK = "minecraft:silk_touch";
    private static final Set<FusionCarrier> WEAPON_CARRIERS = Set.of(
        FusionCarrier.SWORD, FusionCarrier.AXE, FusionCarrier.SPEAR, FusionCarrier.MACE);
    private static final int MAX_PENDING = 256;

    private record AttackKey(UUID owner, UUID target, ResourceKey<Level> dimension) {}
    private record Pending(AttackKey key, FusedPair pair, ItemStack weapon, long tick, boolean burningBeforeHit) {}
    private record Confirmed(Pending pending, Vec3 position, boolean lethal) {}

    /** Charge and target fire are sampled before Player.attack resets its cooldown. */
    public static final class Swing {
        private final Swing previous;
        private final Player player;
        private final Entity target;
        private final ItemStack weapon;
        private final boolean full;
        private final boolean burning;

        private Swing(Swing previous, Player player, Entity target, ItemStack weapon, boolean full, boolean burning) {
            this.previous = previous;
            this.player = player;
            this.target = target;
            this.weapon = weapon;
            this.full = full;
            this.burning = burning;
        }
    }

    // Only touched on the logical server thread. Unconfirmed attempts live at most one tick.
    private static final Map<AttackKey, Pending> PENDING = new HashMap<>();
    private static final List<Confirmed> CONFIRMED = new ArrayList<>();
    private static final ThreadLocal<Swing> CURRENT_SWING = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IN_BONUS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private GlazedStrikeEffect() {}

    public static boolean isApplyingBonus() {
        return Boolean.TRUE.equals(IN_BONUS.get());
    }

    public static boolean isEligibleRole(TargetRole role) {
        return role == TargetRole.SWORD || role == TargetRole.AXE || role == TargetRole.SPEAR || role == TargetRole.MACE;
    }

    /** Fusion carriers can have several roles; do not reject a sword/tool hybrid by a single-role priority. */
    public static boolean supportsCarrier(Set<FusionCarrier> roles) {
        return roles != null && roles.stream().anyMatch(WEAPON_CARRIERS::contains);
    }

    public static boolean isPair(FusedPair pair) {
        return pair != null && FIRE.equals(pair.primaryId()) && SILK.equals(pair.secondaryId());
    }

    /** A datapack can replace the signature mapping; do not seize a different mapping's pair. */
    public static boolean applies(FusedPair pair, ItemStack weapon, String signature) {
        return isPair(pair) && SIGNATURE.equals(signature) && weapon != null && !weapon.isEmpty()
            && supportsCarrier(FusionCarrier.classify(weapon));
    }

    private static boolean canAffect(ServerPlayer player, LivingEntity target, boolean requireAlive) {
        if (target == player || (requireAlive && !target.isAlive()) || target.isSpectator() || target.isInvulnerable()
            || target.isAlliedTo(player) || player.isAlliedTo(target)) return false;
        if (target instanceof Player other) {
            return !other.getAbilities().invulnerable && player.canHarmPlayer(other);
        }
        return true; // A deliberate direct attack, rather than an automatic area target.
    }

    public static boolean canAffect(ServerPlayer player, LivingEntity target) {
        return canAffect(player, target, true);
    }

    private static boolean fullCharge(Player player) {
        return player.getAttackStrengthScale(0.5F) >= (float) FusionMatrixRegistry.serverSnapshot().economy().meleeFullCharge();
    }

    public static Swing beginSwing(Player player, Entity target) {
        boolean full = player instanceof ServerPlayer && fullCharge(player);
        boolean burning = target instanceof LivingEntity living && living.isOnFire();
        Swing frame = new Swing(CURRENT_SWING.get(), player, target, player.getWeaponItem(), full, burning);
        CURRENT_SWING.set(frame); // Mask an outer swing even for an unrelated nested call.
        return frame;
    }

    public static void endSwing(Swing frame) {
        if (frame.previous == null) CURRENT_SWING.remove();
        else CURRENT_SWING.set(frame.previous);
    }

    /** Called immediately before the target damage inside Player.attack. */
    public static void prepareSwing(Player player, Entity target) {
        if (!(player instanceof ServerPlayer owner) || !(target instanceof LivingEntity living)) return;
        discardAttempt(owner, living);
        Swing swing = CURRENT_SWING.get();
        if (swing == null || swing.player != owner || swing.target != target || swing.weapon != owner.getMainHandItem()) return;
        prepareDirect(owner, swing.weapon, living, swing.full, swing.burning);
    }

    /** Spear left-click uses PiercingWeapon; right-click kinetic damage uses its own ready delay. */
    public static void prepareStab(Player player, EquipmentSlot slot, Entity target) {
        if (!(player instanceof ServerPlayer owner) || !(target instanceof LivingEntity living)) return;
        discardAttempt(owner, living);
        ItemStack weapon = owner.getItemBySlot(slot);
        if (slot != EquipmentSlot.MAINHAND || weapon != owner.getMainHandItem()) return;
        KineticWeapon kinetic = weapon.get(DataComponents.KINETIC_WEAPON);
        boolean kineticUse = kinetic != null && owner.isUsingItem() && owner.getUsedItemHand() == InteractionHand.MAIN_HAND
            && owner.getUseItem() == weapon;
        // This wrapper is reached only when vanilla's kinetic weapon elected to deal damage.
        boolean ready = stabReady(kineticUse, owner.getTicksUsingItem(), kineticUse ? kinetic.delayTicks() : 0,
            !kineticUse && fullCharge(owner));
        prepareDirect(owner, weapon, living, ready, living.isOnFire());
    }

    public static boolean stabReady(boolean kineticUse, int useTicks, int delayTicks, boolean fullSwing) {
        return kineticUse ? useTicks >= Math.max(0, delayTicks) : fullSwing;
    }

    private static void discardAttempt(ServerPlayer player, LivingEntity target) {
        PENDING.remove(new AttackKey(player.getUUID(), target.getUUID(), player.level().dimension()));
    }

    private static void prepareDirect(ServerPlayer player, ItemStack weapon, LivingEntity target, boolean full, boolean burning) {
        if (!full || !canAffect(player, target)) return;
        ServerLevel level = player.level();
        AttackKey key = new AttackKey(player.getUUID(), target.getUUID(), level.dimension());
        if (PENDING.size() >= MAX_PENDING && !PENDING.containsKey(key)) return;
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if ((burning || pair.isAberrant())
                && SignatureCatalog.resolve(pair).map(entry -> applies(pair, weapon, entry.id())).orElse(false)) {
                PENDING.put(key, new Pending(key, pair, weapon, level.getGameTime(), burning));
                return;
            }
        }
    }

    /** Fabric AFTER_DAMAGE only reports nonlethal unblocked hits; the frame rules out unrelated damage. */
    public static void onConfirmedDamage(ServerLevel level, ServerPlayer player, LivingEntity target, DamageSource source) {
        ItemStack weapon = player.getMainHandItem();
        if (!AmberSealSkill.isCurrentDirectAttack(player, weapon, target, source) || !canAffect(player, target)) return;
        confirm(level, player, target, source, false);
        igniteNonNativePrimary(level, weapon, target);
    }

    /** AFTER_DEATH has no shield-blocked flag; a partly blocked lethal hit may still show a shatter. */
    public static void onConfirmedDeath(ServerLevel level, ServerPlayer player, LivingEntity target, DamageSource source) {
        if (!AmberSealSkill.isCurrentDirectAttack(player, player.getMainHandItem(), target, source)) return;
        confirm(level, player, target, source, true);
    }

    private static void confirm(ServerLevel level, ServerPlayer player, LivingEntity target, DamageSource source, boolean lethal) {
        AttackKey key = new AttackKey(player.getUUID(), target.getUUID(), level.dimension());
        Pending pending = PENDING.get(key);
        if (pending == null || !AmberSealSkill.isCurrentDirectAttack(player, pending.weapon(), target, source)) return;
        PENDING.remove(key);
        if (pending.tick() != level.getGameTime() || !player.isAlive() || !canAffect(player, target, !lethal)) return;
        ItemStack weapon = pending.weapon();
        if (weapon != player.getMainHandItem()) return;
        FusedEnchantmentList pairs = weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (!pairs.pairs().contains(pending.pair())
            || !SignatureCatalog.resolve(pending.pair()).map(entry -> applies(pending.pair(), weapon, entry.id())).orElse(false)) return;
        if (CONFIRMED.size() < MAX_PENDING
            && MatrixEffectExecutor.acquireDeferredCooldown(level, player, pending.pair(), TriggerType.ON_MELEE_STRIKE)) {
            CONFIRMED.add(new Confirmed(pending, target.getBoundingBox().getCenter(), lethal));
        }
    }

    /** Axes may be selected carriers even when vanilla cannot project Fire Aspect onto them. */
    private static void igniteNonNativePrimary(ServerLevel level, ItemStack weapon, LivingEntity target) {
        if (target.fireImmune()) return;
        var nativeFire = FusionStationOperations.lookupEnchantment(level.registryAccess(), FIRE);
        if (nativeFire.isEmpty() || weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(nativeFire.get()) > 0) return;
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (SignatureCatalog.resolve(pair).map(entry -> applies(pair, weapon, entry.id())).orElse(false)) {
                target.igniteForSeconds(4.0F * Math.clamp(pair.primaryLevel(), 1, 10));
                return;
            }
        }
    }

    public static void tickServer(MinecraftServer server) {
        PENDING.clear(); // An unconfirmed attempt cannot leak into a later attack or tick.
        if (CONFIRMED.isEmpty()) return;
        List<Confirmed> ready = List.copyOf(CONFIRMED);
        CONFIRMED.clear();
        for (Confirmed confirmed : ready) {
            Pending hit = confirmed.pending();
            ServerLevel level = server.getLevel(hit.key().dimension());
            ServerPlayer owner = server.getPlayerList().getPlayer(hit.key().owner());
            if (level == null || owner == null || owner.level() != level || !owner.isAlive()
                || !SignatureCatalog.resolve(hit.pair()).map(entry -> SIGNATURE.equals(entry.id())).orElse(false)) continue;
            Entity entity = level.getEntity(hit.key().target());
            LivingEntity target = entity instanceof LivingEntity living && canAffect(owner, living) ? living : null;
            if (!confirmed.lethal() && target == null) continue;
            IN_BONUS.set(Boolean.TRUE);
            try {
                if (hit.pair().isAberrant()) {
                    AberrantRegistry.executeAberration(level, owner, hit.pair().aberrationId(), target, confirmed.position());
                }
                if (!hit.burningBeforeHit()) continue;
                if (confirmed.lethal()) {
                    // The original hit already killed the target: show the shatter, never damage a corpse.
                    CompendiumDiscoveryTracker.recordDiscovery(owner, hit.pair().primaryId(), hit.pair().secondaryId());
                    showShatter(level, confirmed.position());
                    continue;
                }
                if (!target.isAlive()) continue;
                float bonus = bonusDamage(target.getArmorValue(), hit.pair().primaryLevel(), hit.pair().secondaryLevel());
                DamageSource source = new DamageSource(level.damageSources().magic().typeHolder(), owner);
                int previousInvulnerability = target.invulnerableTime;
                target.invulnerableTime = 0;
                boolean applied = false;
                try {
                    applied = target.hurtServer(level, source, bonus);
                } catch (RuntimeException ex) {
                    EnchantmentFusion.LOGGER.warn("Glazed strike could not affect {}", target.getUUID(), ex);
                } finally {
                    target.invulnerableTime = Math.max(previousInvulnerability, target.invulnerableTime);
                }
                if (applied) {
                    CompendiumDiscoveryTracker.recordDiscovery(owner, hit.pair().primaryId(), hit.pair().secondaryId());
                    showShatter(level, target.getBoundingBox().getCenter());
                }
            } finally {
                IN_BONUS.remove();
            }
        }
    }

    public static float bonusDamage(int armor, int fireLevel, int silkLevel) {
        return 3.2F + Math.max(0, armor) * 0.24F + 0.6F * fireLevel + 0.5F * silkLevel;
    }

    private static void showShatter(ServerLevel level, Vec3 center) {
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 14, 0.25D, 0.3D, 0.25D, 0.08D);
        level.playSound(null, BlockPos.containing(center), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.7F);
    }

    public static void onPlayerDisconnect(UUID id) {
        PENDING.entrySet().removeIf(entry -> entry.getKey().owner().equals(id) || entry.getKey().target().equals(id));
        CONFIRMED.removeIf(hit -> hit.pending().key().owner().equals(id) || hit.pending().key().target().equals(id));
    }

    public static void clear() {
        PENDING.clear();
        CONFIRMED.clear();
        CURRENT_SWING.remove();
    }
}
