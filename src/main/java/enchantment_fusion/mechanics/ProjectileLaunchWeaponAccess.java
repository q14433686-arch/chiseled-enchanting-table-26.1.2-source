package enchantment_fusion.mechanics;

import net.minecraft.world.item.ItemStack;

/**
 * Duck-interface implemented by {@code ProjectileImpactFusionMixin} so both impact hooks and
 * in-flight tick hooks ({@code boomerang_arrow}, {@code soul_seeker_arrow}, {@code sun_piercer})
 * read the exact weapon snapshot captured when the projectile was launched.
 */
public interface ProjectileLaunchWeaponAccess {
    ItemStack ef$getLaunchWeapon();

    void ef$setLaunchWeapon(ItemStack weapon);
}
