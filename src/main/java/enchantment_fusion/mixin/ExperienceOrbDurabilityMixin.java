package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.DurabilityEconomy;
import enchantment_fusion.mechanics.signatures.MeleeMendingSkills;
import enchantment_fusion.mechanics.signatures.GrapnelDurability;
import enchantment_fusion.mechanics.signatures.ExperienceOrbCountAccess;
import enchantment_fusion.mechanics.signatures.RangedMendingCapture;
import enchantment_fusion.mechanics.signatures.RangedMendingSkills;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Observe real orb repairs; use only leftover XP for non-native mending carriers. */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbDurabilityMixin implements ExperienceOrbCountAccess {
    @Shadow private int count;
    @Override public int ef$pickupCount() { return count; }

    // playerTouch calls the recursive vanilla method exactly once. Wrapping that outer
    // call avoids replaying our non-native pass at every recursive return frame.
    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ExperienceOrb;repairPlayerItems(Lnet/minecraft/server/level/ServerPlayer;I)I"))
    private int ef$repairNonNative(ExperienceOrb orb, ServerPlayer player, int amount, Operation<Integer> original) {
        OptionalInt routed = RangedMendingCapture.claim(orb, player, amount);
        if (routed.isPresent()) return routed.getAsInt();
        int remaining = original.call(orb, player, amount);
        return remaining > 0 ? DurabilityEconomy.repairNonNative(player, remaining) : remaining;
    }

    @WrapMethod(method = "awardWithDirection")
    private static void ef$scopeAwardLoop(ServerLevel level, Vec3 position, Vec3 direction, int amount,
                                          Operation<Void> original) {
        RangedMendingCapture.Frame frame = RangedMendingCapture.beginAwardLoop(level, position, amount);
        boolean completed = false;
        try { original.call(level, position, direction, amount); completed = true; }
        finally { RangedMendingCapture.endAwardLoop(frame, completed); }
    }

    // This is the particular allocation performed by vanilla's award loop, not any
    // entity coincidentally spawned nearby or inside a third-party event listener.
    @WrapOperation(method = "awardWithDirection", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean ef$observeAwardInsertion(ServerLevel level, Entity entity, Operation<Boolean> original) {
        RangedMendingCapture.Frame frame = RangedMendingCapture.beginAdd(level);
        try {
            boolean added = original.call(level, entity);
            if (added && entity instanceof ExperienceOrb orb) RangedMendingCapture.added(level, orb);
            return added;
        } finally { RangedMendingCapture.endAdd(frame); }
    }

    @WrapMethod(method = "repairPlayerItems")
    private int ef$scopeRepairCalculation(ServerPlayer player, int amount, Operation<Integer> original) {
        GrapnelDurability.RepairFrame frame = GrapnelDurability.beginRepair(player);
        try { return original.call(player, amount); }
        finally { GrapnelDurability.endRepair(frame); }
    }

    @WrapOperation(method = "repairPlayerItems", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;modifyDurabilityToRepairFromXp(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;I)I"))
    private int ef$observeRepairValue(ServerLevel level, ItemStack stack, int amount, Operation<Integer> original,
                                      ServerPlayer player, int originalAmount) {
        GrapnelDurability.RepairFrame frame = GrapnelDurability.beginRepairCalculation(player, level, stack);
        int result = original.call(level, stack, amount);
        GrapnelDurability.finishRepairCalculation(frame, amount, result);
        return result;
    }

    @WrapOperation(method = "repairPlayerItems", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"))
    private void ef$observeActualRepair(ItemStack stack, int newDamage, Operation<Void> original,
                                        ServerPlayer player, int amount) {
        int before = stack.getDamageValue();
        original.call(stack, newDamage);
        int repaired = Math.max(0, before - stack.getDamageValue());
        if (repaired > 0) {
            GrapnelDurability.afterOrbRepair(player, stack, before, repaired, false);
            DurabilityEconomy.afterOrbRepair(player, stack, repaired);
            MeleeMendingSkills.afterOrbRepair(player, stack, repaired);
            RangedMendingSkills.afterOrbRepair(player, stack, before, repaired, false);
        }
    }
}
