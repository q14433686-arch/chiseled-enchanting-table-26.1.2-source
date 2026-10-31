package enchantment_fusion.mixin;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.jetbrains.annotations.Nullable;
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

    @Accessor("piercingIgnoreEntityIds")
    @Nullable IntOpenHashSet ef$getPiercingIgnoreEntityIds();

    @Accessor("piercingIgnoreEntityIds")
    void ef$setPiercingIgnoreEntityIds(@Nullable IntOpenHashSet ids);
}
