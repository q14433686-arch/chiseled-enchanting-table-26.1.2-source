package enchantment_fusion.mixin;

import enchantment_fusion.mechanics.CrossDomainTransmutations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Snapshots multi-tick consumables ({@link DataComponents#CONSUMABLE}) before {@link LivingEntity#completeUsingItem()}
 * decrements stack count so Cross-Domain Unbreaking ("经久省用") triggers reliably on food and potions.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityConsumableFusionMixin {
    @Shadow
    protected ItemStack useItem;

    @Shadow
    protected int useItemRemaining;

    @Shadow
    public abstract boolean isUsingItem();

    @Unique
    private ItemStack ef$consumableSnapshot = ItemStack.EMPTY;

    @Inject(method = "tick", at = @At("HEAD"))
    private void ef$accelerateBowAndShieldUseSpeed(CallbackInfo ci) {
        if (!this.isUsingItem() || this.useItem.isEmpty() || this.useItemRemaining <= 2) {
            return;
        }
        int boostLv = CrossDomainTransmutations.getEffectiveUseSpeedBoost(this.useItem);
        if (boostLv <= 0) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        int tc = self.tickCount;
        boolean stepExtra = boostLv >= 3
            || (boostLv == 2 && (tc & 1) == 0)
            || (boostLv == 1 && (tc & 3) == 0);
        if (stepExtra) {
            this.useItemRemaining = Math.max(1, this.useItemRemaining - 1);
        }
    }

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void ef$beforeCompleteUsingItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer && !this.useItem.isEmpty() && this.useItem.has(DataComponents.CONSUMABLE)) {
            this.ef$consumableSnapshot = this.useItem.copy();
        } else {
            this.ef$consumableSnapshot = ItemStack.EMPTY;
        }
    }

    @Inject(method = "completeUsingItem", at = @At("TAIL"))
    private void ef$afterCompleteUsingItem(CallbackInfo ci) {
        ItemStack snap = this.ef$consumableSnapshot;
        this.ef$consumableSnapshot = ItemStack.EMPTY;
        if (snap.isEmpty()) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
            CrossDomainTransmutations.onConsumableFinished(serverLevel, player, snap);
        }
    }
}
