package enchantment_fusion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import enchantment_fusion.mechanics.signatures.GrapnelLooting;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Missing Looting is projected only within the original death-loot paths, never onto live gear. */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperLootingMixin {
    @Inject(method = "getEnchantmentLevel", at = @At("RETURN"), cancellable = true)
    private static void ef$lootLevel(Holder<Enchantment> enchantment, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValueI(); int result = GrapnelLooting.lootLevel(enchantment, entity, vanilla);
        if (result != vanilla) cir.setReturnValue(result);
    }
    @WrapMethod(method = "processEquipmentDropChance")
    private static float ef$equipmentScope(ServerLevel level, LivingEntity entity, DamageSource source, float chance,
                                           Operation<Float> original) {
        GrapnelLooting.EquipmentScope scope = GrapnelLooting.beginEquipment(level, entity, source);
        try { return original.call(level, entity, source, chance); }
        finally { GrapnelLooting.endEquipment(scope); }
    }
    @Inject(method = "runIterationOnEquipment", at = @At("HEAD"))
    private static void ef$enterEquipmentIteration(CallbackInfo ci) { GrapnelLooting.enterEquipmentIteration(); }
    @Inject(method = "runIterationOnEquipment", at = @At("RETURN"))
    private static void ef$leaveEquipmentIteration(CallbackInfo ci) { GrapnelLooting.leaveEquipmentIteration(); }
    @WrapOperation(method = "runIterationOnEquipment", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack ef$equipmentCopy(LivingEntity entity, EquipmentSlot slot, Operation<ItemStack> original) {
        ItemStack actual = original.call(entity, slot);
        return GrapnelLooting.equipmentStack(entity, slot, actual);
    }
}
