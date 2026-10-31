package enchantment_fusion.mechanics.signatures;

import com.mojang.serialization.Codec;
import enchantment_fusion.EnchantmentFusion;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;

/** A shot keeps its gravity multiplier after switching hands, save/reload, and client tracking. */
public final class HeavyCrushProjectiles {
    private static final class Attachments {
        private static final AttachmentType<Integer> GRAVITY_PERCENT = AttachmentRegistry.create(
            EnchantmentFusion.id("heavy_crush_projectile_gravity"), builder -> builder
                .persistent(Codec.intRange(100, 300))
                .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
        // Like vanilla arrows' firedFromWeapon: a non-pickup snapshot for conditional datapack effects.
        private static final AttachmentType<ItemStack> ROCKET_WEAPON = AttachmentRegistry.create(
            EnchantmentFusion.id("heavy_crush_rocket_weapon"), builder -> builder.persistent(ItemStack.CODEC));
    }
    private record Shot(@Nullable Shot previous, LivingEntity shooter, ItemStack weapon) {}
    public static final class RocketHit {
        private final @Nullable RocketHit previous;
        private final FireworkRocketEntity projectile;
        private final Entity target;
        private final @Nullable HeavyCrushSkill.Hit hit;
        private RocketHit(@Nullable RocketHit previous, FireworkRocketEntity projectile, Entity target,
                          @Nullable HeavyCrushSkill.Hit hit) {
            this.previous = previous; this.projectile = projectile; this.target = target; this.hit = hit;
        }
    }
    private static final ThreadLocal<Shot> SHOT = new ThreadLocal<>();
    private static final ThreadLocal<RocketHit> ROCKET_HIT = new ThreadLocal<>();
    private HeavyCrushProjectiles() {}
    public static void init() {
        java.util.Objects.requireNonNull(Attachments.GRAVITY_PERCENT);
        java.util.Objects.requireNonNull(Attachments.ROCKET_WEAPON);
    }
    public static void enterShot(LivingEntity shooter, ItemStack weapon) {
        SHOT.set(new Shot(SHOT.get(), shooter, weapon));
    }
    public static void exitShot() {
        Shot current = SHOT.get();
        if (current == null || current.previous() == null) SHOT.remove(); else SHOT.set(current.previous());
    }
    public static @Nullable ItemStack exactLauncher(LivingEntity shooter) {
        Shot current = SHOT.get();
        return current != null && current.shooter() == shooter ? current.weapon() : null;
    }

    /** Real arrows, thrown tridents and crossbow rockets inherit the penalty; ghosts stay independent. */
    public static void markLaunch(Projectile projectile, ItemStack weapon) {
        if ((projectile instanceof AbstractArrow || projectile instanceof FireworkRocketEntity)
            && !PhantomVolley.isGhost(projectile) && HeavyCrushSkill.selected(weapon) != null) {
            projectile.setAttached(Attachments.GRAVITY_PERCENT, 150);
            if (projectile instanceof FireworkRocketEntity) {
                projectile.setAttached(Attachments.ROCKET_WEAPON, weapon.copyWithCount(1));
            }
        }
    }
    public static double gravity(AbstractArrow arrow, double base) {
        Integer percent = arrow.getAttached(Attachments.GRAVITY_PERCENT);
        return percent == null ? base : base * Math.clamp(percent, 100, 300) / 100.0D;
    }
    public static double rocketDownwardAcceleration(FireworkRocketEntity rocket) {
        Integer percent = rocket.getAttached(Attachments.GRAVITY_PERCENT);
        return percent == null || rocket.isNoGravity() || rocket.noPhysics
            ? 0.0D : 0.05D * (Math.clamp(percent, 100, 300) - 100) / 100.0D;
    }
    public static RocketHit beginRocketHit(FireworkRocketEntity rocket, Entity target) {
        ItemStack weapon = rocket.getAttached(Attachments.ROCKET_WEAPON);
        HeavyCrushSkill.Hit hit = null;
        if (weapon != null && rocket.getOwner() instanceof net.minecraft.server.level.ServerPlayer owner
            && rocket.level() instanceof net.minecraft.server.level.ServerLevel level) {
            hit = HeavyCrushSkill.beginProjectile(rocket, weapon, target, level.damageSources().fireworks(rocket, owner));
        }
        RocketHit frame = new RocketHit(ROCKET_HIT.get(), rocket, target, hit);
        ROCKET_HIT.set(frame); // mask an outer rocket, including when this one is ineligible
        return frame;
    }
    public static void endRocketHit(RocketHit frame, boolean completed) {
        if (frame.previous == null) ROCKET_HIT.remove(); else ROCKET_HIT.set(frame.previous);
        HeavyCrushSkill.endHit(frame.hit, completed);
    }
    /** Only the directly struck entity receives the new armor mechanic, never unrelated blast bystanders. */
    public static @Nullable ItemStack rocketWeaponForDirectHit(DamageSource source, LivingEntity victim) {
        RocketHit frame = ROCKET_HIT.get();
        return frame != null && frame.hit != null && frame.target == victim && frame.projectile == source.getDirectEntity()
            ? frame.projectile.getAttached(Attachments.ROCKET_WEAPON) : null;
    }
}
