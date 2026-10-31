package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.BlastDefenseSkills;
import enchantment_fusion.mechanics.signatures.FrostLandingCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/** Observe one actual fall from entry through accepted damage and death handling. */
@Mixin(Player.class)
public abstract class PlayerFrostLandingMixin {
    @WrapMethod(method = "causeFallDamage")
    private boolean ef$observeLanding(double distance, float modifier, DamageSource source, Operation<Boolean> original) {
        Player self = (Player) (Object) this;
        FrostLandingCapture.Frame frame = self instanceof ServerPlayer player
            ? FrostLandingCapture.begin(player, distance, source) : null;
        boolean completed = false;
        try {
            boolean result = original.call(distance, modifier, source);
            completed = true;
            return result;
        } finally {
            FrostLandingCapture.end(frame, completed);
        }
    }

    @WrapMethod(method = "actuallyHurt")
    private void ef$afterHealthSubtraction(ServerLevel level, DamageSource source, float damage, Operation<Void> original) {
        if (!((Object) this instanceof ServerPlayer player)) {
            original.call(level, source, damage);
            return;
        }
        float beforeHealth = player.getHealth(), beforeAbsorption = player.getAbsorptionAmount();
        original.call(level, source, damage);
        FrostLandingCapture.afterHealthDamage(player, source);
        BlastDefenseSkills.observeActualDamage(player, source, beforeHealth, beforeAbsorption);
    }
}
