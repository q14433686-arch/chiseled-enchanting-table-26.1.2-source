package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Fire Aspect ▷ Silk Touch: a fully charged real hit shatters a target that was already
 * burning before the attack. The original Fire Aspect is retained. This handler is deliberately
 * limited to the selected weapon carriers; legacy items on other carriers keep their old effect.
 *
 * AttackEntityCallback precedes damage, so attempts are confirmed by AFTER_DAMAGE and performed
 * at the end of the server tick. A blocked, cancelled, missed or partially charged hit does not
 * spend the fusion cooldown. The currently used item and pair must still match at confirmation.
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

    // Only touched on the logical server thread. Both lists are cleared on server stop.
    private static final Map<AttackKey, Pending> PENDING = new HashMap<>();
    private static final List<Pending> CONFIRMED = new ArrayList<>();
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

    public static boolean canAffect(ServerPlayer player, LivingEntity target) {
        if (target == player || !target.isAlive() || target.isSpectator() || target.isInvulnerable()
            || target.isAlliedTo(player) || player.isAlliedTo(target)) return false;
        if (target instanceof Player other) {
            return !other.getAbilities().invulnerable && player.canHarmPlayer(other);
        }
        return true; // A deliberate direct attack, rather than an automatic area target.
    }

    public static void prepare(ServerLevel level, ServerPlayer player, ItemStack weapon, FusedPair pair,
                               String signature, LivingEntity target) {
        if (!applies(pair, weapon, signature) || !canAffect(player, target)) return;
        boolean alreadyBurning = target.isOnFire();
        if (!alreadyBurning && !pair.isAberrant()) return;
        AttackKey key = new AttackKey(player.getUUID(), target.getUUID(), level.dimension());
        if (PENDING.size() >= MAX_PENDING && !PENDING.containsKey(key)) return;
        PENDING.put(key, new Pending(key, pair, weapon, level.getGameTime(), alreadyBurning));
    }

    /** Confirm only the server's successful, unblocked direct hit, while the same item is held. */
    public static void confirm(ServerLevel level, ServerPlayer player, LivingEntity target) {
        AttackKey key = new AttackKey(player.getUUID(), target.getUUID(), level.dimension());
        Pending pending = PENDING.remove(key);
        if (pending == null || pending.tick() != level.getGameTime() || !canAffect(player, target)) return;
        ItemStack weapon = pending.weapon();
        if (weapon != player.getMainHandItem() && weapon != player.getOffhandItem()) return;
        FusedEnchantmentList pairs = weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY);
        if (!pairs.pairs().contains(pending.pair())
            || !SignatureCatalog.resolve(pending.pair()).map(e -> applies(pending.pair(), weapon, e.id())).orElse(false)) return;
        if (CONFIRMED.size() < MAX_PENDING
            && MatrixEffectExecutor.acquireDeferredCooldown(level, player, pending.pair(), TriggerType.ON_MELEE_STRIKE)) {
            CONFIRMED.add(pending);
        }
    }

    public static void tickServer(MinecraftServer server) {
        PENDING.clear(); // An unconfirmed attempt cannot leak into a later attack or tick.
        if (CONFIRMED.isEmpty()) return;
        List<Pending> ready = List.copyOf(CONFIRMED);
        CONFIRMED.clear();
        for (Pending hit : ready) {
            ServerLevel level = server.getLevel(hit.key().dimension());
            ServerPlayer owner = server.getPlayerList().getPlayer(hit.key().owner());
            Entity entity = level == null ? null : level.getEntity(hit.key().target());
            if (level == null || owner == null || owner.level() != level || !owner.isAlive()
                || !(entity instanceof LivingEntity target) || !canAffect(owner, target)) continue;
            if (!SignatureCatalog.resolve(hit.pair()).map(e -> SIGNATURE.equals(e.id())).orElse(false)) continue;
            IN_BONUS.set(Boolean.TRUE);
            try {
                if (hit.pair().isAberrant()) {
                    AberrantRegistry.executeAberration(level, owner, hit.pair().aberrationId(), target, target.position());
                }
                if (!hit.burningBeforeHit() || !target.isAlive()) continue;
                CompendiumDiscoveryTracker.recordDiscovery(owner, hit.pair().primaryId(), hit.pair().secondaryId());

                // The old glaze's armor-scaled magnitude is retained and Silk level contributes;
                // magic damage bypasses vanilla armor. The source is attributed to the attacker.
                float bonus = 3.2F + target.getArmorValue() * 0.24F
                    + 0.6F * hit.pair().primaryLevel() + 0.5F * hit.pair().secondaryLevel();
                DamageSource source = new DamageSource(level.damageSources().magic().typeHolder(), owner);
                int previousInvulnerability = target.invulnerableTime;
                target.invulnerableTime = 0;
                boolean applied;
                try {
                    applied = target.hurtServer(level, source, bonus);
                } finally {
                    target.invulnerableTime = Math.max(previousInvulnerability, target.invulnerableTime);
                }
                if (applied) {
                    level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                        target.getZ(), 14, 0.25D, 0.3D, 0.25D, 0.08D);
                    level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.7F);
                }
            } finally {
                IN_BONUS.remove();
            }
        }
    }

    public static void onPlayerDisconnect(UUID id) {
        PENDING.entrySet().removeIf(e -> e.getKey().owner().equals(id) || e.getKey().target().equals(id));
        CONFIRMED.removeIf(p -> p.key().owner().equals(id) || p.key().target().equals(id));
    }

    public static void clear() {
        PENDING.clear();
        CONFIRMED.clear();
    }
}
