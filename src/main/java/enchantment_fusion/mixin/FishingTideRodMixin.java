package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.signatures.FishingTideSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/** Associate a real cast with the actual hand/rod, including an offhand cast. */
@Mixin(FishingRodItem.class)
public abstract class FishingTideRodMixin {
    @WrapMethod(method = "use")
    private InteractionResult ef$bindActualCast(Level level, Player player, InteractionHand hand,
                                                 Operation<InteractionResult> original) {
        FishingHook before = player.fishing;
        ItemStack rod = player.getItemInHand(hand);
        InteractionResult result = original.call(level, player, hand);
        if (!level.isClientSide() && player instanceof ServerPlayer owner && before == null && player.fishing != null) {
            FishingTideSkills.bind(player.fishing, owner, rod);
        }
        return result;
    }
}
