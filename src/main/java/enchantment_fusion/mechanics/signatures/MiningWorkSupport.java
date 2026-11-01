package enchantment_fusion.mechanics.signatures;

import enchantment_fusion.mechanics.ExtraBlockHarvest;
import enchantment_fusion.mechanics.MatrixEffectExecutor;
import enchantment_fusion.mechanics.transmute.TransmuteRuntimeSupport;
import enchantment_fusion.model.FusionCarrier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;

/** Shared rules for a one-block work window; the actual break still uses vanilla/Fabric. */
final class MiningWorkSupport {
    private MiningWorkSupport() {}

    static boolean legalBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState block) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && player.level() == level
            && !ExtraBlockHarvest.isActive() && !MatrixEffectExecutor.isExecuting()
            && !Boolean.TRUE.equals(TransmuteRuntimeSupport.IN_CUSTOM_COMBAT.get()) && level.isInWorldBounds(pos)
            && level.hasChunkAt(pos) && level.mayInteract(player, pos)
            && !level.getServer().isUnderSpawnProtection(level, pos, player)
            && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())
            && !block.isAir() && block.getDestroySpeed(level, pos) >= 0
            && MeleeWorkSkills.supportsTool(FusionCarrier.classify(player.getMainHandItem()));
    }
    static AttributeModifier modifier(Identifier id, double multiplier) {
        return new AttributeModifier(id, multiplier - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
    static void remove(ServerPlayer player, Identifier id, double multiplier) {
        AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        AttributeModifier actual = speed == null ? null : speed.getModifier(id);
        if (actual != null && actual.equals(modifier(id, multiplier)) && !speed.getPermanentModifiers().contains(actual))
            speed.removeModifier(id);
    }
    /** Preserve foreign/permanent modifiers with the same identifier; never remove one we do not own. */
    static boolean refresh(ServerPlayer player, Identifier id, double multiplier, boolean waterOnly) {
        AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (speed == null) return false;
        AttributeModifier existing = speed.getModifier(id), wanted = modifier(id, multiplier);
        if (existing != null && (!existing.equals(wanted) || speed.getPermanentModifiers().contains(existing))) return false;
        boolean enabled = MeleeWorkSkills.supportsTool(FusionCarrier.classify(player.getMainHandItem()))
            && (!waterOnly || player.isEyeInFluid(FluidTags.WATER));
        if (enabled && existing == null) speed.addTransientModifier(wanted);
        else if (!enabled && existing != null) speed.removeModifier(id);
        return true;
    }
}
