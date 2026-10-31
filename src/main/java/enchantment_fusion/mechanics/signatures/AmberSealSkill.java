package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.model.TriggerType;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Silk Touch ▷ Fire Aspect: deliberately seal an existing flame, then shatter it with a real hit. */
public final class AmberSealSkill {
    public static final String SIGNATURE = "amber_embalm";
    private static final String SILK = "minecraft:silk_touch";
    private static final String FIRE = "minecraft:fire_aspect";
    private static final Identifier SLOW_ID = EnchantmentFusion.id("amber_seal_slow");
    private static final int MAX_SEALS_AND_BURSTS = 256;
    private static final int MAX_SPLASH_TARGETS = 16;
    private static final int MAX_SPLASH_CANDIDATES = 128;
    private static final int ACTIVATION_COOLDOWN = 60;
    private static final double AIM_RANGE = 6.0D;

    private record Candidate(ItemStack weapon, FusedPair pair) {}
    private record Seal(ServerLevel level, ServerPlayer owner, LivingEntity target, ItemStack weapon,
                        FusedPair pair, int fireTicks, long started, long expires) {}
    private record Burst(ServerLevel level, ServerPlayer owner, LivingEntity target, FusedPair pair,
                         int fireTicks, Vec3 position) {}

    /** Scoped around the exact vanilla damage call, not the earlier attack-attempt callback. */
    public static final class AttackFrame {
        private final @Nullable AttackFrame previous;
        private final @Nullable Candidate candidate;
        private final @Nullable Seal seal;
        private final Entity target;
        private final DamageSource source;
        private boolean confirmed;
        private AttackFrame(@Nullable AttackFrame previous, @Nullable Candidate candidate, @Nullable Seal seal,
                            Entity target, DamageSource source) {
            this.previous = previous;
            this.candidate = candidate;
            this.seal = seal;
            this.target = target;
            this.source = source;
        }
    }

    // These objects live only on the logical server thread. No item or entity data is rewritten.
    private static final Map<UUID, Seal> BY_TARGET = new HashMap<>();
    private static final Map<UUID, UUID> TARGET_BY_OWNER = new HashMap<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final ThreadLocal<AttackFrame> ATTACK = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IN_BURST = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AmberSealSkill() {}

    public static boolean isPair(@Nullable FusedPair pair) {
        return pair != null && SILK.equals(pair.primaryId()) && FIRE.equals(pair.secondaryId());
    }

    /** Respect datapack remapping and use the same weapon role policy as the forward direction. */
    public static boolean applies(@Nullable FusedPair pair, @Nullable ItemStack stack, String signature) {
        return isPair(pair) && SIGNATURE.equals(signature) && stack != null && !stack.isEmpty()
            && GlazedStrikeEffect.supportsCarrier(FusionCarrier.classify(stack));
    }

