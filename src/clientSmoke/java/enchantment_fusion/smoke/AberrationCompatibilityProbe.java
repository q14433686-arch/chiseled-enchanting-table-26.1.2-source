package enchantment_fusion.smoke;

import enchantment_fusion.mechanics.AberrantRegistry;
import enchantment_fusion.mechanics.FusionStationOperations;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.model.FusedPair;
import enchantment_fusion.model.TriggerType;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Scoped dispatcher fixture, not a real bow draw, impact mixin, or multiplayer test. */
final class AberrationCompatibilityProbe {
    private AberrationCompatibilityProbe() {}

    static void exercise(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 oldVelocity = player.getDeltaMovement();
        Zombie target = new Zombie(level);
        target.setNoAi(true);
        target.setPos(player.getX() + 3.0D, player.getY(), player.getZ());
        try {
            require(level.addFreshEntity(target), "Fixture target must enter the world");
            MatrixEffectExecutor.clearCooldowns();
            ItemStack cleanBow = new ItemStack(Items.BOW);
            FusionStationOperations.applyFusedPairToGear(cleanBow,
                FusedPair.clean("minecraft:multishot", 1, "minecraft:piercing", 3), level.registryAccess());
            float initial = target.getHealth();
            player.setDeltaMovement(Vec3.ZERO);
            dispatch(level, player, cleanBow, TriggerType.ON_PROJECTILE_IMPACT, target);
            require(target.getHealth() == initial && player.getDeltaMovement().equals(Vec3.ZERO),
                "A clean phantom pair must not also run its retired generic projectile effect");

            ItemStack anomalousBow = new ItemStack(Items.BOW);
            FusionStationOperations.applyFusedPairToGear(anomalousBow,
                new FusedPair("minecraft:multishot", 1, "minecraft:piercing", 3, AberrantRegistry.RECOIL_SKID),
                level.registryAccess());
            dispatch(level, player, anomalousBow, TriggerType.ON_PROJECTILE_IMPACT, target);
            require(target.getHealth() < initial && player.getDeltaMovement().lengthSqr() > 0.0D,
                "A phantom pair must retain a target-dependent aberration");
            float after = target.getHealth();
            player.setDeltaMovement(Vec3.ZERO);
            target.invulnerableTime = 0;
            dispatch(level, player, anomalousBow, TriggerType.ON_PROJECTILE_IMPACT, target);
            require(target.getHealth() == after && player.getDeltaMovement().equals(Vec3.ZERO),
                "The retained aberration must observe the original pair cooldown");

            ItemStack anomalousTool = new ItemStack(Items.IRON_PICKAXE);
            FusionStationOperations.applyFusedPairToGear(anomalousTool,
                new FusedPair("minecraft:efficiency", 2, "minecraft:silk_touch", 1, AberrantRegistry.RECOIL_SKID),
                level.registryAccess());
            target.invulnerableTime = 0;
            dispatch(level, player, anomalousTool, TriggerType.ON_MELEE_STRIKE, target);
            require(target.getHealth() < after && player.getDeltaMovement().lengthSqr() > 0.0D,
                "A precision tool's aberration must retain its living target on melee dispatch");

            ItemStack reverse = new ItemStack(Items.CROSSBOW);
            FusionStationOperations.applyFusedPairToGear(reverse,
                new FusedPair("minecraft:piercing", 3, "minecraft:multishot", 1, AberrantRegistry.RECOIL_SKID),
                level.registryAccess());
            player.setDeltaMovement(Vec3.ZERO);
            dispatch(level, player, reverse, TriggerType.ON_PROJECTILE_IMPACT, null);
            require(player.getDeltaMovement().lengthSqr() > 0.0D,
                "A main-arrow block impact may retain a targetless aberration");
        } finally {
            target.discard();
            player.setDeltaMovement(oldVelocity);
            MatrixEffectExecutor.clearCooldowns();
        }
    }

    private static void dispatch(ServerLevel level, ServerPlayer player, ItemStack host, TriggerType trigger, Zombie target) {
        MatrixEffectExecutor.dispatch(level, player, host, EnumSet.of(trigger), target,
            target == null ? player.position() : target.position(), true, 1.0F);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
