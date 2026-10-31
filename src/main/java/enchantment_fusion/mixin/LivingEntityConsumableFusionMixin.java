package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import enchantment_fusion.mechanics.CrossDomainTransmutations;
import enchantment_fusion.mechanics.signatures.RangedCadenceSkills;
import enchantment_fusion.mechanics.signatures.RangedCadenceMath;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
    @Unique
    private boolean ef$drawCreditApplied;
    @Unique
    private int ef$lastDrawGeneration, ef$ignoredActiveGeneration, ef$recoverDrawGeneration;

    @WrapMethod(method = "startUsingItem")
    private void ef$startEarnedBowDraw(InteractionHand hand, Operation<Void> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        ItemStack candidate = self.getItemInHand(hand);
        boolean alreadyUsing = self.isUsingItem() || !this.useItem.isEmpty();
        original.call(hand);
        if (alreadyUsing || candidate.isEmpty() || this.useItem != candidate || !(self instanceof Player player)) return;
        this.ef$drawCreditApplied = false;
        this.ef$ignoredActiveGeneration = 0;
        this.ef$recoverDrawGeneration = 0;
        int credit = 0;
        if (player instanceof ServerPlayer serverPlayer) {
            credit = RangedCadenceSkills.startBowDraw(serverPlayer, hand, candidate);
        } else if (self.level().isClientSide()) {
            int view = RangedCadenceSkills.clientDrawView(player, hand, candidate);
            int generation = RangedCadenceMath.viewGeneration(view);
            if (RangedCadenceMath.viewActive(view)) {
                // An active view already present before this local action may be from
                // the previous action; only a new generation may arrive late.
                this.ef$ignoredActiveGeneration = generation;
            } else if (generation > 0 && generation != this.ef$lastDrawGeneration) {
                credit = RangedCadenceMath.viewTicks(view);
                this.ef$lastDrawGeneration = generation;
            }
        }
        if (credit > 0) {
            this.useItemRemaining = Math.max(1, this.useItemRemaining - credit);
            this.ef$drawCreditApplied = true;
        }
    }

    @WrapMethod(method = "onSyncedDataUpdated")
    private void ef$rememberResetDraw(EntityDataAccessor<?> accessor, Operation<Void> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        boolean hadUse = !this.useItem.isEmpty(), hadCredit = this.ef$drawCreditApplied;
        original.call(accessor);
        if (self.level().isClientSide() && self instanceof Player && hadUse && this.useItem.isEmpty() && hadCredit) {
            // A delayed flags update can clear local use; if the same server draw is
            // re-established, restore its credit once after vanilla resets its timer.
            this.ef$recoverDrawGeneration = this.ef$lastDrawGeneration;
            this.ef$drawCreditApplied = false;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ef$syncLateBowDrawView(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!this.isUsingItem() && this.useItem.isEmpty()) {
            this.ef$drawCreditApplied = false;
            return;
        }
        if (!self.level().isClientSide() || this.ef$drawCreditApplied || !this.isUsingItem()
            || !(self instanceof Player player) || this.useItem.isEmpty()) return;
        int view = RangedCadenceSkills.clientDrawView(player, self.getUsedItemHand(), this.useItem);
        int generation = RangedCadenceMath.viewGeneration(view);
        boolean newGeneration = generation != this.ef$lastDrawGeneration;
        boolean restoring = generation == this.ef$recoverDrawGeneration && generation != 0;
        if (RangedCadenceMath.viewActive(view) && generation > 0 && (newGeneration || restoring)
            && generation != this.ef$ignoredActiveGeneration) {
            this.useItemRemaining = Math.max(1, this.useItemRemaining - RangedCadenceMath.viewTicks(view));
            this.ef$drawCreditApplied = true;
            this.ef$lastDrawGeneration = generation;
            this.ef$recoverDrawGeneration = 0;
        }
    }

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