    private static @Nullable Candidate find(ServerPlayer player) {
        ItemStack weapon = player.getMainHandItem();
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            if (SignatureCatalog.resolve(pair).map(entry -> applies(pair, weapon, entry.id())).orElse(false)) {
                return new Candidate(weapon, pair);
            }
        }
        return null;
    }

    public static boolean hasEquipped(ServerPlayer player) {
        return find(player) != null;
    }

    public static int lifetimeTicks(int silkLevel) {
        return 80 + 20 * Math.clamp(silkLevel, 1, 5);
    }

    public static int restorableFire(int storedTicks, long elapsedTicks) {
        if (storedTicks <= 0) return 0;
        long remaining = (long) storedTicks - Math.max(0L, elapsedTicks);
        return remaining <= 0 ? 0 : (int) remaining;
    }

    public static float shockDamage(int silkLevel, int fireLevel, int storedTicks) {
        return 2.4F + 0.6F * Math.clamp(silkLevel, 1, 10)
            + 0.7F * Math.clamp(fireLevel, 1, 10) + Math.min(4.0F, Math.max(0, storedTicks) / 100.0F);
    }

    public static double shockRadius(int silkLevel) {
        return 1.6D + 0.2D * Math.clamp(silkLevel, 1, 5);
    }

    private static AttributeModifier slowModifier() {
        return new AttributeModifier(SLOW_ID, -0.40D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** The request contains no target or numbers: the server raycasts and validates everything. */
    public static boolean activate(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.containerMenu != player.inventoryMenu) return false;
        Candidate candidate = find(player);
        if (candidate == null) return false;
        UUID previous = TARGET_BY_OWNER.get(player.getUUID());
        if (previous != null) {
            Seal seal = BY_TARGET.get(previous);
            if (seal != null) cancel(seal, true);
            else TARGET_BY_OWNER.remove(player.getUUID());
            message(player, "message.enchantment_fusion.amber_cancelled");
            return true;
        }
        ServerLevel level = player.level();
        long now = level.getGameTime();
        if (now < READY_AT.getOrDefault(player.getUUID(), 0L)) {
            message(player, "message.enchantment_fusion.amber_cooldown");
            return false;
        }
        LivingEntity target = aimedTarget(level, player);
        if (target == null || !GlazedStrikeEffect.canAffect(player, target) || !level.hasChunkAt(target.blockPosition())
            || !level.mayInteract(player, target.blockPosition()) || !target.isOnFire()
            || target.isInWaterOrRain() || target.isInLava()) {
            message(player, "message.enchantment_fusion.amber_no_target");
            return false;
        }
        if (BY_TARGET.containsKey(target.getUUID()) || BY_TARGET.size() + BURSTS.size() >= MAX_SEALS_AND_BURSTS) {
            message(player, "message.enchantment_fusion.amber_unavailable");
            return false;
        }
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.hasModifier(SLOW_ID)) {
            message(player, "message.enchantment_fusion.amber_unavailable");
            return false;
        }
        int ticks = target.getRemainingFireTicks();
        if (ticks <= 0) return false;
        Seal seal = new Seal(level, player, target, candidate.weapon(), candidate.pair(), ticks, now,
            now + lifetimeTicks(candidate.pair().primaryLevel()));
        BY_TARGET.put(target.getUUID(), seal);
        TARGET_BY_OWNER.put(player.getUUID(), target.getUUID());
        try {
            speed.addTransientModifier(slowModifier());
            target.clearFire(); // Only the current timer is sealed; later independent ignition remains untouched.
            if (target.getRemainingFireTicks() > 0 || !ownsSlow(target)) {
                cancel(seal, true);
                message(player, "message.enchantment_fusion.amber_unavailable");
                return false;
            }
        } catch (RuntimeException ex) {
            cancel(seal, true);
            EnchantmentFusion.LOGGER.warn("Could not safely seal target {}", target.getUUID(), ex);
            message(player, "message.enchantment_fusion.amber_unavailable");
            return false;
        }
        READY_AT.put(player.getUUID(), now + ACTIVATION_COOLDOWN);
        CompendiumDiscoveryTracker.recordDiscovery(player, candidate.pair().primaryId(), candidate.pair().secondaryId());
        level.sendParticles(ParticleTypes.WAX_ON, target.getX(), target.getY() + target.getBbHeight() * 0.5D,
            target.getZ(), 16, 0.3D, 0.4D, 0.3D, 0.03D);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.2F);
        message(player, "message.enchantment_fusion.amber_sealed");
        return true;
    }

    private static @Nullable LivingEntity aimedTarget(ServerLevel level, ServerPlayer player) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1.0F).scale(AIM_RANGE));
        HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
        AABB search = player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, from, to, search,
            entity -> entity.isPickable() && !entity.isSpectator(), from.distanceToSqr(to));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static void message(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key), true);
    }

    /** Called by the Player mixin for the real hurtOrSimulate call of an attack or spear stab. */
    public static @Nullable AttackFrame beginAttack(Player player, ItemStack weapon, Entity target, DamageSource source) {
        if (!(player instanceof ServerPlayer owner)) return null;
        Candidate candidate = find(owner);
        if (!(target instanceof LivingEntity) || candidate == null || candidate.weapon() != weapon
            || owner.getMainHandItem() != weapon || source.getEntity() != owner || source.getDirectEntity() != owner
            || MatrixEffectExecutor.isExecuting() || GlazedStrikeEffect.isApplyingBonus() || LoyaltyGuard.isExecuting()
            || Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get()) || isApplyingBurst()) candidate = null;
        Seal seal = BY_TARGET.get(target.getUUID());
        if (seal != null && !validSeal(seal)) {
            cancel(seal, true);
            seal = null;
        }
        if (seal != null && (candidate == null || seal.owner() != owner || seal.target() != target || seal.weapon() != weapon
            || !seal.pair().equals(candidate.pair()))) seal = null;
        AttackFrame frame = new AttackFrame(ATTACK.get(), candidate, seal, target, source);
        ATTACK.set(frame); // Also mask a parent frame during an unrelated nested attack.
        return frame;
    }

    public static void afterDamage(LivingEntity entity, DamageSource source, float damageTaken, boolean blocked) {
        AttackFrame frame = ATTACK.get();
        if (frame != null && frame.candidate != null && frame.target == entity && frame.source == source
            && !blocked && damageTaken > 0.0F) frame.confirmed = true;
    }

    /** AFTER_DAMAGE does not fire on death; AFTER_DEATH supplies the distinct lethal path. */
    public static void afterDeath(LivingEntity entity, DamageSource source) {
        AttackFrame frame = ATTACK.get();
        boolean ourHit = frame != null && frame.candidate != null && frame.target == entity && frame.source == source;
        if (ourHit) frame.confirmed = true;
        if (!ourHit || frame.seal == null) {
            Seal seal = BY_TARGET.get(entity.getUUID());
            if (seal != null) cancel(seal, false);
        }
        if (entity instanceof ServerPlayer deadOwner) cancelOwned(deadOwner.getUUID());
    }

    public static void endAttack(@Nullable AttackFrame frame, boolean succeeded) {
        if (frame == null) return;
        if (frame.previous == null) ATTACK.remove(); else ATTACK.set(frame.previous);
        Candidate candidate = frame.candidate;
        if (!succeeded || !frame.confirmed || candidate == null || !(frame.target instanceof LivingEntity target)
            || !(frame.source.getEntity() instanceof ServerPlayer owner) || !validCandidate(owner, candidate)
            || !(target.level() instanceof ServerLevel level) || owner.level() != level
            || !level.mayInteract(owner, target.blockPosition())) return;
        if (target instanceof Player other && !owner.canHarmPlayer(other)) return;
        Seal seal = frame.seal;
        if (seal != null && BY_TARGET.get(target.getUUID()) == seal && validOwnerAndGear(seal)
            && level.getGameTime() < seal.expires() && !target.isInWaterOrRain() && !target.isInLava() && ownsSlow(target)) {
            // A successful hit consumes the captured fire exactly once; new fire is never cleared.
            forget(seal);
            BURSTS.add(new Burst(level, owner, target, seal.pair(), seal.fireTicks(), target.getBoundingBox().getCenter()));
        }
        if (GlazedStrikeEffect.canAffect(owner, target)) {
            // The secondary fire theme supplies its own setup. Like vanilla Ignite this only
            // extends an existing timer, so an independent Fire Aspect is not added twice.
            target.igniteForSeconds(4.0F * Math.clamp(candidate.pair().secondaryLevel(), 1, 10));
        }
    }

    private static boolean validCandidate(ServerPlayer owner, Candidate candidate) {
        ItemStack weapon = candidate.weapon();
        return owner.isAlive() && !owner.isSpectator() && owner.getMainHandItem() == weapon
            && weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs().contains(candidate.pair())
            && SignatureCatalog.resolve(candidate.pair()).map(entry -> applies(candidate.pair(), weapon, entry.id())).orElse(false);
    }

    private static boolean validOwnerAndGear(Seal seal) {
        return seal.owner().level() == seal.level()
            && validCandidate(seal.owner(), new Candidate(seal.weapon(), seal.pair()));
    }

    private static boolean ownsSlow(LivingEntity target) {
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier current = speed == null ? null : speed.getModifier(SLOW_ID);
        return current != null && current.equals(slowModifier()) && !speed.getPermanentModifiers().contains(current);
    }

    private static void forget(Seal seal) {
        if (!BY_TARGET.remove(seal.target().getUUID(), seal)) return;
        TARGET_BY_OWNER.remove(seal.owner().getUUID(), seal.target().getUUID());
        if (ownsSlow(seal.target())) seal.target().getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SLOW_ID);
    }

    private static void cancel(Seal seal, boolean restore) {
        if (BY_TARGET.get(seal.target().getUUID()) != seal) return;
        forget(seal);
        LivingEntity target = seal.target();
        if (!restore || !target.isAlive() || target.isRemoved() || target.level() != seal.level()
            || target.isInWaterOrRain() || target.isInLava() || target.fireImmune()) return;
        int remaining = restorableFire(seal.fireTicks(), seal.level().getGameTime() - seal.started());
        if (remaining > target.getRemainingFireTicks()) target.setRemainingFireTicks(remaining);
    }

    public static boolean isApplyingBurst() {
        return Boolean.TRUE.equals(IN_BURST.get());
    }

    private static boolean validSeal(Seal seal) {
        LivingEntity target = seal.target();
        return validOwnerAndGear(seal) && target.level() == seal.level() && target.isAlive() && !target.isRemoved()
            && !target.isInWaterOrRain() && !target.isInLava() && ownsSlow(target)
            && seal.level().getGameTime() < seal.expires() && GlazedStrikeEffect.canAffect(seal.owner(), target)
            && seal.level().hasChunkAt(target.blockPosition()) && seal.level().mayInteract(seal.owner(), target.blockPosition());
    }

    public static void tickServer(MinecraftServer server) {
        for (Seal seal : List.copyOf(BY_TARGET.values())) {
            LivingEntity target = seal.target();
            long now = seal.level().getGameTime();
            if (!validSeal(seal)) {
                cancel(seal, true);
            } else if (now % 10L == 0L) {
                seal.level().sendParticles(ParticleTypes.WAX_ON, target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                    target.getZ(), 3, 0.25D, 0.3D, 0.25D, 0.0D);
            }
        }
        READY_AT.entrySet().removeIf(entry -> server.getPlayerList().getPlayer(entry.getKey()) == null
            || server.getPlayerList().getPlayer(entry.getKey()).level().getGameTime() >= entry.getValue());
        List<Burst> ready = List.copyOf(BURSTS);
        BURSTS.clear();
        for (Burst burst : ready) burst(burst);
    }

    private static void burst(Burst burst) {
        ServerLevel level = burst.level();
        ServerPlayer owner = burst.owner();
        if (!owner.isAlive() || owner.isSpectator() || owner.level() != level
            || !SignatureCatalog.resolve(burst.pair()).map(entry -> SIGNATURE.equals(entry.id())).orElse(false)) return;
        Vec3 center = burst.position();
        if (!level.hasChunkAt(BlockPos.containing(center)) || !level.mayInteract(owner, BlockPos.containing(center))) return;
        float damage = shockDamage(burst.pair().primaryLevel(), burst.pair().secondaryLevel(), burst.fireTicks());
        double radius = shockRadius(burst.pair().primaryLevel());
        DamageSource source = new DamageSource(level.damageSources().magic().typeHolder(), owner);
        IN_BURST.set(Boolean.TRUE);
        try {
            LivingEntity main = burst.target();
            if (main.level() == level && GlazedStrikeEffect.canAffect(owner, main)
                && level.mayInteract(owner, main.blockPosition())) {
                int invulnerability = main.invulnerableTime;
                main.invulnerableTime = 0; // This bonus is part of the confirmed hit, not a separate free attack.
                try { main.hurtServer(level, source, damage); }
                catch (RuntimeException ex) {
                    EnchantmentFusion.LOGGER.warn("Amber shock could not affect {}", main.getUUID(), ex);
                } finally { main.invulnerableTime = Math.max(invulnerability, main.invulnerableTime); }
            }
            AABB area = AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
            List<LivingEntity> splash = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(LivingEntity.class), area,
                entity -> entity != main && LoyaltyGuard.eligibleTarget(owner, entity), splash, MAX_SPLASH_CANDIDATES);
            splash.sort(Comparator.comparingDouble(entity -> entity.getBoundingBox().getCenter().distanceToSqr(center)));
            int affected = 0;
            for (LivingEntity target : splash) {
                if (affected >= MAX_SPLASH_TARGETS) break;
                Vec3 targetCenter = target.getBoundingBox().getCenter();
                if (targetCenter.distanceToSqr(center) > radius * radius || !level.mayInteract(owner, target.blockPosition())
                    || level.clip(new ClipContext(center, targetCenter, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, owner)).getType() != HitResult.Type.MISS) continue;
                affected++;
                try { target.hurtServer(level, source, damage * 0.5F); } // Other targets keep normal invulnerability frames.
                catch (RuntimeException ex) {
                    EnchantmentFusion.LOGGER.warn("Amber shock could not affect {}", target.getUUID(), ex);
                }
            }
            level.sendParticles(ParticleTypes.WAX_ON, center.x, center.y, center.z, 18, 0.45D, 0.35D, 0.45D, 0.08D);
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 12, 0.35D, 0.3D, 0.35D, 0.04D);
            level.playSound(null, BlockPos.containing(center), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.65F);
            if (burst.pair().isAberrant()
                && MatrixEffectExecutor.acquireDeferredCooldown(level, owner, burst.pair(), TriggerType.ON_MELEE_STRIKE)) {
                AberrantRegistry.executeAberration(level, owner, burst.pair().aberrationId(), main.isAlive() ? main : null, center);
            }
        } finally {
            IN_BURST.remove();
        }
    }

    private static void cancelOwned(UUID owner) {
        UUID target = TARGET_BY_OWNER.get(owner);
        Seal seal = target == null ? null : BY_TARGET.get(target);
        if (seal != null) cancel(seal, true);
        else TARGET_BY_OWNER.remove(owner);
    }

    public static void onEntityUnload(Entity entity) {
        Seal targetSeal = BY_TARGET.get(entity.getUUID());
        if (targetSeal != null && targetSeal.target() == entity) cancel(targetSeal, true);
        if (entity instanceof ServerPlayer player) cancelOwned(player.getUUID());
    }

    public static void onPlayerDisconnect(UUID playerId) {
        cancelOwned(playerId);
        Seal targetSeal = BY_TARGET.get(playerId);
        if (targetSeal != null) cancel(targetSeal, true);
        BURSTS.removeIf(burst -> burst.owner().getUUID().equals(playerId) || burst.target().getUUID().equals(playerId));
        READY_AT.remove(playerId);
    }

    /** Called while entities still exist, before a normal server shutdown saves the world. */
    public static void clear() {
        for (Seal seal : List.copyOf(BY_TARGET.values())) cancel(seal, true);
        BY_TARGET.clear();
        TARGET_BY_OWNER.clear();
        READY_AT.clear();
        BURSTS.clear();
        ATTACK.remove();
        IN_BURST.remove();
    }
}
