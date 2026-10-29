package enchantment_fusion.mechanics;

import enchantment_fusion.model.TriggerType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative 3D flying weapon engine powering {@code 飞剑掷斧 (returning_blade_throw)},
 * {@code 幻影连投 (phantom_trident)}, and {@code 飞剑剑气 / 驭水飞剑} binary signatures via live
 * {@link Display.ItemDisplay} entities with outbound/return arcs, wall ricochet, drop vacuum,
 * and full binary-fusion payload delivery.
 */
public final class FlyingBladeManager {
    private static final List<ActiveFlight> ACTIVE_FLIGHTS = new ArrayList<>();
    private static final int MAX_ACTIVE_PER_PLAYER = 3;

    public enum FlightStyle {
        BOOMERANG_BLADE,
        PHANTOM_LANCE,
        HYDRO_SWORD_QI
    }

    private static final class ActiveFlight {
        final UUID ownerId;
        final ResourceKey<Level> dimension;
        final Display.ItemDisplay displayEntity;
        final ItemStack weaponSnapshot;
        final InteractionHand hand;
        final int level;
        final FlightStyle style;
        final double speedPerTick;
        final int outboundMaxTicks;
        final int hardMaxTicks;
        final Map<UUID, Integer> entityHitTick = new HashMap<>();
        final List<UUID> carriedDrops = new ArrayList<>();

        Vec3 pos;
        Vec3 dir;
        boolean returning;
        int ageTicks;
        int hitEnemiesCount;
        float spinAngle;

