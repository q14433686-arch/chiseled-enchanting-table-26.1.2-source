package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.BlastDefenseSkills;
import enchantment_fusion.mechanics.signatures.FrostLandingCapture;
import enchantment_fusion.mechanics.signatures.FireTradeoffSkills;
import enchantment_fusion.mechanics.signatures.FireThornsSkills;
import enchantment_fusion.mechanics.signatures.MeleeGuardCapture;
import enchantment_fusion.mechanics.signatures.GrapnelDefenseCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float ef$fireTradeoffAfterMagic(Player self, DamageSource source, float damage, Operation<Float> original) {
        float vanilla = original.call(self, source, damage);
        if (!(self instanceof ServerPlayer player)) return vanilla;
        FireThornsSkills.observeFire(player, source, damage, vanilla);
        float result = FireTradeoffSkills.afterMagic(player, source, damage, vanilla);
        MeleeGuardCapture.afterMagic(player, source, damage, result);
        GrapnelDefenseCapture.afterMagic(player, source, damage, result);
        return result;
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
        FireThornsSkills.observeDamage(player, source, beforeHealth, beforeAbsorption);
        MeleeGuardCapture.afterHealth(player, source, beforeHealth, beforeAbsorption);
        GrapnelDefenseCapture.afterHealth(player, source, beforeHealth, beforeAbsorption);
    }
}
