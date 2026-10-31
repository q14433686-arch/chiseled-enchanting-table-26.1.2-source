package enchantment_fusion.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Invoker accessor for {@link AbstractArrow#setPierceLevel(byte)} so cross-domain Bow {@code sun_piercer}
 * and binary {@code [minecraft:piercing ▷ X]} fusions grant real entity penetration at launch.
 */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
    @Invoker("setPierceLevel")
    void ef$setPierceLevel(byte pierceLevel);

    @Accessor("baseDamage")
    double ef$getBaseDamage();
}
