package enchantment_fusion.mechanics.signatures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import enchantment_fusion.EnchantmentFusion;
import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mixin.AbstractArrowAccessor;
import enchantment_fusion.model.FusedEnchantmentList;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.FusionCarrier;
import enchantment_fusion.registry.FusionDataComponents;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The ordered Multishot/Piercing exception. Ghosts are real, non-recoverable arrows. */
public final class PhantomVolley {
    public static final String VOLLEY = "converging_volley";
    public static final String CLUSTER = "fission_warhead";
    private static final String MULTISHOT = "minecraft:multishot";
    private static final String PIERCING = "minecraft:piercing";
    private static final int GHOST = 1, ARMED = 2, MISSED_ROLL = 3, RESERVED = 4, SPENT = 5;
    private static final int GHOST_LIFETIME = 120;
    private static final double SEARCH_RANGE = 12.0D;
    private record BowBatch(boolean selected, int projectileCount) {}
    private static final ThreadLocal<Deque<BowBatch>> BOW_BATCHES = ThreadLocal.withInitial(ArrayDeque::new);

    private record Flight(int kind, String owner, long born, String target) {
        private static final Codec<Flight> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(GHOST, SPENT).fieldOf("kind").forGetter(Flight::kind),
            Codec.STRING.fieldOf("owner").forGetter(Flight::owner),
            Codec.LONG.fieldOf("born").forGetter(Flight::born),
            Codec.STRING.optionalFieldOf("target", "").forGetter(Flight::target)
        ).apply(instance, Flight::new));
        Flight withKind(int next) { return new Flight(next, owner, born, target); }
    }

    // Fabric's registry is mixin-backed and cannot initialize in plain JUnit. Defer it
    // while keeping pure carrier/probability rules independently testable.
    private static final class Attachments {
        private static final AttachmentType<Flight> FLIGHT = AttachmentRegistry.create(
            EnchantmentFusion.id("phantom_volley_flight"), builder -> builder.persistent(Flight.CODEC));
    }

    private PhantomVolley() {}
    public static void init() { java.util.Objects.requireNonNull(Attachments.FLIGHT); }

    public static boolean isVolley(@Nullable FusedPair pair) {
        return pair != null && MULTISHOT.equals(pair.primaryId()) && PIERCING.equals(pair.secondaryId());
    }
    public static boolean isCluster(@Nullable FusedPair pair) {
        return pair != null && PIERCING.equals(pair.primaryId()) && MULTISHOT.equals(pair.secondaryId());
    }
    public static boolean matches(@Nullable FusedPair pair, String signature) {
        return (isVolley(pair) && VOLLEY.equals(signature)) || (isCluster(pair) && CLUSTER.equals(signature));
    }
    public static boolean supportsCarrier(Set<FusionCarrier> carriers) {
        return carriers != null && (carriers.contains(FusionCarrier.BOW) || carriers.contains(FusionCarrier.CROSSBOW));
    }
    public static boolean applies(@Nullable FusedPair pair, ItemStack stack, String signature) {
        return matches(pair, signature) && stack != null && !stack.isEmpty() && supportsCarrier(FusionCarrier.classify(stack));
    }
    public static @Nullable FusedPair selectedPair(ItemStack weapon, boolean volley) {
        if (weapon == null || weapon.isEmpty() || !supportsCarrier(FusionCarrier.classify(weapon))) return null;
        for (FusedPair pair : weapon.getOrDefault(FusionDataComponents.FUSED_PAIRS, FusedEnchantmentList.EMPTY).pairs()) {
            String signature = SignatureCatalog.resolve(pair).map(entry -> entry.id()).orElse("");
            if ((volley ? isVolley(pair) : isCluster(pair)) && applies(pair, weapon, signature)) return pair;
        }
        return null;
    }

    /** One roll per real shot: level I 35%, capped at 75% for level V or higher. */
    public static float clusterChance(int multishotLevel) {
        return 0.25F + 0.10F * Math.clamp(multishotLevel, 1, 5);
    }
    public static void ensurePiercing(AbstractArrow arrow, int level) {
        int wanted = Math.clamp(level, 1, 12);
        if (arrow.getPierceLevel() < wanted) ((AbstractArrowAccessor) arrow).ef$setPierceLevel((byte) wanted);
    }
    public static boolean isGhost(Projectile projectile) {
        Flight flight = projectile.getAttached(Attachments.FLIGHT);
        return flight != null && flight.kind() == GHOST;
    }
    public static void markGhost(AbstractArrow arrow, ServerPlayer owner, @Nullable LivingEntity target) {
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setAttached(Attachments.FLIGHT, new Flight(GHOST, owner.getUUID().toString(), owner.level().getGameTime(),
            target == null ? "" : target.getUUID().toString()));
    }
    public static void markCluster(AbstractArrow arrow, ServerPlayer owner, boolean armed) {
        if (arrow.getAttached(Attachments.FLIGHT) != null) return;
        arrow.setAttached(Attachments.FLIGHT, new Flight(armed ? ARMED : MISSED_ROLL, owner.getUUID().toString(),
            owner.level().getGameTime(), ""));
    }
    public static void rollCluster(AbstractArrow arrow, ServerPlayer owner, FusedPair pair) {
        if (arrow.getAttached(Attachments.FLIGHT) == null) {
            markCluster(arrow, owner, owner.getRandom().nextFloat() < clusterChance(pair.secondaryLevel()));
        }
    }

    /** Scope the actual vanilla Bow shot list, including nested unrelated shots. */
    public static boolean enterBowBatch(ItemStack weapon, int projectileCount) {
        Deque<BowBatch> batches = BOW_BATCHES.get();
        boolean selected = selectedPair(weapon, true) != null || selectedPair(weapon, false) != null;
        if (!selected && batches.isEmpty()) { BOW_BATCHES.remove(); return false; }
        batches.push(new BowBatch(selected, projectileCount));
        return true;
    }
    public static void exitBowBatch(boolean entered) {
        if (!entered) return;
        Deque<BowBatch> batches = BOW_BATCHES.get();
        batches.pop();
        if (batches.isEmpty()) BOW_BATCHES.remove();
    }
    /** Zero means no selected native batch is currently active. */
    public static int nativeBowBatchSize() {
        Deque<BowBatch> batches = BOW_BATCHES.get();
        int size = !batches.isEmpty() && batches.peek().selected() ? batches.peek().projectileCount() : 0;
        if (batches.isEmpty()) BOW_BATCHES.remove();
        return size;
    }
    public static void configureBow(Projectile projectile, LivingEntity shooter, int index) {
        if (!(projectile instanceof AbstractArrow arrow) || !(shooter instanceof ServerPlayer owner)
            || !(owner.level() instanceof ServerLevel)) return;
        ItemStack weapon = arrow.getWeaponItem();
        FusedPair volley = selectedPair(weapon, true), cluster = selectedPair(weapon, false);
        if (volley != null) {
            ensurePiercing(arrow, volley.secondaryLevel());
            if (index > 0) markGhost(arrow, owner, null);
        } else if (cluster != null) {
            if (index == 0) rollCluster(arrow, owner, cluster);
            else markCluster(arrow, owner, false);
        }
    }

    /** Called from the real crossbow firing method where the vanilla projectile index is available. */
    public static void configureCrossbow(Projectile projectile, LivingEntity shooter, int index) {
        if (!(projectile instanceof AbstractArrow arrow) || !(shooter instanceof ServerPlayer owner)
            || !(owner.level() instanceof ServerLevel)) return;
        ItemStack weapon = arrow.getWeaponItem();
        FusedPair volley = selectedPair(weapon, true);
        FusedPair cluster = selectedPair(weapon, false);
        if (volley != null) {
            ensurePiercing(arrow, volley.secondaryLevel());
            if (index > 0) markGhost(arrow, owner, null);
        } else if (cluster != null && index == 0) {
            rollCluster(arrow, owner, cluster);
        }
    }

    /** Reserve before vanilla damage to avoid reentrant double fission. */
    public static boolean reserveImpact(AbstractArrow arrow) {
        Flight flight = arrow.getAttached(Attachments.FLIGHT);
        if (flight == null || flight.kind() != ARMED) return false;
        arrow.setAttached(Attachments.FLIGHT, flight.withKind(RESERVED));
        return true;
    }
    public static void finishImpact(AbstractArrow arrow, Entity target, Vec3 hit, boolean succeeded) {
        Flight flight = arrow.getAttached(Attachments.FLIGHT);
        if (flight == null || flight.kind() != RESERVED) return;
        arrow.setAttached(Attachments.FLIGHT, flight.withKind(succeeded ? SPENT : ARMED));
        if (!succeeded || !(arrow.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = validOwner(arrow, flight);
        if (owner == null) return;
        spawnChildren(level, owner, arrow, target, hit);
        CompendiumDiscoveryTracker.recordDiscovery(owner, PIERCING, MULTISHOT);
    }

    /** A protected collision is stopped before vanilla damage or deflection can occur. */
    public static boolean protectedCollision(Projectile projectile, Entity target) {
        Flight flight = projectile.getAttached(Attachments.FLIGHT);
        if (flight == null || flight.kind() != GHOST) return false;
        ServerPlayer owner = validOwner(projectile, flight);
        return owner == null || (target instanceof LivingEntity living && isProtected(owner, living));
    }
    public static boolean isProtected(ServerPlayer owner, LivingEntity target) {
        if (target instanceof Player || target instanceof AbstractVillager || target.isSpectator() || target.isInvulnerable()
            || target.isAlliedTo(owner) || owner.isAlliedTo(target)) return true;
        if (target instanceof TamableAnimal tame && tame.isTame()) return true;
        if (target instanceof AbstractHorse horse && horse.isTamed()) return true;
        return target instanceof OwnableEntity ownable && ownable.getOwnerReference() != null;
    }
    public static boolean eligibleHomingTarget(ServerPlayer owner, LivingEntity target) {
        if (target == owner || !target.isAlive() || isProtected(owner, target)
            || !owner.level().mayInteract(owner, target.blockPosition())) return false;
        if (target instanceof NeutralMob) return target instanceof Mob mob && mob.getTarget() == owner;
        return target instanceof Enemy;
    }

    /** Return false only if a ghost was discarded; the real cluster carrier remains an ordinary arrow. */
    public static boolean tick(AbstractArrow arrow) {
        Flight flight = arrow.getAttached(Attachments.FLIGHT);
        if (flight == null || !(arrow.level() instanceof ServerLevel level)) return true;
        if (flight.kind() != GHOST) {
            if (flight.kind() == ARMED && arrow.tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0, 0, 0, 0);
            }
            return true;
        }
        ServerPlayer owner = validOwner(arrow, flight);
        if (owner == null || !arrow.isAlive() || level.getGameTime() - flight.born() >= GHOST_LIFETIME) {
            arrow.discard();
            return false;
        }
        // Reassert after reload and against other code changing pickup mode.
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        UUID targetId = parseUuid(flight.target());
        Entity found = targetId == null ? null : level.getEntity(targetId);
        if (found instanceof LivingEntity target && eligibleHomingTarget(owner, target)
            && arrow.distanceToSqr(target) <= 24.0D * 24.0D) {
            Vec3 velocity = arrow.getDeltaMovement();
            Vec3 desired = target.getBoundingBox().getCenter().subtract(arrow.position());
            double speed = velocity.length();
            if (speed > 0.1D && desired.lengthSqr() > 1.0E-6D) {
                Vec3 steering = velocity.normalize().scale(0.72D).add(desired.normalize().scale(0.28D));
                if (steering.lengthSqr() > 1.0E-6D) arrow.setDeltaMovement(steering.normalize().scale(speed));
                arrow.hurtMarked = true;
            }
        }
        if (arrow.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0, 0, 0, 0);
        }
        return true;
    }

    private static @Nullable ServerPlayer validOwner(Projectile projectile, Flight flight) {
        return projectile.getOwner() instanceof ServerPlayer player && player.getUUID().toString().equals(flight.owner())
            && player.level() == projectile.level() && player.isAlive() && !player.isSpectator() ? player : null;
    }
    private static @Nullable UUID parseUuid(String id) {
        try { return UUID.fromString(id); } catch (IllegalArgumentException ex) { return null; }
    }

    private static void spawnChildren(ServerLevel level, ServerPlayer owner, AbstractArrow parent, Entity hitTarget, Vec3 hit) {
        Vec3 movement = parent.getDeltaMovement();
        if (movement.lengthSqr() < 1.0E-6D) return;
        Vec3 direction = movement.normalize();
        AABB body = hitTarget.getBoundingBox().inflate(0.15D);
        double advance = Math.min(16.0D, Math.max(2.0D, hitTarget.getBbWidth() * 2.0D + hitTarget.getBbHeight()));
        Vec3 beyond = hit.add(direction.scale(advance));
        if (body.contains(beyond)) return;
        Vec3 exit = body.clip(beyond, hit).orElse(hit).add(direction.scale(0.12D));
        BlockPos pos = BlockPos.containing(exit);
        if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)
            || level.clip(new ClipContext(hit, exit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, parent)).getType() != HitResult.Type.MISS) return;
        LivingEntity nearest = level.getEntitiesOfClass(LivingEntity.class, new AABB(exit, exit).inflate(SEARCH_RANGE),
                e -> e != hitTarget && eligibleHomingTarget(owner, e) && e.distanceToSqr(exit) <= SEARCH_RANGE * SEARCH_RANGE)
            .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(exit))).orElse(null);
        float speed = (float) Math.clamp(movement.length(), 0.7D, 3.5D);
        for (float offset : new float[]{-10.0F, 10.0F}) {
            Arrow child = new Arrow(level, owner, new ItemStack(Items.ARROW), null);
            child.setPos(exit.x, exit.y, exit.z);
            if (!level.noCollision(child, child.getBoundingBox())) continue;
            markGhost(child, owner, nearest);
            child.setBaseDamage(1.8D);
            Vec3 aim = direction.yRot((float) Math.toRadians(offset));
            child.shoot(aim.x, aim.y, aim.z, speed, 0.0F);
            level.addFreshEntity(child);
        }
        level.sendParticles(ParticleTypes.CRIT, exit.x, exit.y, exit.z, 10, 0.15D, 0.15D, 0.15D, 0.05D);
    }
}
