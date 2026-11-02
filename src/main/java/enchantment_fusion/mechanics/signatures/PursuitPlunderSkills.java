package enchantment_fusion.mechanics.signatures;

import static enchantment_fusion.mechanics.signatures.MeleeImpulseSkills.close;
import static enchantment_fusion.mechanics.signatures.MeleeImpulseSkills.find;
import static enchantment_fusion.mechanics.signatures.MeleeImpulseSkills.finite;
import static enchantment_fusion.mechanics.signatures.MeleeImpulseSkills.hasPendingSteps;
import static enchantment_fusion.mechanics.signatures.MeleeImpulseSkills.owns;
import static enchantment_fusion.mechanics.signatures.MeleeImpulseSkills.ready;
import static enchantment_fusion.mechanics.signatures.MeleeImpulseSkills.removeMark;

import enchantment_fusion.mechanics.CompendiumDiscoveryTracker;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills.Attack;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills.Gear;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills.Mark;
import enchantment_fusion.mechanics.signatures.MeleeImpulseSkills.Pursuit;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** The chosen 08 pursuit, using the shared real-knockback/movement tracker. */
final class PursuitPlunderSkills {
    private PursuitPlunderSkills() {}
    /** Claim only continuously observed, still-forward motion from this weapon's prior real knockback. */
    static void prepare(Attack frame, ServerPlayer owner, LivingEntity target, Gear gear) {
        if (frame.ambiguous || frame.pursuit != null || !target.isAlive() || target.isPassenger() || target.isFallFlying()
            || target.noPhysics || owner.isPassenger() || owner.isFallFlying() || owner.getAbilities().flying
            || owner.level().getEntity(target.getUUID()) != target) return;
        Mark mark = find(owner, target, gear); long now = owner.level().getGameTime();
        if (mark == null || now >= mark.expires || now <= mark.created || mark.motionTick == Long.MIN_VALUE
            || mark.motionTick > now || now - mark.motionTick > 2 || hasPendingSteps(mark)
            || !close(mark.lastOwner, owner.position()) || !close(mark.lastTarget, target.position())
            || !MeleeImpulseMath.trackedMotion(mark.lastMotion.x, mark.lastMotion.z, mark.direction.x, mark.direction.z)) return;
        Vec3 before = frame.beforeVelocity;
        if (frame.beforeSource != frame.confirmed || before == null || !finite(before)) return;
        Vec3 direction = new Vec3(mark.lastMotion.x, 0.0D, mark.lastMotion.z).normalize();
        double available = before.x * direction.x + before.z * direction.z;
        if (MeleeImpulseMath.pursuitStrength(available, gear.pair().primaryLevel(), gear.pair().secondaryLevel()) <= 0.0F) return;
        // A later hit or reentrant callback cannot claim this mark a second time.
        removeMark(mark);
        frame.pursuit = new Pursuit(mark, target, direction, available);
    }
    /** Settle after vanilla knockback and its player packet, never teleporting either entity. */
    static void settle(Attack frame) {
        Pursuit claim = frame.pursuit;
        if (claim == null || !(frame.player instanceof ServerPlayer owner) || frame.target != claim.target()
            || !ready(owner) || owner.getMainHandItem() != frame.weapon || !owns(owner, claim.mark().gear)
            || owner.isPassenger() || owner.isFallFlying() || owner.getAbilities().flying
            || !claim.target().isAlive() || claim.target().isPassenger() || claim.target().isFallFlying() || claim.target().noPhysics
            || owner.level().getEntity(claim.target().getUUID()) != claim.target()
            || !MeleeAttackThemes.eligible(owner, claim.target(), MeleeAttackThemes.SHARP)) return;
        Vec3 targetVelocity = claim.target().getDeltaMovement(), ownerVelocity = owner.getDeltaMovement();
        if (!finite(targetVelocity) || !finite(ownerVelocity)) return;
        Vec3 direction = claim.movement();
        double remaining = targetVelocity.x * direction.x + targetVelocity.z * direction.z;
        // Do not take more than was actually moving at the second hit; leave lateral/new impulses alone.
        double taken = Math.min(claim.available(), Math.max(0.0D, remaining));
        float force = MeleeImpulseMath.pursuitStrength(taken, claim.mark().gear.pair().primaryLevel(),
            claim.mark().gear.pair().secondaryLevel());
        if (force <= 0.0F) return;
        claim.target().setDeltaMovement(targetVelocity.x - direction.x * taken, targetVelocity.y,
            targetVelocity.z - direction.z * taken);
        claim.target().hurtMarked = true;
        if (claim.target() instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        owner.push(direction.x * force, 0.0D, direction.z * force);
        owner.hurtMarked = true; owner.connection.send(new ClientboundSetEntityMotionPacket(owner));
        claim.target().addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30 + 4 * Math.clamp(claim.mark().gear.pair().secondaryLevel(), 1, 10), 0));
        owner.level().sendParticles(ParticleTypes.CRIT, claim.target().getX(), claim.target().getY(0.5D), claim.target().getZ(),
            8, 0.2D, 0.2D, 0.2D, 0.01D);
        CompendiumDiscoveryTracker.recordDiscovery(owner, claim.mark().gear.pair().primaryId(), claim.mark().gear.pair().secondaryId());
    }
}
