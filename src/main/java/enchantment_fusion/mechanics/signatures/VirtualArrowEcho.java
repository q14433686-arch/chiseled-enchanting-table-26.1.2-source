package enchantment_fusion.mechanics.signatures;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded replay of a recorded real trajectory. No spawned or pickable projectile exists. */
final class VirtualArrowEcho {
    enum Theme { NONE, POWER, PUNCH, FLAME }
    private static final int MAX_ECHOES = 128, MAX_PER_OWNER = 2;
    private static final class Echo {
        final UUID owner; final ResourceKey<Level> dimension; final List<Vec3> nodes;
        final float damage; final Theme theme; final int rank; int cursor = 1;
        Echo(ServerPlayer owner, List<Vec3> nodes, float damage, Theme theme, int rank) {
            this.owner = owner.getUUID(); this.dimension = owner.level().dimension(); this.nodes = List.copyOf(nodes);
            this.damage = damage; this.theme = theme; this.rank = rank;
        }
    }
    private static final List<Echo> ECHOES = new ArrayList<>();
    private VirtualArrowEcho() {}
    static boolean canLaunch(ServerPlayer owner) {
        return ECHOES.size() < MAX_ECHOES && ECHOES.stream().filter(echo -> echo.owner.equals(owner.getUUID())).count() < MAX_PER_OWNER;
    }
    static boolean launch(ServerPlayer owner, List<Vec3> nodes, float damage, Theme theme, int rank) {
        if (owner == null || !owner.isAlive() || owner.isSpectator() || !canLaunch(owner) || nodes == null
            || nodes.size() < 2 || nodes.size() > 128 || !Float.isFinite(damage) || damage <= 0.0F || theme == null) return false;
        for (Vec3 node : nodes) if (node == null || !Double.isFinite(node.x) || !Double.isFinite(node.y) || !Double.isFinite(node.z)) return false;
        ECHOES.add(new Echo(owner, nodes, Math.min(100.0F, damage), theme, Math.clamp(rank, 1, 10)));
        return true;
    }
    private static boolean protectedTarget(ServerPlayer owner, LivingEntity target) {
        if (target instanceof Player other) return other == owner || other.isSpectator() || other.isInvulnerable()
            || other.isAlliedTo(owner) || owner.isAlliedTo(other) || !owner.canHarmPlayer(other);
        return PhantomVolley.isProtected(owner, target);
    }
    static void tickServer(MinecraftServer server) {
        // Damage callbacks can re-enter and mutate the queue.
        for (Echo echo : List.copyOf(ECHOES)) {
            if (!ECHOES.contains(echo)) continue;
            ServerLevel level = server.getLevel(echo.dimension);
            ServerPlayer owner = server.getPlayerList().getPlayer(echo.owner);
            if (level == null || owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != level || echo.cursor >= echo.nodes.size()) {
                ECHOES.remove(echo); continue;
            }
            Vec3 from = echo.nodes.get(echo.cursor - 1), to = echo.nodes.get(echo.cursor++);
            if (!level.hasChunkAt(BlockPos.containing(from)) || !level.hasChunkAt(BlockPos.containing(to))) {
                ECHOES.remove(echo); continue;
            }
            HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, owner, from, end,
                new AABB(from, end).inflate(0.5D), entity -> entity != owner && entity.isAlive()
                    && !entity.isSpectator() && entity.canBeHitByProjectile(), 0.31F);
            Vec3 visible = hit == null ? end : hit.getLocation();
            level.sendParticles(ParticleTypes.END_ROD, visible.x, visible.y, visible.z, 2, 0.04D, 0.04D, 0.04D, 0.0D);
            if (hit != null) {
                ECHOES.remove(echo); // Consume before arbitrary damage callbacks.
                if (!(hit.getEntity() instanceof LivingEntity target) || protectedTarget(owner, target)
                    || !level.mayInteract(owner, target.blockPosition())
                    || (echo.theme != Theme.NONE && !LoyaltyGuard.eligibleTarget(owner, target))) continue;
                Arrow source = new Arrow(EntityType.ARROW, level);
                source.pickup = AbstractArrow.Pickup.DISALLOWED;
                source.setPos(from); source.setDeltaMovement(end.subtract(from));
                float beforeHealth = target.getHealth(), beforeAbsorption = target.getAbsorptionAmount();
                boolean accepted;
                try { accepted = target.hurtServer(level, level.damageSources().arrow(source, owner), echo.damage); }
                finally { source.discard(); }
                if (!accepted || !target.isAlive() || target.level() != level || !owner.isAlive()
                    || ConfirmedMeleeMath.actualLoss(beforeHealth, beforeAbsorption, target.getHealth(), target.getAbsorptionAmount()) <= 0) continue;
                Vec3 direction = end.subtract(from);
                if (echo.theme == Theme.PUNCH && direction.horizontalDistanceSqr() > 1.0E-6D)
                    target.knockback(0.20D + 0.10D * echo.rank, -direction.x, -direction.z);
                else if (echo.theme == Theme.FLAME) target.igniteForSeconds(2.0F + 0.5F * echo.rank);
            } else if (wall.getType() != HitResult.Type.MISS || echo.cursor >= echo.nodes.size()) ECHOES.remove(echo);
        }
    }
    static void clearPlayer(UUID id) { ECHOES.removeIf(echo -> echo.owner.equals(id)); }
    static void clear() { ECHOES.clear(); }
}