        ActiveFlight(
            UUID ownerId,
            ResourceKey<Level> dimension,
            Display.ItemDisplay displayEntity,
            ItemStack weaponSnapshot,
            InteractionHand hand,
            int level,
            FlightStyle style,
            Vec3 startPos,
            Vec3 initialDir,
            double speedPerTick,
            int outboundMaxTicks,
            int hardMaxTicks
        ) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.displayEntity = displayEntity;
            this.weaponSnapshot = weaponSnapshot;
            this.hand = hand;
            this.level = level;
            this.style = style;
            this.pos = startPos;
            this.dir = initialDir;
            this.speedPerTick = speedPerTick;
            this.outboundMaxTicks = outboundMaxTicks;
            this.hardMaxTicks = hardMaxTicks;
        }
    }

    private FlyingBladeManager() {}

    public static synchronized boolean canLaunchFor(UUID playerId) {
        int count = 0;
        for (ActiveFlight f : ACTIVE_FLIGHTS) {
            if (f.ownerId.equals(playerId)) {
                count++;
            }
        }
        return count < MAX_ACTIVE_PER_PLAYER;
    }

    public static synchronized boolean launchReturningBlade(
        ServerLevel level,
        ServerPlayer owner,
        InteractionHand hand,
        ItemStack weapon,
        int loyaltyLevel
    ) {
        if (weapon == null || weapon.isEmpty() || !canLaunchFor(owner.getUUID())) {
            return false;
        }
        int clampedLv = Math.clamp(loyaltyLevel, 1, 5);
        Vec3 start = owner.getEyePosition().add(owner.getLookAngle().scale(0.65D)).add(0.0D, -0.18D, 0.0D);
        Vec3 dir = owner.getLookAngle().normalize();

        Display.ItemDisplay display = spawnWeaponDisplay(level, start, dir, weapon, 65.0F);
        double maxDist = 14.0D + 4.0D * clampedLv;
        double speed = 1.65D + 0.16D * clampedLv;
        int outTicks = Math.max(7, (int) Math.round(maxDist / speed));

        ACTIVE_FLIGHTS.add(new ActiveFlight(
            owner.getUUID(),
            level.dimension(),
            display,
            weapon.copyWithCount(1),
            hand,
            clampedLv,
            FlightStyle.BOOMERANG_BLADE,
            start,
            dir,
            speed,
            outTicks,
            outTicks * 3 + 18
        ));

        level.playSound(null, owner.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.95F, 1.32F);
        level.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.75F, 1.25F);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, start.x, start.y, start.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, start.x, start.y, start.z, 8, 0.18D, 0.18D, 0.18D, 0.06D);
        return true;
    }

    public static synchronized boolean launchLinearPhantomWeapon(
        ServerLevel level,
        ServerPlayer owner,
        InteractionHand hand,
        ItemStack weapon,
        int powerTier,
        FlightStyle style
    ) {
        if (weapon == null || weapon.isEmpty() || !canLaunchFor(owner.getUUID())) {
            return false;
        }
        int clampedLv = Math.clamp(powerTier, 1, 5);
        Vec3 start = owner.getEyePosition().add(owner.getLookAngle().scale(0.75D)).add(0.0D, -0.15D, 0.0D);
        Vec3 dir = owner.getLookAngle().normalize();

        float pitchTilt = (style == FlightStyle.PHANTOM_LANCE) ? (owner.getXRot() - 45.0F) : 70.0F;
        Display.ItemDisplay display = spawnWeaponDisplay(level, start, dir, weapon, pitchTilt);
        double speed = (style == FlightStyle.PHANTOM_LANCE) ? (2.15D + 0.15D * clampedLv) : (1.75D + 0.12D * clampedLv);
        int outTicks = (style == FlightStyle.PHANTOM_LANCE) ? 13 : 10;

        ACTIVE_FLIGHTS.add(new ActiveFlight(
            owner.getUUID(),
            level.dimension(),
            display,
            weapon.copyWithCount(1),
            hand,
            clampedLv,
            style,
            start,
            dir,
            speed,
            outTicks,
            outTicks + 2
        ));

        level.playSound(
            null,
            owner.blockPosition(),
            style == FlightStyle.HYDRO_SWORD_QI ? SoundEvents.TRIDENT_RIPTIDE_2.value() : SoundEvents.TRIDENT_THROW.value(),
            SoundSource.PLAYERS,
            0.85F,
            1.42F
        );
        level.sendParticles(
            style == FlightStyle.HYDRO_SWORD_QI ? ParticleTypes.SPLASH : ParticleTypes.ENCHANTED_HIT,
            start.x, start.y, start.z, 10, 0.2D, 0.2D, 0.2D, 0.08D
        );
        return true;
    }

    private static Display.ItemDisplay spawnWeaponDisplay(
        ServerLevel level,
        Vec3 pos,
        Vec3 dir,
        ItemStack weapon,
        float xRot
    ) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        display.setPos(pos.x, pos.y, pos.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        display.setYRot(yaw);
        display.setXRot(xRot);
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setGlowingTag(true);
        ItemStack visual = weapon.copyWithCount(1);
        visual.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        display.getSlot(0).set(visual);
        level.addFreshEntity(display);
        return display;
    }

    public static synchronized void tickServer(MinecraftServer server) {
        if (ACTIVE_FLIGHTS.isEmpty()) {
            return;
        }
        Iterator<ActiveFlight> it = ACTIVE_FLIGHTS.iterator();
        while (it.hasNext()) {
            ActiveFlight f = it.next();
            ServerLevel level = server.getLevel(f.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(f.ownerId);
            if (level == null || owner == null || !owner.isAlive() || owner.level() != level || f.ageTicks >= f.hardMaxTicks) {
                discardQuietly(f.displayEntity);
                it.remove();
                continue;
            }
            if (!tickSingleFlight(level, owner, f)) {
                discardQuietly(f.displayEntity);
                it.remove();
            }
        }
    }

    private static boolean tickSingleFlight(ServerLevel level, ServerPlayer owner, ActiveFlight f) {
        f.ageTicks++;
        Vec3 prevPos = f.pos;

        if (f.style == FlightStyle.BOOMERANG_BLADE) {
            if (!f.returning && f.ageTicks >= f.outboundMaxTicks) {
                f.returning = true;
                level.playSound(null, BlockPos.containing(f.pos), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.65F, 1.45F);
            }

            if (!f.returning) {
                // Subtle outbound homing toward the nearest hostile near the flight axis
                LivingEntity homing = findHomingTarget(level, owner, f.pos, f.dir);
                if (homing != null) {
                    Vec3 toMob = homing.getBoundingBox().getCenter().subtract(f.pos).normalize();
                    f.dir = f.dir.scale(0.80D).add(toMob.scale(0.20D)).normalize();
                }
                Vec3 candidateNext = f.pos.add(f.dir.scale(f.speedPerTick));
                BlockHitResult blockHit = level.clip(new ClipContext(
                    f.pos, candidateNext, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner
                ));
                if (blockHit.getType() == HitResult.Type.BLOCK) {
                    Vec3 hitLoc = blockHit.getLocation();
                    level.sendParticles(ParticleTypes.CRIT, hitLoc.x, hitLoc.y, hitLoc.z, 10, 0.18D, 0.18D, 0.18D, 0.12D);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hitLoc.x, hitLoc.y, hitLoc.z, 8, 0.15D, 0.15D, 0.15D, 0.08D);
                    level.playSound(null, blockHit.getBlockPos(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.85F);
                    f.pos = hitLoc.subtract(f.dir.scale(0.25D));
                    f.returning = true;
                } else {
                    f.pos = candidateNext;
                }
            } else {
                Vec3 handTarget = owner.getEyePosition().add(0.0D, -0.28D, 0.0D);
                Vec3 toOwner = handTarget.subtract(f.pos);
                double dist = toOwner.length();
                double returnSpeed = f.speedPerTick * 1.18D + Math.min(1.2D, f.ageTicks * 0.03D);
                if (dist <= Math.max(1.35D, returnSpeed)) {
                    sweepSegmentCombat(level, owner, f, prevPos, handTarget);
                    deliverVacuumedDrops(level, owner, f);
                    onBladeCaught(level, owner, f);
                    return false;
                }
                f.dir = toOwner.normalize();
                f.pos = f.pos.add(f.dir.scale(returnSpeed));
            }
        } else {
            // Linear Phantom Trident / Sword-Qi Lance
            Vec3 candidateNext = f.pos.add(f.dir.scale(f.speedPerTick));
            BlockHitResult blockHit = level.clip(new ClipContext(
                f.pos, candidateNext, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner
            ));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                Vec3 hitLoc = blockHit.getLocation();
                sweepSegmentCombat(level, owner, f, prevPos, hitLoc);
                CrossDomainTransmutations.onProjectileImpact(level, null, owner, f.weaponSnapshot, null, hitLoc);
                MatrixEffectExecutor.dispatch(
                    level, owner, f.weaponSnapshot,
                    EnumSet.of(TriggerType.ON_TRIDENT_CHANNEL, TriggerType.ON_PROJECTILE_IMPACT),
                    null, hitLoc, true, 1.0F
                );
                level.sendParticles(ParticleTypes.END_ROD, hitLoc.x, hitLoc.y, hitLoc.z, 12, 0.2D, 0.2D, 0.2D, 0.08D);
                level.playSound(null, blockHit.getBlockPos(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.80F, 1.45F);
                return false;
            }
            f.pos = candidateNext;
            if (f.ageTicks >= f.outboundMaxTicks) {
                sweepSegmentCombat(level, owner, f, prevPos, f.pos);
                level.sendParticles(ParticleTypes.ENCHANT, f.pos.x, f.pos.y, f.pos.z, 14, 0.25D, 0.25D, 0.25D, 0.1D);
                return false;
            }
        }

        // Update 3D display entity position & rapid spin
        if (f.displayEntity.isAlive()) {
            f.displayEntity.setPos(f.pos.x, f.pos.y, f.pos.z);
            if (f.style == FlightStyle.BOOMERANG_BLADE) {
                f.spinAngle = (f.spinAngle + 48.0F) % 360.0F;
                f.displayEntity.setYRot(f.spinAngle);
                f.displayEntity.setXRot(72.0F);
            } else {
                float yaw = (float) Math.toDegrees(Math.atan2(-f.dir.x, f.dir.z));
                f.spinAngle = (f.spinAngle + 32.0F) % 360.0F;
                f.displayEntity.setYRot(yaw + (f.style == FlightStyle.HYDRO_SWORD_QI ? f.spinAngle : 0.0F));
            }
        }

        // Sword-qi / trident wake particles & cadence whoosh
        emitFlightTrailParticles(level, f);
        if (f.ageTicks % 4 == 0 && f.style == FlightStyle.BOOMERANG_BLADE) {
            level.playSound(null, BlockPos.containing(f.pos), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.35F, 1.55F);
        }

        sweepSegmentCombat(level, owner, f, prevPos, f.pos);
        if (f.style == FlightStyle.BOOMERANG_BLADE) {
            captureAndTowDrops(level, f);
        }
        return true;
    }

    private static void emitFlightTrailParticles(ServerLevel level, ActiveFlight f) {
        if (f.style == FlightStyle.HYDRO_SWORD_QI) {
            level.sendParticles(ParticleTypes.SPLASH, f.pos.x, f.pos.y, f.pos.z, 5, 0.18D, 0.12D, 0.18D, 0.04D);
            level.sendParticles(ParticleTypes.BUBBLE_POP, f.pos.x, f.pos.y, f.pos.z, 3, 0.15D, 0.15D, 0.15D, 0.02D);
        } else if (f.style == FlightStyle.PHANTOM_LANCE) {
            level.sendParticles(ParticleTypes.END_ROD, f.pos.x, f.pos.y, f.pos.z, 2, 0.06D, 0.06D, 0.06D, 0.01D);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, f.pos.x, f.pos.y, f.pos.z, 3, 0.10D, 0.10D, 0.10D, 0.02D);
        } else {
            level.sendParticles(ParticleTypes.END_ROD, f.pos.x, f.pos.y, f.pos.z, 2, 0.14D, 0.06D, 0.14D, 0.01D);
            level.sendParticles(ParticleTypes.ENCHANT, f.pos.x, f.pos.y, f.pos.z, 3, 0.20D, 0.10D, 0.20D, 0.04D);
            if (f.ageTicks % 2 == 0) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, f.pos.x, f.pos.y, f.pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void sweepSegmentCombat(
        ServerLevel level,
        ServerPlayer owner,
        ActiveFlight f,
        Vec3 from,
        Vec3 to
    ) {
        AABB segBox = new AABB(from, to).inflate(1.25D, 0.95D, 1.25D);
        List<LivingEntity> candidates = level.getEntitiesOfClass(
            LivingEntity.class,
            segBox,
            e -> e != owner && e.isAlive() && !e.isAlliedTo(owner) && CrossDomainTransmutations.allowAttackTarget(owner, e)
        );
        if (candidates.isEmpty()) {
            return;
        }
        float rawAttack = resolveWeaponAttackDamage(f.weaponSnapshot);
        float mult = f.returning ? (0.78F + 0.14F * f.level) : (0.90F + 0.15F * f.level);
        float finalDamage = Math.max(4.0F, rawAttack * mult);

        Set<TriggerType> triggers = (f.style == FlightStyle.PHANTOM_LANCE)
            ? EnumSet.of(TriggerType.ON_TRIDENT_CHANNEL, TriggerType.ON_PROJECTILE_IMPACT)
            : EnumSet.of(TriggerType.ON_MELEE_STRIKE, TriggerType.ON_SWEEP_OR_CLEAVE);

        for (LivingEntity target : candidates) {
            Integer prevHitTick = f.entityHitTick.get(target.getUUID());
            if (prevHitTick != null && (f.ageTicks - prevHitTick) < 7) {
                continue;
            }
            f.entityHitTick.put(target.getUUID(), f.ageTicks);
            f.hitEnemiesCount++;

            int savedInvuln = target.invulnerableTime;
            target.invulnerableTime = 0;
            try {
                target.hurtServer(level, level.damageSources().playerAttack(owner), finalDamage);
                target.invulnerableTime = 0;
                MatrixEffectExecutor.dispatch(level, owner, f.weaponSnapshot, triggers, target, target.position(), true, 1.0F);
                if (f.style == FlightStyle.PHANTOM_LANCE) {
                    CrossDomainTransmutations.onProjectileImpact(level, null, owner, f.weaponSnapshot, target, target.position());
                } else {
                    CrossDomainTransmutations.onFlyingBladeHit(level, owner, f.weaponSnapshot, target);
                }
            } finally {
                target.invulnerableTime = savedInvuln;
            }

            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.85D, target.getZ(), 10, 0.25D, 0.25D, 0.25D, 0.12D);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + 0.85D, target.getZ(), 8, 0.25D, 0.25D, 0.25D, 0.10D);
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.75F, 1.25F);
        }
    }

    public static float resolveWeaponAttackDamage(ItemStack weapon) {
        final double[] bonus = {0.0D};
        ItemAttributeModifiers mods = weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        mods.forEach(EquipmentSlot.MAINHAND, (attr, modifier) -> {
            if (Attributes.ATTACK_DAMAGE.equals(attr)) {
                bonus[0] += modifier.amount();
            }
        });
        double total = 1.0D + bonus[0];
        return (float) Math.max(5.0D, total);
    }

    private static LivingEntity findHomingTarget(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 dir) {
        AABB search = AABB.ofSize(pos.add(dir.scale(3.0D)), 5.5D, 3.5D, 5.5D);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity mob : level.getEntitiesOfClass(
            LivingEntity.class,
            search,
            e -> e != owner && e.isAlive() && !e.isAlliedTo(owner) && CrossDomainTransmutations.allowAttackTarget(owner, e)
        )) {
            Vec3 delta = mob.getBoundingBox().getCenter().subtract(pos);
            double dist = delta.length();
            if (dist < 0.2D || delta.normalize().dot(dir) < 0.45D) {
                continue;
            }
            if (dist < bestScore) {
                bestScore = dist;
                best = mob;
            }
        }
        return best;
    }

    private static void captureAndTowDrops(ServerLevel level, ActiveFlight f) {
        AABB pullBox = AABB.ofSize(f.pos, 4.4D, 3.2D, 4.4D);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, pullBox, Entity::isAlive)) {
            item.setNoPickUpDelay();
            item.setPos(f.pos.x, f.pos.y, f.pos.z);
            if (!f.carriedDrops.contains(item.getUUID())) {
                f.carriedDrops.add(item.getUUID());
            }
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, pullBox, Entity::isAlive)) {
            orb.setPos(f.pos.x, f.pos.y, f.pos.z);
        }
    }

    private static void deliverVacuumedDrops(ServerLevel level, ServerPlayer owner, ActiveFlight f) {
        for (UUID dropId : f.carriedDrops) {
            Entity ent = level.getEntity(dropId);
            if (ent instanceof ItemEntity item && item.isAlive()) {
                item.setNoPickUpDelay();
                item.teleportTo(owner.getX(), owner.getY() + 0.4D, owner.getZ());
            }
        }
        AABB nearBlade = AABB.ofSize(f.pos, 4.8D, 3.5D, 4.8D);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, nearBlade, Entity::isAlive)) {
            item.setNoPickUpDelay();
            item.teleportTo(owner.getX(), owner.getY() + 0.4D, owner.getZ());
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, nearBlade, Entity::isAlive)) {
            orb.teleportTo(owner.getX(), owner.getY() + 0.4D, owner.getZ());
        }
    }

    private static void onBladeCaught(ServerLevel level, ServerPlayer owner, ActiveFlight f) {
        level.playSound(null, owner.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.85F, 1.38F);
        level.playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.70F, 1.48F);
        level.sendParticles(
            ParticleTypes.END_ROD,
            owner.getX(), owner.getY() + 1.05D, owner.getZ(),
            10, 0.28D, 0.25D, 0.28D, 0.05D
        );
        if (f.hitEnemiesCount > 0 && !owner.getAbilities().instabuild) {
            ItemStack inHand = owner.getItemInHand(f.hand);
            if (!inHand.isEmpty() && inHand.isDamageableItem() && ItemStack.isSameItem(inHand, f.weaponSnapshot)) {
                EquipmentSlot slot = f.hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                inHand.hurtAndBreak(1, owner, slot);
            }
        }
    }

    public static synchronized void onPlayerDisconnect(UUID playerId) {
        if (playerId == null) return;
        Iterator<ActiveFlight> it = ACTIVE_FLIGHTS.iterator();
        while (it.hasNext()) {
            ActiveFlight f = it.next();
            if (playerId.equals(f.ownerId)) {
                discardQuietly(f.displayEntity);
                it.remove();
            }
        }
    }

    public static synchronized void flushAll() {
        for (ActiveFlight f : ACTIVE_FLIGHTS) {
            discardQuietly(f.displayEntity);
        }
        ACTIVE_FLIGHTS.clear();
    }

    private static void discardQuietly(Entity entity) {
        if (entity != null && !entity.isRemoved()) {
            entity.discard();
        }
    }
}
