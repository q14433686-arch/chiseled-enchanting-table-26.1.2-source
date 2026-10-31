package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.FishingTideSkills;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Interact with the real bite, wait counter, loot context and spawned catch. */
@Mixin(FishingHook.class)
public abstract class FishingTideHookMixin {
    @Shadow private int nibble;
    @Shadow private int timeUntilLured;
    @Unique private int ef$beforeNibble;

    @Inject(method = "catchingFish", at = @At("HEAD"))
    private void ef$beforeFishing(BlockPos pos, CallbackInfo ci) {
        this.ef$beforeNibble = this.nibble;
        this.nibble = FishingTideSkills.beforeFishingTick((FishingHook) (Object) this, this.nibble);
    }
    @Inject(method = "catchingFish", at = @At("TAIL"))
    private void ef$afterFishing(BlockPos pos, CallbackInfo ci) {
        this.timeUntilLured = FishingTideSkills.afterFishingTick((FishingHook) (Object) this,
            this.ef$beforeNibble, this.nibble, this.timeUntilLured);
    }
    @WrapOperation(method = "retrieve", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/storage/loot/LootParams$Builder;withLuck(F)Lnet/minecraft/world/level/storage/loot/LootParams$Builder;"))
    private LootParams.Builder ef$waitedLuck(LootParams.Builder builder, float luck, Operation<LootParams.Builder> original, ItemStack rod) {
        return original.call(builder, FishingTideSkills.lootLuck((FishingHook) (Object) this, rod, luck, this.nibble));
    }
    @WrapOperation(method = "retrieve", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean ef$realCatch(Level level, Entity entity, Operation<Boolean> original, ItemStack rod) {
        boolean added = original.call(level, entity);
        FishingTideSkills.afterCatch((FishingHook) (Object) this, rod, entity, added);
        return added;
    }
}
