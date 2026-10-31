package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.BlastDefenseSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/** Finish actual-damage capture only after the real damage/death/totem pipeline completes. */
@Mixin(Player.class)
public abstract class PlayerBlastDefenseMixin {
    @WrapMethod(method = "hurtServer")
    private boolean ef$trackCounterDamage(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
        if (!((Object) this instanceof ServerPlayer player)) return original.call(level, source, damage);
        BlastDefenseSkills.DamageFrame frame = BlastDefenseSkills.beginDamage(player, source);
        boolean success = false;
        try {
            success = original.call(level, source, damage);
            return success;
        } finally { BlastDefenseSkills.endDamage(frame, success); }
    }
}
