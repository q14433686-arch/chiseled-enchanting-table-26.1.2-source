package enchantment_fusion.mechanics.signatures;

import com.mojang.serialization.Codec;
import enchantment_fusion.EnchantmentFusion;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/** A shot keeps its gravity multiplier after switching hands, save/reload, and client tracking. */
public final class HeavyCrushProjectiles {
    private static final class Attachments {
        private static final AttachmentType<Integer> GRAVITY_PERCENT = AttachmentRegistry.create(
            EnchantmentFusion.id("heavy_crush_projectile_gravity"), builder -> builder
                .persistent(Codec.intRange(100, 300))
                .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
    }
    private HeavyCrushProjectiles() {}
    public static void init() { java.util.Objects.requireNonNull(Attachments.GRAVITY_PERCENT); }

    /** Only real fired arrows and thrown tridents inherit the launcher penalty; ghosts stay independent. */
    public static void markLaunch(Projectile projectile, ItemStack weapon) {
        if (projectile instanceof AbstractArrow && !PhantomVolley.isGhost(projectile)
            && HeavyCrushSkill.selected(weapon) != null) {
            projectile.setAttached(Attachments.GRAVITY_PERCENT, 150);
        }
    }
    public static double gravity(AbstractArrow arrow, double base) {
        Integer percent = arrow.getAttached(Attachments.GRAVITY_PERCENT);
        return percent == null ? base : base * Math.clamp(percent, 100, 300) / 100.0D;
    }
}
